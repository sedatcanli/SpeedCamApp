package com.example.speedcam

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import com.example.speedcam.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var calib: CalibrationManager
    private var camList: List<CamEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        calib = CalibrationManager(this)

        // Dil seçimi
        val langTags = LocaleHelper.LANGS.map { it.first }
        binding.spLang.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            LocaleHelper.LANGS.map { it.second }
        )
        binding.spLang.setSelection(
            langTags.indexOf(calib.langTag).coerceAtLeast(0)
        )

        // Birim seçimi (yerelleştirilmiş adlar, sabit değerler)
        val unitNames = listOf(
            getString(R.string.unit_kmh),
            getString(R.string.unit_ms),
            getString(R.string.unit_mph)
        )
        val units = listOf(SpeedUnit.KMH, SpeedUnit.MS, SpeedUnit.MPH)
        binding.spUnit.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, unitNames)
        binding.spUnit.setSelection(units.indexOf(calib.speedUnit).coerceAtLeast(0))

        // Analiz çözünürlüğü
        val resNames = listOf(
            getString(R.string.res_fast),
            getString(R.string.res_balanced),
            getString(R.string.res_detailed)
        )
        binding.spRes.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resNames)
        binding.spRes.setSelection(calib.analysisRes)

        SliderUtils.setSafe(
            binding.sliderConf, calib.detectionConfidence.coerceIn(0.2f, 0.9f)
        )
        SliderUtils.setSafe(
            binding.sliderSmooth, calib.smoothingWindow.toFloat().coerceIn(2f, 15f)
        )
        binding.switchLabels.isChecked = calib.showLabels
        SliderUtils.setSafe(binding.sliderMotion, calib.motionThreshold)
        SliderUtils.setSafe(binding.sliderMinSpeed, calib.minSpeedMs)
        updateMinSpeedLabel()
        binding.sliderMinSpeed.addOnChangeListener { _, value, _ ->
            updateMinSpeedLabel(value)
        }
        SliderUtils.setSafe(binding.sliderMaxBox, calib.maxBoxAreaPct * 100f)
        updateMaxBoxLabel()
        binding.sliderMaxBox.addOnChangeListener { _, value, _ ->
            updateMaxBoxLabel(value)
        }
        SliderUtils.setSafe(binding.sliderGhost, calib.ghostSec)
        updateGhostLabel()
        binding.sliderGhost.addOnChangeListener { _, value, _ ->
            updateGhostLabel(value)
        }

        // Önce eski basit liste (kamera izni yoksa bile ekran açılsın)
        setCameraSpinner(
            listOf(
                CamEntry("", getString(R.string.cam_default_back), CameraSelector.LENS_FACING_BACK),
                CamEntry("", getString(R.string.cam_default_front), CameraSelector.LENS_FACING_FRONT)
            )
        )

        // Gerçek kamera listesi (geniş açı dahil): ID'leri göster
        CameraHelper.listCameras(this) { list ->
            try {
                if (list.isNotEmpty()) {
                    camList = list
                    setCameraSpinner(list)
                }
            } catch (_: Throwable) { }
        }

        binding.btnTargets.setOnClickListener {
            try {
                startActivity(
                    android.content.Intent(this, TargetActivity::class.java)
                )
            } catch (_: Exception) { }
        }

        binding.btnSaveSettings.setOnClickListener {
            val pos = binding.spCamera.selectedItemPosition
            if (camList.isNotEmpty() && pos in camList.indices) {
                val sel = camList[pos]
                calib.cameraId = sel.cameraId.ifEmpty { null }
                calib.cameraFacing = sel.facing
            } else {
                // Fallback: eski davranış
                calib.cameraId = null
                calib.cameraFacing = if (pos == 0)
                    CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            }
            calib.speedUnit = units[binding.spUnit.selectedItemPosition]
            calib.analysisRes = binding.spRes.selectedItemPosition.coerceIn(0, 2)
            val newLang = langTags[binding.spLang.selectedItemPosition.coerceIn(0, langTags.size - 1)]
            val langChanged = newLang != calib.langTag
            calib.langTag = newLang
            calib.detectionConfidence =
                SliderUtils.snap(binding.sliderConf, binding.sliderConf.value)
            calib.smoothingWindow = binding.sliderSmooth.value.toInt()
            calib.showLabels = binding.switchLabels.isChecked
            calib.motionThreshold =
                SliderUtils.snap(binding.sliderMotion, binding.sliderMotion.value)
            calib.minSpeedMs =
                SliderUtils.snap(binding.sliderMinSpeed, binding.sliderMinSpeed.value)
            calib.maxBoxAreaPct =
                SliderUtils.snap(binding.sliderMaxBox, binding.sliderMaxBox.value) / 100f
            calib.ghostSec =
                SliderUtils.snap(binding.sliderGhost, binding.sliderGhost.value)
            Toast.makeText(this, getString(R.string.msg_saved), Toast.LENGTH_SHORT).show()
            if (langChanged) {
                try { LocaleHelper.apply(newLang) } catch (_: Exception) { }
            }
            finish()
        }
    }

    private fun updateMinSpeedLabel(v: Float = -1f) {
        val ms = if (v < 0) {
            try { binding.sliderMinSpeed.value } catch (_: Exception) { 0f }
        } else v
        binding.tvMinSpeed.text = if (ms <= 0f) getString(R.string.min_speed_off)
        else "%.1f m/s (%.0f %s)".format(
            ms, SpeedUnit.toDisplay(ms, calib.speedUnit), calib.speedUnit
        )
    }

    private fun updateMaxBoxLabel(v: Float = -1f) {
        val x = if (v < 0) {
            try { binding.sliderMaxBox.value } catch (_: Exception) { 80f }
        } else v
        binding.tvMaxBox.text = "%%%d".format(x.toInt())
    }

    private fun updateGhostLabel(v: Float = -1f) {
        val x = if (v < 0) {
            try { binding.sliderGhost.value } catch (_: Exception) { 1.5f }
        } else v
        binding.tvGhost.text = if (x <= 0f) getString(R.string.ghost_off) else "%.1f s".format(x)
    }

    private fun setCameraSpinner(list: List<CamEntry>) {
        camList = list
        binding.spCamera.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            list.map { it.label }
        )
        // Kayıtlı kamerayı seç
        val savedId = calib.cameraId
        val idx = if (!savedId.isNullOrEmpty()) {
            list.indexOfFirst { it.cameraId == savedId }
        } else {
            list.indexOfFirst { it.facing == calib.cameraFacing && it.cameraId.isEmpty() }
                .takeIf { it >= 0 } ?: list.indexOfFirst { it.facing == calib.cameraFacing }
        }
        if (idx >= 0) binding.spCamera.setSelection(idx)
    }
}
