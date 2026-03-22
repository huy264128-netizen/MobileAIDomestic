package com.projectmaidgroup.mobileaidomestic

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.projectmaidgroup.ui.avatar.AvatarModels
import com.projectmaidgroup.ui.avatar.Live2DAvatarScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Immutable
private data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val content: String,
)

private enum class ChatRole { USER, AGENT }

private interface AgentBackend {
    suspend fun reply(input: String, userName: String): String
}

private class LocalEchoAgent : AgentBackend {
    override suspend fun reply(input: String, userName: String): String {
        delay(450)
        return "收到啦，$userName。关于“$input”，我们可以一起把它拆开慢慢聊。"
    }
}

private class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("live2d_talk_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = sp.getString("user_name", "用户") ?: "用户"
        set(value) = sp.edit { putString("user_name", value) }

    var musicEnabled: Boolean
        get() = sp.getBoolean("music_enabled", true)
        set(value) = sp.edit { putBoolean("music_enabled", value) }
}

@Immutable
private data class StageTheme(
    val pageTop: Color,
    val pageBottom: Color,
    val stageContainer: Color,
    val stageOutline: Color,
    val stageGlow: Color,
    val stageScrim: Color,
    val inputPanel: Color,
    val inputOnPanel: Color,
    val inputMuted: Color,
    val agentBubble: Color,
    val agentBubbleText: Color,
    val userBubble: Color,
    val userBubbleText: Color,
    @param:DrawableRes val lightBoardImageRes: Int? = null,
    @param:DrawableRes val darkBoardImageRes: Int? = null,
    val boardImageAlpha: Float = 1f,
    val boardImageScale: Float = 1f,
)

private val openingLines = listOf(
    "晚上好呀，我已经把小小舞台点亮了。要先聊灵感、日常，还是来一点出其不意的话题？",
    "欢迎回来。这里有故事、有想法，也有一点点神秘感。随便说一句，我都会认真接住。",
    "我在等你开场。今天想探索点新东西，还是把心里那件事慢慢说给我听？",
    "舞台已经准备好啦。丢给我一个词、一句话，或者一个天马行空的念头，我们就能开始。"
)

