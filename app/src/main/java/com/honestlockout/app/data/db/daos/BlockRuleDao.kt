package com.honestlockout.app.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.honestlockout.app.data.db.entities.BlockRule
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: BlockRule)
    @Update suspend fun update(item: BlockRule)
    @Delete suspend fun delete(item: BlockRule)
    @Query("SELECT * FROM BlockRule ORDER BY createdAt") fun getAll(): Flow<List<BlockRule>>
    @Query("SELECT * FROM BlockRule WHERE packageName = :packageName LIMIT 1") suspend fun get(packageName: String): BlockRule?
    @Query("SELECT * FROM BlockRule") suspend fun getAllOnce(): List<BlockRule>
    @Query("SELECT COUNT(*) FROM BlockRule") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM BlockRule WHERE scheduleId = :scheduleId") suspend fun countUsingSchedule(scheduleId: Long): Int
    @Query("DELETE FROM BlockRule WHERE packageName = :packageName") suspend fun deleteByPackage(packageName: String)
}
