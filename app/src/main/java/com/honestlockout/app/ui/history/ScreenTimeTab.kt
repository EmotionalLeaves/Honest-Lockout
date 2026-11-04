package com.honestlockout.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.engine.DayClock
import com.honestlockout.app.ui.AppIcon
import com.honestlockout.app.ui.EmptyState
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.RowDivider
import com.honestlockout.app.ui.theme.Primary
import com.honestlockout.app.ui.theme.SurfaceHighest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private data class Day(val date: LocalDate, val perApp: List<Pair<String, Int>>) {
    val total: Int get() = perApp.sumOf { it.second }
}

/** 7-day bar chart (Compose Canvas, no library) and a per-app list for the selected day. */
@Composable
fun ScreenTimeTab() {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableIntStateOf(6) }
    val days by produceState<List<Day>?>(null) {
        value = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val today = DayClock.localDate(now)
            (6 downTo 0).map { back ->
                val d = today.minusDays(back.toLong())
                val apps = if (back == 0) {
                    AppGraph.usageRepo.todayMinutesByPackage(now).toList()
                } else {
                    AppGraph.usageRepo.forDate(d).map { it.packageName to it.minutes }
                }
                Day(d, apps.sortedByDescending { it.second })
            }
        }
    }
    val list = days ?: return
    if (!AppGraph.usageRepo.hasUsageAccess()) { EmptyState(stringResource(R.string.history_no_usage_access)); return }
    val day = list[selected.coerceIn(0, list.lastIndex)]
    val max = (list.maxOf { it.total }).coerceAtLeast(1)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(Format.date(context, DayClock.startOfDay(day.date)).uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Format.minutes(context, day.total), style = MaterialTheme.typography.headlineMedium)
        }
        Canvas(
            Modifier.fillMaxWidth().height(160.dp).pointerInput(list) {
                detectTapGestures { pos -> selected = (pos.x / (size.width / list.size.toFloat())).toInt().coerceIn(0, list.lastIndex) }
            }
        ) {
            val slot = size.width / list.size
            val barW = slot * 0.6f
            list.forEachIndexed { i, d ->
                val h = (d.total / max.toFloat()) * (size.height - 8f)
                drawRoundRect(
                    color = if (i == selected) Primary else SurfaceHighest,
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = Size(barW, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            list.forEach {
                Text(
                    it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    color = if (it == day) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)
        if (day.perApp.isEmpty()) { EmptyState(stringResource(R.string.history_no_usage_day)); return@Column }
        LazyColumn(Modifier.fillMaxSize()) {
            items(day.perApp, key = { it.first }) { (pkg, minutes) ->
                ListItem(
                    leadingContent = { AppIcon(pkg, 32) },
                    headlineContent = { Text(Format.appLabel(context, pkg)) },
                    trailingContent = { Text(Format.minutes(context, minutes), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
                RowDivider(inset = 64)
            }
        }
    }
}
