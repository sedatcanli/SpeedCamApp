package com.example.speedcam

import android.content.Context
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import android.hardware.camera2.CameraCharacteristics
import androidx.core.content.ContextCompat

data class CamEntry(val cameraId: String, val label: String, val facing: Int)

object CameraHelper {

    fun listCameras(context: Context, done: (List<CamEntry>) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val out = provider.availableCameraInfos.mapNotNull { info ->
                    try {
                        val id = (info as? Camera2CameraInfo)?.cameraId ?: return@mapNotNull null
                        val facingLens = info.lensFacing
                        val facingStr = when (facingLens) {
                            CameraSelector.LENS_FACING_BACK -> "Arka"
                            CameraSelector.LENS_FACING_FRONT -> "Ön"
                            else -> "Diğer"
                        }
                        var extra = ""
                        try {
                            val c2 = Camera2CameraInfo.from(info)
                            val focal = c2.getCameraCharacteristic(
                                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                            )
                            if (focal != null && focal.isNotEmpty()) {
                                val min = focal.minOrNull() ?: 0f
                                extra = " • %.1fmm".format(min)
                            }
                        } catch (_: Exception) { }
                        CamEntry(id, "$facingStr (ID $id)$extra", facingLens)
                    } catch (_: Exception) { null }
                }.sortedBy { it.cameraId.toIntOrNull() ?: 99 }
                done(out)
            } catch (_: Exception) { done(emptyList()) }
        }, ContextCompat.getMainExecutor(context))
    }

    fun selectorFor(calib: CalibrationManager): CameraSelector {
        val id = calib.cameraId
        return if (id.isNullOrEmpty()) {
            CameraSelector.Builder().requireLensFacing(calib.cameraFacing).build()
        } else {
            CameraSelector.Builder()
                .requireLensFacing(calib.cameraFacing)
                .addCameraFilter { infos ->
                    infos.filter { (it as? Camera2CameraInfo)?.cameraId == id }
                        .ifEmpty { infos }
                }.build()
        }
    }
}
