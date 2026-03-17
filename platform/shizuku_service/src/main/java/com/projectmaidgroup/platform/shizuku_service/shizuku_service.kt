package com.projectmaidgroup.platform.shizuku_service

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

}
