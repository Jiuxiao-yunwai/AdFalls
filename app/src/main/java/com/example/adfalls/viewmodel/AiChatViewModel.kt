package com.example.adfalls.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AiChatMessage
import com.example.adfalls.data.model.ChatRole
import com.example.adfalls.data.repository.AiChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiChatUiState(
    val activeChannel: AdChannel = AdChannel.FEATURED,
    val messages: List<AiChatMessage> = emptyList(),
    val inputText: String = "",
    val sending: Boolean = false
)

class AiChatViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = mutableUiState.asStateFlow()

    fun selectChannel(channel: AdChannel) {
        mutableUiState.update { it.copy(activeChannel = channel) }
    }

    fun updateInput(text: String) {
        mutableUiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val state = mutableUiState.value
        val query = state.inputText.trim()
        if (query.isEmpty() || state.sending) return

        val baseId = System.currentTimeMillis()
        val userMessage = AiChatMessage(id = baseId, role = ChatRole.USER, text = query)
        val loadingMessage = AiChatMessage(id = baseId + 1, role = ChatRole.LOADING, text = "正在思考...")
        val history = state.messages + userMessage

        mutableUiState.update {
            it.copy(
                messages = history + loadingMessage,
                sending = true
            )
        }

        viewModelScope.launch {
            runCatching {
                AiChatRepository.sendMessage(state.activeChannel, query, history)
            }.onSuccess { replies ->
                mutableUiState.update {
                    it.copy(
                        messages = it.messages.filterNot { message -> message.id == loadingMessage.id } + replies,
                        inputText = "",
                        sending = false
                    )
                }
            }.onFailure {
                val errorMessage = AiChatMessage(
                    id = System.currentTimeMillis(),
                    role = ChatRole.ERROR,
                    text = "AI 服务暂时不可用，请稍后再试。"
                )
                mutableUiState.update {
                    it.copy(
                        messages = it.messages.filterNot { message -> message.id == loadingMessage.id } + errorMessage,
                        inputText = "",
                        sending = false
                    )
                }
            }
        }
    }
}
