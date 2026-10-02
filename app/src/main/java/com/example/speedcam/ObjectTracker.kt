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
    var maxSpeedMs: Float = 0f,
    var avgSpeedMs: Float = 0f,
    val speedHistory: ArrayDeque<Pair<Long, Float>> = ArrayDeque(),
    var ghost: Boolean = false,
    var lastSeenMs: Long = 0L
) {
    fun center(): PointF = PointF(box.centerX(), box.centerY())
}

/**
 * Basit centroid takipçi.
 * Ardışık karelerde en yakın merkezi eşleştirir, eksen başına metre/piksel
 * ölçeğiyle (trigonometrik kalibrasyon) hızı hesaplar:
 *   metre = sqrt((dx*mx)^2 + (dy*my)^2), hız = metre / süre.
 */
class ObjectTracker(
    var metersPerPixelX: Float = 0.01f,
    var metersPerPixelY: Float = 0.01f,
    var smoothingWindow: Int = 5,
    var avgWindowMs: Long = 2000L,
    /** Kaybolan nesnenin son hızıyla gösterilmeye devam süresi (ms) */
    var ghostMs: Long = 1500L,
    private val maxMatchDistancePx: Float = 180f
) {
    private val tracks = mutableMapOf<Int, TrackedObject>()
    private var nextId = 1

    fun update(detections: List<DetectedBox>, nowMs: Long): List<TrackedObject> {
        // Hayalet süresi dolanları temizle
        tracks.entries.removeIf { nowMs - it.value.lastSeenMs > ghostMs }

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
                best.ghost = false
                best.lastSeenMs = nowMs
                best.trail.addLast(Pair(c, nowMs))
                while (best.trail.size > smoothingWindow) best.trail.removeFirst()
                best.speedMs = computeSpeed(best)
                if (best.speedMs > best.maxSpeedMs) best.maxSpeedMs = best.speedMs
                // Son 2 sn'lik ortalama hız (yavaş nesne filtresi için)
                best.speedHistory.addLast(Pair(nowMs, best.speedMs))
                while (best.speedHistory.isNotEmpty() &&
                    nowMs - best.speedHistory.first().first > avgWindowMs
                ) best.speedHistory.removeFirst()
                best.avgSpeedMs = if (best.speedHistory.isEmpty()) best.speedMs
                else best.speedHistory.map { it.second }.average().toFloat()
            } else {
                val t = TrackedObject(nextId++, RectF(det.box), det.label)
                t.trail.addLast(Pair(c, nowMs))
                t.lastSeenMs = nowMs
                tracks[t.id] = t
            }
        }
        // Eşleşmeyenler hayalet: son kutu + son hızla görünmeye devam eder
        for (t in unmatched) t.ghost = true
        return tracks.values.sortedBy { it.id }
    }

    private fun computeSpeed(t: TrackedObject): Float {
        if (t.trail.size < 2) return 0f
        val first = t.trail.first()
        val last = t.trail.last()
        val dtSec = (last.second - first.second) / 1000f
        if (dtSec <= 0.01f) return t.speedMs // çok küçük aralık, eski değeri koru
        val dxM = (last.first.x - first.first.x) * metersPerPixelX
        val dyM = (last.first.y - first.first.y) * metersPerPixelY
        val distM = hypot(dxM.toDouble(), dyM.toDouble()).toFloat()
        val instant = distM / dtSec
        // Ani sıçramaları yumuşat: %70 eski + %30 yeni
        return t.speedMs * 0.7f + instant * 0.3f
    }

    fun clear() {
        tracks.clear()
        nextId = 1
    }
}
