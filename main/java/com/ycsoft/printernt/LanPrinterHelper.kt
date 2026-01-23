package com.ycsoft.printernt

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ycsoft.printernt.commands.PrinterCommands
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import android.widget.TextView
import com.ycsoft.printernt.R
import android.widget.CheckBox


class LanPrinterHelper(
    private val activity: AppCompatActivity,

    // Callbacks UI từ MainActivity
    private val log: (String) -> Unit,
    private val showResult: (String) -> Unit,
    private val showLoading: (String) -> Unit,
    private val updateLoading: (String) -> Unit,
    private val dismissLoading: () -> Unit,

    // Validator tái dùng từ MainActivity
    private val isValidIpv4WithUi: (String, String) -> Boolean,
    private val isValidSubnetMask: (String) -> Boolean,
    private val sameSubnet: (String, String, String) -> Boolean,

    // Chuỗi lỗi (i18n)
    private val errIpCurrentInvalid: String,
    private val errIpNewInvalid: String,
    private val errMaskInvalid: String,
    private val errGwInvalid: String
) {
    // Trạng thái
    private var isChecking = false
    private var isChanging = false

    // View (lấy 1 lần)
    private lateinit var etCurrentIp: TextInputEditText
    private lateinit var etNewIp: TextInputEditText
    private lateinit var etMask: TextInputEditText
    private lateinit var etGw: TextInputEditText
    private lateinit var btnCheck: MaterialButton
    private lateinit var btnChangeIp: MaterialButton

    private var tvAdvancedToggle: TextView? = null
    private var groupAdvanced: View? = null

    private var btnAdvEscposTest: View? = null
    private var btnAdvTestPage: View? = null
    private var btnAdvCashDrawer: View? = null

    private var btnAdvBeepSet: View? = null
    private var spBeepEnable: Spinner? = null
    private var spBeepCounter: Spinner? = null
    private var spBeepTime: Spinner? = null
    private var spBeepMode: Spinner? = null

    private var spDhcp: Spinner? = null
    private var btnAdvDhcpSet: View? = null
    private var btnAdvRestore: View? = null
    private var cbLabels: CheckBox? = null

    fun init() {
        // Lấy view
        etCurrentIp  = activity.findViewById(R.id.etCurrentIp)
        etNewIp      = activity.findViewById(R.id.etNewIp)
        etMask       = activity.findViewById(R.id.etMask)
        etGw         = activity.findViewById(R.id.etGw)
        btnCheck     = activity.findViewById(R.id.btnCheck)
        btnChangeIp  = activity.findViewById(R.id.btnChangeIp)
        cbLabels     = activity.findViewById(R.id.cbLabels)

        tvAdvancedToggle = activity.findViewById(R.id.tvAdvancedToggle)
        groupAdvanced    = activity.findViewById(R.id.groupAdvanced)

        btnAdvEscposTest = activity.findViewById(R.id.btnAdvEscposTest)
        btnAdvTestPage   = activity.findViewById(R.id.btnAdvTestPage)
        btnAdvCashDrawer = activity.findViewById(R.id.btnAdvCashDrawer)

        btnAdvBeepSet = activity.findViewById(R.id.btnAdvBeepSet)
        spBeepEnable  = activity.findViewById(R.id.spBeepEnable)
        spBeepCounter = activity.findViewById(R.id.spBeepCounter)
        spBeepTime    = activity.findViewById(R.id.spBeepTime)
        spBeepMode    = activity.findViewById(R.id.spBeepMode)

        spDhcp        = activity.findViewById(R.id.spDhcp)
        btnAdvDhcpSet = activity.findViewById(R.id.btnAdvDhcpSet)
        btnAdvRestore = activity.findViewById(R.id.btnAdvRestore)

        // Gắn listener
        btnCheck.setOnClickListener { onCheckClicked() }
        btnChangeIp.setOnClickListener { onChangeIpClicked() }

        // Toggle nâng cao (có thể để ở Main; nếu để ở đây thì xóa ở Main)
        tvAdvancedToggle?.setOnClickListener {
            val ga = groupAdvanced ?: return@setOnClickListener
            val opening = ga.visibility != View.VISIBLE
            ga.visibility = if (opening) View.VISIBLE else View.GONE

            val label = activity.getString(R.string.advanced)
            tvAdvancedToggle?.text = if (opening) "$label ▾" else "$label ▸"
        }


        // Nút cắt
        btnAdvEscposTest?.setOnClickListener {
            val ip = etCurrentIp.text?.toString()?.trim().orEmpty()
            if (!isValidIpv4WithUi(ip, errIpCurrentInvalid)) return@setOnClickListener
            activity.lifecycleScope.launch {

                showLoading(activity.getString(R.string.msg_sending))
                val ok = withContext(Dispatchers.IO) { sendRaw9100(ip, PrinterCommands.escposCut()) }
                dismissLoading()
                showResult(if (ok) activity.getString(R.string.sent_cut_test) else activity.getString(R.string.cmd_fail))
            }
        }

        // Nút test page
        btnAdvTestPage?.setOnClickListener {
            val ip = etCurrentIp.text?.toString()?.trim().orEmpty()
            if (!isValidIpv4WithUi(ip, errIpCurrentInvalid)) return@setOnClickListener
            activity.lifecycleScope.launch {
                //log("LAN TestPage → $ip:9100")
                showLoading(activity.getString(R.string.msg_sending))
                val ok = withContext(Dispatchers.IO) { sendRaw9100(ip, PrinterCommands.testPage()) }
                dismissLoading()
                showResult(if (ok) activity.getString(R.string.sent_test_page) else activity.getString(R.string.cmd_fail))
            }
        }

        // nút két
        btnAdvCashDrawer?.setOnClickListener {
            val ip = etCurrentIp.text?.toString()?.trim().orEmpty()
            if (!isValidIpv4WithUi(ip, errIpCurrentInvalid)) return@setOnClickListener
            activity.lifecycleScope.launch {
                //log("LAN CashDrawer → $ip:9100")
                showLoading(activity.getString(R.string.msg_sending))
                val ok = withContext(Dispatchers.IO) { sendRaw9100(ip, PrinterCommands.cashDrawer()) }
                dismissLoading()
                showResult(if (ok) activity.getString(R.string.sent_cashdrawer) else activity.getString(R.string.cmd_fail))
            }
        }

        // Nút beep
        fun <T> Spinner?.applySimple(items: List<T>) {
            this ?: return
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, items)
        }
        spBeepEnable.applySimple(listOf("ON", "OFF"))
        spBeepCounter.applySimple((1..20).toList())
        spBeepTime.applySimple((1..20).toList())
        spBeepMode.applySimple(listOf(0,1,2,3,4))
        spBeepEnable?.setSelection(0)
        spBeepCounter?.setSelection(3)
        spBeepTime?.setSelection(1)
        spBeepMode?.setSelection(3)

        btnAdvBeepSet?.setOnClickListener {
            val ip = etCurrentIp.text?.toString()?.trim().orEmpty()
            if (!isValidIpv4WithUi(ip, errIpCurrentInvalid)) return@setOnClickListener
            val enable = (spBeepEnable?.selectedItem?.toString() ?: "ON").equals("ON", true)
            val cnt = ((spBeepCounter?.selectedItem as? Int) ?: 4).coerceIn(1, 20)
            val t50 = ((spBeepTime?.selectedItem as? Int) ?: 2).coerceIn(1, 20)
            val md  = ((spBeepMode?.selectedItem as? Int) ?: 3).coerceIn(0, 4)
            activity.lifecycleScope.launch {
                log(activity.getString(R.string.sent_beep_set)+" enable=$enable, count=$cnt, time=${t50}×50ms, mode=$md")
                showLoading(activity.getString(R.string.msg_sending))
                val ok = withContext(Dispatchers.IO) { sendRaw9100(ip, PrinterCommands.beepSet(enable, cnt, t50, md)) }
                dismissLoading()
                showResult(if (ok) activity.getString(R.string.sent_beep_set)+" enable=$enable, count=$cnt, time=${t50}×50ms, mode=$md" else activity.getString(R.string.cmd_fail))
            }
        }

        // Nút DHCP
        spDhcp?.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, listOf("ON", "OFF"))
        spDhcp?.setSelection(1)
        btnAdvDhcpSet?.setOnClickListener {
            val ip = etCurrentIp.text?.toString()?.trim().orEmpty()
            if (!isValidIpv4WithUi(ip, errIpCurrentInvalid)) return@setOnClickListener
            val turningOn = (spDhcp?.selectedItem?.toString()?.uppercase(Locale.ROOT) ?: "OFF") == "ON"
            activity.lifecycleScope.launch {
                //log("LAN DHCP → ${if (turningOn) "ON" else "OFF"}")
                showLoading(activity.getString(R.string.msg_sending))
                val ok = withContext(Dispatchers.IO) {
                    val payload = if (turningOn) PrinterCommands.dhcpOn() else PrinterCommands.dhcpOff()
                    sendRaw9100(ip, payload)
                }
                dismissLoading()
                showResult(if (ok) activity.getString(R.string.sent_dhcp_set) else activity.getString(R.string.cmd_fail))
            }
        }

        // Nút reset
        btnAdvRestore?.setOnClickListener {
            val ip = etCurrentIp.text?.toString()?.trim().orEmpty()
            if (!isValidIpv4WithUi(ip, errIpCurrentInvalid)) return@setOnClickListener
            activity.lifecycleScope.launch {
                //log("LAN Restore factory → $ip")
                showLoading(activity.getString(R.string.msg_sending))
                val ok = withContext(Dispatchers.IO) { sendRaw9100(ip, PrinterCommands.restoreFactory()) }
                dismissLoading()
                showResult(if (ok) activity.getString(R.string.sent_restore_factory) else activity.getString(R.string.cmd_fail))
            }
        }
    }

    // Nút kiểm tra (kiểm tra xem ip đó có mở cổng hay có sống ko)
    private fun onCheckClicked() {
        if (isChecking) return
        val ip = etCurrentIp.text?.toString()?.trim().orEmpty()
        if (!isValidIpv4WithUi(ip, errIpCurrentInvalid)) return

        isChecking = true
        btnCheck.isEnabled = false
        showLoading(activity.getString(R.string.lan_checking_ip)+" $ip")

        activity.lifecycleScope.launch {
            val tcpOk = withContext(Dispatchers.IO) { tcpAlive9100(ip, 1500) }
            if (tcpOk) {
                updateLoading("IP $ip:")
                dismissLoading()
                btnCheck.isEnabled = true
                isChecking = false
                showResult(activity.getString(R.string.lan_found_ip) + " $ip.")
                return@launch
            }
            updateLoading("IP $ip "+activity.getString(R.string.lan_retrying))
            val pingOk = withContext(Dispatchers.IO) { ping(ip, 1500) }
            dismissLoading()
            btnCheck.isEnabled = true
            isChecking = false
            showResult(
                if (pingOk) "IP $ip "+ activity.getString(R.string.lan_not_printer)
                else activity.getString(R.string.lan_not_found)+" $ip."
            )
        }
    }

    // Nút đổi ip
    // Nút đổi ip
    private fun onChangeIpClicked() {
        if (isChanging) return

        val currentIp = etCurrentIp.text?.toString()?.trim().orEmpty()
        val newIpStr  = etNewIp.text?.toString()?.trim().orEmpty()
        val maskStr   = etMask.text?.toString()?.trim().orEmpty()
        val gwStr     = etGw.text?.toString()?.trim().orEmpty()

        if (!isValidIpv4WithUi(currentIp, activity.getString(R.string.err_ip_current_invalid))) return
        if (!isValidIpv4WithUi(newIpStr,  activity.getString(R.string.err_ip_new_invalid))) return
        if (!isValidIpv4WithUi(maskStr,   activity.getString(R.string.err_mask_invalid))) return
        if (!isValidIpv4WithUi(gwStr,     activity.getString(R.string.err_gw_invalid))) return
        if (!isValidSubnetMask(maskStr)) {
            showResult(activity.getString(R.string.err_mask_invalid))
            return
        }
        if (!sameSubnet(newIpStr, gwStr, maskStr)) {
            showResult(activity.getString(R.string.err_gw_not_same_subnet))
            return
        }

        isChanging = true
        btnChangeIp.isEnabled = false
        showLoading(activity.getString(R.string.lan_checking_ip)+ " $newIpStr")

        activity.lifecycleScope.launch {
            val aliveNew9100 = withContext(Dispatchers.IO) { tcpAlive9100(newIpStr, 1200) }
            val aliveNewPing = if (!aliveNew9100) withContext(Dispatchers.IO) { ping(newIpStr, 1200) } else true
            val aliveNew = aliveNew9100 || aliveNewPing
            if (aliveNew) {
                dismissLoading()
                showResult("IP $newIpStr " + activity.getString(R.string.lan_ip_in_use))
                btnChangeIp.isEnabled = true
                isChanging = false
                return@launch
            }

            // Quyết định lệnh & cổng dựa vào checkbox "labels"
            val use22 = (cbLabels?.isChecked == true)
            val port = if (use22) 4000 else 9100
            val payload = if (use22) {
                // Lệnh mới: 1F 1B 1F 22 [IP] — gửi qua cổng 4000
                PrinterCommands.setIp22(newIpStr)
            } else {
                // Lệnh cũ: 1F 1B 1F B2 [IP][MASK][GW] — gửi qua 9100
                PrinterCommands.setIpB2(newIpStr, maskStr, gwStr)
            }

            updateLoading(activity.getString(R.string.msg_sending))
            val ok = withContext(Dispatchers.IO) {
                try {
                    Socket().use { s ->
                        s.soTimeout = 2000
                        s.connect(InetSocketAddress(currentIp, port), 2500)
                        s.getOutputStream().use { os ->
                            os.write(payload)
                            os.flush()
                        }
                    }
                    true
                } catch (_: Exception) { false }
            }

            dismissLoading()
            btnChangeIp.isEnabled = true
            isChanging = false

            if (ok) {
                showResult(
                    if (use22)
                        activity.getString(R.string.lan_send_ok) + " (cmd 0x22, port $port)"
                    else
                        activity.getString(R.string.lan_send_ok)
                )
            } else {
                showResult(activity.getString(R.string.lan_send_fail))
            }
        }
    }


    // ===== Helpers (LAN) =====
    private fun sendRaw9100(ip: String, data: ByteArray, timeoutMs: Int = 2500): Boolean {
        return try {
            Socket().use { s ->
                s.soTimeout = timeoutMs
                s.connect(InetSocketAddress(ip, 9100), timeoutMs)
                s.getOutputStream().use { os ->
                    os.write(data)
                    os.flush()
                }
            }
            true
        } catch (_: Exception) { false }
    }

    private fun tcpAlive9100(ip: String, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s -> s.connect(InetSocketAddress(ip, 9100), timeoutMs) }
            true
        } catch (_: Exception) { false }
    }

    private fun ping(ip: String, timeoutMs: Int): Boolean {
        return try { InetAddress.getByName(ip).isReachable(timeoutMs) } catch (_: Exception) { false }
    }



}
