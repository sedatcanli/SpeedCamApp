package com.example.speedcam

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Telefon hareketini izler: ivmeölçer (+ varsa jiroskop).
 * Eşik üst üste aşılırsa "hareket var", bir süre sakin kalınırsa normale döner.
 */
class MotionMonitor(context: Context) {

    var threshold = 0.6f
    var onStateChanged: ((moving: Boolean) -> Unit)? = null

    @Volatile
    var isMoving = false
        private set

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val gravity = FloatArray(3)
    private var gravityReady = false
    private var gyroMag = 0f
    private var overCount = 0
    private var underCount = 0

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    if (!gravityReady) {
                        // İlk örnekte yerçekimini doğrudan al (açılışta yanlış uyarı olmasın)
                        for (i in 0..2) gravity[i] = e.values[i]
                        gravityReady = true
                        return
                    }
                    // Yerçekimini yavaş takip et (hızlı hareketler skora girsin)
                    val alpha = 0.9f
                    for (i in 0..2) gravity[i] = alpha * gravity[i] + (1 - alpha) * e.values[i]
                    var sum = 0f
                    for (i in 0..2) {
                        val lin = e.values[i] - gravity[i]
                        sum += lin * lin
                    }
                    val score = sqrt(sum) + gyroMag * 3f
                    pushScore(score)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroMag = sqrt(
                        e.values[0] * e.values[0] +
                            e.values[1] * e.values[1] +
                            e.values[2] * e.values[2]
                    )
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun pushScore(score: Float) {
        if (score > threshold) {
            overCount++
            underCount = 0
            if (!isMoving && overCount >= 2) {
                isMoving = true
                try { onStateChanged?.invoke(true) } catch (_: Exception) { }
            }
        } else {
            underCount++
            overCount = 0
            if (isMoving && underCount >= 12) {
                isMoving = false
                try { onStateChanged?.invoke(false) } catch (_: Exception) { }
            }
        }
    }

    fun start() {
        try {
            accel?.let {
                sensorManager.registerListener(
                    listener, it, SensorManager.SENSOR_DELAY_GAME
                )
            }
            gyro?.let {
                sensorManager.registerListener(
                    listener, it, SensorManager.SENSOR_DELAY_GAME
                )
            }
        } catch (_: Exception) { }
    }

    fun stop() {
        try { sensorManager.unregisterListener(listener) } catch (_: Exception) { }
        gravityReady = false
        if (isMoving) {
            isMoving = false
            overCount = 0
            underCount = 0
            try { onStateChanged?.invoke(false) } catch (_: Exception) { }
        }
    }
}
