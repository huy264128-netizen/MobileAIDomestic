package com.projectmaidgroup.mobileaidomestic

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.projectmaidgroup.ui.avatar.AvatarEmotion
import com.projectmaidgroup.ui.avatar.AvatarModels
import com.projectmaidgroup.ui.avatar.EmotionJudge
import com.projectmaidgroup.ui.avatar.Live2DAvatarScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

@Immutable
private data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

private enum class ChatRole { USER, AGENT }

private data class AgentReply(
    val text: String,
    val emotion: AvatarEmotion = AvatarEmotion.NEUTRAL
)

private interface AgentBackend {
    suspend fun reply(input: String, userName: String): AgentReply
}

private class LocalEchoAgent : AgentBackend {
    override suspend fun reply(input: String, userName: String): AgentReply {
        delay(250)
        val text = "收到，$userName：$input"
        return AgentReply(text = text, emotion = EmotionJudge.guess(text))
    }
}

/**
 * vivo AIGC / 蓝心大模型 OpenAI-compatible 接入层。
 *
 * 当前文档要求：
 * POST https://api-ai.vivo.com.cn/v1/chat/completions?request_id={uuid}
 * Header: Authorization: Bearer {AppKey}
 * Body: OpenAI Chat Completions 格式：{"model":"Doubao-Seed-2.0-mini","messages":[...]}
 *
 * 推荐只在 local.properties 中配置：
 * VIVO_AIGC_API_KEY=sk-xuanji-你的AppID-base64编码后的AppKey
 * 代码会优先从组合 key 中提取并 Base64 解码最后一段作为 Bearer AppKey。
 * 也兼容直接配置：VIVO_AIGC_APP_KEY=你的真实AppKey。
 */
