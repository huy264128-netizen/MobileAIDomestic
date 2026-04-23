package com.projectmaidgroup.platform.shizuku_service
import kotlinx.coroutines.runBlocking
import projectmaidgroup.maidlang.*
import kotlin.system.exitProcess

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


    override fun execMaidLang(code:String): String{
        val interpreter=Interpreter()
        //loat Tap
        interpreter.registerNative("tap") {
            InputHelper.tap(it[0].asFloat(), it[1].asFloat())
            MaidValue.NullVal
        }
        val inbuiltCode="external fun tap(float,float) -> void;"
        val exec_code=inbuiltCode+code
        val program= runBlocking{ parser(lexer(code)).program() }
        var result=""
        for (node in program.codes) {
            val currentResult = interpreter.interpret(node)
            if (currentResult !is MaidValue.NullVal) {
                result+=currentResult
            }
        }
        return result
    }
}
