package com.ycsoft.printernt.ads.remotenoti

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.ycsoft.printernt.R
import android.widget.ImageView
import com.bumptech.glide.Glide
import android.graphics.Paint
/**
 * RemoteNotiDialogFragment
 * ----------------------------
 * Nhiệm vụ:
 *  - Hiển thị popup thông báo (title/content)
 *  - Click "Xem thêm" mở actionUrl
 *  - Đánh dấu đã hiển thị (markShown) để giới hạn số lần
 */
class RemoteNotiDialogFragment : DialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false   // ❗ chặn BACK + chặn chạm ra ngoài
    }

    companion object {
        private const val ARG_TITLE = "t"
        private const val ARG_CONTENT = "c"
        private const val ARG_URL = "u"
        private const val ARG_META = "m"
        private const val ARG_IMAGE = "i"

        fun newInstance(data: RemoteNotiManager.NotiData): RemoteNotiDialogFragment {
            return RemoteNotiDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, data.title)
                    putString(ARG_CONTENT, data.content)
                    putString(ARG_URL, data.actionUrl)
                    putInt(ARG_META, data.metaVersion)
                    putString(ARG_IMAGE, data.imageUrl)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        return inflater.inflate(R.layout.dialog_remote_noti, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val title = requireArguments().getString(ARG_TITLE, "")
        val content = requireArguments().getString(ARG_CONTENT, "")
        val actionUrl = requireArguments().getString(ARG_URL, "")

        view.findViewById<TextView>(R.id.tvTitle).text = title
        view.findViewById<TextView>(R.id.tvContent).text = content

        val btnOpen = view.findViewById<TextView>(R.id.btnOpen)
        btnOpen.paintFlags = btnOpen.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismiss() }

        view.findViewById<View>(R.id.btnOpen).setOnClickListener {
            if (actionUrl.isNotBlank()) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl)))
                } catch (e: Exception) {
                    // ===== START LOG =====
                    Log.e("RemoteNoti", "Không mở được link actionUrl", e)
                    // ===== END LOG =====
                }
            }

        }
        val imageUrl = requireArguments().getString(ARG_IMAGE, "")
        val iv = view.findViewById<ImageView>(R.id.ivRemoteImage)

        if (imageUrl.isBlank()) {
            iv.visibility = View.GONE
        } else {
            iv.visibility = View.VISIBLE
            Glide.with(this)
                .load(imageUrl)
                .centerCrop()
                .timeout(8000)
                .into(iv)
        }

        // Đánh dấu đã hiện (ngay khi popup show)
        RemoteNotiManager.markShown()
    }
}
