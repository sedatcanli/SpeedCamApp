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

        // Birim seçimi
        val units = listOf(SpeedUnit.KMH, SpeedUnit.MS, SpeedUnit.MPH)
        binding.spUnit.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, units)
        binding.spUnit.setSelection(units.indexOf(calib.speedUnit).coerceAtLeast(0))

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
                CamEntry("", "Arka Kamera (varsayılan)", CameraSelector.LENS_FACING_BACK),
                CamEntry("", "Ön Kamera (varsayılan)", CameraSelector.LENS_FACING_FRONT)
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
            Toast.makeText(this, "Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateMinSpeedLabel(v: Float = -1f) {
        val ms = if (v < 0) {
            try { binding.sliderMinSpeed.value } catch (_: Exception) { 0f }
        } else v
        binding.tvMinSpeed.text = if (ms <= 0f) "Kapalı"
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
        binding.tvGhost.text = if (x <= 0f) "Kapalı" else "%.1f sn".format(x)
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
