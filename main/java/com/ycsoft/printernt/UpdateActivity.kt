package com.ycsoft.printernt

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ycsoft.printernt.databinding.ActivityUpdateBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.text.InputFilter

class UpdateActivity : AppCompatActivity() {

    private lateinit var b: ActivityUpdateBinding
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val SHEET_CSV_URL =
            "https://docs.google.com/spreadsheets/d/1oPF4N4MT4ZixdLgyMb7AniXBTkZZ0e3Huws7r4aM1ao/gviz/tq?tqx=out:csv&gid=0&range=B2:D11"

        // Domain server thật của anh (KHÔNG có dấu / ở cuối)
        private const val SERVER_BASE_URL = "https://kythuat.xyz"

        // API mới: POST body {"password":"..."}
        // Success 200: {"success":true, "files":[{"name","size","date","download_url"}, ...]}
        // Fail 401: {"success":false,"message":"Sai mật khẩu"}
        private const val API_FILES = "/api/printernet/files"

        // Nếu server không trả download_url, app sẽ tự ghép theo path này.
        // Ví dụ: https://kythuat.xyz/download/<tên file>
        private const val PUBLIC_DOWNLOAD_PATH = "/download/"
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // ====== Fallback Drive (Sheet) ======
    private var driveDownloadUrl: String? = null

