package com.indianequipments.pdfmaster

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PdfPageAdapter(
    private val renderer: PdfRenderer,
    private val pageWidthPx: Int
) : RecyclerView.Adapter<PdfPageAdapter.PageHolder>() {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    class PageHolder(val pageView: PdfPageView) : RecyclerView.ViewHolder(pageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val pageView = PdfPageView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 10, 0, 10)
            setBackgroundColor(Color.rgb(224, 224, 224))
        }
        return PageHolder(pageView)
    }

    override fun getItemCount(): Int = renderer.pageCount

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val view = holder.pageView
        view.clearPage()
        view.tag = position

        executor.execute {
            var result: Bitmap? = null
            var targetHeight = 0
            try {
                synchronized(renderer) {
                    val page = renderer.openPage(position)
                    try {
                        val sourceWidth = page.width.coerceAtLeast(1)
                        val sourceHeight = page.height.coerceAtLeast(1)
                        val targetWidth = pageWidthPx.coerceAtLeast(1)
                        val scale = (targetWidth.toFloat() / sourceWidth).coerceIn(0.5f, 3f)
                        val renderWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
                        targetHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
                        result = Bitmap.createBitmap(renderWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        result?.eraseColor(Color.WHITE)
                        page.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } finally {
                        page.close()
                    }
                }
            } catch (_: Throwable) {
                result?.recycle()
                result = null
            }

            val bitmap = result
            mainHandler.post {
                if (view.tag == position && bitmap != null) {
                    view.layoutParams = view.layoutParams.apply {
                        height = targetHeight + view.paddingTop + view.paddingBottom
                    }
                    view.setRenderedBitmap(bitmap)
                } else {
                    bitmap?.recycle()
                }
            }
        }
    }

    override fun onViewRecycled(holder: PageHolder) {
        holder.pageView.tag = null
        holder.pageView.clearPage()
        super.onViewRecycled(holder)
    }

    fun shutdown() {
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
