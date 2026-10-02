package com.example.speedcam

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Kamera önizlemesi üstüne kutu + hız etiketi çizer. */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var tracks: List<TrackedObject> = emptyList()
    var speedUnit: String = SpeedUnit.KMH
    var showLabels: Boolean = true
    private var warnActive = false

    private val warnPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 14f
    }
    private val ghostPaint = Paint().apply {
        color = Color.argb(180, 170, 170, 170)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(24f, 18f), 0f)
    }

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val textBg = Paint().apply { color = Color.argb(200, 0, 0, 0) }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
    }

    fun setResults(t: List<TrackedObject>, unit: String, labels: Boolean) {
        tracks = t
        speedUnit = unit
        showLabels = labels
        postInvalidate()
    }

    fun setWarning(w: Boolean) {
        if (warnActive != w) {
            warnActive = w
            postInvalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (warnActive) {
            // Genel görüntünün etrafına kırmızı çerçeve
            val p = warnPaint.strokeWidth / 2f
            canvas.drawRect(p, p, width - p, height - p, warnPaint)
        }
        for (t in tracks) {
            val speed = SpeedUnit.toDisplay(t.speedMs, speedUnit)
            val max = SpeedUnit.toDisplay(t.maxSpeedMs, speedUnit)
            if (t.ghost) {
                // Kaybolan cisim: gri kesik çerçeve + son hız
                canvas.drawRect(t.box, ghostPaint)
            } else {
                // Hıza göre renk: yavaş yeşil -> hızlı kırmızı
                boxPaint.color = when {
                    t.speedMs < 2f -> Color.GREEN
                    t.speedMs < 8f -> Color.YELLOW
                    else -> Color.RED
                }
                canvas.drawRect(t.box, boxPaint)
            }
            val label = if (showLabels && t.label != null) "${t.label} " else ""
            val text = "$label#${t.id} %.1f / %.1f %s".format(speed, max, speedUnit)
            val tw = textPaint.measureText(text)
            val x = t.box.left.coerceAtLeast(0f)
            val y = (t.box.top - 12f).coerceAtLeast(50f)
            canvas.drawRect(x, y - 44f, x + tw + 16f, y + 8f, textBg)
            canvas.drawText(text, x + 8f, y, textPaint)
        }
    }
}
