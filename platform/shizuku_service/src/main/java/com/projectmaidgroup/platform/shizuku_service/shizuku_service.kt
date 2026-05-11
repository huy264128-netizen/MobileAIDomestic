package com.projectmaidgroup.platform.shizuku_service

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import projectmaidgroup.maidlang.*

class ShizukuServiceImpl : IShizukuService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun runCommand(cmd: String): String {
        return try {
            Log.d("MaidService", "--- Shell Start ---")
            Log.d("MaidService", "Command: $cmd")
            
            // 尝试使用绝对路径
            val shellPath = if (java.io.File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
            
            val process = ProcessBuilder(shellPath, "-c", cmd)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            Log.d("MaidService", "Exit Code: $exitCode, Output: '$output'")
            
            if (output.isEmpty()) {
                if (exitCode == 0) "Success (No Output)" else "Error: Exit code $exitCode"
            } else {
                output
            }
        } catch (e: Exception) {
            Log.e("MaidService", "Shell execution failed", e)
            "Error: ${e.message}"
        } finally {
            Log.d("MaidService", "--- Shell End ---")
        }
    }

    override fun execMaidLang(code: String): String {
        val interpreter = Interpreter()

        // 注册原生函数
        interpreter.registerNative("tap") {
            InputHelper.tap(it[0].asFloat(), it[1].asFloat())
            MaidValue.NullVal
        }
        interpreter.registerNative("swipe") {
            InputHelper.swipe(
                it[0].asFloat(),
                it[1].asFloat(),
                it[2].asFloat(),
                it[3].asFloat(),
                it[4].asInt().toLong()
            )
            MaidValue.NullVal
        }
        interpreter.registerNative("sleep") {
            delay(it[0].asInt().toLong())
            MaidValue.NullVal
        }
        interpreter.registerNative("back") {
            InputHelper.pressBack()
            MaidValue.NullVal
        }
        interpreter.registerNative("home") {
            InputHelper.pressHome()
            MaidValue.NullVal
        }
        interpreter.registerNative("key") {
            InputHelper.pressKey(it[0].asInt())
            MaidValue.NullVal
        }

        // 新增：应用跳转与系统功能
        interpreter.registerNative("launch") {
            val pkg = it[0].asString()
            runCommand("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
            MaidValue.NullVal
        }
        interpreter.registerNative("openUrl") {
            val url = it[0].asString()
            runCommand("am start -a android.intent.action.VIEW -d $url")
            MaidValue.NullVal
        }
        interpreter.registerNative("inputText") {
            val text = it[0].asString()
            runCommand("input text \"$text\"")
            MaidValue.NullVal
        }
        interpreter.registerNative("shell") {
            val res = runCommand(it[0].asString())
            MaidValue.StringVal(res)
        }
        interpreter.registerNative("stopApp") {
            runCommand("am force-stop ${it[0].asString()}")
            MaidValue.NullVal
        }
        interpreter.registerNative("screenShot") {
            runCommand("screencap -p ${it[0].asString()}")
            MaidValue.NullVal
        }

        val inbuiltCode = "external fun tap(float, float) -> void;\n" +
                "external fun swipe(float, float, float, float, int);\n" +
                "external fun sleep(int);\n" +
                "external fun back() -> void;\n" +
                "external fun home() -> void;\n" +
                "external fun key(int) -> void;\n" +
                "external fun launch(string) -> void;\n" +
                "external fun openUrl(string) -> void;\n" +
                "external fun inputText(string) -> void;\n" +
                "external fun shell(string) -> string;\n" +
                "external fun stopApp(string) -> void;\n" +
                "external fun screenShot(string) -> void;\n"

        val execCode = inbuiltCode + code

        return runBlocking {
            try {
                Log.d("MaidService", "Executing script (v3)...")
                val tokens = lexer(execCode)
                val program = parser(tokens).program()
                var lastResult: MaidValue = MaidValue.NullVal
                for (node in program.codes) {
                    lastResult = interpreter.interpret(node)
                    Log.d("MaidService", "Node: ${node.javaClass.simpleName}, Result: $lastResult")
                }
                
                val finalOutput = if (lastResult is MaidValue.NullVal) "" else lastResult.asString()
                Log.d("MaidService", "Final: '$finalOutput'")
                finalOutput
            } catch (e: Exception) {
                Log.e("MaidService", "Script error", e)
                "Error: ${e.message}"
            }
        }
    }
}
