package com.ycsoft.printernt.ads.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import org.json.JSONObject
import android.os.Handler
import android.os.Looper

/**
 * RemoteBootstrap
 * ----------------------------
 * Nhiệm vụ:
 *  - Điều phối theo logic anh chốt:
 *      1) thử load remote
 *      2) fail -> dùng cache
 *      3) cache fail -> dùng default (RemoteJsonDefault)
 *
 *  - Validate JSON:
 *      Nếu parse lỗi / thiếu meta.version -> coi như "không đúng" -> fallback
 *
 * Output:
 *  - Trả về JSONObject hợp lệ để module dùng luôn
 *
 * Không làm:
 *  - Không show UI
 *  - Không xử lý logic remotenoti/remoteads
 */
object RemoteBootstrap {

    enum class Source { REMOTE, CACHE, DEFAULT }

    var remoteUrl: String = RemoteConfig.JSON_URL

    // Lưu trạng thái để anh debug nhanh (module có thể đọc)
    var lastSource: Source = Source.DEFAULT
        private set
    var lastVersion: Int = 0
        private set
    var lastUpdatedAt: String = ""
        private set

    fun start(context: Context): JSONObject {
        // 1) Thử remote nếu có mạng và có url
        val remoteJson: String? =
            if (remoteUrl.isNotBlank() && isOnline(context)) {
                val s = RemoteJsonLoader.load(remoteUrl) // loader đã tự thêm ?ts=... và no-cache
                if (s != null && isValidJson(s)) s else null
            } else null

        if (remoteJson != null) {
            val parsed = parseMeta(remoteJson)
            lastSource = Source.REMOTE
            lastVersion = parsed.version
            lastUpdatedAt = parsed.updatedAt

            RemoteJsonStore.save(context, remoteJson, parsed.version, parsed.updatedAt)

            // ===== START LOG =====
            Log.d(
                "RemoteCore",
                "Dùng JSON từ GITHUB | version=${parsed.version} | updatedAt=${parsed.updatedAt}"
            )
            // ===== END LOG =====

            return JSONObject(remoteJson)
        }

        // 2) Remote fail -> thử cache
        val cacheJson = RemoteJsonStore.loadJson(context)
        if (cacheJson != null && isValidJson(cacheJson)) {
            val parsed = parseMeta(cacheJson)
            lastSource = Source.CACHE
            lastVersion = parsed.version
            lastUpdatedAt = parsed.updatedAt

            // ===== START LOG =====
            Log.d(
                "RemoteCore",
                "Không lấy được GitHub → dùng JSON CACHE | version=${parsed.version} | updatedAt=${parsed.updatedAt}"
            )
            // ===== END LOG =====

            return JSONObject(cacheJson)
        }

        // 3) Cache fail -> dùng default
        val def = RemoteJsonDefault.json()
        val parsed = parseMeta(def)
        lastSource = Source.DEFAULT
        lastVersion = parsed.version
        lastUpdatedAt = parsed.updatedAt

        // ===== START LOG =====
        Log.d(
            "RemoteCore",
            "Không có GitHub + không có cache → dùng JSON MẶC ĐỊNH | version=${parsed.version}"
        )
        // ===== END LOG =====

        return JSONObject(def)
    }

    // -------------------------
    // Helpers
    // -------------------------

    private fun isValidJson(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val meta = root.optJSONObject("meta") ?: return false
            // Bắt buộc có version (int)
            if (!meta.has("version")) return false
            meta.optInt("version", Int.MIN_VALUE) != Int.MIN_VALUE
        } catch (e: Exception) {
            // ===== START LOG =====
            Log.e(
                "RemoteCore",
                "JSON không hợp lệ (sai format hoặc thiếu meta.version), sẽ fallback",
                e
            )
            // ===== END LOG =====
            false
        }
    }

    private data class MetaParsed(val version: Int, val updatedAt: String)

    private fun parseMeta(json: String): MetaParsed {
        return try {
            val root = JSONObject(json)
            val meta = root.optJSONObject("meta")
            val version = meta?.optInt("version", 0) ?: 0
            val updatedAt = meta?.optString("updatedAt", "") ?: ""
            MetaParsed(version, updatedAt)
        } catch (_: Exception) {
            MetaParsed(0, "")
        }
    }

    private fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val nw = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(nw) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            false
        }
    }

    var currentRoot: JSONObject? = null
        private set

    /**
     * startAsync
     * ----------------------------
     * Nhiệm vụ:
     *  - Chạy RemoteBootstrap.start(context) ở background (ngầm)
     *  - Trả JSONObject về main thread qua callback
     *
     * Lưu ý: dùng đúng logic anh chốt:
     *  có mạng -> remote -> cache -> default
     */
    fun startAsync(context: Context, onDone: (JSONObject) -> Unit) {
        Thread {
            // ===== START LOG =====
            Log.d(
                "RemoteCore",
                "Bắt đầu load JSON NGẦM (background)"
            )
            // ===== END LOG =====

            val root = start(context) // hàm start() hiện tại của anh (đã fallback đủ)

            currentRoot = root

            // ===== START LOG =====
            Log.d(
                "RemoteCore",
                "Load JSON xong | nguồn=$lastSource | version=$lastVersion"
            )
            // ===== END LOG =====

            Handler(Looper.getMainLooper()).post {
                onDone(root)
            }
        }.start()
    }
}
