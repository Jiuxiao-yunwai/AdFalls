package com.example.adfalls.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdDao {
    @Query("SELECT * FROM ads WHERE channel = :channel ORDER BY id ASC")
    fun observeAdsByChannel(channel: String): Flow<List<AdEntity>>

    @Query("SELECT * FROM ads WHERE id = :id LIMIT 1")
    fun observeAdById(id: Long): Flow<AdEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAds(ads: List<AdEntity>)

    @Query("UPDATE ads SET liked = :liked, likes = :likes WHERE id = :id")
    fun updateLike(id: Long, liked: Boolean, likes: Int)

    @Query("UPDATE ads SET favorited = :favorited WHERE id = :id")
    fun updateFavorite(id: Long, favorited: Boolean)

    @Query("UPDATE ads SET shares = shares + 1 WHERE id = :id")
    fun addShare(id: Long)

    @Query("UPDATE ads SET clicks = clicks + 1 WHERE id = :id")
    fun addClick(id: Long)

    @Query("UPDATE ads SET impressions = impressions + 1 WHERE id = :id")
    fun addImpression(id: Long)

    @Query("UPDATE ads SET playing = :playing, muted = :muted WHERE id = :id")
    fun updateVideoState(id: Long, playing: Boolean, muted: Boolean)

    @Query("SELECT * FROM ads WHERE channel = :channel ORDER BY id ASC")
    fun getAdsByChannel(channel: String): List<AdEntity>

    @Query("SELECT * FROM ads WHERE id IN (:ids)")
    fun getAdsByIds(ids: List<Long>): List<AdEntity>

    @Query("SELECT * FROM ads WHERE id = :id LIMIT 1")
    fun getAdById(id: Long): AdEntity?

    @Query("SELECT COUNT(*) FROM ads")
    fun countAds(): Int

    @Query("SELECT MAX(id) FROM ads")
    fun maxAdId(): Long?

    @Query("DELETE FROM ads WHERE channel = :channel")
    fun deleteAdsByChannel(channel: String)

    @Query("DELETE FROM ads")
    fun deleteAllAds()
}
