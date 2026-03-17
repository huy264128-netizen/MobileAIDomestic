package com.projectmaidgroup.ui.avatar

import android.content.Context

object Live2DAssetChecker {

    fun exists(context: Context, spec: Live2DModelSpec): Boolean {
        return try {
            val fullPath = "${spec.folder}/${spec.modelJson}"
            context.assets.open(fullPath).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun debugMessage(context: Context, spec: Live2DModelSpec): String {
        val fullPath = "${spec.folder}/${spec.modelJson}"
        return if (exists(context, spec)) {
            "FOUND: $fullPath"
        } else {
            "MISSING: $fullPath"
        }
    }
}