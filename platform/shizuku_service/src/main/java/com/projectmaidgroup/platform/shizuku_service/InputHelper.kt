package com.projectmaidgroup.platform.shizuku_service

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.SystemClock
import android.view.InputEvent
import android.view.MotionEvent
import android.view.KeyEvent

@SuppressLint("PrivateApi")
object InputHelper {

    // 绕过编译期检查，动态获取 IInputManager
    private val inputManager: Any? by lazy {
        try {
            // 1. 获取 ServiceManager 类
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            // 2. 获取 getService 方法
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            // 3. 拿到 input 服务的 Binder
            val binder = getServiceMethod.invoke(null, "input") as IBinder

            // 4. 转换为 IInputManager 接口
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            asInterfaceMethod.invoke(null, binder)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun tap(x: Float, y: Float) {
        val im = inputManager ?: return
        try {
            // 找到注入方法：injectInputEvent(InputEvent, int)
            val injectMethod = im.javaClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )

            val now = SystemClock.uptimeMillis()
            // 模拟按下
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            injectMethod.invoke(im, down, 0) // 0 为 ASYNC 模式

            // 模拟抬起
            val up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0)
            injectMethod.invoke(im, up, 0)

            down.recycle()
            up.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) {
        val im = inputManager ?: return
        try {
            val injectMethod = im.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            val startTime = SystemClock.uptimeMillis()

            // 1. 按下
            val down = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, startX, startY, 0)
            injectMethod.invoke(im, down, 0)

            // 2. 采样滑动（分 10 段模拟平滑移动）
            val steps = 10
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val curX = startX + (endX - startX) * progress
                val curY = startY + (endY - startY) * progress
                val moveTime = startTime + (durationMs * progress).toLong()

                val move = MotionEvent.obtain(startTime, moveTime, MotionEvent.ACTION_MOVE, curX, curY, 0)
                injectMethod.invoke(im, move, 0)
                move.recycle()
            }

            // 3. 抬起
            val endTime = startTime + durationMs
            val up = MotionEvent.obtain(startTime, endTime, MotionEvent.ACTION_UP, endX, endY, 0)
            injectMethod.invoke(im, up, 0)

            down.recycle()
            up.recycle()
        } catch (e: Exception) { e.printStackTrace() }
    }
    fun pressKey(keyCode: Int) {
        val im = inputManager ?: return
        try {
            val injectMethod = im.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            val now = SystemClock.uptimeMillis()

            // 构造 KeyEvent (例如 KeyEvent.KEYCODE_BACK)
            val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
            injectMethod.invoke(im, down, 0)

            val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
            injectMethod.invoke(im, up, 0)
        } catch (e: Exception) { e.printStackTrace() }
    }

    // 快捷调用
    fun pressBack() = pressKey(KeyEvent.KEYCODE_BACK)
    fun pressHome() = pressKey(KeyEvent.KEYCODE_HOME)
}