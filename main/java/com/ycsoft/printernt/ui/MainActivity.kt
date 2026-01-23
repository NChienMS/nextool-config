    package com.ycsoft.printernt.ui


    import com.ycsoft.printernt.databinding.ActivityMainBinding

    import com.google.android.material.textfield.TextInputLayout
    import android.view.MotionEvent
    import android.widget.ImageView
    import com.ycsoft.printernt.UpdateActivity
    // liên kết với file mã lệnh PrinterCommands


    import com.ycsoft.printernt.UsbPrinterHelper
    import com.ycsoft.printernt.AutoPrinterHelper
    import com.ycsoft.printernt.LanPrinterHelper
    import com.ycsoft.printernt.LocaleManager
    import android.view.KeyEvent
    import com.ycsoft.printernt.WebNavigator


    import android.content.ActivityNotFoundException
    import android.content.Context
    import android.content.Intent
    import android.net.ConnectivityManager
    import android.net.DhcpInfo
    import android.net.LinkAddress
    import android.net.NetworkCapabilities
    import android.net.wifi.WifiInfo
    import android.net.wifi.WifiManager
    import android.os.Build
    import android.os.Bundle
    import android.provider.Settings
    import android.text.format.Formatter
    import android.view.Gravity
    import android.view.LayoutInflater
    import android.view.View
    import android.widget.*
    import android.widget.FrameLayout
    import androidx.appcompat.app.AlertDialog
    import androidx.appcompat.app.AppCompatActivity
    import androidx.drawerlayout.widget.DrawerLayout
    import androidx.lifecycle.lifecycleScope
    import kotlinx.coroutines.delay
    import androidx.recyclerview.widget.LinearLayoutManager
    import androidx.recyclerview.widget.RecyclerView

    import com.google.android.material.bottomsheet.BottomSheetBehavior
    import com.google.android.material.bottomsheet.BottomSheetDialog
    import com.google.android.material.button.MaterialButton
    import com.google.android.material.textfield.TextInputEditText
    import kotlinx.coroutines.launch
    import com.ycsoft.printernt.ads.remotenoti.RemoteNotiDialogFragment
    import com.ycsoft.printernt.ads.remotenoti.RemoteNotiManager
    import java.net.*
    import java.text.SimpleDateFormat
    import java.util.*
    import com.ycsoft.printernt.BuildConfig
    import com.ycsoft.printernt.R

    // imports cần thiết
    import android.content.ClipData
    import android.content.ClipboardManager
    import android.net.Uri
    import android.widget.TextView
    import android.widget.Toast
    import com.ycsoft.printernt.ContactInfo

    import kotlinx.coroutines.*
    import android.util.Log




    class MainActivity : AppCompatActivity() {


        private lateinit var binding: ActivityMainBinding

        private lateinit var bannerContainer: FrameLayout
        private var bannerLoaded = false
        // === UID views (cho Auto) ===-
        private var tvUidFoundCount: TextView? = null
        private var lvUidPrinters: ListView? = null
        private var btnUidApply: com.google.android.material.button.MaterialButton? = null

        // Drawer
        private lateinit var drawerLayout: DrawerLayout

        private lateinit var ivSettings: ImageView
        private lateinit var ivHelp: ImageView
        private lateinit var btnCloseDrawer: ImageButton

        // Network block views
        private lateinit var tvSsid: TextView
        private lateinit var tvIp: TextView
        private lateinit var tvMask: TextView
        private lateinit var tvGw: TextView
        private lateinit var btnRefreshNet: TextView
        private lateinit var btnOpenWifi: TextView
        private lateinit var btnLogs: View

        // Config IP inputs
        private lateinit var etNewIp: TextInputEditText
        private lateinit var etMask: TextInputEditText
        private lateinit var etGw: TextInputEditText

        // Port select
        private lateinit var rgPort: RadioGroup
        private lateinit var rbUsb: RadioButton
        private lateinit var rbLan: RadioButton
        private lateinit var rbUid: RadioButton
        private lateinit var usbGroup: View
        private lateinit var lanGroup: View
        private lateinit var uidGroup: View
        private lateinit var etCurrentIp: TextInputEditText
        private lateinit var btnCheck: MaterialButton
        private lateinit var btnChangeIp: MaterialButton
        private lateinit var btnScanUid: MaterialButton
        private var btnSwapIp: MaterialButton? = null
        private var usbHelper: UsbPrinterHelper? = null
        private var lanHelper: LanPrinterHelper? = null
        private lateinit var cbLabels: CheckBox



        // Nâng cao (optional in layout)
        private var tvAdvancedToggle: TextView? = null
        private var groupAdvanced: View? = null

        // Ghi nhớ "IP hiện tại"
        private var originalPrinterIp: String? = null
        private var lastKnownOldIp: String? = null
        private var swappedToNew = false

        // Trạng thái + dialog Loading
        private var isChecking = false
        private var isChanging = false
        private var loadingDialog: AlertDialog? = null

        // Logs
        private val logs = mutableListOf<String>()
        private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        private var resumedAfterBackground = false




        //áp dụng ngôn ngữ đã chọn trước khi inflate UI.
        override fun attachBaseContext(newBase: Context?) {
            super.attachBaseContext(newBase?.let { LocaleManager.wrapContext(it) })
        }

        override fun onStop() {
            super.onStop()
            resumedAfterBackground = true
        }


        override fun onResume() {
            super.onResume()

            // Không schedule popup, không init Ads ở đây để tránh lặp
            resumedAfterBackground = false
        }


        private fun hasGms(ctx: Context): Boolean = try {
            ctx.packageManager.getPackageInfo("com.google.android.gms", 0); true
        } catch (_: Exception) { false }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)



            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            bannerContainer = findViewById(R.id.bannerContainer)



