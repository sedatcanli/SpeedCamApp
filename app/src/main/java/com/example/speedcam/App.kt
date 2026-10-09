package com.example.speedcam

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        try {
            val tag = getSharedPreferences("speedcam_calib", MODE_PRIVATE)
                .getString("langTag", "en") ?: "en"
            LocaleHelper.apply(tag)
        } catch (_: Exception) { }
    }
}
