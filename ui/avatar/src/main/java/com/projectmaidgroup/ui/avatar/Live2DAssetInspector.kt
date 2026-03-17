package com.projectmaidgroup.ui.avatar

import android.content.Context

object Live2DAssetInspector {

    fun listFiles(context: Context, path: String, depth: Int = 3): List<String> {
        val result = mutableListOf<String>()

        fun walk(current: String, currentDepth: Int) {
            if (currentDepth > depth) return

            val children = try {
                context.assets.list(current)?.toList().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }

            if (children.isEmpty()) {
                result += current
                return
            }

            for (child in children.sorted()) {
                val next = if (current.isEmpty()) child else "$current/$child"
                walk(next, currentDepth + 1)
            }
        }

        walk(path, 0)
        return result
    }

    fun summary(context: Context, spec: Live2DModelSpec): String {
        val fullPath = "${spec.folder}/${spec.modelJson}"
        val exists = Live2DAssetChecker.exists(context, spec)
        val files = listFiles(context, spec.folder, depth = 3)

        return buildString {
            appendLine(if (exists) "MODEL FOUND" else "MODEL MISSING")
            appendLine(fullPath)
            appendLine("----")
            if (files.isEmpty()) {
                append("No files found under ${spec.folder}")
            } else {
                files.take(12).forEach { appendLine(it) }
                if (files.size > 12) {
                    append("...and ${files.size - 12} more")
                }
            }
        }
    }
}