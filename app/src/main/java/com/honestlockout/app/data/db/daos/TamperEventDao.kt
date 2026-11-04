package com.honestlockout.app.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.honestlockout.app.data.db.entities.TamperEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface TamperEventDao {
    @Insert suspend fun insert(item: TamperEvent): Long
    @Update suspend fun update(item: TamperEvent)
    @Delete suspend fun delete(item: TamperEvent)
    @Query("SELECT * FROM TamperEvent ORDER BY detectedAt DESC") fun getAll(): Flow<List<TamperEvent>>
    @Query("SELECT * FROM TamperEvent WHERE kind = 'SERVICE_OFF' AND strikeId IS NULL ORDER BY detectedAt")
    suspend fun unpunishedServiceOff(): List<TamperEvent>
    @Query("SELECT * FROM TamperEvent WHERE kind = 'REBOOT' AND detectedAt >= :sinceMs LIMIT 1")
    suspend fun rebootSince(sinceMs: Long): TamperEvent?
}
