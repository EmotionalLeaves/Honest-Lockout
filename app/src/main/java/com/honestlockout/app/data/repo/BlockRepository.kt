package com.honestlockout.app.data.repo

import com.honestlockout.app.data.db.AppDatabase
import com.honestlockout.app.data.db.BlockMode
import com.honestlockout.app.data.db.entities.BlockRule
import com.honestlockout.app.data.db.entities.Schedule
import kotlinx.coroutines.flow.Flow

/**
 * Block rules and schedules. Adding a rule is tightening and happens at once. Every other
 * change to a rule goes through LoosenGuard, which calls [applyMode] / [removeRule] when the
 * change is applied (now, or after the loosening delay).
 */
class BlockRepository(private val db: AppDatabase) {
    private val rules = db.blockRuleDao()
    private val schedules = db.scheduleDao()

    fun rules(): Flow<List<BlockRule>> = rules.getAll()
    fun schedules(): Flow<List<Schedule>> = schedules.getAll()

    suspend fun getRule(packageName: String): BlockRule? = rules.get(packageName)
    suspend fun ruleCount(): Int = rules.count()

    /** Adds [packageName] as ALWAYS if it is not already blocked. */
    suspend fun add(packageName: String, now: Long = System.currentTimeMillis()) {
        if (rules.get(packageName) == null) {
            rules.insert(BlockRule(packageName = packageName, mode = BlockMode.ALWAYS, createdAt = now))
        }
    }

    suspend fun applyMode(packageName: String, mode: BlockMode, budgetMin: Int, scheduleId: Long?) {
        val existing = rules.get(packageName) ?: return
        rules.update(
            existing.copy(
                mode = mode,
                dailyBudgetMin = if (mode == BlockMode.BUDGET) budgetMin else existing.dailyBudgetMin,
                scheduleId = if (mode == BlockMode.SCHEDULE) scheduleId else existing.scheduleId,
            )
        )
    }

    suspend fun removeRule(packageName: String) = rules.deleteByPackage(packageName)

    suspend fun getSchedule(id: Long): Schedule? = schedules.get(id)

    /** Inserts when id == 0, else updates. Returns the id. */
    suspend fun saveSchedule(schedule: Schedule): Long =
        if (schedule.id == 0L) schedules.insert(schedule) else schedule.also { schedules.update(it) }.id

    suspend fun scheduleInUse(id: Long): Boolean = rules.countUsingSchedule(id) > 0

    /** Refused (returns false) when any rule uses the schedule. */
    suspend fun deleteSchedule(id: Long): Boolean {
        if (scheduleInUse(id)) return false
        schedules.get(id)?.let { schedules.delete(it) }
        return true
    }
}
