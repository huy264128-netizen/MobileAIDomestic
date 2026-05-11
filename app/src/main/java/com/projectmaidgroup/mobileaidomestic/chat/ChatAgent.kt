package com.projectmaidgroup.mobileaidomestic.chat

interface ChatAgent {
    suspend fun sendMessage(userText: String): ChatTurnResult
}