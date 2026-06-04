package com.example.adfalls.data.model

import java.util.concurrent.atomic.AtomicLong

enum class ChatRole {
    USER,
    ASSISTANT,
    LOADING,
    ERROR
}

data class AiChatMessage(
    val id: Long = nextAiChatMessageId(),
    val role: ChatRole,
    val text: String,
    val relatedAdIds: List<Long> = emptyList(),
    val relatedAds: List<AdItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

private val aiChatMessageId = AtomicLong(System.currentTimeMillis())

fun nextAiChatMessageId(): Long = aiChatMessageId.incrementAndGet()
