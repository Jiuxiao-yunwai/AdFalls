package com.example.adfalls.data.remote

import com.example.adfalls.data.model.AiChatMessage
import com.example.adfalls.data.model.ChatRole
import com.example.adfalls.data.model.nextAiChatMessageId

data class AiChatRequestDto(
    val channel: String,
    val query: String,
    val history: List<AiChatHistoryDto>,
    val contextAd: AiChatAdContextDto? = null
)

data class AiChatHistoryDto(
    val role: String,
    val content: String
)

data class AiChatAdContextDto(
    val id: Long,
    val title: String,
    val brand: String,
    val summary: String,
    val detail: String,
    val tags: List<String>
)

data class AiChatResponseDto(
    val messages: List<AiChatMessageDto>
)

data class AiChatMessageDto(
    val type: String,
    val content: String,
    val adIds: List<Long> = emptyList()
)

fun AiChatResponseDto.toChatMessages(): List<AiChatMessage> {
    val baseId = System.currentTimeMillis()
    return messages.mapIndexed { index, dto ->
        AiChatMessage(
            id = nextAiChatMessageId(),
            role = ChatRole.ASSISTANT,
            text = dto.content,
            relatedAdIds = dto.adIds,
            createdAt = baseId + index
        )
    }
}
