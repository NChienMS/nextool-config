package com.ycsoft.printernt

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class ConfirmDialogFragment(
    private val titleText: String,
    private val messageText: String? = null,
    private val customContentView: View? = null,
    private val positiveText: String = "OK",
    private val negativeText: String = "Hủy",
    private val onPositive: () -> Unit
) : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false // ✅ không cho đóng bằng back/outside
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setCanceledOnTouchOutside(false) // ✅ không đóng khi chạm ngoài
        d.setCancelable(false)             // ✅ không đóng bằng nút back
        return d
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(), // ✅ rộng hơn
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.dialog_confirm, container, false)

        val tvTitle = v.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = v.findViewById<TextView>(R.id.tvMessage)
        val btnPositive = v.findViewById<TextView>(R.id.btnPositive)
        val btnNegative = v.findViewById<TextView>(R.id.btnNegative)
        val customContainer = v.findViewById<FrameLayout>(R.id.customContainer)

        tvTitle.text = titleText

        if (!messageText.isNullOrBlank()) {
            tvMessage.visibility = View.VISIBLE
            tvMessage.text = messageText
        } else {
            tvMessage.visibility = View.GONE
        }

        if (customContentView != null) {
            customContainer.visibility = View.VISIBLE
            customContainer.removeAllViews()
            customContainer.addView(customContentView)
        } else {
            customContainer.visibility = View.GONE
        }

        btnPositive.text = positiveText
        btnNegative.text = negativeText

        // ✅ Chỉ nút Hủy mới đóng popup
        btnNegative.setOnClickListener { dismissAllowingStateLoss() }

        // ✅ Positive KHÔNG tự đóng (caller tự dismiss sau khi xử lý xong)
        btnPositive.setOnClickListener { onPositive.invoke() }

        return v
    }
}
