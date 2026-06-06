package com.example.adfalls.ui.aichat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.adfalls.R
import com.example.adfalls.data.model.AiChatMessage
import com.example.adfalls.data.model.ChatRole
import kotlin.math.roundToInt

class AiChatAdapter(
    private val onAdRecommendationClick: (Long) -> Unit
) : ListAdapter<AiChatMessage, AiChatAdapter.ChatViewHolder>(Diff) {
    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return when (message.role) {
            ChatRole.USER -> R.layout.item_chat_user
            ChatRole.ASSISTANT -> if (message.relatedAdIds.isEmpty()) {
                R.layout.item_chat_assistant
            } else {
                R.layout.item_chat_ad_recommendation
            }
            ChatRole.LOADING -> R.layout.item_chat_loading
            ChatRole.ERROR -> R.layout.item_chat_error
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ChatViewHolder(view, onAdRecommendationClick)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatViewHolder(
        itemView: View,
        private val onAdRecommendationClick: (Long) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.chat_message_text)
        private val recommendationCard: View? = itemView.findViewById(R.id.chat_recommendation_card)
        private val recommendationLinks: LinearLayout? = itemView.findViewById(R.id.chat_recommendation_links)
        private val recommendationEmpty: TextView? = itemView.findViewById(R.id.chat_recommendation_empty)

        fun bind(message: AiChatMessage) {
            messageText.text = message.text
            applyTrackRelativeWidth()
            when (message.role) {
                ChatRole.ERROR -> {
                    messageText.setTextColor(itemView.context.getColor(R.color.app_error))
                    messageText.setBackgroundResource(R.drawable.bg_chat_error_bubble)
                }
                ChatRole.USER -> {
                    messageText.setTextColor(itemView.context.getColor(R.color.app_text_primary))
                    messageText.setBackgroundResource(R.drawable.bg_chat_user_bubble)
                }
                ChatRole.ASSISTANT -> {
                    messageText.setTextColor(itemView.context.getColor(R.color.app_text_on_dark_primary))
                    if (message.relatedAdIds.isEmpty()) {
                        messageText.setBackgroundResource(R.drawable.bg_chat_assistant_bubble)
                    } else {
                        messageText.background = null
                    }
                }
                ChatRole.LOADING -> {
                    messageText.setTextColor(itemView.context.getColor(R.color.app_text_on_dark_secondary))
                    messageText.setBackgroundResource(R.drawable.bg_chat_assistant_bubble)
                }
            }

            recommendationLinks?.removeAllViews()
            recommendationEmpty?.visibility = if (message.relatedAds.isEmpty()) View.VISIBLE else View.GONE
            message.relatedAds.forEach { ad ->
                val link = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.item_chat_ad_link, recommendationLinks, false)
                link.findViewById<TextView>(R.id.chat_ad_link_brand).text =
                    "${ad.channel.title} · ${ad.brand}"
                link.findViewById<TextView>(R.id.chat_ad_link_title).text = ad.title
                link.findViewById<TextView>(R.id.chat_ad_link_summary).text = ad.summary
                link.findViewById<TextView>(R.id.chat_ad_link_tags).text =
                    ad.tags.joinToString("  ") { "#$it" }
                link.setOnClickListener { onAdRecommendationClick(ad.id) }
                recommendationLinks?.addView(link)
            }
        }

        private fun applyTrackRelativeWidth() {
            val rowWidth = itemView.width
            if (rowWidth <= 0) {
                itemView.post { applyTrackRelativeWidth() }
                return
            }
            val maxMessageWidth = (rowWidth * MESSAGE_MAX_TRACK_FRACTION).roundToInt()
            recommendationCard?.let { card ->
                card.layoutParams = card.layoutParams.apply {
                    width = (rowWidth * RECOMMENDATION_CARD_TRACK_FRACTION).roundToInt()
                }
                return
            }
            messageText.maxWidth = maxMessageWidth
        }
    }

    private object Diff : DiffUtil.ItemCallback<AiChatMessage>() {
        override fun areItemsTheSame(oldItem: AiChatMessage, newItem: AiChatMessage): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AiChatMessage, newItem: AiChatMessage): Boolean = oldItem == newItem
    }

    private companion object {
        private const val MESSAGE_MAX_TRACK_FRACTION = 0.8f
        private const val RECOMMENDATION_CARD_TRACK_FRACTION = 0.9f
    }
}
