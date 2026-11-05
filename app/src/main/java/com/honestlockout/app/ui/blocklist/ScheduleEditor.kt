package com.honestlockout.app.ui.blocklist

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.ui.cardBorder
import com.honestlockout.app.data.db.entities.Schedule
import com.honestlockout.app.engine.ScheduleLogic
import com.honestlockout.app.engine.SettingChange
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.AppTextField
import com.honestlockout.app.ui.BottomActionBar
import com.honestlockout.app.ui.DayToggles
import com.honestlockout.app.ui.GroupHeader
import com.honestlockout.app.ui.InfoNote
import com.honestlockout.app.ui.PrimaryButton
import com.honestlockout.app.ui.RowDivider
import com.honestlockout.app.ui.SectionCard
import com.honestlockout.app.ui.TextLink
import com.honestlockout.app.ui.guardMessage
import kotlinx.coroutines.launch

/**
 * Name, 7 day toggles, start and end time. Editing a schedule that a rule uses goes through
 * LoosenGuard (fewer days or shorter hours is loosening). Delete is refused while a rule uses it.
 */
@Composable
fun ScheduleEditorScreen(nav: NavController, id: Long?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val dayNames = stringArrayResource(R.array.day_short_names)
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf(context.getString(R.string.schedule_default_name)) }
    var days by rememberSaveable { mutableStateOf("1111111") }
    var start by rememberSaveable { mutableIntStateOf(22 * 60) }
    var end by rememberSaveable { mutableIntStateOf(7 * 60) }

    LaunchedEffect(id) {
        if (id != null && !loaded) {
            AppGraph.blockRepo.getSchedule(id)?.let { name = it.name; days = it.days; start = it.startMinute; end = it.endMinute }
            loaded = true
        }
    }

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, DateFormat.is24HourFormat(context)).show()
    }

    AppScaffold(
        title = stringResource(if (id == null) R.string.schedule_new_title else R.string.schedule_edit_title),
        onBack = { nav.popBackStack() }, snackbar = snackbar,
        bottomBar = {
            if (loaded) BottomActionBar {
                PrimaryButton(stringResource(R.string.action_save), enabled = name.isNotBlank() && days.contains('1') && start != end) {
                    scope.launch {
                        if (id == null) {
                            val newId = AppGraph.blockRepo.saveSchedule(Schedule(name = name.trim(), days = days, startMinute = start, endMinute = end))
                            nav.previousBackStackEntry?.savedStateHandle?.set(NEW_SCHEDULE_KEY, newId)
                            nav.popBackStack()
                        } else {
                            val r = AppGraph.loosenGuard.request(SettingChange.SetSchedule(id, name.trim(), days, start, end))
                            snackbar.showSnackbar(guardMessage(context, r))
                        }
                    }
                }
                if (id != null) TextLink(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) {
                    scope.launch {
                        if (AppGraph.blockRepo.deleteSchedule(id)) nav.popBackStack()
                        else snackbar.showSnackbar(context.getString(R.string.schedule_in_use))
                    }
                }
            }
        },
    ) { padding ->
        if (!loaded) return@AppScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                GroupHeader(stringResource(R.string.schedule_name))
                AppTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, placeholder = stringResource(R.string.schedule_default_name))
            }
            SectionCard(stringResource(R.string.schedule_days)) {
                DayToggles(days, dayNames) { days = it }
            }
            Column {
                GroupHeader(stringResource(R.string.schedule_hours))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = cardBorder(),
                ) {
                    Column {
                        TimeRow(stringResource(R.string.schedule_starts), ScheduleLogic.hhmm(start)) { pickTime(start) { start = it } }
                        RowDivider()
                        TimeRow(stringResource(R.string.schedule_ends), ScheduleLogic.hhmm(end)) { pickTime(end) { end = it } }
                    }
                }
                if (end <= start) {
                    Text(
                        stringResource(R.string.schedule_crosses_midnight), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
            }
            if (id != null) InfoNote(stringResource(R.string.rule_loosen_note))
        }
    }
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}
