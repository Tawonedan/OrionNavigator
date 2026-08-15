package com.orion.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.orion.app.R
import com.orion.app.camera.CameraManager
import com.orion.app.databinding.ActivityFaceRecognitionBinding
import com.orion.app.face.domain.FaceRecognitionHelper
import com.orion.app.face.domain.FaceRecognitionHelper.FaceRecognitionResult
import com.orion.app.tts.TTSManager
import com.orion.app.utils.PermissionHelper
import com.orion.app.voice.GeminiCommandProcessor
import com.orion.app.voice.ListeningDialogHelper
import com.orion.app.voice.VoiceCommandManager
import com.orion.app.voice.VoiceIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FaceRecognitionActivity - On-device real-time face recognition for Tunanetra
 * 
 * Features:
 * - Live CameraX stream with MLKit Face Detection
 * - PyTorch ExecuTorch FaceNet embedding extraction
 * - Silent-Face Anti-Spoofing verification
 * - ObjectBox Vector DB similarity search (HNSW Cosine)
 * - Automatic TTS announcement with debounce cooldown
 * - Haptic pulse when a known person is recognized
 * - Triple-layer voice command integration ("Hello Orion", "Siapa ini?")
 */
@ExperimentalGetImage
class FaceRecognitionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FaceRecognitionActivity"
        private const val DEBOUNCE_COOLDOWN_MS = 4000L
    }

    private lateinit var binding: ActivityFaceRecognitionBinding
    private lateinit var permissionHelper: PermissionHelper
    private var cameraManager: CameraManager? = null
    private var faceHelper: FaceRecognitionHelper? = null
    private var ttsManager: TTSManager? = null
    private var vibrator: Vibrator? = null

    private var frameSkipCounter = 0
    private val frameSkipInterval = 4
    @Volatile private var isProcessingFrame = false
    @Volatile private var isActivityDestroyed = false

    private val lastAnnouncedMap = mutableMapOf<String, Long>()
    private var lastRecognizedResults: List<FaceRecognitionResult> = emptyList()

    // Voice Command
    private var voiceCommandManager: VoiceCommandManager? = null
    private var geminiCommandProcessor: GeminiCommandProcessor? = null
    private var pulseAnimation: Animation? = null
    private var toneGenerator: ToneGenerator? = null
    private var listeningDialog: ListeningDialogHelper? = null
    private var isWakeWordEnabled = false

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceCommandManager?.startWakeWordMode()
        } else {
            ttsManager?.speak("Izin mikrofon diperlukan untuk perintah suara")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaceRecognitionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        toneGenerator = try { ToneGenerator(AudioManager.STREAM_MUSIC, 100) } catch (e: Exception) { null }

        initializeComponents()
        setupClickListeners()
        initVoiceCommand()
        checkPermissionAndStart()
    }

    private fun initializeComponents() {
        ttsManager = TTSManager(this)
        faceHelper = FaceRecognitionHelper(this)
        permissionHelper = PermissionHelper(this) { isGranted ->
            if (isGranted) {
                showCameraView()
                startCamera()
            } else {
                showPermissionDeniedView()
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabBack.setOnClickListener {
            vibrate()
            ttsManager?.speak("Kembali ke menu utama")
            finish()
        }

        binding.fabFlipCamera.setOnClickListener {
            vibrate()
            val isFront = cameraManager?.flipCamera { e ->
                Toast.makeText(this, "Gagal membalik kamera: ${e.message}", Toast.LENGTH_SHORT).show()
            } ?: false
            val msg = if (isFront) "Kamera depan aktif" else "Kamera belakang aktif"
            ttsManager?.speak(msg)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.fabManageFaces.setOnClickListener {
            vibrate()
            try {
                val intent = Intent(this, ManageFacesActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open ManageFacesActivity", e)
                Toast.makeText(this, "Gagal membuka daftar wajah: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.fabIdentify.setOnClickListener {
            vibrate()
            identifyCurrentFaces()
        }

        binding.fabSound.setOnClickListener {
            vibrate()
            ttsManager?.let {
                val isEnabled = it.toggleSound()
                val icon = if (isEnabled) R.drawable.ic_volume_on else R.drawable.ic_volume_off
                binding.fabSound.setImageResource(icon)
                val status = if (isEnabled) "Suara diaktifkan" else "Suara dinonaktifkan"
                Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
                if (isEnabled) it.speak(status)
            }
        }

        binding.btnWakeWord.setOnClickListener {
            vibrate()
            toggleWakeWord()
        }

        binding.fabMic.setOnClickListener {
            vibrate()
            startDirectListening()
        }

        binding.btnRequestPermission.setOnClickListener {
            permissionHelper.requestCameraPermission()
        }
    }

    private fun identifyCurrentFaces() {
        val results = lastRecognizedResults
        if (results.isEmpty()) {
            ttsManager?.speak("Tidak ada wajah yang terlihat di depan kamera. Arahkan kamera lurus ke depan.")
            return
        }

        val recognizedNames = results.filter { it.isRecognized }.map { it.personName }
        val unknownCount = results.count { !it.isRecognized }

        if (recognizedNames.isNotEmpty()) {
            val namesStr = recognizedNames.joinToString(", ")
            val msg = if (recognizedNames.size == 1) {
                "Ada $namesStr di depan Anda."
            } else {
                "Ada beberapa orang: $namesStr di depan Anda."
            }
            ttsManager?.speak(msg)
            vibrate()
        } else if (unknownCount > 0) {
            ttsManager?.speak("Ada orang di depan Anda, namun wajahnya belum terdaftar di aplikasi.")
        }
    }

    private fun checkPermissionAndStart() {
        if (permissionHelper.hasCameraPermission()) {
            showCameraView()
            startCamera()
        } else {
            showPermissionDeniedView()
            permissionHelper.requestCameraPermission()
        }
    }

    private fun showCameraView() {
        binding.previewView.visibility = View.VISIBLE
        binding.faceOverlayView.visibility = View.VISIBLE
        binding.layoutPermissionDenied.visibility = View.GONE
    }

    private fun showPermissionDeniedView() {
        binding.previewView.visibility = View.GONE
        binding.faceOverlayView.visibility = View.GONE
        binding.layoutPermissionDenied.visibility = View.VISIBLE
    }

    private fun startCamera() {
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView,
        )

        cameraManager?.startCamera(
            onAnalyze = { imageProxy ->
                processImageProxy(imageProxy)
            },
            onError = { e ->
                Log.e(TAG, "Camera error", e)
            }
        )
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isActivityDestroyed) {
            imageProxy.close()
            return
        }

        frameSkipCounter++
        if (frameSkipCounter % frameSkipInterval != 0 || isProcessingFrame) {
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

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val (_, results) = faceHelper?.processFrame(bitmap) ?: Pair(null, emptyList())
                lastRecognizedResults = results

                withContext(Dispatchers.Main) {
                    if (!isActivityDestroyed) {
                        handleRecognitionResults(results, bitmap.width, bitmap.height)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Face processing error", e)
            } finally {
                isProcessingFrame = false
            }
        }
    }

    private fun handleRecognitionResults(
        results: List<FaceRecognitionResult>,
        imageWidth: Int,
        imageHeight: Int,
    ) {
        val isFront = cameraManager?.isFrontFacing() == true
        binding.faceOverlayView.setResults(results, imageWidth, imageHeight, isFront)

        if (results.isEmpty()) {
            binding.tvPersonName.visibility = View.GONE
            binding.tvStatus.text = "Mengarahkan kamera untuk mengenali wajah..."
            return
        }

        val recognizedPerson = results.firstOrNull { it.isRecognized }
        if (recognizedPerson != null) {
            val name = recognizedPerson.personName
            val confidence = (recognizedPerson.confidence * 100).toInt()
            binding.tvPersonName.text = "$name ($confidence%)"
            binding.tvPersonName.visibility = View.VISIBLE
            binding.tvStatus.text = "Wajah dikenali: $name"

            // Announce via TTS if cooldown has expired
            val now = System.currentTimeMillis()
            val lastAnnounced = lastAnnouncedMap[name] ?: 0L
            if (now - lastAnnounced > DEBOUNCE_COOLDOWN_MS) {
                lastAnnouncedMap[name] = now
                ttsManager?.speak("Ada $name di depan Anda")
                vibrate()
            }
        } else {
            binding.tvPersonName.visibility = View.GONE
            binding.tvStatus.text = "Wajah terdeteksi (Belum terdaftar)"
        }
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

            val yuvImage = android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                image.width,
                image.height,
                null,
            )
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 85, out)
            val imageBytes = out.toByteArray()
            val rawBitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

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

    private fun initVoiceCommand() {
        geminiCommandProcessor = GeminiCommandProcessor(lifecycleScope)
        listeningDialog = ListeningDialogHelper(this)

        pulseAnimation = AlphaAnimation(1f, 0.4f).apply {
            duration = 600
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }

        voiceCommandManager = VoiceCommandManager(
            context = this,
            geminiProcessor = geminiCommandProcessor,
            listener = object : VoiceCommandManager.OnCommandListener {
                override fun onCommandRecognized(spokenText: String, intent: VoiceIntent) {
                    runOnUiThread {
                        listeningDialog?.dismiss()
                        handleVoiceIntent(intent, spokenText)
                    }
                }

                override fun onWakeWordDetected() {
                    runOnUiThread {
                        binding.fabMic.setImageResource(R.drawable.ic_mic_active)
                        binding.fabMic.startAnimation(pulseAnimation)
                        listeningDialog?.show(
                            statusText = "Mendengarkan...",
                            hintText = "Ucapkan \"Siapa ini?\" atau perintah lain",
                        )
                    }
                }

                override fun onListeningStarted() {
                    runOnUiThread {
                        binding.fabMic.setImageResource(R.drawable.ic_mic_active)
                        binding.fabMic.startAnimation(pulseAnimation)
                    }
                }

                override fun onListeningStopped() {
                    runOnUiThread {
                        binding.fabMic.clearAnimation()
                        binding.fabMic.setImageResource(R.drawable.ic_mic)
                        listeningDialog?.dismiss()
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        ttsManager?.speak(message)
                    }
                }
            }
        )

        voiceCommandManager?.setAllowedIntents(setOf(
            VoiceIntent.IDENTIFY_PERSON,
            VoiceIntent.OPEN_FACE_RECOGNITION,
            VoiceIntent.TOGGLE_SOUND_ON,
            VoiceIntent.TOGGLE_SOUND_OFF,
            VoiceIntent.GO_BACK,
            VoiceIntent.HELP,
        ))
    }

    private fun handleVoiceIntent(intent: VoiceIntent, spokenText: String = "") {
        vibrate()
        when (intent) {
            VoiceIntent.IDENTIFY_PERSON -> {
                identifyCurrentFaces()
            }
            VoiceIntent.OPEN_FACE_RECOGNITION -> {
                ttsManager?.speak("Anda sudah berada di fitur kenali wajah.")
            }
            VoiceIntent.TOGGLE_SOUND_ON -> {
                ttsManager?.setSoundEnabled(true)
                binding.fabSound.setImageResource(R.drawable.ic_volume_on)
                ttsManager?.speak("Suara diaktifkan")
            }
            VoiceIntent.TOGGLE_SOUND_OFF -> {
                ttsManager?.speak("Suara dinonaktifkan")
                ttsManager?.setSoundEnabled(false)
                binding.fabSound.setImageResource(R.drawable.ic_volume_off)
            }
            VoiceIntent.GO_BACK -> {
                ttsManager?.speak("Kembali ke menu utama")
                finish()
            }
            VoiceIntent.HELP -> {
                ttsManager?.speak("Perintah yang tersedia: Siapa ini, untuk mengenali orang di depan Anda. Balik kamera, suara mati, suara hidup, dan kembali.")
            }
            else -> {
                if (spokenText.contains("daftar", ignoreCase = true) || spokenText.contains("tambah", ignoreCase = true)) {
                    val name = spokenText.replace(Regex("(?i)(daftar|tambahkan|tambah|wajah|orang|baru)"), "").trim()
                    val addIntent = Intent(this, AddFaceActivity::class.java).apply {
                        if (name.isNotBlank()) putExtra(AddFaceActivity.EXTRA_PERSON_NAME, name)
                    }
                    startActivity(addIntent)
                } else if (spokenText.contains("siapa", ignoreCase = true) || spokenText.contains("orang", ignoreCase = true)) {
                    identifyCurrentFaces()
                } else if (spokenText.contains("kelola", ignoreCase = true) || spokenText.contains("daftar orang", ignoreCase = true)) {
                    startActivity(Intent(this, ManageFacesActivity::class.java))
                } else if (spokenText.contains("balik kamera", ignoreCase = true) ||
                    spokenText.contains("ganti kamera", ignoreCase = true) ||
                    spokenText.contains("putar kamera", ignoreCase = true) ||
                    spokenText.contains("kamera depan", ignoreCase = true) ||
                    spokenText.contains("kamera belakang", ignoreCase = true)) {
                    val isFront = cameraManager?.flipCamera() ?: false
                    val msg = if (isFront) "Kamera depan aktif" else "Kamera belakang aktif"
                    ttsManager?.speak(msg)
                } else {
                    ttsManager?.speak("Perintah tidak dikenali di halaman kenali wajah.")
                }
            }
        }
    }

    private fun toggleWakeWord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        isWakeWordEnabled = !isWakeWordEnabled
        if (isWakeWordEnabled) {
            voiceCommandManager?.startWakeWordMode()
            binding.btnWakeWord.setImageResource(R.drawable.ic_wakeword)
            ttsManager?.speak("Hello Orion diaktifkan")
        } else {
            voiceCommandManager?.stopWakeWordMode()
            binding.btnWakeWord.setImageResource(R.drawable.ic_wakeword_off)
            ttsManager?.speak("Hello Orion dinonaktifkan")
        }
    }

    private fun startDirectListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        listeningDialog?.show(
            statusText = "Mendengarkan...",
            hintText = "Ucapkan perintah Anda...",
        )
        voiceCommandManager?.startListening()
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(80)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isWakeWordEnabled && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            voiceCommandManager?.startWakeWordMode()
        }
    }

    override fun onPause() {
        super.onPause()
        voiceCommandManager?.stopWakeWordMode()
    }

    override fun onDestroy() {
        isActivityDestroyed = true
        cameraManager?.shutdown()
        ttsManager?.shutdown()
        voiceCommandManager?.destroy()
        toneGenerator?.release()
        super.onDestroy()
    }
}
