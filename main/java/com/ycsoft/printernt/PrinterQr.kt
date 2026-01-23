package com.ycsoft.printernt

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


class PrinterQr : AppCompatActivity() {

    data class PrinterInfo(
        val name: String,
        val brand: String,
        val mac: String,
        val ip: String,
        val sub: String,
        val gate: String
    )

    private lateinit var tabLayout: TabLayout
    private lateinit var layoutQrSmart: View
    private lateinit var layoutPrinterList: View

    // QR tab
    private lateinit var btnScanQr: View
    private lateinit var tvQrCount: TextView
    private lateinit var qrResultContainer: ViewGroup

    // ✅ QR smart lưu tạm cho tới khi bấm "Lưu máy in"
    private var currentQrInfo: PrinterInfo? = null

    // List tab
    private lateinit var btnQuest: View
    private lateinit var tvListCount: TextView
    private lateinit var rvPrinters: RecyclerView

    private val printers = mutableListOf<PrinterInfo>()
    private lateinit var listAdapter: PrinterAdapter

    private val prefs by lazy { getSharedPreferences("printer_qr_store", Context.MODE_PRIVATE) }
    private val KEY_SAVED = "saved_printers"
    // QR SMART: dùng UID logic từ AutoPrinterHelper

    // ===== QR payload chuẩn: Name=Brand=MAC=IP=SUB=Gate =====
    private fun toQrPayload(p: PrinterInfo): String {
        return listOf(p.name, p.brand, p.mac, p.ip, p.sub, p.gate).joinToString("=")
    }

