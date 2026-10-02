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

    /** Hareket hassasiyeti: düşük değer = daha hassas */
    var motionThreshold: Float
        get() = prefs.getFloat("motion", 0.6f).coerceIn(0.1f, 3f)
        set(v) = prefs.edit().putFloat("motion", v).apply()

    // ---- Trigonometrik kalibrasyon (iğne deliği modeli) ----
    /** Ölçüm düzleminin kameraya uzaklığı (metre) — kullanıcı girer */
    var distanceM: Float
        get() = prefs.getFloat("dist", 10f).coerceIn(0.5f, 500f)
        set(v) = prefs.edit().putFloat("dist", v).apply()

    /** Yatay/dikey görüş açısı (derece) — sistemden okunur, elle düzeltilebilir */
    var fovHdeg: Float
        get() = prefs.getFloat("fovH", 0f)
        set(v) = prefs.edit().putFloat("fovH", v).apply()
    var fovVdeg: Float
        get() = prefs.getFloat("fovV", 0f)
        set(v) = prefs.edit().putFloat("fovV", v).apply()

    /** Bilgi amaçlı optik değerler (sistemden) */
    var focalMm: Float
        get() = prefs.getFloat("focal", 0f)
        set(v) = prefs.edit().putFloat("focal", v).apply()
    var pixelUm: Float
        get() = prefs.getFloat("pixelUm", 0f)
        set(v) = prefs.edit().putFloat("pixelUm", v).apply()
    var sensorMp: Float
        get() = prefs.getFloat("sensorMp", 0f)
        set(v) = prefs.edit().putFloat("sensorMp", v).apply()
    var sensorWmm: Float
        get() = prefs.getFloat("sensorW", 0f)
        set(v) = prefs.edit().putFloat("sensorW", v).apply()
    var arrayW: Int
        get() = prefs.getInt("arrayW", 0)
        set(v) = prefs.edit().putInt("arrayW", v).apply()
    var arrayH: Int
        get() = prefs.getInt("arrayH", 0)
        set(v) = prefs.edit().putInt("arrayH", v).apply()

    /** Sistemden optikleri oku ve kaydet. force=true ise kayıtlı FOV'u ezer. */
    fun refreshOptics(context: Context, force: Boolean): OpticsInfo? {
        val info = CameraHelper.readOptics(context, cameraId, cameraFacing)
            ?: return null
        if (force || fovHdeg <= 0f) fovHdeg = info.fovHdeg
        if (force || fovVdeg <= 0f) fovVdeg = info.fovVdeg
        focalMm = info.focalMm
        pixelUm = info.pixelUm
        sensorMp = info.mp
        sensorWmm = info.sensorWmm
        arrayW = info.arrayW
        arrayH = info.arrayH
        return info
    }

    /**
     * Verilen görüntü boyutu için eksen başına metre/piksel:
     * görünür genişlik = 2 * Uzaklık * tan(FOV/2).
     * FOV bilinmiyorsa eski 100 px/m varsayımına düşer.
     */
    fun metersPerPixel(imgW: Float, imgH: Float): Pair<Float, Float> {
        val fh = Math.toRadians(fovHdeg.toDouble())
        val fv = Math.toRadians(fovVdeg.toDouble())
        val mx = if (fh > 0.01 && imgW > 0)
            (2 * distanceM * Math.tan(fh / 2) / imgW).toFloat() else 0.01f
        val my = if (fv > 0.01 && imgH > 0)
            (2 * distanceM * Math.tan(fv / 2) / imgH).toFloat() else 0.01f
        return Pair(mx, my)
    }

    /** Bilinen mesafe (metre) + piksel uzunluktan ppm hesapla (eski yöntem, duruyor) */
    fun calibrateFromReference(pixelLength: Float, knownMeters: Float): Float {
        if (knownMeters <= 0f || pixelLength <= 0f) return pixelsPerMeter
        val ppm = pixelLength / knownMeters
        pixelsPerMeter = ppm
        return ppm
    }

    fun summary(): String {
        val cam = if (!cameraId.isNullOrEmpty()) "ID $cameraId"
        else if (cameraFacing == CameraSelector.LENS_FACING_BACK) "Arka" else "Ön"
        return if (fovHdeg > 0f) "%.0fm • %.0f° • %s • %s".format(
            distanceM, fovHdeg, speedUnit, cam
        )
        else "%.1f px/m • %s • %s".format(pixelsPerMeter, speedUnit, cam)
    }
}
