package com.frank.magic8ball

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.media.MediaPlayer
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


class ToyService: Service() {
    private var mGM: GlyphMatrixManager? = null
    private var mCallback: GlyphMatrixManager.Callback? = null

    private lateinit var shakingDetector: ShakingDetector
    private lateinit var orientationDetector: OrientationDetector

    private lateinit var positiveResponses: List<String>
    private lateinit var neutralResponses: List<String>
    private lateinit var negativeResponses: List<String>
    private lateinit var stronkResponses: List<String>

    private var highlightedTokenIndex = 0
    private var currentResponse: String = ""

    // getting a positive or negative response is influenced by the force applied to the shake
    // neutral responses always have the same probability
    private var probabilityThreshold = DEFAULT_PROBABILITY_THRESHOLD

    companion object {
        const val TAG = "Magic8Ball:ToyService"

        const val MIN_PROBABILITY_THRESHOLD = 20
        const val DEFAULT_PROBABILITY_THRESHOLD = 33
        const val MAX_PROBABILITY_THRESHOLD = 47

        const val LOW_BRIGHTNESS = 100
        const val MAX_BRIGHTNESS = 255
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

        MediaPlayer.create(this, R.raw.slosh_slow).apply {
            setVolume(0.25f, 0.25f)
            setOnCompletionListener {
                reset()
                release()
            }
            start()
        }

        val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))

        // get a random response, adjust probabilities based on the avg force if needed
        currentResponse =
            if(avgForce >= 10) stronkResponses.random()
            else {
                probabilityThreshold =
                    if(avgForce > 5f) max(DEFAULT_PROBABILITY_THRESHOLD - avgForce.toInt(), MIN_PROBABILITY_THRESHOLD)
                    else min(DEFAULT_PROBABILITY_THRESHOLD + avgForce.toInt(), MAX_PROBABILITY_THRESHOLD)

                getRndResponse()
            }

        highlightedTokenIndex = currentResponse.split(" ").size/2
        drawResponse(currentResponse)
        orientationDetector.activate()
    }

    private fun highLightToken(pitch: Double) {
        highlightedTokenIndex =
            if(pitch > 0) max(highlightedTokenIndex - 1, 0)
            else min(highlightedTokenIndex + 1, currentResponse.split(" ").size - 1)

        drawResponse(currentResponse)
    }

    override fun onCreate() {
        super.onCreate()
        positiveResponses = resources.getStringArray(R.array.positive_responses).toList()
        neutralResponses = resources.getStringArray(R.array.neutral_responses).toList()
        negativeResponses = resources.getStringArray(R.array.negative_responses).toList()
        stronkResponses = resources.getStringArray(R.array.stronk_responses).toList()
    }

    override fun onBind(intent: Intent?): IBinder {
        initGDK()

        shakingDetector = ShakingDetector(
            applicationContext,
            onShakeEnd = { avgForce -> onShakeEnd(avgForce) }
        ).apply {
            register()
            activate()
        }

        orientationDetector = OrientationDetector(
            applicationContext,
            onOrientationChange = { pitch -> highLightToken(pitch)}
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
        //TODO: draw 8Ball preview
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