    /**
     * Parse QR:
     * - Ưu tiên 6 phần: name, brand, mac, ip, sub, gate
     * - Nếu QR cũ 5 phần: name=brand=mac=ip=gate (sub trống)
     */
    private fun parseQrPayload(raw: String): PrinterInfo? {
        val s = raw.trim()
        if (s.isEmpty()) return null

        val parts = s.split("=").map { it.trim() }
        if (parts.size < 5) return null

        return if (parts.size >= 6) {
            PrinterInfo(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
        } else {
            PrinterInfo(parts[0], parts[1], parts[2], parts[3], "", parts[4])
        }
    }

    private fun loadSavedPrinters(): MutableList<PrinterInfo> {
        val out = mutableListOf<PrinterInfo>()
        val raw = prefs.getString(KEY_SAVED, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        for (i in 0 until arr.length()) {
            val payload = arr.optString(i, "")
            val p = parseQrPayload(payload)
            if (p != null) out.add(p)
        }
        return out
    }

    private fun savePrintersToPrefs(list: List<PrinterInfo>) {
        val arr = JSONArray()
        list.forEach { arr.put(toQrPayload(it)) }
        prefs.edit().putString(KEY_SAVED, arr.toString()).apply()
    }

    /** Lưu theo MAC (trùng MAC thì update) */
    private fun upsertPrinter(p: PrinterInfo) {
        val idx = printers.indexOfFirst { it.mac.equals(p.mac, ignoreCase = true) }
        if (idx >= 0) printers[idx] = p else printers.add(p)
        savePrintersToPrefs(printers)
        listAdapter.notifyDataSetChanged()
        updateListCount()
    }

    // ===== Validate & Auto =====
    private fun isValidIpv4(ip: String): Boolean {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return false
        return parts.all { p ->
            if (p.isEmpty()) return@all false
            val n = p.toIntOrNull() ?: return@all false
            n in 0..255
        }
    }

    private fun gatewayFromIp(ip: String): String? {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return null
        val a = parts[0].toIntOrNull() ?: return null
        val b = parts[1].toIntOrNull() ?: return null
        val c = parts[2].toIntOrNull() ?: return null
        if (a !in 0..255 || b !in 0..255 || c !in 0..255) return null
        return "$a.$b.$c.1"
    }

    private val askCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openQrScannerPopup()
        else Toast.makeText(this, "Chưa cấp quyền Camera", Toast.LENGTH_SHORT).show()
    }

    private fun openQrScannerPopup() {
        QrScanDialogFragment { raw ->
            val info = parseQrPayload(raw)
            if (info == null) {
                Toast.makeText(this, "QR không đúng định dạng", Toast.LENGTH_SHORT).show()
                return@QrScanDialogFragment
            }
            // ✅ QR smart lưu tạm
            currentQrInfo = info
            showQrResult(info)
        }.show(supportFragmentManager, "qr_scan")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.printer_qr)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        toolbar.setNavigationOnClickListener { finish() }
        findViewById<TextView>(R.id.tvToolbarTitle).setOnClickListener { finish() }

        tabLayout = findViewById(R.id.tabLayout)
        tabLayout.addTab(tabLayout.newTab().setText("QR SMART"))
        tabLayout.addTab(tabLayout.newTab().setText("Danh sách máy in"))

        layoutQrSmart = findViewById(R.id.layoutQrSmart)
        layoutPrinterList = findViewById(R.id.layoutPrinterList)

        // Bind QR tab
        btnScanQr = findViewById(R.id.btnScanQr)
        tvQrCount = findViewById(R.id.tvQrCount)
        qrResultContainer = findViewById(R.id.qrResultContainer)

        // Bind List tab
        btnQuest = findViewById(R.id.btnQuest)
        tvListCount = findViewById(R.id.tvListCount)
        rvPrinters = findViewById(R.id.rvPrinters)

        // RecyclerView setup
        listAdapter = PrinterAdapter(
            items = printers,
            mode = PrinterAdapter.Mode.LIST,
            onEdit = { openEditPrinterDialog(origin = it, saveToPrefs = true) }, // ✅ sửa list -> lưu luôn
            onDelete = {
                // ✅ popup confirm (positive phải tự dismiss vì dialog chỉ đóng khi Hủy)
                lateinit var dlg: ConfirmDialogFragment
                dlg = ConfirmDialogFragment(
                    titleText = "Xóa máy in",
                    messageText = "Anh có chắc muốn xóa máy in này không?\n\n${it.name}\n${it.mac}",
                    positiveText = "Xóa",
                    negativeText = "Hủy"
                ) {
                    printers.remove(it)
                    savePrintersToPrefs(printers)
                    listAdapter.notifyDataSetChanged()
                    updateListCount()
                    dlg.dismissAllowingStateLoss()
                }
                dlg.show(supportFragmentManager, "confirm_delete")
            },
            onQr = { Toast.makeText(this, "QR: ${it.name}", Toast.LENGTH_SHORT).show() },
            onSave = { /* không dùng ở LIST */ },
            onConfig = { Toast.makeText(this, "Cấu hình: ${it.name}", Toast.LENGTH_SHORT).show() }
        )

        rvPrinters.layoutManager = LinearLayoutManager(this)
        rvPrinters.adapter = listAdapter
        printers.clear()
        printers.addAll(loadSavedPrinters())
        listAdapter.notifyDataSetChanged()
        updateListCount()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = switchTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        switchTab(0)

        // QR SMART: Quét
        btnScanQr.setOnClickListener {
            askCameraPermission.launch(android.Manifest.permission.CAMERA)
        }

        // LIST: Quest (demo)
        btnQuest.setOnClickListener {
            printers.clear()
            printers.addAll(
                listOf(
                    PrinterInfo("Máy in thu ngân", "Xprinter", "AA:BB:V5:DD:69", "192.168.1.123", "255.255.255.0", "192.168.1.1"),
                    PrinterInfo("Máy in bếp", "Zywell", "11:22:33:44:55:66", "192.168.1.50", "255.255.255.0", "192.168.1.1")
                )
            )
            listAdapter.notifyDataSetChanged()
            updateListCount()
        }
    }

