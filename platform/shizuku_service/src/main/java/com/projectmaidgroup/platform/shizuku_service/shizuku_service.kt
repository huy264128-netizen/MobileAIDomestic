package com.projectmaidgroup.platform.shizuku_service

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
            val process = Runtime.getRuntime().exec(cmd)
            process.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            e.toString()
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
            // 使用 monkey 启动应用的主 Activity
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
            MaidValue.StringVal(runCommand(it[0].asString()))
        }
        interpreter.registerNative("stopApp") {
            runCommand("am force-stop ${it[0].asString()}")
            MaidValue.NullVal
        }
        interpreter.registerNative("screenShot") {
            runCommand("screencap -p ${it[0].asString()}")
            MaidValue.NullVal
        }

        val inbuiltCode = "external fun tap(float, float) -> void;" +
                "external fun swipe(float, float, float, float, int);" +
                "external fun sleep(int);" +
                "external fun back() -> void;" +
                "external fun home() -> void;" +
                "external fun key(int) -> void;" +
                "external fun launch(string) -> void;" +
                "external fun openUrl(string) -> void;" +
                "external fun inputText(string) -> void;" +
                "external fun shell(string) -> string;" +
                "external fun stopApp(string) -> void;" +
                "external fun screenShot(string) -> void;"

        val execCode = inbuiltCode + code

        return runBlocking {
            try {
                val tokens = lexer(execCode)
                val program = parser(tokens).program()
                var result = ""
                for (node in program.codes) {
                    val currentResult = interpreter.interpret(node)
                    if (currentResult !is MaidValue.NullVal) {
                        result += currentResult.asString()
                    }
                }
                result
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }
}
