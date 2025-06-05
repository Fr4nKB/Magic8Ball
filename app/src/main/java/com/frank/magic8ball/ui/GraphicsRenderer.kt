package com.frank.magic8ball.ui

import android.content.Context
import android.graphics.Bitmap
import com.frank.magic8ball.Constants.MATRIX_DIMENSION
import com.frank.magic8ball.Constants.MAX_CHANNEL_BRIGHTNESS
import com.frank.magic8ball.Constants.MAX_OBJECT_BRIGHTNESS
import com.frank.magic8ball.util.SynchronizedFlag
import com.frank.magic8ball.util.Util.calculateHorizontalOffset
import com.frank.magic8ball.util.Util.calculateVerticalOffset
import com.frank.magic8ball.util.Util.createFrameBuilderWithCenteredText
import com.frank.magic8ball.util.Util.createLoadingAnimationFrame
import com.frank.magic8ball.util.Util.getRingIndexes
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

class GraphicsRenderer(
    private val context: Context,
    private val mGM: GlyphMatrixManager? = null
) {
    private var isLoadingAnimShowing by SynchronizedFlag(false)

    private var animationJob: Job? = null
    private var loadingAnimationFrames: MutableList<Bitmap> = mutableListOf()
    private val radius = MATRIX_DIMENSION / 2
    private var gradientIndexes = listOf(
        getRingIndexes(MATRIX_DIMENSION, radius - 2),
        getRingIndexes(MATRIX_DIMENSION, radius - 1),
        getRingIndexes(MATRIX_DIMENSION, radius)
    )

    private var currentLoadingAnimationFrame: IntArray = IntArray(0) { MATRIX_DIMENSION * MATRIX_DIMENSION }
    var currentResponse: String = ""

    var offsetX: Int = 0
    var offsetY: Int = 0

    companion object {
        private const val LOW_BRIGHTNESS = 10
    }

    init {
        // create the load animation frames once so they can be reused
        repeat(20) {
            loadingAnimationFrames.add(
                createLoadingAnimationFrame(MATRIX_DIMENSION)
            )
        }
    }

    /**
     * Applies a gradient to the outer borders of the matrix frame to give the impression of a sphere
     * */
    private fun applyGradient(channels: IntArray): IntArray {
        gradientIndexes[0].forEach {
            if(channels[it] != 0) {
                channels[it] = (channels[it] * 0.66).roundToInt().coerceAtLeast(LOW_BRIGHTNESS)
            }
        }

        gradientIndexes[1].forEach {
            if(channels[it] != 0) {
                channels[it] = (channels[it] * 0.33).roundToInt().coerceAtLeast(1)
            }
        }

        gradientIndexes[2].forEach {
            if(channels[it] != 0) {
                channels[it] = (channels[it] * 0.05).roundToInt().coerceAtLeast(1)
            }
        }

        return channels
    }

    private fun renderLoadingAnimationFrame(frame: Bitmap, brightness: Int): IntArray {
        val obj = GlyphMatrixObject.Builder()
            .setImageSource(frame)
            .setPosition(0, 0)
            .setBrightness(brightness)
            .build()

        return GlyphMatrixFrame.Builder().addTop(obj).build(context).render()
    }

    fun drawLoadingAnimation(onEnd: () -> Unit) {
        isLoadingAnimShowing = true
        val maxFrameDurationMs = 200L
        val minFrameDurationMs = 160L
        val percentages = listOf(0.0, 0.25, 0.5, 0.75, 1.0)

        val numPercentages = percentages.size
        val numFrames = loadingAnimationFrames.size

        animationJob?.cancel()
        animationJob = CoroutineScope(Dispatchers.Main).launch {
            val halfFrames = numFrames / 2
            var halfIndex = 0
            var reverse = false
            currentLoadingAnimationFrame.fill(0)

            // applies a fade-in and fade-out effects
            // starting and ending frames are shown for a longer period
            // interpolation is applied between frames
            loadingAnimationFrames.forEach { frame ->
                val brightnessFactor = ((halfIndex + 1) / halfFrames.toDouble()).pow(1.5)
                val delayFactor = ((halfFrames - halfIndex) / halfFrames.toDouble()).pow(0.5)

                if(!reverse && halfIndex == halfFrames - 1) {
                    reverse = true
                    halfIndex = halfFrames  // keep brightness and delay equal for two middle frames
                }

                if(reverse) halfIndex -= 1
                else halfIndex += 1

                val delayPerInterpolatedFrame = (maxFrameDurationMs * delayFactor)
                    .toLong()
                    .coerceAtLeast(minFrameDurationMs) / numPercentages

                // create next frame and interpolate with current one
                renderLoadingAnimationFrame(frame, (MAX_OBJECT_BRIGHTNESS * brightnessFactor).toInt()).also { nextFrame ->
                    percentages.map { percentage ->
                        IntArray(currentLoadingAnimationFrame.size) { i ->
                            ((currentLoadingAnimationFrame[i] * (1 - percentage)) + (nextFrame[i] * percentage)).toInt()
                        }
                    }.forEach { newFrame ->
                        mGM?.setMatrixFrame(newFrame)
                        delay(delayPerInterpolatedFrame)
                    }

                    currentLoadingAnimationFrame = nextFrame
                }
            }

            isLoadingAnimShowing = false
            onEnd()
        }

    }

    fun cancelAnimation() {
        animationJob?.let {
            if(it.isActive) it.cancel()
        }
    }

    fun drawPreview() {
        val channels = GlyphMatrixFrame.Builder().build(context).render()

        // small white circle at the center
        getRingIndexes(25, 6, true, 0.5).forEach {
            channels[it] = MAX_CHANNEL_BRIGHTNESS
        }

        // eight shape
        val eight = listOf(236, 237, 238, 311, 312, 313, 386, 387, 388, 260, 264, 285, 289, 335, 339, 360, 364)
        eight.forEach {
            channels[it] = 0
        }

        mGM?.setMatrixFrame(channels)
    }

    private fun drawBitmap(bitmap: Bitmap, brightness: Int = MAX_OBJECT_BRIGHTNESS, gradient: Boolean = true) {
        val obj = GlyphMatrixObject.Builder()
            .setImageSource(bitmap)
            .setPosition(0, 0)
            .setBrightness(brightness)
            .build()

        val frame = GlyphMatrixFrame.Builder().addTop(obj).build(context).render()
        val frameWithGradient = if(gradient) applyGradient(frame) else frame
        mGM?.setMatrixFrame(frameWithGradient)
    }

    /**
     * Draws the given bitmap while gradually increasing the brightness
     * */
    fun drawBitmapWithFadeIn(bitmap: Bitmap) {
        val numFrames = 25

        animationJob?.cancel()
        animationJob = CoroutineScope(Dispatchers.Main).launch {
            for(i in 0 until numFrames) {
                val brightnessFactor = ((i + 1) / numFrames.toDouble()).pow(2)
                drawBitmap(bitmap, (MAX_OBJECT_BRIGHTNESS * brightnessFactor).toInt())
                delay(20L)
            }
        }
    }

    fun drawResponse() {
        if(currentResponse.isEmpty() || animationJob?.isActive == true) return

        val frameBuilder = createFrameBuilderWithCenteredText(
            currentResponse,
            MAX_OBJECT_BRIGHTNESS,
            offsetX,
            offsetY
        )

        val frame = frameBuilder.build(context).render()
        val frameWithGradient = applyGradient(frame)
        mGM?.setMatrixFrame(frameWithGradient)
    }

    fun drawResponseWithFadeIn() {
        if(currentResponse.isEmpty()) return

        val numFrames = 25

        animationJob?.cancel()
        animationJob = CoroutineScope(Dispatchers.Main).launch {
            for(i in 0 until numFrames) {
                val brightnessFactor = ((i + 1) / numFrames.toDouble()).pow(2)

                val frameBuilder = createFrameBuilderWithCenteredText(
                    currentResponse,
                    (MAX_OBJECT_BRIGHTNESS * brightnessFactor).toInt(),
                    offsetX,
                    offsetY
                )

                val frame = frameBuilder.build(context).render()
                val frameWithGradient = applyGradient(frame)
                mGM?.setMatrixFrame(frameWithGradient)

                delay(20L)
            }
        }
    }

    /**
     * Shifts the response by some pixels based on the pitch and roll angle of the phone
     *
     * @param pitch The current pitch value.
     * @param roll The current roll value.
     * */
    fun updateOffsets(pitch: Double, roll: Double): Boolean {
        // Calculate new offsets based on the current pitch and roll
        val targetOffsetX = calculateHorizontalOffset(
            roll,
            MATRIX_DIMENSION
        )
        val targetOffsetY = calculateVerticalOffset(
            pitch,
            MATRIX_DIMENSION
        )

        var isNowCentered = false

        // Only trigger feedback and redrawing if the offsets have changed
        if(!isLoadingAnimShowing && (targetOffsetX != offsetX || targetOffsetY != offsetY)) {
            offsetX = targetOffsetX
            offsetY = targetOffsetY
            isNowCentered = (offsetX == 0 && offsetY == 0)
            drawResponse()
        }
        else {
            offsetX = targetOffsetX
            offsetY = targetOffsetY
        }

        return isNowCentered
    }
}