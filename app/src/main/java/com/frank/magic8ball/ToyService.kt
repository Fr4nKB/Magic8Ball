package com.frank.magic8ball

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.VibrationEffect
import android.os.VibratorManager
import com.frank.magic8ball.Util.colCoordinates
import com.frank.magic8ball.Util.rowCoordinates
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import com.frank.magic8ball.Util.createPreviewBitmap

/**
 * The main service through which we interact with the GDK.
 *
 * @author Fr4nKB
 */
class ToyService: Service() {
    private var mGM: GlyphMatrixManager? = null
    private var mCallback: GlyphMatrixManager.Callback? = null

    private lateinit var shakingDetector: ShakingDetector
    private lateinit var orientationDetector: OrientationDetector

    private lateinit var positiveResponses: List<String>
    private lateinit var neutralResponses: List<String>
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

    companion object {
        const val TAG = "Magic8Ball:ToyService"

        const val MATRIX_DIMENSION = 25
        const val LOW_BRIGHTNESS = 50
        const val MAX_BRIGHTNESS = 255
        
        const val MIN_PROBABILITY_THRESHOLD = 20
        const val DEFAULT_PROBABILITY_THRESHOLD = 33
        const val MAX_PROBABILITY_THRESHOLD = 47

        const val SHAKE_FORCE_BOUNDARY = 14f
        const val TOO_HIGH_FORCE_THRESHOLD = 28f

        const val PITCH_THRESHOLD = 5.0
    }

    private val serviceHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val bundle: Bundle = msg.getData()

            when(msg.what) {
                GlyphToy.MSG_GLYPH_TOY -> {
                    val event = bundle.getString(GlyphToy.MSG_GLYPH_TOY_DATA)

                    if(GlyphToy.EVENT_CHANGE == event) {    // long press event
                        mGM?.turnOff()
                        shakingDetector.activate()
                        orientationDetector.deactivate()
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val serviceMessenger: Messenger = Messenger(serviceHandler)

    private fun getRndResponse(): String {
        val rndIndex = Random.nextInt(1, 101)   // generate rnd number between 1 and 100

        // select response list based on set probabilities
        val responses = when(rndIndex) {
            in 0..DEFAULT_PROBABILITY_THRESHOLD -> neutralResponses
            in DEFAULT_PROBABILITY_THRESHOLD..DEFAULT_PROBABILITY_THRESHOLD + probabilityThreshold -> positiveResponses
            else -> negativeResponses
        }

        // return random response from within the drawn list
        return responses.random()
    }

    private fun onShakeEnd(avgForce: Double) {
        shakingDetector.deactivate()    // temporarily deactivate shaking detection
        soundEffectPlayer.stopSound()   // stop sloshing sound effect
        vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))

        // get a random response, adjust probabilities based on the avg force if needed
        currentResponse =
            if(avgForce >= TOO_HIGH_FORCE_THRESHOLD) strongResponses.random()
            else {
                probabilityThreshold =
                    if(avgForce > SHAKE_FORCE_BOUNDARY) max(DEFAULT_PROBABILITY_THRESHOLD - avgForce.toInt(), MIN_PROBABILITY_THRESHOLD)
                    else min(DEFAULT_PROBABILITY_THRESHOLD + avgForce.toInt(), MAX_PROBABILITY_THRESHOLD)

                getRndResponse()
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

        positiveResponses = resources.getStringArray(R.array.positive_responses).toList()
        neutralResponses = resources.getStringArray(R.array.neutral_responses).toList()
        negativeResponses = resources.getStringArray(R.array.negative_responses).toList()
        strongResponses = resources.getStringArray(R.array.stronk_responses).toList()

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
                drawPreview()
            }

            override fun onServiceDisconnected(componentName: ComponentName) {}
        }
        mGM?.init(mCallback)
    }

    private fun drawPreview() {
        val obj = GlyphMatrixObject.Builder()
            .setImageSource(previewBitmap)
            .setPosition(0, 0)
            .build()

        val frame = GlyphMatrixFrame.Builder().addTop(obj).build(applicationContext)
        mGM?.setMatrixFrame(frame.render())
    }

    private fun drawResponse(response: String) {
        val tokens = response.split(" ")
        val numTokens = tokens.size

        // get coordinates for tokens based on their number and size
        val rowCoordinates = rowCoordinates(numTokens)
        val colCoordinates = colCoordinates(tokens)

        val frameBuilder = GlyphMatrixFrame.Builder()

        tokens.forEachIndexed { index, token ->
            val brightness = if(index == highlightedTokenIndex) MAX_BRIGHTNESS else LOW_BRIGHTNESS

            val obj = GlyphMatrixObject.Builder()
                .setText(token)
                .setPosition(colCoordinates[index], rowCoordinates[index])
                .setBrightness(brightness)
                .build()

            when(index) {
                0 -> frameBuilder.addTop(obj)
                1 -> frameBuilder.addMid(obj)
                2 -> frameBuilder.addLow(obj)
            }
        }

        val frame = frameBuilder.build(applicationContext)
        try {
            mGM?.setMatrixFrame(frame.render())
        }
        catch(e: Exception) {
            e.printStackTrace()
        }
    }

}