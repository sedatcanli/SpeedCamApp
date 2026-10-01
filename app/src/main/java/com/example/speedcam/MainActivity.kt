package com.example.speedcam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.content.res.Configuration
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    private var camera: Camera? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera(force = true) else Toast.makeText(
            this, "Kamera izni gerekli", Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ölçüm sırasında ekran uyumasın
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        calib = CalibrationManager(this)
        ensureDetector()
        applyFullscreen()

        binding.btnCalib.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnExit.setOnClickListener {
            finishAffinity()
        }

        // Zoom kaydırıcısı (dijital zoom: telefoto yerine geçer, değer korunur)
        binding.sliderZoom.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                setZoom(value)
                calib.zoomRatio = value
            }
            binding.tvZoom.text = "%.1fx".format(value)
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

    private var detectorThreshold = -1f

    /**
     * Önce COCO modelli (araba, insan...) özel dedektörü dener,
     * model yoksa/bozuksa dahili genel dedektöre düşer.
     */
    private fun ensureDetector() {
        val want = calib.detectionConfidence
        if (detector != null && detectorThreshold == want) return
        try { detector?.close() } catch (_: Exception) { }
        detector = null
        detector = try {
            // Model assets'te yoksa burada patlar -> fallback çalışır
            assets.openFd("mobilenet_ssd.tflite").close()
            val localModel =
                com.google.mlkit.common.model.LocalModel.Builder()
                    .setAssetFilePath("mobilenet_ssd.tflite")
                    .build()
            val options =
                com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions.Builder(
                    localModel
                )
                    .setDetectorMode(
                        com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions.STREAM_MODE
                    )
                    .enableMultipleObjects()
                    .enableClassification()
                    .setClassificationConfidenceThreshold(want)
                    .setMaxPerObjectLabelCount(1)
                    .build()
            detectorThreshold = want
            ObjectDetection.getClient(options)
        } catch (_: Exception) {
            val fallback = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .build()
            detectorThreshold = want
            ObjectDetection.getClient(fallback)
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

                // Fiziksel kamera (geniş/tele) seçildiyse mantıksal kameraya bağlanıp
                // fiziksel ID'yi Camera2Interop ile dayat.
                val wantedId = calib.cameraId?.ifEmpty { null }
                val logicalId =
                    CameraHelper.logicalIdFor(this, wantedId, calib.cameraFacing)
                val physicalId =
                    if (wantedId != null && logicalId != null && wantedId != logicalId)
                        wantedId else null

                val previewBuilder = Preview.Builder()
                val analysisBuilder = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                if (physicalId != null) {
                    try {
                        Camera2Interop.Extender(previewBuilder)
                            .setPhysicalCameraId(physicalId)
                        Camera2Interop.Extender(analysisBuilder)
                            .setPhysicalCameraId(physicalId)
                    } catch (_: Exception) { }
                }
                val preview = previewBuilder.build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
                val selector = CameraHelper.selectorFor(calib)

                val analysis = analysisBuilder.build()

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
                                            val label = TurkishLabels.of(obj.labels.firstOrNull()?.text)
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
                    camera = provider.bindToLifecycle(this, selector, preview, analysis)
                    boundCameraKey = key
                    runOnUiThread { setupZoomSlider() }
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

    /** Yatayda tam ekran (durum/gezinme çubuklarını gizle), dikeyde normal. */
    private fun applyFullscreen() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val ctrl = WindowInsetsControllerCompat(window, window.decorView)
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ctrl.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                ctrl.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                ctrl.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        } catch (_: Exception) { }
    }

    private fun setupZoomSlider() {
        try {
            val cam = camera ?: return
            val state = cam.cameraInfo.zoomState.value
            val max = state?.maxZoomRatio?.coerceIn(1f, 20f) ?: 8f
            val min = state?.minZoomRatio?.coerceIn(0.5f, 1f) ?: 1f
            binding.sliderZoom.valueFrom = min
            binding.sliderZoom.valueTo = max
            // Kayıtlı zoom'u geri yükle (döndürmede 1x'e dönmesin)
            val saved = calib.zoomRatio.coerceIn(min, max)
            binding.sliderZoom.value = saved
            binding.tvZoom.text = "%.1fx".format(saved)
            setZoom(saved)
        } catch (_: Exception) { }
    }

    private fun setZoom(ratio: Float) {
        try {
            camera?.cameraControl?.setZoomRatio(ratio)
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { detector?.close() } catch (_: Exception) { }
        detector = null
        cameraExecutor.shutdown()
    }
}
