package com.ycsoft.printernt

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class IpChangeProgressDialogFragment : DialogFragment() {

    private lateinit var row1: LinearLayout
    private lateinit var row2: LinearLayout
    private lateinit var row3: LinearLayout

    private lateinit var pb1: ProgressBar
    private lateinit var pb2: ProgressBar
    private lateinit var pb3: ProgressBar

    private lateinit var tv1: TextView
    private lateinit var tv2: TextView
    private lateinit var tv3: TextView

    private lateinit var iv1: TextView
    private lateinit var iv2: TextView
    private lateinit var iv3: TextView

    private lateinit var tvHint: TextView

    private var okButton: android.widget.Button? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ip_change_progress, null, false)

        row1 = v.findViewById(R.id.rowStep1)
        row2 = v.findViewById(R.id.rowStep2)
        row3 = v.findViewById(R.id.rowStep3)

        pb1 = v.findViewById(R.id.pb1)
        pb2 = v.findViewById(R.id.pb2)
        pb3 = v.findViewById(R.id.pb3)

        tv1 = v.findViewById(R.id.tv1)
        tv2 = v.findViewById(R.id.tv2)
        tv3 = v.findViewById(R.id.tv3)

        iv1 = v.findViewById(R.id.iv1)
        iv2 = v.findViewById(R.id.iv2)
        iv3 = v.findViewById(R.id.iv3)

        tvHint = v.findViewById(R.id.tvHint)

        val dlg = AlertDialog.Builder(requireContext())
            .setView(v)
            .setCancelable(false)
            .setPositiveButton("OK", null) // chặn đóng cho tới khi xong
            .create()

        dlg.setOnShowListener {
            okButton = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton?.isEnabled = false
            okButton?.setOnClickListener { dismissAllowingStateLoss() }
        }

        return dlg
    }

    // ---- API update UI ----

    fun showStep(step: Int, text: String) {
        if (!isAdded) return
        when (step) {
            1 -> { row1.visibility = View.VISIBLE; tv1.text = text; setLoading(1) }
            2 -> { row2.visibility = View.VISIBLE; tv2.text = text; setLoading(2) }
            3 -> { row3.visibility = View.VISIBLE; tv3.text = text; setLoading(3) }
        }
    }

    fun setOk(step: Int) {
        if (!isAdded) return
        when (step) {
            1 -> { pb1.visibility = View.GONE; iv1.text = "✅" }
            2 -> { pb2.visibility = View.GONE; iv2.text = "✅" }
            3 -> { pb3.visibility = View.GONE; iv3.text = "✅" }
        }
    }

    fun setFail(step: Int, msg: String) {
        if (!isAdded) return
        when (step) {
            1 -> { pb1.visibility = View.GONE; iv1.text = "❌" }
            2 -> { pb2.visibility = View.GONE; iv2.text = "❌" }
            3 -> { pb3.visibility = View.GONE; iv3.text = "❌" }
        }
        tvHint.text = msg
        okButton?.isEnabled = true // fail là cho OK luôn để user đóng
    }

    fun finishAll(msg: String) {
        if (!isAdded) return
        tvHint.text = msg
        okButton?.isEnabled = true
    }

    private fun setLoading(step: Int) {
        when (step) {
            1 -> { pb1.visibility = View.VISIBLE; iv1.text = "" }
            2 -> { pb2.visibility = View.VISIBLE; iv2.text = "" }
            3 -> { pb3.visibility = View.VISIBLE; iv3.text = "" }
        }
    }
}
