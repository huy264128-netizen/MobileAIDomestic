package com.projectmaidgroup.ui.avatar

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class Live2DGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val live2dRenderer = Live2DRenderer(context)

    init {
        setEGLContextClientVersion(2)

        preserveEGLContextOnPause = true

        setRenderer(live2dRenderer)

        // 先保留连续渲染，后面调通了再优化
        renderMode = RENDERMODE_CONTINUOUSLY

        setOnClickListener {
            live2dRenderer.playTapMotion()
        }
    }

    fun loadModel(spec: Live2DModelSpec) {
        queueEvent {
            live2dRenderer.setModel(spec)
        }
        requestRender()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
}