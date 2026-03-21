package com.projectmaidgroup.mobileaidomestic

import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.font.FontWeight
import com.projectmaidgroup.ui.avatar.AvatarModels
import com.projectmaidgroup.ui.avatar.Live2DAvatarScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

@Immutable
private data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

private enum class ChatRole { USER, AGENT }

private interface AgentBackend {
    suspend fun reply(input: String, userName: String): String
}

private class LocalEchoAgent : AgentBackend {
    override suspend fun reply(input: String, userName: String): String {
        delay(450)
        return "收到，$userName：$input"
    }
}

private class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("live2d_talk_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = sp.getString("user_name", "用户") ?: "用户"
        set(value) = sp.edit().putString("user_name", value).apply()

    var musicEnabled: Boolean
        get() = sp.getBoolean("music_enabled", true)
        set(value) = sp.edit().putBoolean("music_enabled", value).apply()
}

@Composable
fun Live2DTalk() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()
    val backend = remember { LocalEchoAgent() }

    val isDark = isSystemInDarkTheme()

// 页面主背景：低饱和高级蓝，仍然跟系统浅深色走
    val bgTop = if (isDark) Color(0xFF0D1825) else Color(0xFFF2F6FB)
    val bgBottom = if (isDark) Color(0xFF162636) else Color(0xFFDCE7F2)

// Live2D 区域背景：和页面同色系，但刻意拉开一点层次，避免和边框/舞台撞色
    val live2dBgColor = if (isDark) Color(0xFF172535) else Color(0xFFEAF1F8)
    val live2dStageTop = if (isDark) Color(0xFF223548) else Color(0xFFF7FAFD)
    val live2dStageBottom = if (isDark) Color(0xFF12202E) else Color(0xFFE4EDF6)
    val live2dFrameColor = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color(0xFF7890AA).copy(alpha = 0.16f)
    }

// 保持你原来的“系统浅色 -> UI深色；系统深色 -> UI浅色”
    val uiIsDark = !isDark

    val panelColor = if (uiIsDark) Color(0xFF1F2733) else Color(0xFFF8FAFD)
    val panelTextColor = if (uiIsDark) Color.White else Color(0xFF1A2433)
    val panelSubTextColor = if (uiIsDark) Color.White.copy(alpha = 0.68f) else Color(0xFF1A2433).copy(alpha = 0.68f)

    val agentBubbleColor = if (uiIsDark) Color(0xFF2A3442) else Color.White
    val agentTextColor = if (uiIsDark) Color.White else Color(0xFF1A2433)
    val userBubbleColor = if (uiIsDark) Color(0xFF4D657E) else Color(0xFFDCE6F2)
    val userTextColor = if (uiIsDark) Color.White else Color(0xFF1A2433)

