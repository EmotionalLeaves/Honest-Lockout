package com.honestlockout.app.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.honestlockout.app.data.db.entities.UsageDay
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: UsageDay)
    @Update suspend fun update(item: UsageDay)
    @Delete suspend fun delete(item: UsageDay)
    @Query("SELECT * FROM UsageDay ORDER BY date DESC") fun getAll(): Flow<List<UsageDay>>
    @Query("SELECT * FROM UsageDay WHERE date = :date ORDER BY minutes DESC") suspend fun forDate(date: String): List<UsageDay>
    @Query("SELECT COALESCE(SUM(minutes), 0) FROM UsageDay WHERE date = :date AND packageName = :packageName")
    suspend fun minutesFor(date: String, packageName: String): Int
    @Query("INSERT OR REPLACE INTO UsageDay(date, packageName, minutes) VALUES(:date, :packageName, :minutes)")
    suspend fun upsert(date: String, packageName: String, minutes: Int)
    @Query("SELECT COUNT(*) FROM UsageDay WHERE date = :date") suspend fun countFor(date: String): Int
}
