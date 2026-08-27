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
import kotlin.math.*

class PdfCanvasView(private val ctx: Context, private val onActivityBar: (Boolean) -> Unit): View(ctx) {
    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var tempFile: File? = null
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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hideRunnable = Runnable { onActivityBar(false) }
    private var cachedBitmap: Bitmap? = null
    private var cachedPage = -1
    private var cachedScale = 0f

    fun open(uri: Uri) {
        renderer?.close(); pfd?.close(); tempFile?.delete(); cachedBitmap?.recycle(); cachedBitmap = null
        tempFile = File.createTempFile("pdfmaster_", ".pdf", ctx.cacheDir)
        ctx.contentResolver.openInputStream(uri)!!.use { input -> FileOutputStream(tempFile!!).use { output -> input.copyTo(output) } }
        pfd = ParcelFileDescriptor.open(tempFile!!, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd!!)
        // The view is normally measured before the PDF is opened. Therefore
        // onSizeChanged cannot calculate the fit while renderer is still null.
        // Calculate it here so the first frame uses the full viewer width.
        fit = calculateFit()
        scale = fit
        tx = 0f
        ty = 0f
        cachedPage = -1
        cachedScale = 0f
        invalidate(); post { reveal() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (renderer != null) { fit = calculateFit(); scale = fit; tx = 0f; ty = initialSinglePageY(); clamp(); invalidate() }
    }

    // Always fit PDF pages to the full available viewer width.
    private fun calculateFit(): Float {
        val r = renderer ?: return 1f
        if (r.pageCount == 0 || width == 0) return 1f
        r.openPage(0).use { p -> return width.toFloat() / p.width.toFloat() }
    }

    private fun initialSinglePageY(): Float {
        val r = renderer ?: return 0f
        if (r.pageCount != 1 || width == 0) return 0f
        r.openPage(0).use { p ->
            val h = p.height * fit
            return if (h < height) (height - h) / 2f else 0f
        }
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c); c.drawColor(Color.rgb(235,235,235))
        val r = renderer ?: return
        if (fit <= 0f) fit = calculateFit()
        if (r.pageCount == 1 && scale <= fit) {
            r.openPage(0).use { page ->
                val w = width.toFloat()
                val h = page.height * fit
                renderCached(c, page, 0, 0f, initialSinglePageY(), w, h)
            }
            return
        }
        var y = ty
        for (i in 0 until r.pageCount) {
            r.openPage(i).use { page ->
                val w = page.width * scale; val h = page.height * scale
                val left = (width - w) / 2f + tx
                if (y + h >= 0 && y <= height) renderCached(c, page, i, left, y, w, h)
                y += h + gap
            }
        }
    }

    private fun renderCached(c: Canvas, page: PdfRenderer.Page, index: Int, left: Float, top: Float, w: Float, h: Float) {
        val maxSide = 2048f
        val bw = min(maxSide, max(1f, w)).roundToInt()
        val bh = min(maxSide, max(1f, h)).roundToInt()
        if (cachedBitmap == null || cachedPage != index || abs(cachedScale - scale) > 0.001f || cachedBitmap!!.width != bw || cachedBitmap!!.height != bh) {
            cachedBitmap?.recycle()
            cachedBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val bitmap = cachedBitmap
            bitmap?.eraseColor(Color.WHITE)
            bitmap?.let { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            cachedPage = index
            cachedScale = scale
        }
        val bitmap = cachedBitmap
        bitmap?.let { c.drawBitmap(it, null, RectF(left, top, left + w, top + h), paint) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX=e.x; lastY=e.y; reveal(); return true }
            MotionEvent.ACTION_POINTER_DOWN -> { oldDist=distance(e); if(oldDist>0f){pinchMidX=(e.getX(0)+e.getX(1))/2f; pinchMidY=(e.getY(0)+e.getY(1))/2f}; reveal(); return true }
            MotionEvent.ACTION_MOVE -> {
                reveal()
                if (e.pointerCount >= 2) {
                    val d=distance(e)
                    if(oldDist>0f && d>0f){
                        val newScale=(scale*(d/oldDist)).coerceIn(fit, fit*10f)
                        val k=newScale/scale
                        tx=pinchMidX+(tx-pinchMidX)*k; ty=pinchMidY+(ty-pinchMidY)*k
                        scale=newScale; oldDist=d; clamp(); invalidate()
                    }
                } else { tx+=e.x-lastX; ty+=e.y-lastY; lastX=e.x; lastY=e.y; clamp(); invalidate() }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> { oldDist=0f; return true }
            MotionEvent.ACTION_UP -> { oldDist=0f; clamp(); reveal(); return true }
            MotionEvent.ACTION_CANCEL -> { oldDist=0f; return true }
        }
        return true
    }

    private fun distance(e: MotionEvent): Float = if(e.pointerCount>=2) hypot((e.getX(0)-e.getX(1)).toDouble(),(e.getY(0)-e.getY(1)).toDouble()).toFloat() else 0f

    private fun totalHeight(): Float {
        val r=renderer?:return 0f
        var total=0f
        for(i in 0 until r.pageCount) r.openPage(i).use { total += it.height*scale }
        return total + gap*max(0,r.pageCount-1)
    }

    private fun clamp(){
        if(scale < fit) scale=fit
        if(renderer?.pageCount == 1 && scale <= fit){ tx=0f; ty=initialSinglePageY(); return }
        val total=totalHeight(); ty=ty.coerceIn(min(0f,height-total),0f)
        renderer?.let { r -> if(r.pageCount>0) r.openPage(0).use { p -> tx=tx.coerceIn(min(0f,width-p.width*scale),0f) } }
    }

    fun search(term: String): Int {
        val file=tempFile?:return -1
        return try { PDDocument.load(file).use { doc -> val stripper=PDFTextStripper(); for(page in 1..doc.numberOfPages){ stripper.startPage=page; stripper.endPage=page; if(stripper.getText(doc).contains(term,true)){goToPage(page-1); return page-1} }; -1 } } catch(_:Exception){-1}
    }

    private fun goToPage(pageIndex:Int){ val r=renderer?:return; var y=0f; for(i in 0 until pageIndex.coerceAtMost(r.pageCount-1)) r.openPage(i).use{y+=it.height*scale+gap}; ty=-y; clamp(); invalidate() }
    fun infoText(uri:Uri?):String { val r=renderer; val name=uri?.lastPathSegment?:"PDF"; val size=tempFile?.length()?:0L; return "File: $name\nPages: ${r?.pageCount?:0}\nSize: ${size/1024} KB" }
    private fun reveal(){onActivityBar(true);removeCallbacks(hideRunnable);postDelayed(hideRunnable,2000)}
    override fun onDetachedFromWindow(){removeCallbacks(hideRunnable);cachedBitmap?.recycle();renderer?.close();pfd?.close();tempFile?.delete();super.onDetachedFromWindow()}
}
