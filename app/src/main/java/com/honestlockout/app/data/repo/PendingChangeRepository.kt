package com.honestlockout.app.data.repo

import com.honestlockout.app.data.db.AppDatabase
import com.honestlockout.app.data.db.entities.PendingChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Queued loosening changes. Applied rows are deleted by PendingChangeWorker so they are never
 * applied twice. Cancelling is tightening, so it is instant.
 */
class PendingChangeRepository(private val db: AppDatabase) {
    private val dao = db.pendingChangeDao()

    fun pending(): Flow<List<PendingChange>> = dao.pending()
    fun pendingCount(): Flow<Int> = dao.pending().map { it.size }

    suspend fun insert(change: PendingChange): Long = dao.insert(change)
    suspend fun due(now: Long): List<PendingChange> = dao.due(now)
    suspend fun pendingOnce(): List<PendingChange> = dao.pendingOnce()
    suspend fun hasPendingLoosening(): Boolean = dao.pendingOnce().isNotEmpty()

    suspend fun cancel(id: Long) {
        dao.get(id)?.let { dao.update(it.copy(cancelled = true)) }
    }

    suspend fun delete(id: Long) {
        dao.get(id)?.let { dao.delete(it) }
    }

    fun breakGlass(): Flow<PendingChange?> = dao.pending().map { list -> list.firstOrNull { isBreakGlass(it) } }
    suspend fun breakGlassOnce(): PendingChange? = dao.pendingOnce().firstOrNull { isBreakGlass(it) }

    companion object {
        fun isBreakGlass(c: PendingChange) = c.changeJson.contains("\"BreakGlass\"")
        fun isAllowDisable(c: PendingChange) = c.changeJson.contains("\"AllowDisable\"")
    }
}
