package com.example.adfalls.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adfalls.data.model.AiChatMessage
import com.example.adfalls.data.model.ChatRole
import com.example.adfalls.data.model.nextAiChatMessageId
import com.example.adfalls.data.repository.AdRepository
import com.example.adfalls.data.repository.AiChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiChatUiState(
    val messages: List<AiChatMessage> = emptyList(),
    val inputText: String = "",
    val sending: Boolean = false,
    val historyLoaded: Boolean = false
)

class AiChatViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = mutableUiState.asStateFlow()
    private var pendingInitialQuery: String? = null
    private var pendingInitialContextAdId: Long? = null
    private var initialQuerySubmitted = false

    init {
        viewModelScope.launch {
            AiChatRepository.observeMessages().collect { messages ->
                mutableUiState.update { state ->
                    val transientMessages = state.messages.filter {
                        it.role == ChatRole.LOADING || it.role == ChatRole.ERROR
                    }
                    state.copy(
                        messages = messages + transientMessages,
                        historyLoaded = true
                    )
                }
                submitPendingInitialQuery()
            }
        }
    }

    fun updateInput(text: String) {
        mutableUiState.update { it.copy(inputText = text) }
    }

    fun submitInitialQueryOnce(query: String, contextAdId: Long? = null) {
        val initialQuery = query.trim()
        if (initialQuery.isEmpty() || initialQuerySubmitted) return

        initialQuerySubmitted = true
        pendingInitialQuery = initialQuery
        pendingInitialContextAdId = contextAdId
        submitPendingInitialQuery()
    }

    fun registerAdClick(adId: Long) {
        viewModelScope.launch { AdRepository.registerClick(adId) }
    }

    fun sendMessage(contextAdId: Long? = null) {
        val state = mutableUiState.value
        val query = state.inputText.trim()
        if (query.isEmpty() || state.sending || !state.historyLoaded) return

        val baseId = System.currentTimeMillis()
        val userMessage = AiChatMessage(
            id = nextAiChatMessageId(),
            role = ChatRole.USER,
            text = query,
            createdAt = baseId
        )
        val loadingMessage = AiChatMessage(
            id = nextAiChatMessageId(),
            role = ChatRole.LOADING,
            text = "正在思考...",
            createdAt = baseId + 1
        )
        val history = state.messages + userMessage

        mutableUiState.update {
            it.copy(
                messages = history + loadingMessage,
                inputText = "",
                sending = true
            )
        }

        viewModelScope.launch {
            runCatching {
                AiChatRepository.saveMessages(listOf(userMessage))
                AiChatRepository.sendMessage(query, history, contextAdId)
            }.onSuccess { replies ->
                AiChatRepository.saveMessages(replies)
                mutableUiState.update {
                    val persistedReplies = if (AiChatRepository.isInitialized()) emptyList() else replies
                    it.copy(
                        messages = it.messages.filterNot { message -> message.role == ChatRole.LOADING } +
                            persistedReplies,
                        sending = false
                    )
                }
            }.onFailure {
                val errorMessage = AiChatMessage(
                    id = nextAiChatMessageId(),
                    role = ChatRole.ERROR,
                    text = "AI 服务暂时不可用，请稍后再试。"
                )
                mutableUiState.update {
                    it.copy(
                        messages = it.messages.filterNot { message -> message.role == ChatRole.LOADING } + errorMessage,
                        sending = false
                    )
                }
            }
        }
    }

    private fun submitPendingInitialQuery() {
        val query = pendingInitialQuery ?: return
        val state = mutableUiState.value
        if (!state.historyLoaded || state.sending) return

        val contextAdId = pendingInitialContextAdId
        pendingInitialQuery = null
        pendingInitialContextAdId = null
        mutableUiState.update { it.copy(inputText = query) }
        sendMessage(contextAdId)
    }
}
