package com.projectmaidgroup.ui.avatar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Live2D 头像 Compose 入口。
 *
 * 这个版本使用 Live2DGLSurfaceView，
 * 不依赖 Live2DTextureView。
 */
@Composable
fun Live2DAvatarScreen(
    model: Live2DModelSpec,
    modifier: Modifier = Modifier,
    backgroundColor: Int,
    replyMotionTrigger: Int = 0,
    emotion: AvatarEmotion = AvatarEmotion.NEUTRAL,
    emotionTrigger: Int = 0
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            Live2DGLSurfaceView(context).apply {
                loadModel(model)
                setClearColor(backgroundColor)
            }
        },
        update = { view: Live2DGLSurfaceView ->
            view.setClearColor(backgroundColor)
            view.loadModel(model)

            if (view.lastReplyMotionTrigger != replyMotionTrigger) {
                view.lastReplyMotionTrigger = replyMotionTrigger
                if (replyMotionTrigger > 0) {
                    view.playReplyMotion()
                }
            }

            if (view.lastEmotionTrigger != emotionTrigger) {
                view.lastEmotionTrigger = emotionTrigger
                if (emotionTrigger > 0) {
                    view.playEmotion(emotion)
                }
            }
        }
    )
}