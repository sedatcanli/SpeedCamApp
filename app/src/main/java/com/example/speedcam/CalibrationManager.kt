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

    /** Minimum hız filtresi (m/s): 2 sn ortalaması bunun altındaysa gösterme. 0 = kapalı */
    var minSpeedMs: Float
        get() = prefs.getFloat("minSpeed", 0f).coerceIn(0f, 5f)
        set(v) = prefs.edit().putFloat("minSpeed", v).apply()

    /** Maksimum kutu boyutu (ekran oranı): bundan büyük kutular cisim değildir */
    var maxBoxAreaPct: Float
        get() = prefs.getFloat("maxBox", 0.8f).coerceIn(0.1f, 1f)
        set(v) = prefs.edit().putFloat("maxBox", v).apply()

    /** Kaybolan cismin son hızıyla gösterilme süresi (sn). 0 = kapalı */
    var ghostSec: Float
        get() = prefs.getFloat("ghostSec", 1.5f).coerceIn(0f, 3f)
        set(v) = prefs.edit().putFloat("ghostSec", v).apply()

    /** Cetvel biriminin ekrandaki konumu (0..1 oran, serbest sürüklenir) */
    var rulerFx: Float
        get() = prefs.getFloat("rulerFx", 0f).coerceIn(0f, 1f)
        set(v) = prefs.edit().putFloat("rulerFx", v).apply()
    var rulerFy: Float
        get() = prefs.getFloat("rulerFy", 1f).coerceIn(0f, 1f)
        set(v) = prefs.edit().putFloat("rulerFy", v).apply()

    /** Cetvel hedef boyu (ekran genişliğine oran). Uçlardan sürüklenince değişir */
    var rulerTargetFrac: Float
        get() = prefs.getFloat("rulerTarget", 0.55f).coerceIn(0.15f, 0.9f)
        set(v) = prefs.edit().putFloat("rulerTarget", v).apply()

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
        var wroteFov = false
        if (force || fovHdeg <= 0f) { fovHdeg = info.fovHdeg; wroteFov = true }
        if (force || fovVdeg <= 0f) { fovVdeg = info.fovVdeg; wroteFov = true }
        focalMm = info.focalMm
        pixelUm = info.pixelUm
        sensorMp = info.mp
        sensorWmm = info.sensorWmm
        arrayW = info.arrayW
        arrayH = info.arrayH
        if (wroteFov) {
            opticsCamId = info.cameraId
            opticsFacing = cameraFacing
        }
        return info
    }

    /** FOV değerleri hangi kamera için okundu */
    var opticsCamId: String?
        get() = prefs.getString("opticsCamId", null)
        set(v) = prefs.edit().putString("opticsCamId", v).apply()
    var opticsFacing: Int
        get() = prefs.getInt("opticsFacing", -1)
        set(v) = prefs.edit().putInt("opticsFacing", v).apply()

    /**
     * Verilen görüntü boyutu için eksen başına metre/piksel:
     * görünür genişlik = 2 * Uzaklık * tan(FOV/2).
     * FOV bilinmiyorsa eski 100 px/m varsayımına düşer.
     * scaleCorr: referans ölçümle bulunan ince ayar katsayısı.
     */
    fun metersPerPixel(imgW: Float, imgH: Float): Pair<Float, Float> {
        val fh = Math.toRadians(fovHdeg.toDouble())
        val fv = Math.toRadians(fovVdeg.toDouble())
        val corr = scaleCorr
        val mx = if (fh > 0.01 && imgW > 0)
            (2 * distanceM * Math.tan(fh / 2) / imgW * corr).toFloat() else 0.01f
        val my = if (fv > 0.01 && imgH > 0)
            (2 * distanceM * Math.tan(fv / 2) / imgH * corr).toFloat() else 0.01f
        return Pair(mx, my)
    }

    /** Referans düzeltme: cetvel Y gösteriyorsa gerçek X ise katsayı X/Y */
    var scaleCorr: Float
        get() = prefs.getFloat("scaleCorr", 1f).coerceIn(0.3f, 3f)
        set(v) = prefs.edit().putFloat("scaleCorr", v).apply()

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
