package com.honestlockout.app.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.honestlockout.app.data.db.entities.ActivitySample
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivitySampleDao {
    @Insert suspend fun insert(item: ActivitySample): Long
    @Update suspend fun update(item: ActivitySample)
    @Delete suspend fun delete(item: ActivitySample)
    @Query("SELECT * FROM ActivitySample ORDER BY id") fun getAll(): Flow<List<ActivitySample>>
    @Query("SELECT * FROM ActivitySample WHERE overrideId = :id ORDER BY minuteIndex") suspend fun forOverride(id: Long): List<ActivitySample>
    @Query("SELECT * FROM ActivitySample WHERE overrideId = :overrideId ORDER BY minuteIndex DESC LIMIT :n")
    suspend fun lastMinutes(overrideId: Long, n: Int): List<ActivitySample>
    @Query("SELECT * FROM ActivitySample WHERE overrideId IN (:ids)") suspend fun forOverrides(ids: List<Long>): List<ActivitySample>
}
