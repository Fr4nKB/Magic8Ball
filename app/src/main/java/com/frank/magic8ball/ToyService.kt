package com.frank.magic8ball

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.content.edit
import com.frank.magic8ball.Constants.LAST_UNLOCK_NEGATIVE
import com.frank.magic8ball.Constants.LAST_UNLOCK_NEUTRAL
import com.frank.magic8ball.Constants.LAST_UNLOCK_POSITIVE
import com.frank.magic8ball.Constants.MATRIX_DIMENSION
import com.frank.magic8ball.Constants.NUM_UNLOCKS_NEGATIVE
import com.frank.magic8ball.Constants.NUM_UNLOCKS_NEUTRAL
import com.frank.magic8ball.Constants.NUM_UNLOCKS_POSITIVE
import com.frank.magic8ball.Constants.SHARED_PREF_KEY
import com.frank.magic8ball.Util.createFrameBuilderWithCenteredText
import com.frank.magic8ball.Util.createPreviewBitmap
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The main service through which we interact with the GDK.
 *
 * @author Fr4nKB
 */
class ToyService: Service() {
    private var mGM: GlyphMatrixManager? = null
    private var mCallback: GlyphMatrixManager.Callback? = null

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var shakingDetector: ShakingDetector
    private lateinit var orientationDetector: OrientationDetector

    private var isStarted = false
    private lateinit var neutralResponses: List<String>
    private lateinit var positiveResponses: List<String>
    private lateinit var negativeResponses: List<String>
    private lateinit var strongResponses: List<String>

    private val previewBitmap = createPreviewBitmap(MATRIX_DIMENSION)
    private var highlightedTokenIndex = 0
    private var currentResponse: String = ""

    private lateinit var soundEffectPlayer: SoundEffectPlayer
    private lateinit var vibratorManager: VibratorManager

    // getting a positive or negative response is influenced by the force applied to the shake
    // neutral responses always have the same probability
    private var probabilityThreshold = DEFAULT_PROBABILITY_THRESHOLD

    // to count the number of consecutive responses of the same category
    private var prevResponseCategory = -1
    private var consecutiveResponses = 0

    // keep track how many unlocks so far for each category
    private var numUnlocksNeutral: Int = 0
    private var numUnlocksPositive: Int = 0
    private var numUnlocksNegative: Int = 0

    // lists of all available unlocks
    private var neutralUnlocks: List<Int> = listOf(R.raw.emoji_neutral_1, R.raw.emoji_neutral_2, R.raw.emoji_neutral_3)
    private var positiveUnlocks: List<Int> = listOf(R.raw.emoji_positive_1, R.raw.emoji_positive_2, R.raw.emoji_positive_3)
    private var negativeUnlocks: List<Int> = listOf(R.raw.emoji_negative_1, R.raw.emoji_negative_2, R.raw.emoji_negative_3)

    companion object {
        private const val TAG = "Magic8Ball:ToyService"

        private const val LOW_BRIGHTNESS = 5
        private const val MAX_BRIGHTNESS = 255

        private const val MIN_PROBABILITY_THRESHOLD = 20
        private const val DEFAULT_PROBABILITY_THRESHOLD = 33
        private const val MAX_PROBABILITY_THRESHOLD = 47

        private const val SHAKE_FORCE_BOUNDARY = 14f
        private const val TOO_HIGH_FORCE_THRESHOLD = 35f

        private const val PITCH_THRESHOLD = 5.0

        private const val START_TIME_POSITIVE = "07:00:00"
        private const val END_TIME_POSITIVE = "18:00:00"

        private const val START_TIME_NEUTRAL = "01:00:00"
        private const val END_TIME_NEUTRAL = "18:00:00"

        private const val START_TIME_NEGATIVE = "03:00:00"
        private const val END_TIME_NEGATIVE = "00:00:00"
    }

    private fun isCurrentTimeInInterval(start: String, end: String): Boolean {
        val currentTime = ZonedDateTime.now(ZoneId.systemDefault()).toLocalTime()
        val startTime = LocalTime.parse(start)
        val endTime = LocalTime.parse(end)

        return if (endTime.isBefore(startTime)) {
            // Interval spans midnight
            currentTime.isAfter(startTime) || currentTime.isBefore(endTime)
        }
        else currentTime.isAfter(startTime) && currentTime.isBefore(endTime)
    }

