package com.example.adfalls.data.remote

import kotlinx.coroutines.delay

object AiChatRemoteDataSource {
    suspend fun sendMessage(
        request: AiChatRequestDto
    ): AiChatResponseDto {
        delay(800)

        // TODO: Replace this fake response with an OkHttp POST request to /api/ai-search/chat.
        // Serialize AiChatRequestDto as the JSON request body, parse the JSON response into
        // AiChatResponseDto, then let AiChatRepository convert it to UI-facing AiChatMessage.
        return AiChatResponseDto(
            messages = listOf(
                AiChatMessageDto(
                    type = "text",
                    content = "我理解你的需求是：${request.query}"
                ),
                AiChatMessageDto(
                    type = "text",
                    content = "当前频道是：${request.channel}，我会优先从这个频道中查找相关广告。"
                ),
                AiChatMessageDto(
                    type = "ad_recommendation",
                    content = "推荐查看：轻量跑鞋、校园通勤包、性价比耳机等广告。",
                    adIds = listOf(10001, 10005)
                ),
                AiChatMessageDto(
                    type = "text",
                    content = "后续这里会替换为真实 C/S 服务端接口返回的搜索结果。"
                )
            )
        )
    }
}
