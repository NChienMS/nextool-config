package com.ycsoft.printernt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.ycsoft.printernt.R

class QrScanDialogFragment(
    private val onResult: (String) -> Unit
) : DialogFragment() {

    private lateinit var scanner: DecoratedBarcodeView
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.dialog_qr_scan, container, false)
        scanner = v.findViewById(R.id.barcodeScanner)

        v.findViewById<View>(R.id.btnClose).setOnClickListener { dismissAllowingStateLoss() }

        // Quét 1 lần rồi trả kết quả
        scanner.decodeSingle { result ->
            if (handled) return@decodeSingle
            handled = true
            onResult(result.text ?: "")
            dismissAllowingStateLoss()
        }
        return v
    }

    override fun onResume() {
        super.onResume()
        scanner.resume()
    }

    override fun onPause() {
        scanner.pause()
        super.onPause()
    }
}
