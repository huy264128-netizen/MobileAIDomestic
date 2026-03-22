package com.projectmaidgroup.ui.avatar

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class Live2DGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val live2dRenderer = Live2DRenderer(context)
    var lastReplyMotionTrigger: Int = Int.MIN_VALUE

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)
        preserveEGLContextOnPause = true
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setRenderer(live2dRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        setOnClickListener {
            live2dRenderer.playTapMotion()
        }
    }

    fun loadModel(spec: Live2DModelSpec) {
        queueEvent { live2dRenderer.setModel(spec) }
        requestRender()
    }

    fun setClearColor(colorInt: Int) {
        queueEvent { live2dRenderer.setClearColor(colorInt) }
        requestRender()
    }

    fun playReplyMotion() {
        queueEvent { live2dRenderer.playRandomReplyMotion() }
        requestRender()
    }
}
