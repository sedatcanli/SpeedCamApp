package com.example.speedcam

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.speedcam.databinding.ActivityCalibrationBinding

/**
 * Kalibrasyon mantığı:
 * Kullanıcı gerçek dünyada bildiği bir uzunluğu (örn. 2 m'lik çizgi / kapı genişliği)
 * kamerada kaç piksel tuttuğunu girer -> px/m hesaplanır.
 * Hız formülü: hız(m/s) = (piksel_hareket / px_per_metre) / geçen_süre
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var calib: CalibrationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        calib = CalibrationManager(this)

        binding.etPpm.setText(calib.pixelsPerMeter.toString())
        binding.etKnown.setText("2.0")
        binding.etPixels.setText("200")

        updatePreview()

        binding.btnCalc.setOnClickListener {
            val known = binding.etKnown.text.toString().toFloatOrNull()
            val px = binding.etPixels.text.toString().toFloatOrNull()
            if (known == null || px == null || known <= 0 || px <= 0) {
                Toast.makeText(this, "Geçerli sayı girin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ppm = calib.calibrateFromReference(px, known)
            binding.etPpm.setText(ppm.toString())
            updatePreview()
            Toast.makeText(this, "Kalibre edildi: %.1f px/m".format(ppm), Toast.LENGTH_SHORT).show()
        }

        binding.btnSave.setOnClickListener {
            val ppm = binding.etPpm.text.toString().toFloatOrNull()
            if (ppm == null || ppm <= 0) {
                Toast.makeText(this, "Geçerli px/m girin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            calib.pixelsPerMeter = ppm
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updatePreview() {
        val ppm = binding.etPpm.text.toString().toFloatOrNull() ?: calib.pixelsPerMeter
        // Örnek: 100 px hareket, 0.2 sn'de ne hız yapar?
        val exampleMs = (100f / ppm.coerceAtLeast(1f)) / 0.2f
        binding.tvExample.text =
            "Örnek: 100px / 0.2sn = %.2f m/s = %.1f km/h".format(exampleMs, exampleMs * 3.6f)
    }
}
