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

        binding.sliderConf.value = calib.detectionConfidence
        binding.sliderSmooth.value = calib.smoothingWindow.toFloat()
        binding.switchLabels.isChecked = calib.showLabels

        // Önce eski basit liste (kamera izni yoksa bile ekran açılsın)
        setCameraSpinner(
            listOf(
                CamEntry("", "Arka Kamera (varsayılan)", CameraSelector.LENS_FACING_BACK),
                CamEntry("", "Ön Kamera (varsayılan)", CameraSelector.LENS_FACING_FRONT)
            )
        )

        // Gerçek kamera listesi (geniş açı dahil): ID'leri göster
        CameraHelper.listCameras(this) { list ->
            if (list.isNotEmpty()) {
                camList = list
                setCameraSpinner(list)
            }
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
            calib.detectionConfidence = binding.sliderConf.value
            calib.smoothingWindow = binding.sliderSmooth.value.toInt()
            calib.showLabels = binding.switchLabels.isChecked
            Toast.makeText(this, "Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }
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
