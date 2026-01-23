package com.ycsoft.printernt

// liên kết với file mã lệnh PrinterCommands
import com.ycsoft.printernt.commands.PrinterCommands
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View
import android.widget.TextView
import com.ycsoft.printernt.R
import android.widget.CheckBox



/**
 * ===== usb printer: helper (tách khỏi MainActivity) =====
 * - Liệt kê thiết bị USB (Spinner)
 * - Quét lại danh sách
 * - In test ESC/POS (kèm cắt) qua Bulk-OUT
 * - Đổi IP qua USB (payload giống khối LAN: 0x1F 1B 1F B2 + IP/MASK/GW)
 */
class UsbPrinterHelper(
    private val context: Context,
    private val spinner: Spinner,
    private val btnScan: MaterialButton,
    private val btnTest: MaterialButton,
    // NEW: nút Đổi IP (USB)
    private val btnChangeIp: MaterialButton,
    // NEW: dùng lại 3 ô nhập từ khối cấu hình IP
    private val etNewIp: TextInputEditText,
    private val etMask: TextInputEditText,
    private val etGw: TextInputEditText,
    private val log: (String) -> Unit,
    private val showResult: (String) -> Unit,
    private val showLoading: (String) -> Unit,
    private val dismissLoading: () -> Unit,
    private val useLabelCommand: () -> Boolean   // <<< THÊM
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val usbList = mutableListOf<UsbDevice>()
    private val usbAdapter = ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item)

    companion object {
        private const val ACTION_USB_PERMISSION = "com.ycsoft.nextool.USB_PERMISSION"
    }

    // popup check usb cho các nút
    private inline fun withDevice(block: (UsbDevice) -> Unit) {
        val dev = currentSelectedDevice()
        if (dev == null) {
            showResult(context.getString(R.string.err_usb_no_device))
            return
        }
        block(dev)
    }


    // Một số VID tham khảo
    private val vendorMap = mapOf(
        0x04835 to "Printer - 80"
    )

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ACTION_USB_PERMISSION) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                log("USB permission for ${device?.deviceName}: $granted")
            }
        }
    }

    /** Gửi payload RAW qua USB Bulk-OUT */
    private fun sendRawOverUsb(device: UsbDevice, data: ByteArray, timeoutMs: Int = 2000): Boolean {
        return try {
            // Xin quyền & mở đúng cổng USB
            requestUsbPermission(device)
            if (!usbManager.hasPermission(device)) return false
            val pair = findBulkOut(device) ?: return false
            val (intf, epOut) = pair
            val conn = usbManager.openDevice(device) ?: return false
            var ok = false
            try {
                if (!conn.claimInterface(intf, true)) return false
                ok = conn.bulkTransfer(epOut, data, data.size, timeoutMs) == data.size
            } finally {
                try { conn.releaseInterface(intf) } catch (_: Exception) {}
                try { conn.close() } catch (_: Exception) {}
            }
            ok
        } catch (_: Exception) { false }
    }

    // Trạng thái nhỏ ngay trên spinner (set trong init)
    private var tvUsbStatus: TextView? = null
    // Checkbox "labels" dùng chọn lệnh 0x22
    private var cbLabels: CheckBox? = null

    // Receiver phát hiện cắm/tháo USB → tự refresh + tự chọn
    private val usbHotplugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    log("USB attached → refresh list")
                    refreshUsbList(autoSelect = true, requestPermission = true, updateStatus = true)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    log("USB detached → refresh list")
                    refreshUsbList(updateStatus = true)
                }
            }
        }
    }

    /** Gắn adapter, đăng ký receiver, bind nút & quét lần đầu */
    fun init() {
        spinner.adapter = usbAdapter

        // Quyền USB (giữ nguyên)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(usbPermissionReceiver, filter)
        }

        // NEW: đăng ký hot-plug (cắm/tháo)
        val hotFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(usbHotplugReceiver, hotFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(usbHotplugReceiver, hotFilter)
        }




        btnScan.setOnClickListener {
            showLoading("Đang quét USB ...")
            refreshUsbList(updateStatus = true)
            dismissLoading()
        }
        btnTest.setOnClickListener { sendUsbTestToSelected() }
        btnChangeIp.setOnClickListener { sendUsbChangeIpToSelected() } // NEW

        val act = (context as? Activity)
        tvUsbStatus = act?.findViewById(R.id.tvUsbStatus)
        cbLabels    = act?.findViewById(R.id.cbLabels)   // <-- LẤY CHECKBOX

        // NEW: quét lần đầu + cập nhật dòng trạng thái
        refreshUsbList(updateStatus = true)



        refreshUsbList()
        // ===== USB › Nâng cao (UI) =====


        act?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsbAdvRestore)
            ?.setOnClickListener { sendUsbRestoreToSelected() }

        act?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsbAdvEscposTest)
            ?.setOnClickListener { sendUsbEscposCutToSelected() }

        act?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsbAdvCashDrawer)
            ?.setOnClickListener { sendUsbCashDrawerToSelected() }

        act?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsbInfo)
            ?.setOnClickListener { sendUsbInfoToSelected() }

        // Bắt 2 ô nhập và nút Set Wi-Fi (nằm trong groupUsbAdvanced của USB)
        val etUsbWifiSsid = act?.findViewById<TextInputEditText>(R.id.etUsbWifiSsid)
        val etUsbWifiPassword = act?.findViewById<TextInputEditText>(R.id.etUsbWifiPassword)

        act?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsbSetWifi)
            ?.setOnClickListener {
                val ssid = etUsbWifiSsid?.text?.toString().orEmpty()
                val pwd  = etUsbWifiPassword?.text?.toString().orEmpty()
                sendUsbSetWifiToSelected(ssid, pwd)
            }

