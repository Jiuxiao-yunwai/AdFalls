package com.example.adfalls.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.adfalls.data.model.AdCardType
import com.example.adfalls.data.model.AdChannel
import com.example.adfalls.data.model.AdItem

@Entity(tableName = "ads")
data class AdEntity(
    @PrimaryKey val id: Long,
    val channel: String,
    val type: String,
    val title: String,
    val brand: String,
    val videoUrl: String?,
    val summary: String,
    val detail: String,
    val tags: String,
    val mediaColor: Int,
    val liked: Boolean,
    val favorited: Boolean,
    val playing: Boolean,
    val muted: Boolean,
    val likes: Int,
    val shares: Int,
    val impressions: Int,
    val clicks: Int
)

fun AdEntity.toModel(): AdItem {
    return AdItem(
        id = id,
        channel = AdChannel.valueOf(channel),
        type = AdCardType.valueOf(type),
        title = title,
        brand = brand,
        videoUrl = videoUrl,
        summary = summary,
        detail = detail,
        tags = tags.split(",").filter { it.isNotBlank() },
        mediaColor = mediaColor,
        liked = liked,
        favorited = favorited,
        playing = playing,
        muted = muted,
        likes = likes,
        shares = shares,
        impressions = impressions,
        clicks = clicks
    )
}

fun AdItem.toEntity(): AdEntity {
    return AdEntity(
        id = id,
        channel = channel.name,
        type = type.name,
        title = title,
        brand = brand,
        videoUrl = videoUrl,
        summary = summary,
        detail = detail,
        tags = tags.joinToString(","),
        mediaColor = mediaColor,
        liked = liked,
        favorited = favorited,
        playing = playing,
        muted = muted,
        likes = likes,
        shares = shares,
        impressions = impressions,
        clicks = clicks
    )
}
