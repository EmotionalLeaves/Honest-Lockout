package com.honestlockout.app.ui.override

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.data.db.ActivityShape
import com.honestlockout.app.data.db.entities.Override
import com.honestlockout.app.data.db.entities.PurposeTemplate
import com.honestlockout.app.data.repo.Eligibility
import com.honestlockout.app.data.repo.OverrideDraft
import com.honestlockout.app.data.rules.PurposeTemplates
import com.honestlockout.app.service.OverrideForegroundService
import com.honestlockout.app.ui.AppIcon
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.AppTextField
import com.honestlockout.app.ui.BottomActionBar
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.GroupHeader
import com.honestlockout.app.ui.Pill
import com.honestlockout.app.ui.PrimaryButton
import com.honestlockout.app.ui.RowDivider
import com.honestlockout.app.ui.SelectRow
import com.honestlockout.app.ui.SegmentedControl
import com.honestlockout.app.ui.ThinSlider
import com.honestlockout.app.ui.cardBorder
import com.honestlockout.app.ui.nav.Routes
import kotlinx.coroutines.launch

const val STATEMENT_MIN = 8
const val STATEMENT_MAX = 120

private data class Gate(val refusal: String?, val activeStrikes: Int, val last: Override?, val active: Override?, val extendMax: Int?)

/**
 * One screen for the whole declaration: which app, what for, what exactly, how long.
 * Replaces the separate purpose picker and declare screens. Lock screen → here → countdown.
 * In extend mode the app and purpose are fixed and the length is capped by the Extend chain.
 */
