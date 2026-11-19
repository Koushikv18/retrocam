package com.koushik.retrocam.util

import android.content.Context
import android.media.MediaActionSound
import android.util.Log

class SoundHelper(private val context: Context) {
    private val TAG = "SoundHelper"
    private var actionSound: MediaActionSound? = null

    init {
        try {
            actionSound = MediaActionSound().apply {
                load(MediaActionSound.SHUTTER_CLICK)
                load(MediaActionSound.START_VIDEO_RECORDING)
                load(MediaActionSound.STOP_VIDEO_RECORDING)
                load(MediaActionSound.FOCUS_COMPLETE)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MediaActionSound could not be initialized: ${t.message}")
        }
    }

    fun playShutter(enabled: Boolean) {
        if (!enabled) return
        try {
            actionSound?.play(MediaActionSound.SHUTTER_CLICK)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to play shutter sound: ${t.message}")
        }
    }

    fun playStartVideo(enabled: Boolean) {
        if (!enabled) return
        try {
            actionSound?.play(MediaActionSound.START_VIDEO_RECORDING)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to play start video sound: ${t.message}")
        }
    }

    fun playStopVideo(enabled: Boolean) {
        if (!enabled) return
        try {
            actionSound?.play(MediaActionSound.STOP_VIDEO_RECORDING)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to play stop video sound: ${t.message}")
        }
    }

    fun playFocus(enabled: Boolean) {
        if (!enabled) return
        try {
            actionSound?.play(MediaActionSound.FOCUS_COMPLETE)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to play focus sound: ${t.message}")
        }
    }

    fun release() {
        try {
            actionSound?.release()
            actionSound = null
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to release sound: ${t.message}")
        }
    }
}
