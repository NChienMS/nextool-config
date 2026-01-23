package com.ycsoft.printernt.commands

/**
 * PrinterCommands — kho lệnh dùng chung LAN/USB.
 * IN TEST PAGE
 * DAO CẮT GIẤY
 */


// Lệnh in trang test
object PrinterCommands {
    /** In test page (1F 1B 1F 67 00) */
    fun testPage(): ByteArray = byteArrayOf(0x1F, 0x1B, 0x1F, 0x67, 0x00)

    /** Cắt giấy (1D 56 42 00) */
    fun escposCut(): ByteArray = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    /** In check usb + cắt (init → căn giữa → text → LF x3 → cut) */
    fun escposTestAndCut(text: String = "NEXTOOL TEST PRINT"): ByteArray {
        val escInit = byteArrayOf(0x1B, 0x40)
        val align   = byteArrayOf(0x1B, 0x61, 0x01)
        val lf      = byteArrayOf(0x0A, 0x0A, 0x0A)
        val cut     = escposCut()
        return escInit + align + text.toByteArray(Charsets.US_ASCII) + lf + cut
    }

    /** ESC/POS mở két tiền (ESC p m t1 t2) */
    fun cashDrawer(): ByteArray = byteArrayOf(0x1B, 0x70, 0x00, 0x1E, 0xFF.toByte(), 0x00)

    /** DHCP ON */
    fun dhcpOn(): ByteArray = byteArrayOf(
        0x1F.toByte(),0x1B.toByte(),0x1F.toByte(),0x91.toByte(),0x00.toByte(),
        0x49.toByte(),0xFA.toByte(),0x01.toByte(),0x5A.toByte(),
        0xC0.toByte(),0xA8.toByte(),0x7B.toByte(),0x64.toByte(),
        0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xF0.toByte(),
        0xC0.toByte(),0xA8.toByte(),0x7B.toByte(),0x01.toByte()
    )

    /** DHCP OFF */
    fun dhcpOff(): ByteArray = byteArrayOf(
        0x1F.toByte(),0x1B.toByte(),0x1F.toByte(),0x91.toByte(),0x00.toByte(),
        0x49.toByte(),0xFA.toByte(),0x01.toByte(),0x5A.toByte(),
        0xC0.toByte(),0xA8.toByte(),0x7B.toByte(),0x64.toByte(),
        0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x00.toByte(),
        0xC0.toByte(),0xA8.toByte(),0x7B.toByte(),0x01.toByte()
    )

    /** Lệnh khôi phục cài đặt gốc (Restore Factory) */
    fun restoreFactory(): ByteArray = byteArrayOf(0x1F, 0x1B, 0x1F, 0x11, 0x11, 0x00)

    /**
    Lệnh beep
     */
    fun beepSet(enable: Boolean, counter: Int, timeUnits50ms: Int, mode: Int): ByteArray {
        val header = byteArrayOf(0x1F, 0x1B, 0x1F, 0xE0.toByte(), 0x13, 0x14)

        val en  = if (enable) 0x01.toByte() else 0x00.toByte()
        val cnt = counter.coerceIn(1, 20).toByte()
        val t   = timeUnits50ms.coerceIn(1, 20).toByte()
        val md  = mode.coerceIn(0, 4).toByte()

        val tail = byteArrayOf(0x0A, 0x00)
        return header + byteArrayOf(en, cnt, t, md) + tail
    }

    /** B2: 1F 1B 1F B2 [IP:4][MASK:4][GW:4] — đổi IP
     * Đổi ip qua B2
     * */
    fun setIpB2(ip: String, mask: String, gw: String): ByteArray {
        fun ipv4ToBytesStrict(s: String): ByteArray {
            val p = s.split('.'); require(p.size == 4)
            return byteArrayOf(
                p[0].toInt().toByte(),
                p[1].toInt().toByte(),
                p[2].toInt().toByte(),
                p[3].toInt().toByte()
            )
        }
        val header = byteArrayOf(0x1F, 0x1B, 0x1F, 0xB2.toByte())
        return header + ipv4ToBytesStrict(ip) + ipv4ToBytesStrict(mask) + ipv4ToBytesStrict(gw)
    }

    /** 22: 1F 1B 1F 22 [IP:4] — đổi IP kiểu mới (đơn giản chỉ có IP) */
    fun setIp22(ip: String): ByteArray {
        val header = byteArrayOf(0x1F, 0x1B, 0x1F, 0x22.toByte())
        return header + ipv4ToBytesStrict(ip)
    }

    // ------- Helpers -------
    private fun ipv4ToBytesStrict(s: String): ByteArray {
        val p = s.split('.')
        require(p.size == 4) { "Invalid IPv4: $s" }
        return byteArrayOf(
            p[0].toInt().toByte(),
            p[1].toInt().toByte(),
            p[2].toInt().toByte(),
            p[3].toInt().toByte()
        )
    }


    /** B3 06: 1F 1B 1F B3 06 + SSID + 00 + PASS + 00 — set Wi-Fi
     * Mã lệnh set wifi
     * */
    fun wifiSet(ssid: String, password: String): ByteArray {
        val header = byteArrayOf(0x1F, 0x1B, 0x1F, 0xB3.toByte(), 0x06)
        val ssidBytes = ssid.toByteArray(Charsets.US_ASCII)
        val passBytes = password.toByteArray(Charsets.US_ASCII)
        return header + ssidBytes + byteArrayOf(0x00) + passBytes + byteArrayOf(0x00)
    }


}