// Toggle bung/thu nhóm Nâng cao (USB)
        val tvUsbAdv = act?.findViewById<TextView>(R.id.tvUsbAdvancedToggle)
        val groupUsbAdv = act?.findViewById<View>(R.id.groupUsbAdvanced)
        tvUsbAdv?.setOnClickListener {
            groupUsbAdv ?: return@setOnClickListener
            val opening = groupUsbAdv.visibility != View.VISIBLE
            groupUsbAdv.visibility = if (opening) View.VISIBLE else View.GONE
            tvUsbAdv.text = if (opening)
                context.getString(R.string.advanced) + " ▾"
            else
                context.getString(R.string.advanced) + " ▸"

        }

// Xử lý dữ liệu ở lệnh beep
        val spBeepEnable = act?.findViewById<Spinner>(R.id.spUsbBeepEnable)
        val spBeepCounter = act?.findViewById<Spinner>(R.id.spUsbBeepCounter)
        val spBeepTime   = act?.findViewById<Spinner>(R.id.spUsbBeepTime)
        val spBeepMode   = act?.findViewById<Spinner>(R.id.spUsbBeepMode)
        val btnBeepSet   = act?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsbAdvBeepSet)

        // Gắn dữ liệu spinner
        fun <T> Spinner?.simple(items: List<T>) {
            this ?: return
            adapter = ArrayAdapter(this.context, android.R.layout.simple_spinner_dropdown_item, items)
        }
        spBeepEnable.simple(listOf("ON", "OFF"))
        spBeepCounter.simple((1..20).toList())
        spBeepTime.simple((1..20).toList())
        spBeepMode.simple(listOf(0,1,2,3,4))

        // Gía trị mặc định
        spBeepEnable?.setSelection(0)  // "ON"
        spBeepCounter?.setSelection(3) // 4
        spBeepTime?.setSelection(1)    // 2
        spBeepMode?.setSelection(3)    // 3

        btnBeepSet?.setOnClickListener {

            val enable = (spBeepEnable?.selectedItem?.toString() ?: "ON").equals("ON", true)
            val cnt = ((spBeepCounter?.selectedItem as? Int) ?: 4).coerceIn(1, 20)
            val t50 = ((spBeepTime?.selectedItem   as? Int) ?: 2).coerceIn(1, 20)
            val md  = ((spBeepMode?.selectedItem   as? Int) ?: 3).coerceIn(0, 4)

            // lấy được 4 giá trị rồi gọi hàm sendUsbBeepSetToSelected
            sendUsbBeepSetToSelected(enable, cnt, t50, md)
        }



