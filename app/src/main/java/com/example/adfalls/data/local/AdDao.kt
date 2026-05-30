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
    suspend fun insertAds(ads: List<AdEntity>)

    @Query("UPDATE ads SET liked = :liked, likes = :likes WHERE id = :id")
    suspend fun updateLike(id: Long, liked: Boolean, likes: Int)

    @Query("UPDATE ads SET favorited = :favorited WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorited: Boolean)

    @Query("UPDATE ads SET shares = shares + 1 WHERE id = :id")
    suspend fun addShare(id: Long)

    @Query("UPDATE ads SET clicks = clicks + 1 WHERE id = :id")
    suspend fun addClick(id: Long)

    @Query("UPDATE ads SET impressions = impressions + 1 WHERE id = :id")
    suspend fun addImpression(id: Long)

    @Query("UPDATE ads SET playing = :playing, muted = :muted WHERE id = :id")
    suspend fun updateVideoState(id: Long, playing: Boolean, muted: Boolean)

    @Query("SELECT * FROM ads WHERE channel = :channel ORDER BY id ASC")
    suspend fun getAdsByChannel(channel: String): List<AdEntity>

    @Query("SELECT * FROM ads WHERE id IN (:ids)")
    suspend fun getAdsByIds(ids: List<Long>): List<AdEntity>

    @Query("SELECT * FROM ads WHERE id = :id LIMIT 1")
    suspend fun getAdById(id: Long): AdEntity?

    @Query("SELECT COUNT(*) FROM ads")
    suspend fun countAds(): Int

    @Query("SELECT MAX(id) FROM ads")
    suspend fun maxAdId(): Long?

    @Query("DELETE FROM ads WHERE channel = :channel")
    suspend fun deleteAdsByChannel(channel: String)

    @Query("DELETE FROM ads")
    suspend fun deleteAllAds()
}
