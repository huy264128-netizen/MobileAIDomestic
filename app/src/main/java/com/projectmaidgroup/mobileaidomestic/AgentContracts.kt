package com.projectmaidgroup.mobileaidomestic

internal data class AgentReply(val text: String)

internal interface AgentBackend {
    suspend fun reply(input: String, userName: String): AgentReply
}
