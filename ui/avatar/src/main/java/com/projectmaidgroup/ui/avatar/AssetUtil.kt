package com.projectmaidgroup.ui.avatar.live2d

import android.content.Context
import java.io.ByteArrayOutputStream

object AssetUtil {

    fun readBytes(context: Context, assetPath: String): ByteArray {
        context.assets.open(assetPath).use { input ->
            val buffer = ByteArray(16 * 1024)
            val out = ByteArrayOutputStream()
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }

    fun exists(context: Context, assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).close()
            true
        } catch (_: Throwable) {
            false
        }
    }
}