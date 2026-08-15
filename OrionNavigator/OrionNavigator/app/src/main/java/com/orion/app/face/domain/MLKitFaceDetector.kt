package com.orion.app.face.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MLKitFaceDetector(
    private val context: Context,
) : BaseFaceDetector() {

    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()
    private val realTimeFaceDetector = FaceDetection.getClient(realTimeOpts)

    private val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .build()
    private val highAccuracyFaceDetector = FaceDetection.getClient(highAccuracyOpts)

    override suspend fun getCroppedFace(imageUri: Uri): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            val imageBitmap =
                getBitmapFromUri(context, imageUri) ?: return@withContext Result.failure(
                    Exception("Failed to decode image from URI")
                )

            val faces = Tasks.await(highAccuracyFaceDetector.process(InputImage.fromBitmap(imageBitmap, 0)))
            if (faces.size > 1) {
                return@withContext Result.failure(Exception("Multiple faces detected in image"))
            } else if (faces.isEmpty()) {
                return@withContext Result.failure(Exception("No face detected in image"))
            } else {
                val rect = faces[0].boundingBox
                val safeRect = clampRect(imageBitmap, rect)
                if (safeRect.width() > 10 && safeRect.height() > 10) {
                    val croppedBitmap =
                        Bitmap.createBitmap(
                            imageBitmap,
                            safeRect.left,
                            safeRect.top,
                            safeRect.width(),
                            safeRect.height(),
                        )
                    return@withContext Result.success(croppedBitmap)
                } else {
                    return@withContext Result.failure(Exception("Invalid face bounding box"))
                }
            }
        }

    override suspend fun getAllCroppedFaces(frameBitmap: Bitmap): List<Pair<Bitmap, Rect>> =
        withContext(Dispatchers.IO) {
            val faces = Tasks.await(realTimeFaceDetector.process(
                InputImage.fromBitmap(frameBitmap, 0)
            ))
            return@withContext faces
                .map { clampRect(frameBitmap, it.boundingBox) }
                .filter { it.width() > 10 && it.height() > 10 }
                .map { rect ->
                    val croppedBitmap =
                        Bitmap.createBitmap(
                            frameBitmap,
                            rect.left,
                            rect.top,
                            rect.width(),
                            rect.height(),
                        )
                    Pair(croppedBitmap, rect)
                }
        }

    override suspend fun getFacePose(frameBitmap: Bitmap): FacePoseResult? =
        withContext(Dispatchers.IO) {
            val faces = Tasks.await(realTimeFaceDetector.process(InputImage.fromBitmap(frameBitmap, 0)))
            val validFace = faces.firstOrNull() ?: return@withContext null
            val rect = clampRect(frameBitmap, validFace.boundingBox)
            if (rect.width() <= 10 || rect.height() <= 10) return@withContext null

            val croppedBitmap = Bitmap.createBitmap(
                frameBitmap,
                rect.left,
                rect.top,
                rect.width(),
                rect.height()
            )

            val widthRatio = rect.width().toFloat() / frameBitmap.width.toFloat()
            val heightRatio = rect.height().toFloat() / frameBitmap.height.toFloat()

            val centerX = rect.centerX().toFloat()
            val centerY = rect.centerY().toFloat()
            val frameCenterX = frameBitmap.width / 2f
            val frameCenterY = frameBitmap.height / 2f
            val isCentered = abs(centerX - frameCenterX) < (frameBitmap.width * 0.25f) &&
                    abs(centerY - frameCenterY) < (frameBitmap.height * 0.25f)

            return@withContext FacePoseResult(
                croppedBitmap = croppedBitmap,
                boundingBox = rect,
                yaw = validFace.headEulerAngleY,
                pitch = validFace.headEulerAngleX,
                widthRatio = widthRatio,
                heightRatio = heightRatio,
                isCentered = isCentered
            )
        }

    private fun clampRect(bitmap: Bitmap, rect: Rect): Rect {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val right = rect.right.coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
        return Rect(left, top, right, bottom)
    }
}
