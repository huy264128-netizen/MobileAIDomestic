package com.projectmaidgroup.ui.avatar

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.TextureView
import android.view.Surface
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.SystemClock

class Live2DTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private val live2dRenderer = Live2DRenderer(context)
    @Volatile
    var lastReplyMotionTrigger: Int = Int.MIN_VALUE

    @Volatile
    private var currentSpec: Live2DModelSpec? = null

    @Volatile
    private var clearColorInt: Int = android.graphics.Color.TRANSPARENT

    private var renderThread: RenderThread? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
        setOnClickListener {
            live2dRenderer.playTapMotion()
        }
    }

    fun loadModel(spec: Live2DModelSpec) {
        currentSpec = spec
        live2dRenderer.setModel(spec)
        renderThread?.requestRender()
    }

    fun setClearColor(colorInt: Int) {
        clearColorInt = colorInt
        live2dRenderer.setClearColor(colorInt)
        renderThread?.requestRender()
    }

    fun playReplyMotion() {
        live2dRenderer.playRandomReplyMotion()
        renderThread?.requestRender()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        live2dRenderer.setClearColor(clearColorInt)
        currentSpec?.let { live2dRenderer.setModel(it) }
        renderThread = RenderThread(surface, width, height, live2dRenderer).also { it.start() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.onResize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderThread?.shutdown()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private class RenderThread(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
        private val renderer: Live2DRenderer
    ) : Thread("Live2DTextureRenderThread") {

        @Volatile private var running = true
        @Volatile private var paused = false
        @Volatile private var widthPx = width
        @Volatile private var heightPx = height
        @Volatile private var needsResize = true
        @Volatile private var renderRequested = true

        private val surface = Surface(surfaceTexture)
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        override fun run() {
            initEgl()
            renderer.onSurfaceCreated(null, null)
            renderer.onSurfaceChanged(null, widthPx, heightPx)
            needsResize = false

            while (running) {
                if (paused) {
                    SystemClock.sleep(16)
                    continue
                }
                if (needsResize) {
                    renderer.onSurfaceChanged(null, widthPx, heightPx)
                    needsResize = false
                }
                renderer.onDrawFrame(null)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                renderRequested = false
                SystemClock.sleep(16)
            }
            releaseEgl()
            surface.release()
        }

        fun onResize(width: Int, height: Int) {
            widthPx = width
            heightPx = height
            needsResize = true
            renderRequested = true
        }

        fun requestRender() {
            renderRequested = true
        }

        fun shutdown() {
            running = false
            interrupt()
            try {
                join(500)
            } catch (_: InterruptedException) {
            }
        }

        private fun initEgl() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_RENDERABLE_TYPE, 4,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            val eglConfig = configs[0]!!

            val contextAttribs = intArrayOf(0x3098, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun releaseEgl() {
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
                if (eglSurface !== EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext !== EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }
}
