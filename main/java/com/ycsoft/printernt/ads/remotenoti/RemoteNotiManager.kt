package com.ycsoft.printernt.ads.remotenoti

import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import com.ycsoft.printernt.ads.core.RemoteBootstrap
import org.json.JSONObject

/**
 * RemoteNotiManager
 * ----------------------------
 * Yêu cầu:
 *  - Kill app mở lại -> show lại
 *  - Ẩn app (background) mở lại -> KHÔNG show lại
 *  => Chỉ show 1 lần / 1 session (process). Không dùng SharedPreferences.
 */
object RemoteNotiManager {

    // ====== SESSION FLAGS (RAM) ======
    // Process còn sống (ẩn app) => vẫn true => không show lại
    // Kill app => process chết => reset về false => show lại
    private var shownThisSession = false
    private var lastShowAtMs = 0L // chống show dồn khi onResume gọi liên tục

    data class NotiData(
        val title: String,
        val content: String,
        val imageUrl: String,
        val actionUrl: String,
        val delaySeconds: Int,
        val showCountPerVersion: Int,
        val metaVersion: Int
    )

    /**
     * Trả về NotiData nếu đủ điều kiện hiển thị, ngược lại trả null.
     */
    fun getNotiDataIfAllowed(activity: Activity): NotiData? {
        val root = RemoteBootstrap.currentRoot
        if (root == null) {
            Log.d("RemoteNoti", "Chưa có JSON (core chưa load xong) -> bỏ qua")
            return null
        }

        val metaVersion = root.optJSONObject("meta")?.optInt("version", 0) ?: 0
        val noti = root.optJSONObject("remotenoti") ?: JSONObject()

        val enabled = noti.optBoolean("enabled", false)
        if (!enabled) {
            Log.d("RemoteNoti", "remotenoti đang tắt (enabled=false) -> không hiện")
            return null
        }

        // Giữ lại field này theo JSON (anh có thể set =1)
        val showLimit = noti.optInt("showCountPerVersion", 0)
        if (showLimit <= 0) {
            Log.d("RemoteNoti", "showCountPerVersion <= 0 -> không hiện")
            return null
        }

        // ====== ĐIỀU KIỆN THEO SESSION ======
        if (shownThisSession) {
            Log.d("RemoteNoti", "Đã hiện 1 lần trong session này -> không hiện lại khi chỉ ẩn app")
            return null
        }

        // chống show dồn (vd onResume chạy 2 lần nhanh)
        val now = System.currentTimeMillis()
        if (now - lastShowAtMs < 800) {
            Log.d("RemoteNoti", "Bỏ qua vì gọi quá nhanh (anti-spam)")
            return null
        }

        val data = NotiData(
            title = noti.optString("title", ""),
            content = noti.optString("content", ""),
            imageUrl = noti.optString("imageUrl", ""),
            actionUrl = noti.optString("actionUrl", ""),
            delaySeconds = noti.optInt("delaySeconds", 0).coerceAtLeast(0),
            showCountPerVersion = showLimit,
            metaVersion = metaVersion
        )

        Log.d("RemoteNoti", "Đủ điều kiện hiện popup | delay=${data.delaySeconds}s | versionJSON=$metaVersion")
        return data
    }

    /**
     * Gọi hàm này NGAY SAU KHI popup thật sự show thành công.
     */
    fun markShown() {
        shownThisSession = true
        lastShowAtMs = System.currentTimeMillis()
        Log.d("RemoteNoti", "Đã đánh dấu popup đã hiển thị (session=true)")
    }

    // ================== HELPERS ==================

    @Suppress("unused")
    private fun getVersionCode(activity: Activity): Long {
        return try {
            val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) pInfo.longVersionCode else pInfo.versionCode.toLong()
        } catch (_: PackageManager.NameNotFoundException) {
            0L
        }
    }
}
