package com.honestlockout.app.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.honestlockout.app.data.db.entities.Schedule
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Insert suspend fun insert(item: Schedule): Long
    @Update suspend fun update(item: Schedule)
    @Delete suspend fun delete(item: Schedule)
    @Query("SELECT * FROM Schedule ORDER BY name") fun getAll(): Flow<List<Schedule>>
    @Query("SELECT * FROM Schedule WHERE id = :id LIMIT 1") suspend fun get(id: Long): Schedule?
}
