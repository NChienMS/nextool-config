package com.ycsoft.printernt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class DownloadedFileAdapter(
    private val onOpen: (File) -> Unit,
    private val onInstall: (File) -> Unit,
    private val onCopy: (File) -> Unit,
    private val onMove: (File) -> Unit
) : ListAdapter<File, DownloadedFileAdapter.FileVH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_downloaded_file, parent, false)
        return FileVH(view)
    }

    override fun onBindViewHolder(holder: FileVH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(file: File) {
            val tvName = itemView.findViewById<TextView>(R.id.txtFileName)
            val btnOpen = itemView.findViewById<View>(R.id.btnOpen)
            val btnInstall = itemView.findViewById<View>(R.id.btnInstall)
            val btnMore = itemView.findViewById<View>(R.id.btnMore)

            tvName.text = file.name

            btnOpen.setOnClickListener { onOpen(file) }
            btnInstall.setOnClickListener { onInstall(file) }

            btnMore.setOnClickListener {
                PopupMenu(itemView.context, it).apply {
                    menu.add("Sao chép")
                    menu.add("Di chuyển")
                    setOnMenuItemClickListener { m ->
                        when (m.title) {
                            "Sao chép" -> onCopy(file)
                            "Di chuyển" -> onMove(file)
                        }
                        true
                    }
                }.show()
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(old: File, new: File): Boolean =
            old.absolutePath == new.absolutePath

        override fun areContentsTheSame(old: File, new: File): Boolean =
            old.length() == new.length()
    }
}
