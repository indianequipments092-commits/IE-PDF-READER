package com.indianequipments.pdfmaster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class PdfPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var bitmap: Bitmap? = null
    private var baseScale = 1f
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragging = false

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldZoom = zoom
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 4f)
            if (zoom != oldZoom) {
                val focusX = detector.focusX
                val focusY = detector.focusY
                offsetX = focusX - (focusX - offsetX) * (zoom / oldZoom)
                offsetY = focusY - (focusY - offsetY) * (zoom / oldZoom)
                clampOffsets()
                invalidate()
            }
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val oldZoom = zoom
            zoom = if (zoom <= 1.05f) 2f else 1f
            if (zoom == 1f) {
                offsetX = 0f
                offsetY = 0f
            } else {
                offsetX = e.x - (e.x - offsetX) * (zoom / oldZoom)
                offsetY = e.y - (e.y - offsetY) * (zoom / oldZoom)
                clampOffsets()
            }
            invalidate()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (zoom > 1.001f) {
                offsetX -= distanceX
                offsetY -= distanceY
                clampOffsets()
                invalidate()
                return true
            }
            return false
        }
    })

    init {
        setBackgroundColor(Color.rgb(224, 224, 224))
        isClickable = true
    }

    fun showPage(renderer: PdfRenderer, pageIndex: Int) {
        val page = renderer.openPage(pageIndex)
        val pageWidth = max(1, page.width)
        val pageHeight = max(1, page.height)
        val targetWidth = max(1, width.takeIf { it > 0 } ?: pageWidth * 2)
        val renderScale = (targetWidth.toFloat() / pageWidth).coerceIn(1f, 3f)
        val targetHeight = max(1, (pageHeight * renderScale).toInt())

        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        bitmap!!.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        resetToFit()
        invalidate()
    }

    private fun resetToFit() {
        val image = bitmap ?: return
        val availableWidth = width.toFloat().coerceAtLeast(1f)
        val availableHeight = height.toFloat().coerceAtLeast(1f)
        baseScale = min(availableWidth / image.width, availableHeight / image.height)
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
        clampOffsets()
    }

    private fun clampOffsets() {
        val image = bitmap ?: return
        val scale = baseScale * zoom
        val drawWidth = image.width * scale
        val drawHeight = image.height * scale
        val maxX = max(0f, (drawWidth - width) / 2f)
        val maxY = max(0f, (drawHeight - height) / 2f)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    fun resetZoom() {
        resetToFit()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                dragging = zoom > 1.001f
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && dragging && event.pointerCount == 1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    // GestureDetector also receives the event; manual movement is used only
                    // as the direct drag path to keep the PDF responsive on slow devices.
                    if (dx != 0f || dy != 0f) {
                        offsetX += dx
                        offsetY += dy
                        clampOffsets()
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 || oldh == 0) resetToFit()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        val scale = baseScale * zoom
        val drawWidth = image.width * scale
        val drawHeight = image.height * scale
        val left = (width - drawWidth) / 2f + offsetX
        val top = (height - drawHeight) / 2f + offsetY
        canvas.drawBitmap(image, null, RectF(left, top, left + drawWidth, top + drawHeight), paint)
    }

    override fun onDetachedFromWindow() {
        bitmap?.recycle()
        bitmap = null
        super.onDetachedFromWindow()
    }
}
