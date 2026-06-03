package com.example.adfalls.ui.aichat

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.adfalls.R
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.viewmodel.AiChatViewModel
import kotlinx.coroutines.launch

class AiChatActivity : ComponentActivity() {
    private lateinit var viewModel: AiChatViewModel
    private lateinit var adapter: AiChatAdapter
    private lateinit var messageList: RecyclerView
    private lateinit var input: EditText
    private lateinit var sendButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(R.layout.activity_ai_chat)

        val channel = intent.getStringExtra(EXTRA_CHANNEL)
            ?.let { runCatching { AdChannel.valueOf(it) }.getOrNull() }
            ?: AdChannel.FEATURED

        viewModel = ViewModelProvider.create(this)[AiChatViewModel::class]
        viewModel.selectChannel(channel)

        adapter = AiChatAdapter()
        messageList = findViewById<RecyclerView>(R.id.ai_chat_messages).apply {
            layoutManager = LinearLayoutManager(this@AiChatActivity)
            adapter = this@AiChatActivity.adapter
        }
        input = findViewById(R.id.ai_chat_input)
        sendButton = findViewById(R.id.ai_chat_send)

        findViewById<TextView>(R.id.ai_chat_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.ai_chat_scope).text = "当前频道：${channel.title}"
        sendButton.setOnClickListener { viewModel.sendMessage() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                viewModel.sendMessage()
                true
            } else {
                false
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateInput(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.messages) {
                        if (state.messages.isNotEmpty()) {
                            messageList.scrollToPosition(state.messages.lastIndex)
                        }
                    }
                    if (input.text.toString() != state.inputText) {
                        input.setText(state.inputText)
                        input.setSelection(input.text.length)
                    }
                    sendButton.isEnabled = !state.sending && state.inputText.isNotBlank()
                    sendButton.alpha = if (sendButton.isEnabled) 1f else 0.45f
                }
            }
        }
    }

    companion object {
        const val EXTRA_CHANNEL = "extra_channel"
    }
}
