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
            this, getString(R.string.msg_camera_perm), Toast.LENGTH_LONG
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
        yolo = YoloDetector(this)
        motion = MotionMonitor(this)
        binding.overlay.onRulerTap = { showDistanceDialog() }
        motion.onStateChanged = { moving ->            runOnUiThread {
                try {
                    binding.tvMotion.visibility =
                        if (moving) android.view.View.VISIBLE else android.view.View.GONE
                    binding.overlay.setWarning(moving)
                    if (moving) binding.tvCount.text = getString(R.string.paused)
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
            try { yolo.close() } catch (_: Exception) { }
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
        try {
            // Otomatik kalibrasyon için sınıf -> boyut önbelleği (metre)
            val m = HashMap<String, Pair<Float, Float>>()
            for (o in TargetActivity.OPTIONS) {
                if (o.coco.size == 1 && o.id == o.coco.first()) {
                    val w = calib.dimWcm(o.id) / 100f
                    val h = calib.dimHcm(o.id) / 100f
                    if (w > 0f && h > 0f) m[o.id] = Pair(w, h)
                }
            }
            dimCache = m
        } catch (_: Exception) { }
        try {
            if (calib.calibMode != "auto") autoScale.clear()
        } catch (_: Exception) { }
        try {
            calib.refreshOptics(this, false)
            // Kamera değiştiyse FOV'u yeni kameradan tazele
            val curKey = (calib.cameraId ?: "") + "|" + calib.cameraFacing
            val readKey = (calib.opticsCamId ?: "") + "|" + calib.opticsFacing
            if (calib.fovHdeg > 0f && readKey != curKey) {
                calib.refreshOptics(this, true)
            }
        } catch (_: Exception) { }
        motion.threshold = calib.motionThreshold
        motion.start()
        try {
            binding.tvStatus.text = getString(R.string.status_calibration, calib.summary(this))
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
    private lateinit var yolo: YoloDetector
    private lateinit var motion: MotionMonitor
    private var reportedEngine: String? = null
    private val autoScale = AutoScaleEstimator()
    private var dimCache: Map<String, Pair<Float, Float>> = emptyMap()

    /**
     * Dahili genel dedektor (yedek). Birincil motor YOLOv8'dir.
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

        val key = (calib.cameraId ?: "") + "|" + calib.cameraFacing + "|" + calib.analysisRes
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
                val (aw, ah) = calib.analysisSize()
                val analysisBuilder = ImageAnalysis.Builder()
                    .setTargetResolution(Size(aw, ah))
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
                        // 1) Birincil: YOLOv8 motoru (araba, insan... Turkce etiket)
                        if (yolo.ensure()) {
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
                                // PreviewView FILL modda kırpar: tek ölçek + kaydırma
                                val fillS = kotlin.math.max(viewW / imgW, viewH / imgH)
                                val fillOffX = (viewW - imgW * fillS) / 2f
                                val fillOffY = (viewH - imgH * fillS) / 2f
                                val raw = yolo.detect(bmp, calib.detectionConfidence)
                                try { bmp.recycle() } catch (_: Exception) { }
                                val maxF = try { calib.maxBoxAreaPct } catch (_: Exception) { 1f }
                                val clean = BoxFilter.prepare(raw, imgW, imgH, maxF)
                                val dets = clean.mapNotNull { d ->
                                    try {
                                        RectF(
                                            d.box.left * fillS + fillOffX,
                                            d.box.top * fillS + fillOffY,
                                            d.box.right * fillS + fillOffX,
                                            d.box.bottom * fillS + fillOffY
                                        ).let { DetectedBox(it, d.label, d.confidence, d.eng) }
                                    } catch (_: Exception) { null }
                                }
                                postDetections(dets, "YOLO", imgW, imgH)
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
                                    // PreviewView FILL modda kırpar: tek ölçek + kaydırma
                                    val fillS = kotlin.math.max(viewW / imgW, viewH / imgH)
                                    val fillOffX = (viewW - imgW * fillS) / 2f
                                    val fillOffY = (viewH - imgH * fillS) / 2f

                                    val rawFb = objects.mapNotNull { obj ->
                                        try {
                                            val b = obj.boundingBox
                                            val engRaw = obj.labels.firstOrNull()?.text
                                            val label = DetectionLabels.of(engRaw)
                                            val conf = obj.labels.firstOrNull()?.confidence ?: 0f
                                            if (obj.labels.isNotEmpty() && conf < calib.detectionConfidence) null
                                            else DetectedBox(RectF(b), label, conf, engRaw?.lowercase())
                                        } catch (_: Exception) { null }
                                    }
                                    val maxFb = try { calib.maxBoxAreaPct } catch (_: Exception) { 1f }
                                    val dets = BoxFilter.prepare(rawFb, imgW, imgH, maxFb).map { d ->
                                        DetectedBox(
                                            RectF(
                                                d.box.left * fillS + fillOffX,
                                                d.box.top * fillS + fillOffY,
                                                d.box.right * fillS + fillOffX,
                                                d.box.bottom * fillS + fillOffY
                                            ), d.label, d.confidence, d.eng
                                        )
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
                            this, getString(R.string.msg_camera_error, e.message), Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this, getString(R.string.msg_camera_error, e.message), Toast.LENGTH_LONG
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

    /** Cetvele dokununca: uzaklık + referans uzunluk (logaritmik). */
    private fun showDistanceDialog() {
        try {
            val density = resources.displayMetrics.density
            val pad = (16 * density).toInt()
            val tvDist = android.widget.TextView(this).apply {
                textSize = 20f
                gravity = android.view.Gravity.CENTER
            }
            val slider = com.google.android.material.slider.Slider(this).apply {
                valueFrom = 0f
                valueTo = 1000f
                stepSize = 1f
            }
            val tvRef = android.widget.TextView(this).apply {
                textSize = 20f
                gravity = android.view.Gravity.CENTER
            }
            val sliderRef = com.google.android.material.slider.Slider(this).apply {
                valueFrom = 0f
                valueTo = 1000f
                stepSize = 1f
            }
            fun distOf(v: Float): Float {
                val t = (v / 1000f).coerceIn(0f, 1f)
                return (0.5 * Math.pow(200.0, t.toDouble())).toFloat()
            }
            fun sliderOf(d: Float): Float {
                val c = d.coerceIn(0.5f, 100f)
                return (1000f * Math.log((c / 0.5).toDouble()) / Math.log(200.0)).toFloat()
                    .coerceIn(0f, 1000f)
            }
            fun refOf(v: Float): Float {
                val t = (v / 1000f).coerceIn(0f, 1f)
                return (0.1 * Math.pow(500.0, t.toDouble())).toFloat()
            }
            fun sliderOfRef(d: Float): Float {
                val c = d.coerceIn(0.1f, 50f)
                return (1000f * Math.log((c / 0.1).toDouble()) / Math.log(500.0)).toFloat()
                    .coerceIn(0f, 1000f)
            }
            fun fmt(d: Float): String =
                if (d < 10f) "%.1f m".format(d) else "%.0f m".format(d)
            SliderUtils.setSafe(slider, sliderOf(calib.distanceM))
            tvDist.text = getString(R.string.dlg_distance, fmt(distOf(slider.value)))
            slider.addOnChangeListener { _, v, _ ->
                val d = distOf(v)
                tvDist.text = getString(R.string.dlg_distance, fmt(d))
                calib.distanceM = d.coerceIn(0.5f, 500f)
            }
            SliderUtils.setSafe(sliderRef, sliderOfRef(calib.refLenM))
            tvRef.text = getString(R.string.dlg_reference, fmt(refOf(sliderRef.value)))
            sliderRef.addOnChangeListener { _, v, _ ->
                val d = refOf(v)
                tvRef.text = getString(R.string.dlg_reference, fmt(d))
                calib.refLenM = d.coerceIn(0.05f, 100f)
            }
            // İnce ayar: ölçek katsayısını binde 2 adımlarla dürt
            val tvCorr = android.widget.TextView(this).apply {
                textSize = 18f
                gravity = android.view.Gravity.CENTER
            }
            fun corrText(): String = getString(R.string.scale_value, calib.scaleCorr)
            tvCorr.text = corrText()
            val btnMinus = android.widget.Button(this).apply { text = "−" }
            val btnPlus = android.widget.Button(this).apply { text = "+" }
            btnMinus.setOnClickListener {
                calib.scaleCorr = (calib.scaleCorr - 0.002f).coerceIn(0.3f, 3f)
                tvCorr.text = corrText()
            }
            btnPlus.setOnClickListener {
                calib.scaleCorr = (calib.scaleCorr + 0.002f).coerceIn(0.3f, 3f)
                tvCorr.text = corrText()
            }
            val rowCorr = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                addView(
                    btnMinus,
                    android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(
                    tvCorr,
                    android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                )
                addView(
                    btnPlus,
                    android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                addView(tvDist, lp)
                addView(slider, lp)
                addView(tvRef, lp)
                addView(sliderRef, lp)
                val tvFine = android.widget.TextView(this@MainActivity).apply {
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                    text = getString(R.string.dlg_fine)
                }
                addView(tvFine, lp)
                addView(rowCorr, lp)
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.dlg_ruler_title))
                .setView(layout)
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    reportedEngine = null
                    try {
                        binding.tvStatus.text = getString(R.string.status_calibration, calib.summary(this))
                    } catch (_: Exception) { }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
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
                    binding.overlay.setAutoInfo(null)
                    binding.tvCount.text = getString(R.string.paused)
                } catch (_: Exception) { }
            }
            return
        }
        // Ölçek: trig (Manuel) veya hedef boyutlarından otomatik (Otomatik).
        // Hepsi GÖRÜNÜM pikseli cinsinden (kutular görünümde).
        var mPerViewPx = 0f
        var effEngine = engine
        var autoText: String? = null
        try {
            val zoom = try {
                camera?.cameraInfo?.zoomState?.value?.zoomRatio
                    ?: calib.zoomRatio
            } catch (_: Exception) { calib.zoomRatio }.coerceIn(0.5f, 20f)
            val (mx, my) = calib.metersPerPixel(imgW, imgH)
            val viewW = try { binding.previewView.width.toFloat() } catch (_: Exception) { 0f }
            val viewH = try { binding.previewView.height.toFloat() } catch (_: Exception) { 0f }
            val fillS = if (viewW > 0f && viewH > 0f && imgW > 0f && imgH > 0f) {
                kotlin.math.max(viewW / imgW, viewH / imgH)
            } else 1f
            var ex = mx / zoom / fillS
            var ey = my / zoom / fillS
            tracker.ghostMs = (calib.ghostSec * 1000).toLong()
            val auto = try { calib.calibMode == "auto" } catch (_: Exception) { false }
            if (auto) {
                val nowA = System.currentTimeMillis()
                for (d in dets) {
                    try {
                        val e = d.eng ?: continue
                        val dims = dimCache[e] ?: continue
                        val bw = d.box.width()
                        val bh = d.box.height()
                        if (bw <= 0f || bh <= 0f) continue
                        val obs = kotlin.math.sqrt(
                            (dims.first * dims.second / 10000f / (bw * bh)).toDouble()
                        ).toFloat()
                        // Trig ölçeğin 0.2x-5x bandı dışı = yanlış sınıf/boyut, at
                        if (obs > ex * 0.2f && obs < ex * 5f) {
                            autoScale.add(obs, nowA)
                        }
                    } catch (_: Exception) { }
                }
                val med = try { autoScale.median(nowA) } catch (_: Exception) { null }
                if (med != null && med > 0f) {
                    ex = med
                    ey = med
                    effEngine = engine + " •OTO"
                    try {
                        val n = autoScale.count(nowA)
                        autoText = getString(R.string.auto_live, med * 100f, n)
                    } catch (_: Exception) { }
                } else {
                    effEngine = engine + " •OTO?"
                }
            }
            tracker.metersPerPixelX = ex
            tracker.metersPerPixelY = ey
            mPerViewPx = ex
        } catch (_: Exception) { }
        val now = System.currentTimeMillis()
        // Hedef filtresi: seçili değilse takibe bile girmez
        val useAll = try { calib.targetAll } catch (_: Exception) { true }
        val useDets = if (useAll) dets else try {
            val allow = calib.targetCoco()
            val allowOther = calib.targetIds().contains("other")
            dets.filter { d ->
                val e = d.eng
                if (e != null && allow.contains(e)) true
                else allowOther && (e == null || !TargetActivity.ALL_COCO.contains(e))
            }
        } catch (_: Exception) { dets }
        val tracked = try {
            tracker.update(useDets, now)
        } catch (_: Exception) { emptyList() }
        // Yavaş nesne filtresi: 2 sn ortalaması eşiğin altındaysa gösterme
        // (takip sürer, hızlanınca yeniden görünür)
        val minS = try { calib.minSpeedMs } catch (_: Exception) { 0f }
        val visible = if (minS > 0f) {
            try { tracked.filter { it.avgSpeedMs >= minS } }
            catch (_: Exception) { tracked }
        } else tracked
        // UI thread ile yarış olmasın: kopya gönder
        val snapshot = visible.map {
            it.copy(box = RectF(it.box))
        }
        runOnUiThread {
            try {
                binding.overlay.setResults(
                    snapshot, calib.speedUnit, calib.showLabels
                )
                if (mPerViewPx > 0f) binding.overlay.setScaleBar(mPerViewPx)
                binding.overlay.setAutoInfo(autoText)
                binding.tvCount.text = getString(R.string.objects_count, snapshot.size)
                if (reportedEngine != effEngine) {
                    reportedEngine = effEngine
                    binding.tvStatus.text =
                        getString(R.string.status_calibration, calib.summary(this)) + " • " + effEngine
                }
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { motion.stop() } catch (_: Exception) { }
        // Önce kare akışını durdur, analyzer'ın işi bitsin, sonra kapat.
        // Yoksa native detect() ortasında close() çöker.
        try {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
        } catch (_: Exception) { }
        cameraExecutor.shutdown()
        try {
            cameraExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) { }
        try { detector?.close() } catch (_: Exception) { }
        detector = null
        try { yolo.close() } catch (_: Exception) { }
    }
}
