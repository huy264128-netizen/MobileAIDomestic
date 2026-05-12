package com.projectmaidgroup.mobileaidomestic.agent

import android.util.Log
import com.projectmaidgroup.platform.shizuku_for_maid.ShizukuManager
import com.projectmaidgroup.platform.shizuku_service.ShizukuServiceManager
import dev.langchain4j.agent.tool.Tool

/**
 * LangChain4j 可调用的本机工具；底层走 Shizuku UserService 的 [Runtime.exec]。
 *
 * 注意：不得在此使用 [kotlinx.coroutines.runBlocking] 嵌套 [Dispatchers.IO]——
 * 智能体本身已在 IO 线程调用模型，工具里再 `runBlocking(Dispatchers.IO)` 调用 suspend 的
 * [ShizukuManager.execCommand] 容易造成 **IO 线程池饥饿 / 死锁**，表现为卡死或闪退。
 * 这里直接走同步 Binder [ShizukuServiceManager.runCommand]。
 */
class ShizukuTooling {

    @Tool(
        """
在 Shizuku 已授权时执行一条设备 shell 命令，返回 exitCode、stdout、stderr。
仅用于用户明确需要的设备操作或查询（如 input tap、input swipe、pm list packages、getprop）。
禁止在用户未同意时执行可能卸载应用、格式化或破坏数据的命令。
""",
    )
    fun runPrivilegedShellCommand(command: String): String {
        return try {
            when (ShizukuManager.serviceState.value) {
                ShizukuManager.ShizukuState.Granted -> {
                    val raw = ShizukuServiceManager.runCommand(command)
                        ?: return "错误：Shizuku 服务未绑定，请稍后重试或重新授权。"
                    val out = truncateForBinder(raw)
                    buildString {
                        appendLine("exitCode=0")
                        if (out.isNotBlank()) {
                            appendLine("stdout:")
                            append(out)
                        }
                    }.trim()
                }
                ShizukuManager.ShizukuState.Denied ->
                    "错误：Shizuku 权限被拒绝。请在 Shizuku 管理器中为该应用授权。"
                ShizukuManager.ShizukuState.Waiting ->
                    "错误：Shizuku 尚未就绪（等待授权）。"
                ShizukuManager.ShizukuState.Disconnected ->
                    "错误：Shizuku 未连接。请先安装并启动 Shizuku，再为本应用授权。"
            }
        } catch (e: Throwable) {
            Log.e(TAG, "runPrivilegedShellCommand failed: $command", e)
            "错误：执行命令时异常：${e.message ?: e.javaClass.simpleName}"
        }
    }

    @Tool(
        """
执行一段 MaidLang 自动化脚本。适用于需要复杂逻辑（循环、条件、多步操作）的任务。
MaidLang 语法简述：
- 变量：int i=0; string s="a"; var x=1.0; (分号必带)
- 函数：void funcName(arg){...} 或 fun int name(){return 1;}
- 控制流：if/else, while, for(int i=0;i<n;i++)
- 内置函数（已自动声明，直接调用）：
  tap(float, float), swipe(x1, y1, x2, y2, durationMs), sleep(ms), 
  back(), home(), key(code), launch(pkg), openUrl(url), 
  inputText(text), shell(cmd) -> string, stopApp(pkg), screenShot(path)
""",
    )
    fun executeMaidLangScript(code: String): String {
        return try {
            when (ShizukuManager.serviceState.value) {
                ShizukuManager.ShizukuState.Granted -> {
                    val res = ShizukuServiceManager.execMaidLang(code)
                        ?: return "错误：服务未连接或执行失败。"
                    truncateForBinder(res)
                }
                else -> "错误：Shizuku 权限未就绪，当前状态：${ShizukuManager.serviceState.value}"
            }
        } catch (e: Throwable) {
            Log.e(TAG, "executeMaidLangScript failed", e)
            "脚本执行异常：${e.message}"
        }
    }

    companion object {
        private const val TAG = "ShizukuTooling"
        /** Binder 单次传输不宜过大；截断避免 TransactionTooLarge / OOM 导致进程被杀。 */
        private const val MAX_TOOL_OUTPUT_CHARS = 120_000

        private fun truncateForBinder(s: String): String {
            if (s.length <= MAX_TOOL_OUTPUT_CHARS) return s
            return s.take(MAX_TOOL_OUTPUT_CHARS) +
                "\n\n…(输出已截断至前 $MAX_TOOL_OUTPUT_CHARS 字符，避免数据过大导致崩溃)"
        }
    }
}
