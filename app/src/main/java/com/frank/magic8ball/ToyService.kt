package com.frank.magic8ball

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
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
import android.os.UserManager
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.frank.magic8ball.Constants.ALL_UNLOCKS_DONE
import com.frank.magic8ball.Constants.LAST_UNLOCK_NEGATIVE
import com.frank.magic8ball.Constants.LAST_UNLOCK_NEUTRAL
import com.frank.magic8ball.Constants.LAST_UNLOCK_POSITIVE
import com.frank.magic8ball.Constants.NUM_UNLOCKS_NEGATIVE
import com.frank.magic8ball.Constants.NUM_UNLOCKS_NEUTRAL
import com.frank.magic8ball.Constants.NUM_UNLOCKS_POSITIVE
import com.frank.magic8ball.Constants.SHARED_PREF_KEY
import com.frank.magic8ball.detectors.OrientationDetector
import com.frank.magic8ball.detectors.ShakingDetector
import com.frank.magic8ball.ui.GraphicsRenderer
import com.frank.magic8ball.ui.SoundEffectPlayer
import com.frank.magic8ball.util.Util.isCurrentTimeInInterval
import com.nothing.ketchum.Glyph.DEVICE_23112
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import java.time.LocalDate
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
    private var glyphMatrixManager: GlyphMatrixManager? = null

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var shakingDetector: ShakingDetector
    private lateinit var orientationDetector: OrientationDetector
    private lateinit var soundEffectPlayer: SoundEffectPlayer
    private lateinit var vibratorManager: VibratorManager
    private lateinit var graphicsRenderer: GraphicsRenderer

    enum class State {
        PREVIEW, SHAKE_PREVIEW, LOAD_ANIMATION, RESPONSE
    }
    private var currentState = State.PREVIEW

    private lateinit var neutralResponses: List<String>
    private lateinit var positiveResponses: List<String>
    private lateinit var negativeResponses: List<String>
    private lateinit var strongResponses: List<String>

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

        private const val MIN_PROBABILITY_THRESHOLD = 20
        private const val DEFAULT_PROBABILITY_THRESHOLD = 33
        private const val MAX_PROBABILITY_THRESHOLD = 47

        private const val SHAKE_FORCE_BOUNDARY = 14F
        private const val TOO_HIGH_FORCE_THRESHOLD = 30F

        private const val CHANNEL_ID = "motion_channel"

        private const val START_TIME_POSITIVE = "06:00:00"
        private const val END_TIME_POSITIVE = "09:00:00"

        private const val START_TIME_NEUTRAL = "12:00:00"
        private const val END_TIME_NEUTRAL = "14:00:00"

        private const val START_TIME_NEGATIVE = "23:00:00"
        private const val END_TIME_NEGATIVE = "01:00:00"
    }

    private fun createNotificationChannel() {
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if(notificationManager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.glyph_toy_magicball),
            IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForeground() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Motion tracking active")
            .setContentText("Collecting accelerometer and gyroscope")
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun isSecondaryUser(): Boolean {
        val userManager = getSystemService(USER_SERVICE) as UserManager
        return !userManager.isSystemUser
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

        soundEffectPlayer = SoundEffectPlayer(
            this,
            listOf(R.raw.slosh_1, R.raw.slosh_2, R.raw.slosh_3),
            SHAKE_FORCE_BOUNDARY
        )

        vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager

        graphicsRenderer = GraphicsRenderer(applicationContext)

        // workaround for secondary profile restrictions on sensors
        if(isSecondaryUser()) startForeground()
    }

    override fun onBind(intent: Intent?): IBinder {
        // when onBind is called, the toy is currently being used by the user
        initGDK()

        consecutiveResponses = 0
        prevResponseCategory = -1

        shakingDetector = ShakingDetector(
            applicationContext,
            onShakeStart = {
                if(currentState != State.LOAD_ANIMATION) {
                    soundEffectPlayer.startSound()
                }
            },
            onShake = { intensity ->
                if(currentState != State.LOAD_ANIMATION) {
                    soundEffectPlayer.adjustSound(intensity.toFloat())
                }
            },
            onShakeReset = {
                if(currentState != State.LOAD_ANIMATION) {
                    soundEffectPlayer.stopSound()
                }
            },
            onShakeEnd = { avgForce ->
                soundEffectPlayer.stopSound()
                orientationDetector.startCalibration()
                onShakeEnd(avgForce)
            }
        ).apply {
            register()
            activate()
        }

        orientationDetector = OrientationDetector(
            applicationContext,
            onOrientationChange = { pitch, roll ->
                if(graphicsRenderer.updateOffsets(pitch, roll)) {
                    vibratorManager.defaultVibrator.vibrate(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    )
                }
            }
        ).apply {
            register()
            activate()
        }

        return serviceMessenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // the user changed toy
        graphicsRenderer.cancelAnimation()
        glyphMatrixManager?.turnOff()
        glyphMatrixManager?.unInit()
        glyphMatrixManager = null

        // release all sensors
        soundEffectPlayer.release()
        shakingDetector.unregister()
        orientationDetector.unregister()

        return false
    }

    private fun initGDK() {
        glyphMatrixManager = GlyphMatrixManager.getInstance(applicationContext)

        val callback = object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(componentName: ComponentName) {
                glyphMatrixManager?.register(DEVICE_23112)

                graphicsRenderer.setGlyphManager(glyphMatrixManager)
                graphicsRenderer.drawPreview()
                orientationDetector.startCalibration()
            }

            override fun onServiceDisconnected(componentName: ComponentName) {}
        }

        glyphMatrixManager?.init(callback)
    }

    private val serviceHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val bundle: Bundle = msg.data
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

    private fun onLongPress() {
        currentState = when(currentState) {
           State.PREVIEW -> {
               graphicsRenderer.currentResponse = getString(R.string.shake)
               graphicsRenderer.offsetX = 0
               graphicsRenderer.offsetY = 0
               graphicsRenderer.drawResponse()
               orientationDetector.endCalibration()

               State.SHAKE_PREVIEW
           }

           State.SHAKE_PREVIEW -> {
               graphicsRenderer.currentResponse = ""
               graphicsRenderer.offsetX = 0
               graphicsRenderer.offsetY = 0
               orientationDetector.startCalibration()
               graphicsRenderer.drawPreview()

               State.PREVIEW
           }

           State.RESPONSE -> {
               graphicsRenderer.currentResponse = ""
               graphicsRenderer.offsetX = 0
               graphicsRenderer.offsetY = 0
               orientationDetector.startCalibration()
               graphicsRenderer.drawPreview()

               State.PREVIEW
           }

           else -> currentState
        }
    }

    /**
     * Called when a shake is successfully detected,
     * adjust probabilities based on avg shake force and draws a random response
     * */
    private fun onShakeEnd(avgForce: Double) {
        vibratorManager.defaultVibrator.vibrate(
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        )

        graphicsRenderer.currentResponse = ""
        currentState = State.LOAD_ANIMATION

        graphicsRenderer.drawLoadingAnimation {
            // get a random response, adjust probabilities based on the avg force if needed
            if(avgForce >= TOO_HIGH_FORCE_THRESHOLD) {
                val newResponse = strongResponses.random()
                graphicsRenderer.currentResponse = newResponse
                graphicsRenderer.drawResponseWithFadeIn()
            }
            else {
                updateProbabilities(avgForce)

                val (rndCategory, newUnlockBitmap) = drawRndCategory()
                if(newUnlockBitmap != null) graphicsRenderer.drawBitmapWithFadeIn(newUnlockBitmap)
                else {
                    when(val response = getRndResponse(rndCategory)) {
                        is String -> {
                            graphicsRenderer.currentResponse = response
                            graphicsRenderer.drawResponseWithFadeIn()
                        }
                        is Bitmap -> {
                            graphicsRenderer.drawBitmapWithFadeIn(response)
                        }
                        else -> {}
                    }
                }
            }

            orientationDetector.endCalibration()
            currentState = State.RESPONSE
        }
    }

    private fun updateProbabilities(avgForce: Double) {
        probabilityThreshold =
            if(avgForce > SHAKE_FORCE_BOUNDARY) {
                max(
                    DEFAULT_PROBABILITY_THRESHOLD - 3 - (avgForce - SHAKE_FORCE_BOUNDARY).toInt(),
                    MIN_PROBABILITY_THRESHOLD
                )
            }
            else {
                min(
                    DEFAULT_PROBABILITY_THRESHOLD + 3 + (SHAKE_FORCE_BOUNDARY - avgForce).toInt(),
                    MAX_PROBABILITY_THRESHOLD
                )
            }
    }

    private fun drawRndCategory(): Pair<Int, Bitmap?> {
        val rndIndex = Random.nextInt(0, 100)   // generate rnd number between 0 and 99

        // select response category based on set probabilities
        val category = when(rndIndex) {
            in 0..DEFAULT_PROBABILITY_THRESHOLD -> 0
            in DEFAULT_PROBABILITY_THRESHOLD..DEFAULT_PROBABILITY_THRESHOLD + probabilityThreshold -> 1
            else -> 2
        }

        // update consecutive category counter
        if(prevResponseCategory != category) consecutiveResponses = 1
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

        return if(numUnlocks > 0 && Random.nextInt(0, 100) < 25) {
            val rndIndex = Random.nextInt(0, unlocks.size)
            BitmapFactory.decodeResource(resources, unlocks[rndIndex])
        }
        else {
            val rndIndex = Random.nextInt(0, responses.size)
            responses[rndIndex]
        }
    }

    private fun getLastUnlockDateAndTotalNum(): Pair<String, Int>? {
        val lastUnlockDate: String
        val numUnlocks: Int

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

        return Pair(lastUnlockDate, numUnlocks)
    }

    private fun updateUnlocksCount(numUnlocks: Int, currentDate: LocalDate): List<Int>? {
        when(prevResponseCategory) {
            0 -> {
                numUnlocksNeutral = numUnlocks
                sharedPreferences.edit { putInt(NUM_UNLOCKS_NEUTRAL, numUnlocks) }
                sharedPreferences.edit { putString(LAST_UNLOCK_NEUTRAL, currentDate.toString()) }
                return neutralUnlocks
            }
            1 -> {
                numUnlocksPositive = numUnlocks
                sharedPreferences.edit { putInt(NUM_UNLOCKS_POSITIVE, numUnlocks) }
                sharedPreferences.edit { putString(LAST_UNLOCK_POSITIVE, currentDate.toString()) }
                return positiveUnlocks
            }
            2 -> {
                numUnlocksNegative = numUnlocks
                sharedPreferences.edit { putInt(NUM_UNLOCKS_NEGATIVE, numUnlocks) }
                sharedPreferences.edit { putString(LAST_UNLOCK_NEGATIVE, currentDate.toString()) }
                return negativeUnlocks
            }
            else -> return null
        }
    }

    private fun unlockResponse(): Bitmap? {
        val result = getLastUnlockDateAndTotalNum() ?: return null

        val lastUnlockDate = result.first
        var numUnlocks = result.second

        if(numUnlocks >= 3) return null     // all responses for this category are already unlocked

        // check if an unlock for this category was already made today
        val currentDate = ZonedDateTime.now(ZoneId.systemDefault()).toLocalDate()
        if(lastUnlockDate.isNotEmpty()) {
            try {
                if(LocalDate.parse(lastUnlockDate) == currentDate) return null
            }
            catch (_: Exception) { return null }
        }

        numUnlocks += 1
        val unlocks = updateUnlocksCount(numUnlocks, currentDate) ?: return null

        if(numUnlocksNeutral + numUnlocksPositive + numUnlocksNegative >= 9) {
            sharedPreferences.edit { putBoolean(ALL_UNLOCKS_DONE, true) }
        }

        return BitmapFactory.decodeResource(resources, unlocks[numUnlocks - 1])
    }
}