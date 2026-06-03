package com.example.adfalls.data.remote

import com.example.adfalls.data.model.AiChatMessage
import com.example.adfalls.data.model.ChatRole

data class AiChatRequestDto(
    val channel: String,
    val query: String,
    val history: List<AiChatHistoryDto>
)

data class AiChatHistoryDto(
    val role: String,
    val content: String
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
            id = baseId + index,
            role = ChatRole.ASSISTANT,
            text = dto.content,
            relatedAdIds = dto.adIds
        )
    }
}
