package com.example.speedcam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/** ImageProxy (YUV_420_888) -> dik çevrilmiş Bitmap. Analyzer thread'inde çağrılır. */
fun ImageProxy.toUprightBitmap(): Bitmap? {
    return try {
        if (format != ImageFormat.YUV_420_888) return null
        val nv21 = yuv420ToNv21() ?: return null
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        val bytes = out.toByteArray()
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rot = imageInfo.rotationDegrees
        if (rot != 0) {
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        bmp
    } catch (_: Exception) { null }
}

private fun ImageProxy.yuv420ToNv21(): ByteArray? {
    return try {
        val w = width
        val h = height
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val uvRowStrideU = uPlane.rowStride
        val uvRowStrideV = vPlane.rowStride
        val uvPixelStrideU = uPlane.pixelStride
        val uvPixelStrideV = vPlane.pixelStride

        val nv21 = ByteArray(w * h * 3 / 2)
        // Y düzlemi (stride dolgusu olabilir -> satır satır)
        var pos = 0
        for (row in 0 until h) {
            val base = row * yRowStride
            for (col in 0 until w) nv21[pos++] = yBuf.get(base + col)
        }
        // VU ardışık (NV21 sırası: önce V sonra U)
        val uvH = h / 2
        val uvW = w / 2
        for (row in 0 until uvH) {
            for (col in 0 until uvW) {
                nv21[pos++] = vBuf.get(row * uvRowStrideV + col * uvPixelStrideV)
                if (pos < nv21.size)
                    nv21[pos++] = uBuf.get(row * uvRowStrideU + col * uvPixelStrideU)
            }
        }
        nv21
    } catch (_: Exception) { null }
}
