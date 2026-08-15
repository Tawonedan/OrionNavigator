package com.orion.app.face.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

abstract class BaseFaceDetector {
    data class FacePoseResult(
        val croppedBitmap: Bitmap,
        val boundingBox: Rect,
        val yaw: Float,
        val pitch: Float,
        val widthRatio: Float,
        val heightRatio: Float,
        val isCentered: Boolean,
    )

    abstract suspend fun getCroppedFace(imageUri: Uri): Result<Bitmap>

    abstract suspend fun getAllCroppedFaces(frameBitmap: Bitmap): List<Pair<Bitmap, Rect>>

    abstract suspend fun getFacePose(frameBitmap: Bitmap): FacePoseResult?

    protected fun getBitmapFromUri(context: Context, imageUri: Uri): Bitmap? {
        val imageInputStream =
            context.contentResolver.openInputStream(imageUri) ?: return null
        val imageBitmap = BitmapFactory.decodeStream(imageInputStream)
        imageInputStream.close()

        val exifInputStream =
            context.contentResolver.openInputStream(imageUri) ?: return imageBitmap
        val exifInterface = ExifInterface(exifInputStream)
        val rotatedBitmap =
            when (
                exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(imageBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(imageBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(imageBitmap, 270f)
                else -> imageBitmap
            }
        exifInputStream.close()
        return rotatedBitmap
    }

    protected fun rotateBitmap(
        source: Bitmap,
        degrees: Float,
    ): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, false)
    }

    protected fun validateRect(
        cameraFrameBitmap: Bitmap,
        boundingBox: Rect,
    ): Boolean =
        boundingBox.left >= 0 &&
                boundingBox.top >= 0 &&
                (boundingBox.left + boundingBox.width()) <= cameraFrameBitmap.width &&
                (boundingBox.top + boundingBox.height()) <= cameraFrameBitmap.height
}
