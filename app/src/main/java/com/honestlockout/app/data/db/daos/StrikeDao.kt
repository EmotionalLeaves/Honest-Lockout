package com.honestlockout.app.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.honestlockout.app.data.db.entities.Strike
import kotlinx.coroutines.flow.Flow

@Dao
interface StrikeDao {
    @Insert suspend fun insert(item: Strike): Long
    @Update suspend fun update(item: Strike)
    @Delete suspend fun delete(item: Strike)
    @Query("SELECT * FROM Strike ORDER BY createdAt DESC") fun getAll(): Flow<List<Strike>>
    @Query("SELECT COUNT(*) FROM Strike WHERE forgivenAt IS NULL AND expiresAt > :now") suspend fun activeCount(now: Long): Int
    @Query("SELECT * FROM Strike WHERE forgivenAt IS NULL AND expiresAt > :sinceMs ORDER BY createdAt")
    suspend fun activeAt(sinceMs: Long): List<Strike>
    @Query("SELECT * FROM Strike WHERE createdAt >= :sinceMs AND forgivenAt IS NULL ORDER BY createdAt")
    suspend fun activeInWindow(sinceMs: Long): List<Strike>
    @Query("SELECT * FROM Strike WHERE forgivenAt IS NULL AND expiresAt > :now ORDER BY createdAt LIMIT 1")
    suspend fun oldestActive(now: Long = System.currentTimeMillis()): Strike?
    @Query("SELECT * FROM Strike WHERE id = :id LIMIT 1") suspend fun get(id: Long): Strike?
}
