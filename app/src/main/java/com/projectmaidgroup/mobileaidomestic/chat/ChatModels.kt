package com.projectmaidgroup.mobileaidomestic.chat

import com.projectmaidgroup.ui.avatar.AvatarEmotion
import com.projectmaidgroup.ui.avatar.Live2DMotionCommand

data class ChatTurnResult(
    val replyText: String,
    val emotion: AvatarEmotion = AvatarEmotion.NEUTRAL,
    val motionGroup: String? = null,
    val motionFile: String? = null
) {
    fun toMotionCommand(): Live2DMotionCommand {
        return Live2DMotionCommand(
            emotion = emotion,
            group = motionGroup,
            motionFile = motionFile
        )
    }
}