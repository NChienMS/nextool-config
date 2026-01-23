package com.ycsoft.printernt.ads.core

/**
 * RemoteConfig
 * ----------------------------
 * Nhiệm vụ:
 *  - Chứa cấu hình KỸ THUẬT cho core:
 *      + Link JSON GitHub (raw)
 *      + timeout
 *      + tag log (nếu muốn)
 *
 * Lưu ý:
 *  - KHÔNG để nội dung quảng cáo / adUnitId ở đây
 *  - Dữ liệu nằm trong JSON GitHub và RemoteJsonDefault
 */
object RemoteConfig {

    // ✅ Link JSON raw GitHub của anh (dán vào đây)
    const val JSON_URL = "https://raw.githubusercontent.com/NChienMS/nextool-config/main/config.json"

    // ✅ Timeout (ms)
    const val CONNECT_TIMEOUT_MS = 5000
    const val READ_TIMEOUT_MS = 5000

    // ✅ Tag log (để dễ filter logcat)
    const val LOG_TAG = "RemoteCore"
}
