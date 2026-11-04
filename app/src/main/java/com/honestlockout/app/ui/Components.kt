package com.honestlockout.app.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = titleModifier) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                },
            )
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
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
        IconButton(enabled = enabled && idx > 0, onClick = { onChange(options[idx - 1]) }) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_decrease))
        }
        Text(format(value), Modifier.width(88.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(enabled = enabled && idx < options.lastIndex, onClick = { onChange(options[idx + 1]) }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_increase))
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
fun EmptyState(text: String, padding: PaddingValues = PaddingValues()) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