// 中央回复卡片：浅色模式下灰色半透明白字；深色模式下白色半透明黑字
    val centerReplyTopColor = if (isDark) {
        Color.White.copy(alpha = 0.84f)
    } else {
        Color(0xFF6A7079).copy(alpha = 0.62f)
    }
    val centerReplyBottomColor = if (isDark) {
        Color.White.copy(alpha = 0.70f)
    } else {
        Color(0xFF4F5660).copy(alpha = 0.48f)
    }
    val centerReplyTextColor = if (isDark) Color(0xFF14181D) else Color.White
    val centerReplyBorderColor = if (isDark) {
        Color.Black.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }
    val themeColors = remember {
        listOf(
            Color(0xFF516A86),
            Color(0xFF4E7891),
            Color(0xFF5F6F84),
            Color(0xFF66768E)
        )
    }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                id = 1L,
                role = ChatRole.AGENT,
                content = "你好，我已经准备好了。"
            )
        )
    }

    var inputText by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var themeColorIndex by rememberSaveable { mutableIntStateOf(0) }
    var panelAlpha by rememberSaveable { mutableFloatStateOf(0.64f) }
    var userName by rememberSaveable { mutableStateOf(prefs.userName) }
    var musicEnabled by rememberSaveable { mutableStateOf(prefs.musicEnabled) }
    var agentAnimateTick by rememberSaveable { mutableIntStateOf(0) }
    var showCenterReply by rememberSaveable { mutableStateOf(false) }
    //val panelColor = themeColors[themeColorIndex]
    val panelBackground = panelColor.copy(alpha = panelAlpha)
    val lastUserMessage = messages.lastOrNull { it.role == ChatRole.USER }
    val lastAgentMessage = messages.lastOrNull { it.role == ChatRole.AGENT }

    val avatarScale by animateFloatAsState(
        targetValue = if (agentAnimateTick % 2 == 1) 1.04f else 1f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "avatarScale"
    )

    val avatarAlpha by animateFloatAsState(
        targetValue = if (agentAnimateTick % 2 == 1) 0.96f else 1f,
        animationSpec = tween(320),
        label = "avatarAlpha"
    )


    // 播放背景音乐：请把 mp3 放到 app/src/main/res/raw/bg_music.mp3
    DisposableEffect(musicEnabled) {
        var player: MediaPlayer? = null
        if (musicEnabled) {
            runCatching {
                player = MediaPlayer.create(context, R.raw.bg_music)?.apply {
                    isLooping = true
                    setVolume(0.45f, 0.45f)
                    start()
                }
            }
        }
        onDispose {
            player?.stop()
            player?.release()
        }
    }

    LaunchedEffect(userName) {
        prefs.userName = userName
    }

    LaunchedEffect(musicEnabled) {
        prefs.musicEnabled = musicEnabled
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(bgTop, bgBottom)
                )
            )
    ) {
        val screenWidth = maxWidth
        val isCompact = screenWidth < 420.dp
        val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val panelHorizontalPadding = if (isCompact) 12.dp else 20.dp
        val bubbleMaxWidth = if (isCompact) screenWidth * 0.72f else screenWidth * 0.56f
        val centerReplyMaxWidth = if (isCompact) screenWidth * 0.78f else screenWidth * 0.54f
        val inputBottomPadding = bottomInset + 8.dp
        val inputPanelHeight = if (isCompact) 96.dp else 104.dp
        val centerReplyBottomReserved = inputBottomPadding + inputPanelHeight + 42.dp

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.04f else 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset + 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(if (isCompact) 0.96f else 0.92f)
                        .fillMaxHeight(0.84f)
                        .padding(horizontal = if (isCompact) 8.dp else 16.dp, vertical = 6.dp)
                        .shadow(24.dp, RoundedCornerShape(34.dp))
                        .clip(RoundedCornerShape(34.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(live2dStageTop, live2dStageBottom)
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = live2dFrameColor,
                            shape = RoundedCornerShape(34.dp)
                        )
                        .scale(avatarScale)
                        .alpha(avatarAlpha)
                ) {
                    // 顶部轻微高光
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isDark) 0.06f else 0.18f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = if (isDark) 0.08f else 0.03f)
                                    )
                                )
                            )
                    )

                    Live2DAvatarScreen(
                        modifier = Modifier.fillMaxSize(),
                        model = AvatarModels.DefaultAssistant,
                        backgroundColor = live2dBgColor.toArgb(),
                        replyMotionTrigger = agentAnimateTick,
                        //isDarkTheme = isDark
                    )
                }

                CenterAgentReplyOverlay(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    visible = showCenterReply && !lastAgentMessage?.content.isNullOrBlank(),
                    text = lastAgentMessage?.content.orEmpty(),
                    maxWidth = centerReplyMaxWidth,
                    topInset = topInset + 12.dp,
                    bottomReserved = centerReplyBottomReserved,
                    topColor = centerReplyTopColor,
                    bottomColor = centerReplyBottomColor,
                    borderColor = centerReplyBorderColor,
                    textColor = centerReplyTextColor
                )
            }

            AnimatedVisibility(
                visible = !lastUserMessage?.content.isNullOrBlank(),
                enter = bubbleEnter(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = panelHorizontalPadding,
                        bottom = inputBottomPadding + inputPanelHeight + 18.dp
                    )
            ) {

                MessageBubble(
                    text = lastUserMessage?.content.orEmpty(),
                    maxWidth = bubbleMaxWidth,
                    backgroundColor = userBubbleColor,
                    contentColor = if (isDark) Color.Black else userTextColor,
                    tailOnStart = false,
                    isAgent = false

                )
            }

            ChatInputPanel(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = panelHorizontalPadding,
                        end = panelHorizontalPadding,
                        bottom = inputBottomPadding
                    ),
                text = inputText,
                panelColor = panelBackground,
                onTextChange = { inputText = it },
                onOpenSettings = { showSettings = true },
                onSend = {
                    val content = inputText.trim()
                    if (content.isEmpty()) return@ChatInputPanel

                    messages += ChatMessage(
                        id = System.currentTimeMillis(),
                        role = ChatRole.USER,
                        content = content
                    )
                    inputText = ""

                    scope.launch {
                        val answer = backend.reply(content, userName)
                        agentAnimateTick++
                        messages += ChatMessage(
                            id = System.currentTimeMillis() + 1,
                            role = ChatRole.AGENT,
                            content = answer
                        )

                        showCenterReply = true
                        delay(3000)   // 显示 3 秒
                        showCenterReply = false
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 8.dp,
                        end = 8.dp,
                        top = topInset + 6.dp,
                        bottom = 6.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                CircleIconButton(
                    onClick = { showHistory = true },
                    containerColor = Color.Black.copy(alpha = 0.25f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "历史对话",
                        tint = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleIconButton(
                        onClick = { musicEnabled = !musicEnabled },
                        containerColor = Color.Black.copy(alpha = 0.25f)
                    ) {
                        AnimatedContent(
                            targetState = musicEnabled,
                            transitionSpec = {
                                (scaleIn(tween(180)) + fadeIn()).togetherWith(scaleOut(tween(180)) + fadeOut())
                            },
                            label = "musicIcon"
                        ) { enabled ->
                            Icon(
                                imageVector = if (enabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = if (enabled) "关闭音乐" else "开启音乐",
                                tint = Color.White
                            )
                        }
                    }

                    CircleIconButton(
                        onClick = { showSettings = true },
                        containerColor = Color.Black.copy(alpha = 0.25f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = Color.White
                        )
                    }
                }
            }

            if (showHistory) {
                HistoryOverlay(
                    messages = messages.toList(),
                    userName = userName,
                    onDismiss = { showHistory = false }
                )
            }
        }

        if (showSettings) {
            SettingsDialog(
                userName = userName,
                alpha = panelAlpha,
                currentColorIndex = themeColorIndex,
                colors = themeColors,
                musicEnabled = musicEnabled,
                onUserNameChange = { userName = it },
                onAlphaChange = { panelAlpha = it },
                onColorSelected = { themeColorIndex = it },
                onMusicEnabledChange = { musicEnabled = it },
                onDismiss = { showSettings = false }
            )
        }
    }
}

