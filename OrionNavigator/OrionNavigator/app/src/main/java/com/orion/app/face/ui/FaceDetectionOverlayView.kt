package com.orion.app.face.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.orion.app.face.domain.FaceRecognitionHelper.FaceRecognitionResult

class FaceDetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var results: List<FaceRecognitionResult> = emptyList()
    private var sourceImageWidth: Int = 1
    private var sourceImageHeight: Int = 1

    private val boxPaintRecognized = Paint().apply {
        color = Color.parseColor("#00E676") // Green
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val boxPaintUnknown = Paint().apply {
        color = Color.parseColor("#FF9100") // Orange
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val boxPaintSpoof = Paint().apply {
        color = Color.parseColor("#FF1744") // Red
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val subTextPaint = Paint().apply {
        color = Color.parseColor("#EEEEEE")
        textSize = 26f
        isAntiAlias = true
    }

    private var isFrontCamera: Boolean = false

    fun setResults(
        recognitionResults: List<FaceRecognitionResult>,
        imageWidth: Int,
        imageHeight: Int,
        isFrontCamera: Boolean = false,
    ) {
        this.results = recognitionResults
        this.sourceImageWidth = imageWidth
        this.sourceImageHeight = imageHeight
        this.isFrontCamera = isFrontCamera
        postInvalidate()
    }

    fun clear() {
        this.results = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (results.isEmpty() || sourceImageWidth <= 0 || sourceImageHeight <= 0) return

        val scaleX = width.toFloat() / sourceImageWidth.toFloat()
        val scaleY = height.toFloat() / sourceImageHeight.toFloat()

        for (result in results) {
            val rect = result.boundingBox
            val scaledRect = if (isFrontCamera) {
                RectF(
                    width - (rect.right * scaleX),
                    rect.top * scaleY,
                    width - (rect.left * scaleX),
                    rect.bottom * scaleY,
                )
            } else {
                RectF(
                    rect.left * scaleX,
                    rect.top * scaleY,
                    rect.right * scaleX,
                    rect.bottom * scaleY,
                )
            }

            val boxPaint = when {
                result.isSpoof -> boxPaintSpoof
                result.isRecognized -> boxPaintRecognized
                else -> boxPaintUnknown
            }

            // Draw bounding box
            canvas.drawRoundRect(scaledRect, 16f, 16f, boxPaint)

            // Label text
            val label = when {
                result.isSpoof -> "SPOOF / PALSU"
                result.isRecognized -> result.personName
                else -> "Tidak Dikenal"
            }

            val subLabel = when {
                result.isSpoof -> "Bukan wajah asli"
                result.isRecognized -> "Kecocokan: ${(result.confidence * 100).toInt()}%"
                else -> "Belum terdaftar"
            }

            val textWidth = textPaint.measureText(label).coerceAtLeast(subTextPaint.measureText(subLabel))
            val badgeHeight = 72f
            val badgeWidth = textWidth + 32f

            val badgeRect = RectF(
                scaledRect.left,
                (scaledRect.top - badgeHeight - 8f).coerceAtLeast(8f),
                scaledRect.left + badgeWidth,
                (scaledRect.top - 8f).coerceAtLeast(badgeHeight + 8f),
            )

            bgPaint.color = when {
                result.isSpoof -> Color.parseColor("#CCB71C1C")
                result.isRecognized -> Color.parseColor("#CC1B5E20")
                else -> Color.parseColor("#CCBF360C")
            }

            canvas.drawRoundRect(badgeRect, 12f, 12f, bgPaint)
            canvas.drawText(label, badgeRect.left + 16f, badgeRect.top + 34f, textPaint)
            canvas.drawText(subLabel, badgeRect.left + 16f, badgeRect.top + 62f, subTextPaint)
        }
    }
}
