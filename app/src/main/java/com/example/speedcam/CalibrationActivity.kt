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

        if (calib.calibMode == "auto") binding.rbAuto.isChecked = true
        else binding.rbManual.isChecked = true
        updateModeDesc()

        binding.rgMode.setOnCheckedChangeListener { _, id ->
            try {
                calib.calibMode =
                    if (id == binding.rbAuto.id) "auto" else "manual"
                updateModeDesc()
            } catch (_: Exception) { }
        }

        binding.btnRefreshOptics.setOnClickListener {
            try {
                val info = calib.refreshOptics(this, true)
                if (info == null) {
                    Toast.makeText(
                        this, getString(R.string.msg_optics_fail),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this, getString(R.string.msg_optics_ok, info.cameraId), Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.msg_optics_err), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.msg_invalid), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val corr = (real / shown).coerceIn(0.3f, 3f)
            calib.scaleCorr = corr
            binding.tvCorr.text = corrText()
            updatePreview()
            Toast.makeText(this, getString(R.string.msg_corr_applied), Toast.LENGTH_SHORT).show()
        }

        binding.btnSave.setOnClickListener {
            if (!readInputs()) return@setOnClickListener
            Toast.makeText(this, getString(R.string.msg_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateModeDesc() {
        try {
            val auto = calib.calibMode == "auto"
            binding.tvModeDesc.text = if (auto) {
                getString(R.string.mode_auto_desc)
            } else {
                getString(R.string.mode_manual_desc)
            }
        } catch (_: Exception) { }
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
        getString(R.string.corr_value, calib.scaleCorr)

    private fun opticsText(): String {
        val sb = StringBuilder()
        if (calib.arrayW > 0) {
            sb.append(getString(
                R.string.optics_cam, calib.arrayW, calib.arrayH, calib.sensorMp
            )).append('\n')
        } else sb.append(getString(R.string.optics_none)).append('\n')
        if (calib.sensorWmm > 0f) {
            sb.append(getString(R.string.optics_sensor, calib.sensorWmm)).append('\n')
        }
        if (calib.pixelUm > 0f) {
            sb.append(getString(R.string.optics_pixel, calib.pixelUm)).append('\n')
        }
        if (calib.focalMm > 0f) {
            sb.append(getString(R.string.optics_focal, calib.focalMm))
        }
        val s = sb.toString().trim()
        return s.ifEmpty { getString(R.string.optics_none) }
    }

    /** Alanları okuyup kaydet; geçersizse false. */
    private fun readInputs(): Boolean {
        val d = parseDec(binding.etDistance.text.toString())
        val fh = parseDec(binding.etFovH.text.toString())
        val fv = parseDec(binding.etFovV.text.toString())
        if (d == null || d < 0.5f || d > 500f) {
            Toast.makeText(this, getString(R.string.msg_invalid), Toast.LENGTH_SHORT).show()
            return false
        }
        if (fh == null || fh < 5f || fh > 170f) {
            Toast.makeText(this, getString(R.string.msg_invalid), Toast.LENGTH_SHORT).show()
            return false
        }
        if (fv == null || fv < 5f || fv > 170f) {
            Toast.makeText(this, getString(R.string.msg_invalid), Toast.LENGTH_SHORT).show()
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
            val exKmh = exMs * 3.6f
            binding.tvExample.text =
                getString(R.string.example_template, d, zoom, cmPerPx, exKmh)
        } else {
            binding.tvExample.text = getString(R.string.msg_invalid)
        }
    }
}
