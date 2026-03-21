package com.projectmaidgroup.mobileaidomestic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.projectmaidgroup.ui.avatar.AvatarModels
import com.projectmaidgroup.ui.avatar.Live2DAvatarScreen

@Immutable
private data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

private enum class ChatRole {
    USER, AGENT
}

private interface AgentBackend {
    suspend fun reply(input: String): String
}

/**
 * 先做本地假数据，后续直接替换成真实对话接口即可
 */
private class LocalEchoAgent : AgentBackend {
    override suspend fun reply(input: String): String {
        delay(350)
        return "我听到了“$input”"
    }
}

@Composable
fun Live2DTalk() {
    val scope = rememberCoroutineScope()
    val backend = remember { LocalEchoAgent() }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                id = 1L,
                role = ChatRole.AGENT,
                content = "你好，我已经准备好了。你输入什么，我就会先本地回复什么。"
            )
        )
    }

    var inputText by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val themeColors = remember {
        listOf(
            Color(0xFF7C4DFF),
            Color(0xFF03A9F4),
            Color(0xFF26A69A),
            Color(0xFFFF7043),
            Color(0xFFE91E63)
        )
    }

    var themeColorIndex by rememberSaveable { mutableIntStateOf(0) }
    var panelAlpha by rememberSaveable { mutableFloatStateOf(0.72f) }

    val panelColor = themeColors[themeColorIndex]
    val panelBackground = panelColor.copy(alpha = panelAlpha)

    val lastUserMessage = messages.lastOrNull { it.role == ChatRole.USER }
    val lastAgentMessage = messages.lastOrNull { it.role == ChatRole.AGENT }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101114))
            .padding(WindowInsets.safeDrawing.asPaddingValues())
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isCompact = screenWidth < 420.dp

        val avatarWidth = if (isCompact) screenWidth * 0.74f else screenWidth * 0.58f
        val panelHorizontalPadding = if (isCompact) 12.dp else 20.dp
        val bubbleMaxWidth = if (isCompact) screenWidth * 0.62f else screenWidth * 0.46f

        Box(modifier = Modifier.fillMaxSize()) {

            // 顶层按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                CircleIconButton(
                    onClick = { showHistory = true },
                    containerColor = Color.Black.copy(alpha = 0.28f)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "历史对话",
                        tint = Color.White
                    )
                }

                CircleIconButton(
                    onClick = { showSettings = true },
                    containerColor = Color.Black.copy(alpha = 0.28f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = Color.White
                    )
                }
            }

            // Live2D 区域 + 智能体气泡
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .align(Alignment.TopCenter)
                    .padding(top = 36.dp)
            ) {
                Live2DAvatarScreen(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = avatarWidth)
                        .fillMaxHeight(),
                    model = AvatarModels.DefaultAssistant
                )

                AnimatedVisibility(
                    visible = !lastAgentMessage?.content.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            end = if (isCompact) 12.dp else 24.dp,
                            top = if (isCompact) 36.dp else 56.dp
                        )
                ) {
                    MessageBubble(
                        text = lastAgentMessage?.content.orEmpty(),
                        maxWidth = bubbleMaxWidth,
                        backgroundColor = Color.White.copy(alpha = 0.92f),
                        contentColor = Color(0xFF202124),
                        tailOnStart = true
                    )
                }
            }

            // 用户上一条消息气泡（显示在底部输入框上方）
            AnimatedVisibility(
                visible = !lastUserMessage?.content.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = panelHorizontalPadding,
                        bottom = if (isCompact) 160.dp else 176.dp
                    )
            ) {
                MessageBubble(
                    text = lastUserMessage?.content.orEmpty(),
                    maxWidth = bubbleMaxWidth,
                    backgroundColor = panelColor.copy(alpha = 0.95f),
                    contentColor = Color.White,
                    tailOnStart = false
                )
            }

            // 底部半透明对话框
            ChatInputPanel(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = panelHorizontalPadding,
                        end = panelHorizontalPadding,
                        bottom = 12.dp
                    ),
                text = inputText,
                panelColor = panelBackground,
                onTextChange = { inputText = it },
                onOpenSettings = { showSettings = true },
                onSend = {
                    val content = inputText.trim()
                    if (content.isEmpty()) return@ChatInputPanel

                    val userMessage = ChatMessage(
                        id = System.currentTimeMillis(),
                        role = ChatRole.USER,
                        content = content
                    )
                    messages += userMessage
                    inputText = ""

                    scope.launch {
                        val answer = backend.reply(content)
                        messages += ChatMessage(
                            id = System.currentTimeMillis() + 1,
                            role = ChatRole.AGENT,
                            content = answer
                        )
                    }
                }
            )

            if (showHistory) {
                HistoryOverlay(
                    messages = messages.toList(),
                    onDismiss = { showHistory = false }
                )
            }
        }

        if (showSettings) {
            SettingsDialog(
                alpha = panelAlpha,
                currentColorIndex = themeColorIndex,
                colors = themeColors,
                onAlphaChange = { panelAlpha = it },
                onColorSelected = { themeColorIndex = it },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
private fun ChatInputPanel(
    modifier: Modifier = Modifier,
    text: String,
    panelColor: Color,
    onTextChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSend: () -> Unit
) {
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = panelColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp + maxOf(imeBottom, navBottom))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "本地测试对话",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置对话框样式",
                        tint = Color.White
                    )
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 140.dp),
                label = { Text("请输入内容") },
                placeholder = { Text("例如：你好") },
                shape = RoundedCornerShape(18.dp),
                singleLine = false,
                maxLines = 4
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    onClick = onSend,
                    containerColor = Color.White.copy(alpha = 0.18f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    maxWidth: Dp,
    backgroundColor: Color,
    contentColor: Color,
    tailOnStart: Boolean
) {
    Row(
        verticalAlignment = Alignment.Bottom
    ) {
        if (tailOnStart) {
            BubbleTail(
                color = backgroundColor,
                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
            )
        }

        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = maxWidth)
        ) {
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        if (!tailOnStart) {
            BubbleTail(
                color = backgroundColor,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun BubbleTail(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(10.dp)
            .background(color = color, shape = RoundedCornerShape(3.dp))
    )
}

@Composable
private fun HistoryOverlay(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.72f)
                .clickable(enabled = false) { },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1F24).copy(alpha = 0.94f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "历史对话",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭历史对话",
                            tint = Color.White
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (message.role == ChatRole.USER) {
                                Alignment.End
                            } else {
                                Alignment.Start
                            }
                        ) {
                            Text(
                                text = if (message.role == ChatRole.USER) "用户" else "智能体",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.64f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (message.role == ChatRole.USER) {
                                    Color(0xFF7C4DFF).copy(alpha = 0.88f)
                                } else {
                                    Color.White.copy(alpha = 0.92f)
                                },
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Text(
                                    text = message.content,
                                    color = if (message.role == ChatRole.USER) Color.White else Color(0xFF222222),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    alpha: Float,
    currentColorIndex: Int,
    colors: List<Color>,
    onAlphaChange: (Float) -> Unit,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
        title = {
            Text("对话框设置")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("主题色")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    colors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (currentColorIndex == index) 3.dp else 1.dp,
                                    color = if (currentColorIndex == index) Color.Black else Color.White.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { onColorSelected(index) }
                        )
                    }
                }

                Column {
                    Text("透明度：${(alpha * 100).toInt()}%")
                    Slider(
                        value = alpha,
                        onValueChange = onAlphaChange,
                        valueRange = 0.25f..0.95f
                    )
                }
            }
        }
    )
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    containerColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.minimumInteractiveComponentSize()
    ) {
        Box(
            modifier = Modifier
                .size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}