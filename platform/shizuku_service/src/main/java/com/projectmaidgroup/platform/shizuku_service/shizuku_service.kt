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

        val inbuiltCode = "external fun tap(float, float) -> void;" +
                "external fun swipe(float, float, float, float, int);" +
                "external fun sleep(int);" +
                "external fun back() -> void;" +
                "external fun home() -> void;" +
                "external fun key(int) -> void;"

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
