package com.example.adfalls.ui.aichat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.adfalls.R
import com.example.adfalls.data.model.AiChatMessage
import com.example.adfalls.data.model.ChatRole

class AiChatAdapter : ListAdapter<AiChatMessage, AiChatAdapter.ChatViewHolder>(Diff) {
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).role) {
            ChatRole.USER -> R.layout.item_chat_user
            ChatRole.ASSISTANT -> R.layout.item_chat_assistant
            ChatRole.LOADING -> R.layout.item_chat_loading
            ChatRole.ERROR -> R.layout.item_chat_error
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.chat_message_text)

        fun bind(message: AiChatMessage) {
            messageText.text = message.text
            when (message.role) {
                ChatRole.ERROR -> {
                    messageText.setTextColor(0xFFFFB4B4.toInt())
                    messageText.setBackgroundResource(R.drawable.bg_chat_error_bubble)
                }
                ChatRole.USER -> {
                    messageText.setTextColor(0xFF101010.toInt())
                    messageText.setBackgroundResource(R.drawable.bg_chat_user_bubble)
                }
                ChatRole.ASSISTANT -> {
                    messageText.setTextColor(0xFFFFFFFF.toInt())
                    messageText.setBackgroundResource(R.drawable.bg_chat_assistant_bubble)
                }
                ChatRole.LOADING -> {
                    messageText.setTextColor(0xFFCFCFCF.toInt())
                    messageText.setBackgroundResource(R.drawable.bg_chat_assistant_bubble)
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AiChatMessage>() {
        override fun areItemsTheSame(oldItem: AiChatMessage, newItem: AiChatMessage): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AiChatMessage, newItem: AiChatMessage): Boolean = oldItem == newItem
    }
}
