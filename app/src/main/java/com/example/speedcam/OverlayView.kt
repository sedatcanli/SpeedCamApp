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

    // ---- Sürüklenebilir cetvel + uzaklık birimi ----
    var onRulerTap: (() -> Unit)? = null
    private val calibRef by lazy { CalibrationManager(context.applicationContext) }
    private val unitRect = android.graphics.RectF()
    private var lastUnitW = 0f
    private var lastUnitH = 0f
    private var dragging = false
    private var dragId = -1
    private var downX = 0f
    private var downY = 0f
    private var dragOffX = 0f
    private var dragOffY = 0f
    private var dragMoved = false

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                if (unitRect.contains(e.x, e.y)) {
                    dragging = true
                    dragMoved = false
                    dragId = e.getPointerId(0)
                    downX = e.x
                    downY = e.y
                    dragOffX = e.x - unitRect.left
                    dragOffY = e.y - unitRect.top
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val idx = e.findPointerIndex(dragId)
                    if (idx >= 0) {
                        val x = e.getX(idx)
                        val y = e.getY(idx)
                        if (kotlin.math.hypot(
                                (x - downX).toDouble(), (y - downY).toDouble()
                            ) > 12
                        ) dragMoved = true
                        if (dragMoved && width > 0 && height > 0) {
                            val denomW = (width - lastUnitW).coerceAtLeast(1f)
                            val denomH = (height - lastUnitH).coerceAtLeast(1f)
                            calibRef.rulerFx =
                                ((x - dragOffX) / denomW).coerceIn(0f, 1f)
                            calibRef.rulerFy =
                                ((y - dragOffY) / denomH).coerceIn(0f, 1f)
                            invalidate()
                        }
                    }
                    return true
                }
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    if (!dragMoved) {
                        try { onRulerTap?.invoke() } catch (_: Exception) { }
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(e)
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

    /** Serbest sürüklenebilir cetvel + uzaklık birimi (konum hatırlanır). */
    private fun drawScaleBar(canvas: Canvas) {
        val mpp = metersPerViewPx
        if (mpp <= 0f || width <= 0 || height <= 0) {
            unitRect.setEmpty()
            return
        }
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
            if (barPx < 30f) {
                unitRect.setEmpty()
                return
            }
            val scaleLabel = if (nice < 1f) "%d cm".format((nice * 100).toInt()) else {
                val whole = nice.toInt()
                if (nice == whole.toFloat()) "%d m".format(whole)
                else "%.1f m".format(nice)
            }
            val dist = try { calibRef.distanceM } catch (_: Exception) { 0f }
            val distLabel = if (dist < 10f) "Uzak: %.1f m".format(dist)
            else "Uzak: %.0f m".format(dist)
            val twScale = textPaint.measureText(scaleLabel)
            val twDist = textPaint.measureText(distLabel)
            val pad = 14f
            val rowH = 56f
            val unitW = pad * 2 + maxOf(barPx + 16f + twScale, twDist)
            val unitH = pad * 2 + rowH * 2
            val fx = try { calibRef.rulerFx } catch (_: Exception) { 0f }
            val fy = try { calibRef.rulerFy } catch (_: Exception) { 1f }
            val x0 = (fx * (width - unitW)).coerceIn(0f, (width - unitW).coerceAtLeast(0f))
            val y0 = (fy * (height - unitH)).coerceIn(0f, (height - unitH).coerceAtLeast(0f))
            lastUnitW = unitW
            lastUnitH = unitH
            unitRect.set(x0, y0, x0 + unitW, y0 + unitH)
            // Arka plan
            canvas.drawRect(unitRect, textBg)
            // 1. satır: cetvel çizgisi + uzunluk
            val ly = y0 + pad + 30f
            canvas.drawLine(x0 + pad, ly, x0 + pad + barPx, ly, scalePaint)
            canvas.drawLine(x0 + pad, ly - 18f, x0 + pad, ly + 18f, scalePaint)
            canvas.drawLine(x0 + pad + barPx, ly - 18f, x0 + pad + barPx, ly + 18f, scalePaint)
            canvas.drawText(scaleLabel, x0 + pad + barPx + 16f, ly + 14f, textPaint)
            // 2. satır: uzaklık (dokun = değiştir)
            canvas.drawText(distLabel, x0 + pad, y0 + pad + rowH + 42f, textPaint)
        } catch (_: Exception) { }
    }
}
