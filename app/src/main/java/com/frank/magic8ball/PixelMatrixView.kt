package com.frank.magic8ball


import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class PixelMatrixView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var pixels: IntArray = IntArray(625) { 0 }
    private val paint = Paint()

    fun setPixels(newPixels: IntArray) {
        if (newPixels.size == 625) {
            pixels = newPixels
            invalidate()
        }
    }

    val pixPerRows = intArrayOf(7, 11, 15, 17, 19, 21, 21, 23, 23, 25, 25, 25, 25, 25, 25, 25, 23, 23, 21, 21, 19, 17, 15, 11, 7)

    fun isDisplayed(row : Int, col : Int) : Boolean {
        val pixOnRow = pixPerRows[row]
        val min = 12 - (pixOnRow - 1) / 2
        val max = 12 + (pixOnRow - 1) / 2
        val isDisplayed = col in min..max
        return isDisplayed
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cols = 25
        val rows = 25
        val padding = 2f

        val cellSize = minOf(width / cols.toFloat(), height / rows.toFloat())

        for (i in pixels.indices) {
            val value = pixels[i].coerceIn(0, 255)
            paint.color = Color.rgb(value, value, value)

            val col = i % cols
            val row = i / cols

            val left = col * cellSize + padding / 2
            val top = row * cellSize + padding / 2
            val right = (col + 1) * cellSize - padding / 2
            val bottom = (row + 1) * cellSize - padding / 2
            if (isDisplayed(row, col))
                canvas.drawRect(left, top, right, bottom, paint)
        }
    }

}
