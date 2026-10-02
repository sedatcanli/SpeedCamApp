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
        coco = CocoDetector(this)
        motion = MotionMonitor(this)
        motion.onStateChanged = { moving ->            runOnUiThread {
                try {
                    binding.tvMotion.visibility =
                        if (moving) android.view.View.VISIBLE else android.view.View.GONE
                    binding.overlay.setWarning(moving)
                    if (moving) binding.tvCount.text = "Duraklatıldı"
                } catch (_: Exception) { }
            }
        }
        applyFullscreen()

        binding.btnCalib.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnExit.setOnClickListener {
            // Kamerayı bırak, görevlerden kaldır, işlemi sonlandır
            try {
                ProcessCameraProvider.getInstance(this).get().unbindAll()
            } catch (_: Exception) { }
            try { detector?.close() } catch (_: Exception) { }
            try { coco.close() } catch (_: Exception) { }
            finishAndRemoveTask()
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        // Zoom kaydırıcısı (dijital zoom: telefoto yerine geçer, değer korunur)
        binding.sliderZoom.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val clean = SliderUtils.snap(binding.sliderZoom, value)
                setZoom(clean)
                calib.zoomRatio = clean
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
        tracker.smoothingWindow = calib.smoothingWindow
        try { calib.refreshOptics(this, false) } catch (_: Exception) { }
        motion.threshold = calib.motionThreshold
        motion.start()
        try {
            binding.tvStatus.text = "Kalibrasyon: " + calib.summary()
        } catch (_: Exception) { }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) startCamera(force = false)
    }

    override fun onPause() {
        super.onPause()
        try { motion.stop() } catch (_: Exception) { }
        // Ekrandan çıkınca kamerayı bırak (arka planda çökme/ısınma olmasın)
        try {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
        } catch (_: Exception) { }
        boundCameraKey = null
    }

    private var detectorThreshold = -1f
    private lateinit var coco: CocoDetector
    private lateinit var motion: MotionMonitor
    private var reportedEngine: String? = null

    /**
     * Dahili genel dedektör (yedek). Birincil motor COCO'dur.
     */
    private fun ensureDetector() {
        if (detector != null) return
        try { detector?.close() } catch (_: Exception) { }
        detector = null
        val fallback = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
        detector = ObjectDetection.getClient(fallback)
        detectorThreshold = calib.detectionConfidence
    }

    private fun startCamera(force: Boolean) {
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
                        // 1) Birincil: COCO motoru (araba, insan... Türkçe etiket)
                        if (coco.ensure(calib.detectionConfidence)) {
                            try {
                                val bmp = imageProxy.toUprightBitmap()
                                closeOnce()
                                if (bmp == null) return@setAnalyzer
                                val viewW = binding.previewView.width.toFloat()
                                val viewH = binding.previewView.height.toFloat()
                                val imgW = bmp.width.toFloat()
                                val imgH = bmp.height.toFloat()
                                if (viewW <= 0 || viewH <= 0 || imgW <= 0 || imgH <= 0) {
                                    try { bmp.recycle() } catch (_: Exception) { }
                                    return@setAnalyzer
                                }
                                val scaleX = viewW / imgW
                                val scaleY = viewH / imgH
                                val raw = coco.detect(bmp)
                                try { bmp.recycle() } catch (_: Exception) { }
                                val dets = raw.mapNotNull { d ->
                                    try {
                                        RectF(
                                            d.box.left * scaleX, d.box.top * scaleY,
                                            d.box.right * scaleX, d.box.bottom * scaleY
                                        ).let { DetectedBox(it, d.label, d.confidence) }
                                    } catch (_: Exception) { null }
                                }
                                postDetections(dets, "COCO", imgW, imgH)
                            } catch (_: Exception) { closeOnce() }
                            return@setAnalyzer
                        }
                        // 2) Yedek: dahili genel dedektör
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
                                    postDetections(dets, "Genel", imgW, imgH)
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

    /** Her yönde gerçek tam ekran; çubuklar kaydırınca geçici görünür. */
    private fun applyFullscreen() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val ctrl = WindowInsetsControllerCompat(window, window.decorView)
            ctrl.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val density = resources.displayMetrics.density
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
                )
                binding.tvStatus.setPadding(
                    (8 * density).toInt(), bars.top + (8 * density).toInt(),
                    (8 * density).toInt(), (8 * density).toInt()
                )
                binding.bottomBar.setPadding(
                    (6 * density).toInt(), (6 * density).toInt(),
                    (6 * density).toInt(), bars.bottom + (6 * density).toInt()
                )
                insets
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
            SliderUtils.setSafe(binding.sliderZoom, saved)
            val applied = try { binding.sliderZoom.value } catch (_: Exception) { saved }
            binding.tvZoom.text = "%.1fx".format(applied)
            setZoom(applied)
        } catch (_: Exception) { }
    }

    private fun setZoom(ratio: Float) {
        try {
            camera?.cameraControl?.setZoomRatio(ratio)
        } catch (_: Exception) { }
    }

    private fun postDetections(
        dets: List<DetectedBox>, engine: String, imgW: Float, imgH: Float
    ) {
        // Telefon hareket ediyorsa ölçüm yapma (yanlış hız üretmemek için)
        if (motion.isMoving) {
            runOnUiThread {
                try {
                    binding.overlay.setResults(emptyList(), calib.speedUnit, calib.showLabels)
                    binding.overlay.setWarning(true)
                    binding.tvCount.text = "Duraklatıldı"
                } catch (_: Exception) { }
            }
            return
        }
        // Kare boyutuna göre trigonometrik ölçeği güncelle
        try {
            val (mx, my) = calib.metersPerPixel(imgW, imgH)
            tracker.metersPerPixelX = mx
            tracker.metersPerPixelY = my
        } catch (_: Exception) { }
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
                binding.tvCount.text = "%d nesne".format(snapshot.size)
                if (reportedEngine != engine) {
                    reportedEngine = engine
                    binding.tvStatus.text =
                        "Kalibrasyon: " + calib.summary() + " • " + engine
                }
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { motion.stop() } catch (_: Exception) { }
        try { detector?.close() } catch (_: Exception) { }
        detector = null
        try { coco.close() } catch (_: Exception) { }
        cameraExecutor.shutdown()
    }
}
