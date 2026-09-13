package com.reelpal.tracker.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelScrollDao {

    @Query("SELECT * FROM reel_counts WHERE date = :date")
    fun observeForDate(date: String): Flow<List<ReelScrollEntity>>

    @Query("SELECT * FROM reel_counts WHERE date = :date AND packageName = :pkg LIMIT 1")
    suspend fun getRow(date: String, pkg: String): ReelScrollEntity?

    @Upsert
    suspend fun upsert(entity: ReelScrollEntity)

    @Query("SELECT * FROM reel_counts WHERE date >= :sinceDate ORDER BY date ASC")
    fun observeSince(sinceDate: String): Flow<List<ReelScrollEntity>>
}
