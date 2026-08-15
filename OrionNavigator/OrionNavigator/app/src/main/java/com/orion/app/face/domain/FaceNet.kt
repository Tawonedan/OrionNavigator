package com.orion.app.face.domain

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import java.io.FileOutputStream

/**
 * FaceNet feature extractor running via PyTorch ExecuTorch.
 * Computes 512D face embeddings from 160x160 RGB face bitmaps.
 */
class FaceNet(private val context: Context) {
    private val imgSize = 160L
    private var module: Module

    init {
        module = Module.load(copyAndReturnPath("model.pte"))
    }

    suspend fun getFaceEmbedding(image: Bitmap): FloatArray =
        withContext(Dispatchers.Default) {
            return@withContext runFaceNet(convertBitmapToBuffer(image))[0]
        }

    private fun runFaceNet(inputs: FloatArray): Array<FloatArray> {
        val imageTensor = Tensor.fromBlob(
            inputs,
            longArrayOf(1, imgSize, imgSize, 3),
        )
        val outputTensor = module.forward(EValue.from(imageTensor))[0].toTensor()
        val embedding = outputTensor.dataAsFloatArray
        return arrayOf(embedding)
    }

    private fun convertBitmapToBuffer(image: Bitmap): FloatArray {
        val resizedBitmap = image.scale(160, 160, true)
        val pixels = IntArray(160 * 160)
        resizedBitmap.getPixels(pixels, 0, 160, 0, 0, 160, 160)
        val floats = FloatArray(160 * 160 * 3)
        var i = 0
        for (p in pixels) {
            floats[i++] = ((p shr 16) and 0xFF).toFloat()  // R
            floats[i++] = ((p shr 8) and 0xFF).toFloat()   // G
            floats[i++] = (p and 0xFF).toFloat()           // B
        }
        return floats
    }

    private fun copyAndReturnPath(assetsFilepath: String): String {
        val storageFile = File(context.filesDir, assetsFilepath)
        if (!storageFile.exists() || storageFile.length() == 0L) {
            storageFile.parentFile?.mkdirs()
            FileOutputStream(storageFile).use { outputStream ->
                context.assets.open(assetsFilepath).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return storageFile.absolutePath
    }
}
