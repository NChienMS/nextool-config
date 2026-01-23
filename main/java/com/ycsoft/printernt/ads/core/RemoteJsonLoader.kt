package com.ycsoft.printernt.ads.core

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

object RemoteJsonLoader {

    fun load(urlString: String): String? {
        return try {
            val finalUrl = if (urlString.contains("?")) {
                "$urlString&ts=${System.currentTimeMillis()}"
            } else {
                "$urlString?ts=${System.currentTimeMillis()}"
            }

            Log.d("RemoteCore", "Bắt đầu tải JSON từ GitHub: $finalUrl")

            val url = URL(finalUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = RemoteConfig.CONNECT_TIMEOUT_MS
                readTimeout = RemoteConfig.READ_TIMEOUT_MS
                requestMethod = "GET"

                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
                setRequestProperty("Pragma", "no-cache")
            }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Log.e("RemoteCore", "Lỗi HTTP khi tải JSON, mã lỗi = $code")
                return null
            }

            val result = conn.inputStream.bufferedReader().use { it.readText() }
            Log.d("RemoteCore", "Tải JSON thành công, độ dài = ${result.length}")

            result
        } catch (e: Exception) {
            Log.e("RemoteCore", "Tải JSON thất bại (lỗi mạng hoặc timeout)", e)
            null
        }
    }
}
