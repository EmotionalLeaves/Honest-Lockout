package com.honestlockout.app.ui.blocklist

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.engine.EmergencyAllowlist
import com.honestlockout.app.ui.AppIcon
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.EmptyState
import com.honestlockout.app.ui.nav.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LaunchableApp(val packageName: String, val label: String)

/** All launchable apps (MAIN/LAUNCHER), alphabetical. */
fun launchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** Launchable apps minus the emergency allowlist minus already-blocked apps. Tap = block (ALWAYS) and edit. */
@Composable
fun AppPickerScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blocked by AppGraph.blockRepo.rules().collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val apps by produceState<List<LaunchableApp>?>(initialValue = null) {
        EmergencyAllowlist.refreshDefaults(context)
        value = withContext(Dispatchers.Default) { launchableApps(context) }
    }
    val blockedSet = remember(blocked) { blocked.map { it.packageName }.toSet() }

    AppScaffold(title = stringResource(R.string.apppicker_title), onBack = { nav.popBackStack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.apppicker_search)) },
                singleLine = true,
            )
            val all = apps
            if (all == null) {
                CircularProgressIndicator(Modifier.padding(32.dp))
                return@Column
            }
            val shown = all.filter {
                !EmergencyAllowlist.contains(it.packageName) && it.packageName !in blockedSet &&
                    (query.isBlank() || it.label.contains(query, ignoreCase = true))
            }
            if (shown.isEmpty()) { EmptyState(stringResource(R.string.apppicker_empty)); return@Column }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.packageName }) { app ->
                    ListItem(
                        modifier = Modifier.clickable {
                            scope.launch {
                                AppGraph.blockRepo.add(app.packageName)
                                nav.navigate(Routes.blockRule(app.packageName)) { popUpTo(Routes.BLOCKLIST) }
                            }
                        },
                        leadingContent = { AppIcon(app.packageName) },
                        headlineContent = { Text(app.label) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
