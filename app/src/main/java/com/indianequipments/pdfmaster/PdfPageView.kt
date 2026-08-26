package com.indianequipments.pdfmaster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.View

class PdfPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var bitmap: Bitmap? = null

    init {
        setBackgroundColor(Color.rgb(224, 224, 224))
    }

    fun showPage(renderer: PdfRenderer, pageIndex: Int) {
        val page = renderer.openPage(pageIndex)
        val width = maxOf(1, page.width)
        val height = maxOf(1, page.height)
        val targetWidth = maxOf(1, width)
        val targetHeight = maxOf(1, (height.toFloat() * targetWidth / width).toInt())
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        bitmap!!.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        val availableWidth = width.toFloat()
        val availableHeight = height.toFloat()
        val scale = minOf(availableWidth / image.width, availableHeight / image.height)
        val drawWidth = image.width * scale
        val drawHeight = image.height * scale
        val left = (availableWidth - drawWidth) / 2f
        val top = (availableHeight - drawHeight) / 2f
        canvas.drawBitmap(image, null, Rect(left.toInt(), top.toInt(), (left + drawWidth).toInt(), (top + drawHeight).toInt()), paint)
    }

    override fun onDetachedFromWindow() {
        bitmap?.recycle()
        bitmap = null
        super.onDetachedFromWindow()
    }
}
