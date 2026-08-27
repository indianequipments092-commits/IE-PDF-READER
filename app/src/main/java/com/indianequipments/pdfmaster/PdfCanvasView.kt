package com.indianequipments.pdfmaster

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.View
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

class PdfCanvasView(private val ctx: Context, private val onActivityBar: (Boolean) -> Unit) : View(ctx) {
    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var tempFile: File? = null
    private var pageSizes = emptyList<Point>()

    private var scale = 1f
    private var fit = 1f
    private var tx = 0f
    private var ty = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var oldDist = 0f
    private var pinchMidX = 0f
    private var pinchMidY = 0f

    private val gap = 24f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
    }
    private val hideRunnable = Runnable { onActivityBar(false) }
    private val renderExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val renderGeneration = AtomicInteger(0)
    private val rendererLock = Any()

    @Volatile private var cachedBitmap: Bitmap? = null
    @Volatile private var cachedPage = -1
    @Volatile private var cachedRenderScale = 0f
    @Volatile private var requestedPage = -1
    @Volatile private var requestedScale = 0f

    fun open(uri: Uri) {
        synchronized(rendererLock) {
            renderGeneration.incrementAndGet()
            renderer?.close()
            pfd?.close()
            tempFile?.delete()
            cachedBitmap?.recycle()
            cachedBitmap = null
            cachedPage = -1
            cachedRenderScale = 0f

            tempFile = File.createTempFile("pdfmaster_", ".pdf", ctx.cacheDir)
            ctx.contentResolver.openInputStream(uri)!!.use { input ->
                FileOutputStream(tempFile!!).use { output -> input.copyTo(output) }
            }
            pfd = ParcelFileDescriptor.open(tempFile!!, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd!!)
            val r = renderer!!
            pageSizes = List(r.pageCount) { i ->
                r.openPage(i).use { Point(it.width, it.height) }
            }
        }

        fit = calculateFit()
        scale = fit
        tx = 0f
        ty = initialSinglePageY()
        requestedPage = -1
        requestedScale = 0f
        invalidate()
        post {
            reveal()
            requestRender(0, fit)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (pageSizes.isNotEmpty()) {
            fit = calculateFit()
            scale = fit
            tx = 0f
            ty = initialSinglePageY()
            invalidate()
            post { requestRender(0, fit) }
        }
    }

    private fun calculateFit(): Float {
        val first = pageSizes.firstOrNull() ?: return 1f
        if (width <= 0 || first.x <= 0) return 1f
        return width.toFloat() / first.x.toFloat()
    }

    private fun initialSinglePageY(): Float {
        val first = pageSizes.firstOrNull() ?: return 0f
        if (pageSizes.size != 1 || width <= 0) return 0f
        val h = first.y * fit
        return if (h < height) (height - h) / 2f else 0f
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(Color.rgb(235, 235, 235))
        if (pageSizes.isEmpty()) return

        if (pageSizes.size == 1 && scale <= fit) {
            val s = pageSizes[0]
            drawCached(c, 0, 0f, initialSinglePageY(), width.toFloat(), s.y * fit)
            return
        }

        var y = ty
        for (i in pageSizes.indices) {
            val s = pageSizes[i]
            val w = s.x * scale
            val h = s.y * scale
            val left = (width - w) / 2f + tx
            if (y + h >= 0f && y <= height.toFloat()) {
                drawCached(c, i, left, y, w, h)
            }
            y += h + gap
            if (y > height && i > 0) break
        }
    }

    private fun drawCached(c: Canvas, index: Int, left: Float, top: Float, w: Float, h: Float) {
        val bitmap = cachedBitmap
        if (bitmap != null && cachedPage == index) {
            c.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), paint)
        } else {
            c.drawColor(Color.rgb(235, 235, 235))
            requestRender(index, if (scale > fit) scale else fit)
        }
    }

    /**
     * Rendering is deliberately done away from Canvas/onDraw. During a pinch we
     * keep the last bitmap on screen and only render the new resolution after
     * the gesture settles. This prevents PDF rendering from blocking frames.
     */
    private fun requestRender(index: Int, targetScale: Float) {
        if (index !in pageSizes.indices) return
        val generation = renderGeneration.get()
        val clampedScale = targetScale.coerceIn(fit, fit * 10f)
        if (requestedPage == index && abs(requestedScale - clampedScale) < 0.01f) return
        if (cachedPage == index && abs(cachedRenderScale - clampedScale) < 0.01f) return

        requestedPage = index
        requestedScale = clampedScale

        renderExecutor.execute {
            val bitmap = renderPage(index, clampedScale) ?: return@execute
            if (generation != renderGeneration.get()) {
                bitmap.recycle()
                return@execute
            }
            post {
                if (generation != renderGeneration.get()) {
                    bitmap.recycle()
                    return@post
                }
                val old = cachedBitmap
                cachedBitmap = bitmap
                cachedPage = index
                cachedRenderScale = clampedScale
                if (old != null && old !== bitmap && !old.isRecycled) old.recycle()
                invalidate()
            }
        }
    }

    private fun renderPage(index: Int, targetScale: Float): Bitmap? {
        val size = pageSizes.getOrNull(index) ?: return null
        val maxSide = 3072
        val desiredW = max(1, ceil(size.x * targetScale).toInt())
        val desiredH = max(1, ceil(size.y * targetScale).toInt())
        val factor = min(1f, min(maxSide.toFloat() / desiredW, maxSide.toFloat() / desiredH))
        val bw = max(1, floor(desiredW * factor).toInt())
        val bh = max(1, floor(desiredH * factor).toInt())

        val bitmap = try {
            Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return null
        }

        try {
            synchronized(rendererLock) {
                val r = renderer ?: run {
                    bitmap.recycle()
                    return null
                }
                r.openPage(index).use { page ->
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
            return bitmap
        } catch (_: Exception) {
            bitmap.recycle()
            return null
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x
                lastY = e.y
                reveal()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = distance(e)
                if (oldDist > 0f) {
                    pinchMidX = (e.getX(0) + e.getX(1)) / 2f
                    pinchMidY = (e.getY(0) + e.getY(1)) / 2f
                }
                reveal()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                reveal()
                if (e.pointerCount >= 2) {
                    val d = distance(e)
                    if (oldDist > 0f && d > 0f) {
                        val newScale = (scale * (d / oldDist)).coerceIn(fit, fit * 10f)
                        val k = newScale / scale
                        tx = pinchMidX + (tx - pinchMidX) * k
                        ty = pinchMidY + (ty - pinchMidY) * k
                        scale = newScale
                        oldDist = d
                        clamp()
                        // Do not re-render on every pinch frame. Existing bitmap is scaled smoothly.
                        invalidate()
                    }
                } else {
                    tx += e.x - lastX
                    ty += e.y - lastY
                    lastX = e.x
                    lastY = e.y
                    clamp()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                oldDist = 0f
                return true
            }
            MotionEvent.ACTION_UP -> {
                oldDist = 0f
                clamp()
                requestRender(nearestVisiblePage(), scale)
                reveal()
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                oldDist = 0f
                return true
            }
        }
        return true
    }

    private fun distance(e: MotionEvent): Float =
        if (e.pointerCount >= 2) hypot(
            (e.getX(0) - e.getX(1)).toDouble(),
            (e.getY(0) - e.getY(1)).toDouble()
        ).toFloat() else 0f

    private fun totalHeight(): Float {
        var total = 0f
        for (s in pageSizes) total += s.y * scale
        return total + gap * max(0, pageSizes.size - 1)
    }

    private fun clamp() {
        if (scale < fit) scale = fit
        if (pageSizes.size == 1 && scale <= fit) {
            tx = 0f
            ty = initialSinglePageY()
            return
        }
        val total = totalHeight()
        ty = if (total <= height) (height - total) / 2f else ty.coerceIn(height - total, 0f)
        val maxPageWidth = pageSizes.maxOfOrNull { it.x * scale } ?: width.toFloat()
        val minTx = min(0f, width - maxPageWidth)
        tx = tx.coerceIn(minTx, 0f)
    }

    private fun nearestVisiblePage(): Int {
        if (pageSizes.isEmpty()) return 0
        var y = ty
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in pageSizes.indices) {
            val center = y + pageSizes[i].y * scale / 2f
            val distance = abs(center - height / 2f)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
            y += pageSizes[i].y * scale + gap
        }
        return best
    }

    fun search(term: String): Int {
        val file = tempFile ?: return -1
        return try {
            PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper()
                for (page in 1..doc.numberOfPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    if (stripper.getText(doc).contains(term, true)) {
                        goToPage(page - 1)
                        return page - 1
                    }
                }
                -1
            }
        } catch (_: Exception) { -1 }
    }

    private fun goToPage(pageIndex: Int) {
        var y = 0f
        for (i in 0 until pageIndex.coerceAtMost(pageSizes.lastIndex)) {
            y += pageSizes[i].y * scale + gap
        }
        ty = -y
        clamp()
        requestRender(pageIndex.coerceIn(0, pageSizes.lastIndex), scale)
        invalidate()
    }

    fun infoText(uri: Uri?): String {
        val name = uri?.lastPathSegment ?: "PDF"
        val size = tempFile?.length() ?: 0L
        return "File: $name\nPages: ${pageSizes.size}\nSize: ${size / 1024} KB"
    }

    private fun reveal() {
        onActivityBar(true)
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, 2000)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideRunnable)
        renderGeneration.incrementAndGet()
        renderExecutor.shutdownNow()
        synchronized(rendererLock) {
            cachedBitmap?.recycle()
            cachedBitmap = null
            renderer?.close()
            pfd?.close()
            tempFile?.delete()
        }
        super.onDetachedFromWindow()
    }
}
