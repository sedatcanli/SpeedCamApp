package com.example.speedcam

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/** Kutu ön filtreleri: çok büyük kutular + kesişen kopyalar (NMS). */
object BoxFilter {

    fun prepare(
        raw: List<DetectedBox>,
        imgW: Float,
        imgH: Float,
        maxFrac: Float,
        iouThr: Float = 0.45f
    ): List<DetectedBox> {
        if (raw.isEmpty()) return raw
        var out = raw
        // 1) Ekranın çok büyük kısmını kaplayanlar cisim değildir
        val area = imgW * imgH
        if (area > 0 && maxFrac < 1f) {
            out = out.filter {
                (it.box.width() * it.box.height()) / area <= maxFrac
            }
        }
        // 2) Kesişen kutulardan güveni yüksek olanı tut (NMS)
        if (out.size < 2) return out
        val sorted = out.sortedByDescending { it.confidence }
        val kept = ArrayList<DetectedBox>(sorted.size)
        for (d in sorted) {
            var dup = false
            for (k in kept) {
                if (iou(k.box, d.box) > iouThr) {
                    dup = true
                    break
                }
            }
            if (!dup) kept.add(d)
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val ix = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val iy = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = ix * iy
        if (inter <= 0f) return 0f
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }
}
