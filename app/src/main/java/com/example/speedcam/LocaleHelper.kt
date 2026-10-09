package com.example.speedcam

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Uygulama dili (varsayılan İngilizce + 30 dil). */
object LocaleHelper {

    /** tag -> kendi dilinde ad */
    val LANGS = listOf(
        "" to "System default",
        "en" to "English",
        "tr" to "Türkçe",
        "de" to "Deutsch",
        "fr" to "Français",
        "es" to "Español",
        "it" to "Italiano",
        "pt" to "Português",
        "ru" to "Русский",
        "uk" to "Українська",
        "ar" to "العربية",
        "fa" to "فارسی",
        "zh-CN" to "中文简体",
        "zh-TW" to "中文繁體",
        "ja" to "日本語",
        "ko" to "한국어",
        "hi" to "हिन्दी",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Melayu",
        "th" to "ไทย",
        "vi" to "Tiếng Việt",
        "pl" to "Polski",
        "nl" to "Nederlands",
        "el" to "Ελληνικά",
        "he" to "עברית",
        "hu" to "Magyar",
        "ro" to "Română",
        "cs" to "Čeština",
        "bg" to "Български",
        "hr" to "Hrvatski",
        "sr" to "Српски",
        "sv" to "Svenska"
    )

    fun apply(tag: String) {
        try {
            if (tag.isEmpty()) {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.getEmptyLocaleList()
                )
            } else {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(tag)
                )
            }
        } catch (_: Exception) { }
    }
}
