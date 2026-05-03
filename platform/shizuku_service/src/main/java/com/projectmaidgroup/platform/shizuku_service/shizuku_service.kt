package com.projectmaidgroup.platform.shizuku_service
import android.icu.util.TimeUnit
import kotlinx.coroutines.delay
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
        interpreter.registerNative("swipe"){
            InputHelper.swipe(it[0].asFloat(),it[1].asFloat(),it[2].asFloat(),it[3].asFloat(),it[4].asInt().toLong())
            MaidValue.NullVal
        }
        interpreter.registerNative("sleep"){
            delay(it[0].asInt().toLong())
            MaidValue.NullVal
        }
        val inbuiltCode="external fun tap(float,float) -> void;" +
                "external fun swipe(float,float,float,float,int);" +
                "external fun sleep(int);"
        val execCode=inbuiltCode+code
        val program= runBlocking{ parser(lexer(execCode)).program() }
        var result=""
        for (node in program.codes) {
            val currentResult = runBlocking{ interpreter.interpret(node) }
            if (currentResult !is MaidValue.NullVal) {
                result+=currentResult
            }
        }
        return result
    }
}
