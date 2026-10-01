package com.example.speedcam

import android.content.Context
import android.content.SharedPreferences
import androidx.camera.core.CameraSelector

object SpeedUnit {
    const val KMH = "km/h"
    const val MS = "m/s"
    const val MPH = "mph"

    fun toDisplay(ms: Float, unit: String): Float = when (unit) {
        KMH -> ms * 3.6f
        MPH -> ms * 2.23694f
        else -> ms
    }
}

class CalibrationManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("speedcam_calib", Context.MODE_PRIVATE)

    var pixelsPerMeter: Float
        get() = prefs.getFloat("ppm", 100f)
        set(v) = prefs.edit().putFloat("ppm", v).apply()

    var speedUnit: String
        get() = prefs.getString("unit", SpeedUnit.KMH) ?: SpeedUnit.KMH
        set(v) = prefs.edit().putString("unit", v).apply()

    // 0 = arka (BACK), 1 = ön (FRONT)
    var cameraFacing: Int
        get() = prefs.getInt("facing", CameraSelector.LENS_FACING_BACK)
        set(v) = prefs.edit().putInt("facing", v).apply()

    /** Belirli kamera ID'si (geniş açı vb.), boş = varsayılan */
    var cameraId: String?
        get() = prefs.getString("camId", null)
        set(v) = prefs.edit().putString("camId", v).apply()

    var detectionConfidence: Float
        get() = prefs.getFloat("conf", 0.5f)
        set(v) = prefs.edit().putFloat("conf", v).apply()

    var smoothingWindow: Int
        get() = prefs.getInt("smooth", 5).coerceIn(2, 15)
        set(v) = prefs.edit().putInt("smooth", v).apply()

    var showLabels: Boolean
        get() = prefs.getBoolean("labels", true)
        set(v) = prefs.edit().putBoolean("labels", v).apply()

    /** Zoom kalıcı olsun (döndürmede sıfırlanmasın) */
    var zoomRatio: Float
        get() = prefs.getFloat("zoom", 1f).coerceIn(0.5f, 20f)
        set(v) = prefs.edit().putFloat("zoom", v).apply()

    /** Bilinen mesafe (metre) + piksel uzunluktan ppm hesapla */
    fun calibrateFromReference(pixelLength: Float, knownMeters: Float): Float {
        if (knownMeters <= 0f || pixelLength <= 0f) return pixelsPerMeter
        val ppm = pixelLength / knownMeters
        pixelsPerMeter = ppm
        return ppm
    }

    fun summary(): String {
        val cam = if (!cameraId.isNullOrEmpty()) "ID $cameraId"
        else if (cameraFacing == CameraSelector.LENS_FACING_BACK) "Arka" else "Ön"
        return "%.1f px/m • %s • %s".format(pixelsPerMeter, speedUnit, cam)
    }
}
