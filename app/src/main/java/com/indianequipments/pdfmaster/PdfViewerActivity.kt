package com.indianequipments.pdfmaster

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
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
    private var controlsHideRunnable: Runnable? = null
    private var controlsVisible = true
    private var multiplePages = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.rgb(16, 17, 20)
        window.navigationBarColor = android.graphics.Color.BLACK

        toolbar = findViewById(R.id.viewerToolbar)
        recyclerView = findViewById(R.id.pdfRecyclerView)
        pageIndicator = findViewById(R.id.pageIndicator)
        loading = findViewById(R.id.loading)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(false)
        recyclerView.itemAnimator = null
        recyclerView.setItemViewCacheSize(2)

        sourceUri = intent?.data
        pdfName = resolveDisplayName(sourceUri) ?: "PDF"
        findViewById<TextView>(R.id.pdfName).text = pdfName
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.shareButton).setOnClickListener { sharePdf() }
        findViewById<ImageButton>(R.id.infoButton).setOnClickListener { showInfo() }
        findViewById<ImageButton>(R.id.openWithButton).setOnClickListener { openWithAnotherApp() }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                updateCurrentPage()
                if (dy != 0 && controlsVisible) scheduleControlsHide(2000L)
            }
        })

        showControlsAndScheduleHide()
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
                    multiplePages = pdfRenderer.pageCount > 1

                    // RecyclerView width is not reliable during onCreate (it can still be 0/1px).
                    // Wait for layout so every page is rendered at the actual screen width.
                    recyclerView.post {
                        adapter = PdfPageAdapter(
                            pdfRenderer,
                            recyclerView.width.coerceAtLeast(1),
                            onPageTap = { toggleControls() }
                        )
                        recyclerView.adapter = adapter
                        pageIndicator.visibility = if (multiplePages && controlsVisible) View.VISIBLE else View.GONE
                        if (multiplePages) pageIndicator.text = "1 / ${pdfRenderer.pageCount}"
                        showControlsAndScheduleHide()
                    }
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
        if (total <= 1) return
        if (first != RecyclerView.NO_POSITION) pageIndicator.text = "${first + 1} / $total"
    }

    private fun toggleControls() {
        if (controlsVisible) {
            hideControls()
        } else {
            showControlsAndScheduleHide()
        }
    }

    private fun showControlsAndScheduleHide() {
        controlsHideRunnable?.let(uiHandler::removeCallbacks)
        controlsVisible = true
        toolbar.visibility = View.VISIBLE
        pageIndicator.visibility = if (multiplePages) View.VISIBLE else View.GONE
        setRecyclerBottomInset(toolbarVisible = true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())
        scheduleControlsHide(2000L)
    }

    private fun scheduleControlsHide(delayMs: Long) {
        controlsHideRunnable?.let(uiHandler::removeCallbacks)
        controlsHideRunnable = Runnable { hideControls() }
        uiHandler.postDelayed(controlsHideRunnable!!, delayMs)
    }

    private fun hideControls() {
        controlsHideRunnable?.let(uiHandler::removeCallbacks)
        controlsHideRunnable = null
        controlsVisible = false
        toolbar.visibility = View.GONE
        pageIndicator.visibility = View.GONE
        setRecyclerBottomInset(toolbarVisible = false)
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.statusBars())
    }

    private fun setRecyclerBottomInset(toolbarVisible: Boolean) {
        val params = recyclerView.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val targetBottom = if (toolbarVisible) dp(72) else 0
        if (params.bottomMargin != targetBottom) {
            params.bottomMargin = targetBottom
            recyclerView.layoutParams = params
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

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
        AlertDialog.Builder(this)
            .setTitle("PDF information")
            .setMessage("Name: $pdfName\nSize: $size\nPages: $pages\nLocation: ${sourceUri ?: "Unknown"}")
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
        controlsHideRunnable?.let(uiHandler::removeCallbacks)
        adapter?.shutdown()
        recyclerView.adapter = null
        renderer?.close()
        descriptor?.close()
        cachedFile?.delete()
        super.onDestroy()
    }
}
