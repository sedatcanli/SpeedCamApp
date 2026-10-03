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

    // ---- Sürüklenebilir + boyutlandırılabilir cetvel birimi ----
    var onRulerTap: (() -> Unit)? = null
    private val calibRef by lazy { CalibrationManager(context.applicationContext) }
    private val unitRect = android.graphics.RectF()
    private var lastUnitW = 0f
    private var lastUnitH = 0f
    private var lastBarX0 = 0f
    private var lastBarX1 = 0f
    private var lastBarY = 0f
    private var dragMode = 0 // 0 yok, 1 taşı, 2 sol uç, 3 sağ uç
    private var dragId = -1
    private var downX = 0f
    private var downY = 0f
    private var dragOffX = 0f
    private var dragOffY = 0f
    private var fixedEndX = 0f
    private var dragMoved = false

    private fun endHit(x: Float, y: Float, ex: Float, ey: Float): Boolean =
        kotlin.math.hypot((x - ex).toDouble(), (y - ey).toDouble()) <= 48f

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                if (endHit(e.x, e.y, lastBarX0, lastBarY)) {
                    dragMode = 2
                    fixedEndX = lastBarX1
                } else if (endHit(e.x, e.y, lastBarX1, lastBarY)) {
                    dragMode = 3
                    fixedEndX = lastBarX0
                } else if (unitRect.contains(e.x, e.y)) {
                    dragMode = 1
                } else return super.onTouchEvent(e)
                dragMoved = false
                dragId = e.getPointerId(0)
                downX = e.x
                downY = e.y
                dragOffX = e.x - unitRect.left
                dragOffY = e.y - unitRect.top
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (dragMode != 0) {
                    val idx = e.findPointerIndex(dragId)
                    if (idx >= 0) {
                        val x = e.getX(idx)
                        val y = e.getY(idx)
                        if (kotlin.math.hypot(
                                (x - downX).toDouble(), (y - downY).toDouble()
                            ) > 12
                        ) dragMoved = true
                        if (dragMoved && width > 0 && height > 0) {
                            if (dragMode == 1) {
                                val denomW = (width - lastUnitW).coerceAtLeast(1f)
                                val denomH = (height - lastUnitH).coerceAtLeast(1f)
                                calibRef.rulerFx =
                                    ((x - dragOffX) / denomW).coerceIn(0f, 1f)
                                calibRef.rulerFy =
                                    ((y - dragOffY) / denomH).coerceIn(0f, 1f)
                            } else {
                                // Uçtan kalibrasyon: etiket sabit, ölçek uyar.
                                // Yeni boy -> scaleCorr = L / (yeniPx * trig_ölçeği)
                                val mpp = metersPerViewPx
                                val refL = try { calibRef.refLenM } catch (_: Exception) { 0f }
                                val corrOld = try { calibRef.scaleCorr } catch (_: Exception) { 1f }
                                if (mpp > 0f && refL > 0f && corrOld > 0f) {
                                    val newPx = kotlin.math.abs(x - fixedEndX)
                                    if (newPx > 30f) {
                                        val corrNew =
                                            (refL * corrOld / (newPx * mpp)).coerceIn(0.3f, 3f)
                                        calibRef.scaleCorr = corrNew
                                    }
                                }
                            }
                            invalidate()
                        }
                    }
                    return true
                }
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                if (dragMode != 0) {
                    dragMode = 0
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

    private val rulerBg = Paint().apply { color = Color.argb(130, 128, 128, 128) }
    private val handlePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val handleEdge = Paint().apply {
        color = Color.argb(200, 60, 60, 60)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    /** Serbest sürüklenebilir + uçlarından boyutlandırılabilir cetvel birimi. */
    private fun drawScaleBar(canvas: Canvas) {
        val mpp = metersPerViewPx
        if (mpp <= 0f || width <= 0 || height <= 0) {
            unitRect.setEmpty()
            return
        }
        try {
            // Cetvel: seçili referans uzunluk, piksel hassasiyetinde.
            // Uçlar sürüklenince etiket sabit kalır, ölçek katsayısı yazar.
            val refL = try { calibRef.refLenM } catch (_: Exception) { 0f }
            if (refL <= 0f) {
                unitRect.setEmpty()
                return
            }
            val barPx = refL / mpp
            if (barPx < 30f) {
                unitRect.setEmpty()
                return
            }
            val scaleLabel = if (refL < 1f) "%d cm".format((refL * 100).toInt()) else {
                val whole = refL.toInt()
                if (refL == whole.toFloat()) "%d m".format(whole)
                else "%.1f m".format(refL)
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
            // Gri yarı şeffaf arka plan
            canvas.drawRect(unitRect, rulerBg)
            // 1. satır: cetvel çizgisi + uzunluk
            val ly = y0 + pad + 30f
            lastBarX0 = x0 + pad
            lastBarX1 = x0 + pad + barPx
            lastBarY = ly
            canvas.drawLine(lastBarX0, ly, lastBarX1, ly, scalePaint)
            canvas.drawLine(lastBarX0, ly - 18f, lastBarX0, ly + 18f, scalePaint)
            canvas.drawLine(lastBarX1, ly - 18f, lastBarX1, ly + 18f, scalePaint)
            // Tutamaçlar (uçlardan boyutlandırma)
            canvas.drawCircle(lastBarX0, ly, 16f, handlePaint)
            canvas.drawCircle(lastBarX0, ly, 16f, handleEdge)
            canvas.drawCircle(lastBarX1, ly, 16f, handlePaint)
            canvas.drawCircle(lastBarX1, ly, 16f, handleEdge)
            canvas.drawText(scaleLabel, x0 + pad + barPx + 16f, ly + 14f, textPaint)
            // 2. satır: uzaklık (dokun = değiştir)
            canvas.drawText(distLabel, x0 + pad, y0 + pad + rowH + 42f, textPaint)
        } catch (_: Exception) { }
    }
}
