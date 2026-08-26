package com.indianequipments.pdfmaster

import android.content.Intent
import android.database.Cursor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class PdfViewerActivity : AppCompatActivity() {
    private var renderer: PdfRenderer? = null
    private var descriptor: android.os.ParcelFileDescriptor? = null
    private var currentPage = 0
    private lateinit var pageView: PdfPageView
    private lateinit var pageIndicator: TextView
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var loading: ProgressBar
    private var pdfName = "PDF"
    private var cachedFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        pageView = findViewById(R.id.pdfPageView)
        pageIndicator = findViewById(R.id.pageIndicator)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        loading = findViewById(R.id.loading)

        pdfName = resolveDisplayName(intent?.data) ?: "PDF"
        findViewById<TextView>(R.id.pdfName).text = pdfName

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.shareButton).setOnClickListener { sharePdf() }
        findViewById<ImageButton>(R.id.infoButton).setOnClickListener { showInfo() }
        previousButton.setOnClickListener { showPage(currentPage - 1) }
        nextButton.setOnClickListener { showPage(currentPage + 1) }

        openPdf(intent?.data)
    }

    private fun openPdf(uri: Uri?) {
        if (uri == null) {
            showError("No PDF was provided")
            return
        }
        loading.visibility = View.VISIBLE
        Thread {
            try {
                val file = copyToCache(uri)
                cachedFile = file
                val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(fd)
                runOnUiThread {
                    descriptor = fd
                    renderer = pdfRenderer
                    loading.visibility = View.GONE
                    showPage(0)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading.visibility = View.GONE
                    showError("This PDF could not be opened")
                }
            }
        }.start()
    }

    private fun showPage(index: Int) {
        val pdf = renderer ?: return
        if (index !in 0 until pdf.pageCount) return
        currentPage = index
        pageView.showPage(pdf, index)
        pageIndicator.text = "${index + 1} / ${pdf.pageCount}"
        previousButton.isEnabled = index > 0
        nextButton.isEnabled = index < pdf.pageCount - 1
    }

    private fun sharePdf() {
        val file = cachedFile
        if (file == null) {
            Toast.makeText(this, "PDF is still loading", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share PDF"))
    }

    private fun showInfo() {
        val pages = renderer?.pageCount ?: 0
        AlertDialog.Builder(this)
            .setTitle("PDF information")
            .setMessage("Name: $pdfName\nPages: $pages")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun copyToCache(uri: Uri): File {
        val file = File(cacheDir, "opened_${System.currentTimeMillis()}.pdf")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read PDF" }
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file
    }

    private fun resolveDisplayName(uri: Uri?): String? {
        if (uri == null) return null
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor?.moveToFirst() == true) cursor.getString(0) else uri.lastPathSegment
        } finally {
            cursor?.close()
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        renderer?.close()
        descriptor?.close()
        cachedFile?.delete()
        super.onDestroy()
    }
}
