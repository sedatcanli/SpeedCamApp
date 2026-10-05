package com.example.speedcam

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.speedcam.databinding.ActivityCalibrationBinding

/**
 * Trigonometrik kalibrasyon (iğne deliği modeli):
 *   görünür genişlik = 2 * Uzaklık * tan(FOV / 2)
 *   metre/piksel = görünür genişlik / görüntü genişliği (px)
 *   hız = piksel_hareket * metre/piksel / süre
 *
 * Uzaklık kullanıcıdan alınır; FOV, piksel boyutu, sensör ve kamera
 * çözünürlüğü sistemden otomatik okunur (elle düzeltilebilir).
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var calib: CalibrationManager

    /** Türkçe virgül de kabul et (65,5 ve 65.5 aynı). */
    private fun parseDec(s: String): Float? =
        s.trim().replace(',', '.').toFloatOrNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        calib = CalibrationManager(this)

        // İlk açılışta sistemden otomatik oku
        try {
            calib.refreshOptics(this, false)
        } catch (_: Exception) { }

        fillFields()
        updatePreview()

        binding.btnRefreshOptics.setOnClickListener {
            try {
                val info = calib.refreshOptics(this, true)
                if (info == null) {
                    Toast.makeText(
                        this, "Optik okunamadı, değerleri elle girin",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this, "Okundu: %s".format(info.cameraId), Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Okuma hatası", Toast.LENGTH_SHORT).show()
            }
            fillFields()
            updatePreview()
        }

        binding.btnCalc.setOnClickListener {
            if (!readInputs()) return@setOnClickListener
            updatePreview()
        }

        binding.tvCorr.text = corrText()

        binding.btnCorrect.setOnClickListener {
            val real = parseDec(binding.etReal.text.toString())
            val shown = parseDec(binding.etShown.text.toString())
            if (real == null || shown == null || real <= 0f || shown <= 0f) {
                Toast.makeText(this, "Gerçek ve gösterilen uzunluğu girin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val corr = (real / shown).coerceIn(0.3f, 3f)
            calib.scaleCorr = corr
            binding.tvCorr.text = corrText()
            updatePreview()
            Toast.makeText(this, "Düzeltme uygulandı", Toast.LENGTH_SHORT).show()
        }

        binding.btnSave.setOnClickListener {
            if (!readInputs()) return@setOnClickListener
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun fillFields() {
        binding.etDistance.setText(calib.distanceM.toString().replace(',', '.'))
        if (calib.fovHdeg > 0f) binding.etFovH.setText(
            "%.1f".format(java.util.Locale.US, calib.fovHdeg))
        if (calib.fovVdeg > 0f) binding.etFovV.setText(
            "%.1f".format(java.util.Locale.US, calib.fovVdeg))
        binding.tvOptics.text = opticsText()
    }

    private fun corrText(): String =
        "İnce ayar katsayısı: %.3f (1.000 = düzeltme yok)".format(calib.scaleCorr)

    private fun opticsText(): String {        val sb = StringBuilder()
        if (calib.arrayW > 0) {
            sb.append("Kamera: %d x %d (%.1f MP)\n".format(
                calib.arrayW, calib.arrayH, calib.sensorMp))
        } else sb.append("Kamera çözünürlüğü: okunamadı\n")
        if (calib.sensorWmm > 0f) {
            sb.append("Sensör: %.2f mm genişlik\n".format(calib.sensorWmm))
        }
        if (calib.pixelUm > 0f) {
            sb.append("Piksel boyutu: %.2f µm\n".format(calib.pixelUm))
        }
        if (calib.focalMm > 0f) {
            sb.append("Odak uzaklığı: %.2f mm".format(calib.focalMm))
        }
        if (sb.isEmpty()) sb.append("Henüz okunamadı — Yenile'ye basın")
        return sb.toString()
    }

    /** Alanları okuyup kaydet; geçersizse false. */
    private fun readInputs(): Boolean {
        val d = parseDec(binding.etDistance.text.toString())
        val fh = parseDec(binding.etFovH.text.toString())
        val fv = parseDec(binding.etFovV.text.toString())
        if (d == null || d < 0.5f || d > 500f) {
            Toast.makeText(this, "Uzaklık 0.5 - 500 m olmalı", Toast.LENGTH_SHORT).show()
            return false
        }
        if (fh == null || fh < 5f || fh > 170f) {
            Toast.makeText(this, "Yatay FOV 5 - 170° olmalı", Toast.LENGTH_SHORT).show()
            return false
        }
        if (fv == null || fv < 5f || fv > 170f) {
            Toast.makeText(this, "Dikey FOV 5 - 170° olmalı", Toast.LENGTH_SHORT).show()
            return false
        }
        calib.distanceM = d
        calib.fovHdeg = fh
        calib.fovVdeg = fv
        return true
    }

    private fun updatePreview() {
        val d = parseDec(binding.etDistance.text.toString()) ?: calib.distanceM
        val fh = parseDec(binding.etFovH.text.toString()) ?: calib.fovHdeg
        // Analiz karesi 1280x720 üzerinden örnekle
        val imgW = 1280f
        val rad = Math.toRadians(fh.toDouble())
        if (rad > 0.01) {
            val zoom = try { calib.zoomRatio.coerceIn(0.5f, 20f) } catch (_: Exception) { 1f }
            val corr = try { calib.scaleCorr } catch (_: Exception) { 1f }
            val visW = 2 * d * Math.tan(rad / 2) / zoom * corr
            // Sensör kırpması (ana hesapla aynı)
            var hFrac = 1f
            try {
                if (calib.arrayW > 0 && calib.arrayH > 0) {
                    val sa = calib.arrayW.toFloat() / calib.arrayH
                    if (1280f / 720f < sa) hFrac =
                        ((1280f / 720f) / sa).coerceIn(0.2f, 1f)
                }
            } catch (_: Exception) { }
            val cmPerPx = visW / imgW * 100 * hFrac
            val exMs = (100f / imgW * visW).toFloat() / 0.2f
            binding.tvExample.text =
                "%.0f m uzakta (%.1fx zoom) 1 px = %.1f cm\nÖrnek: 100px / 0.2sn = %.1f km/h".format(
                    d, zoom, cmPerPx, exMs * 3.6f
                )
        } else {
            binding.tvExample.text = "Önce geçerli FOV girin"
        }
    }
}
