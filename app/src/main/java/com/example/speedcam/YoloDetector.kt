package com.example.speedcam

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * YOLOv11-Nano (COCO 80 sınıf) ONNX Runtime çıkarımı.
 * Model: assets/yolo.onnx (640x640, çıktı [1,84,8400] transpoze).
 */
class YoloDetector(private val context: Context) {

    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null
    private val lock = Any()
    var ready = false
        private set

    fun ensure(): Boolean = synchronized(lock) {
        if (ready) return true
        return try {
            val f = File(context.filesDir, "yolo.onnx")
            if (!f.exists()) {
                context.assets.open("yolo.onnx").use { input ->
                    f.outputStream().use { output -> input.copyTo(output) }
                }
            }
            env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(4)
            }
            session = env!!.createSession(f.absolutePath, opts)
            ready = true
            true
        } catch (_: Exception) {
            ready = false
            false
        }
    }

    fun detect(bitmap: Bitmap, scoreThr: Float): List<DetectedBox> = synchronized(lock) {
        val sess = session ?: return emptyList()
        return try {
            val s = 640
            val scale = min(
                s / bitmap.width.toFloat(), s / bitmap.height.toFloat()
            )
            val nw = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val nh = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val resized = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
            val square = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(square)
            cv.drawColor(android.graphics.Color.rgb(114, 114, 114))
            val padX = (s - nw) / 2f
            val padY = (s - nh) / 2f
            cv.drawBitmap(resized, padX, padY, null)
            if (resized !== bitmap) {
                try { resized.recycle() } catch (_: Exception) { }
            }

            // NCHW float32
            val px = IntArray(s * s)
            square.getPixels(px, 0, s, 0, 0, s, s)
            try { square.recycle() } catch (_: Exception) { }
            val data = FloatArray(1 * 3 * s * s)
            var r = 0
            var g = s * s
            var b = 2 * s * s
            for (p in px) {
                data[r++] = ((p shr 16) and 0xFF) / 255f
                data[g++] = ((p shr 8) and 0xFF) / 255f
                data[b++] = (p and 0xFF) / 255f
            }
            val inputName = sess.inputNames?.firstOrNull() ?: "images"
            val input = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(data), longArrayOf(1, 3, s.toLong(), s.toLong())
            )
            val out: Array<Array<FloatArray>>
            sess.run(mapOf(inputName to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                out = (result[0].value as Array<Array<FloatArray>>)
            }
            try { input.close() } catch (_: Exception) { }
            val o = out[0] // [84][8400]
            if (o.size < 84) return emptyList()
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val dets = ArrayList<DetectedBox>(32)
            val cols = o[0].size
            for (j in 0 until cols) {
                // Sigmoid monoton: argmax ham logit ile aynı, sigmoid sadece kazananın
                var bc = -1
                var blogit = Float.NEGATIVE_INFINITY
                for (c in 4 until 84) {
                    val sc = o[c][j]
                    if (sc > blogit) {
                        blogit = sc
                        bc = c - 4
                    }
                }
                if (bc < 0 || bc >= COCO80.size) continue
                val bs =
                    (1.0 / (1.0 + Math.exp(-blogit.toDouble()))).toFloat()
                if (bs < scoreThr) continue
                var cx = o[0][j] / s
                var cy = o[1][j] / s
                var w = o[2][j] / s
                var h = o[3][j] / s
                // Piksel ise normale çevir (0..640 aralığı)
                if (cx > 1.5f || cy > 1.5f || w > 1.5f || h > 1.5f) {
                    cx /= s
                    cy /= s
                    w /= s
                    h /= s
                }
                var x0 = (cx * s - w * s / 2 - padX) / scale
                var y0 = (cy * s - h * s / 2 - padY) / scale
                var x1 = (cx * s + w * s / 2 - padX) / scale
                var y1 = (cy * s + h * s / 2 - padY) / scale
                x0 = x0.coerceIn(0f, bw)
                y0 = y0.coerceIn(0f, bh)
                x1 = x1.coerceIn(0f, bw)
                y1 = y1.coerceIn(0f, bh)
                if (x1 <= x0 || y1 <= y0) continue
                dets.add(
                    DetectedBox(
                        RectF(x0, y0, x1, y1),
                        DetectionLabels.of(COCO80[bc]), bs, COCO80[bc]
                    )
                )
                )
            }
            dets
        } catch (_: Exception) { emptyList() }
    }

    fun close() {
        synchronized(lock) {
            try { session?.close() } catch (_: Exception) { }
            session = null
            ready = false
        }
    }

    companion object {
        /** Ultralytics COCO sıra (0-79). */
        val COCO80 = arrayOf(
            "person", "bicycle", "car", "motorcycle", "airplane",
            "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench",
            "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat",
            "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife",
            "spoon", "bowl", "banana", "apple", "sandwich",
            "orange", "broccoli", "carrot", "hot dog", "pizza",
            "donut", "cake", "chair", "couch", "potted plant",
            "bed", "dining table", "toilet", "tv", "laptop",
            "mouse", "remote", "keyboard", "cell phone", "microwave",
            "oven", "toaster", "sink", "refrigerator", "book",
            "clock", "vase", "scissors", "teddy bear", "hair drier",
            "toothbrush"
        )
    }
}
