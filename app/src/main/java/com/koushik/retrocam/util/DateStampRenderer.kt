package com.koushik.retrocam.util

import android.graphics.*
import com.koushik.retrocam.data.model.DateStampConfig
import java.text.SimpleDateFormat
import java.util.*

object DateStampRenderer {

    fun formatCurrentDate(config: DateStampConfig): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val shortYear = (year % 100).toString().padStart(2, '0')
        val month = (calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val day = calendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val minute = calendar.get(Calendar.MINUTE).toString().padStart(2, '0')

        return when (config.format) {
            "M D 'YY HH:mm" -> "$month $day '$shortYear  $hour:$minute"
            "YYYY-MM-DD" -> "$year-$month-$day"
            else -> "'$shortYear  $month  $day"
        }
    }

    fun drawDateStamp(
        canvas: Canvas,
        width: Int,
        height: Int,
        config: DateStampConfig
    ) {
        if (!config.enabled) return

        val text = formatCurrentDate(config)
        val parsedColor = try {
            Color.parseColor(config.color)
        } catch (e: Exception) {
            Color.parseColor("#FF9A2E")
        }

        val textSize = (height * 0.028f).coerceAtLeast(18f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parsedColor
            this.textSize = textSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            style = Paint.Style.FILL
            letterSpacing = 0.08f
            // Subtle vintage glow
            setShadowLayer(textSize * 0.25f, 0f, 0f, parsedColor)
        }

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val margin = height * 0.035f

        val (x, y) = when (config.position) {
            "bottom_left" -> {
                paint.textAlign = Paint.Align.LEFT
                Pair(margin, height - margin)
            }
            else -> {
                paint.textAlign = Paint.Align.RIGHT
                Pair(width - margin, height - margin)
            }
        }

        canvas.drawText(text, x, y, paint)
    }
}

