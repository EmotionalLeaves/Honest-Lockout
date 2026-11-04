package com.honestlockout.app.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.honestlockout.app.data.db.entities.Override
import kotlinx.coroutines.flow.Flow

@Dao
interface OverrideDao {
    @Insert suspend fun insert(item: Override): Long
    @Update suspend fun update(item: Override)
    @Delete suspend fun delete(item: Override)
    @Query("SELECT * FROM Override ORDER BY startedAt DESC") fun getAll(): Flow<List<Override>>
    @Query("SELECT * FROM Override WHERE endedAt IS NULL LIMIT 1") suspend fun getActive(): Override?
    @Query("SELECT * FROM Override WHERE endedAt IS NULL LIMIT 1") fun getActiveFlow(): Flow<Override?>
    @Query("SELECT * FROM Override WHERE id = :id LIMIT 1") suspend fun get(id: Long): Override?
    @Query("SELECT * FROM Override ORDER BY startedAt DESC LIMIT :n") suspend fun lastN(n: Int): List<Override>
    @Query("SELECT * FROM Override WHERE startedAt >= :sinceMs") suspend fun startedSince(sinceMs: Long): List<Override>
    @Query(
        "SELECT COUNT(*) FROM Override WHERE endReason IN ('DONE','CONFESSED','TIMEOUT') AND startedAt > " +
            "COALESCE((SELECT MAX(startedAt) FROM Override WHERE endReason = 'STRIKE'), 0)"
    )
    suspend fun countCleanSinceLastStrike(): Int
}
