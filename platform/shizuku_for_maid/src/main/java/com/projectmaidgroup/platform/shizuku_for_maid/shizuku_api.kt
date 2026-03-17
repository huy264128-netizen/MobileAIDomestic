package com.projectmaidgroup.platform.shizuku_for_maid

import android.content.pm.PackageManager
import android.util.Log
import com.projectmaidgroup.platform.shizuku_service.ShizukuServiceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import rikka.shizuku.Shizuku

data class ShellResult(val exitCode: Int, val output: String, val error: String)

object ShizukuManager {
    private val _serviceState = MutableStateFlow(ShizukuState.Disconnected)
    val serviceState: StateFlow<ShizukuState> = _serviceState.asStateFlow()

    enum class ShizukuState { Disconnected, Waiting, Granted, Denied }

    init {
        Shizuku.addBinderReceivedListener {
            updateState()
            if (Shizuku.pingBinder()) ShizukuServiceManager.bind()
        }
        Shizuku.addBinderDeadListener { updateState() }
        Shizuku.addRequestPermissionResultListener { _, _ -> updateState() }

        updateState()
        // 如果已经有权限，直接尝试绑定 Service
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuServiceManager.bind()
        }
    }

    private fun updateState() {
        val newState = when {
            !Shizuku.pingBinder() -> ShizukuState.Disconnected
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.Denied
            else -> ShizukuState.Granted
        }
        _serviceState.value = newState
    }

    suspend fun <T> runWithShizuku(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        if (_serviceState.value != ShizukuState.Granted) {
            return@withContext Result.failure(IllegalStateException("Shizuku not granted"))
        }
        return@withContext try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 使用 User Service 替代 newProcess
    suspend fun execCommand(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            // 直接调用远程 Service 的方法
            val output = ShizukuServiceManager.runCommand(cmd) ?: ""
            // 由于 User Service 运行在特权进程，默认返回成功(0)
            ShellResult(0, output, "")
        } catch (e: Exception) {
            ShellResult(1, "", e.message ?: "Unknown error")
        }
    }

    // 模拟 Session 行为，底层不再维护 sh 进程
    class ShizukuSession {
        suspend fun execCommand(cmd: String) = withContext(Dispatchers.IO) {
            ShizukuServiceManager.runCommand(cmd)
        }

        suspend fun tap(x: Int, y: Int) = execCommand("input tap $x $y")
        suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int) =
            execCommand("input swipe $x1 $y1 $x2 $y2 $duration")
        suspend fun pressKey(key: String) = execCommand("input keyevent $key")
        suspend fun uninstall(packageName: String) = execCommand("pm uninstall $packageName")
        suspend fun install(packagePath: String) = execCommand("pm install $packagePath")
        suspend fun setScreenBrightness(brightness: Int) =
            execCommand("settings put system screen_brightness $brightness")

        fun deinit(): Int = 0 // User Service 模式下无需手动关闭 sh 流
    }

    suspend fun execCommands(cmds: suspend (shizukuSession: ShizukuSession) -> Unit) =
        withContext(Dispatchers.IO) {
            if (_serviceState.value == ShizukuState.Granted) {
                val session = ShizukuSession()
                try {
                    cmds(session)
                    return@withContext 0
                } catch (e: Exception) {
                    Log.e("execCommands", e.toString())
                    throw e
                }
            } else {
                throw IllegalStateException("Shizuku not granted")
            }
        }
}
suspend fun tap_shell(x: Int, y: Int) = ShizukuManager.execCommand("input tap $x $y")
suspend fun swipe_shell(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int) = ShizukuManager.execCommand("input swipe $x1 $y1 $x2 $y2 $duration")
suspend fun pressKey_shell(key: String) = ShizukuManager.execCommand("input keyevent $key")
suspend fun uninstall_shell(packageName: String) = ShizukuManager.execCommand("pm uninstall $packageName")
suspend fun install_shell(packagePath: String) = ShizukuManager.execCommand("pm install $packagePath")
suspend fun setScreenBrightness_shell(brightness: Int) = ShizukuManager.execCommand("settings put system screen_brightness $brightness")
