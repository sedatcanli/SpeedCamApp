package com.example.speedcam

import com.google.android.material.slider.Slider
import kotlin.math.round

/**
 * Material Slider, ızgara dışı değerde patlar (IllegalStateException).
 * Kaydedilmiş eski bozuk değerler dahil her şeyi ızgaraya oturtur.
 */
object SliderUtils {
    fun setSafe(s: Slider, v: Float) {
        try {
            val from = s.valueFrom
            val to = s.valueTo
            val step = s.stepSize
            var x = v.coerceIn(from, to)
            if (step > 0f) {
                x = from + round((x - from) / step) * step
                // Float hatasını temizle: virgülden sonra 4 basamak
                x = (round(x * 10000f) / 10000f).coerceIn(from, to)
            }
            try {
                s.value = x
            } catch (_: Exception) {
                // Son çare: en yakın uca koy
                try {
                    s.value = if (x - from < to - x) from else to
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    /** Kaydetmeden önce ızgaraya oturt (ileride çökme olmasın). */
    fun snap(s: Slider, v: Float): Float {
        return try {
            val from = s.valueFrom
            val step = s.stepSize
            if (step <= 0f) return v
            val x = from + round((v - from) / step) * step
            round(x * 10000f) / 10000f
        } catch (_: Exception) { v }
    }
}
