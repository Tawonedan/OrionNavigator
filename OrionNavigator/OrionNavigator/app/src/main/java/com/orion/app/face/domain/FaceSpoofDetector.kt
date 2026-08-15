package com.orion.app.face.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import kotlin.math.exp
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Dual-scale Silent-Face Anti-Spoofing (FASNet) via TensorFlow Lite.
 * Evaluates whether a face is a real live person or a 2D presentation attack (photo/screen).
 */
class FaceSpoofDetector(context: Context) {
    data class FaceSpoofResult(
        val isSpoof: Boolean,
        val score: Float,
        val timeMillis: Long,
    )

    private val scale1 = 2.7f
    private val scale2 = 4.0f
    private val inputImageDim = 80
    private val outputDim = 3

    private var firstModelInterpreter: Interpreter
    private var secondModelInterpreter: Interpreter
    private val imageTensorProcessor =
        ImageProcessor
            .Builder()
            .add(CastOp(DataType.FLOAT32))
            .build()

    init {
        val interpreterOptions = Interpreter.Options().apply {
            numThreads = 4
        }
        firstModelInterpreter = Interpreter(
            FileUtil.loadMappedFile(context, "spoof_model_scale_2_7.tflite"),
            interpreterOptions,
        )
        secondModelInterpreter = Interpreter(
            FileUtil.loadMappedFile(context, "spoof_model_scale_4_0.tflite"),
            interpreterOptions,
        )
    }

    suspend fun detectSpoof(
        frameImage: Bitmap,
        faceRect: Rect,
    ): FaceSpoofResult =
        withContext(Dispatchers.Default) {
            try {
                val croppedImage1 = crop(
                    origImage = frameImage,
                    bbox = faceRect,
                    bboxScale = scale1,
                    targetWidth = inputImageDim,
                    targetHeight = inputImageDim,
                )
                toBgrInPlace(croppedImage1)

                val croppedImage2 = crop(
                    origImage = frameImage,
                    bbox = faceRect,
                    bboxScale = scale2,
                    targetWidth = inputImageDim,
                    targetHeight = inputImageDim,
                )
                toBgrInPlace(croppedImage2)

                val input1 = imageTensorProcessor.process(TensorImage.fromBitmap(croppedImage1)).buffer
                val input2 = imageTensorProcessor.process(TensorImage.fromBitmap(croppedImage2)).buffer
                val output1 = arrayOf(FloatArray(outputDim))
                val output2 = arrayOf(FloatArray(outputDim))

                val time = measureTime {
                    firstModelInterpreter.run(input1, output1)
                    secondModelInterpreter.run(input2, output2)
                }.toLong(DurationUnit.MILLISECONDS)

                val output = softMax(output1[0]).zip(softMax(output2[0])).map {
                    (it.first + it.second)
                }
                val label = output.indexOf(output.maxOrNull() ?: 0f)
                val isSpoof = label != 1
                val score = (output.getOrNull(label) ?: 0f) / 2f

                FaceSpoofResult(isSpoof = isSpoof, score = score, timeMillis = time)
            } catch (e: Exception) {
                FaceSpoofResult(isSpoof = false, score = 0.5f, timeMillis = 0L)
            }
        }

    private fun toBgrInPlace(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            pixels[i] = Color.rgb(b, g, r)
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun softMax(x: FloatArray): FloatArray {
        val exp = x.map { exp(it) }
        val expSum = exp.sum().coerceAtLeast(1e-6f)
        return exp.map { it / expSum }.toFloatArray()
    }

    private fun crop(
        origImage: Bitmap,
        bbox: Rect,
        bboxScale: Float,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap {
        val srcWidth = origImage.width
        val srcHeight = origImage.height
        val scaledBox = getScaledBox(srcWidth, srcHeight, bbox, bboxScale)
        val croppedBitmap =
            Bitmap.createBitmap(
                origImage,
                scaledBox.left,
                scaledBox.top,
                scaledBox.width(),
                scaledBox.height(),
            )
        return Bitmap.createScaledBitmap(croppedBitmap, targetWidth, targetHeight, true)
    }

    private fun getScaledBox(
        srcWidth: Int,
        srcHeight: Int,
        box: Rect,
        bboxScale: Float,
    ): Rect {
        val x = box.left
        val y = box.top
        val w = box.width().coerceAtLeast(1)
        val h = box.height().coerceAtLeast(1)
        val scale = floatArrayOf((srcHeight - 1f) / h, (srcWidth - 1f) / w, bboxScale).minOrNull() ?: bboxScale
        val newWidth = w * scale
        val newHeight = h * scale
        val centerX = w / 2f + x
        val centerY = h / 2f + y
        var topLeftX = centerX - newWidth / 2f
        var topLeftY = centerY - newHeight / 2f
        var bottomRightX = centerX + newWidth / 2f
        var bottomRightY = centerY + newHeight / 2f
        if (topLeftX < 0) {
            bottomRightX -= topLeftX
            topLeftX = 0f
        }
        if (topLeftY < 0) {
            bottomRightY -= topLeftY
            topLeftY = 0f
        }
        if (bottomRightX > srcWidth - 1) {
            topLeftX -= (bottomRightX - (srcWidth - 1))
            bottomRightX = (srcWidth - 1).toFloat()
        }
        if (bottomRightY > srcHeight - 1) {
            topLeftY -= (bottomRightY - (srcHeight - 1))
            bottomRightY = (srcHeight - 1).toFloat()
        }
        val safeLeft = topLeftX.toInt().coerceIn(0, srcWidth - 1)
        val safeTop = topLeftY.toInt().coerceIn(0, srcHeight - 1)
        val safeRight = bottomRightX.toInt().coerceIn(safeLeft + 1, srcWidth)
        val safeBottom = bottomRightY.toInt().coerceIn(safeTop + 1, srcHeight)
        return Rect(safeLeft, safeTop, safeRight, safeBottom)
    }
}
