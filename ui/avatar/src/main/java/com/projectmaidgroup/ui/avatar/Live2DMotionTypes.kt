package com.projectmaidgroup.ui.avatar

/**
 * 与助手回复情绪对应的 Live2D 侧抽象；具体播哪一组动作由 [AvatarEmotionMapper] 决定。
 */
enum class AvatarEmotion {
    NEUTRAL,
    HAPPY,
    SAD,
    ANGRY,
    SURPRISED,
}

/**
 * 描述一次要播放的 Cubism 动作：可按 **分组**、**文件名** 或仅按 **情绪**（再走映射）查找。
 */
data class Live2DMotionCommand(
    val emotion: AvatarEmotion = AvatarEmotion.NEUTRAL,
    val group: String? = null,
    val motionFile: String? = null,
    val priority: Int = 3,
)

/**
 * 将情绪映射为 [MaoUserModel] 能解析的 [Live2DMotionCommand]。
 * 默认使用 Mao 模型里常见的 `TapBody` 分组；若你的模型分组不同，可在此集中调整。
 */
object AvatarEmotionMapper {

    fun toMotionCommand(emotion: AvatarEmotion): Live2DMotionCommand = when (emotion) {
        AvatarEmotion.NEUTRAL -> Live2DMotionCommand(
            emotion = emotion,
            group = "TapBody",
            priority = 2,
        )
        AvatarEmotion.HAPPY, AvatarEmotion.SURPRISED -> Live2DMotionCommand(
            emotion = emotion,
            group = "TapBody",
            priority = 3,
        )
        AvatarEmotion.SAD -> Live2DMotionCommand(
            emotion = emotion,
            group = "TapBody",
            priority = 2,
        )
        AvatarEmotion.ANGRY -> Live2DMotionCommand(
            emotion = emotion,
            group = "TapBody",
            priority = 3,
        )
    }
}