private class RemoteVivoBlueLMAgent(
    private val apiKey: String = BuildConfig.VIVO_AIGC_API_KEY,
    private val appKey: String = BuildConfig.VIVO_AIGC_APP_KEY,
    private val baseUrl: String = BuildConfig.VIVO_AIGC_BASE_URL.ifBlank { DEFAULT_CHAT_URL },
    private val model: String = BuildConfig.VIVO_AIGC_MODEL.ifBlank { DEFAULT_MODEL },
) : AgentBackend {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun reply(input: String, userName: String): AgentReply = withContext(Dispatchers.IO) {
        val auth = VivoAigcAuth.from(apiKey = apiKey, appKey = appKey)
        if (!auth.isValid) {
            return@withContext AgentReply(
                text = "蓝心大模型 API 还没有配置完整。请在 local.properties 里填写 VIVO_AIGC_API_KEY，或直接填写 VIVO_AIGC_APP_KEY。",
                emotion = AvatarEmotion.CONFUSED
            )
        }

        runCatching {
            val bodyJson = JSONObject()
                .put("model", model)
                .put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    put(JSONObject().put("role", "user").put("content", "用户昵称：$userName\n用户消息：$input"))
                })
                .put("temperature", 0.7)
                .put("max_tokens", 1024)
                .put("stream", false)

            var lastAuthError: AgentReply? = null
            for (candidateAppKey in auth.appKeyCandidates) {
                val request = buildOpenAiCompatRequest(
                    url = baseUrl,
                    body = bodyJson.toString(),
                    appKey = candidateAppKey
                )

                client.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Vivo AIGC API failed: HTTP ${response.code}, body=$rawBody")
                        val text = httpErrorMessage(response.code, rawBody)
                        val reply = AgentReply(text = text, emotion = AvatarEmotion.SAD)
                        if (response.code == 401 || response.code == 403) {
                            lastAuthError = reply
                        } else {
                            return@withContext reply
                        }
                    } else {
                        val parsed = extractAssistantContent(rawBody)
                        if (parsed.errorMessage != null) {
                            Log.w(TAG, "Vivo AIGC API returned error: ${parsed.errorMessage}, body=$rawBody")
                            return@withContext AgentReply(
                                text = parsed.errorMessage,
                                emotion = if (parsed.errorMessage.contains("model", ignoreCase = true) || parsed.errorMessage.contains("权限")) {
                                    AvatarEmotion.CONFUSED
                                } else {
                                    AvatarEmotion.SAD
                                }
                            )
                        }

                        val assistantContent = parsed.content.orEmpty()
                        if (assistantContent.isBlank()) {
                            Log.w(TAG, "Vivo AIGC API empty content: $rawBody")
                            return@withContext AgentReply(
                                text = "蓝心接口返回了空内容，请稍后再试。",
                                emotion = AvatarEmotion.CONFUSED
                            )
                        }
                        return@withContext parseAgentReply(assistantContent)
                    }
                }
            }

            lastAuthError ?: AgentReply(
                text = "蓝心接口认证失败。请确认 local.properties 中填的是有效 AppKey；如果使用 sk-xuanji-APPID-xxx== 组合 key，请不要缺少最后的 ==。",
                emotion = AvatarEmotion.SAD
            )
        }.getOrElse { error ->
            Log.e(TAG, "Vivo AIGC API error", error)
            AgentReply(
                text = "我连接蓝心接口时出错：${error.message ?: "未知错误"}",
                emotion = AvatarEmotion.SAD
            )
        }
    }

    private fun buildOpenAiCompatRequest(
        url: String,
        body: String,
        appKey: String
    ): Request {
        val requestId = UUID.randomUUID().toString()
        val requestUrl = appendQuery(url, "request_id", requestId)
        return Request.Builder()
            .url(requestUrl)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $appKey")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun httpErrorMessage(httpCode: Int, rawBody: String): String {
        val serverMessage = runCatching {
            val root = JSONObject(rawBody)
            root.optJSONObject("error")?.optString("message")
                ?: root.optString("msg")
                ?: root.optString("message")
        }.getOrDefault("").orEmpty()

        return when (httpCode) {
            401, 403 -> "蓝心接口认证失败：HTTP $httpCode。请检查 AppKey 是否完整、是否过期，以及 Authorization 是否应使用真实 AppKey。${if (serverMessage.isNotBlank()) " 服务端信息：$serverMessage" else ""}"
            429 -> "蓝心接口触发限流：HTTP 429。请稍后再试，或降低请求频率。${if (serverMessage.isNotBlank()) " 服务端信息：$serverMessage" else ""}"
            else -> "蓝心接口请求失败：HTTP $httpCode。${if (serverMessage.isNotBlank()) "服务端信息：$serverMessage" else "请检查接口地址、模型名或网络。"}"
        }
    }

    private fun extractAssistantContent(rawBody: String): ApiParsedResult {
        return runCatching {
            val root = JSONObject(rawBody)

            val openAiError = root.optJSONObject("error")
            if (openAiError != null) {
                val message = openAiError.optString("message").ifBlank { openAiError.toString() }
                return@runCatching ApiParsedResult(errorMessage = "蓝心接口返回错误：$message")
            }

            val code = root.optInt("code", 0)
            if (code != 0) {
                val msg = root.optString("msg")
                    .ifBlank { root.optString("message") }
                    .ifBlank { root.optString("data") }
                    .ifBlank { "code=$code" }
                val suggestion = when {
                    code == 30001 && msg.contains("model", ignoreCase = true) -> "。这个通常是模型没有权限，请在 local.properties 里把 VIVO_AIGC_MODEL 改成你账号有权限的模型，例如 Doubao-Seed-2.0-mini、Doubao-Seed-2.0-lite、Doubao-Seed-2.0-pro、Volc-DeepSeek-V3.2 或 qwen3.5-plus。"
                    code == 30001 -> "。这个通常是权限到期、模型无权限或触发限流。"
                    code == 2003 -> "。今天用量可能已达上限，请明天再试。"
                    code == 1001 -> "。请求参数异常，请检查 request_id、model、messages。"
                    else -> ""
                }
                return@runCatching ApiParsedResult(errorMessage = "蓝心接口返回错误：$msg$suggestion")
            }

            val choices = root.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val first = choices.optJSONObject(0)
                val messageContent = first
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (messageContent.isNotBlank()) {
                    return@runCatching ApiParsedResult(content = messageContent)
                }
                val deltaContent = first
                    ?.optJSONObject("delta")
                    ?.optString("content")
                    .orEmpty()
                if (deltaContent.isNotBlank()) {
                    return@runCatching ApiParsedResult(content = deltaContent)
                }
            }

            ApiParsedResult(
                content = root.optJSONObject("data")?.optString("content")
                    ?: root.optString("content")
            )
        }.getOrElse { error ->
            ApiParsedResult(errorMessage = "蓝心接口返回内容解析失败：${error.message ?: "未知错误"}")
        }
    }

    private data class ApiParsedResult(
        val content: String? = null,
        val errorMessage: String? = null
    )

    private fun parseAgentReply(rawContent: String): AgentReply {
        val cleaned = rawContent.stripJsonFence()
        val json = runCatching { JSONObject(cleaned) }.getOrNull()
        val text = json?.optString("reply")?.takeIf { it.isNotBlank() } ?: cleaned
        val emotion = parseAvatarEmotion(json?.optString("emotion"), text)
        return AgentReply(text = text, emotion = emotion)
    }

    private fun parseAvatarEmotion(value: String?, fallbackText: String): AvatarEmotion {
        return when (value?.trim()?.uppercase(Locale.ROOT)) {
            "NEUTRAL" -> AvatarEmotion.NEUTRAL
            "HAPPY" -> AvatarEmotion.HAPPY
            "SAD" -> AvatarEmotion.SAD
            "ANGRY" -> AvatarEmotion.ANGRY
            "SHY" -> AvatarEmotion.SHY
            "SURPRISED" -> AvatarEmotion.SURPRISED
            "THINKING" -> AvatarEmotion.THINKING
            "CONFUSED" -> AvatarEmotion.CONFUSED
            "EXCITED" -> AvatarEmotion.EXCITED
            else -> EmotionJudge.guess(fallbackText)
        }
    }

    private fun String.stripJsonFence(): String {
        val value = trim()
        if (!value.startsWith("```")) return value

        val lines = value.split('\n').toMutableList()
        if (lines.isNotEmpty()) {
            lines.removeAt(0)
        }
        while (lines.isNotEmpty() && lines.last().trim() == "```") {
            lines.removeAt(lines.lastIndex)
        }
        return lines.joinToString("\n").trim()
    }

    private data class VivoAigcAuth(
        val appKeyCandidates: List<String>
    ) {
        val isValid: Boolean get() = appKeyCandidates.isNotEmpty()

        companion object {
            fun from(apiKey: String, appKey: String): VivoAigcAuth {
                val candidates = linkedSetOf<String>()

                appKey.trim().takeIf { it.isNotBlank() }?.let { candidates += it }

                val rawApiKey = apiKey.trim()
                if (rawApiKey.isNotBlank()) {
                    val parts = rawApiKey.split("-", limit = 4)
                    if (parts.size == 4 && parts[0] == "sk" && parts[1].contains("xuanji", ignoreCase = true)) {
                        val encodedOrRawAppKey = parts[3].trim()
                        decodeBase64Utf8(encodedOrRawAppKey)?.let { candidates += it }
                        candidates += encodedOrRawAppKey
                    }
                    candidates += rawApiKey
                }

                return VivoAigcAuth(appKeyCandidates = candidates.filter { it.isNotBlank() })
            }

            private fun decodeBase64Utf8(value: String): String? {
                return runCatching {
                    String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8).trim()
                }.getOrNull()?.takeIf { it.isNotBlank() && !it.contains('\u0000') }
            }
        }
    }

    companion object {
        private const val TAG = "RemoteVivoBlueLMAgent"
        private const val DEFAULT_CHAT_URL = "https://api-ai.vivo.com.cn/v1/chat/completions"
        private const val DEFAULT_MODEL = "Doubao-Seed-2.0-mini"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val SYSTEM_PROMPT = """
你是一个可爱的 Live2D 手机助手，需要用中文自然回答用户。
你必须只返回一段 JSON，不能添加 Markdown、解释或代码块。
JSON 格式固定为：{"reply":"回复文本","emotion":"HAPPY"}
reply 是显示在聊天气泡里并朗读出来的文本，尽量简洁、口语化。
emotion 只能从这些值里选择一个：NEUTRAL、HAPPY、SAD、ANGRY、SHY、SURPRISED、THINKING、CONFUSED、EXCITED。
Live2D 动作会由客户端根据 emotion 自动匹配，你不要返回 motion 文件名或表情资源名。
"""

        private fun appendQuery(url: String, key: String, value: String): String {
            val separator = if (url.contains("?")) "&" else "?"
            return url + separator + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }
    }
}

