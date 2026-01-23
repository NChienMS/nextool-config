package com.ycsoft.printernt

import android.content.Context
import android.os.Build
import java.util.Locale

object LocaleManager {
    private const val PREF_NAME = "locale_prefs"
    private const val KEY_LANG  = "lang"

    /** Lưu mã ngôn ngữ ("vi"/"en") */
    fun saveLang(ctx: Context, lang: String) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, lang)
            .apply()
    }

    /** Đọc ngôn ngữ đã lưu, có thể trả về null nếu chưa từng lưu */
    fun loadSavedLang(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, null)
    }

    /** Đọc ngôn ngữ đã lưu nhưng luôn có mặc định "vi" để dùng nhanh trong nội bộ */
    fun getSavedLang(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, null) ?: "vi"
    }

    /** Bọc context theo locale hiện tại (dùng trong attachBaseContext) */
    fun wrapContext(base: Context): Context {
        val code = getSavedLang(base)       // luôn có default "vi"
        val locale = Locale(code)
        Locale.setDefault(locale)

        val res = base.resources
        val config = res.configuration

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            base.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            run {
                config.setLocale(locale)
                res.updateConfiguration(config, res.displayMetrics)
                base
            }
        }
    }
}
