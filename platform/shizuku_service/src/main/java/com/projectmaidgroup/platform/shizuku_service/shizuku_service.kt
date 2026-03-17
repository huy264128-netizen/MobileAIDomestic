package com.projectmaidgroup.platform.shizuku_service

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.SystemClock
import android.view.MotionEvent
import kotlin.system.exitProcess

@SuppressLint("PrivateApi")
class ShizukuServiceImpl : IShizukuService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    private val inputManager: Any? by lazy {
        runCatching {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "input") as IBinder

            Class.forName("android.hardware.input.IInputManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
        }.getOrNull()
    }

    private val injectMethod by lazy {
        inputManager?.javaClass?.getMethod(
            "injectInputEvent",
            android.view.InputEvent::class.java,
            Int::class.javaPrimitiveType
        )
    }
    override fun runCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            process.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            e.toString()
        }
    }
    override fun tap(x: Int, y: Int) {
        val now = SystemClock.uptimeMillis()
        injectMotionEvent(x.toFloat(), y.toFloat(), MotionEvent.ACTION_DOWN, now)
        injectMotionEvent(x.toFloat(), y.toFloat(), MotionEvent.ACTION_UP, now)
    }

    private fun injectMotionEvent(x: Float, y: Float, action: Int, downTime: Long) {
        val event = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action, x, y, 0
        )
        try {
            // mode 2 = INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
            injectMethod?.invoke(inputManager, event, 2)
        } finally {
            event.recycle()
        }
    }

}
