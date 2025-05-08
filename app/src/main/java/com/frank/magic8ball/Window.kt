package com.frank.magic8ball

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.nothing.ketchum.GlyphToy


class Window(
    private val context: Context
) {
    private val mView: View
    private var mParams: WindowManager.LayoutParams? = null
    private val mWindowManager: WindowManager
    private val layoutInflater: LayoutInflater

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }
        layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mView = layoutInflater.inflate(R.layout.popup_window, null)
        val closeButton = mView.findViewById<View>(R.id.glyphButton)
        closeButton.setOnClickListener { callAction() }
        mParams!!.gravity = Gravity.TOP or Gravity.START // Start from top left
        mWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun pushPixels(pixels: IntArray) {
        val pixelView = mView.findViewById<PixelMatrixView>(R.id.matrixView)
        pixelView.setPixels(pixels)
    }


    fun open() {
        try {
            if (mView.windowToken == null) {
                if (mView.parent == null) {
                    mWindowManager.addView(mView, mParams)
                }
            }
        } catch (e: Exception) {
            Log.d("Error1", e.toString())
        }
    }

    fun callAction() {
        val msg = Message.obtain().apply {
            what = GlyphToy.MSG_GLYPH_TOY
            data = Bundle().apply {
                putString(GlyphToy.MSG_GLYPH_TOY_DATA, GlyphToy.EVENT_CHANGE)
            }
        }
        ToyService.serviceHandler.sendMessage(msg)
        Log.d("callAction", "GlyphToy.EVENT_CHANGE sent")
    }


    fun close() {
        try {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(mView)
            mView.invalidate()
            (mView.parent as ViewGroup).removeAllViews()
        } catch (e: Exception) {
            Log.d("Error2", e.toString())
        }
    }
}