package com.frank.magic8ball

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixObject
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

object Util {
    // used to center text vertically
    private fun rowCoordinates(numTokens: Int): List<Int> {
        return when(numTokens) {
            1 -> listOf(9)
            2 -> listOf(6, 12)
            3 -> listOf(3, 9, 15)
            else -> emptyList()
        }
    }

    // used to center text horizontally
    private fun colCoordinates(tokens: List<String>): List<Int> {
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
            val halfWordDimPx = ((token.toList().count() / 2.0) * letterWidth).roundToInt()
            (middlePoints[index] - halfWordDimPx).coerceAtLeast(0)
        }
    }

    fun createLoadingResponseBitmap(dimension: Int, boundary: Float = 0.4f): Bitmap {
        val bitmap = createBitmap(dimension, dimension)
        val canvas = Canvas(bitmap)
        val center = dimension / 2f

        val gradientPaint = Paint().apply {
            shader = RadialGradient(
                center, center,
                dimension.toFloat(), // radius
                intArrayOf(Color.BLACK, Color.BLACK, Color.WHITE), // black starts from center, stay black, finishes with white
                floatArrayOf(0f, boundary, 1f), // black until boundary (%) then starts becoming white
                Shader.TileMode.CLAMP // avoid repeating patterns
            )
        }

        canvas.drawCircle(center, center, center, gradientPaint)
        return bitmap
    }

    fun createPreviewBitmap(dimension: Int): Bitmap {
        val bitmap = createBitmap(dimension, dimension)
        val canvas = Canvas(bitmap)

        val center = dimension / 2f

        val gradientPaint = Paint().apply {
            shader = RadialGradient(
                center,
                center,
                dimension.toFloat(), // radius
                intArrayOf(Color.WHITE, Color.WHITE, Color.BLACK), // white starts from center, stay white, finishes with black
                floatArrayOf(0f, 0.35f, 1f), // white until 35% of the way then starts becoming black
                Shader.TileMode.CLAMP // avoid repeating patterns
            )
        }

        val blackPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = false
            isFilterBitmap = false
        }

        canvas.drawCircle(center, center, center, gradientPaint)

        // draw an 8, two black circles with a hole in it
        canvas.drawCircle(center, center - center / 2 + 3, center / 2 - 3.05f, blackPaint)
        canvas.drawCircle(center, center + center / 2 - 3, center / 2 - 3.05f, blackPaint)

        return bitmap
    }

    /**
     * Given a string, creates a GlyphMatrixFrame.Builder object containing each word in the string
     * centered vertically and horizontally.
     *
     * @param text the string to display
     * @param highlightedTokenIndex the word to highlight, -1 all words should be highlighted
     * @param lowBrightness the brightness level for non-highlighted words (default 5)
     * @param maxBrightness the brightness level for the highlighted word (default 255)
     * @param offsetX the offset to apply to the x coordinate of the object (default 0)
     * @param offsetY the offset to apply to the y coordinate of the object (default 0)
     *
     * @return GlyphMatrixFrame.Builder object containing the centered and optionally highlighted string
     *
     * @author Fr4nKB
     */
    fun createFrameBuilderWithCenteredText(
        text: String,
        highlightedTokenIndex: Int = -1,
        lowBrightness: Int = 5,
        maxBrightness: Int = 255,
        offsetX: Int = 0,
        offsetY: Int = 0
    ): GlyphMatrixFrame.Builder {
        val tokens = text.split(" ")
        val numTokens = tokens.size

        // get tokens' coordinates in order to be centered
        // based on number of tokens and single token size
        val rowCoordinates = rowCoordinates(numTokens)
        val colCoordinates = colCoordinates(tokens)

        val frameBuilder = GlyphMatrixFrame.Builder()

        tokens.forEachIndexed { index, token ->
            val brightness =
                if(highlightedTokenIndex == -1 || index == highlightedTokenIndex) maxBrightness
                else lowBrightness

            val obj = GlyphMatrixObject.Builder()
                .setText(token)
                .setPosition(
                    colCoordinates[index] + offsetX,
                    rowCoordinates[index] + offsetY
                )
                .setBrightness(brightness)
                .build()

            // each word is drawn on a different level
            when(index) {
                0 -> frameBuilder.addTop(obj)
                1 -> frameBuilder.addMid(obj)
                2 -> frameBuilder.addLow(obj)
            }
        }

        return frameBuilder
    }

    fun isCurrentTimeInInterval(start: String, end: String): Boolean {
        val currentTime = ZonedDateTime.now(ZoneId.systemDefault()).toLocalTime()
        val startTime = LocalTime.parse(start)
        val endTime = LocalTime.parse(end)

        return if (endTime.isBefore(startTime)) {
            // interval spans midnight
            currentTime.isAfter(startTime) || currentTime.isBefore(endTime)
        }
        else currentTime.isAfter(startTime) && currentTime.isBefore(endTime)
    }
}