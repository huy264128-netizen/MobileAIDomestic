package com.projectmaidgroup.ui.avatar

object AvatarEmotionMapper {
    fun toMotionCommand(emotion: AvatarEmotion): Live2DMotionCommand {
        return when (emotion) {
            AvatarEmotion.HAPPY -> Live2DMotionCommand(emotion = emotion, group = "TapBody")
            else -> Live2DMotionCommand(emotion = emotion, group = "Idle")
        }
    }
}
