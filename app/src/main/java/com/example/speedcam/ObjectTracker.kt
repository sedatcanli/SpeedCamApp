package com.example.speedcam

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.hypot

data class DetectedBox(
    val box: RectF,
    val label: String?,
    val confidence: Float
)

data class TrackedObject(
    val id: Int,
    var box: RectF,
    var label: String?,
    val trail: ArrayDeque<Pair<PointF, Long>> = ArrayDeque(),
    var speedMs: Float = 0f,
    var maxSpeedMs: Float = 0f
) {
    fun center(): PointF = PointF(box.centerX(), box.centerY())
}

/**
 * Basit centroid takipçi.
 * Ardışık karelerde en yakın merkezi eşleştirir, ppm (pixel-per-meter)
 * kalibrasyonu ile hızı hesaplar: metre = piksel / ppm, hız = metre / sn.
 */
class ObjectTracker(
    var pixelsPerMeter: Float = 100f,
    var smoothingWindow: Int = 5,
    private val maxMatchDistancePx: Float = 180f,
    private val maxIdleMs: Long = 1200L
) {
    private val tracks = mutableMapOf<Int, TrackedObject>()
    private var nextId = 1

    fun update(detections: List<DetectedBox>, nowMs: Long): List<TrackedObject> {
        // 1. Eski izleri temizle
        tracks.entries.removeIf { nowMs - (it.value.trail.lastOrNull()?.second ?: 0L) > maxIdleMs }

        val unmatched = tracks.values.toMutableSet()

        for (det in detections) {
            val c = PointF(det.box.centerX(), det.box.centerY())
            // En yakın izi bul
            var best: TrackedObject? = null
            var bestDist = Float.MAX_VALUE
            for (t in unmatched) {
                val tc = t.center()
                val d = hypot((c.x - tc.x).toDouble(), (c.y - tc.y).toDouble()).toFloat()
                if (d < bestDist) { bestDist = d; best = t }
            }
            if (best != null && bestDist < maxMatchDistancePx) {
                unmatched.remove(best)
                best.box = RectF(det.box)
                best.label = det.label
                best.trail.addLast(Pair(c, nowMs))
                while (best.trail.size > smoothingWindow) best.trail.removeFirst()
                best.speedMs = computeSpeed(best)
                if (best.speedMs > best.maxSpeedMs) best.maxSpeedMs = best.speedMs
            } else {
                val t = TrackedObject(nextId++, RectF(det.box), det.label)
                t.trail.addLast(Pair(c, nowMs))
                tracks[t.id] = t
            }
        }
        return tracks.values.sortedBy { it.id }
    }

    private fun computeSpeed(t: TrackedObject): Float {
        if (t.trail.size < 2 || pixelsPerMeter <= 0f) return 0f
        val first = t.trail.first()
        val last = t.trail.last()
        val dtSec = (last.second - first.second) / 1000f
        if (dtSec <= 0.01f) return t.speedMs // çok küçük aralık, eski değeri koru
        val dx = last.first.x - first.first.x
        val dy = last.first.y - first.first.y
        val distPx = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val distM = distPx / pixelsPerMeter
        val instant = distM / dtSec
        // Ani sıçramaları yumuşat: %70 eski + %30 yeni
        return t.speedMs * 0.7f + instant * 0.3f
    }

    fun clear() {
        tracks.clear()
        nextId = 1
    }
}
