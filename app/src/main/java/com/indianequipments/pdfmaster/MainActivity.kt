package com.indianequipments.pdfmaster

import android.app.*
import android.content.*
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : Activity() {
    private lateinit var viewer: PdfCanvasView
    private lateinit var bars: WindowInsetsControllerCompat
    private val pick = 41
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        bars = WindowInsetsControllerCompat(window, window.decorView)
        viewer = PdfCanvasView(this) { visible -> setBarsVisible(visible) }
        setContentView(FrameLayout(this).apply { addView(viewer, FrameLayout.LayoutParams(-1,-1)) })
        pickPdf()
    }
    private fun setBarsVisible(visible: Boolean) {
        if (visible) bars.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        else bars.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    }
    private fun pickPdf() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type="application/pdf"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, pick)
    }
    override fun onActivityResult(requestCode:Int, resultCode:Int, data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==pick && resultCode==RESULT_OK) data?.data?.let { viewer.open(it) }
    }
}