private class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("live2d_talk_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = sp.getString("user_name", "用户") ?: "用户"
        set(value) = sp.edit().putString("user_name", value).apply()

    var voiceEnabled: Boolean
        get() = sp.getBoolean("voice_enabled", true)
        set(value) = sp.edit().putBoolean("voice_enabled", value).apply()
}

private class AppTts(context: Context) {
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.language = Locale.CHINESE
                tts?.setSpeechRate(1.03f)
                tts?.setPitch(1.05f)
            }
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent-${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}

@Composable
fun Live2DTalk() {
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    val scope = rememberCoroutineScope()
    val backend = remember { RemoteVivoBlueLMAgent() }
    val tts = remember(context) { AppTts(context) }

    val isDark = isSystemInDarkTheme()
    val bgTop = if (isDark) Color(0xFF102033) else Color(0xFFE5EEF8)
    val bgBottom = if (isDark) Color(0xFF07111D) else Color(0xFFD7E3F2)
    val live2dBgColor = bgBottom
    val uiIsDark = !isDark
    val panelColor = if (uiIsDark) Color(0xFF1F2733) else Color(0xFFF8FAFD)
    val panelTextColor = if (uiIsDark) Color.White else Color(0xFF1A2433)
    val agentBubbleColor = if (uiIsDark) Color(0xFF2A3442) else Color.White
    val agentTextColor = if (uiIsDark) Color.White else Color(0xFF1A2433)
    val userBubbleColor = if (uiIsDark) Color(0xFF4D657E) else Color(0xFFDCE6F2)
    val userTextColor = if (uiIsDark) Color.White else Color(0xFF1A2433)

    val messages = remember {
        mutableStateListOf(
            ChatMessage(id = 1L, role = ChatRole.AGENT, content = "你好，我已经准备好了。")
        )
    }

    var inputText by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var panelAlpha by rememberSaveable { mutableFloatStateOf(0.64f) }
    var userName by rememberSaveable { mutableStateOf(prefs.userName) }
    var voiceEnabled by rememberSaveable { mutableStateOf(prefs.voiceEnabled) }
    var agentAnimateTick by rememberSaveable { mutableIntStateOf(0) }
    var avatarEmotionTrigger by rememberSaveable { mutableIntStateOf(0) }
    var avatarEmotion by remember { mutableStateOf(AvatarEmotion.NEUTRAL) }

    val panelBackground = panelColor.copy(alpha = panelAlpha)
    val lastUserMessage = messages.lastOrNull { it.role == ChatRole.USER }
    val lastAgentMessage = messages.lastOrNull { it.role == ChatRole.AGENT }

    DisposableEffect(tts) {
        onDispose { tts.shutdown() }
    }
    LaunchedEffect(userName) { prefs.userName = userName }
    LaunchedEffect(voiceEnabled) { prefs.voiceEnabled = voiceEnabled }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
    ) {
        val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val panelHorizontalPadding = 16.dp
        val bubbleMaxWidth = 300.dp
        val inputPanelHeight = 96.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset + 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .alpha(0.99f)
            ) {
                Live2DAvatarScreen(
                    modifier = Modifier.fillMaxSize(),
                    model = AvatarModels.DefaultAssistant,
                    backgroundColor = live2dBgColor.toArgb(),
                    replyMotionTrigger = agentAnimateTick,
                    emotion = avatarEmotion,
                    emotionTrigger = avatarEmotionTrigger
                )
            }

            AnimatedVisibility(
                visible = !lastAgentMessage?.content.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = topInset + 88.dp, start = 20.dp)
            ) {
                MessageBubble(
                    text = lastAgentMessage?.content.orEmpty(),
                    maxWidth = bubbleMaxWidth,
                    backgroundColor = agentBubbleColor,
                    contentColor = agentTextColor,
                    tailOnStart = true,
                    isAgent = true
                )
            }
        }

        AnimatedVisibility(
            visible = !lastUserMessage?.content.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = panelHorizontalPadding,
                    bottom = bottomInset + inputPanelHeight + 26.dp
                )
        ) {
            MessageBubble(
                text = lastUserMessage?.content.orEmpty(),
                maxWidth = bubbleMaxWidth,
                backgroundColor = userBubbleColor,
                contentColor = userTextColor,
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
                    bottom = bottomInset + 8.dp
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
                    val result = backend.reply(content, userName)
                    avatarEmotion = result.emotion
                    avatarEmotionTrigger++
                    agentAnimateTick++
                    messages += ChatMessage(
                        id = System.currentTimeMillis() + 1,
                        role = ChatRole.AGENT,
                        content = result.text
                    )
                    if (voiceEnabled) tts.speak(result.text)
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = topInset + 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            CircleIconButton(onClick = { showHistory = true }) {
                Icon(Icons.Default.AccessTime, contentDescription = "历史对话", tint = Color.White)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleIconButton(onClick = { voiceEnabled = !voiceEnabled }) {
                    Icon(
                        imageVector = if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = if (voiceEnabled) "关闭语音" else "开启语音",
                        tint = Color.White
                    )
                }
                CircleIconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color.White)
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

        if (showSettings) {
            SettingsDialog(
                userName = userName,
                alpha = panelAlpha,
                voiceEnabled = voiceEnabled,
                onUserNameChange = { userName = it },
                onAlphaChange = { panelAlpha = it },
                onVoiceEnabledChange = { voiceEnabled = it },
                onDismiss = { showSettings = false },
                panelTextColor = panelTextColor
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
    onSend: () -> Unit,
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
            CircleIconButton(onClick = onSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color.White)
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
    isAgent: Boolean,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        if (tailOnStart) BubbleTail(backgroundColor, Modifier.padding(end = 4.dp, bottom = 4.dp))
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
                style = if (isAgent) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        if (!tailOnStart) BubbleTail(backgroundColor, Modifier.padding(start = 4.dp, bottom = 4.dp))
    }
}

@Composable
private fun BubbleTail(color: Color, modifier: Modifier = Modifier) {
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
    onDismiss: () -> Unit,
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
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F24).copy(alpha = 0.94f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("历史对话", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭历史对话", tint = Color.White)
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
                                color = if (message.role == ChatRole.USER) Color(0xFF5B728D).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.94f),
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
    voiceEnabled: Boolean,
    onUserNameChange: (String) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onVoiceEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    panelTextColor: Color,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("AI 语音朗读", modifier = Modifier.weight(1f), color = panelTextColor)
                    Switch(checked = voiceEnabled, onCheckedChange = onVoiceEnabledChange)
                }
                Column {
                    Text("输入面板透明度：${(alpha * 100).toInt()}%", color = panelTextColor)
                    Slider(value = alpha, onValueChange = onAlphaChange, valueRange = 0.25f..0.9f)
                }
            }
        }
    )
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.25f),
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
