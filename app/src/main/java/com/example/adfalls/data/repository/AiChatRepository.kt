package com.example.adfalls.data.repository

import android.content.Context
import com.example.adfalls.data.local.AiChatDao
import com.example.adfalls.data.local.AppDatabase
import com.example.adfalls.data.local.toEntity
import com.example.adfalls.data.local.toModel
import com.example.adfalls.data.model.AiChatMessage
import com.example.adfalls.data.model.ChatRole
import com.example.adfalls.data.remote.AiChatAdContextDto
import com.example.adfalls.data.remote.AiChatRemoteDataSource
import com.example.adfalls.data.remote.AiChatHistoryDto
import com.example.adfalls.data.remote.AiChatRequestDto
import com.example.adfalls.data.remote.toChatMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

object AiChatRepository {
    private var aiChatDao: AiChatDao? = null

    fun initialize(context: Context) {
        if (aiChatDao != null) return
        aiChatDao = AppDatabase.getInstance(context).aiChatDao()
    }

    internal fun isInitialized(): Boolean = aiChatDao != null

    fun observeMessages(): Flow<List<AiChatMessage>> {
        val dao = aiChatDao ?: return flowOf(emptyList())
        return dao.observeMessages(GLOBAL_SESSION_KEY).map { entities ->
            hydrateMessages(entities.map { it.toModel() })
        }
    }

    suspend fun saveMessages(messages: List<AiChatMessage>) {
        val dao = aiChatDao ?: return
        val persistentMessages = messages.filter {
            it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT
        }
        if (persistentMessages.isEmpty()) return

        withContext(Dispatchers.IO) {
            dao.insertMessages(
                persistentMessages.map { it.toEntity(GLOBAL_SESSION_KEY, GLOBAL_CHANNEL) }
            )
        }
    }

    suspend fun sendMessage(
        query: String,
        history: List<AiChatMessage>,
        contextAdId: Long? = null
    ): List<AiChatMessage> {
        val contextAd = contextAdId?.let { AdRepository.findAd(it) }
        val request = AiChatRequestDto(
            channel = GLOBAL_CHANNEL,
            query = query,
            history = history
                .filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }
                .map { message ->
                    AiChatHistoryDto(
                        role = if (message.role == ChatRole.USER) "user" else "assistant",
                        content = message.text
                    )
                },
            contextAd = contextAd?.let { ad ->
                AiChatAdContextDto(
                    id = ad.id,
                    title = ad.title,
                    brand = ad.brand,
                    summary = ad.summary,
                    detail = ad.detail,
                    tags = ad.tags
                )
            }
        )

        val response = AiChatRemoteDataSource.sendMessage(request)
        return hydrateMessages(response.toChatMessages())
    }

    private suspend fun hydrateMessages(messages: List<AiChatMessage>): List<AiChatMessage> {
        if (aiChatDao == null) return messages
        val relatedAdsById = AdRepository.findAds(messages.flatMap { it.relatedAdIds })
            .associateBy { it.id }
        return messages.map { message ->
            message.copy(relatedAds = message.relatedAdIds.mapNotNull(relatedAdsById::get))
        }
    }

    private const val GLOBAL_SESSION_KEY = "global"
    private const val GLOBAL_CHANNEL = "ALL"
}
