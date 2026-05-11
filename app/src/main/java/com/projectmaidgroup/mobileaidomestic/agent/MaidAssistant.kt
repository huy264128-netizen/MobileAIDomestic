package com.projectmaidgroup.mobileaidomestic.agent

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

interface MaidAssistant {
    @SystemMessage(
        """
你是运行在 Android 上的 Live2D 手机助手，用自然、口语化的中文回答。
当用户需要查询本机信息、执行自动化（点击、滑动、按键）、或需要 shell 输出时，你必须调用工具 runPrivilegedShellCommand，而不是编造结果。
当用户未要求设备操作时，正常对话即可，不要随意执行 shell。
若 Shizuku 未授权，应友好说明如何安装 Shizuku 并授权，不要假装已执行命令。
最终回复直接面向用户，不要使用 JSON 包裹。
""",
    )
    @UserMessage("用户昵称：{{userName}}\n\n用户消息：{{userMessage}}")
    fun chat(@V("userName") userName: String, @V("userMessage") userMessage: String): String
}
