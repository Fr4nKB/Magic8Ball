package com.frank.magic8ball

import android.content.Context
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager

private var window: Window? = null

fun GlyphMatrixManager.setMatrixFrame(frame: IntArray, isSimulation: Boolean = false) {
    if (isSimulation)
        window?.pushPixels(frame)
    this.setMatrixFrame(frame)
}

fun GlyphMatrixManager.setMatrixFrame(frame: GlyphMatrixFrame, isSimulation: Boolean = false) {
    if (isSimulation)
        window?.pushPixels(frame.render())
    this.setMatrixFrame(frame)
}

fun setWindow(context: Context)
{
    window = Window(context)
    window?.open()
}

fun closeWindow()
{
    window?.close()
    window = null
}
