package com.example.speedcam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.speedcam.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var calib: CalibrationManager
    private val tracker = ObjectTracker()
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else Toast.makeText(
            this, "Kamera izni gerekli", Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        calib = CalibrationManager(this)

        binding.btnCalib.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        // Ayarlar değişmiş olabilir -> takipçiyi güncelle
        tracker.pixelsPerMeter = calib.pixelsPerMeter
        tracker.smoothingWindow = calib.smoothingWindow
        binding.tvStatus.text = "Kalibrasyon: " + calib.summary()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) startCamera()
    }

    private fun startCamera() {
        tracker.pixelsPerMeter = calib.pixelsPerMeter
        tracker.smoothingWindow = calib.smoothingWindow

        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
        val detector = ObjectDetection.getClient(options)

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val selector = CameraSelector.Builder()
                .requireLensFacing(calib.cameraFacing)
                .build()

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val image = InputImage.fromMediaImage(mediaImage, rotation)
                    detector.process(image)
                        .addOnSuccessListener { objects ->
                            val viewW = binding.previewView.width.toFloat()
                            val viewH = binding.previewView.height.toFloat()
                            val imgW: Float
                            val imgH: Float
                            if (rotation == 90 || rotation == 270) {
                                imgW = imageProxy.height.toFloat()
                                imgH = imageProxy.width.toFloat()
                            } else {
                                imgW = imageProxy.width.toFloat()
                                imgH = imageProxy.height.toFloat()
                            }
                            val scaleX = if (imgW > 0) viewW / imgW else 1f
                            val scaleY = if (imgH > 0) viewH / imgH else 1f

                            val dets = objects.mapNotNull { obj ->
                                val b = obj.boundingBox
                                val mapped = RectF(
                                    b.left * scaleX, b.top * scaleY,
                                    b.right * scaleX, b.bottom * scaleY
                                )
                                val label = obj.labels.firstOrNull()?.text
                                val conf = obj.labels.firstOrNull()?.confidence ?: 0f
                                // Etiketsiz kutuları tut, etiketlilerde eşik uygula
                                if (obj.labels.isNotEmpty() && conf < calib.detectionConfidence) null
                                else DetectedBox(mapped, label, conf)
                            }
                            val now = System.currentTimeMillis()
                            val tracked = tracker.update(dets, now)
                            runOnUiThread {
                                binding.overlay.setResults(
                                    tracked, calib.speedUnit, calib.showLabels
                                )
                                binding.tvCount.text = "%d nesne".format(tracked.size)
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                } else imageProxy.close()
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, analysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Kamera hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
