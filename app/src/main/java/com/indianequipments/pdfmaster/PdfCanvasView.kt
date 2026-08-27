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

    fun open(uri: Uri) {
        renderer?.close(); pfd?.close(); tempFile?.delete()
        tempFile = File.createTempFile("pdfmaster_", ".pdf", ctx.cacheDir)
        ctx.contentResolver.openInputStream(uri)!!.use { input -> FileOutputStream(tempFile!!).use { output -> input.copyTo(output) } }
        pfd = ParcelFileDescriptor.open(tempFile!!, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd!!)
        scale = 1f; fit = 1f; tx = 0f; ty = 0f
        invalidate(); post { reveal() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (renderer != null) { fit = calculateFit(); if (scale < fit) scale = fit; clamp() }
    }

    private fun calculateFit(): Float {
        val r = renderer ?: return 1f
        if (r.pageCount == 0 || width == 0 || height == 0) return 1f
        r.openPage(0).use { p -> return min(width.toFloat() / p.width, height.toFloat() / p.height) }
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c); c.drawColor(Color.rgb(235,235,235))
        val r = renderer ?: return
        if (fit <= 0f) fit = calculateFit()
        var y = ty
        for (i in 0 until r.pageCount) {
            r.openPage(i).use { page ->
                val w = page.width * scale; val h = page.height * scale
                val left = (width - w) / 2f + tx
                if (y + h >= 0 && y <= height) renderPage(c, page, left, y, w, h)
                y += h + gap
            }
        }
    }

    private fun renderPage(c: Canvas, page: PdfRenderer.Page, left: Float, top: Float, w: Float, h: Float) {
        val maxSide = 2048f
        val bw = min(maxSide, max(1f, w)).roundToInt()
        val bh = min(maxSide, max(1f, h)).roundToInt()
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        c.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), paint)
        bitmap.recycle()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX=e.x; lastY=e.y; reveal(); return true }
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist=distance(e)
                if(oldDist>0f){ pinchMidX=(e.getX(0)+e.getX(1))/2f; pinchMidY=(e.getY(0)+e.getY(1))/2f }
                reveal(); return true
            }
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
                } else {
                    tx += e.x-lastX; ty += e.y-lastY; lastX=e.x; lastY=e.y; clamp(); invalidate()
                }
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
        val total=totalHeight(); val minY=min(0f,height-total)
        ty=ty.coerceIn(minY,0f); if(scale <= fit) tx=0f
    }

    fun search(term: String): Int {
        val file = tempFile ?: return -1
        return try {
            PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper()
                for (page in 1..doc.numberOfPages) {
                    stripper.startPage = page; stripper.endPage = page
                    if (stripper.getText(doc).contains(term, ignoreCase = true)) {
                        goToPage(page - 1); return page - 1
                    }
                }
            }
            -1
        } catch (_: Exception) { -1 }
    }

    private fun goToPage(pageIndex: Int) {
        val r = renderer ?: return
        var y = 0f
        for (i in 0 until pageIndex.coerceAtMost(r.pageCount - 1)) r.openPage(i).use { y += it.height * scale + gap }
        ty = -y; clamp(); invalidate()
    }

    fun infoText(uri: Uri?): String {
        val r = renderer
        val name = uri?.lastPathSegment ?: "PDF"
        val size = tempFile?.length() ?: 0L
        return "File: $name\nPages: ${r?.pageCount ?: 0}\nSize: ${size / 1024} KB"
    }

    private fun reveal(){
        onActivityBar(true); removeCallbacks(hideRunnable); postDelayed(hideRunnable,2000)
    }

    override fun onDetachedFromWindow(){
        removeCallbacks(hideRunnable); renderer?.close(); pfd?.close(); tempFile?.delete(); super.onDetachedFromWindow()
    }
}
