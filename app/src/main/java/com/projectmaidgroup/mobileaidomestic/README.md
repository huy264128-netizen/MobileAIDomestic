# Live2D Talk 集成说明（给后端 / AI / 其他客户端同学）

本文档说明如何将 **真实 AI 对话能力** 接入当前的 `Live2dTalk` UI，使 Live2D 角色从“本地回声”升级为“智能体对话”。

---

# 1. 当前状态说明

当前 UI 已完成：

- Live2D 渲染（TextureView 方案，支持透明 + 正确层级）
- 对话 UI（用户气泡 / MAID 气泡）
- 输入框 + 历史记录
- 本地模拟 Agent：

```kotlin
private class LocalEchoAgent : AgentBackend {
    override suspend fun reply(input: String, userName: String): String {
        delay(450)
        return "收到，$userName：$input"
    }
}
```

👉 现在要做的：把这个 **LocalEchoAgent 替换成真实 AI API**。

---

# 2. 核心接入点（最重要）

所有 AI 接入只需要实现这个接口：

```kotlin
private interface AgentBackend {
    suspend fun reply(input: String, userName: String): String
}
```

UI 层完全不关心你用：

- OpenAI / Azure / 自建模型
- HTTP / WebSocket
- 流式 / 非流式

只要返回一个 `String` 即可。

---

# 3. 推荐实现方式（标准版）

## 3.1 创建真实 Agent

```kotlin
class RemoteAgent(
    private val api: ChatApi
) : AgentBackend {

    override suspend fun reply(input: String, userName: String): String {
        return try {
            val response = api.chat(
                ChatRequest(
                    user = userName,
                    message = input
                )
            )
            response.text
        } catch (e: Exception) {
            "网络有点问题，我们稍后再聊～"
        }
    }
}
```

---

## 3.2 API 示例（REST）

```kotlin
interface ChatApi {
    @POST("/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}


data class ChatRequest(
    val user: String,
    val message: String
)


data class ChatResponse(
    val text: String
)
```

---

## 3.3 在 UI 中替换

找到：

```kotlin
val backend = remember { LocalEchoAgent() }
```

改成：

```kotlin
val backend = remember { RemoteAgent(api) }
```

即可完成接入。

---

# 4. 对话触发流程（时序）

```text
用户输入 → 点击发送
        ↓
Live2dTalk
        ↓
backend.reply()
        ↓
网络请求
        ↓
返回 AI 文本
        ↓
UI 添加消息
        ↓
触发 Live2D 动作
```

关键代码：

```kotlin
scope.launch {
    val answer = backend.reply(content, userName)
    agentAnimateTick++
    messages += ChatMessage(..., role = AGENT, content = answer)
}
```

---

# 5. Live2D 动作联动（重要）

AI 回复后会触发：

```kotlin
agentAnimateTick++
```

它会：

- 驱动 `Live2DAvatarScreen`
- 调用：

```kotlin
view.playReplyMotion()
```

👉 如果你想做更高级控制：

可以扩展为：

```kotlin
reply(text: String): AgentResult
```

```kotlin
data class AgentResult(
    val text: String,
    val motion: String?
)
```

然后：

- 开心 → 笑
- 生气 → angry motion

---

# 6. 可选增强（推荐后期做）

## 6.1 流式输出（打字机效果）

替换：

```kotlin
suspend fun reply(...): String
```

为：

```kotlin
fun replyStream(...): Flow<String>
```

UI 每次 append 字符。

---

## 6.2 记忆 / 上下文

当前：无上下文

建议：

```kotlin
messages.map {
    role + content
}
```

传给后端。

---

## 6.3 情绪驱动动画

AI 返回：

```json
{
  "text": "今天也很开心！",
  "emotion": "happy"
}
```

UI 根据 emotion 控制 Live2D motion。

---

# 7. 常见坑（非常重要）

### ❌ 不要在 UI 线程直接请求网络

必须用：

```kotlin
scope.launch
```

---

### ❌ 不要在 reply 里操作 UI

Agent 只负责返回数据。

---

### ❌ 不要返回 null

统一返回 String，必要时给 fallback。

---

### ⚠️ 超时处理

建议：

```kotlin
withTimeout(10_000)
```

---

# 8. 最小可运行接入（总结）

只需要做三件事：

1. 写一个 `RemoteAgent`
2. 实现 `reply()` 调 API
3. 替换 `LocalEchoAgent`

完成。

---

# 9. 联系方式 / 说明

如果接入后出现：

- 气泡不显示 → UI 层问题
- Live2D 不动 → motion trigger 问题
- 有文字但卡住 → 协程 / API 问题

请带日志反馈。

---

# ✅ 一句话总结

👉 **UI 已完全解耦，只要实现一个 `reply()`，Live2D 就能变成 AI 角色。**