@Composable
fun UnlockScreen(
    nav: NavController,
    presetPackage: String?,
    presetTemplateId: Long? = null,
    extend: Boolean = false,
    presetStatement: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val templates by AppGraph.overrideRepo.templates().collectAsState(initial = emptyList())
    val rules by AppGraph.blockRepo.rules().collectAsState(initial = emptyList())

    val gate by produceState<Gate?>(null, presetPackage) {
        val now = System.currentTimeMillis()
        val e = AppGraph.overrideRepo.eligibility(presetPackage, null, now, extending = extend)
        val active = if (extend) AppGraph.overrideRepo.getActive() else null
        val extendMax = active?.let { a ->
            val t = AppGraph.overrideRepo.template(a.purposeId)
            val chainStart = AppGraph.settings.snapshot().extendChainStartedAt.takeIf { it in 1..a.startedAt } ?: a.startedAt
            ((t?.maxMinutes ?: a.plannedMin) - ((now - chainStart) / 60_000L).toInt()).coerceAtLeast(1)
        }
        value = Gate((e as? Eligibility.Refused)?.message, AppGraph.strikeRepo.activeCount(now), AppGraph.overrideRepo.lastClean(presetPackage), active, extendMax)
    }

    var templateId by rememberSaveable { mutableLongStateOf(presetTemplateId ?: -1L) }
    var app by rememberSaveable { mutableStateOf(presetPackage) }
    var statement by rememberSaveable { mutableStateOf(presetStatement.orEmpty()) }
    var minutes by rememberSaveable { mutableIntStateOf(-1) }
    var labels by rememberSaveable { mutableStateOf(listOf<String>()) }
    var shape by rememberSaveable { mutableStateOf(ActivityShape.ANY) }
    var showAppSheet by remember { mutableStateOf(false) }

    val g = gate
    val shown = templates.filter { it.enabled }.sortedBy { if (it.id == PurposeTemplates.ID_CUSTOM) Long.MAX_VALUE else it.id }
    val template = shown.firstOrNull { it.id == templateId }
    val allowedAt4 = { t: PurposeTemplate -> (g?.activeStrikes ?: 0) < 4 || t.id in PurposeTemplates.allowedAtFourStrikes }

    // Default purpose: the preset, else the last one used for this app, else Reply.
    LaunchedEffect(shown, g) {
        if (g == null || shown.isEmpty() || templateId >= 0) return@LaunchedEffect
        val pick = g.active?.purposeId ?: g.last?.purposeId?.takeIf { id -> shown.any { it.id == id } } ?: PurposeTemplates.ID_REPLY
        templateId = shown.firstOrNull { it.id == pick && allowedAt4(it) }?.id ?: shown.first { allowedAt4(it) }.id
        g.active?.let { app = it.packageName }
    }
    val maxMinutes = when {
        template == null -> 1
        g?.extendMax != null -> g.extendMax
        else -> template.maxMinutes.coerceAtLeast(1)
    }
    LaunchedEffect(templateId, maxMinutes) {
        val t = template ?: return@LaunchedEffect
        minutes = if (minutes in 1..maxMinutes && presetTemplateId == templateId) minutes else defaultMinutes(t, maxMinutes)
        if (t.id == PurposeTemplates.ID_CUSTOM && labels.isEmpty()) {
            labels = PurposeTemplates.split(t.blockedScreenLabels); shape = t.expectedShape
        }
    }
    val purposeApps = remember(template, rules) {
        val allowed = template?.let { PurposeTemplates.split(it.allowedPackages).filter { p -> Format.isInstalled(context, p) } }.orEmpty()
        (allowed.ifEmpty { rules.map { it.packageName } }).sortedBy { Format.appLabel(context, it).lowercase() }
    }

    LaunchedEffect(purposeApps) { if (app == null && purposeApps.size == 1) app = purposeApps.first() }

    val canStart = g?.refusal == null && template != null && app != null && statement.trim().length >= STATEMENT_MIN
    AppScaffold(
        title = stringResource(if (extend) R.string.unlock_extend_title else R.string.unlock_title),
        onBack = { nav.popBackStack() }, snackbar = snackbar,
        bottomBar = {
            if (g != null && g.refusal == null) BottomActionBar {
                PrimaryButton(stringResource(R.string.unlock_start, Format.minutes(context, minutes.coerceAtLeast(1))), enabled = canStart) {
                    val t = template ?: return@PrimaryButton
                    val pkg = app ?: return@PrimaryButton
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val e = AppGraph.overrideRepo.eligibility(pkg, t.id, now, extending = extend)
                        if (e is Eligibility.Refused) { snackbar.showSnackbar(e.message); return@launch }
                        val custom = t.id == PurposeTemplates.ID_CUSTOM
                        AppGraph.overrideRepo.draft = OverrideDraft(
                            packageName = pkg, templateId = t.id, statement = statement.trim(),
                            minutes = minutes.coerceIn(1, maxMinutes),
                            customLabels = if (custom) labels else null, customShape = if (custom) shape else null,
                            isExtend = extend,
                        )
                        if (extend) OverrideForegroundService.send(context, OverrideForegroundService.ACTION_DONE_FOR_EXTEND)
                        nav.navigate(Routes.OVERRIDE_COUNTDOWN)
                    }
                }
            }
        },
    ) { padding ->
        if (g == null) return@AppScaffold
        if (g.refusal != null) { Refused(g.refusal, padding); return@AppScaffold }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // App
            AppHeader(app, enabled = !extend && (presetPackage == null || purposeApps.size > 1)) { showAppSheet = true }

            // Purpose
            Column {
                Box(Modifier.padding(horizontal = 16.dp)) { GroupHeader(stringResource(R.string.unlock_purpose)) }
                val chipState = rememberLazyListState()
                LaunchedEffect(templateId, shown.size) {
                    val i = shown.indexOfFirst { it.id == templateId }
                    if (i > 1) chipState.animateScrollToItem((i - 1).coerceAtLeast(0))
                }
                LazyRow(
                    state = chipState, modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { t ->
                        PurposeChip(
                            t, selected = t.id == templateId, enabled = !extend && allowedAt4(t),
                        ) {
                            templateId = t.id
                            val allowed = PurposeTemplates.split(t.allowedPackages)
                            if (allowed.isNotEmpty() && app !in allowed) app = allowed.firstOrNull { Format.isInstalled(context, it) } ?: app
                        }
                    }
                }
                template?.let {
                    AnimatedContent(it.id, label = "purposeDesc") { id ->
                        Text(
                            stringResource(PurposeTemplates.descriptionRes(id)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 10.dp).fillMaxWidth(),
                        )
                    }
                }
            }

            // Statement
            Column(Modifier.padding(horizontal = 16.dp)) {
                GroupHeader(stringResource(R.string.declare_label))
                AppTextField(
                    value = statement, onValueChange = { statement = it.replace('\n', ' ').take(STATEMENT_MAX) },
                    placeholder = stringResource(R.string.declare_placeholder), minLines = 2,
                )
                val missing = STATEMENT_MIN - statement.trim().length
                if (statement.isNotEmpty() && missing > 0) {
                    Text(
                        stringResource(R.string.unlock_more_chars, missing), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                    )
                }
                val last = g.last
                val suggestions = buildList {
                    if (last != null && !extend && last.statement != statement) add(last.statement)
                    addAll(suggestionsFor(templateId))
                }.distinct()
                if (statement.isBlank() && suggestions.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) { suggestions.forEach { sug -> Pill(sug) { statement = sug } } }
                }
            }

            // Length
            Column(Modifier.padding(horizontal = 16.dp)) {
                GroupHeader(stringResource(R.string.unlock_length))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = cardBorder()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(Format.minutes(context, minutes.coerceAtLeast(1)), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.unlock_max, Format.minutes(context, maxMinutes)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (maxMinutes > 1) {
                            val snap = if (maxMinutes > 60) 5 else 1
                            ThinSlider(
                                value = minutes.coerceIn(1, maxMinutes).toFloat(),
                                onValueChange = { v ->
                                    val m = if (snap > 1) (Math.round(v / snap) * snap).coerceAtLeast(snap) else Math.round(v)
                                    minutes = m.coerceIn(1, maxMinutes)
                                },
                                valueRange = 1f..maxMinutes.toFloat(),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            val quick = (if (maxMinutes > 60) listOf(15, 30, 60, 120, 240) else listOf(2, 5, 10, 15, 30)).filter { it < maxMinutes } + maxMinutes
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                quick.distinct().takeLast(5).forEach { m ->
                                    Text(
                                        Format.minutes(context, m), style = MaterialTheme.typography.labelMedium,
                                        color = if (m == minutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { minutes = m }.padding(horizontal = 6.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (template?.expectedShape == ActivityShape.LISTEN) {
                    Text(
                        stringResource(R.string.declare_listen_note, maxOf(5, minutes / 10)), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                }
            }

            // Custom purpose extras
            if (template?.id == PurposeTemplates.ID_CUSTOM) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    GroupHeader(stringResource(R.string.declare_custom_shape))
                    val shapes = ActivityShape.entries
                    SegmentedControl(shapes.map { shapeName(it) }, shapes.indexOf(shape)) { shape = shapes[it] }
                    Text(shapeHint(shape), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    GroupHeader(stringResource(R.string.declare_custom_screens))
                    val all = remember { AppGraph.screenRules.allLabels() }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = cardBorder()) {
                        Column {
                            all.forEachIndexed { i, l ->
                                if (i > 0) RowDivider()
                                SelectRow(title = screenLabel(l), selected = l in labels) { labels = if (l in labels) labels - l else labels + l }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (showAppSheet) AppSheet(purposeApps, onPick = { app = it; showAppSheet = false }, onDismiss = { showAppSheet = false })
    }
}

private fun defaultMinutes(t: PurposeTemplate, max: Int): Int = when (t.id) {
    PurposeTemplates.ID_LISTEN -> minOf(120, max)
    PurposeTemplates.ID_NAVIGATION -> minOf(30, max)
    else -> max
}

/** Big app identity at the top; tap to change when more than one app fits. */
@Composable
private fun AppHeader(pkg: String?, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (pkg != null) {
            AppIcon(pkg, 52)
            Column(Modifier.weight(1f)) {
                Text(Format.appLabel(context, pkg), style = MaterialTheme.typography.titleLarge)
                if (enabled) Text(stringResource(R.string.unlock_change_app), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(stringResource(R.string.declare_choose_app), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        if (enabled) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PurposeChip(t: PurposeTemplate, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "chipBorder")
    Column(
        Modifier.width(96.dp).alpha(if (enabled || selected) 1f else 0.35f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            purposeIcon(t.id), contentDescription = null, modifier = Modifier.size(22.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            purposeShortName(t), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun purposeShortName(t: PurposeTemplate): String = when (t.id) {
    PurposeTemplates.ID_NAVIGATION -> stringResource(R.string.purpose_short_navigation)
    PurposeTemplates.ID_REPLY -> stringResource(R.string.purpose_short_reply)
    PurposeTemplates.ID_LOOKUP -> stringResource(R.string.purpose_short_lookup)
    PurposeTemplates.ID_WORK -> stringResource(R.string.purpose_short_work)
    PurposeTemplates.ID_PAY -> stringResource(R.string.purpose_short_pay)
    PurposeTemplates.ID_LISTEN -> stringResource(R.string.purpose_short_listen)
    PurposeTemplates.ID_CUSTOM -> stringResource(R.string.purpose_short_custom)
    else -> t.name
}

/** Overrides aren't available right now: say why, nothing else. */
@Composable
private fun Refused(message: String, padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.unlock_unavailable), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AppSheet(apps: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        AppTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            placeholder = stringResource(R.string.apppicker_search), modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (apps.isEmpty()) {
            Text(stringResource(R.string.declare_no_apps), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.padding(top = 8.dp, bottom = 24.dp)) {
            items(apps.filter { query.isBlank() || Format.appLabel(context, it).contains(query, true) }, key = { it }) { p ->
                ListItem(
                    modifier = Modifier.clickable { onPick(p) },
                    leadingContent = { AppIcon(p) },
                    headlineContent = { Text(Format.appLabel(context, p)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
    }
}

fun purposeIcon(id: Long): ImageVector = when (id) {
    PurposeTemplates.ID_NAVIGATION -> Icons.Outlined.Navigation
    PurposeTemplates.ID_REPLY -> Icons.Outlined.ChatBubbleOutline
    PurposeTemplates.ID_LOOKUP -> Icons.Outlined.Search
    PurposeTemplates.ID_WORK -> Icons.Outlined.WorkOutline
    PurposeTemplates.ID_PAY -> Icons.Outlined.AccountBalance
    PurposeTemplates.ID_LISTEN -> Icons.Outlined.Headphones
    else -> Icons.Outlined.Tune
}

/** "home_feed" -> "home feed". */
fun screenLabel(label: String) = label.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

@Composable
private fun suggestionsFor(templateId: Long): List<String> = when (templateId) {
    PurposeTemplates.ID_NAVIGATION -> stringArrayResource(R.array.suggest_navigation)
    PurposeTemplates.ID_REPLY -> stringArrayResource(R.array.suggest_reply)
    PurposeTemplates.ID_LOOKUP -> stringArrayResource(R.array.suggest_lookup)
    PurposeTemplates.ID_WORK -> stringArrayResource(R.array.suggest_work)
    PurposeTemplates.ID_PAY -> stringArrayResource(R.array.suggest_pay)
    PurposeTemplates.ID_LISTEN -> stringArrayResource(R.array.suggest_listen)
    else -> emptyArray()
}.toList()

@Composable
private fun shapeHint(shape: ActivityShape) = stringResource(
    when (shape) {
        ActivityShape.TYPING -> R.string.shape_typing_hint
        ActivityShape.READING -> R.string.shape_reading_hint
        ActivityShape.PASSIVE -> R.string.shape_passive_hint
        ActivityShape.ANY -> R.string.shape_any_hint
        ActivityShape.LISTEN -> R.string.shape_listen_hint
    }
)

@Composable
private fun shapeName(shape: ActivityShape) = stringResource(
    when (shape) {
        ActivityShape.TYPING -> R.string.shape_typing
        ActivityShape.READING -> R.string.shape_reading
        ActivityShape.PASSIVE -> R.string.shape_passive
        ActivityShape.ANY -> R.string.shape_any
        ActivityShape.LISTEN -> R.string.shape_listen
    }
)

