package com.ycsoft.printernt.ads.core

import android.util.Log

/**
 * RemoteJsonDefault
 * ----------------------------
 * Nhiệm vụ:
 *  - Cung cấp JSON mặc định (raw string) nằm TRONG CORE
 *  - Dùng khi: remote fail + cache fail + JSON không hợp lệ
 *
 * Không làm:
 *  - Không gọi mạng
 *  - Không đọc file res/raw
 *  - Không cache
 */
object RemoteJsonDefault {

    fun json(): String {
        // ===== START LOG =====
        Log.d(
            "RemoteCore",
            "Sử dụng JSON MẶC ĐỊNH trong core (không có mạng, không có cache)"
        )
        // ===== END LOG =====

        // Default: tắt hết để app mở lên không gây phiền
        return """
            {
              "meta": {
                "version": 0,
                "updatedAt": "default"
              },
              "remotenoti": {
                "enabled": true,
                "title": "mặc định nè",
                "content": "khi ko có mạng, ko lấy được json và ko  cache\n Trên trời cao muôn vì sao lung linh \n vì sao \n hihi haha hô hôkhi ko có mạng, ko lấy được json và ko  cache\n Trên trời cao muôn vì sao lung linh \n vì sao \n hihi haha hô hôkhi ko có mạng, ko lấy được json và ko  cache\n Trên trời cao muôn vì sao lung linh \n vì sao \n hihi haha hô hôkhi ko có mạng, ko lấy được json và ko  cache\n Trên trời cao muôn vì sao lung linh \n vì sao \n hihi haha hô hô",
                "imageUrl": "https://ariesvn8.wordpress.com/wp-content/uploads/2025/09/untitled-1.jpg",
                "actionUrl": "",
                "delaySeconds": 5,
                "showCountPerVersion": 1
              }
            }
        """.trimIndent()
    }
}