    private fun unlockResponse(): Bitmap? {
        var lastUnlockDate = ""
        var numUnlocks: Int

        // check if event inside a valid time interval and retrieve last unlock time
        // for corresponding category
        when(prevResponseCategory) {
            0 -> {
                if(!isCurrentTimeInInterval(START_TIME_NEUTRAL, END_TIME_NEUTRAL)) return null

                lastUnlockDate = sharedPreferences.getString(LAST_UNLOCK_NEUTRAL, "") ?: ""
                numUnlocks = numUnlocksNeutral
            }
            1 -> {
                if(!isCurrentTimeInInterval(START_TIME_POSITIVE, END_TIME_POSITIVE)) return null

                lastUnlockDate = sharedPreferences.getString(LAST_UNLOCK_POSITIVE, "") ?: ""
                numUnlocks = numUnlocksPositive
            }
            2 -> {
                if(!isCurrentTimeInInterval(START_TIME_NEGATIVE, END_TIME_NEGATIVE)) return null

                lastUnlockDate = sharedPreferences.getString(LAST_UNLOCK_NEGATIVE, "") ?: ""
                numUnlocks = numUnlocksNegative
            }
            else -> return null
        }

        if(numUnlocks >= 3) return null

        // check if an unlock for this category was already made today
        val currentDate = ZonedDateTime.now(ZoneId.systemDefault()).toLocalDate()
        if(lastUnlockDate.isNotEmpty()) {
            try {
                if(LocalDate.parse(lastUnlockDate) == currentDate) return null
            }
            catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        numUnlocks += 1
        val unlocks: List<Int>
        when(prevResponseCategory) {
            0 -> {
                numUnlocksNeutral = numUnlocks
                sharedPreferences.edit { putInt(NUM_UNLOCKS_NEUTRAL, numUnlocks) }
                sharedPreferences.edit { putString(LAST_UNLOCK_NEUTRAL, currentDate.toString()) }
                unlocks = neutralUnlocks
            }
            1 -> {
                numUnlocksPositive = numUnlocks
                sharedPreferences.edit { putInt(NUM_UNLOCKS_POSITIVE, numUnlocks) }
                sharedPreferences.edit { putString(LAST_UNLOCK_POSITIVE, currentDate.toString()) }
                unlocks = positiveUnlocks
            }
            2 -> {
                numUnlocksNegative = numUnlocks
                sharedPreferences.edit { putInt(NUM_UNLOCKS_NEGATIVE, numUnlocks) }
                sharedPreferences.edit { putString(LAST_UNLOCK_NEGATIVE, currentDate.toString()) }
                unlocks = negativeUnlocks
            }
            else -> return null
        }

        return BitmapFactory.decodeResource(resources, unlocks[numUnlocks - 1])
    }

    private fun drawRndCategory(): Pair<Int, Bitmap?> {
        val rndIndex = Random.nextInt(1, 101)   // generate rnd number between 1 and 100

        // select response category based on set probabilities
        val category = when(rndIndex) {
            in 0..DEFAULT_PROBABILITY_THRESHOLD -> 0
            in DEFAULT_PROBABILITY_THRESHOLD..DEFAULT_PROBABILITY_THRESHOLD + probabilityThreshold -> 1
            else -> 2
        }

        // update consecutive category counter
        if(prevResponseCategory != category) {
            consecutiveResponses = 1
        }
        else consecutiveResponses += 1

        prevResponseCategory = category

        var bitmap: Bitmap? = null
        if(consecutiveResponses == 3) bitmap = unlockResponse()

        return Pair(category, bitmap)
    }

    private fun getRndResponse(category: Int): Any {
        // return random response from within the drawn list
        val numUnlocks: Int
        val responses: List<String>
        val unlocks: List<Int>

        when(category) {
            0 -> {
                responses = neutralResponses
                unlocks = neutralUnlocks
                numUnlocks = numUnlocksNeutral
            }
            1 -> {
                responses = positiveResponses
                unlocks = positiveUnlocks
                numUnlocks = numUnlocksPositive
            }
            else -> {
                responses = negativeResponses
                unlocks = negativeUnlocks
                numUnlocks = numUnlocksNegative
            }
        }

        val numResponses = responses.size
        val numTotalResponses = numResponses + numUnlocks
        val rndIndex = Random.nextInt(0, numTotalResponses)

        return if(rndIndex < numResponses) responses[rndIndex]
        else {
            val offset = numTotalResponses - rndIndex
            BitmapFactory.decodeResource(resources, unlocks[unlocks.size - offset])
        }
    }

    private fun updateProbabilities(avgForce: Double) {
        probabilityThreshold =
            if(avgForce > SHAKE_FORCE_BOUNDARY) {
                max(
                    DEFAULT_PROBABILITY_THRESHOLD - (avgForce - SHAKE_FORCE_BOUNDARY).toInt(),
                    MIN_PROBABILITY_THRESHOLD
                )
            }
            else {
                min(
                    DEFAULT_PROBABILITY_THRESHOLD + (SHAKE_FORCE_BOUNDARY - avgForce).toInt(),
                    MAX_PROBABILITY_THRESHOLD
                )
            }
    }

    private fun onShakeEnd(avgForce: Double) {
        shakingDetector.deactivate()    // temporarily deactivate shaking detection
        soundEffectPlayer.stopSound()   // stop sloshing sound effect
        vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))

        // get a random response, adjust probabilities based on the avg force if needed
        currentResponse =
            if(avgForce >= TOO_HIGH_FORCE_THRESHOLD) strongResponses.random()
            else {
                updateProbabilities(avgForce)

                val (rndCategory, newUnlockBitmap) = drawRndCategory()
                if(newUnlockBitmap != null) {
                    drawBitmap(newUnlockBitmap)
                    return
                }

                val response = getRndResponse(rndCategory)
                when(response) {
                    is String -> response
                    is Bitmap -> {
                        drawBitmap(response)
                        return
                    }
                    else -> return
                }
            }

