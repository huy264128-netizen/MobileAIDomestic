package com.projectmaidgroup.mobileaidomestic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 本地回声，用于无密钥或调试。
 */
internal class LocalEchoAgent : AgentBackend {
    override suspend fun reply(input: String, userName: String): AgentReply {
        delay(450)
        return AgentReply(
            text = "收到啦，$userName。关于“$input”，我们可以一起把它拆开慢慢聊。",
        )
    }
}

/**
 * 直连蓝心 OpenAI 兼容接口（无 LangChain4j、无工具调用）。
 */
internal class RemoteVivoBlueLMAgent(
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
                text = "蓝心大模型 API 还没有配置完整。请在 local.properties 里填写 VIVO_AIGC_API_KEY 或 VIVO_AIGC_APP_KEY。",
            )
        }

        runCatching {
            val bodyJson = JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                        put(JSONObject().put("role", "user").put("content", "用户昵称：$userName\n用户消息：$input"))
                    },
                )
                .put("temperature", 0.7)
                .put("max_tokens", 1024)
                .put("stream", false)

            var lastAuthError: AgentReply? = null

            for (candidateAppKey in auth.appKeyCandidates) {
                val request = buildOpenAiCompatRequest(
                    url = baseUrl,
                    body = bodyJson.toString(),
                    appKey = candidateAppKey,
                )

                client.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Vivo AIGC API failed: HTTP ${response.code}, body=$rawBody")
                        val reply = AgentReply(
                            text = httpErrorMessage(response.code, rawBody),
                        )

                        if (response.code == 401 || response.code == 403) {
                            lastAuthError = reply
                            return@use
                        } else {
                            return@withContext reply
                        }
                    }

                    val parsed = extractAssistantContent(rawBody)

                    if (parsed.errorMessage != null) {
                        Log.w(TAG, "Vivo AIGC API returned error: ${parsed.errorMessage}, body=$rawBody")
                        return@withContext AgentReply(text = parsed.errorMessage)
                    }

                    val assistantContent = parsed.content.orEmpty()

                    if (assistantContent.isBlank()) {
                        Log.w(TAG, "Vivo AIGC API empty content: $rawBody")
                        return@withContext AgentReply(text = "蓝心接口返回了空内容，请稍后再试。")
                    }

                    return@withContext parseAgentReply(assistantContent)
                }
            }

            lastAuthError ?: AgentReply(
                text = "蓝心接口认证失败。请确认 local.properties 中填的是有效 AppKey；如果使用 sk-xuanji-APPID-xxx== 组合 key，请不要缺少最后的 ==。",
            )
        }.getOrElse { error ->
            Log.e(TAG, "Vivo AIGC API error", error)
            AgentReply(
                text = "我连接蓝心接口时出错：${error.message ?: "未知错误"}",
            )
        }
    }

    private fun buildOpenAiCompatRequest(
        url: String,
        body: String,
        appKey: String,
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
            401, 403 -> "蓝心接口认证失败：HTTP $httpCode。请检查 AppKey 是否完整、是否过期。${if (serverMessage.isNotBlank()) " 服务端信息：$serverMessage" else ""}"
            404 -> "蓝心接口地址或模型不存在：HTTP 404。请检查 VIVO_AIGC_BASE_URL 和 VIVO_AIGC_MODEL。${if (serverMessage.isNotBlank()) " 服务端信息：$serverMessage" else ""}"
            429 -> "蓝心接口请求过快或额度不足：HTTP 429。请稍后再试。${if (serverMessage.isNotBlank()) " 服务端信息：$serverMessage" else ""}"
            else -> "蓝心接口请求失败：HTTP $httpCode。${if (serverMessage.isNotBlank()) " 服务端信息：$serverMessage" else rawBody.take(200)}"
        }
    }

    private fun extractAssistantContent(rawBody: String): ApiParsedResult {
        return runCatching {
            val root = JSONObject(rawBody)

            val errorObj = root.optJSONObject("error")
            if (errorObj != null) {
                return@runCatching ApiParsedResult(
                    errorMessage = errorObj.optString("message").ifBlank { errorObj.toString() },
                )
            }

            val choices = root.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.optJSONObject(0)
                val messageContent = firstChoice
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()

                if (messageContent.isNotBlank()) {
                    return@runCatching ApiParsedResult(content = messageContent)
                }

                val deltaContent = firstChoice
                    ?.optJSONObject("delta")
                    ?.optString("content")
                    .orEmpty()

                if (deltaContent.isNotBlank()) {
                    return@runCatching ApiParsedResult(content = deltaContent)
                }
            }

            ApiParsedResult(
                content = root.optJSONObject("data")?.optString("content")
                    ?: root.optString("content"),
            )
        }.getOrElse { error ->
            ApiParsedResult(errorMessage = "蓝心接口返回内容解析失败：${error.message ?: "未知错误"}")
        }
    }

    private data class ApiParsedResult(
        val content: String? = null,
        val errorMessage: String? = null,
    )

    private fun parseAgentReply(rawContent: String): AgentReply {
        val cleaned = rawContent.stripJsonFence()
        val json = runCatching { JSONObject(cleaned) }.getOrNull()
        val text = json?.optString("reply")?.takeIf { it.isNotBlank() } ?: cleaned
        return AgentReply(text = text)
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
"""

        private fun appendQuery(url: String, key: String, value: String): String {
            val separator = if (url.contains("?")) "&" else "?"
            return url + separator + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }
    }
}
