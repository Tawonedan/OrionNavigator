package com.orion.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.orion.app.R
import com.orion.app.camera.CameraManager
import com.orion.app.databinding.ActivityAddFaceBinding
import com.orion.app.face.domain.BaseFaceDetector.FacePoseResult
import com.orion.app.face.domain.FaceRecognitionHelper
import com.orion.app.tts.TTSManager
import com.orion.app.utils.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

@ExperimentalGetImage
class AddFaceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddFaceBinding
    private var cameraManager: CameraManager? = null
    private var ttsManager: TTSManager? = null
    private var vibrator: Vibrator? = null
    private lateinit var faceHelper: FaceRecognitionHelper
    private lateinit var permissionHelper: PermissionHelper

    private var targetPersonName: String = ""
    private var isScannerActive = false
    private var isProcessingFrame = false
    private var isEnrolled = false
    private var lastVoicePromptTime = 0L
    private var lastValidPose: FacePoseResult? = null
    private var lastCapturedBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && targetPersonName.isNotBlank()) {
            enrollFromGalleryUri(targetPersonName, uri)
        }
    }

    companion object {
        private const val TAG = "AddFaceActivity"
        const val EXTRA_PERSON_NAME = "EXTRA_PERSON_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddFaceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        faceHelper = FaceRecognitionHelper(this)
        permissionHelper = PermissionHelper(this) { isGranted ->
            if (isGranted) {
                startCameraScanner()
            } else {
                Toast.makeText(this, "Izin kamera diperlukan untuk memindai wajah", Toast.LENGTH_SHORT).show()
            }
        }
        ttsManager = TTSManager(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        val prefilledName = intent.getStringExtra(EXTRA_PERSON_NAME) ?: ""
        if (prefilledName.isNotBlank()) {
            binding.etPersonName.setText(prefilledName)
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            vibrate()
            if (isScannerActive) {
                stopCameraScanner()
            } else {
                finish()
            }
        }

        binding.btnStartCameraEnroll.setOnClickListener {
            vibrate()
            val name = binding.etPersonName.text.toString().trim()
            if (name.isEmpty()) {
                binding.tilPersonName.error = "Ketik nama terlebih dahulu"
                return@setOnClickListener
            }
            binding.tilPersonName.error = null
            targetPersonName = name

            if (permissionHelper.hasCameraPermission()) {
                startCameraScanner()
            } else {
                permissionHelper.requestCameraPermission()
            }
        }

        binding.btnPickFromGallery.setOnClickListener {
            vibrate()
            val name = binding.etPersonName.text.toString().trim()
            if (name.isEmpty()) {
                binding.tilPersonName.error = "Ketik nama terlebih dahulu"
                return@setOnClickListener
            }
            binding.tilPersonName.error = null
            targetPersonName = name
            pickImageLauncher.launch("image/*")
        }

        binding.btnFlipCamera.setOnClickListener {
            vibrate()
            val isFront = cameraManager?.flipCamera { e ->
                Toast.makeText(this, "Gagal membalik kamera: ${e.message}", Toast.LENGTH_SHORT).show()
            } ?: false
            val msg = if (isFront) "Kamera depan aktif" else "Kamera belakang aktif"
            ttsManager?.speak(msg)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnSnapNow.setOnClickListener {
            vibrate()
            manualSnapAndEnroll()
        }

        binding.btnCancelScanner.setOnClickListener {
            vibrate()
            stopCameraScanner()
        }

        binding.btnDone.setOnClickListener {
            vibrate()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun startCameraScanner() {
        isScannerActive = true
        isEnrolled = false

        binding.layoutNameInput.visibility = View.GONE
        binding.layoutSuccess.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE
        binding.viewVignette.visibility = View.VISIBLE
        binding.layoutScannerControls.visibility = View.VISIBLE
        binding.btnFlipCamera.visibility = View.VISIBLE
        binding.tvHeaderTitle.text = "MEMINDAI WAJAH"

        ttsManager?.speak("Memulai pendaftaran wajah untuk $targetPersonName. Posisikan wajah Anda di tengah kamera.")
        lastVoicePromptTime = System.currentTimeMillis()

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView,
        )

        cameraManager?.startCamera(
            onAnalyze = { imageProxy ->
                processScannerFrame(imageProxy)
            },
            onError = { e ->
                Log.e(TAG, "Failed to start camera for enrollment", e)
                runOnUiThread {
                    Toast.makeText(this, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_LONG).show()
                    stopCameraScanner()
                }
            }
        )
    }

    private fun stopCameraScanner() {
        isScannerActive = false
        cameraManager?.shutdown()
        cameraManager = null

        binding.previewView.visibility = View.GONE
        binding.viewVignette.visibility = View.GONE
        binding.layoutScannerControls.visibility = View.GONE
        binding.btnFlipCamera.visibility = View.GONE
        binding.layoutNameInput.visibility = View.VISIBLE
        binding.tvHeaderTitle.text = "DAFTARKAN WAJAH"
    }

    private fun processScannerFrame(imageProxy: ImageProxy) {
        if (!isScannerActive || isEnrolled || isProcessingFrame) {
            imageProxy.close()
            return
        }

        isProcessingFrame = true
        val bitmap = imageProxyToBitmap(imageProxy)
        imageProxy.close()

        if (bitmap == null) {
            isProcessingFrame = false
            return
        }

        lastCapturedBitmap = bitmap

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val pose = faceHelper.faceDetector.getFacePose(bitmap)
                lastValidPose = pose
                val now = System.currentTimeMillis()

                if (pose == null) {
                    withContext(Dispatchers.Main) {
                        binding.tvQualityProgress.text = "Kualitas: 0%"
                        binding.tvScannerPrompt.text = "Wajah tidak terdeteksi. Arahkan kamera ke wajah."
                    }
                    if (now - lastVoicePromptTime > 5000L) {
                        ttsManager?.speak("Wajah tidak terdeteksi. Harap posisikan kamera searah mata.")
                        lastVoicePromptTime = now
                    }
                    return@launch
                }

                if (!pose.isCentered) {
                    withContext(Dispatchers.Main) {
                        binding.tvQualityProgress.text = "Kualitas: 30%"
                        binding.tvScannerPrompt.text = "Wajah tidak di tengah lingkaran. Geser ponsel sedikit."
                    }
                    if (now - lastVoicePromptTime > 5000L) {
                        ttsManager?.speak("Wajah belum di tengah. Geser ponsel perlahan.")
                        lastVoicePromptTime = now
                    }
                    return@launch
                }

                if (pose.widthRatio < 0.25f) {
                    withContext(Dispatchers.Main) {
                        binding.tvQualityProgress.text = "Kualitas: 45%"
                        binding.tvScannerPrompt.text = "Terlalu jauh! Dekatkan ponsel ke wajah."
                    }
                    if (now - lastVoicePromptTime > 5000L) {
                        ttsManager?.speak("Terlalu jauh. Dekatkan ponsel ke wajah.")
                        lastVoicePromptTime = now
                    }
                    return@launch
                }

                if (pose.widthRatio > 0.85f) {
                    withContext(Dispatchers.Main) {
                        binding.tvQualityProgress.text = "Kualitas: 45%"
                        binding.tvScannerPrompt.text = "Terlalu dekat! Jauhkan ponsel sedikit."
                    }
                    if (now - lastVoicePromptTime > 5000L) {
                        ttsManager?.speak("Terlalu dekat. Jauhkan ponsel sedikit.")
                        lastVoicePromptTime = now
                    }
                    return@launch
                }

                val isStraight = abs(pose.yaw) < 15f && abs(pose.pitch) < 15f
                if (!isStraight) {
                    withContext(Dispatchers.Main) {
                        binding.tvQualityProgress.text = "Kualitas: 60%"
                        binding.tvScannerPrompt.text = "Harap menghadap lurus ke arah kamera."
                    }
                    if (now - lastVoicePromptTime > 5000L) {
                        ttsManager?.speak("Harap menghadap lurus ke depan.")
                        lastVoicePromptTime = now
                    }
                    return@launch
                }

                // Anti spoof check
                val spoofResult = faceHelper.faceSpoofDetector.detectSpoof(bitmap, pose.boundingBox)
                if (spoofResult != null && spoofResult.isSpoof) {
                    withContext(Dispatchers.Main) {
                        binding.tvQualityProgress.text = "Kualitas: 0%"
                        binding.tvScannerPrompt.text = "Wajah terdeteksi tidak asli."
                    }
                    return@launch
                }

                // Quality passed 100%! Auto-capture & enroll
                withContext(Dispatchers.Main) {
                    binding.tvQualityProgress.text = "Kualitas: 100%"
                    binding.tvScannerPrompt.text = "Wajah terdeteksi sempurna! Menyimpan..."
                }

                saveAndCompleteEnrollment(pose.croppedBitmap)

            } catch (e: Exception) {
                Log.e(TAG, "Frame analysis error during enrollment", e)
            } finally {
                isProcessingFrame = false
            }
        }
    }

    private fun manualSnapAndEnroll() {
        val pose = lastValidPose
        val frame = lastCapturedBitmap
        val cropToUse = pose?.croppedBitmap ?: frame

        if (cropToUse == null) {
            Toast.makeText(this, "Arahkan kamera ke wajah sebelum mengambil foto", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            saveAndCompleteEnrollment(cropToUse)
        }
    }

    private suspend fun saveAndCompleteEnrollment(faceBitmap: Bitmap) {
        if (isEnrolled) return
        isEnrolled = true

        val enrollResult = faceHelper.enrollPersonFromBitmap(targetPersonName, faceBitmap)
        withContext(Dispatchers.Main) {
            if (enrollResult.isSuccess) {
                vibrate()
                ttsManager?.speak("Wajah $targetPersonName berhasil didaftarkan!")
                Toast.makeText(this@AddFaceActivity, "Wajah $targetPersonName berhasil didaftarkan!", Toast.LENGTH_SHORT).show()
                showSuccessState()
            } else {
                isEnrolled = false
                val err = enrollResult.exceptionOrNull()?.message ?: "Gagal mengekstrak vektor wajah"
                Toast.makeText(this@AddFaceActivity, "Gagal: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enrollFromGalleryUri(name: String, uri: Uri) {
        lifecycleScope.launch {
            Toast.makeText(this@AddFaceActivity, "Memproses foto dari galeri...", Toast.LENGTH_SHORT).show()
            val result = faceHelper.enrollPerson(name, uri)
            if (result.isSuccess) {
                vibrate()
                ttsManager?.speak("Wajah $name berhasil didaftarkan!")
                Toast.makeText(this@AddFaceActivity, "Wajah $name berhasil didaftarkan!", Toast.LENGTH_SHORT).show()
                showSuccessState()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Gagal mendeteksi wajah pada foto"
                Toast.makeText(this@AddFaceActivity, "Gagal: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSuccessState() {
        isScannerActive = false
        cameraManager?.shutdown()
        cameraManager = null

        binding.previewView.visibility = View.GONE
        binding.viewVignette.visibility = View.GONE
        binding.layoutScannerControls.visibility = View.GONE
        binding.layoutNameInput.visibility = View.GONE
        binding.btnFlipCamera.visibility = View.GONE

        binding.tvSuccessTitle.text = "Wajah $targetPersonName Terdaftar!"
        binding.layoutSuccess.visibility = View.VISIBLE
        binding.tvHeaderTitle.text = "PENDAFTARAN SELESAI"
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                image.width,
                image.height,
                null,
            )
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 85, out)
            val imageBytes = out.toByteArray()
            val rawBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0 && rawBitmap != null) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "ImageProxy conversion error", e)
            null
        }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(80)
        }
    }

    override fun onDestroy() {
        cameraManager?.shutdown()
        ttsManager?.shutdown()
        super.onDestroy()
    }
}