private fun bubbleEnter(): EnterTransition {
    return fadeIn(animationSpec = tween(220)) +
            slideInVertically(
                animationSpec = tween(280, easing = LinearOutSlowInEasing),
                initialOffsetY = { it / 2 }
            ) +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(220)
            )
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = panelColor),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 54.dp, max = 92.dp),
                placeholder = { Text("请输入内容") },
                shape = RoundedCornerShape(22.dp),
                singleLine = true,
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(8.dp))

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

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = Color.White
                )
            }
        }
    }
}
@Composable
private fun CenterAgentReplyOverlay(
    modifier: Modifier = Modifier,
    visible: Boolean,
    text: String,
    maxWidth: Dp,
    topInset: Dp,
    bottomReserved: Dp,
    topColor: Color,
    bottomColor: Color,
    borderColor: Color,
    textColor: Color
) {
    AnimatedVisibility(
        visible = visible && text.isNotBlank(),
        enter = fadeIn(animationSpec = tween(220)) + scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(260, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(160)) + scaleOut(
            targetScale = 0.96f,
            animationSpec = tween(160)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 28.dp,
                end = 28.dp,
                top = topInset,
                bottom = bottomReserved
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .shadow(18.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(topColor, bottomColor)
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Text(
                    text = text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp)
                )
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
    tailOnStart: Boolean,
    isAgent: Boolean
) {
    Row(verticalAlignment = Alignment.Bottom) {
        if (tailOnStart) {
            BubbleTail(
                color = backgroundColor,
                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
            )
        }

        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 0.dp,
            shadowElevation = 3.dp,
            modifier = Modifier.widthIn(max = maxWidth)
        ) {
            Text(
                text = text,
                color = contentColor,
                style = if (isAgent) {
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
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
    userName: String,
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
            Column(modifier = Modifier.fillMaxSize()) {
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
                            horizontalAlignment = if (message.role == ChatRole.USER) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                text = if (message.role == ChatRole.USER) userName else "智能体",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.64f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (message.role == ChatRole.USER) {
                                    Color(0xFF5B728D).copy(alpha = 0.92f)
                                } else {
                                    Color.White.copy(alpha = 0.94f)
                                },
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Text(
                                    text = message.content,
                                    color = if (message.role == ChatRole.USER) Color.White else Color(0xFF222222),
                                    style = if (message.role == ChatRole.AGENT) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
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
    userName: String,
    alpha: Float,
    currentColorIndex: Int,
    colors: List<Color>,
    musicEnabled: Boolean,
    onUserNameChange: (String) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onColorSelected: (Int) -> Unit,
    onMusicEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
        title = { Text("设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = userName,
                    onValueChange = onUserNameChange,
                    singleLine = true,
                    label = { Text("用户名字") },
                    placeholder = { Text("例如：小明") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("背景音乐", modifier = Modifier.weight(1f))
                    Switch(
                        checked = musicEnabled,
                        onCheckedChange = onMusicEnabledChange
                    )
                }

                Text("输入面板颜色")

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
                    Text("面板透明度：${(alpha * 100).toInt()}%")
                    Slider(
                        value = alpha,
                        onValueChange = onAlphaChange,
                        valueRange = 0.25f..0.9f
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
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}