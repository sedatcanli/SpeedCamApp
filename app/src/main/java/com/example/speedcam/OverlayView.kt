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

    /** Referans cetvel: ekrandaki 1 pikselin kaç metre olduğu (görünüm koordinatı). */
    private var metersPerViewPx = 0f

    fun setScaleBar(mPerPx: Float) {
        if (mPerPx > 0f && mPerPx != metersPerViewPx) {
            metersPerViewPx = mPerPx
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
        drawScaleBar(canvas)
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

    private val scalePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.SQUARE
    }

    /** Sol altta dinamik referans cetvel (mesafe + FOV + zoom'a göre). */
    private fun drawScaleBar(canvas: Canvas) {
        val mpp = metersPerViewPx
        if (mpp <= 0f || width <= 0) return
        try {
            // ~120 px'e denk gelen "güzel" uzunluğu seç (1-2-5 kuralı)
            val raw = 120f * mpp
            val mag = Math.pow(10.0, Math.floor(Math.log10(raw.toDouble()))).toFloat()
            val norm = raw / mag
            val nice = when {
                norm >= 5f -> 5f * mag
                norm >= 2f -> 2f * mag
                else -> 1f * mag
            }
            val barPx = nice / mpp
            if (barPx < 30f || barPx > width - 48f) return
            val label = if (nice < 1f) "%d cm".format((nice * 100).toInt()) else {
                val whole = nice.toInt()
                if (nice == whole.toFloat()) "%d m".format(whole)
                else "%.1f m".format(nice)
            }
            val tw = textPaint.measureText(label)
            val x0 = 24f
            val y = height - 40f
            // Arka plan
            canvas.drawRect(x0 - 12f, y - 56f, x0 + barPx + tw + 32f, y + 16f, textBg)
            // Çizgi + uçlar
            canvas.drawLine(x0, y, x0 + barPx, y, scalePaint)
            canvas.drawLine(x0, y - 18f, x0, y + 18f, scalePaint)
            canvas.drawLine(x0 + barPx, y - 18f, x0 + barPx, y + 18f, scalePaint)
            canvas.drawText(label, x0 + barPx + 16f, y + 14f, textPaint)
        } catch (_: Exception) { }
    }
}
