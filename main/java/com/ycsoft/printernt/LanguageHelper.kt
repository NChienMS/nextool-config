package com.ycsoft.printernt

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ycsoft.printernt.LocaleManager

object LanguageHelper {

    /** Áp dụng ngôn ngữ đã lưu */
    fun applySavedLanguage(context: Context) {
        val code = LocaleManager.loadSavedLang(context) ?: "vi"
        setLanguage(code)
    }

    /** Đổi ngôn ngữ và lưu lại */
    fun changeLanguage(context: Context, code: String) {
        LocaleManager.saveLang(context, code)
        setLanguage(code)
    }

    /** Hàm áp dụng thực sự bằng AppCompat */
    private fun setLanguage(code: String) {
        val tags = if (code.lowercase() == "vi") "vi" else "en"
        val locales = LocaleListCompat.forLanguageTags(tags)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
