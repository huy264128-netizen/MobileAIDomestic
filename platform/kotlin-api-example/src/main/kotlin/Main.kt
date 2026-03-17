import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

fun main() {
    val prompt = "你好，请用三点介绍一下你自己"
    println(callQwen(prompt))
}

private val httpClient: HttpClient = HttpClient.newBuilder().build()

fun callQwen(prompt: String): String {
    val apiKey = requireEnv("DASHSCOPE_API_KEY").trim()
    val baseUrl = System.getenv("QWEN_BASE_URL")?.trim()
        ?: "https://dashscope.aliyuncs.com/compatible-mode/v1"
    val model = System.getenv("MODEL_NAME")?.trim()
        ?: "qwen2.5-14b-instruct"

    val requestBody = """
        {
          "model": "$model",
          "messages": [
            {
              "role": "system",
              "content": "You are a helpful assistant."
            },
            {
              "role": "user",
              "content": "${escapeJson(prompt)}"
            }
          ],
          "stream": false,
          "temperature": 0.7,
          "max_tokens": 1024
        }
    """.trimIndent()

    val request = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl/chat/completions"))
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
        .build()

    val response = httpClient.send(
        request,
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
    )

    if (response.statusCode() !in 200..299) {
        return "Request failed\nHTTP ${response.statusCode()}\n${response.body()}"
    }

    return response.body()
}

fun escapeJson(text: String): String {
    val sb = StringBuilder()
    for (ch in text) {
        when (ch) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> {
                if (ch.code < 32) {
                    sb.append("\\u%04x".format(ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
    }
    return sb.toString()
}

fun requireEnv(name: String): String {
    return System.getenv(name)
        ?: throw IllegalArgumentException("Missing environment variable: $name")
}