package com.example.speedcam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.min

/**
 * YOLOv8-Nano (COCO 80 sınıf) doğrudan TFLite çıkarımı.
 * Model: assets/yolov8n.tflite (640x640, fp32, NMS'siz çıktı [1,84,8400]).
 */
class YoloDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val lock = Any()
    var ready = false
        private set

    fun ensure(): Boolean = synchronized(lock) {
        if (ready) return true
        return try {
            val f = File(context.filesDir, "yolov8n.tflite")
            if (!f.exists()) {
                context.assets.open("yolov8n.tflite").use { input ->
                    f.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val opts = Interpreter.Options()
                .setNumThreads(4)
                .setUseXNNPACK(true)
            interpreter = Interpreter(loadModel(f), opts)
            ready = true
            true
        } catch (_: Exception) {
            ready = false
            false
        }
    }

    fun detect(bitmap: Bitmap, scoreThr: Float): List<DetectedBox> = synchronized(lock) {
        val tf = interpreter ?: return emptyList()
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

            val input = ByteBuffer.allocateDirect(s * s * 3 * 4)
                .order(ByteOrder.nativeOrder())
            val px = IntArray(s * s)
            square.getPixels(px, 0, s, 0, 0, s, s)
            try { square.recycle() } catch (_: Exception) { }
            for (p in px) {
                input.putFloat(((p shr 16) and 0xFF) / 255f)
                input.putFloat(((p shr 8) and 0xFF) / 255f)
                input.putFloat((p and 0xFF) / 255f)
            }
            input.rewind()

            val out = Array(1) { Array(84) { FloatArray(8400) } }
            tf.run(input, out)
            val o = out[0]
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val dets = ArrayList<DetectedBox>(32)
            for (j in 0 until 8400) {
                var bc = -1
                var bs = scoreThr
                for (c in 4 until 84) {
                    val sc = o[c][j]
                    if (sc > bs) {
                        bs = sc
                        bc = c - 4
                    }
                }
                if (bc < 0) continue
                val cx = o[0][j] * s
                val cy = o[1][j] * s
                val w = o[2][j] * s
                val h = o[3][j] * s
                var x0 = (cx - w / 2 - padX) / scale
                var y0 = (cy - h / 2 - padY) / scale
                var x1 = (cx + w / 2 - padX) / scale
                var y1 = (cy + h / 2 - padY) / scale
                x0 = x0.coerceIn(0f, bw)
                y0 = y0.coerceIn(0f, bh)
                x1 = x1.coerceIn(0f, bw)
                y1 = y1.coerceIn(0f, bh)
                if (x1 <= x0 || y1 <= y0) continue
                dets.add(
                    DetectedBox(
                        RectF(x0, y0, x1, y1),
                        TurkishLabels.of(COCO80[bc]), bs
                    )
                )
            }
            dets
        } catch (_: Exception) { emptyList() }
    }

    fun close() {
        synchronized(lock) {
            try { interpreter?.close() } catch (_: Exception) { }
            interpreter = null
            ready = false
        }
    }

    private fun loadModel(f: File): ByteBuffer {
        val fis = FileInputStream(f)
        val ch = fis.channel
        val mapped = ch.map(FileChannel.MapMode.READ_ONLY, 0, f.length())
        ch.close()
        fis.close()
        return mapped
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
