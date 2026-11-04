package com.honestlockout.app.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.honestlockout.app.data.db.entities.Lockout
import kotlinx.coroutines.flow.Flow

@Dao
interface LockoutDao {
    @Insert suspend fun insert(item: Lockout): Long
    @Update suspend fun update(item: Lockout)
    @Delete suspend fun delete(item: Lockout)
    @Query("SELECT * FROM Lockout ORDER BY until DESC") fun getAll(): Flow<List<Lockout>>
    @Query("SELECT * FROM Lockout WHERE packageName = :packageName AND until > :now ORDER BY until DESC LIMIT 1")
    suspend fun activeFor(packageName: String, now: Long): Lockout?
    @Query("SELECT * FROM Lockout WHERE packageName IS NULL AND until > :now ORDER BY until DESC LIMIT 1")
    suspend fun activeGlobal(now: Long): Lockout?
    @Query("SELECT COUNT(*) FROM Lockout WHERE until > :now") suspend fun activeCount(now: Long): Int
    @Query("DELETE FROM Lockout WHERE until < :now") suspend fun deleteExpired(now: Long): Int
}
