package com.example.adfalls.data.model

enum class ChatRole {
    USER,
    ASSISTANT,
    LOADING,
    ERROR
}

data class AiChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: ChatRole,
    val text: String,
    val relatedAdIds: List<Long> = emptyList()
)
