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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        calib = CalibrationManager(this)

        // Kamera seçimi
        val cams = listOf("Arka Kamera", "Ön Kamera")
        binding.spCamera.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cams)
        binding.spCamera.setSelection(
            if (calib.cameraFacing == CameraSelector.LENS_FACING_BACK) 0 else 1
        )

        // Birim seçimi
        val units = listOf(SpeedUnit.KMH, SpeedUnit.MS, SpeedUnit.MPH)
        binding.spUnit.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, units)
        binding.spUnit.setSelection(units.indexOf(calib.speedUnit).coerceAtLeast(0))

        binding.sliderConf.value = calib.detectionConfidence
        binding.sliderSmooth.value = calib.smoothingWindow.toFloat()
        binding.switchLabels.isChecked = calib.showLabels

        binding.btnSaveSettings.setOnClickListener {
            calib.cameraFacing = if (binding.spCamera.selectedItemPosition == 0)
                CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            calib.speedUnit = units[binding.spUnit.selectedItemPosition]
            calib.detectionConfidence = binding.sliderConf.value
            calib.smoothingWindow = binding.sliderSmooth.value.toInt()
            calib.showLabels = binding.switchLabels.isChecked
            Toast.makeText(this, "Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
