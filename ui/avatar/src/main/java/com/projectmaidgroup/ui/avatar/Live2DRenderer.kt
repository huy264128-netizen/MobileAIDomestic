package com.projectmaidgroup.ui.avatar

import android.content.Context
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

    private var surfaceWidth = 1
    private var surfaceHeight = 1

    fun setModel(spec: Live2DModelSpec) {
        currentSpec = spec
        mao = null
        loadFailed = false
    }

    fun playTapMotion() {
        pendingTapMotion = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.12f, 0.12f, 0.16f, 1.0f)

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
        Log.d("Live2DRenderer", "surface changed: ${width}x${height}")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (loadFailed) return

        try {
            if (mao == null) {
                val spec = currentSpec ?: AvatarModels.DefaultAssistant
                Log.d("Live2DRenderer", "loading model: ${spec.folder}/${spec.modelJson}")

                mao = MaoUserModel(context).apply {
                    load(
                        modelDir = spec.folder,
                        modelJson = spec.modelJson
                    )
                }

                Log.d("Live2DRenderer", "model load success")
            }

            if (pendingTapMotion) {
                pendingTapMotion = false
                Log.d("Live2DRenderer", "tap motion requested")
            }

            mao?.update(1f / 60f)
            mao?.draw(surfaceWidth, surfaceHeight)
        } catch (t: Throwable) {
            loadFailed = true
            Log.e("Live2DRenderer", "onDrawFrame failed", t)
        }
    }
}