// Xử lý lựa chọn on/off
        val spUsbDhcp = act?.findViewById<Spinner>(R.id.spUsbDhcp)
        val btnUsbDhcpSet = act?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsbAdvDhcpSet)
        spUsbDhcp.simple(listOf("ON", "OFF"))
        spUsbDhcp?.setSelection(1) // mặc định OFF

        btnUsbDhcpSet?.setOnClickListener {

            // Lấy trạng thái ON/OFF từ spinner
            val turningOn = (spUsbDhcp?.selectedItem?.toString() ?: "OFF").equals("ON", true)

            sendUsbDhcpToSelected(turningOn)
        }



    }

    /** Hủy receiver để tránh leak */
    fun release() {
        try { context.unregisterReceiver(usbPermissionReceiver) } catch (_: Exception) {}
        try { context.unregisterReceiver(usbHotplugReceiver) } catch (_: Exception) {} // NEW
    }


    // ===== usb printer: actions =====

    fun refreshUsbList(
        autoSelect: Boolean = false,
        requestPermission: Boolean = false,
        updateStatus: Boolean = false
    ) {
        usbList.clear()
        usbAdapter.clear()

        val map = usbManager.deviceList
        if (map.isEmpty()) {
            usbAdapter.add(context.getString(R.string.usb_not_found))
            usbAdapter.notifyDataSetChanged()
            //log("USB: không có device nào.")
            if (updateStatus) tvUsbStatus?.text = context.getString(R.string.usb_not_found)
            return
        }

        usbList += map.values
        usbList.sortBy { it.deviceName }

        usbList.forEachIndexed { index, dev ->
            val label = buildLabel(dev)
            usbAdapter.add("${index + 1}: $label")
        }
        usbAdapter.notifyDataSetChanged()
        //log("USB: phát hiện ${usbList.size} thiết bị.")

        // NEW: cập nhật dòng trạng thái
        if (updateStatus) tvUsbStatus?.text = context.getString(R.string.usb_found)+" ${usbList.size} USB"

        // NEW: tự chọn khi có 1 thiết bị, hoặc ép autoSelect
        if (usbList.isNotEmpty()) {
            val pick = 0 // ưu tiên chọn phần tử đầu
            spinner.setSelection(pick)

            // NEW: xin quyền ngay nếu được yêu cầu
            if (requestPermission) {
                try { requestUsbPermission(usbList[pick]) } catch (_: Exception) {}
            }
        }
    }





    // ===== quét hiển thị USB

    private fun buildLabel(device: UsbDevice): String {
        // 1) Ưu tiên chuỗi Android parse sẵn (không cần permission)
        val manu0 = device.manufacturerName?.trim().orEmpty()
        val prod0 = device.productName?.trim().orEmpty()
        val vp = "VID:${hex4(device.vendorId)} PID:${hex4(device.productId)}"

        if (manu0.isNotEmpty() || prod0.isNotEmpty()) {
            val name = listOf(manu0, prod0).filter { it.isNotEmpty() }.joinToString(" - ")
            return "$name ($vp)"
        }

        // 2) Nếu chưa có, thử đọc thủ công (chỉ khi đã có permission)
        val (manu, prod) = getManufacturerProduct(device)
        if (!manu.isNullOrBlank() || !prod.isNullOrBlank()) {
            return "${manu ?: "USB"} - ${prod ?: "Printer"} ($vp)"
        }

        // 3) Cuối cùng: đoán theo vendor map hoặc hiển thị VID/PID
        val guess = vendorMap[device.vendorId]
        return guess?.let { "$it ($vp)" } ?: "$vp - ${device.deviceName}"
    }


    private fun getManufacturerProduct(device: UsbDevice): Pair<String?, String?> {
        val conn = usbManager.openDevice(device) ?: return null to null
        return try {
            val dd = readDeviceDescriptor(conn) ?: return null to null
            val iManu = dd[14].toInt() and 0xFF
            val iProd = dd[15].toInt() and 0xFF
            val manu = readUsbString(conn, iManu)
            val prod = readUsbString(conn, iProd)
            manu to prod
        } catch (_: Exception) {
            null to null
        } finally {
            try { conn.close() } catch (_: Exception) {}
        }
    }

    private fun readUsbString(conn: UsbDeviceConnection, index: Int, langId: Int = 0x0409): String? {
        if (index <= 0) return null
        val reqType = 0x80
        val request = 0x06
        val value = (0x03 shl 8) or (index and 0xFF)
        val buf = ByteArray(255)
        val len = conn.controlTransfer(reqType, request, value, langId, buf, buf.size, 200)
        if (len <= 2) return null
        return try { String(buf, 2, len - 2, Charsets.UTF_16LE).trim() } catch (_: Exception) { null }
    }

    private fun readDeviceDescriptor(conn: UsbDeviceConnection): ByteArray? {
        val reqType = 0x80
        val request = 0x06
        val value = (0x01 shl 8) or 0x00
        val buf = ByteArray(18)
        val len = conn.controlTransfer(reqType, request, value, 0, buf, buf.size, 200)
        return if (len == 18) buf else null
    }

    // ===== usb printer: core send =====

    private fun requestUsbPermission(device: UsbDevice) {
        if (!usbManager.hasPermission(device)) {
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, pi)
        }
    }

    // Nút kiểm tra USB
    private fun sendUsbTestToSelected() = withDevice { device ->
        CoroutineScope(Dispatchers.Main).launch {
            showLoading(context.getString(R.string.msg_sending))

            val payload = PrinterCommands.escposTestAndCut("NEXTOOL CHECK USB PORT")
            val ok = withContext(Dispatchers.IO) { sendRawOverUsb(device, payload, 2000) }

            dismissLoading()
            showResult(if (ok) context.getString(R.string.sent_check_printer) else context.getString(R.string.cmd_fail))
            //log("USB: ESC/POS TEST+CUT, bytes=${payload.size}")
        }
    }

    //  Đổi IP (USB)
    //  Đổi IP (USB)
    private fun sendUsbChangeIpToSelected() {
        val ip   = etNewIp.text?.toString()?.trim().orEmpty()
        val mask = etMask.text?.toString()?.trim().orEmpty()
        val gw   = etGw.text?.toString()?.trim().orEmpty()

        if (!isValidIpv4(ip)) {
            showResult(context.getString(R.string.err_ip_new_invalid))
            return
        }

        // Xem người dùng có tick "labels" không
        val use22 = (cbLabels?.isChecked == true)

        withDevice { device ->
            CoroutineScope(Dispatchers.Main).launch {
                showLoading(context.getString(R.string.msg_sending))

                val payload = if (use22) {
                    // Lệnh mới: 1F 1B 1F 22 [IP]
                    PrinterCommands.setIp22(ip)
                } else {
                    // Lệnh cũ: 1F 1B 1F B2 [IP][MASK][GW]
                    PrinterCommands.setIpB2(ip, mask, gw)
                }

                val ok = withContext(Dispatchers.IO) {
                    sendRawOverUsb(device, payload, 2000)
                }

                dismissLoading()

                if (use22) {
                    showResult(
                        if (ok)
                            context.getString(R.string.sent_change_ip) + " ip=$ip (USB cmd 0x22)"
                        else
                            context.getString(R.string.cmd_fail)
                    )
                    log(context.getString(R.string.sent_change_ip) + " ip=$ip (USB cmd 0x22)")
                } else {
                    showResult(
                        if (ok)
                            context.getString(R.string.sent_change_ip) + " ip=$ip mask=$mask gw=$gw"
                        else
                            context.getString(R.string.cmd_fail)
                    )
                    log(context.getString(R.string.sent_change_ip) + " ip=$ip mask=$mask gw=$gw")
                }
            }
        }
    }


    // Nút test page
    private fun sendUsbInfoToSelected() = withDevice { device ->
        CoroutineScope(Dispatchers.Main).launch {
            showLoading(context.getString(R.string.msg_sending))
            val payload = PrinterCommands.testPage()
            val ok = withContext(Dispatchers.IO) { sendRawOverUsb(device, payload) }
            dismissLoading()
            showResult(if (ok) context.getString(R.string.sent_test_page) else context.getString(R.string.cmd_fail))
            //log("USB: Đã gửi lệnh in thông tin máy in")
        }
    }

    // NÚT cắt giấy
    private fun sendUsbEscposCutToSelected() = withDevice { device ->
        CoroutineScope(Dispatchers.Main).launch {
            showLoading(context.getString(R.string.msg_sending))
            val ok = withContext(Dispatchers.IO) {
                val payload = PrinterCommands.escposCut()
                sendRawOverUsb(device, payload)
            }
            dismissLoading()
            showResult(if (ok) context.getString(R.string.sent_cut_test) else context.getString(R.string.cmd_fail))
            //log("Đã gửi lệnh cắt giấy (USB)")
        }
    }



    // Nút két
    private fun sendUsbCashDrawerToSelected() = withDevice { device ->
        val payload = PrinterCommands.cashDrawer()
        CoroutineScope(Dispatchers.Main).launch {
            showLoading(context.getString(R.string.msg_sending))
            val ok = withContext(Dispatchers.IO) { sendRawOverUsb(device, payload) }
            dismissLoading()
            showResult(if (ok) context.getString(R.string.sent_cashdrawer) else context.getString(R.string.cmd_fail))
            //log("USB: Cash Drawer (ESC p)")
        }
    }


    // Nút beep
    private fun sendUsbBeepSetToSelected(enable: Boolean, counter: Int, time50ms: Int, mode: Int) =
        withDevice { device ->
            val payload = PrinterCommands.beepSet(enable, counter, time50ms, mode)
            CoroutineScope(Dispatchers.Main).launch {
                showLoading(context.getString(R.string.msg_sending))
                val ok = withContext(Dispatchers.IO) { sendRawOverUsb(device, payload) }
                dismissLoading()
                showResult(if (ok) context.getString(R.string.sent_beep_set)
                else context.getString(R.string.cmd_fail))
                log(context.getString(R.string.sent_beep_set) + " → en=$enable, count=$counter, time=${time50ms}×50ms, mode=$mode")
            }
        }


    // Nút DHCP
    private fun sendUsbDhcpToSelected(turnOn: Boolean) = withDevice { device ->
        val payload = if (turnOn) PrinterCommands.dhcpOn() else PrinterCommands.dhcpOff()
        CoroutineScope(Dispatchers.Main).launch {
            showLoading(context.getString(R.string.msg_sending))
            val ok = withContext(Dispatchers.IO) { sendRawOverUsb(device, payload) }
            dismissLoading()
            showResult(if (ok) context.getString(R.string.sent_dhcp_set) else context.getString(R.string.cmd_fail))
            //log("USB: DHCP ${if (turnOn) "ON" else "OFF"} (payload giống LAN)")
        }
    }



    // Nút Reset
    private fun sendUsbRestoreToSelected() = withDevice { device ->
        val payload = PrinterCommands.restoreFactory()
        CoroutineScope(Dispatchers.Main).launch {
            showLoading(context.getString(R.string.msg_sending))
            val ok = withContext(Dispatchers.IO) { sendRawOverUsb(device, payload) }
            dismissLoading()
            showResult(if (ok) context.getString(R.string.sent_restore_factory) else context.getString(R.string.cmd_fail))
            //log("USB: Restore Factory")
        }
    }

    // nút wifi
    private fun sendUsbSetWifiToSelected(ssid: String, pwd: String) {
        if (ssid.isBlank()) {
            showResult(context.getString(R.string.err_wifi_ssid_required))
            return
        }
        withDevice { device ->
            val payload = PrinterCommands.wifiSet(ssid, pwd)
            CoroutineScope(Dispatchers.Main).launch {
                showLoading(context.getString(R.string.msg_sending))
                val ok = withContext(Dispatchers.IO) { sendRawOverUsb(device, payload) }
                dismissLoading()
                showResult(if (ok) context.getString(R.string.sent_wifi_set) + " → SSID=\"$ssid\", Password=\"$pwd\"" else context.getString(R.string.cmd_fail))
                log(context.getString(R.string.sent_wifi_set) + " → SSID=\"$ssid\", Password=\"$pwd\"")
            }
        }
    }



    /** Tìm endpoint Bulk-OUT để gửi dữ liệu */
    private fun findBulkOut(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                val isBulkOut = ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        ep.direction == UsbConstants.USB_DIR_OUT
                if (isBulkOut) return intf to ep
            }
        }
        return null
    }

    private fun currentSelectedDevice(): UsbDevice? {
        val idx = spinner.selectedItemPosition
        if (idx < 0 || idx >= usbList.size) return null
        return usbList[idx]
    }

    private fun hex4(v: Int) = "0x" + v.toString(16).uppercase().padStart(4, '0')
    // ===== Helpers: IP/bytes & validation =====



    private fun isValidIpv4(s: String): Boolean {
        val parts = s.split('.')
        if (parts.size != 4) return false
        return try {
            parts.all { it.isNotEmpty() && it.toInt() in 0..255 }
        } catch (_: Exception) { false }
    }
}
