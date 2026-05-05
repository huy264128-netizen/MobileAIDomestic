package com.projectmaidgroup.mobileaidomestic

import com.projectmaidgroup.platform.shizuku_for_maid.ShizukuManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class TestResult(val name: String, val passed: Boolean, val message: String)

object MaidLangTestRunner {
    private val tests = listOf(
        "Basic Math" to "{ 1 + 2 * 3; }",
        "Variables" to "{ var x = 10; x = x + 5; x; }",
        "If-Else" to "{ var y = 10; var res = 0; if (y > 5) { res = 1; } else { res = 0; } res; }",
        "Functions" to "{ int add(int a, int b) { return a + b; } add(10, 20); }",
        "Shizuku Shell" to "shell(\"id\");"
    )

    fun runAll(): Flow<TestResult> = flow {
        // 1. 权限检查
        if (ShizukuManager.serviceState.value != ShizukuManager.ShizukuState.Granted) {
            emit(TestResult("Shizuku Permission", false, "Permission denied. Requesting..."))
            ShizukuManager.requestPermission()
            return@flow
        }

        // 2. 自动尝试绑定并等待连接 (最多等待 3 秒)
        var retryCount = 0
        while (retryCount < 6) {
            val outputRaw = ShizukuManager.execMaidLang("1;")
            if (!outputRaw.startsWith("Error: Service not connected")) break
            
            emit(TestResult("Shizuku Bind", false, "Waiting for service... (${retryCount + 1}/6)"))
            com.projectmaidgroup.platform.shizuku_service.ShizukuServiceManager.bind()
            kotlinx.coroutines.delay(500)
            retryCount++
        }

        for ((name, code) in tests) {
            val outputRaw = ShizukuManager.execMaidLang(code)
            val output = outputRaw.trim()
            val displayOutput = if (output.isEmpty()) "[Empty String]" else "'$output'"
            
            val (passed, detail) = when (name) {
                "Basic Math" -> (output == "7") to "Expected 7, Got $displayOutput"
                "Variables" -> (output == "15") to "Expected 15, Got $displayOutput"
                "If-Else" -> (output == "1") to "Expected 1, Got $displayOutput"
                "Functions" -> (output == "30") to "Expected 30, Got $displayOutput"
                "Shizuku Shell" -> output.contains("uid=").let { it to (if (it) "Passed" else "Expected uid=..., Got $displayOutput") }
                else -> false to "Unknown test"
            }
            emit(TestResult(name, passed, if (passed) "Passed" else "Failed ($detail)"))
        }
    }
}
