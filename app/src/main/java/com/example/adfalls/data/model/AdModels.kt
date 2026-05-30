package com.example.adfalls.data.model

enum class AdChannel(val title: String) {
    FEATURED("精选"),
    COMMERCE("电商"),
    LOCAL("本地")
}

enum class AdCardType {
    LARGE_IMAGE,
    SMALL_IMAGE,
    VIDEO
}

data class AdItem(
    val id: Long,
    val channel: AdChannel,
    val type: AdCardType,
    val title: String,
    val brand: String,
    val summary: String,
    val detail: String,
    val tags: List<String>,
    val mediaColor: Int,
    val liked: Boolean = false,
    val favorited: Boolean = false,
    val playing: Boolean = false,
    val muted: Boolean = true,
    val likes: Int = 0,
    val shares: Int = 0,
    val impressions: Int = 0,
    val clicks: Int = 0
)
