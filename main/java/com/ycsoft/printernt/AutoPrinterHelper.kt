package com.ycsoft.printernt

import android.content.Context
import android.net.wifi.WifiManager
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.*
import java.util.*
import kotlin.collections.LinkedHashMap
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import java.util.Locale
import java.io.*
import java.net.InetSocketAddress






// ===== Helper tách riêng khối AUTO (đa-hãng) =====
// Nhiệm vụ: quét FIND, parse, đổ ListView, và gửi SAVE/SetIP theo hãng.
// MainActivity chỉ cần truyền các view + callback & gọi init()

class AutoPrinterHelper(
    private val activity: AppCompatActivity,



    // VIEW bắt buộc
    private val btnScanUid: MaterialButton,
    private val lvUidPrinters: ListView,
    private val tvUidFoundCount: TextView,
    private val btnUidApply: MaterialButton,

    // VIEW lấy giá trị IP cần áp
    private val etNewIp: TextInputEditText,
    private val etMask: TextInputEditText,
    private val etGw: TextInputEditText,

    // Callbacks / helpers tái dùng từ MainActivity
    private val popup: (String) -> Unit,
    private val addLog: (String) -> Unit,
    private val showLoading: (String) -> Unit,
    private val updateLoading: (String) -> Unit,
    private val dismissLoading: () -> Unit,
    private val showResult: (String) -> Unit,

    // Validators (dùng lại y như trong MainActivity)
    private val isValidIpv4WithUi: (String, String) -> Boolean,
    private val isValidSubnetMask: (String) -> Boolean,
    private val sameSubnet: (String, String, String) -> Boolean,

    // Chuỗi lỗi/label đa ngữ từ resources (truyền sẵn để helper không phụ thuộc R)
    private val errIpNewInvalid: String,
    private val errMaskInvalid: String,
    private val errGwInvalid: String
) {

    // ====== State & adapter ======
// ====== State & adapter ======
    private val uidDevices = mutableListOf<UidDevice>() // danh sách kết quả đa-hãng

    private val uidAdapter: ArrayAdapter<String> =
        object : ArrayAdapter<String>(
            activity,
            R.layout.item_uid_single_choice,
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                // simple_list_item_single_choice dùng CheckedTextView
                (v as? CheckedTextView)?.setTextColor(0xFF2E7D32.toInt()) // xanh lá
                return v
            }
        }

    private var isUidSearching = false

    // ====== Models & Providers ======
    data class UidDevice(
        val brandTag: String,      // "XP", "ZYWELL", ...
        val brandName: String,     // "XPrinter", "Zywell", ...
        val mac: String,           // "AA:BB:CC:DD:EE:FF"
        val ip: String,
        val extras: Map<String, Any> = emptyMap()
    )

    data class SetIpAction(
        val protocol: String,      // "TCP" hoặc "UDP"
        val host: String,
        val port: Int,
        val payload: ByteArray
    )

    data class DiscoveryPacket(
        val port: Int,             // cổng UDP để gửi FIND
        val payload: ByteArray
    )

    interface UidBrandProvider {
        val tag: String
        val name: String

        fun discoveryPackets(): List<DiscoveryPacket>
        fun tryParseDiscovery(packet: ByteArray): UidDevice?
        fun buildSetIpActions(device: UidDevice, ip: String, mask: String, gw: String): List<SetIpAction>
    }

    private val providers: List<UidBrandProvider> = listOf(XPrinterProvider(), ZywellProvider())

    // ====== Public ======
    fun init() {
        // Gắn adapter + chế độ chọn 1
        lvUidPrinters.adapter = uidAdapter
        lvUidPrinters.choiceMode = ListView.CHOICE_MODE_SINGLE

        // Nút “Quét thiết bị” (đa-hãng)
        btnScanUid.setOnClickListener {
            if (isUidSearching) { popup("Đang quét, vui lòng chờ xíu..."); return@setOnClickListener }
            popup("Đang tìm kiếm thiết bị...") // <- thêm lại để hiện popup ngay khi bấm
            startScan()
        }

        // Nút “Áp IP”
        btnUidApply.setOnClickListener {
            val pos = lvUidPrinters.checkedItemPosition
            if (pos == ListView.INVALID_POSITION) {
                showResult("Hãy chọn 1 thiết bị máy in quét được.")
                return@setOnClickListener
            }

            val ipNew = etNewIp.text?.toString()?.trim().orEmpty()
            val mask  = etMask.text?.toString()?.trim().orEmpty()
            val gw    = etGw.text?.toString()?.trim().orEmpty()

            if (!isValidIpv4WithUi(ipNew, errIpNewInvalid)) return@setOnClickListener
            if (!isValidSubnetMask(mask)) { showResult(errMaskInvalid); return@setOnClickListener }
            if (!isValidIpv4WithUi(gw, errGwInvalid)) return@setOnClickListener
            if (!sameSubnet(ipNew, gw, mask)) {
                showResult("Gateway phải cùng subnet với IP theo mask.")
                return@setOnClickListener
            }

            val target = uidDevices[pos]
            applySetIp(target, ipNew, mask, gw)
        }

        // Khởi tạo hiển thị
        tvUidFoundCount.text = "Tìm thấy: 0 thiết bị (MAC - IP)"
    }

    // ====== Scan ======
    private fun startScan() {
        isUidSearching = true
        activity.lifecycleScope.launch {
            //addLog("Bắt đầu quét máy in")
            showLoading("Đang tìm máy in...")

            uidDevices.clear()
            uidAdapter.clear()
            tvUidFoundCount.text = "Tìm thấy: 0 thiết bị (MAC - IP)"
            lvUidPrinters.post { resizeUidListView() }   // thu nhỏ list khi chưa có gì

            val results = withContext(Dispatchers.IO) { uidDiscoverMulti(timeoutMs = 4000) }

            dismissLoading()
            isUidSearching = false

            if (results.isEmpty()) {
                //addLog("Không tìm thấy thiết bị máy in nào.")
                showResult("Không tìm thấy thiết bị nào trên mạng hiện tại.\nHãy kiểm tra Wi-Fi và thử lại.")
            } else {
                uidDevices += results

                // THÊM STT 1., 2., 3. ... và đổ lên adapter
                results.forEachIndexed { index, d ->
                    val stt = index + 1
                    uidAdapter.add("$stt. ${d.brandName} - ${d.mac} - ${d.ip}")
                }
                uidAdapter.notifyDataSetChanged()
                tvUidFoundCount.text = "Tìm thấy: ${results.size} thiết bị (MAC - IP)"
                addLog("Quét máy in thành công: ${results.size} thiết bị.")

                // Cập nhật chiều cao list theo số lượng thiết bị
                lvUidPrinters.post { resizeUidListView() }
            }
        }
    }


    // ====== Apply Set IP ======
    private fun applySetIp(target: UidDevice, ipNew: String, mask: String, gw: String) {
        activity.lifecycleScope.launch {
            // Kiểm tra IP mới đã có thiết bị dùng chưa
            showLoading("Đang kiểm tra IP mới: $ipNew")
            val aliveNew9100 = withContext(Dispatchers.IO) { tcpAlive9100(ipNew, 1200) }
            val aliveNewPing = if (!aliveNew9100) withContext(Dispatchers.IO) { ping(ipNew, 1200) } else true
            if (aliveNew9100 || aliveNewPing) {
                dismissLoading()
                addLog("⚠ IP mới $ipNew đang được thiết bị khác sử dụng. Dừng gửi cấu hình.")
                showResult("IP mới $ipNew đã có thiết bị sử dụng, vui lòng đổi IP khác.")
                return@launch
            }

            updateLoading("Đang gửi cấu hình tới ${target.mac} → $ipNew ...")
            val ok = uidApplySetIp(target, ipNew, mask, gw)
            dismissLoading()

            if (ok) {
                addLog("Gửi cấu hình thành công: ${target.mac} → $ipNew")
                showResult("Đã gửi đổi IP tới máy in đã chọn.\nMáy in sẽ nhận IP mới: $ipNew.\nHãy quét lại sau vài giây để xác nhận.")
            } else {
                addLog("Gửi cấu hình thất bại tới ${target.mac}")
                showResult("Gửi cấu hình thất bại. Vui lòng kiểm tra mạng và thử lại.")
            }
        }
    }

    // ===================== Hạ tầng đa-hãng =====================

    // --- XPrinter ---
    private inner class XPrinterProvider : UidBrandProvider {
        override val tag = "XP"
        override val name = "XP"

        override fun discoveryPackets(): List<DiscoveryPacket> =
            listOf(DiscoveryPacket(9000, "58503030303146494E44".hexToBytes())) // "XP0001FIND"

        override fun tryParseDiscovery(packet: ByteArray): UidDevice? {
            val xp = parseUidFoundPacket(packet) ?: return null
            return UidDevice(
                brandTag = tag,
                brandName = name,
                mac = xp.macStr,
                ip = xp.ipStr,
                extras = mapOf("mask" to xp.maskStr, "gw" to xp.gwStr, "raw" to packet)
            )
        }

        override fun buildSetIpActions(device: UidDevice, ip: String, mask: String, gw: String): List<SetIpAction> {
            val payload = buildUidSavePayload(macStrToBytes(device.mac), ip, mask, gw)
            return listOf(
                SetIpAction("UDP", "255.255.255.255", 9000, payload),
                SetIpAction("UDP", device.ip, 9000, payload)
            )
        }
    }

    // --- Zywell (khung theo log) ---
    private inner class ZywellProvider : UidBrandProvider {
        override val tag = "ZYWELL"
        override val name = "ZY"

        override fun discoveryPackets(): List<DiscoveryPacket> =
            listOf(DiscoveryPacket(1460, "5A593030303146494E44".hexToBytes())) // "ZY0001FIND"

        override fun tryParseDiscovery(packet: ByteArray): UidDevice? {
            if (!looksLikeZywell(packet)) return null
            val mac = parseZyMac(packet) ?: return null
            val ip  = parseZyIp(packet)  ?: return null
            return UidDevice(tag, name, mac, ip, parseZyExtras(packet))
        }

        override fun buildSetIpActions(device: UidDevice, ip: String, mask: String, gw: String): List<SetIpAction> {
            val payload = buildZywellSavePayload(
                macStr = device.mac, ip = ip, mask = mask, gw = gw,
                fixedPort = 9100, dhcpOn = false,
                modelTag = (device.extras["modelTag"] as? String) ?: "-zy80-"
            )
            return listOf(SetIpAction("UDP", "255.255.255.255", 1460, payload))
        }

        // === các parser mẫu, tùy log thực tế tinh chỉnh tiếp ===
        private fun looksLikeZywell(p: ByteArray): Boolean {
            val sig = "ZY0001FOUND".toByteArray(Charsets.US_ASCII)
            return p.indexOfSub(sig) != null
        }
        private fun parseZyMac(p: ByteArray): String? {
            val sig = "ZY0001FOUND".toByteArray(Charsets.US_ASCII)
            val idx = p.indexOfSub(sig) ?: return null
            val off = idx + sig.size
            if (off + 6 > p.size) return null
            val mac = p.copyOfRange(off, off + 6)
            return mac.joinToString(":") { "%02X".format(it) }
        }
        private fun parseZyIp(p: ByteArray): String? {
            val sig = "ZY0001FOUND".toByteArray(Charsets.US_ASCII)
            val idx = p.indexOfSub(sig) ?: return null
            var off = idx + sig.size
            if (off + 6 + 2 + 4 > p.size) return null
            off += 6; off += 2  // MAC + len
            return p.copyOfRange(off, off + 4).toIpv4()
        }
        private fun parseZyExtras(p: ByteArray): Map<String, Any> {
            val sig = "ZY0001FOUND".toByteArray(Charsets.US_ASCII)
            val idx = p.indexOfSub(sig) ?: return mapOf("raw" to p)
            var off = idx + sig.size + 6 + 2
            fun get4(): String { val v = p.copyOfRange(off, off + 4).toIpv4(); off += 4; return v }
            val ip = get4(); val mask = get4(); val gw = get4()
            return mapOf("ip" to ip, "mask" to mask, "gw" to gw, "raw" to p)
        }
    }

    // ===================== Core logic bê từ MainActivity =====================
    private data class UidPrinterInfo(
        val macBytes: ByteArray,
        val ipStr: String,
        val maskStr: String,
        val gwStr: String
    ) { val macStr: String get() = macBytes.joinToString(":") { "%02X".format(it) } }

    // FIND/SAVE constants
    private val SEARCH_PAYLOAD_HEX = "58503030303146494E44" // "XP0001FIND"
    private val FOUND_TAG_ASCII    = "XP0001FOUND".toByteArray(Charsets.US_ASCII)
    private val HEADER_SAVE_HEX    = "585030303031"         // "XP0001"
    private val SAVE_CMD_HEX       = "53415645"             // "SAVE"
    private val SAVE_EXTRA2_HEX    = "F574"
    private val SAVE_TAIL_HEX      = "8C2300"

    // Scan đa-hãng
    private suspend fun uidDiscoverMulti(timeoutMs: Int = 4000): List<UidDevice> =
        withContext(Dispatchers.IO) {
            val found = Collections.synchronizedMap(LinkedHashMap<String, UidDevice>())
            val globalBc = InetAddress.getByName("255.255.255.255")
            val directedBc = getDirectedBroadcast() ?: globalBc
            val targets = listOf(globalBc, directedBc).distinctBy { it.hostAddress }

            var lock: WifiManager.MulticastLock? = null
            try {
                lock = acquireMulticastLock()
                DatagramSocket(9000).use { sock9000 ->
                    DatagramSocket(1460).use { sock1460 ->
                        listOf(sock9000, sock1460).forEach { s -> s.broadcast = true; s.soTimeout = 350 }

                        // Gửi FIND theo hãng & cổng
                        for (p in providers) for (dpkt in p.discoveryPackets()) for (dst in targets) {
                            try {
                                val pkt = DatagramPacket(dpkt.payload, dpkt.payload.size, dst, dpkt.port)
                                if (dpkt.port == 9000) sock9000.send(pkt) else if (dpkt.port == 1460) sock1460.send(pkt)
                            } catch (_: Exception) {}
                        }

                        // Lắng nghe đến hết timeout
                        val start = System.currentTimeMillis()
                        val buf9000 = ByteArray(4096); val buf1460 = ByteArray(4096)
                        while (System.currentTimeMillis() - start < timeoutMs) {
                            // port 9000
                            try {
                                val dp = DatagramPacket(buf9000, buf9000.size); sock9000.receive(dp)
                                val data = dp.data.copyOf(dp.length)
                                for (p in providers) p.tryParseDiscovery(data)?.let { dev -> found.putIfAbsent(dev.mac, dev) }
                            } catch (_: SocketTimeoutException) {} catch (_: Exception) {}

                            // port 1460
                            try {
                                val dp = DatagramPacket(buf1460, buf1460.size); sock1460.receive(dp)
                                val data = dp.data.copyOf(dp.length)
                                for (p in providers) p.tryParseDiscovery(data)?.let { dev -> found.putIfAbsent(dev.mac, dev) }
                            } catch (_: SocketTimeoutException) {} catch (_: Exception) {}
                        }
                    }
                }
            } finally { try { lock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {} }
            found.values.toList()
        }

    // Gửi SetIP theo provider
    private suspend fun uidApplySetIp(device: UidDevice, ip: String, mask: String, gw: String): Boolean =
        withContext(Dispatchers.IO) {
            val provider = providers.firstOrNull { it.tag == device.brandTag } ?: return@withContext false
            val actions = provider.buildSetIpActions(device, ip, mask, gw)
            actions.all { act ->
                when (act.protocol) {
                    "TCP" -> try {
                        Socket().use { s ->
                            s.soTimeout = 2500
                            s.connect(InetSocketAddress(act.host, act.port), 2500)
                            s.getOutputStream().use { os -> os.write(act.payload); os.flush() }
                        }; true
                    } catch (_: Exception) { false }

                    "UDP" -> try {
                        DatagramSocket().use { ds ->
                            ds.broadcast = true
                            val targets = if (act.host == "255.255.255.255") {
                                val g = InetAddress.getByName("255.255.255.255")
                                val d = getDirectedBroadcast() ?: g
                                listOf(g, d).distinctBy { it.hostAddress }
                            } else listOf(InetAddress.getByName(act.host))
                            for (dst in targets) ds.send(DatagramPacket(act.payload, act.payload.size, dst, act.port))
                        }; true
                    } catch (_: Exception) { false }

                    else -> false
                }
            }
        }

    // ====== Utils bê nguyên logic từ MainActivity ======
    private fun parseUidFoundPacket(data: ByteArray): UidPrinterInfo? {
        val idx = data.indexOfSub(FOUND_TAG_ASCII) ?: return null
        var p = idx + FOUND_TAG_ASCII.size
        if (p + 6 > data.size) return null
        val mac = data.copyOfRange(p, p + 6); p += 6
        if (p + 2 > data.size) return null
        p += 2
        if (p + 12 > data.size) return null
        val ip   = data.copyOfRange(p, p + 4).toIpv4(); p += 4
        val mask = data.copyOfRange(p, p + 4).toIpv4(); p += 4
        val gw   = data.copyOfRange(p, p + 4).toIpv4()
        return UidPrinterInfo(mac, ip, mask, gw)
    }

    private fun buildUidSavePayload(mac: ByteArray, ip: String, mask: String, gw: String): ByteArray {
        val header = HEADER_SAVE_HEX.hexToBytes()
        val save   = SAVE_CMD_HEX.hexToBytes()
        val extra2 = SAVE_EXTRA2_HEX.hexToBytes()
        val tail   = SAVE_TAIL_HEX.hexToBytes()

        val ipB   = ipv4ToBytes(ip)   ?: byteArrayOf(0,0,0,0)
        val maskB = ipv4ToBytes(mask) ?: byteArrayOf(0,0,0,0)
        val gwB   = ipv4ToBytes(gw)   ?: byteArrayOf(0,0,0,0)
        return header + save + mac + extra2 + ipB + maskB + gwB + tail
    }

    private fun getDirectedBroadcast(): InetAddress? = try {
        val wm = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wm.dhcpInfo ?: return null
        val bc = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
        val bytes = byteArrayOf(
            (bc and 0xFF).toByte(),
            (bc shr 8 and 0xFF).toByte(),
            (bc shr 16 and 0xFF).toByte(),
            (bc shr 24 and 0xFF).toByte()
        )
        InetAddress.getByAddress(bytes)
    } catch (_: Exception) { null }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? = try {
        val wm = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wm.createMulticastLock("nextool-uid-scan").apply { setReferenceCounted(false); acquire() }
    } catch (_: Exception) { null }

    private fun macStrToBytes(mac: String): ByteArray {
        val clean = mac.replace("[-:]".toRegex(), "").uppercase(Locale.ROOT)
        require(clean.length == 12) { "MAC không hợp lệ: $mac" }
        return ByteArray(6) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private fun ipv4ToBytes(ip: String): ByteArray? {
        return try {
            val p = ip.trim().split(".")
            if (p.size != 4) {
                null
            } else {
                byteArrayOf(
                    p[0].toInt().toByte(),
                    p[1].toInt().toByte(),
                    p[2].toInt().toByte(),
                    p[3].toInt().toByte()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tcpAlive9100(ip: String, timeoutMs: Int): Boolean = try {
        Socket().use { s -> s.connect(InetSocketAddress(ip, 9100), timeoutMs) }; true
    } catch (_: Exception) { false }

    private fun ping(ip: String, timeoutMs: Int): Boolean = try {
        InetAddress.getByName(ip).isReachable(timeoutMs)
    } catch (_: Exception) { false }

    // Tự điều chỉnh chiều cao list UID theo số lượng thiết bị
    private fun resizeUidListView() {
        val adapter = lvUidPrinters.adapter ?: return

        // Không có thiết bị -> ẩn luôn phần list
        if (adapter.count == 0) {
            val params = lvUidPrinters.layoutParams
            params.height = 0
            lvUidPrinters.layoutParams = params
            lvUidPrinters.requestLayout()
            return
        }

        var totalHeight = 0
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
            lvUidPrinters.width,
            View.MeasureSpec.AT_MOST
        )

        for (i in 0 until adapter.count) {
            val itemView = adapter.getView(i, null, lvUidPrinters)
            itemView.measure(widthMeasureSpec, View.MeasureSpec.UNSPECIFIED)
            totalHeight += itemView.measuredHeight
        }

        val params = lvUidPrinters.layoutParams
        params.height = totalHeight + lvUidPrinters.dividerHeight * (adapter.count - 1)
        lvUidPrinters.layoutParams = params
        lvUidPrinters.requestLayout()
    }


    // ===== Byte helpers =====

    // LE 16-bit
    private fun le16(v: Int): ByteArray =
        byteArrayOf((v and 0xFF).toByte(), ((v ushr 8) and 0xFF).toByte())

    /**
     * Zywell SAVE (UDP/1460) — 64 bytes
     * "ZY0001"(6) + "SAVE"(4) + MAC(6) + LEN(2=0x0010, LE)
     * + IP(4) + MASK(4) + GW(4) + PORT(2, LE=9100->8C 23) + DHCP(1:00=OFF)
     * + modelTag + padding 0x00 đến đủ 64B
     */
    private fun buildZywellSavePayload(
        macStr: String,
        ip: String,
        mask: String,
        gw: String,
        fixedPort: Int = 9100,
        dhcpOn: Boolean = false,
        modelTag: String = "-zy80-"
    ): ByteArray {
        val header  = "ZY0001".toByteArray(Charsets.US_ASCII)
        val command = "SAVE".toByteArray(Charsets.US_ASCII)
        val mac     = macStrToBytes(macStr)
        val len16   = le16(0x0010)

        val ipB   = ipv4ToBytes(ip)   ?: byteArrayOf(0,0,0,0)
        val maskB = ipv4ToBytes(mask) ?: byteArrayOf(0,0,0,0)
        val gwB   = ipv4ToBytes(gw)   ?: byteArrayOf(0,0,0,0)
        val portB = le16(fixedPort)
        val dhcpB = byteArrayOf(if (dhcpOn) 0x01 else 0x00)

        val model = modelTag.toByteArray(Charsets.US_ASCII)
        val core = header + command + mac + len16 + ipB + maskB + gwB + portB + dhcpB + model
        return if (core.size < 64) core + ByteArray(64 - core.size) else core.copyOf(64)
    }

    private fun String.hexToBytes(): ByteArray =
        replace("\\s".toRegex(), "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.indexOfSub(pattern: ByteArray): Int? {
        outer@ for (i in 0..size - pattern.size) {
            for (j in pattern.indices) if (this[i + j] != pattern[j]) continue@outer
            return i
        }
        return null
    }
    private fun ByteArray.toIpv4(): String = joinToString(".") { (it.toInt() and 0xFF).toString() }
    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val out = ByteArray(size + other.size)
        System.arraycopy(this, 0, out, 0, size)
        System.arraycopy(other, 0, out, size, other.size)
        return out
    }




}
// ===================== QR SMART API (TOP-LEVEL) =====================
// File: AutoPrinterHelper.kt (đặt OUTSIDE class AutoPrinterHelper)


object QrSmartUidApi {

    data class UidDevice(
        val brandTag: String,     // "XP" hoặc "ZYWELL"
        val brandName: String,    // "XP" hoặc "ZY"
        val mac: String,          // "AA:BB:CC:DD:EE:FF"
        val ip: String,
        val extras: Map<String, Any> = emptyMap()
    )

    fun brandToTag(brand: String): String {
        val b = brand.trim().lowercase(Locale.ROOT)
        return when {
            b.contains("xprinter") || b.contains("xp") -> "XP"
            b.contains("zywell") || b.contains("zy") -> "ZYWELL"
            else -> "XP"
        }
    }

    // ===== XP constants (y hệt file anh) =====
    private val FOUND_TAG_XP = "XP0001FOUND".toByteArray(Charsets.US_ASCII)
    private val FIND_XP = "58503030303146494E44".hexToBytes() // "XP0001FIND"
    private val HEADER_SAVE_HEX = "585030303031"              // "XP0001"
    private val SAVE_CMD_HEX    = "53415645"                  // "SAVE"
    private val SAVE_EXTRA2_HEX = "F574"
    private val SAVE_TAIL_HEX   = "8C2300"

    // ===== ZY constants =====
    private val FIND_ZY = "5A593030303146494E44".hexToBytes() // "ZY0001FIND"
    private val FOUND_TAG_ZY = "ZY0001FOUND".toByteArray(Charsets.US_ASCII)

    private fun acquireMulticastLock(ctx: Context): WifiManager.MulticastLock? = try {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wm.createMulticastLock("nextool-uid-scan").apply {
            setReferenceCounted(false)
            acquire()
        }
    } catch (_: Exception) { null }

    private fun getDirectedBroadcast(ctx: Context): InetAddress? = try {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wm.dhcpInfo ?: return null
        val bc = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
        val bytes = byteArrayOf(
            (bc and 0xFF).toByte(),
            (bc shr 8 and 0xFF).toByte(),
            (bc shr 16 and 0xFF).toByte(),
            (bc shr 24 and 0xFF).toByte()
        )
        InetAddress.getByAddress(bytes)
    } catch (_: Exception) { null }

    // ---------------- BƯỚC 1: FIND + match MAC ----------------
    suspend fun findByMac(
        ctx: Context,
        macNeed: String,
        brandTag: String,
        timeoutMs: Int = 2500
    ): UidDevice? = withContext(Dispatchers.IO) {

        val macNorm = macNeed.trim().uppercase(Locale.ROOT)
        val globalBc = InetAddress.getByName("255.255.255.255")
        val directedBc = getDirectedBroadcast(ctx) ?: globalBc
        val targets = listOf(globalBc, directedBc).distinctBy { it.hostAddress }

        var lock: WifiManager.MulticastLock? = null
        try {
            lock = acquireMulticastLock(ctx)

            when (brandTag) {
                "ZYWELL" -> discoverZywell(macNorm, targets, timeoutMs)
                else     -> discoverXprinter(macNorm, targets, timeoutMs)
            }
        } finally {
            try { lock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        }
    }

    private fun discoverXprinter(macNorm: String, targets: List<InetAddress>, timeoutMs: Int): UidDevice? {
        DatagramSocket(9000).use { sock ->
            sock.broadcast = true
            sock.soTimeout = 250

            // gửi FIND
            for (dst in targets) {
                runCatching {
                    sock.send(DatagramPacket(FIND_XP, FIND_XP.size, dst, 9000))
                }
            }

            val start = System.currentTimeMillis()
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() - start < timeoutMs) {
                try {
                    val dp = DatagramPacket(buf, buf.size)
                    sock.receive(dp)
                    val data = dp.data.copyOf(dp.length)

                    val xp = parseXpFound(data) ?: continue
                    val mac = xp.first
                    val ip  = xp.second
                    if (mac.uppercase(Locale.ROOT) == macNorm) {
                        return UidDevice("XP", "XP", mac, ip)
                    }
                } catch (_: SocketTimeoutException) {
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private fun discoverZywell(macNorm: String, targets: List<InetAddress>, timeoutMs: Int): UidDevice? {
        DatagramSocket(1460).use { sock ->
            sock.broadcast = true
            sock.soTimeout = 250

            // gửi FIND
            for (dst in targets) {
                runCatching {
                    sock.send(DatagramPacket(FIND_ZY, FIND_ZY.size, dst, 1460))
                }
            }

            val start = System.currentTimeMillis()
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() - start < timeoutMs) {
                try {
                    val dp = DatagramPacket(buf, buf.size)
                    sock.receive(dp)
                    val data = dp.data.copyOf(dp.length)

                    val zy = parseZyFound(data) ?: continue
                    val mac = zy.first
                    val ip  = zy.second
                    if (mac.uppercase(Locale.ROOT) == macNorm) {
                        return UidDevice("ZYWELL", "ZY", mac, ip)
                    }
                } catch (_: SocketTimeoutException) {
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    // XP FOUND: "XP0001FOUND" + MAC(6) + ??(2) + IP(4) + MASK(4) + GW(4)
    private fun parseXpFound(data: ByteArray): Pair<String, String>? {
        val idx = data.indexOfSub(FOUND_TAG_XP) ?: return null
        var p = idx + FOUND_TAG_XP.size
        if (p + 6 > data.size) return null
        val macBytes = data.copyOfRange(p, p + 6); p += 6
        if (p + 2 > data.size) return null
        p += 2
        if (p + 4 > data.size) return null
        val ip = data.copyOfRange(p, p + 4).toIpv4()
        val mac = macBytes.joinToString(":") { "%02X".format(it) }
        return mac to ip
    }

    // ZY FOUND: tối thiểu match chuỗi "ZY0001FOUND" rồi đọc MAC(6) + len(2) + IP(4)
    private fun parseZyFound(p: ByteArray): Pair<String, String>? {
        val idx = p.indexOfSub(FOUND_TAG_ZY) ?: return null
        var off = idx + FOUND_TAG_ZY.size
        if (off + 6 > p.size) return null
        val macBytes = p.copyOfRange(off, off + 6)
        off += 6
        if (off + 2 + 4 > p.size) return null
        off += 2
        val ip = p.copyOfRange(off, off + 4).toIpv4()
        val mac = macBytes.joinToString(":") { "%02X".format(it) }
        return mac to ip
    }

    // ---------------- BƯỚC 2: SET IP ----------------
    suspend fun setIp(
        ctx: Context,
        device: UidDevice,
        ipNew: String,
        mask: String,
        gw: String
    ): Boolean = withContext(Dispatchers.IO) {

        when (device.brandTag) {
            "ZYWELL" -> {
                val payload = buildZywellSavePayload(
                    macStr = device.mac,
                    ip = ipNew,
                    mask = mask,
                    gw = gw,
                    fixedPort = 9100,
                    dhcpOn = false,
                    modelTag = "-zy80-"
                )
                sendUdpBroadcast(ctx, payload, 1460)
            }

            else -> {
                val payload = buildXpSavePayload(device.mac, ipNew, mask, gw)
                // gửi broadcast + gửi thẳng IP hiện tại (giống anh)
                val ok1 = sendUdpBroadcast(ctx, payload, 9000)
                val ok2 = sendUdpUnicast(device.ip, payload, 9000)
                ok1 || ok2
            }
        }
    }

    private fun sendUdpBroadcast(ctx: Context, payload: ByteArray, port: Int): Boolean = try {
        DatagramSocket().use { ds ->
            ds.broadcast = true
            val globalBc = InetAddress.getByName("255.255.255.255")
            val directedBc = getDirectedBroadcast(ctx) ?: globalBc
            val targets = listOf(globalBc, directedBc).distinctBy { it.hostAddress }
            for (dst in targets) {
                ds.send(DatagramPacket(payload, payload.size, dst, port))
            }
        }
        true
    } catch (_: Exception) { false }

    private fun sendUdpUnicast(ip: String, payload: ByteArray, port: Int): Boolean = try {
        DatagramSocket().use { ds ->
            ds.broadcast = true
            val dst = InetAddress.getByName(ip)
            ds.send(DatagramPacket(payload, payload.size, dst, port))
        }
        true
    } catch (_: Exception) { false }

    // ---------------- BƯỚC 3: VERIFY ----------------
    fun tcpAlive9100(ip: String, timeoutMs: Int): Boolean = try {
        Socket().use { s -> s.connect(InetSocketAddress(ip, 9100), timeoutMs) }
        true
    } catch (_: Exception) { false }

    fun ping(ip: String, timeoutMs: Int): Boolean = try {
        InetAddress.getByName(ip).isReachable(timeoutMs)
    } catch (_: Exception) { false }

    suspend fun verifyIp(ip: String): Boolean = withContext(Dispatchers.IO) {
        if (tcpAlive9100(ip, 1200)) true else ping(ip, 1200)
    }

    // ====== Build payload XP SAVE (y hệt anh) ======
    private fun buildXpSavePayload(macStr: String, ip: String, mask: String, gw: String): ByteArray {
        val header = HEADER_SAVE_HEX.hexToBytes()
        val save   = SAVE_CMD_HEX.hexToBytes()
        val extra2 = SAVE_EXTRA2_HEX.hexToBytes()
        val tail   = SAVE_TAIL_HEX.hexToBytes()

        val mac = macStrToBytes(macStr)
        val ipB   = ipv4ToBytes(ip)   ?: byteArrayOf(0,0,0,0)
        val maskB = ipv4ToBytes(mask) ?: byteArrayOf(0,0,0,0)
        val gwB   = ipv4ToBytes(gw)   ?: byteArrayOf(0,0,0,0)
        return header + save + mac + extra2 + ipB + maskB + gwB + tail
    }

    // ====== Zywell SAVE 64 bytes (y hệt anh) ======
    private fun le16(v: Int): ByteArray =
        byteArrayOf((v and 0xFF).toByte(), ((v ushr 8) and 0xFF).toByte())

    private fun buildZywellSavePayload(
        macStr: String,
        ip: String,
        mask: String,
        gw: String,
        fixedPort: Int = 9100,
        dhcpOn: Boolean = false,
        modelTag: String = "-zy80-"
    ): ByteArray {
        val header  = "ZY0001".toByteArray(Charsets.US_ASCII)
        val command = "SAVE".toByteArray(Charsets.US_ASCII)
        val mac     = macStrToBytes(macStr)
        val len16   = le16(0x0010)

        val ipB   = ipv4ToBytes(ip)   ?: byteArrayOf(0,0,0,0)
        val maskB = ipv4ToBytes(mask) ?: byteArrayOf(0,0,0,0)
        val gwB   = ipv4ToBytes(gw)   ?: byteArrayOf(0,0,0,0)
        val portB = le16(fixedPort)
        val dhcpB = byteArrayOf(if (dhcpOn) 0x01 else 0x00)

        val model = modelTag.toByteArray(Charsets.US_ASCII)
        val core = header + command + mac + len16 + ipB + maskB + gwB + portB + dhcpB + model
        return if (core.size < 64) core + ByteArray(64 - core.size) else core.copyOf(64)
    }

    // ===== helpers =====
    private fun String.hexToBytes(): ByteArray =
        replace("\\s".toRegex(), "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.indexOfSub(pattern: ByteArray): Int? {
        outer@ for (i in 0..size - pattern.size) {
            for (j in pattern.indices) if (this[i + j] != pattern[j]) continue@outer
            return i
        }
        return null
    }

    private fun ByteArray.toIpv4(): String =
        joinToString(".") { (it.toInt() and 0xFF).toString() }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val out = ByteArray(size + other.size)
        System.arraycopy(this, 0, out, 0, size)
        System.arraycopy(other, 0, out, size, other.size)
        return out
    }

    private fun macStrToBytes(mac: String): ByteArray {
        val clean = mac.replace("[-:]".toRegex(), "").uppercase(Locale.ROOT)
        require(clean.length == 12) { "MAC không hợp lệ: $mac" }
        return ByteArray(6) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private fun ipv4ToBytes(ip: String): ByteArray? {
        return try {
            val p = ip.trim().split(".")
            if (p.size != 4) null
            else byteArrayOf(p[0].toInt().toByte(), p[1].toInt().toByte(), p[2].toInt().toByte(), p[3].toInt().toByte())
        } catch (_: Exception) { null }
    }
}


