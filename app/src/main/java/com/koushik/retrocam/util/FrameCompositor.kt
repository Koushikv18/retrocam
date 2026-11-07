package com.koushik.retrocam.util

import android.graphics.*
import kotlin.math.roundToInt

object FrameCompositor {

    fun applyFrame(source: Bitmap, frameType: String): Bitmap {
        return when (frameType) {
            "polaroid" -> createPolaroidFrame(source)
            "film_strip" -> createFilmStripFrame(source)
            "slide_mount" -> createSlideMountFrame(source)
            "rounded_black" -> createRoundedFrame(source, Color.BLACK)
            "rounded_white" -> createRoundedFrame(source, Color.parseColor("#F4F0E6"))
            else -> source
        }
    }

    private fun createPolaroidFrame(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val sideBorder = (w * 0.055f).roundToInt().coerceAtLeast(12)
        val topBorder = sideBorder
        val bottomChin = (w * 0.22f).roundToInt().coerceAtLeast(48)

        val outW = w + sideBorder * 2
        val outH = h + topBorder + bottomChin

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Polaroid creamy vintage white background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#F5F2EB")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), bgPaint)

        // Draw photo with slight inner shadow
        canvas.drawBitmap(source, sideBorder.toFloat(), topBorder.toFloat(), null)

        val borderPaint = Paint().apply {
            color = Color.argb(20, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(
            sideBorder.toFloat(),
            topBorder.toFloat(),
            (sideBorder + w).toFloat(),
            (topBorder + h).toFloat(),
            borderPaint
        )

        return output
    }

    private fun createFilmStripFrame(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val border = (h * 0.08f).roundToInt().coerceAtLeast(24)

        val outW = w
        val outH = h + border * 2

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Black film base
        canvas.drawColor(Color.parseColor("#0C0C0C"))

        // Draw photo
        canvas.drawBitmap(source, 0f, border.toFloat(), null)

        // Draw sprocket holes along top and bottom
        val holeWidth = border * 0.45f
        val holeHeight = border * 0.35f
        val holeSpacing = border * 0.75f
        val holeRadius = border * 0.08f

        val holePaint = Paint().apply {
            color = Color.parseColor("#1E1E1E")
            style = Paint.Style.FILL
        }

        var x = holeSpacing * 0.5f
        while (x < outW) {
            // Top hole
            val topRect = RectF(
                x,
                border * 0.3f,
                x + holeWidth,
                border * 0.3f + holeHeight
            )
            canvas.drawRoundRect(topRect, holeRadius, holeRadius, holePaint)

            // Bottom hole
            val bottomRect = RectF(
                x,
                outH - border * 0.65f,
                x + holeWidth,
                outH - border * 0.65f + holeHeight
            )
            canvas.drawRoundRect(bottomRect, holeRadius, holeRadius, holePaint)

            x += holeWidth + holeSpacing
        }

        // Film edge markings
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFA500")
            textSize = border * 0.28f
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText("RETRO 400", border.toFloat(), border * 0.85f, textPaint)
        canvas.drawText("24A", outW - border * 2.5f, border * 0.85f, textPaint)

        return output
    }

    private fun createSlideMountFrame(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val border = (w * 0.07f).roundToInt().coerceAtLeast(20)

        val outW = w + border * 2
        val outH = h + border * 2

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Cardboard slide mount color
        canvas.drawColor(Color.parseColor("#EAE6DF"))
        canvas.drawBitmap(source, border.toFloat(), border.toFloat(), null)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#666666")
            textSize = border * 0.32f
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText("35mm COLOR SLIDE", border.toFloat(), outH - border * 0.35f, textPaint)

        return output
    }

    private fun createRoundedFrame(source: Bitmap, frameColor: Int): Bitmap {
        val w = source.width
        val h = source.height
        val border = (w * 0.04f).roundToInt().coerceAtLeast(16)

        val outW = w + border * 2
        val outH = h + border * 2

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(frameColor)

        val cornerRadius = border * 1.5f

        val photoBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val photoCanvas = Canvas(photoBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        photoCanvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        photoCanvas.drawBitmap(source, 0f, 0f, paint)

        canvas.drawBitmap(photoBitmap, border.toFloat(), border.toFloat(), null)

        return output
    }
}