        highlightedTokenIndex = currentResponse.split(" ").size / 2
        drawResponse(currentResponse)
        orientationDetector.activate()
    }

    private fun highLightToken(pitch: Double) {
        val numTokens = currentResponse.split(" ").size
        val previousIndex = highlightedTokenIndex

        // change which token is highlighted based on pitch angle
        highlightedTokenIndex =
            if(pitch > PITCH_THRESHOLD) max(highlightedTokenIndex - 1, 0)
            else if(pitch < -PITCH_THRESHOLD)  min(highlightedTokenIndex + 1, numTokens - 1)
            else {
                when(numTokens) {
                    2 -> highlightedTokenIndex
                    3 -> 1
                    else -> 0
                }
            }

        if(highlightedTokenIndex != previousIndex) {
            vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            drawResponse(currentResponse)
        }
    }

    override fun onCreate() {
        super.onCreate()

        sharedPreferences = getSharedPreferences(SHARED_PREF_KEY, MODE_PRIVATE)

        numUnlocksNeutral = sharedPreferences.getInt(NUM_UNLOCKS_NEUTRAL, 0)
        numUnlocksPositive = sharedPreferences.getInt(NUM_UNLOCKS_POSITIVE, 0)
        numUnlocksNegative = sharedPreferences.getInt(NUM_UNLOCKS_NEGATIVE, 0)

        positiveResponses = resources.getStringArray(R.array.positive_responses).toList()
        neutralResponses = resources.getStringArray(R.array.neutral_responses).toList()
        negativeResponses = resources.getStringArray(R.array.negative_responses).toList()
        strongResponses = resources.getStringArray(R.array.strong_responses).toList()

        soundEffectPlayer = SoundEffectPlayer(this, listOf(R.raw.slosh_1,R.raw.slosh_2,R.raw.slosh_3) , SHAKE_FORCE_BOUNDARY)
        vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }

    override fun onBind(intent: Intent?): IBinder {
        initGDK()

        shakingDetector = ShakingDetector(
            applicationContext,
            onShakeStart = {
                soundEffectPlayer.startSound()
            },
            onShake = { intensity ->
                soundEffectPlayer.adjustSound(intensity.toFloat())
            },
            onShakeReset = {
                soundEffectPlayer.stopSound()
            },
            onShakeEnd = { avgForce ->
                onShakeEnd(avgForce)
            }
        ).apply {
            register()
        }

        orientationDetector = OrientationDetector(
            applicationContext,
            onOrientationChange = { pitch ->
                highLightToken(pitch)
            }
        ).apply {
            register()
        }

        return serviceMessenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isStarted = false

        mGM?.turnOff()
        mGM?.unInit()
        mGM = null
        mCallback = null

        shakingDetector.unregister(applicationContext)
        orientationDetector.unregister(applicationContext)
        soundEffectPlayer.stopSound()
        return false
    }

    private fun initGDK() {
        mGM = GlyphMatrixManager.getInstance(applicationContext)
        mCallback = object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(componentName: ComponentName) {
                mGM?.register(Glyph.DEVICE_23112)
                drawBitmap(previewBitmap)
            }

            override fun onServiceDisconnected(componentName: ComponentName) {}
        }
        mGM?.init(mCallback)
    }

    private fun drawBitmap(bitmap: Bitmap) {
        val obj = GlyphMatrixObject.Builder()
            .setImageSource(bitmap)
            .setPosition(0, 0)
            .build()

        val frame = GlyphMatrixFrame.Builder().addTop(obj).build(applicationContext)
        mGM?.setMatrixFrame(frame.render())
    }

    private fun drawResponse(response: String) {
        val frameBuilder = createFrameBuilderWithCenteredText(response, highlightedTokenIndex, LOW_BRIGHTNESS, MAX_BRIGHTNESS)
        val frame = frameBuilder.build(applicationContext)
        mGM?.setMatrixFrame(frame.render())
    }

    private fun onLongPress() {
        if(!isStarted) {
            isStarted = true

            consecutiveResponses = 0
            prevResponseCategory = -1

            val frameBuilder = createFrameBuilderWithCenteredText(getString(R.string.shake))
            val frame = frameBuilder.build(applicationContext)

            try {
                mGM?.setMatrixFrame(frame.render())
            }
            catch(e: Exception) {
                e.printStackTrace()
            }
        }
        else {
            mGM?.turnOff()
        }

        shakingDetector.activate()
        orientationDetector.deactivate()
    }

    private val serviceHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val bundle: Bundle = msg.getData()

            when(msg.what) {
                GlyphToy.MSG_GLYPH_TOY -> {
                    val event = bundle.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
                    if(GlyphToy.EVENT_CHANGE == event) onLongPress()
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val serviceMessenger: Messenger = Messenger(serviceHandler)
}