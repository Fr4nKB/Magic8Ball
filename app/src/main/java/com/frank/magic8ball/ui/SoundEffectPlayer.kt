package com.frank.magic8ball.ui

import android.content.Context
import android.media.SoundPool
import kotlin.math.abs
import kotlin.math.min

/**
 * A class for managing sound effects playback using a SoundPool. It provides functionality to
 * start, adjust, and stop sound playback based on an intensity value and a configurable boundary.
 *
 * @param context The application context used for loading sound effects.
 * @param soundEffectIds A list of resource IDs for the sound effects that can be played.
 * @param boundary A threshold to determine how intensity influences playback speed and volume.
 *
 * @author Fr4nKB
 */
class SoundEffectPlayer(
    private val context: Context,
    private val soundEffectIds: List<Int>,
    private val boundary: Float
) {
    private val soundPool: SoundPool = SoundPool.Builder().setMaxStreams(1).build()
    private var streamId: Int = -1

    companion object {
        const val MAX_VOLUME = 0.25f
    }

    fun startSound() {
        if(streamId == -1) { // only play if not already playing
            val soundId = soundPool.load(context, soundEffectIds.random(), 1)
            soundPool.setOnLoadCompleteListener { _, _, status ->
                if(status == 0) {
                    streamId = soundPool.play(soundId, MAX_VOLUME, MAX_VOLUME, 1, -1, 1f)
                }
            }

        }
    }

    /**
     * adjusts playback speed and volume based on given intensity
     */
    fun adjustSound(intensity: Float) {
        if(streamId == -1) return

        val factor =
            if(intensity >= boundary) (min((intensity - boundary) / boundary, 1f)) / 4
            else -(intensity / boundary) / 4

        val playbackSpeed = 1.0f + factor
        val volume = abs(factor) * MAX_VOLUME

        soundPool.setRate(streamId, playbackSpeed)
        soundPool.setVolume(streamId, volume, volume)
    }

    fun stopSound() {
        if(streamId != -1) {
            soundPool.stop(streamId)
            streamId = -1
        }
    }

    fun release() {
        stopSound()
        soundPool.release()
    }
}