    // ====== Server list ======
    private lateinit var serverAdapter: ServerFileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        b = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(b.root)
        // ✅ Chỉ cho nhập số (kể cả khi dán/paste)
        b.etInputId.filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
            for (i in start until end) {
                if (!source[i].isDigit()) return@InputFilter ""
            }
            null
        })


        initServerList()

        // Ẩn tất cả dữ liệu ban đầu
        hideDriveViews()
        hideServerViews()
        setLoading(false, null)

        b.btnRefreshAll.setOnClickListener { onClickUpdate() }

        b.btnDownloadDrive.setOnClickListener {
            val raw = driveDownloadUrl
            if (raw.isNullOrBlank()) {
                Toast.makeText(this, "Không có dữ liệu.", Toast.LENGTH_SHORT).show()
            } else {
                val url = normalizeGoogleDriveLink(raw)
                val filename = guessFileNameFromUrl(url)
                startDownloadToDownloads(url, filename)
            }
        }

        b.btnOpenDownloads.setOnClickListener {
            try {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Thiết bị không hỗ trợ mở mục Tệp đã tải.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initServerList() {
        serverAdapter = ServerFileAdapter { file ->
            val urlToDownload = file.url ?: (SERVER_BASE_URL + PUBLIC_DOWNLOAD_PATH + Uri.encode(file.name))
            val filename = file.name.ifBlank { guessFileNameFromUrl(urlToDownload) }
            startDownloadToDownloads(urlToDownload, filename)
        }
        b.rvServerFiles.layoutManager = LinearLayoutManager(this)
        b.rvServerFiles.adapter = serverAdapter
        // ✅ Chừa khoảng trắng dưới cùng để item cuối không dính đáy
        b.rvServerFiles.setPadding(
            b.rvServerFiles.paddingLeft,
            b.rvServerFiles.paddingTop,
            b.rvServerFiles.paddingRight,
            dp(80) // chỉnh 60/80/100 tuỳ anh muốn chừa bao nhiêu
        )
        b.rvServerFiles.clipToPadding = false


        // ✅ ĐƯỜNG KẺ NGĂN DÒNG
        b.rvServerFiles.addItemDecoration(
            androidx.recyclerview.widget.DividerItemDecoration(
                this,
                androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
            )
        )
    }

    // =========================
    // FLOW: Cập nhật
    // 1) Gọi server: xác thực + lấy list file
    // 2) Nếu fail => fallback Google Sheet (Excel)
    // =========================
    private fun onClickUpdate() {
        val inputId = b.etInputId.text?.toString()?.trim().orEmpty()

        // reset UI
        setLoading(false, null)
        hideDriveViews()
        hideServerViews()
        b.tvDriveTitle.isVisible = false

        if (inputId.isBlank()) {
            b.tvDriveTitle.text = "Vui lòng nhập ID"
            b.tvDriveTitle.isVisible = true
            return
        }

        setLoading(true, null)

        lifecycleScope.launch {
            // 1) thử server trước
            val serverResult = withContext(Dispatchers.IO) { tryFetchServerFiles(inputId) }

            if (serverResult is ServerFetchResult.Success) {
                setLoading(false, null)
                showServerFiles(serverResult.files)
                return@launch
            }

            // 2) server fail => fallback excel (sheet)
            val driveResult = withContext(Dispatchers.IO) { tryFetchDriveLink(inputId) }

            setLoading(false, null)

            if (driveResult is DriveFetchResult.Success) {
                showDriveLink(driveResult.row)
            } else {
                b.tvDriveTitle.text = "Không tìm thấy dữ liệu"
                b.tvDriveTitle.isVisible = true
            }
        }
    }

    // =========================
    // SERVER
    // =========================

    private data class ServerFile(
        val name: String,
        val size: Long?,
        val date: String?,
        val url: String? = null // download_url (absolute/relative)
    )

    private sealed class ServerFetchResult {
        data class Success(val files: List<ServerFile>) : ServerFetchResult()
        data class Fail(val reason: String) : ServerFetchResult()
    }

    private fun tryFetchServerFiles(password: String): ServerFetchResult {
        return try {
            val url = SERVER_BASE_URL + API_FILES
            val bodyJson = JSONObject().put("password", password).toString()
            val body = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

            val req = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                val txt = resp.body?.string().orEmpty()

                // 401 => sai mật khẩu
                if (resp.code == 401) {
                    val msg = runCatching { JSONObject(txt).optString("message", "Sai mật khẩu") }
                        .getOrDefault("Sai mật khẩu")
                    return ServerFetchResult.Fail(msg)
                }

                if (!resp.isSuccessful) return ServerFetchResult.Fail("Lỗi HTTP ${resp.code}")

                val ok = runCatching { JSONObject(txt).optBoolean("success", false) }.getOrDefault(false)
                if (!ok) {
                    val msg = runCatching { JSONObject(txt).optString("message", "Xác thực không thành công") }
                        .getOrDefault("Xác thực không thành công")
                    return ServerFetchResult.Fail(msg)
                }

                val files = parseServerFiles(txt)
                if (files.isEmpty()) return ServerFetchResult.Fail("Không có file")
                ServerFetchResult.Success(files)
            }
        } catch (_: Exception) {
            ServerFetchResult.Fail("Lỗi mạng")
        }
    }

    private fun parseServerFiles(json: String): List<ServerFile> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()

        val root = JSONObject(trimmed)
        val arr: JSONArray = root.optJSONArray("files") ?: JSONArray()

        val out = mutableListOf<ServerFile>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name")
            val size = if (o.has("size")) o.optLong("size") else null
            val date = o.optString("date", null)

            // server trả download_url theo spec mới
            var url = o.optString("download_url", null)
            if (url.isNullOrBlank()) url = o.optString("url", null)

            // nếu server trả đường dẫn tương đối "/download/..."
            if (!url.isNullOrBlank() && url.startsWith("/")) {
                url = SERVER_BASE_URL + url
            }

            if (name.isNotBlank()) out.add(ServerFile(name = name, size = size, date = date, url = url))
        }
        return out
    }

    private fun showServerFiles(files: List<ServerFile>) {
        b.tvDriveTitle.text = "Danh sách file"
        b.tvDriveTitle.isVisible = true

        // ẩn drive (fallback)
        hideDriveViews()

        // hiện list
        b.progressServer.isVisible = false
        b.tvServerError.isVisible = false
        b.rvServerFiles.isVisible = true

        serverAdapter.submit(files)
    }

    private fun hideServerViews() {
        b.progressServer.isVisible = false
        b.tvServerError.text = ""
        b.tvServerError.isVisible = false
        b.rvServerFiles.isVisible = false
        serverAdapter.submit(emptyList())
    }

    // =========================
    // EXCEL (fallback sheet)
    // =========================

    private data class Row(val name: String, val id: String, val link: String)

    private sealed class DriveFetchResult {
        data class Success(val row: Row) : DriveFetchResult()
        object NotFound : DriveFetchResult()
        object Error : DriveFetchResult()
    }

    private fun tryFetchDriveLink(inputId: String): DriveFetchResult {
        return try {
            val req = Request.Builder().url(SHEET_CSV_URL).get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return DriveFetchResult.Error

            val bytes = resp.body?.bytes() ?: ByteArray(0)
            val csv = bytes.toString(Charset.forName("UTF-8"))
            val rows = parseBlockB2toD11(csv)

            val match = rows.firstOrNull { it.id.equals(inputId, ignoreCase = true) }
            if (match != null && match.link.isNotBlank()) DriveFetchResult.Success(match)
            else DriveFetchResult.NotFound
        } catch (_: Exception) {
            DriveFetchResult.Error
        }
    }

    private fun parseBlockB2toD11(csv: String): List<Row> {
        val lines = csv.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
            .toList()

        val result = mutableListOf<Row>()
        for (ln in lines) {
            val cells = parseCsvLine(ln)
            val name = cells.getOrNull(0)?.trim()?.trim('"') ?: ""
            val id = cells.getOrNull(1)?.trim()?.trim('"') ?: ""
            val link = cells.getOrNull(2)?.trim()?.trim('"') ?: ""
            if (name.isNotEmpty() || id.isNotEmpty() || link.isNotEmpty()) {
                result.add(Row(name, id, link))
            }
        }
        return result
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when (c) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> {
                    if (inQuotes) cur.append(c) else {
                        out.add(cur.toString())
                        cur.clear()
                    }
                }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    private fun showDriveLink(row: Row) {
        driveDownloadUrl = row.link

        b.tvDriveTitle.text = row.name.ifBlank { "Tải từ Drive" }
        b.tvDriveLink.text = row.link

        b.tvDriveTitle.isVisible = true
        b.tvDriveLink.isVisible = true
        b.btnDownloadDrive.isVisible = true
        b.btnDownloadDrive.isEnabled = true

        hideServerViews()
    }

    private fun hideDriveViews() {
        driveDownloadUrl = null
        b.tvDriveLink.text = ""
        b.tvDriveLink.isVisible = false
        b.btnDownloadDrive.isVisible = false
        b.btnDownloadDrive.isEnabled = false
    }

    // =========================
    // UI chung
    // =========================

    private fun setLoading(loading: Boolean, genericMsg: String? = null) {
        b.progress.isVisible = loading
        b.tvError.isVisible = !loading && genericMsg != null
        b.tvError.text = genericMsg ?: ""
    }

    // =========================
    // Download + utils
    // =========================

    private fun normalizeGoogleDriveLink(link: String): String {
        val idx = link.indexOf("/file/d/")
        if (idx != -1) {
            val id = link.substring(idx + 8).substringBefore('/')
            if (id.isNotBlank()) return "https://drive.google.com/uc?id=$id&export=download"
        }

        val uri = Uri.parse(link)
        val qId = uri.getQueryParameter("id")
        if (!qId.isNullOrBlank()) return "https://drive.google.com/uc?id=$qId&export=download"

        return link
    }

    private fun startDownloadToDownloads(url: String, fileName: String) {
        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(url)

            val request = DownloadManager.Request(uri)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setTitle(fileName)
                .setDescription("Đang tải…")

            val ext = fileName.substringAfterLast('.', "")
            val mime = if (ext.equals("apk", true)) {
                "application/vnd.android.package-archive"
            } else {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.US))
            }
            if (!mime.isNullOrBlank()) request.setMimeType(mime)

            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            dm.enqueue(request)
            Toast.makeText(this, "Bắt đầu tải: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi tải: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun guessFileNameFromUrl(url: String): String {
        val uri = Uri.parse(url)
        val nameFromQuery = uri.getQueryParameter("filename")
        if (!nameFromQuery.isNullOrBlank()) return nameFromQuery

        val last = uri.lastPathSegment ?: return "download.bin"
        return if (last.equals("uc", true)) "file_tu_drive.bin" else last
    }

    // =========================
    // Adapter hiển thị list file server: (Tên / Dung lượng / Ngày / Nút tải)
    // =========================

    private class ServerFileAdapter(
        private val onDownload: (ServerFile) -> Unit
    ) : RecyclerView.Adapter<ServerFileVH>() {

        private val items = mutableListOf<ServerFile>()

        fun submit(newItems: List<ServerFile>) {
            items.clear()
            // Sort A-Z theo từng ký tự (lexicographic) - ignore case để ổn định hơn
            items.addAll(
                newItems.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            )
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ServerFileVH {
            return ServerFileVH(parent)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ServerFileVH, position: Int) {
            holder.bind(items[position], position, onDownload) // truyền position để hiện STT
        }
    }


    private class ServerFileVH(parent: android.view.ViewGroup) : RecyclerView.ViewHolder(
        android.widget.LinearLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(parent.context, 12), dp(parent.context, 10), dp(parent.context, 12), dp(parent.context, 10))

            // Cột trái (tên + meta)
            addView(android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                addView(android.widget.TextView(context).apply {
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                addView(android.widget.TextView(context).apply {
                    textSize = 13f
                    setPadding(0, dp(context, 4), 0, 0)
                })
            })

// Nút tải bên phải (căn phải cuối dòng)
            addView(android.widget.Button(context).apply {
                text = "Tải"
                isAllCaps = false
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(context, 12)  // cách cột trái 1 chút
                    gravity = android.view.Gravity.END
                }
            })

        }
    ) {

        companion object {
            private fun dp(ctx: Context, v: Int): Int {
                return (v * ctx.resources.displayMetrics.density).toInt()
            }
        }

        private val tvName: android.widget.TextView
        private val tvMeta: android.widget.TextView
        private val btn: android.widget.Button

        init {
            val root = itemView as android.widget.LinearLayout
            val leftCol = root.getChildAt(0) as android.widget.LinearLayout
            tvName = leftCol.getChildAt(0) as android.widget.TextView
            tvMeta = leftCol.getChildAt(1) as android.widget.TextView
            btn = root.getChildAt(1) as android.widget.Button
        }

        fun bind(item: ServerFile, position: Int, onDownload: (ServerFile) -> Unit) {
            val stt = position + 1
            val name = item.name.ifBlank { "Tệp không tên" }
            tvName.text = "$stt. $name"

            val sizeText = item.size?.let { humanSize(it) } ?: ""
            val dateText = item.date?.let { formatDate_ddMMyyyy(it) } ?: ""

            // Dòng meta nằm dưới tên file
            tvMeta.text = listOf(sizeText, dateText).filter { it.isNotBlank() }.joinToString(" • ")

            btn.setOnClickListener { onDownload(item) }
        }

        private fun formatDate_ddMMyyyy(input: String): String {
            // server đang trả "yyyy-MM-dd" -> đổi thành "dd-MM-yyyy"
            return try {
                val src = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dst = java.text.SimpleDateFormat("dd-MM-yyyy", Locale.US)
                val d = src.parse(input.trim())
                if (d != null) dst.format(d) else input
            } catch (_: Exception) {
                input
            }
        }

        private fun humanSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var b = bytes.toDouble()
            var idx = 0
            while (b >= 1024 && idx < units.lastIndex) {
                b /= 1024
                idx++
            }
            val value = if (idx == 0) b.toInt().toString() else String.format(Locale.US, "%.1f", b)
            return "$value ${units[idx]}"
        }
    }

}
