package com.indianequipments.pdfmaster

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream

class PdfViewerActivity : AppCompatActivity() {
    private var renderer: android.graphics.pdf.PdfRenderer? = null
    private var descriptor: android.os.ParcelFileDescriptor? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var pageIndicator: TextView
    private lateinit var loading: ProgressBar
    private lateinit var toolbar: View
    private var pdfName = "PDF"
    private var cachedFile: File? = null
    private var sourceUri: Uri? = null
    private var adapter: PdfPageAdapter? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var revealRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        toolbar = findViewById(R.id.viewerToolbar)
        recyclerView = findViewById(R.id.pdfRecyclerView)
        pageIndicator = findViewById(R.id.pageIndicator)
        loading = findViewById(R.id.loading)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(false)
        recyclerView.itemAnimator = null

        sourceUri = intent?.data
        pdfName = resolveDisplayName(sourceUri) ?: "PDF"
        findViewById<TextView>(R.id.pdfName).text = pdfName

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.shareButton).setOnClickListener { sharePdf() }
        findViewById<ImageButton>(R.id.infoButton).setOnClickListener { showInfo() }
        findViewById<ImageButton>(R.id.openWithButton).setOnClickListener { openWithAnotherApp() }

        recyclerView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) revealControlsTemporarily()
            false
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                updateCurrentPage()
                if (dy != 0) hideControlsSoon()
            }
        })

        openPdf(sourceUri)
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
                val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = android.graphics.pdf.PdfRenderer(fd)
                runOnUiThread {
                    cachedFile = file
                    descriptor = fd
                    renderer = pdfRenderer
                    loading.visibility = View.GONE
                    val width = resources.displayMetrics.widthPixels - 20
                    adapter = PdfPageAdapter(pdfRenderer, width)
                    recyclerView.adapter = adapter
                    pageIndicator.text = "1 / ${pdfRenderer.pageCount}"
                    revealControlsTemporarily()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    loading.visibility = View.GONE
                    showError("This PDF could not be opened")
                }
            }
        }.start()
    }

    private fun updateCurrentPage() {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val total = renderer?.pageCount ?: return
        if (first != RecyclerView.NO_POSITION) pageIndicator.text = "${first + 1} / $total"
    }

    private fun revealControlsTemporarily() {
        toolbar.visibility = View.VISIBLE
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.statusBars())
        revealRunnable?.let(uiHandler::removeCallbacks)
        revealRunnable = Runnable { hideControls() }
        uiHandler.postDelayed(revealRunnable!!, 3000L)
    }

    private fun hideControlsSoon() {
        revealRunnable?.let(uiHandler::removeCallbacks)
        revealRunnable = Runnable { hideControls() }
        uiHandler.postDelayed(revealRunnable!!, 1200L)
    }

    private fun hideControls() {
        toolbar.visibility = View.GONE
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.statusBars())
    }

    private fun sharePdf() {
        val file = cachedFile ?: run {
            Toast.makeText(this, "PDF is still loading", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share PDF"))
    }

    private fun openWithAnotherApp() {
        val uri = sourceUri ?: cachedFile?.let { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) }
        if (uri == null) {
            Toast.makeText(this, "PDF is still loading", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, "No compatible PDF app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInfo() {
        val pages = renderer?.pageCount ?: 0
        val size = cachedFile?.length()?.let { formatSize(it) } ?: "Unknown"
        val location = sourceUri?.toString() ?: "Unknown"
        AlertDialog.Builder(this)
            .setTitle("PDF information")
            .setMessage("Name: $pdfName\nSize: $size\nPages: $pages\nLocation: $location")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024f)
        else -> "$bytes B"
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
        revealRunnable?.let(uiHandler::removeCallbacks)
        adapter?.shutdown()
        recyclerView.adapter = null
        renderer?.close()
        descriptor?.close()
        cachedFile?.delete()
        super.onDestroy()
    }
}
