package com.example.speedcam

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat

data class CamEntry(val cameraId: String, val label: String, val facing: Int)

object CameraHelper {

    /**
     * Önce Camera2 ile TÜM kameraları (fiziksel geniş/tele dahil) listeler,
     * olmazsa CameraX listesine düşer.
     */
    fun listCameras(context: Context, done: (List<CamEntry>) -> Unit) {
        try {
            val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = mgr.cameraIdList
            if (!ids.isNullOrEmpty()) {
                val out = mutableListOf<CamEntry>()
                for (id in ids) {
                    try {
                        val c = mgr.getCameraCharacteristics(id)
                        val facingRaw =
                            c.get(CameraCharacteristics.LENS_FACING)
                                ?: CameraCharacteristics.LENS_FACING_BACK
                        val facingLens = when (facingRaw) {
                            CameraCharacteristics.LENS_FACING_FRONT ->
                                CameraSelector.LENS_FACING_FRONT
                            CameraCharacteristics.LENS_FACING_EXTERNAL ->
                                CameraSelector.LENS_FACING_BACK
                            else -> CameraSelector.LENS_FACING_BACK
                        }
                        val facingStr = when (facingLens) {
                            CameraSelector.LENS_FACING_FRONT -> "Ön"
                            else -> "Arka"
                        }
                        val focal =
                            c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        val focalStr = if (focal != null && focal.isNotEmpty())
                            " • %.1fmm".format(focal.minOrNull() ?: 0f) else ""
                        val isLogical = if (android.os.Build.VERSION.SDK_INT >= 28) {
                            try {
                                c.get(
                                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
                                )?.contains(
                                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                                ) == true
                            } catch (_: Throwable) { false }
                        } else false
                        val phys: Set<String> = try {
                            if (isLogical && android.os.Build.VERSION.SDK_INT >= 28) {
                                c.physicalCameraIds
                            } else emptySet()
                        } catch (_: Throwable) { emptySet() }
                        val kind = when {
                            facingLens == CameraSelector.LENS_FACING_FRONT -> "selfie"
                            isLogical && phys.size >= 2 -> "mantıksal"
                            else -> kindFromFocal(focal?.minOrNull())
                        }
                        out.add(
                            CamEntry(
                                id,
                                "$facingStr $kind (ID $id)$focalStr",
                                facingLens
                            )
                        )
                    } catch (_: Exception) { }
                }
                if (out.isNotEmpty()) {
                    out.sortWith(
                        compareBy({ it.facing }, { it.cameraId.toIntOrNull() ?: 99 })
                    )
                    done(out)
                    return
                }
            }
        } catch (_: Exception) { }
        // Yedek: CameraX listesi
        listViaCameraX(context, done)
    }

    private fun kindFromFocal(f: Float?): String = when {
        f == null -> "kamera"
        f < 3.0f -> "ultra-geniş"
        f < 4.5f -> "geniş"
        else -> "zoom/tele"
    }

    private fun listViaCameraX(context: Context, done: (List<CamEntry>) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val out = provider.availableCameraInfos.mapNotNull { info ->
                    try {
                        val id = (info as? Camera2CameraInfo)?.cameraId
                            ?: return@mapNotNull null
                        val facingLens = info.lensFacing
                        val facingStr = when (facingLens) {
                            CameraSelector.LENS_FACING_BACK -> "Arka"
                            CameraSelector.LENS_FACING_FRONT -> "Ön"
                            else -> "Diğer"
                        }
                        CamEntry(id, "$facingStr (ID $id)", facingLens)
                    } catch (_: Exception) { null }
                }.sortedBy { it.cameraId.toIntOrNull() ?: 99 }
                done(out)
            } catch (_: Exception) { done(emptyList()) }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Seçili kameranın hangi mantıksal kameraya ait olduğunu bulur. */
    fun logicalIdFor(context: Context, wantedId: String?, facing: Int): String? {
        if (wantedId.isNullOrEmpty()) return null
        try {
            val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            // Doğrudan mantıksal/fiziksel ID ise sorun yok, fiziksel ise üstünü bul
            for (id in mgr.cameraIdList) {
                if (id == wantedId) return id
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        val c = mgr.getCameraCharacteristics(id)
                        if (c.physicalCameraIds.contains(wantedId)) return id
                    }
                } catch (_: Throwable) { }
            }
        } catch (_: Exception) { }
        return null
    }

    fun selectorFor(calib: CalibrationManager): CameraSelector {
        // Fiziksel ID filtreleme bağlama anında Camera2Interop ile yapılır,
        // burada sadece yön filtresi yeterli.
        return CameraSelector.Builder().requireLensFacing(calib.cameraFacing).build()
    }
}
