package com.example.adfalls.ui.aichat

import android.content.Intent
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
import com.example.adfalls.ui.detail.DetailActivity
import com.example.adfalls.viewmodel.AiChatViewModel
import kotlinx.coroutines.launch

class AiChatActivity : ComponentActivity() {
    private lateinit var viewModel: AiChatViewModel
    private lateinit var adapter: AiChatAdapter
    private lateinit var messageList: RecyclerView
    private lateinit var input: EditText
    private lateinit var sendButton: TextView
    private var initialQuerySubmitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(R.layout.activity_ai_chat)

        viewModel = ViewModelProvider.create(this)[AiChatViewModel::class]

        adapter = AiChatAdapter { adId ->
            viewModel.registerAdClick(adId)
            startActivity(
                Intent(this, DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_AD_ID, adId)
            )
        }
        messageList = findViewById<RecyclerView>(R.id.ai_chat_messages).apply {
            layoutManager = LinearLayoutManager(this@AiChatActivity)
            adapter = this@AiChatActivity.adapter
        }
        input = findViewById(R.id.ai_chat_input)
        sendButton = findViewById(R.id.ai_chat_send)

        findViewById<TextView>(R.id.ai_chat_back).setOnClickListener { finish() }
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
                    sendButton.isEnabled = state.historyLoaded && !state.sending && state.inputText.isNotBlank()
                    sendButton.alpha = if (sendButton.isEnabled) 1f else 0.45f
                }
            }
        }

        initialQuerySubmitted = savedInstanceState?.getBoolean(STATE_INITIAL_QUERY_SUBMITTED) == true
        if (!initialQuerySubmitted) {
            val initialQuery = intent.getStringExtra(EXTRA_INITIAL_QUERY).orEmpty()
            if (initialQuery.isNotBlank()) {
                initialQuerySubmitted = true
                viewModel.submitInitialQueryOnce(
                    query = initialQuery,
                    contextAdId = intent.getLongExtra(EXTRA_CONTEXT_AD_ID, -1L).takeIf { it > 0L }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_INITIAL_QUERY_SUBMITTED, initialQuerySubmitted)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_INITIAL_QUERY = "extra_initial_query"
        const val EXTRA_CONTEXT_AD_ID = "extra_context_ad_id"
        private const val STATE_INITIAL_QUERY_SUBMITTED = "state_initial_query_submitted"
    }
}