@Composable
fun Live2DTalk() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()
    val backend = remember { LocalEchoAgent() }
    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme

    val stageThemes = remember(isDark, colorScheme) {
        listOf(
            StageTheme(
                pageTop = if (isDark) colorScheme.surfaceDim else colorScheme.surfaceBright,
                pageBottom = if (isDark) colorScheme.surface else colorScheme.surfaceContainerLowest,
                stageContainer = colorScheme.surfaceContainer.copy(alpha = if (isDark) 0.72f else 0.78f),
                stageOutline = colorScheme.outlineVariant.copy(alpha = if (isDark) 0.62f else 0.84f),
                stageGlow = colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.14f),
                stageScrim = if (isDark) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f),
                inputPanel = colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.88f else 0.94f),
                inputOnPanel = colorScheme.onSurface,
                inputMuted = colorScheme.onSurfaceVariant,
                agentBubble = if (isDark) colorScheme.surfaceBright.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.42f),
                agentBubbleText = colorScheme.onSurface,
                userBubble = colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.68f else 0.82f),
                userBubbleText = colorScheme.onSecondaryContainer,
                lightBoardImageRes = R.drawable.live2d_stage_light,
                darkBoardImageRes = R.drawable.live2d_stage_dark,
                boardImageAlpha = 0.96f,
                boardImageScale = 1.08f
            ),
            StageTheme(
                pageTop = if (isDark) colorScheme.surfaceContainerLow else colorScheme.surfaceBright,
                pageBottom = if (isDark) colorScheme.surface else colorScheme.surfaceContainerLow,
                stageContainer = colorScheme.tertiaryContainer.copy(alpha = if (isDark) 0.24f else 0.30f),
                stageOutline = colorScheme.tertiary.copy(alpha = if (isDark) 0.38f else 0.32f),
                stageGlow = colorScheme.tertiary.copy(alpha = if (isDark) 0.16f else 0.12f),
                stageScrim = if (isDark) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f),
                inputPanel = colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.88f else 0.94f),
                inputOnPanel = colorScheme.onSurface,
                inputMuted = colorScheme.onSurfaceVariant,
                agentBubble = if (isDark) colorScheme.surfaceBright.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.38f),
                agentBubbleText = colorScheme.onSurface,
                userBubble = colorScheme.tertiaryContainer.copy(alpha = if (isDark) 0.66f else 0.80f),
                userBubbleText = colorScheme.onTertiaryContainer,
                lightBoardImageRes = null,
                darkBoardImageRes = null,
                boardImageAlpha = 1f,
                boardImageScale = 1.04f
            )
        )
    }

    val initialGreeting = remember { openingLines[Random.nextInt(openingLines.size)] }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(id = 1L, role = ChatRole.AGENT, content = initialGreeting)
        )
    }

    var inputText by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var themeIndex by rememberSaveable { mutableIntStateOf(0) }
    var panelAlpha by rememberSaveable { mutableFloatStateOf(0.92f) }
    var userName by rememberSaveable { mutableStateOf(prefs.userName) }
    var musicEnabled by rememberSaveable { mutableStateOf(prefs.musicEnabled) }
    var agentAnimateTick by rememberSaveable { mutableIntStateOf(0) }

    val palette = stageThemes[themeIndex % stageThemes.size]
    val panelColor = palette.inputPanel.copy(alpha = panelAlpha)
    val lastUserMessage = messages.lastOrNull { it.role == ChatRole.USER }
    val lastAgentMessage = messages.lastOrNull { it.role == ChatRole.AGENT }

    val avatarScale by animateFloatAsState(
        targetValue = if (agentAnimateTick % 2 == 1) 1.02f else 1f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "avatarScale"
    )
    val avatarAlpha by animateFloatAsState(
        targetValue = if (agentAnimateTick % 2 == 1) 0.98f else 1f,
        animationSpec = tween(320),
        label = "avatarAlpha"
    )

    val floatTransition = rememberInfiniteTransition(label = "stageFloat")
    val boardDriftX by floatTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
        label = "boardDriftX"
    )
    val boardDriftY by floatTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(6800, easing = LinearEasing), RepeatMode.Reverse),
        label = "boardDriftY"
    )
    val avatarFloat by floatTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "avatarFloat"
    )

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

    DisposableEffect(userName, musicEnabled) {
        prefs.userName = userName
        prefs.musicEnabled = musicEnabled
        onDispose { }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.pageTop, palette.pageBottom)))
    ) {
        val screenWidth = maxWidth
        val isCompact = screenWidth < 420.dp
        val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val horizontalPadding = if (isCompact) 12.dp else 20.dp
        val bubbleMaxWidth = if (isCompact) screenWidth * 0.84f else screenWidth * 0.74f
        val inputPanelHeight = if (isCompact) 98.dp else 108.dp
        val inputBottomPadding = bottomInset + 8.dp

        Box(modifier = Modifier.fillMaxSize()) {
            AmbientBackground(
                modifier = Modifier.fillMaxSize(),
                pageTop = palette.pageTop,
                pageBottom = palette.pageBottom,
                glow = palette.stageGlow,
                driftX = boardDriftX,
                driftY = boardDriftY
            )

            AvatarStage(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .fillMaxHeight(0.84f)
                    .padding(horizontal = if (isCompact) 10.dp else 22.dp, vertical = topInset + 12.dp),
                palette = palette,
                isDark = isDark,
                boardDriftX = boardDriftX,
                boardDriftY = boardDriftY,
                avatarFloat = avatarFloat,
                avatarScale = avatarScale,
                avatarAlpha = avatarAlpha,
                replyMotionTrigger = agentAnimateTick
            )

            lastAgentMessage?.let { msg ->
                AgentDialogBubble(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topInset + if (isCompact) 88.dp else 82.dp)
                        .padding(horizontal = 24.dp),
                    text = msg.content,
                    maxWidth = bubbleMaxWidth,
                    backgroundColor = palette.agentBubble,
                    contentColor = palette.agentBubbleText,
                    borderColor = palette.stageOutline
                )
            }

            lastUserMessage?.let { msg ->
                UserBubble(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = topInset + if (isCompact) 460.dp else 420.dp,
                            end = horizontalPadding + 8.dp
                        ),
                    text = msg.content,
                    maxWidth = bubbleMaxWidth * 0.82f,
                    backgroundColor = palette.userBubble,
                    contentColor = palette.userBubbleText,
                    borderColor = palette.stageOutline
                )
            }

            ChatInputPanel(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = horizontalPadding, end = horizontalPadding, bottom = inputBottomPadding),
                text = inputText,
                panelColor = panelColor,
                onPanelColor = palette.inputOnPanel,
                mutedColor = palette.inputMuted,
                accentColor = palette.stageOutline,
                onTextChange = { inputText = it },
                onOpenSettings = { showSettings = true },
                onSend = {
                    val content = inputText.trim()
                    if (content.isEmpty()) return@ChatInputPanel
                    messages += ChatMessage(id = System.currentTimeMillis(), role = ChatRole.USER, content = content)
                    inputText = ""
                    scope.launch {
                        val answer = backend.reply(content, userName)
                        agentAnimateTick++
                        messages += ChatMessage(id = System.currentTimeMillis() + 1, role = ChatRole.AGENT, content = answer)
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = topInset + 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                CircleIconButton(
                    onClick = { showHistory = true },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = "历史对话", tint = MaterialTheme.colorScheme.onSurface)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleIconButton(
                        onClick = { musicEnabled = !musicEnabled },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                    ) {
                        AnimatedContent(
                            targetState = musicEnabled,
                            transitionSpec = {
                                (scaleIn(tween(180)) + fadeIn()).togetherWith(scaleOut(tween(180)) + fadeOut())
                            },
                            label = "musicIcon"
                        ) { enabled ->
                            Icon(
                                imageVector = if (enabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = if (enabled) "关闭音乐" else "开启音乐",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    CircleIconButton(
                        onClick = { showSettings = true },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (showHistory) {
                HistoryOverlay(
                    messages = messages.toList(),
                    userName = userName,
                    agentName = "M.A.I.D.",
                    palette = palette,
                    onDismiss = { showHistory = false }
                )
            }
        }

        if (showSettings) {
            SettingsDialog(
                userName = userName,
                alpha = panelAlpha,
                currentThemeIndex = themeIndex,
                themes = stageThemes,
                musicEnabled = musicEnabled,
                onUserNameChange = { userName = it },
                onAlphaChange = { panelAlpha = it },
                onThemeSelected = { themeIndex = it },
                onMusicEnabledChange = { musicEnabled = it },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
private fun AmbientBackground(
    modifier: Modifier,
    pageTop: Color,
    pageBottom: Color,
    glow: Color,
    driftX: Float,
    driftY: Float
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(colors = listOf(glow.copy(alpha = 0.16f), Color.Transparent), radius = 1200f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (24 + driftX).dp, y = (72 + driftY).dp)
                .size(200.dp)
                .background(Brush.radialGradient(colors = listOf(pageTop.copy(alpha = 0.24f), Color.Transparent)), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-24 + driftX).dp, y = (-64 + driftY).dp)
                .size(220.dp)
                .background(Brush.radialGradient(colors = listOf(pageBottom.copy(alpha = 0.34f), Color.Transparent)), CircleShape)
        )
    }
}

@Composable
private fun AvatarStage(
    modifier: Modifier,
    palette: StageTheme,
    isDark: Boolean,
    boardDriftX: Float,
    boardDriftY: Float,
    avatarFloat: Float,
    avatarScale: Float,
    avatarAlpha: Float,
    replyMotionTrigger: Int
) {
    val boardImageRes = if (isDark) palette.darkBoardImageRes else palette.lightBoardImageRes

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(34.dp),
        color = palette.stageContainer,
        tonalElevation = 4.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, palette.stageOutline)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (boardImageRes != null) {
                Image(
                    painter = painterResource(id = boardImageRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = boardDriftX * 2f
                            translationY = boardDriftY * 2f
                            scaleX = palette.boardImageScale
                            scaleY = palette.boardImageScale
                            alpha = palette.boardImageAlpha
                        },
                    contentScale = ContentScale.Crop
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                palette.stageScrim.copy(alpha = 0.16f),
                                Color.Transparent,
                                palette.stageScrim.copy(alpha = 0.14f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(palette.stageGlow.copy(alpha = 0.28f), Color.Transparent),
                            radius = 1100f
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-16).dp)
                    .fillMaxWidth(0.70f)
                    .height(76.dp)
                    .background(
                        Brush.radialGradient(colors = listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent)),
                        RoundedCornerShape(100.dp)
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .offset(y = avatarFloat.dp)
                    .scale(avatarScale)
                    .alpha(avatarAlpha)
            ) {
                Live2DAvatarScreen(
                    modifier = Modifier.fillMaxSize(),
                    model = AvatarModels.DefaultAssistant,
                    backgroundColor = Color.Transparent.toArgb(),
                    replyMotionTrigger = replyMotionTrigger
                )
            }
        }
    }
}

@Composable
private fun AgentDialogBubble(
    modifier: Modifier = Modifier,
    text: String,
    maxWidth: Dp,
    backgroundColor: Color,
    contentColor: Color,
    borderColor: Color
) {
    Surface(
        modifier = modifier.widthIn(max = maxWidth),
        color = backgroundColor,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "M.A.I.D.",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun UserBubble(
    modifier: Modifier = Modifier,
    text: String,
    maxWidth: Dp,
    backgroundColor: Color,
    contentColor: Color,
    borderColor: Color
) {
    Surface(
        modifier = modifier.widthIn(max = maxWidth),
        color = backgroundColor,
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.22f))
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun ChatInputPanel(
    modifier: Modifier = Modifier,
    text: String,
    panelColor: Color,
    onPanelColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onTextChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = panelColor),
        shape = RoundedCornerShape(30.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.34f))
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
                    .heightIn(min = 56.dp, max = 96.dp),
                placeholder = { Text("请输入内容", color = mutedColor) },
                shape = RoundedCornerShape(22.dp),
                singleLine = true,
                maxLines = 1,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = onPanelColor,
                    unfocusedTextColor = onPanelColor,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = accentColor.copy(alpha = 0.75f),
                    unfocusedIndicatorColor = accentColor.copy(alpha = 0.35f),
                    cursorColor = accentColor,
                    focusedPlaceholderColor = mutedColor,
                    unfocusedPlaceholderColor = mutedColor
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            CircleIconButton(
                onClick = onSend,
                containerColor = accentColor.copy(alpha = 0.20f)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = onPanelColor)
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "设置", tint = onPanelColor)
            }
        }
    }
}

