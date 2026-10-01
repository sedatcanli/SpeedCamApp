package com.example.speedcam

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File

/**
 * COCO (80 sınıf: araba, insan, bisiklet...) dedektörü.
 * TF Hub ssd_mobilenet modeli Task-Vision ile çalışır.
 */
class CocoDetector(private val context: Context) {

    private var detector: ObjectDetector? = null
    private var builtThreshold = -1f
    var ready = false
        private set

    /** Eşik değiştiyse modeli yeniden kurar. Aynı tek thread'den çağrılmalı. */
    fun ensure(threshold: Float): Boolean {
        if (ready && builtThreshold == threshold) return true
        close()
        return try {
            val modelFile = File(context.filesDir, "mobilenet_ssd.tflite")
            if (!modelFile.exists()) {
                context.assets.open("mobilenet_ssd.tflite").use { input ->
                    modelFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val baseOptions = BaseOptions.builder().setNumThreads(4).build()
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setScoreThreshold(threshold)
                .setMaxResults(10)
                .setBaseOptions(baseOptions)
                .build()
            detector = ObjectDetector.createFromFileAndOptions(
                modelFile.absolutePath, options
            )
            builtThreshold = threshold
            ready = true
            true
        } catch (_: Exception) {
            ready = false
            false
        }
    }

    fun detect(bitmap: Bitmap): List<DetectedBox> {
        val det = detector ?: return emptyList()
        return try {
            val image = TensorImage.fromBitmap(bitmap)
            det.detect(image).mapNotNull { d ->
                try {
                    val cat = d.categories.maxByOrNull { it.score }
                    val conf = cat?.score ?: 0f
                    // Eşik altı etiketi at, kutuyu etiketsiz tutma (COCO modu)
                    if (conf <= 0f) null
                    else DetectedBox(
                        android.graphics.RectF(d.boundingBox),
                        TurkishLabels.of(cat?.label),
                        conf
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun close() {
        try { detector?.close() } catch (_: Exception) { }
        detector = null
        ready = false
    }
}
