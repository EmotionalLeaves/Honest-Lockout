package com.honestlockout.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.honestlockout.app.R
import com.honestlockout.app.engine.GuardResult

/** Standard screen frame: title, optional back arrow, snackbar host. */
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbar: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    titleModifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // One compact row directly under the status bar: back chevron (if any) and a large title.
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title, modifier = titleModifier, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = stringResource(R.string.cd_back), modifier = Modifier.size(20.dp))
                        }
                    }
                },
                actions = { actions() },
                expandedHeight = 64.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) GroupHeader(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/** Small muted uppercase label above a grouped card. */
@Composable
fun GroupHeader(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun AppIcon(packageName: String, size: Int = 40) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching { Format.appIcon(context, packageName)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(size.dp))
    else Spacer(Modifier.size(size.dp))
}

/**
 * A -/+ stepper over an ordered list of allowed values. [format] renders a value.
 * The displayed value is the current setting; a queued change does not move it.
 */
@Composable
fun Stepper(
    label: String,
    value: Int,
    options: List<Int>,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
    hint: String? = null,
) {
    val idx = options.indexOf(value).let { if (it >= 0) it else options.indexOfFirst { o -> o >= value }.coerceAtLeast(0) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(enabled = enabled && idx > 0, onClick = { onChange(options[idx - 1]) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_decrease), modifier = Modifier.size(18.dp))
            }
            Text(format(value), Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
            IconButton(enabled = enabled && idx < options.lastIndex, onClick = { onChange(options[idx + 1]) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_increase), modifier = Modifier.size(18.dp))
            }
        }
    }
}

fun rangeOptions(from: Int, to: Int, step: Int = 1): List<Int> = (from..to step step).toList()

/** "Applied" or "Queued. Applies <date time>." */
fun guardMessage(context: Context, result: GuardResult): String = when (result) {
    GuardResult.Applied -> context.getString(R.string.guard_applied)
    is GuardResult.Queued -> context.getString(R.string.guard_queued, Format.dateTime(context, result.applyAt))
}

@Composable
fun EmptyState(text: String, padding: PaddingValues = PaddingValues(), icon: ImageVector = Icons.Outlined.Inbox) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
