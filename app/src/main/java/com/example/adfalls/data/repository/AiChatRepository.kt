package com.example.adfalls.data.repository

import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AiChatMessage
import com.example.adfalls.data.model.ChatRole
import com.example.adfalls.data.remote.AiChatRemoteDataSource
import com.example.adfalls.data.remote.AiChatHistoryDto
import com.example.adfalls.data.remote.AiChatRequestDto
import com.example.adfalls.data.remote.toChatMessages

object AiChatRepository {
    suspend fun sendMessage(
        channel: AdChannel,
        query: String,
        history: List<AiChatMessage>
    ): List<AiChatMessage> {
        val request = AiChatRequestDto(
            channel = channel.name,
            query = query,
            history = history
                .filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }
                .map { message ->
                    AiChatHistoryDto(
                        role = if (message.role == ChatRole.USER) "user" else "assistant",
                        content = message.text
                    )
                }
        )

        val response = AiChatRemoteDataSource.sendMessage(request)
        return response.toChatMessages()
    }
}