@Composable
private fun HistoryOverlay(
    messages: List<ChatMessage>,
    userName: String,
    agentName: String,
    palette: StageTheme,
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
            colors = CardDefaults.cardColors(containerColor = palette.inputPanel.copy(alpha = 0.98f)),
            border = BorderStroke(1.dp, palette.stageOutline.copy(alpha = 0.34f))
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
                        color = palette.inputOnPanel,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭历史对话", tint = palette.inputOnPanel)
                    }
                }
                HorizontalDivider(color = palette.inputOnPanel.copy(alpha = 0.10f))
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
                                text = if (message.role == ChatRole.USER) userName else agentName,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.inputMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = if (message.role == ChatRole.USER) palette.userBubble else palette.agentBubble,
                                modifier = Modifier.widthIn(max = 320.dp),
                                border = BorderStroke(1.dp, palette.stageOutline.copy(alpha = 0.24f))
                            ) {
                                Text(
                                    text = message.content,
                                    color = if (message.role == ChatRole.USER) palette.userBubbleText else palette.agentBubbleText,
                                    style = MaterialTheme.typography.bodyMedium,
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
    currentThemeIndex: Int,
    themes: List<StageTheme>,
    musicEnabled: Boolean,
    onUserNameChange: (String) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onThemeSelected: (Int) -> Unit,
    onMusicEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
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
                    Switch(checked = musicEnabled, onCheckedChange = onMusicEnabledChange)
                }
                Text("舞台主题")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    themes.forEachIndexed { index, theme ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    Brush.linearGradient(listOf(theme.pageTop, theme.pageBottom)),
                                    CircleShape
                                )
                                .border(
                                    width = if (currentThemeIndex == index) 3.dp else 1.dp,
                                    color = if (currentThemeIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable { onThemeSelected(index) }
                        )
                    }
                }
                Column {
                    Text("输入面板透明度：${(alpha * 100).toInt()}%")
                    Slider(
                        value = alpha,
                        onValueChange = onAlphaChange,
                        valueRange = 0.30f..0.92f
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
