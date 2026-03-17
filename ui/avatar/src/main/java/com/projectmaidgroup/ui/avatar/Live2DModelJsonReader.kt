package com.projectmaidgroup.ui.avatar

import android.content.Context
import org.json.JSONObject

object Live2DModelJsonReader {

    fun readRawJson(context: Context, spec: Live2DModelSpec): String? {
        return try {
            val path = "${spec.folder}/${spec.modelJson}"
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    fun readFileReferences(context: Context, spec: Live2DModelSpec): List<String> {
        val json = readRawJson(context, spec) ?: return emptyList()

        return try {
            val root = JSONObject(json)
            val fileRefs = root.optJSONObject("FileReferences") ?: return emptyList()

            buildList {
                fileRefs.optString("Moc").takeIf { it.isNotBlank() }?.let { add("Moc: $it") }
                fileRefs.optString("Physics").takeIf { it.isNotBlank() }?.let { add("Physics: $it") }
                fileRefs.optString("Pose").takeIf { it.isNotBlank() }?.let { add("Pose: $it") }

                val textures = fileRefs.optJSONArray("Textures")
                if (textures != null) {
                    for (i in 0 until textures.length()) {
                        add("Texture[$i]: ${textures.optString(i)}")
                    }
                }

                val motions = fileRefs.optJSONObject("Motions")
                if (motions != null) {
                    val keys = motions.keys()
                    while (keys.hasNext()) {
                        val group = keys.next()
                        add("MotionGroup: $group")
                    }
                }

                val expressions = fileRefs.optJSONArray("Expressions")
                if (expressions != null) {
                    add("Expressions: ${expressions.length()}")
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}