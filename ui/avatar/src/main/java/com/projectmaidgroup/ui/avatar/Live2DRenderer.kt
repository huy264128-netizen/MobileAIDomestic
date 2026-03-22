package com.projectmaidgroup.ui.avatar

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismFramework
import com.projectmaidgroup.ui.avatar.live2d.MaoUserModel
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class Live2DRenderer(
    private val context: Context
) : GLSurfaceView.Renderer {

    private var loadFailed = false
    private var currentSpec: Live2DModelSpec? = null
    private var mao: MaoUserModel? = null
    private var started = false
    private var pendingTapMotion = false
    private var pendingReplyMotion = false
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    private var clearR = 0f
    private var clearG = 0f
    private var clearB = 0f
    private var clearA = 0f

    fun setModel(spec: Live2DModelSpec) {
        currentSpec = spec
        mao = null
        loadFailed = false
    }

    fun setClearColor(colorInt: Int) {
        clearR = Color.red(colorInt) / 255f
        clearG = Color.green(colorInt) / 255f
        clearB = Color.blue(colorInt) / 255f
        clearA = Color.alpha(colorInt) / 255f
    }

    fun playTapMotion() {
        mao?.playTapMotion() ?: run { pendingTapMotion = true }
    }

    fun playRandomReplyMotion() {
        mao?.playRandomReplyMotion() ?: run { pendingReplyMotion = true }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(clearR, clearG, clearB, clearA)
        try {
            if (!started) {
                CubismFramework.cleanUp()
                CubismFramework.startUp(CubismFramework.Option())
                CubismFramework.initialize()
                started = true
            }
            Log.d("Live2DRenderer", "Cubism init success")
        } catch (t: Throwable) {
            loadFailed = true
            Log.e("Live2DRenderer", "Cubism init failed", t)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(clearR, clearG, clearB, clearA)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (loadFailed) return

        try {
            if (mao == null) {
                val spec = currentSpec ?: AvatarModels.DefaultAssistant
                mao = MaoUserModel(context).apply {
                    load(
                        modelDir = spec.folder,
                        modelJson = spec.modelJson
                    )
                }
            }

            mao?.let { model ->
                if (pendingTapMotion) {
                    pendingTapMotion = false
                    model.playTapMotion()
                }
                if (pendingReplyMotion) {
                    pendingReplyMotion = false
                    model.playRandomReplyMotion()
                }
                model.update(1f / 60f)
                model.draw(surfaceWidth, surfaceHeight)
            }
        } catch (t: Throwable) {
            loadFailed = true
            Log.e("Live2DRenderer", "onDrawFrame failed", t)
        }
    }
}
