package com.indianequipments.pdfmaster

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : Activity() {
    private lateinit var viewer: PdfCanvasView
    private lateinit var bars: WindowInsetsControllerCompat
    private lateinit var controls: LinearLayout
    private var currentUri: Uri? = null
    private val pick = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        bars = WindowInsetsControllerCompat(window, window.decorView)

        val root = FrameLayout(this)
        viewer = PdfCanvasView(this) { visible ->
            controls.visibility = if (visible) View.VISIBLE else View.GONE
            setBarsVisible(visible)
        }
        root.addView(viewer, FrameLayout.LayoutParams(-1, -1))

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(10, 8, 10, 8)
            setBackgroundColor(Color.WHITE)
            elevation = 10f
        }
        root.addView(controls, FrameLayout.LayoutParams(-1, 64, Gravity.BOTTOM))
        addButton("Search") { showSearch() }
        addButton("Share") { sharePdf() }
        addButton("Open") { openOtherApp() }
        addButton("Info") { showInfo() }
        setContentView(root)
        pickPdf()
    }

    private fun addButton(label: String, action: () -> Unit) {
        controls.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        })
    }

    private fun setBarsVisible(visible: Boolean) {
        if (visible) bars.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        else bars.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    }

    private fun pickPdf() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, pick)
    }

    private fun showSearch() {
        val input = EditText(this).apply { hint = "Search text in PDF" }
        AlertDialog.Builder(this).setTitle("Search PDF").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Search") { _, _ ->
                val term = input.text.toString().trim()
                if (term.isNotEmpty()) {
                    val page = viewer.search(term)
                    Toast.makeText(this, if (page >= 0) "Found on page ${page + 1}" else "Text not found", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun sharePdf() {
        currentUri?.let {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share PDF"))
        }
    }

    private fun openOtherApp() {
        currentUri?.let {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(it, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    private fun showInfo() {
        AlertDialog.Builder(this).setTitle("PDF Info")
            .setMessage(viewer.infoText(currentUri)).setPositiveButton("OK", null).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pick && resultCode == RESULT_OK) data?.data?.let {
            currentUri = it
            viewer.open(it)
        }
    }
}
