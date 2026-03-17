package com.projectmaidgroup.ui.avatar

import android.content.Context

object AvatarModels {

    val DefaultAssistant = Live2DModelSpec(
        folder = "live2d/assistant_default/Mao",
        modelJson = "Mao.model3.json"
    )

}

class Live2DAvatarController(
    private val context: Context
) {
    private var currentModel: Live2DModelSpec? = null

    fun load(spec: Live2DModelSpec) {
        currentModel = spec
    }

    fun isModelReady(): Boolean {
        val model = currentModel ?: return false
        return Live2DAssetChecker.exists(context, model)
    }

    fun describeCurrentModel(): List<String> {
        val model = currentModel ?: return listOf("No model selected")
        return Live2DModelJsonReader.readFileReferences(context, model)
    }

    fun playIdle() {
        // 后续接入真正 Live2D SDK
    }

    fun playTapMotion() {
        // 后续接入真正 Live2D SDK
    }

    fun getCurrentModel(): Live2DModelSpec? = currentModel
}