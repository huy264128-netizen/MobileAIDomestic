package com.projectmaidgroup.mobileaidomestic

import android.util.Log
import com.projectmaidgroup.mobileaidomestic.agent.MaidAssistant
import com.projectmaidgroup.mobileaidomestic.agent.ShizukuTooling
import com.projectmaidgroup.platform.shizuku_for_maid.ShizukuManager
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.AiServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * 使用 LangChain4j（OpenAI 兼容 Chat + AiServices）驱动对话，并注册基于 Shizuku 的本地工具。
 */
internal class LangChain4jShizukuAgent(
    private val apiKey: String = BuildConfig.VIVO_AIGC_API_KEY,
    private val appKey: String = BuildConfig.VIVO_AIGC_APP_KEY,
    private val configuredBaseUrl: String = BuildConfig.VIVO_AIGC_BASE_URL,
    private val modelName: String = BuildConfig.VIVO_AIGC_MODEL.ifBlank { "Doubao-Seed-2.0-mini" },
) : AgentBackend {

    private val memory = MessageWindowChatMemory.withMaxMessages(24)
    private val shizukuTools = ShizukuTooling()

    override suspend fun reply(input: String, userName: String): AgentReply = withContext(Dispatchers.IO) {
        ShizukuManager.serviceState.value

        val auth = VivoAigcAuth.from(apiKey = apiKey, appKey = appKey)
        if (!auth.isValid) {
            return@withContext AgentReply(
                text = "蓝心大模型 API 还没有配置完整。请在 local.properties 里填写 VIVO_AIGC_API_KEY 或 VIVO_AIGC_APP_KEY。",
            )
        }

        val base = normalizeOpenAiBaseUrl(configuredBaseUrl)
        var lastError: String? = null

        for (candidateKey in auth.appKeyCandidates) {
            val result = runCatching {
                val model = OpenAiChatModel.builder()
                    .apiKey(candidateKey)
                    .baseUrl(base)
                    .modelName(modelName)
                    .temperature(0.35)
                    .timeout(Duration.ofSeconds(90))
                    .maxRetries(1)
                    .logRequests(false)
                    .logResponses(false)
                    .build()

                val assistant = AiServices.builder(MaidAssistant::class.java)
                    .chatLanguageModel(model)
                    .chatMemory(memory)
                    .tools(shizukuTools)
                    .build()

                assistant.chat(userName, input).trim()
            }
            result.onSuccess { text ->
                if (text.isNotBlank()) {
                    return@withContext AgentReply(text = text)
                }
                lastError = "模型返回了空文本"
            }
            result.onFailure { e ->
                Log.w(TAG, "LangChain4j / OpenAI 调用失败，尝试下一候选密钥", e)
                lastError = e.message ?: e::class.java.simpleName
            }
        }

        AgentReply(
            text = lastError?.let { err -> "调用大模型失败：$err。请检查网络与密钥，或查看 Logcat（$TAG）。" }
                ?: "调用大模型失败，请稍后再试。",
        )
    }

    companion object {
        private const val TAG = "LangChain4jShizukuAgent"

        internal fun normalizeOpenAiBaseUrl(raw: String): String {
            val u = raw.trim().ifBlank { "https://api-ai.vivo.com.cn/v1/chat/completions" }
            val noTrail = u.trimEnd('/')
            return when {
                noTrail.endsWith("/chat/completions", ignoreCase = true) ->
                    noTrail.removeSuffix("/chat/completions").trimEnd('/')
                noTrail.endsWith("/v1", ignoreCase = true) -> noTrail
                noTrail.startsWith("http", ignoreCase = true) -> noTrail
                else -> "https://api-ai.vivo.com.cn/v1"
            }
        }
    }
}
