package com.projectmaidgroup.ui.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun Live2DAvatarScreen(
    model: Live2DModelSpec,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            Live2DGLSurfaceView(context).apply {
                loadModel(model)
            }
        },
        update = { view ->
            view.loadModel(model)
        }
    )
}