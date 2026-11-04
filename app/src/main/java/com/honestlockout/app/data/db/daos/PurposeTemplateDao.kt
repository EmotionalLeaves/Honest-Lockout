package com.honestlockout.app.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.honestlockout.app.data.db.entities.PurposeTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface PurposeTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: PurposeTemplate): Long
    @Update suspend fun update(item: PurposeTemplate)
    @Delete suspend fun delete(item: PurposeTemplate)
    @Query("SELECT * FROM PurposeTemplate ORDER BY id") fun getAll(): Flow<List<PurposeTemplate>>
    @Query("SELECT * FROM PurposeTemplate WHERE id = :id LIMIT 1") suspend fun get(id: Long): PurposeTemplate?
    @Query("SELECT COUNT(*) FROM PurposeTemplate") suspend fun count(): Int
}
