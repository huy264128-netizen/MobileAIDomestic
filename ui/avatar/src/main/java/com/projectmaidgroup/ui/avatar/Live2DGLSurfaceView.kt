package com.projectmaidgroup.ui.avatar

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * Live2D 的 Android View 容器。
 *
 * Compose 页面不会直接操作 Live2DRenderer，
 * 而是通过这个 View 暴露简单接口：
 *
 * loadModel()
 * setClearColor()
 * playReplyMotion()
 * playAction()
 * playEmotion()
 */
class Live2DGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val live2dRenderer = Live2DRenderer(context)

    /**
     * 用于 Compose 判断 replyMotionTrigger 是否变化。
     */
    var lastReplyMotionTrigger: Int = Int.MIN_VALUE

    /**
     * 用于 Compose 判断 emotionTrigger 是否变化。
     */
    var lastEmotionTrigger: Int = Int.MIN_VALUE

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        holder.setFormat(PixelFormat.TRANSLUCENT)

        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)

        preserveEGLContextOnPause = true
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        setRenderer(live2dRenderer)

        /**
         * 你现在项目里原本是 RENDERMODE_CONTINUOUSLY。
         * 这个模式会持续刷新，Live2D 动作更稳。
         */
        renderMode = RENDERMODE_CONTINUOUSLY

        setOnClickListener {
            queueEvent {
                live2dRenderer.playTapMotion()
            }
            requestRender()
        }
    }

    fun loadModel(spec: Live2DModelSpec) {
        queueEvent {
            live2dRenderer.setModel(spec)
        }
        requestRender()
    }

    fun setClearColor(colorInt: Int) {
        queueEvent {
            live2dRenderer.setClearColor(colorInt)
        }
        requestRender()
    }

    fun playReplyMotion() {
        queueEvent {
            live2dRenderer.playRandomReplyMotion()
        }
        requestRender()
    }

    fun playAction(action: AvatarAction) {
        queueEvent {
            live2dRenderer.queueAction(action)
        }
        requestRender()
    }

    fun playEmotion(emotion: AvatarEmotion) {
        queueEvent {
            live2dRenderer.playEmotion(emotion)
        }
        requestRender()
    }
}