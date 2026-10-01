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
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.speedcam.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var calib: CalibrationManager
    private val tracker = ObjectTracker()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var detector: ObjectDetector? = null
    private var boundCameraKey: String? = null
    private var cameraStarting = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera(force = true) else Toast.makeText(
            this, "Kamera izni gerekli", Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        calib = CalibrationManager(this)
        ensureDetector()

        binding.btnCalib.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) startCamera(force = true)
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        tracker.pixelsPerMeter = calib.pixelsPerMeter
        tracker.smoothingWindow = calib.smoothingWindow
        try {
            binding.tvStatus.text = "Kalibrasyon: " + calib.summary()
        } catch (_: Exception) { }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) startCamera(force = false)
    }

    override fun onPause() {
        super.onPause()
        // Ekrandan çıkınca kamerayı bırak (arka planda çökme/ısınma olmasın)
        try {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
        } catch (_: Exception) { }
        boundCameraKey = null
    }

    private fun ensureDetector() {
        if (detector == null) {
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .build()
            detector = ObjectDetection.getClient(options)
        }
    }

    private fun startCamera(force: Boolean) {
        tracker.pixelsPerMeter = calib.pixelsPerMeter
        tracker.smoothingWindow = calib.smoothingWindow
        ensureDetector()

        val key = (calib.cameraId ?: "") + "|" + calib.cameraFacing
        if (!force && key == boundCameraKey) return
        if (cameraStarting) return
        cameraStarting = true

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraStarting = false
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
                val selector = CameraHelper.selectorFor(calib)

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val det = detector ?: return@addListener
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    var closed = false
                    fun closeOnce() {
                        if (!closed) {
                            closed = true
                            try { imageProxy.close() } catch (_: Exception) { }
                        }
                    }
                    try {
                        val mediaImage = imageProxy.image ?: run { closeOnce(); return@setAnalyzer }
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val image = try {
                            InputImage.fromMediaImage(mediaImage, rotation)
                        } catch (_: Exception) { closeOnce(); return@setAnalyzer }

                        det.process(image)
                            .addOnSuccessListener { objects ->
                                try {
                                    val viewW = binding.previewView.width.toFloat()
                                    val viewH = binding.previewView.height.toFloat()
                                    if (viewW <= 0 || viewH <= 0) return@addOnSuccessListener
                                    val imgW: Float
                                    val imgH: Float
                                    if (rotation == 90 || rotation == 270) {
                                        imgW = imageProxy.height.toFloat()
                                        imgH = imageProxy.width.toFloat()
                                    } else {
                                        imgW = imageProxy.width.toFloat()
                                        imgH = imageProxy.height.toFloat()
                                    }
                                    if (imgW <= 0 || imgH <= 0) return@addOnSuccessListener
                                    val scaleX = viewW / imgW
                                    val scaleY = viewH / imgH

                                    val dets = objects.mapNotNull { obj ->
                                        try {
                                            val b = obj.boundingBox
                                            val mapped = RectF(
                                                b.left * scaleX, b.top * scaleY,
                                                b.right * scaleX, b.bottom * scaleY
                                            )
                                            val label = obj.labels.firstOrNull()?.text
                                            val conf = obj.labels.firstOrNull()?.confidence ?: 0f
                                            if (obj.labels.isNotEmpty() && conf < calib.detectionConfidence) null
                                            else DetectedBox(mapped, label, conf)
                                        } catch (_: Exception) { null }
                                    }
                                    val now = System.currentTimeMillis()
                                    val tracked = try {
                                        tracker.update(dets, now)
                                    } catch (_: Exception) { emptyList() }
                                    // UI thread ile yarış olmasın: kopya gönder
                                    val snapshot = tracked.map {
                                        it.copy(box = RectF(it.box))
                                    }
                                    runOnUiThread {
                                        try {
                                            binding.overlay.setResults(
                                                snapshot, calib.speedUnit, calib.showLabels
                                            )
                                            binding.tvCount.text =
                                                "%d nesne".format(snapshot.size)
                                        } catch (_: Exception) { }
                                    }
                                } catch (_: Exception) { }
                            }
                            .addOnFailureListener { /* kareyi atla, çökme */ }
                            .addOnCompleteListener { closeOnce() }
                    } catch (_: Exception) { closeOnce() }
                }

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(this, selector, preview, analysis)
                    boundCameraKey = key
                } catch (e: Exception) {
                    boundCameraKey = null
                    runOnUiThread {
                        Toast.makeText(
                            this, "Kamera hatası: ${e.message}", Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this, "Kamera başlatılamadı: ${e.message}", Toast.LENGTH_LONG
                    ).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        try { detector?.close() } catch (_: Exception) { }
        detector = null
        cameraExecutor.shutdown()
    }
}
