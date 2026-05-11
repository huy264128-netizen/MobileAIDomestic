package com.projectmaidgroup.platform.shizuku_service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs

object ShizukuServiceManager {
    private var service: IShizukuService? = null
    private val userServiceArgs = UserServiceArgs(
        ComponentName(
            BuildConfig.SHIZUKU_HOST_APPLICATION_ID,
            ShizukuServiceImpl::class.java.name,
        ),
    )
        .daemon(false)
        .debuggable(true)
        .processNameSuffix("maid_shizuku_user_service")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IShizukuService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun bind() {
        if (service != null) return
        Shizuku.bindUserService(userServiceArgs, connection)
    }

    fun unbind() {
        Shizuku.unbindUserService(userServiceArgs, connection, true)
    }

    fun runCommand(cmd: String): String? {
        return service?.runCommand(cmd)
    }
    fun execMaidLang(code: String): String? {
        return service?.execMaidLang(code)
    }
}
