package com.frank.magic8ball

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

object Util {
    // used to center text vertically
    fun rowCoordinates(numTokens: Int): List<Int> {
        return when(numTokens) {
            1 -> listOf(9)
            2 -> listOf(6, 12)
            3 -> listOf(3, 9, 15)
            else -> emptyList()
        }
    }

    // used to center text horizontally
    fun colCoordinates(tokens: List<String>): List<Int> {
        // each letter is 4px wide on avg and each letter is spaced by 1 px
        // decrease from middle point by 5px for each letter
        val letterWidth = 5

        val middlePoints = when(tokens.size) {
            1 -> listOf(12)
            2 -> listOf(13, 13)
            3 -> listOf(14, 12, 14)
            else -> emptyList()
        }

        return tokens.mapIndexed { index, token ->
            middlePoints[index] - ((token.toList().count() / 2.0) * letterWidth).roundToInt()
        }
    }

    fun createPreviewBitmap(dimension: Int): Bitmap {
        val bitmap = createBitmap(dimension, dimension)
        val canvas = Canvas(bitmap)

        val center = dimension / 2f

        val gradientPaint = Paint().apply {
            shader = RadialGradient(
                center,
                center,
                center, // radius
                Color.GRAY, // start color (center)
                Color.BLACK, // end color (edges)
                Shader.TileMode.CLAMP // avoid repeating patterns
            )
        }

        val blackPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        canvas.drawCircle(center, center, center, gradientPaint)

        // draw an 8, two black circles and two other inner white circle
        canvas.drawCircle(center, center - center / 2 + 3, center / 2 - 3, blackPaint)
        canvas.drawCircle(center, center + center / 2 - 3, center / 2 - 3, blackPaint)

        return bitmap
    }
}