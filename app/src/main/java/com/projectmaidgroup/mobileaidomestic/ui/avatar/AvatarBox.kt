package com.example.modernaiaid.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AvatarBox() {

    Box(
        modifier = Modifier
            .size(120.dp)
            .background(Color.Gray),
        contentAlignment = Alignment.Center
    ) {
        Text("Avatar")
    }

}