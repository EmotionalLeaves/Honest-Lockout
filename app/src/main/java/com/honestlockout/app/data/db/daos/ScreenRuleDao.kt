package com.honestlockout.app.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.honestlockout.app.data.db.entities.ScreenRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenRuleDao {
    @Insert suspend fun insert(item: ScreenRule): Long
    @Insert suspend fun insertAll(items: List<ScreenRule>)
    @Update suspend fun update(item: ScreenRule)
    @Delete suspend fun delete(item: ScreenRule)
    @Query("SELECT * FROM ScreenRule ORDER BY packageName, label") fun getAll(): Flow<List<ScreenRule>>
    @Query("SELECT * FROM ScreenRule") suspend fun getAllOnce(): List<ScreenRule>
    @Query("DELETE FROM ScreenRule") suspend fun clear()
}
