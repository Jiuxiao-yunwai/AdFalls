package com.example.adfalls.data.remote

import kotlinx.coroutines.delay

object AiChatRemoteDataSource {
    suspend fun sendMessage(
        request: AiChatRequestDto
    ): AiChatResponseDto {
        delay(800)
        request.contextAd?.let { return analyzeAd(it) }
        val recommendedAdIds = recommendAdIds(request.query)

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
                    content = "我从全部广告中识别到“${inferDimensions(request.query)}”相关需求，并找到了几条匹配结果。"
                ),
                AiChatMessageDto(
                    type = "ad_recommendation",
                    content = "可以优先看看下面这些推荐。",
                    adIds = recommendedAdIds
                )
            )
        )
    }

    private fun analyzeAd(ad: AiChatAdContextDto): AiChatResponseDto {
        val primaryTags = ad.tags.take(3).joinToString("、").ifBlank { "当前广告主题" }
        val audience = when {
            ad.tags.any { it in listOf("学生", "教育", "性价比") } -> "学生、年轻用户和重视性价比的人群"
            ad.tags.any { it in listOf("通勤", "办公", "效率") } -> "城市通勤者和希望提升日常效率的职场人群"
            ad.tags.any { it in listOf("运动", "健身", "跑步", "骑行", "户外") } ->
                "关注运动体验、健康生活和户外场景的人群"
            ad.tags.any { it in listOf("餐饮", "咖啡", "轻食", "火锅", "本地") } ->
                "附近有消费需求、喜欢探店和本地生活服务的人群"
            else -> "对${primaryTags}感兴趣，并愿意尝试相关产品或服务的人群"
        }
        val positioning = if (ad.summary.isNotBlank()) {
            ad.summary
        } else {
            ad.detail
        }

        return AiChatResponseDto(
            messages = listOf(
                AiChatMessageDto(
                    type = "text",
                    content = "这条广告的核心定位是“${ad.title}”，由 ${ad.brand} 发布，重点围绕${primaryTags}展开。"
                ),
                AiChatMessageDto(
                    type = "text",
                    content = "适合人群：$audience。它通过“$positioning”来建立吸引力。"
                ),
                AiChatMessageDto(
                    type = "text",
                    content = "从投放角度看，可以继续强化具体使用场景、差异化卖点和明确行动指引，让用户更快判断是否适合自己。"
                )
            )
        )
    }

    internal fun recommendAdIds(query: String): List<Long> {
        val contextAdId = contextAdIdPattern.find(query)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        val matchedRecommendationGroups = recommendationRules
            .mapNotNull { rule ->
                val matchCount = rule.keywords.count(query::contains)
                if (matchCount > 0) matchCount to rule.adIds else null
            }
            .sortedByDescending { it.first }
            .map { it.second }
        val recommendations = if (matchedRecommendationGroups.isEmpty()) {
            DEFAULT_RECOMMENDATIONS
        } else {
            mergeRecommendations(matchedRecommendationGroups)
        }

        val filteredRecommendations = recommendations
            .distinct()
            .filter { it in 1L..50L && it != contextAdId }

        return filteredRecommendations
            .ifEmpty {
                DEFAULT_RECOMMENDATIONS.filter { it != contextAdId }
            }
            .take(MAX_RECOMMENDATIONS)
    }

    private fun inferDimensions(query: String): String {
        return recommendationRules
            .filter { rule -> rule.keywords.any(query::contains) }
            .joinToString("、") { it.dimension }
            .ifBlank { "综合探索" }
    }

    private fun mergeRecommendations(groups: List<List<Long>>): List<Long> {
        val largestGroupSize = groups.maxOfOrNull { it.size } ?: return emptyList()
        return buildList {
            repeat(largestGroupSize) { index ->
                groups.forEach { group -> group.getOrNull(index)?.let(::add) }
            }
        }
    }

    private data class RecommendationRule(
        val dimension: String,
        val keywords: List<String>,
        val adIds: List<Long>
    )

    private val contextAdIdPattern = Regex("""当前广告 ID[：:]\s*(\d+)""")
    private const val MAX_RECOMMENDATIONS = 5
    private val DEFAULT_RECOMMENDATIONS = listOf(13L, 24L, 37L, 42L, 47L)

    private val recommendationRules = listOf(
        RecommendationRule(
            dimension = "学生数码与性价比",
            keywords = listOf("学生", "数码", "耳机", "性价比"),
            adIds = listOf(13L, 19L, 24L)
        ),
        RecommendationRule(
            dimension = "城市通勤",
            keywords = listOf("通勤", "背包", "降噪"),
            adIds = listOf(24L, 29L, 3L, 6L)
        ),
        RecommendationRule(
            dimension = "餐饮与本地生活",
            keywords = listOf("咖啡", "午餐", "轻食", "火锅", "餐饮"),
            adIds = listOf(33L, 37L, 42L, 3L, 11L)
        ),
        RecommendationRule(
            dimension = "周末户外",
            keywords = listOf("周末", "露营", "户外"),
            adIds = listOf(2L, 34L, 47L, 22L, 9L, 16L)
        ),
        RecommendationRule(
            dimension = "运动与健康",
            keywords = listOf("运动", "健身", "跑步", "骑行"),
            adIds = listOf(1L, 35L, 44L, 25L, 6L, 31L)
        )
    )
}
