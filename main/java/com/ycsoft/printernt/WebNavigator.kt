package com.ycsoft.printernt

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object WebNavigator {

    /**
     * Ưu tiên mở bằng TRÌNH DUYỆT. Nếu không có, fallback sang WebView trong app.
     * Tự thêm FLAG_ACTIVITY_NEW_TASK khi context không phải Activity.
     */
    fun open(
        context: Context,
        url: String,
        title: String? = null,
        fallbackToInApp: Boolean = true
    ) {
        val uri = try { Uri.parse(url) } catch (_: Throwable) {
            Toast.makeText(context, context.getString(R.string.webmsg_invalid_link), Toast.LENGTH_SHORT).show()
            return
        }

        // 1) Ưu tiên mở ngoài (trình duyệt)
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        val canHandleExternally = viewIntent.resolveActivity(context.packageManager) != null
        if (canHandleExternally) {
            try {
                if (context !is android.app.Activity) viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(viewIntent)
                return
            } catch (_: ActivityNotFoundException) { /* rơi xuống fallback */ }
            catch (_: Throwable) { /* rơi xuống fallback */ }
        }

        // 2) Fallback: mở WebView trong app (nếu cho phép)
        if (fallbackToInApp) {
            try {
                val wv = Intent(context, InAppWebViewActivity::class.java)
                    .putExtra(InAppWebViewActivity.EXTRA_URL, url)
                    .putExtra(InAppWebViewActivity.EXTRA_TITLE, title ?: context.getString(R.string.webmenu_help))
                if (context !is android.app.Activity) wv.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(wv)
                return
            } catch (_: Throwable) { /* rơi xuống báo lỗi */ }
        }

        // 3) Báo lỗi thân thiện
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.webmsg_cannot_open_link))
            .setMessage(context.getString(R.string.msg_no_browser_explain))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Mặc định mở trang Hướng dẫn sử dụng (lấy URL từ strings, có fallback). */
    fun openGuide(context: Context) {
        val url = try { context.getString(R.string.guide_url) } catch (_: Exception) {
            "https://your-domain.vn/guide"
        }
        open(context, url, title = context.getString(R.string.webmenu_help))
    }
}
