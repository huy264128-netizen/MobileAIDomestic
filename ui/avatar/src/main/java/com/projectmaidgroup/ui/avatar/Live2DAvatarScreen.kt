package com.projectmaidgroup.ui.avatar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun Live2DAvatarScreen(
    model: Live2DModelSpec,
    modifier: Modifier = Modifier,
    backgroundColor: Int,
    replyMotionTrigger: Int
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            Live2DTextureView(context).apply {
                loadModel(model)
                setClearColor(backgroundColor)
            }
        },
        update = { view ->
            view.setClearColor(backgroundColor)
            view.loadModel(model)

            if (view.lastReplyMotionTrigger != replyMotionTrigger) {
                view.lastReplyMotionTrigger = replyMotionTrigger
                if (replyMotionTrigger > 0) {
                    view.playReplyMotion()
                }
            }
        }
    )
}
