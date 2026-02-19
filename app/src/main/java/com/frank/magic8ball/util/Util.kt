package com.frank.magic8ball.util

import android.content.Context
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixObject
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import java.io.BufferedReader
import java.io.InputStreamReader

object Util {
    private val specialCharDimensions: Map<Char, Int> = mapOf(
        'i' to 3,
        'm' to 5,
        'n' to 5,
        't' to 3,
        'v' to 3,
        'w' to 5
    )

    // used to center text vertically
    private fun rowCoordinates(numTokens: Int): List<Int> {
        return when(numTokens) {
            1 -> listOf(9)
            2 -> listOf(6, 12)
            3 -> listOf(3, 9, 15)
            else -> emptyList()
        }
    }

    private fun getTokenWidthPixels(token: String): Int {
        var totalWidth = 0
        token.toList().forEachIndexed { index, char ->
            // char width default to 4 pixels
            totalWidth += specialCharDimensions.getOrDefault(char.lowercaseChar(), 4)
            if(index < token.lastIndex) totalWidth += 1 // account for spacing between chars
        }

        return totalWidth
    }

    // used to center text horizontally
    private fun colCoordinates(tokens: List<String>): List<Int> {
        // each letter is 4px wide with some exceptions and each letter is spaced by 1 px

        val numWords = tokens.size

        return tokens.mapIndexed { _, token ->
            val isNumCharsEven = token.toList().size % 2 == 0

            val middlePoint = when(numWords) {
                1 -> 12
                2 -> {
                    if(isNumCharsEven) 12
                    else 13
                }
                3 -> {
                    if(isNumCharsEven) 12
                    else 13
                }
                else -> 0
            }

            val tokenWidth = getTokenWidthPixels(token)
            val halfWordDimPx =
                if(isNumCharsEven) floor(tokenWidth / 2.0).toInt()
                else (tokenWidth / 2.0).roundToInt()

            (middlePoint - halfWordDimPx).coerceAtLeast(0)
        }
    }

    fun getRingIndexes(matrixDim: Int, radius: Int, inclusive: Boolean = false, tolerance: Double = 0.5): List<Int> {
        val indexes = mutableListOf<Int>()
        val center = matrixDim / 2
        val range = listOf(
            (radius - tolerance).pow(2),
            (radius + tolerance).pow(2)
        )

        for(i in 0..matrixDim) {
            for(j in 0..matrixDim) {
                val distSquared = ((j - center).toDouble().pow(2) + (i - center).toDouble().pow(2))
                if((inclusive && distSquared <= range[1]) || (!inclusive && distSquared in range[0]..range[1])) {
                    indexes.add(i * matrixDim + j)
                }
            }
        }

        return indexes
    }

    fun loadCsv(context: Context, resId: Int): List<IntArray> {
        val inputStream = context.resources.openRawResource(resId)
        return BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.lineSequence()
                .map { line -> line.split(",").map { it.trim().toInt() }.toIntArray() }
                .toList()
        }
    }

    /**
     * Given a string, creates a GlyphMatrixFrame.Builder object containing each word in the string
     * centered vertically and horizontally.
     *
     * @param text the string to display
     * @param brightness the brightness for each object (default 255)
     * @param offsetX the offset to apply to the x coordinate of the object (default 0)
     * @param offsetY the offset to apply to the y coordinate of the object (default 0)
     *
     * @return GlyphMatrixFrame.Builder object containing the centered and optionally highlighted string
     *
     * @author Fr4nKB
     */
    fun createFrameBuilderWithCenteredText(
        text: String,
        brightness: Int = 255,
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

    /**
     * Calculates the horizontal offset based on the roll value.
     *
     * @param roll The current roll value, representing the tilt or rotation.
     * @param matrixDim The number of pixel across the matrix.
     * @return An integer representing the computed horizontal offset.
     */
    fun calculateHorizontalOffset(roll: Double, matrixDim: Int): Int {
        val absRoll = abs(roll)
        val baseOffset = (absRoll / 90.0 * matrixDim).roundToInt()
        return if(roll > 0) -baseOffset else baseOffset
    }

    /**
     * Calculates the vertical offset based on the pitch value.
     *
     * @param pitch The current pitch value, representing the vertical tilt or inclination.
     * @param matrixDim The number of pixel across the matrix.
     * @return An integer representing the computed vertical offset.
     */
    fun calculateVerticalOffset(pitch: Double, matrixDim: Int): Int {
        val absPitch = abs(pitch)
        val baseOffset = (absPitch / 90.0 * matrixDim).roundToInt()
        return if(pitch < 0) -baseOffset else baseOffset
    }
}