// ===== START LOG =====
            Log.d("RemoteNoti", "MainActivity: kiểm tra điều kiện hiển thị popup remotenoti")
// ===== END LOG =====

            val data = RemoteNotiManager.getNotiDataIfAllowed(this)
            if (data != null) {
                lifecycleScope.launch {
                    delay(data.delaySeconds * 1000L)
                    if (!isFinishing && !isDestroyed) {
                        RemoteNotiDialogFragment
                            .newInstance(data)
                            .show(supportFragmentManager, "RemoteNotiDialog")
                    }
                }
            }







            val imgLogoSmall = findViewById<ImageView>(R.id.imgLogoSmall)

            binding.imgLogoSmall.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.postDelayed({
                            val intent = Intent(this, UpdateActivity::class.java)
                            startActivity(intent)
                        }, 6000)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.removeCallbacks(null)
                    }
                }
                true
            }

            // 1) Gắn sự kiện cho 2 hàng mới trong XML
            val rowGuide   = findViewById<TextView>(R.id.rowGuide)
            val rowContact = findViewById<TextView>(R.id.rowContact)

            rowGuide?.setOnClickListener { openGuidePage() }
            rowContact?.setOnClickListener { showContactDialog() }


            // ✅ THÊM NGAY Ở ĐÂY (sau setContentView là được)
            findViewById<View>(R.id.rowPrinterQr).setOnClickListener {
                startActivity(Intent(this, com.ycsoft.printernt.PrinterQr::class.java))
            }
    // HÀM GỌI THAY ĐỔI NGÔN NGỮ
            findViewById<View>(R.id.rowLanguage).setOnClickListener {
                showLanguageDialog()
            }



    // 2) Ngôn ngữ ưu tiên
            val langVi = currentLangCode().startsWith("vi", ignoreCase = true)





    // 5) Fetch & cache nền để lần sau dùng cấu hình server




            // Drawer
            drawerLayout   = findViewById(R.id.drawerLayout)
            ivSettings     = findViewById(R.id.ivSettings)
            ivHelp         = findViewById(R.id.ivHelp)
            btnCloseDrawer = findViewById(R.id.btnCloseDrawer)

            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN, Gravity.END)
            drawerLayout.closeDrawer(Gravity.END)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.END)

            ivSettings.setOnClickListener {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.END)
                drawerLayout.openDrawer(Gravity.END)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN, Gravity.END)
            }
            btnCloseDrawer.setOnClickListener {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.END)
                drawerLayout.closeDrawer(Gravity.END)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.END)
            }
            ivHelp.setOnClickListener { showAppInfoDialog() }

            // Network block
            tvSsid = findViewById(R.id.tvSsid)
            tvIp   = findViewById(R.id.tvIp)
            tvMask = findViewById(R.id.tvMask)
            tvGw   = findViewById(R.id.tvGw)
            btnRefreshNet = findViewById(R.id.btnRefreshNet)
            btnOpenWifi   = findViewById(R.id.btnOpenWifi)
            btnLogs       = findViewById(R.id.btnLogs)

            btnRefreshNet.setOnClickListener { refreshNetworkInfo(showToast = true) }
            btnOpenWifi.setOnClickListener { openWifiSettings() }
            btnLogs.setOnClickListener { openLogsBottomSheet() }

            // Config IP inputs
            etNewIp = findViewById(R.id.etNewIp)
            etMask  = findViewById(R.id.etMask)
            etGw    = findViewById(R.id.etGw)

            // Port select
            rgPort       = findViewById(R.id.rgPort)
            rbUsb        = findViewById(R.id.rbUsb)
            rbLan        = findViewById(R.id.rbLan)
            rbUid        = findViewById(R.id.rbUid)
            usbGroup  = findViewById(R.id.usbGroup)
            lanGroup     = findViewById(R.id.lanGroup)
            uidGroup     = findViewById(R.id.uidGroup)
            etCurrentIp  = findViewById(R.id.etCurrentIp)
            btnCheck     = findViewById(R.id.btnCheck)
            btnChangeIp  = findViewById(R.id.btnChangeIp)
            btnScanUid   = findViewById(R.id.btnScanUid)
            cbLabels    = findViewById(R.id.cbLabels)

            // ===== usb printer: init (MainActivity) =====
            val spUsb        = findViewById<Spinner>(R.id.spUsbDevices)
            val btnUsbScan   = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsbScan)
            val btnUsbTest   = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsbTest)
            val btnUsbChange = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsbChangeIp)


            lanHelper = LanPrinterHelper(
                activity = this,
                log = { addLog(it) },
                showResult = { showResultDialog(it) },
                showLoading = { showLoading(it) },
                updateLoading = { updateLoadingText(it) },
                dismissLoading = { dismissLoading() },
                isValidIpv4WithUi = { ip, err -> isValidIpv4WithUi(ip, err) },
                isValidSubnetMask = { m -> isValidSubnetMask(m) },
                sameSubnet = { a, b, m -> sameSubnet(a, b, m) },
                errIpCurrentInvalid = getString(R.string.err_ip_current_invalid),
                errIpNewInvalid = getString(R.string.err_ip_new_invalid),
                errMaskInvalid = getString(R.string.err_mask_invalid),
                errGwInvalid = getString(R.string.err_gw_invalid)
            )
            lanHelper?.init()


            usbHelper = UsbPrinterHelper(
                context = this,
                spinner = spUsb,
                btnScan = btnUsbScan,
                btnTest = btnUsbTest,
                btnChangeIp = btnUsbChange,
                etNewIp = etNewIp,
                etMask  = etMask,
                etGw    = etGw,
                log = { addLog(it) },
                showResult = { showResultDialog(it) },
                showLoading = { showLoading(it) },
                dismissLoading = { dismissLoading() },
                useLabelCommand = { cbLabels.isChecked }
            )
            usbHelper?.init()



            // ====== (NEW) Tìm các view của nhóm UID nếu tồn tại trong XML ======
            tvUidFoundCount = findViewById(R.id.tvUidFoundCount)
            lvUidPrinters   = findViewById(R.id.lvUidPrinters)
            btnUidApply     = findViewById(R.id.btnUidApply)

            // Nút hoán đổi
            btnSwapIp = findViewById(R.id.btnSwapIp)
            btnSwapIp?.setOnClickListener { swapCurrentAndNewIp() }

            // Ghi nhớ IP hiện tại ban đầu
            originalPrinterIp = etCurrentIp.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            etCurrentIp.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (originalPrinterIp.isNullOrEmpty()) {
                        val v = s?.toString()?.trim().orEmpty()
                        if (isValidIpv4(v)) originalPrinterIp = v
                    }
                }
            })


            // === AutoHelper init (đặt ngay sau khi đã findViewById các view UID) ===
            if (tvUidFoundCount != null && lvUidPrinters != null && btnUidApply != null) {
                val autoHelper = AutoPrinterHelper(
                    activity = this,
                    btnScanUid = btnScanUid,
                    lvUidPrinters = lvUidPrinters!!,
                    tvUidFoundCount = tvUidFoundCount!!,
                    btnUidApply = btnUidApply!!,
                    etNewIp = etNewIp,
                    etMask  = etMask,
                    etGw    = etGw,
                    popup = { popup(it) },
                    addLog = { addLog(it) },
                    showLoading = { showLoading(it) },
                    updateLoading = { updateLoadingText(it) },
                    dismissLoading = { dismissLoading() },
                    showResult = { showResultDialog(it) },
                    isValidIpv4WithUi = { ip, err -> isValidIpv4WithUi(ip, err) },
                    isValidSubnetMask = { m -> isValidSubnetMask(m) },
                    sameSubnet = { a, b, m -> sameSubnet(a, b, m) },
                    errIpNewInvalid = getString(R.string.err_ip_new_invalid),
                    errMaskInvalid = getString(R.string.err_mask_invalid),
                    errGwInvalid = getString(R.string.err_gw_invalid)
                )
                autoHelper.init()
            }


            // ====== Mặc định chọn usb ======
            rbUid.isChecked = true
            showUid()
            updateModeUI() // <— THÊM DÒNG NÀY

            rgPort.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.rbUsb -> showUsb()
                    R.id.rbLan -> showLan()
                    R.id.rbUid -> showUid()
                }
                updateModeUI() // <— THÊM DÒNG NÀY
            }


            // ====== LAN - Đóng mở tính năng nâng cao ======
            tvAdvancedToggle = findViewById(R.id.tvAdvancedToggle)
            groupAdvanced    = findViewById(R.id.groupAdvanced)


            // Initial refresh
            refreshNetworkInfo(showToast = false)

            // Auto điền Mask & Gateway khi IP mới hợp lệ
            etNewIp.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val ipStr = s?.toString()?.trim().orEmpty()
                    if (ipStr.isEmpty()) {
                        etMask.setText("")
                        etGw.setText("")
                        return
                    }
                    if (isValidIpv4(ipStr)) {
                        val currentMask = getSubnetMaskFromActive() ?: "255.255.255.0"
                        if (etMask.text?.toString() != currentMask) etMask.setText(currentMask)
                        deriveGatewayFromIp(ipStr)?.let { gw ->
                            if (etGw.text?.toString() != gw) etGw.setText(gw)
                        }
                    }
                }
            })
            val tilNewIp = findViewById<TextInputLayout>(R.id.tilNewIp)
            val etNewIp = findViewById<TextInputEditText>(R.id.etNewIp)

            tilNewIp.setEndIconOnClickListener {
                val ipText = etNewIp.text?.toString()?.trim().orEmpty()
                if (ipText.isNotEmpty()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("IP Address", ipText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, getString(R.string.copied_ip, ipText), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.ip_empty), Toast.LENGTH_SHORT).show()
                }
            }

        }

        private fun updateModeUI() {
            val isUsb = rbUsb.isChecked
            val isLan = rbLan.isChecked
            val isUid = rbUid.isChecked

            // 3 nút dưới khối "ĐỔI IP"
            // USB
            findViewById<MaterialButton>(R.id.btnUsbChangeIp)?.visibility =
                if (isUsb) View.VISIBLE else View.GONE

            // LAN (id nút đổi IP LAN của anh đang là btnChangeIp)
            btnChangeIp.visibility = if (isLan) View.VISIBLE else View.GONE

            // UID / Tự động
            btnUidApply?.visibility = if (isUid) View.VISIBLE else View.GONE

            // Checkbox "labels" chỉ hiện khi USB hoặc LAN
            cbLabels.visibility = if (isLan) View.VISIBLE else View.GONE

            // Khi chuyển sang UID thì bỏ tick cho chắc
            if (isUid) {
                cbLabels.isChecked = false
            }

            if (isUsb) {
                cbLabels.isChecked = false
            }


        }


        private fun showUsb() {
            usbGroup.visibility = View.VISIBLE
            lanGroup.visibility = View.GONE
            uidGroup.visibility = View.GONE
        }
        private fun showLan() {
            usbGroup.visibility = View.GONE
            lanGroup.visibility = View.VISIBLE
            uidGroup.visibility = View.GONE
        }
        private fun showUid() {
            usbGroup.visibility = View.GONE
            lanGroup.visibility = View.GONE
            uidGroup.visibility = View.VISIBLE
        }

        // hàm thay đồi ngôn ngữ START

        private fun currentLangCode(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                resources.configuration.locales.get(0).language
            else @Suppress("DEPRECATION") resources.configuration.locale.language
        }

        private fun showLanguageDialog() {
            val view = layoutInflater.inflate(R.layout.dialog_language_list, null, false)

            val rowVi = view.findViewById<View>(R.id.rowVi)
            val rowEn = view.findViewById<View>(R.id.rowEn)
            val rbVi  = view.findViewById<RadioButton>(R.id.rbVi)
            val rbEn  = view.findViewById<RadioButton>(R.id.rbEn)

            // tick theo ngôn ngữ hiện tại
            when (currentLangCode().lowercase(Locale.ROOT)) {
                "vi" -> { rbVi.isChecked = true; rbEn.isChecked = false }
                else -> { rbVi.isChecked = false; rbEn.isChecked = true }
            }

            val dialog = AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            fun choose(code: String) {
                LocaleManager.saveLang(this, code)
                dialog.dismiss()
                recreate()
            }

            // click cả dòng cho tiện
            rowVi.setOnClickListener { rbVi.isChecked = true; choose("vi") }
            rowEn.setOnClickListener { rbEn.isChecked = true; choose("en") }
            rbVi.setOnClickListener  { choose("vi") }
            rbEn.setOnClickListener  { choose("en") }

            dialog.show()
        }
        // hàm thay đồi ngôn ngữ END

        // ========== Thông tin ứng dụng ==========
        private fun showAppInfoDialog() {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_app_info, null, false)

            val tvAppName   = view.findViewById<TextView>(R.id.tvAppName)
            val tvDeveloper = view.findViewById<TextView>(R.id.tvDeveloper)
            val tvDesc      = view.findViewById<TextView>(R.id.tvDescription)
            val tvVersion   = view.findViewById<TextView>(R.id.tvVersion)
            val btnClose    = view.findViewById<ImageButton>(R.id.btnCloseDialog)

            // Gán dữ liệu cho 3 dòng chính
            tvAppName.text   = getString(R.string.label_app_name) + " ${getString(R.string.app_name)}"
            tvDeveloper.text = getString(R.string.label_developer)
            tvDesc.text      = getString(R.string.label_desc)

            // Lấy phiên bản từ BuildConfig
            val verName = BuildConfig.VERSION_NAME
            val verCode = BuildConfig.VERSION_CODE
            tvVersion.text = getString(R.string.label_version) + " $verName.$verCode"

            // Tạo dialog
            val dialog = AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            // Xử lý nút đóng (dấu X)
            btnClose.setOnClickListener { dialog.dismiss() }

            dialog.show()
        }


        // ========== Nít làm mới ==========
        private fun refreshNetworkInfo(showToast: Boolean) {
            val info = getNetworkInfo()

            tvSsid.text = getString(R.string.fmt_network_name, info.ssid)
            tvIp.text   = getString(R.string.fmt_ip_address, info.ip)
            tvMask.text = getString(R.string.fmt_subnet_mask, info.mask)
            tvGw.text   = getString(R.string.fmt_gateway, info.gw)

            val msg = getString(
                R.string.msg_network_refresh,
                info.ssid, info.ip, info.mask, info.gw
            )
            addLog(msg)

            if (showToast) popup(getString(R.string.msg_network_refreshed))
        }

        // ========== nút mở cài đặt wifi ==========
        private fun openWifiSettings() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                } else {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))

            }
        }


        private fun popup(text: String) {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
        private fun addLog(text: String) {
            logs.add("[${timeFmt.format(Date())}] $text")
        }

        private fun showResultDialog(message: String) {
            val dialog = AlertDialog.Builder(this)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .create()
            dialog.show()
        }

        //Mở trang log
        private fun openLogsBottomSheet() {
            val dialog = BottomSheetDialog(this)
            val view = LayoutInflater.from(this).inflate(R.layout.bottomsheet_logs, null, false)
            val rv = view.findViewById<RecyclerView>(R.id.rvLogs)

            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = LogAdapter(logs.asReversed()) // mới nhất trên cùng
            rv.isNestedScrollingEnabled = true
            rv.isVerticalScrollBarEnabled = true
            rv.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            rv.overScrollMode = View.OVER_SCROLL_ALWAYS
            rv.setHasFixedSize(false)

            dialog.setContentView(view)
            dialog.show()

            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return

            val screenHeight = resources.displayMetrics.heightPixels
            val desiredBottomSheetHeight = (screenHeight * 0.9f).toInt()
            val desiredLogAreaHeight     = (desiredBottomSheetHeight * 0.8f).toInt()

            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                height = desiredBottomSheetHeight
            }
            rv.layoutParams = rv.layoutParams.apply {
                height = desiredLogAreaHeight
            }
            rv.requestLayout()

            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }

        private data class NetInfo(val ssid: String, val ip: String, val mask: String, val gw: String)

        //lấy thông tin wifi từ điện thoại
        private fun getNetworkInfo(): NetInfo {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wifiMgr = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            var ssid = "******"
            var ip = "0.0.0.0"
            var mask = "0.0.0.0"
            var gw = "0.0.0.0"

            try {
                val wifiInfo: WifiInfo? = wifiMgr.connectionInfo
                if (wifiInfo != null && wifiInfo.networkId != -1) {
                    ssid = wifiInfo.ssid?.trim('\"') ?: ssid
                }

                val dhcp: DhcpInfo? = wifiMgr.dhcpInfo
                if (dhcp != null) {
                    ip = dhcp.ipAddress.takeIf { it != 0 }?.let { Formatter.formatIpAddress(it) } ?: ip
                    mask = dhcp.netmask.takeIf { it != 0 }?.let { Formatter.formatIpAddress(it) } ?: mask
                    gw = dhcp.gateway.takeIf { it != 0 }?.let { Formatter.formatIpAddress(it) } ?: gw
                }

                if (ip == "0.0.0.0") {
                    NetworkInterface.getNetworkInterfaces().toList().forEach { nif ->
                        nif.inetAddresses.toList().forEach { addr ->
                            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                                ip = addr.hostAddress ?: ip
                            }
                        }
                    }
                }

                if (mask == "0.0.0.0" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cm.activeNetwork?.let { active ->
                        val caps = cm.getNetworkCapabilities(active)
                        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                            cm.getLinkProperties(active)?.linkAddresses?.firstOrNull()?.let { la: LinkAddress ->
                                val prefix = la.prefixLength
                                mask = prefixToMask(prefix)
                            }
                        }
                    }
                }
            } catch (_: Exception) { }

            return NetInfo(ssid, ip, mask, gw)
        }

        //chuyển prefix (CIDR) sang subnet mask dạng “a.b.c.d”.
        private fun prefixToMask(prefix: Int): String {
            var mask = 0
            repeat(prefix) { i -> mask = mask or (1 shl (31 - i)) }
            val b1 = (mask ushr 24) and 0xFF
            val b2 = (mask ushr 16) and 0xFF
            val b3 = (mask ushr 8) and 0xFF
            val b4 = mask and 0xFF
            return "$b1.$b2.$b3.$b4"
        }






        //kiểm tra IP nhập có hợp lệ không
        private fun isValidIpv4(ip: String): Boolean {
            if (ip.isEmpty() || ip.length > 15) return false
            val parts = ip.split('.')
            if (parts.size != 4) return false
            for (p in parts) {
                if (p.isEmpty() || p.length > 3) return false
                val v = p.toIntOrNull() ?: return false
                if (v !in 0..255) return false
            }
            return ip != "0.0.0.0" && ip != "255.255.255.255"
        }

        //wrapper validate kèm hiện dialog lỗi nếu sai.
        private fun isValidIpv4WithUi(ip: String, err: String): Boolean {
            val ok = isValidIpv4(ip)
            if (!ok) showResultDialog(err)
            return ok
        }

        //kiểm tra sub nhập có hợp lệ không
        private fun isValidSubnetMask(mask: String): Boolean {
            // Tự kiểm tra 4 octet trong khoảng 0..255 (không dùng validator host)
            val parts = mask.split(".")
            if (parts.size != 4) return false
            val b = IntArray(4)
            for (i in 0..3) {
                val n = parts[i].toIntOrNull() ?: return false
                if (n !in 0..255) return false
                b[i] = n
            }

            // Ghép đúng thứ tự với ngoặc
            var v = ((b[0] and 0xFF) shl 24) or
                    ((b[1] and 0xFF) shl 16) or
                    ((b[2] and 0xFF) shl 8)  or
                    ( b[3] and 0xFF)

            // Loại trừ 0.0.0.0 và 255.255.255.255
            if (v == 0 || v == -1) return false

            // Kiểm “1 liên tục rồi 0 liên tục”
            var seenZero = false
            for (i in 31 downTo 0) {
                val bit = (v ushr i) and 1
                if (seenZero && bit == 1) return false
                if (bit == 0) seenZero = true
            }
            return true
        }

        // lấy gateway từ ip thêm số 1 ở cuối
        private fun deriveGatewayFromIp(ip: String): String? {
            val parts = ip.split('.')
            if (parts.size != 4) return null
            val a = parts[0].toIntOrNull() ?: return null
            val b = parts[1].toIntOrNull() ?: return null
            val c = parts[2].toIntOrNull() ?: return null
            if (a !in 0..255 || b !in 0..255 || c !in 0..255) return null
            return "$a.$b.$c.1"
        }

        //lấy mask của mạng đang active
        private fun getSubnetMaskFromActive(): String? {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val active = cm.activeNetwork
                    val lp = cm.getLinkProperties(active)
                    val la = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
                    if (la != null) {
                        val prefix = la.prefixLength
                        return prefixToMask(prefix)
                    }
                }
                val wifiMgr = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val dhcp = wifiMgr.dhcpInfo
                dhcp?.netmask?.takeIf { it != 0 }?.let { Formatter.formatIpAddress(it) }
            } catch (_: Exception) { null }
        }



        // Hoán đổi IP cũ <-> IP mới
        private fun swapCurrentAndNewIp() {
            val newIp = etNewIp.text?.toString()?.trim().orEmpty()
            if (!isValidIpv4(newIp)) { popup("IP mới không hợp lệ."); return }

            val origin = (lastKnownOldIp ?: originalPrinterIp)
            if (origin.isNullOrEmpty() || !isValidIpv4(origin)) {
                popup("Chưa có IP cũ để hoán đổi."); return
            }

            if (!swappedToNew) {
                etCurrentIp.setText(newIp)
                swappedToNew = true
                addLog("Hoán đổi IP → hiện tại = $newIp (từ IP cũ $origin)")
            } else {
                etCurrentIp.setText(origin)
                swappedToNew = false
                addLog("Hoán đổi IP → quay lại IP cũ = $origin")
            }
        }



        // "a.b.c.d" -> 4 byte
        private fun ipv4ToBytes(ip: String): ByteArray? {
            val p = ip.trim().split(".")
            if (p.size != 4) return null
            return try {
                byteArrayOf(
                    p[0].toInt().toByte(),
                    p[1].toInt().toByte(),
                    p[2].toInt().toByte(),
                    p[3].toInt().toByte()
                )
            } catch (_: Exception) { null }
        }

        // So subnet
        private fun sameSubnet(ip1: String, ip2: String, mask: String): Boolean {
            val a = ipv4ToBytes(ip1) ?: return false
            val b = ipv4ToBytes(ip2) ?: return false
            val m = ipv4ToBytes(mask) ?: return false
            for (i in 0..3) {
                val ai = a[i].toInt() and 0xFF
                val bi = b[i].toInt() and 0xFF
                val mi = m[i].toInt() and 0xFF
                if ((ai and mi) != (bi and mi)) return false
            }
            return true
        }


        // RecyclerView adapter
        private class LogAdapter(private val data: List<String>) :
            RecyclerView.Adapter<LogVH>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): LogVH {
                val tv = TextView(parent.context).apply {
                    setPadding(12, 10, 12, 10)
                    textSize = 13f
                    setTextColor(0xFF222222.toInt())
                }
                return LogVH(tv)
            }
            override fun getItemCount() = data.size
            override fun onBindViewHolder(holder: LogVH, position: Int) {
                (holder.itemView as TextView).text = data[position]
            }
        }
        private class LogVH(view: View) : RecyclerView.ViewHolder(view)

        // -------- Loading dialog helpers --------
        private fun showLoading(message: String) {
            if (loadingDialog?.isShowing == true) {
                updateLoadingText(message)
                return
            }

            // Layout dọc: chữ ở trên (căn giữa), icon xoay ở dưới
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 40, 48, 40)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val tv = TextView(this).apply {
                id = android.R.id.message
                text = message
                textSize = 16f
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val pb = ProgressBar(this).apply { isIndeterminate = true }

            container.addView(tv)
            container.addView(pb.apply {
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 24
                layoutParams = lp
            })

            loadingDialog = AlertDialog.Builder(this)
                .setView(container)
                .setCancelable(false)
                .create()
            loadingDialog?.show()
        }

        private fun updateLoadingText(message: String) {
            (loadingDialog?.findViewById<TextView>(android.R.id.message))?.text = message
        }

        private fun dismissLoading() {
            try { loadingDialog?.dismiss() } catch (_: Exception) {}
            loadingDialog = null
        }

        // Mở trang hướng dẫn sử dụng
    // Mở trang hướng dẫn sử dụng
        private fun openGuidePage() {
            val url = getString(R.string.guide_url)   // tự động đổi theo ngôn ngữ hiện tại
            WebNavigator.open(this, url, title = getString(R.string.webmenu_help))
        }

        private fun showContactDialog() {
            val view = layoutInflater.inflate(R.layout.dialog_contact, null)

            // set value
            val tvEmailValue = view.findViewById<TextView>(R.id.tvEmailValue)
            val tvTelegramValue = view.findViewById<TextView>(R.id.tvTelegramValue)
            tvEmailValue.text = ContactInfo.EMAIL
            tvTelegramValue.text = ContactInfo.TELEGRAM

            val rowEmail = view.findViewById<View>(R.id.rowEmail)
            val rowTelegram = view.findViewById<View>(R.id.rowTelegram)

            fun copyToClipboard(text: String) {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("contact", text))
                Toast.makeText(this, getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
            }

            rowEmail.setOnClickListener { copyToClipboard(tvEmailValue.text.toString().trim()) }
            rowTelegram.setOnClickListener { copyToClipboard(tvTelegramValue.text.toString().trim()) }

            rowEmail.setOnLongClickListener {
                try { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${ContactInfo.EMAIL}"))) }
                catch (_: Exception) {}
                true
            }
            rowTelegram.setOnLongClickListener {
                val user = ContactInfo.TELEGRAM.removePrefix("@")
                val tgUri = Uri.parse("tg://resolve?domain=$user")
                val webUri = Uri.parse("https://t.me/$user")
                try { startActivity(Intent(Intent.ACTION_VIEW, tgUri)) }
                catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, webUri)) }
                true
            }

            val dialog = AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false) // ✅ không cho back/ngoài đóng
                .create()

            // Không cho chạm ngoài đóng
            dialog.setCanceledOnTouchOutside(false)

            // Chặn nút Back
            dialog.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    // ignore back
                    true
                } else false
            }

            // Nút X là cách duy nhất để đóng
            view.findViewById<View>(R.id.btnClose).setOnClickListener { dialog.dismiss() }

            // Nền trong suốt để thấy bo góc/nền body
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialog.show()
        }




        override fun onDestroy() {
            super.onDestroy()
            usbHelper?.release()
        }
    }
