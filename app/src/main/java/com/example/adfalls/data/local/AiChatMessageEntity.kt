package com.example.adfalls.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.adfalls.data.model.AiChatMessage
import com.example.adfalls.data.model.ChatRole

@Entity(
    tableName = "ai_chat_messages",
    indices = [Index(value = ["sessionKey"])]
)
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionKey: String,
    val channel: String,
    val role: String,
    val text: String,
    val relatedAdIds: String,
    val createdAt: Long
)

fun AiChatMessageEntity.toModel(): AiChatMessage {
    return AiChatMessage(
        id = id,
        role = ChatRole.valueOf(role),
        text = text,
        relatedAdIds = relatedAdIds
            .split(",")
            .mapNotNull(String::toLongOrNull),
        createdAt = createdAt
    )
}

fun AiChatMessage.toEntity(sessionKey: String, channel: String): AiChatMessageEntity {
    return AiChatMessageEntity(
        sessionKey = sessionKey,
        channel = channel,
        role = role.name,
        text = text,
        relatedAdIds = relatedAdIds.joinToString(","),
        createdAt = createdAt
    )
}
