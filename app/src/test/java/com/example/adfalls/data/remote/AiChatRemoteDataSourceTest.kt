package com.example.adfalls.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class AiChatRemoteDataSourceTest {
    @Test
    fun defaultRecommendationsCoverMultipleDimensions() {
        assertEquals(
            listOf(13L, 24L, 37L, 42L, 47L),
            AiChatRemoteDataSource.recommendAdIds("随便看看")
        )
    }

    @Test
    fun recommendationsMergeMultipleMatchingIntents() {
        val recommendations = AiChatRemoteDataSource.recommendAdIds("周末骑行")

        assertEquals(listOf(2L, 1L, 34L, 35L, 47L), recommendations)
    }

    @Test
    fun currentAdIsExcludedFromRelatedRecommendations() {
        val recommendations = AiChatRemoteDataSource.recommendAdIds("帮我分析。当前广告 ID：35；标签：健身、体验")

        assertEquals(listOf(1L, 44L, 25L, 6L, 31L), recommendations)
        assertTrue(35L !in recommendations)
    }

    @Test
    fun excludingOneMatchKeepsOtherCrossDimensionAds() {
        val recommendations = AiChatRemoteDataSource.recommendAdIds("当前广告 ID：13；标签：学生、数码、性价比")

        assertEquals(listOf(19L, 24L), recommendations)
    }

    @Test
    fun recommendationsAlwaysStayInsideMockAdPool() {
        val queries = listOf("学生数码", "通勤降噪", "咖啡午餐", "周末露营", "运动骑行", "随便看看")

        queries.forEach { query ->
            val recommendations = AiChatRemoteDataSource.recommendAdIds(query)
            assertTrue(recommendations.isNotEmpty())
            assertTrue(recommendations.size <= 5)
            assertTrue(recommendations.all { it in 1L..50L })
        }
    }

    @Test
    fun matchingIntentCanReturnAdsFromDifferentFeedChannels() {
        val recommendations = AiChatRemoteDataSource.recommendAdIds("通勤降噪背包")

        assertTrue(recommendations.any { it in 1L..16L })
        assertTrue(recommendations.any { it in 17L..32L })
    }

    @Test
    fun detailContextReturnsAnalysisInsteadOfSearchEcho() = runTest {
        val response = AiChatRemoteDataSource.sendMessage(
            AiChatRequestDto(
                channel = "ALL",
                query = "请分析一下这条广告适合什么人",
                history = emptyList(),
                contextAd = AiChatAdContextDto(
                    id = 13L,
                    title = "学生党数码精简包",
                    brand = "PixelBox",
                    summary = "高性价比数码组合，适合学习和校园通勤。",
                    detail = "覆盖学生的日常数码需求。",
                    tags = listOf("学生", "数码", "性价比")
                )
            )
        )

        assertEquals(3, response.messages.size)
        assertTrue(response.messages.any { it.content.contains("适合人群") })
        assertFalse(response.messages.any { it.content.contains("我理解你的需求是") })
        assertTrue(response.messages.all { it.adIds.isEmpty() })
    }
}
