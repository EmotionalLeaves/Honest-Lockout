package com.honestlockout.app.ui.override

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.R
import com.honestlockout.app.ui.AppScaffold
import com.honestlockout.app.ui.nav.Routes

/**
 * Notification "Extend": reopens Declare with the same app and purpose, an empty statement and
 * minutes limited to maxMinutes - minutes used. Refused on the third Extend in a row.
 */
@Composable
fun ExtendEntry(nav: NavController) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val active = AppGraph.overrideRepo.getActive()
        when {
            active == null -> message = context.getString(R.string.extend_none)
            !AppGraph.overrideRepo.canExtend() -> message = context.getString(R.string.extend_refused)
            else -> nav.navigate(Routes.declare(active.purposeId, active.packageName, extend = true)) {
                popUpTo(Routes.OVERRIDE_EXTEND) { inclusive = true }
            }
        }
    }
    AppScaffold(title = stringResource(R.string.notif_extend), onBack = { nav.popBackStack() }) { padding ->
        val m = message ?: return@AppScaffold
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(m)
            Button(onClick = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }) {
                Text(stringResource(R.string.action_ok))
            }
        }
    }
}
