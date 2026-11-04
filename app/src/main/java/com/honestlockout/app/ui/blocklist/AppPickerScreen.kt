package com.honestlockout.app.ui.blocklist

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.data.db.BlockMode
import com.honestlockout.app.engine.EmergencyAllowlist
import com.honestlockout.app.ui.AppIcon
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.AppTextField
import com.honestlockout.app.ui.PrimaryButton
import com.honestlockout.app.ui.RowDivider
import com.honestlockout.app.ui.SegmentedControl
import com.honestlockout.app.ui.EmptyState
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.Stepper
import com.honestlockout.app.ui.nav.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppCategory { ALL, SOCIAL, GAMES, VIDEO, OTHER }

data class LaunchableApp(val packageName: String, val label: String, val category: AppCategory)

/** All launchable apps (MAIN/LAUNCHER), alphabetical, with Android's own app category. */
fun launchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map {
            val cat = when (it.activityInfo.applicationInfo.category) {
                ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
                ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
                ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
                else -> AppCategory.OTHER
            }
            LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString(), cat)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/**
 * Pick one app or every app: checkboxes, select-all for the current filter, category chips,
 * search, and a mode (Always / Budget / Schedule) applied to the whole selection at once.
 * Emergency apps and already-blocked apps are not shown.
 */
@Composable
fun AppPickerScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blocked by AppGraph.blockRepo.rules().collectAsState(initial = emptyList())
    val schedules by AppGraph.blockRepo.schedules().collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(AppCategory.ALL) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    var mode by rememberSaveable { mutableStateOf(BlockMode.ALWAYS) }
    var budget by rememberSaveable { mutableIntStateOf(30) }
    var scheduleId by rememberSaveable { mutableStateOf<Long?>(null) }
    val apps by produceState<List<LaunchableApp>?>(initialValue = null) {
        EmergencyAllowlist.refreshDefaults(context)
        value = withContext(Dispatchers.Default) { launchableApps(context) }
    }
    val blockedSet = remember(blocked) { blocked.map { it.packageName }.toSet() }

    AppScaffold(title = stringResource(R.string.apppicker_title), onBack = { nav.popBackStack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AppTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                placeholder = stringResource(R.string.apppicker_search), singleLine = true,
            )
            val all = apps
            if (all == null) {
                CircularProgressIndicator(Modifier.padding(32.dp).align(Alignment.CenterHorizontally))
                return@Column
            }
            val available = all.filter { !EmergencyAllowlist.contains(it.packageName) && it.packageName !in blockedSet }
            val shown = available.filter {
                (category == AppCategory.ALL || it.category == category) &&
                    (query.isBlank() || it.label.contains(query, ignoreCase = true))
            }
            val cats = AppCategory.entries.filter { c -> c == AppCategory.ALL || available.any { it.category == c } }
            SegmentedControl(
                cats.map { categoryName(it) }, cats.indexOf(category).coerceAtLeast(0),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) { category = cats[it] }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val allShownSelected = shown.isNotEmpty() && shown.all { it.packageName in selected }
                TextButton(onClick = {
                    selected = if (allShownSelected) selected - shown.map { it.packageName }.toSet()
                    else selected + shown.map { it.packageName }
                }) { Text(stringResource(if (allShownSelected) R.string.apppicker_clear_all else R.string.apppicker_select_all, shown.size)) }
                Text(
                    stringResource(R.string.apppicker_selected, selected.size), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (shown.isEmpty()) { EmptyState(stringResource(R.string.apppicker_empty)); return@Column }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    ListItem(
                        modifier = Modifier.clickable { selected = if (checked) selected - app.packageName else selected + app.packageName },
                        leadingContent = { AppIcon(app.packageName) },
                        headlineContent = { Text(app.label) },
                        supportingContent = if (app.category != AppCategory.OTHER) ({ Text(categoryName(app.category), color = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
                        trailingContent = {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape)
                                    .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) { if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    )
                    RowDivider(inset = 72)
                }
            }
            AnimatedVisibility(selected.isNotEmpty(), enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val modes = BlockMode.entries
                        SegmentedControl(modes.map { modeName(it) }, modes.indexOf(mode)) { mode = modes[it] }
                        when (mode) {
                            BlockMode.BUDGET -> Stepper(
                                label = stringResource(R.string.rule_budget_label), value = budget,
                                options = BUDGET_OPTIONS, format = { Format.minutes(context, it) }, onChange = { budget = it },
                            )
                            BlockMode.SCHEDULE -> Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                schedules.forEach { s ->
                                    FilterChip(selected = scheduleId == s.id, onClick = { scheduleId = s.id }, label = { Text(s.name) })
                                }
                                FilterChip(selected = false, onClick = { nav.navigate(Routes.schedule(null)) }, label = { Text(stringResource(R.string.rule_new_schedule)) })
                            }
                            BlockMode.ALWAYS -> Unit
                        }
                        PrimaryButton(stringResource(R.string.apppicker_block_n, selected.size), enabled = mode != BlockMode.SCHEDULE || scheduleId != null) {
                            scope.launch {
                                AppGraph.blockRepo.addAll(selected, mode, budget, scheduleId)
                                nav.popBackStack()
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun categoryName(c: AppCategory) = stringResource(
    when (c) {
        AppCategory.ALL -> R.string.cat_all
        AppCategory.SOCIAL -> R.string.cat_social
        AppCategory.GAMES -> R.string.cat_games
        AppCategory.VIDEO -> R.string.cat_video
        AppCategory.OTHER -> R.string.cat_other
    }
)

@Composable
fun modeName(m: BlockMode) = stringResource(
    when (m) {
        BlockMode.ALWAYS -> R.string.mode_always
        BlockMode.BUDGET -> R.string.mode_budget
        BlockMode.SCHEDULE -> R.string.mode_schedule
    }
)
