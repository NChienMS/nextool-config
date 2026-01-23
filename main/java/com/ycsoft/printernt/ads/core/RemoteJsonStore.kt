package com.ycsoft.printernt.ads.core
import android.content.Context
import android.util.Log

/**
 * RemoteJsonStore
 * ----------------------------
 * Nhiệm vụ:
 *  - Lưu JSON cache (raw string) vào SharedPreferences
 *  - Đọc lại JSON cache khi cần
 *
 * Không làm:
 *  - Không gọi mạng
 *  - Không parse/validate JSON
 */
object RemoteJsonStore {

    private const val PREF_NAME = "remote_json_store"
    private const val KEY_JSON = "json_cache"
    private const val KEY_VERSION = "json_version"
    private const val KEY_UPDATED_AT = "json_updated_at"

    fun save(context: Context, json: String, version: Int, updatedAt: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, json)
            .putInt(KEY_VERSION, version)
            .putString(KEY_UPDATED_AT, updatedAt ?: "")
            .apply()

        // ===== START LOG =====
        Log.d(
            "RemoteCore",
            "Lưu cache JSON thành công | version=$version | updatedAt=${updatedAt ?: "không có"}"
        )
        // ===== END LOG =====
    }

    fun loadJson(context: Context): String? {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null)

        // ===== START LOG =====
        Log.d(
            "RemoteCore",
            "Đọc cache JSON | tồn tại = ${json != null}"
        )
        // ===== END LOG =====

        return json
    }

    fun loadVersion(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_VERSION, 0)
    }

    fun loadUpdatedAt(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_UPDATED_AT, null)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        // ===== START LOG =====
        Log.d("RemoteCore", "Đã xoá toàn bộ cache JSON")
        // ===== END LOG =====
    }
}
