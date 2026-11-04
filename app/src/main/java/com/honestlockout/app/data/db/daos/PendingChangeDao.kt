package com.honestlockout.app.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.honestlockout.app.data.db.entities.PendingChange
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingChangeDao {
    @Insert suspend fun insert(item: PendingChange): Long
    @Update suspend fun update(item: PendingChange)
    @Delete suspend fun delete(item: PendingChange)
    @Query("SELECT * FROM PendingChange ORDER BY applyAt") fun getAll(): Flow<List<PendingChange>>
    @Query("SELECT * FROM PendingChange WHERE applyAt <= :now AND cancelled = 0 ORDER BY applyAt")
    suspend fun due(now: Long): List<PendingChange>
    @Query("SELECT * FROM PendingChange WHERE cancelled = 0 ORDER BY applyAt") fun pending(): Flow<List<PendingChange>>
    @Query("SELECT * FROM PendingChange WHERE cancelled = 0 ORDER BY applyAt") suspend fun pendingOnce(): List<PendingChange>
    @Query("SELECT * FROM PendingChange WHERE id = :id LIMIT 1") suspend fun get(id: Long): PendingChange?
    /** Returns 1 if this call removed the row (used to claim a change before applying it). */
    @Query("DELETE FROM PendingChange WHERE id = :id") suspend fun deleteById(id: Long): Int
}
