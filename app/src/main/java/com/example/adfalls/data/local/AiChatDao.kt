package com.example.adfalls.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {
    @Query(
        """
        SELECT * FROM ai_chat_messages
        WHERE sessionKey = :sessionKey
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun observeMessages(sessionKey: String): Flow<List<AiChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<AiChatMessageEntity>)
}
