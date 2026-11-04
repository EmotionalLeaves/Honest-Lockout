package com.honestlockout.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.honestlockout.app.AppGraph
import com.honestlockout.app.data.db.BlockMode
import com.honestlockout.app.data.db.entities.Override
import com.honestlockout.app.data.repo.BlockRepository
import com.honestlockout.app.data.repo.OverrideRepository
import com.honestlockout.app.data.repo.PendingChangeRepository
import com.honestlockout.app.data.repo.StrikeRepository
import com.honestlockout.app.data.repo.UsageRepository
import com.honestlockout.app.engine.DayClock
import com.honestlockout.app.service.OverrideForegroundService
import com.honestlockout.app.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class BudgetRow(val packageName: String, val name: String, val budgetMin: Int, val leftMin: Int)

data class HomeState(
    val loaded: Boolean = false,
    val todayMinutes: Int = 0,
    val budgets: List<BudgetRow> = emptyList(),
    val activeStrikes: Int = 0,
    val oldestStrikeExpiresAt: Long? = null,
    val nextOverrideAt: Long? = null,
    val active: Override? = null,
    val activeMinutesLeft: Int = 0,
    val pendingCount: Int = 0,
    val accessibilityOn: Boolean = true,
)

class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val blockRepo: BlockRepository = AppGraph.blockRepo,
    private val overrideRepo: OverrideRepository = AppGraph.overrideRepo,
    private val strikeRepo: StrikeRepository = AppGraph.strikeRepo,
    private val usageRepo: UsageRepository = AppGraph.usageRepo,
    private val pendingRepo: PendingChangeRepository = AppGraph.pendingRepo,
) : AndroidViewModel(application) {

    private val refresh = MutableStateFlow(0)

    /** Re-evaluated every 30 s, and whenever a table it depends on changes. */
    private val clock = flow {
        while (true) { emit(System.currentTimeMillis()); delay(30_000L) }
    }

    val state: StateFlow<HomeState> = combine(
        clock, refresh, overrideRepo.active(), blockRepo.rules(),
        combine(strikeRepo.strikes(), strikeRepo.lockouts(), pendingRepo.pendingCount()) { a, b, c -> Triple(a, b, c) },
    ) { now0, _, active, rules, (_, _, pendingCount) ->
        val now = maxOf(now0, System.currentTimeMillis())
        val ctx = getApplication<Application>()
        val today = DayClock.dateKey(now)
        val budgets = rules.filter { it.mode == BlockMode.BUDGET }.map { r ->
            val used = AppGraph.db.usageDayDao().minutesFor(today, r.packageName) + AppGraph.liveUsage.minutesFor(r.packageName, now)
            BudgetRow(r.packageName, Format.appLabel(ctx, r.packageName), r.dailyBudgetMin, (r.dailyBudgetMin - used).coerceAtLeast(0))
        }
        HomeState(
            loaded = true,
            todayMinutes = usageRepo.todayTotalMinutes(now),
            budgets = budgets,
            activeStrikes = strikeRepo.activeCount(now),
            oldestStrikeExpiresAt = strikeRepo.oldestActive(now)?.expiresAt,
            nextOverrideAt = strikeRepo.activeGlobalLockout(now)?.until,
            active = active,
            activeMinutesLeft = active?.let {
                (((it.startedAt + it.plannedMin * 60_000L) - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
            } ?: 0,
            pendingCount = pendingCount,
            accessibilityOn = AppGraph.accessibilityConnected,
        )
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), HomeState())

    fun refreshNow() { refresh.value++ }

    fun done() = OverrideForegroundService.send(getApplication(), OverrideForegroundService.ACTION_DONE)
    fun lockIt() = OverrideForegroundService.send(getApplication(), OverrideForegroundService.ACTION_CONFESS)
}