    private fun showQrResult(info: PrinterInfo) {
        // ✅ luôn cập nhật bản tạm QR smart
        currentQrInfo = info

        tvQrCount.text = "Nhận được 1 máy in"

        qrResultContainer.removeAllViews()
        val v = layoutInflater.inflate(R.layout.item_printer, qrResultContainer, false)
        qrResultContainer.addView(v)
        qrResultContainer.visibility = View.VISIBLE

        // bind data
        v.findViewById<TextView>(R.id.tvTitle).text = info.name
        v.findViewById<TextView>(R.id.tvSubTitle).text = "${info.brand}  •  ${info.mac}"
        v.findViewById<TextView>(R.id.tvIp).text = info.ip
        v.findViewById<TextView>(R.id.tvSub).text = info.sub
        v.findViewById<TextView>(R.id.tvGate).text = info.gate

        // mode QR SMART: hiện actionsQrSmart, ẩn actionsList
        v.findViewById<View>(R.id.actionsQrSmart).visibility = View.VISIBLE
        v.findViewById<View>(R.id.actionsList).visibility = View.GONE

        // ✅ Edit QR smart: chỉ sửa tạm
        v.findViewById<ImageView>(R.id.ivEdit).setOnClickListener {
            openEditPrinterDialog(origin = currentQrInfo ?: info, saveToPrefs = false)
        }

        // ✅ Save: check trùng MAC -> hỏi ghi đè
        v.findViewById<View>(R.id.btnSave).setOnClickListener {
            val cur = currentQrInfo ?: info
            val idx = printers.indexOfFirst { it.mac.equals(cur.mac, ignoreCase = true) }

            if (idx >= 0) {
                lateinit var dlgOverwrite: ConfirmDialogFragment
                dlgOverwrite = ConfirmDialogFragment(
                    titleText = "Máy in đã tồn tại",
                    messageText = "Đã có máy in trùng MAC.\nAnh có muốn ghi đè không?\n\n${cur.name}\n${cur.mac}",
                    positiveText = "Ghi đè",
                    negativeText = "Hủy"
                ) {
                    upsertPrinter(cur)
                    Toast.makeText(this, "Đã ghi đè: ${cur.name}", Toast.LENGTH_SHORT).show()
                    dlgOverwrite.dismissAllowingStateLoss()
                }
                dlgOverwrite.show(supportFragmentManager, "confirm_overwrite")
            } else {
                upsertPrinter(cur)
                Toast.makeText(this, "Đã lưu: ${cur.name}", Toast.LENGTH_SHORT).show()
            }
        }

        v.findViewById<View>(R.id.btnConfigQr).setOnClickListener {
            val cur = currentQrInfo
            if (cur == null) {
                Toast.makeText(this, "Chưa có thông tin QR để cấu hình", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lateinit var dlgConfirm: ConfirmDialogFragment
            dlgConfirm = ConfirmDialogFragment(
                titleText = "Cấu hình máy in",
                messageText = "Anh có muốn cấu hình máy in \"${cur.name}\" theo thông tin QR quét được không?\n\n" +
                        "Hãng: ${cur.brand}\nMAC: ${cur.mac}\nIP mới: ${cur.ip}\nMask: ${cur.sub}\nGW: ${cur.gate}",
                positiveText = "Có",
                negativeText = "Không"
            ) {
// Positive KHÔNG tự dismiss -> mình xử lý xong sẽ dismiss
                lifecycleScope.launch {
                    dlgConfirm.dismissAllowingStateLoss()

                    val progressDlg = IpChangeProgressDialogFragment()
                    progressDlg.show(supportFragmentManager, "ip_change_progress")
                    supportFragmentManager.executePendingTransactions()

                    fun ui(block: () -> Unit) = runOnUiThread { block() }

                    try {
                        // STEP 1
                        ui { progressDlg.showStep(1, "Bước 1: Đang kiểm tra máy in trên mạng...") }

                        val foundDevice = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val brandTag = QrSmartUidApi.brandToTag(cur.brand)
                            QrSmartUidApi.findByMac(
                                ctx = this@PrinterQr,
                                macNeed = cur.mac,
                                brandTag = brandTag,
                                timeoutMs = 2500
                            )
                        }

                        if (foundDevice == null) {
                            ui {
                                progressDlg.setFail(
                                    step = 1,
                                    msg = "Không tìm thấy máy in MAC ${cur.mac} trên mạng.\nHãy kiểm tra Wi-Fi/LAN và thử lại."
                                )
                            }
                            return@launch
                        }
                        ui { progressDlg.setOk(1) }

                        // STEP 2
                        ui { progressDlg.showStep(2, "Bước 2: Đang gửi lệnh đổi IP...") }

                        val okSet = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            QrSmartUidApi.setIp(
                                ctx = this@PrinterQr,
                                device = foundDevice,
                                ipNew = cur.ip,
                                mask = cur.sub,
                                gw = cur.gate
                            )
                        }


                        if (!okSet) {
                            ui {
                                progressDlg.setFail(
                                    step = 2,
                                    msg = "Gửi đổi IP thất bại. Anh thử lại giúp em."
                                )
                            }
                            return@launch
                        }
                        ui { progressDlg.setOk(2) }

                        delay(1200)

                        // STEP 3
                        ui {
                            progressDlg.showStep(
                                3,
                                "Bước 3: Đang kiểm tra lại IP mới (${cur.ip})..."
                            )
                        }

                        val okVerify = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            QrSmartUidApi.verifyIp(cur.ip)
                        }

                        if (okVerify) {
                            ui {
                                progressDlg.setOk(3)
                                progressDlg.finishAll(msg = "✅ Đổi IP thành công: (${cur.ip})\nAnh bấm OK để đóng.")
                            }
                        } else {
                            ui {
                                progressDlg.setFail(
                                    step = 3,
                                    msg = "Đã gửi đổi IP nhưng chưa xác nhận được ở IP mới.\nAnh đợi 3–5 giây rồi thử lại."
                                )
                            }
                        }

                    } catch (e: Exception) {
                        ui { progressDlg.setFail(step = 3, msg = "Lỗi cấu hình: ${e.message}") }
                    }
                }
            }

            dlgConfirm.show(supportFragmentManager, "confirm_config_qr")
        }

    }

    /**
     * Popup sửa thông tin:
     * - Sửa: Tên máy in, IP, Subnet, Gateway
     * - Hãng: chọn Xprinter/Zywell
     * - MAC: chỉ hiển thị (read-only)
     *
     * saveToPrefs:
     *  - true: sửa trong Danh sách máy in -> lưu xuống bộ nhớ ngay
     *  - false: sửa trong QR smart -> chỉ lưu tạm (currentQrInfo) cho tới khi bấm "Lưu máy in"
     */
    private fun openEditPrinterDialog(origin: PrinterInfo, saveToPrefs: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_printer, null, false)

        val edName = dialogView.findViewById<EditText>(R.id.edName)
        val spBrand = dialogView.findViewById<Spinner>(R.id.spBrand)
        val edMacReadonly = dialogView.findViewById<EditText>(R.id.edMacReadonly)
        val edIp = dialogView.findViewById<EditText>(R.id.edIp)
        val edSub = dialogView.findViewById<EditText>(R.id.edSub)
        val edGate = dialogView.findViewById<EditText>(R.id.edGate)

        // ✅ 3.1: Tên không xuống dòng + chặn ký tự đặc biệt
        edName.setSingleLine(true)
        edName.maxLines = 1
        edName.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
            val s = source.toString()
            if (s.contains('\n') || s.contains('\r')) return@InputFilter ""
            // cho phép: chữ (kể cả tiếng Việt), số, khoảng trắng, _ -
            val ok = Regex("^[a-zA-Z0-9À-ỹ _-]+$")
            if (ok.matches(s)) null else ""
        })

        val brands = listOf("Xprinter", "Zywell")
        spBrand.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            brands
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Fill
        edName.setText(origin.name)
        edMacReadonly.setText(origin.mac)
        edIp.setText(origin.ip)
        edSub.setText(origin.sub)
        edGate.setText(origin.gate)

        val idxBrand = brands.indexOfFirst { it.equals(origin.brand, ignoreCase = true) }
        spBrand.setSelection(if (idxBrand >= 0) idxBrand else 0)

        // ✅ 3.3: auto subnet + auto gateway (nhưng vẫn cho sửa)
        var userEditedGateway = false

        if (edSub.text.isNullOrBlank()) edSub.setText("255.255.255.0")
        if (edGate.text.isNullOrBlank()) {
            gatewayFromIp(edIp.text?.toString()?.trim().orEmpty())?.let { edGate.setText(it) }
        }

        edGate.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) userEditedGateway = true
        }
        edGate.setOnClickListener { userEditedGateway = true }

        edIp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val ip = s?.toString()?.trim().orEmpty()
                if (edSub.text.isNullOrBlank()) edSub.setText("255.255.255.0")
                if (!userEditedGateway) {
                    gatewayFromIp(ip)?.let { edGate.setText(it) }
                }
            }
        })

        // ✅ Popup chỉ đóng bằng Hủy, nên Positive phải tự dismiss khi xử lý xong
        lateinit var dlg: ConfirmDialogFragment
        dlg = ConfirmDialogFragment(
            titleText = "Sửa thông tin",
            customContentView = dialogView,
            positiveText = "Lưu",
            negativeText = "Hủy"
        ) {
            val name = edName.text?.toString()?.trim().orEmpty()
            val ip = edIp.text?.toString()?.trim().orEmpty()
            val sub = edSub.text?.toString()?.trim().orEmpty()
            val gate = edGate.text?.toString()?.trim().orEmpty()
            val brand = brands.getOrNull(spBrand.selectedItemPosition) ?: "Xprinter"

            if (name.isBlank()) {
                Toast.makeText(this, "Tên máy in không được để trống", Toast.LENGTH_SHORT).show()
                return@ConfirmDialogFragment
            }

            // ✅ 3.2: chặn IP sai định dạng
            if (!isValidIpv4(ip)) {
                Toast.makeText(this, "IP không đúng định dạng IPv4", Toast.LENGTH_SHORT).show()
                return@ConfirmDialogFragment
            }

            val updated = PrinterInfo(
                name = name,
                brand = brand,
                mac = origin.mac,
                ip = ip,
                sub = sub,
                gate = gate
            )

            if (saveToPrefs) {
                val idx = printers.indexOfFirst { it.mac.equals(origin.mac, ignoreCase = true) }
                if (idx >= 0) {
                    printers[idx] = updated
                    savePrintersToPrefs(printers)
                    listAdapter.notifyDataSetChanged()
                    updateListCount()
                }
            } else {
                currentQrInfo = updated
                showQrResult(updated)
            }

            dlg.dismissAllowingStateLoss()
        }

        dlg.show(supportFragmentManager, "edit_printer")
    }

    private fun updateListCount() {
        tvListCount.text = "Danh sách máy in: ${printers.size}"
    }

    private fun switchTab(pos: Int) {
        if (pos == 0) {
            layoutQrSmart.visibility = View.VISIBLE
            layoutPrinterList.visibility = View.GONE
        } else {
            layoutQrSmart.visibility = View.GONE
            layoutPrinterList.visibility = View.VISIBLE
        }
    }

    // ===== Adapter dùng chung item_printer.xml =====
    private class PrinterAdapter(
        private val items: MutableList<PrinterInfo>,
        private val mode: Mode,
        private val onEdit: (PrinterInfo) -> Unit,
        private val onDelete: (PrinterInfo) -> Unit,
        private val onQr: (PrinterInfo) -> Unit,
        private val onSave: (PrinterInfo) -> Unit,
        private val onConfig: (PrinterInfo) -> Unit

    ) : RecyclerView.Adapter<PrinterAdapter.VH>() {

        enum class Mode { LIST, QR_SMART }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvTitle)
            val tvSubTitle: TextView = v.findViewById(R.id.tvSubTitle)
            val tvIp: TextView = v.findViewById(R.id.tvIp)
            val tvSub: TextView = v.findViewById(R.id.tvSub)
            val tvGate: TextView = v.findViewById(R.id.tvGate)
            val ivEdit: ImageView = v.findViewById(R.id.ivEdit)

            val actionsQrSmart: View = v.findViewById(R.id.actionsQrSmart)
            val actionsList: View = v.findViewById(R.id.actionsList)

            val btnDelete: View = v.findViewById(R.id.btnDelete)
            val btnQr: View = v.findViewById(R.id.btnQr)
            val btnConfigList: View = v.findViewById(R.id.btnConfigList)

            val btnSave: View = v.findViewById(R.id.btnSave)
            val btnConfigQr: View = v.findViewById(R.id.btnConfigQr)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_printer, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val item = items[position]

            h.tvTitle.text = item.name
            h.tvSubTitle.text = "${item.brand}  •  ${item.mac}"
            h.tvIp.text = item.ip
            h.tvSub.text = item.sub
            h.tvGate.text = item.gate

            if (mode == Mode.LIST) {
                h.actionsList.visibility = View.VISIBLE
                h.actionsQrSmart.visibility = View.GONE
            } else {
                h.actionsList.visibility = View.GONE
                h.actionsQrSmart.visibility = View.VISIBLE
            }

            h.ivEdit.setOnClickListener { onEdit(item) }
            h.btnDelete.setOnClickListener { onDelete(item) }
            h.btnQr.setOnClickListener { onQr(item) }
            h.btnSave.setOnClickListener { onSave(item) }

            h.btnConfigList.setOnClickListener { onConfig(item) }
            h.btnConfigQr.setOnClickListener { onConfig(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
