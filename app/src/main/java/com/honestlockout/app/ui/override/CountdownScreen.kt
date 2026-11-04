package com.honestlockout.app.ui.override

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.honestlockout.app.AppGraph
import com.honestlockout.app.AppLog
import com.honestlockout.app.R
import com.honestlockout.app.data.repo.Eligibility
import com.honestlockout.app.service.OverrideForegroundService
import com.honestlockout.app.ui.Format
import com.honestlockout.app.ui.nav.Routes
import com.honestlockout.app.ui.theme.HugeNumber
import kotlinx.coroutines.delay

/**
 * override_countdown_sec seconds (default 20) with the statement in large text and a Cancel button.
 * There is no way to skip. At 0 the override starts, MainActivity finishes and the app launches.
 */
@Composable
fun CountdownScreen(nav: NavController) {
    val context = LocalContext.current
    val draft = remember { AppGraph.overrideRepo.draft }
    var left by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }

    fun cancel() {
        AppGraph.overrideRepo.draft = null
        nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
    }
    BackHandler { cancel() }

    LaunchedEffect(draft) {
        if (draft == null) { cancel(); return@LaunchedEffect }
        left = AppGraph.settings.snapshot().countdownSec.coerceAtLeast(1)
        while (left > 0) { delay(1_000L); left-- }
        val now = System.currentTimeMillis()
        val e = AppGraph.overrideRepo.eligibility(draft.packageName, draft.templateId, now)
        if (e is Eligibility.Refused) { error = e.message; return@LaunchedEffect }
        runCatching {
            AppGraph.overrideRepo.start(draft, now)
            AppGraph.overrideRepo.draft = null
            OverrideForegroundService.send(context, OverrideForegroundService.ACTION_START)
            context.packageManager.getLaunchIntentForPackage(draft.packageName)?.let { context.startActivity(it) }
            (context as? Activity)?.finish()
        }.onFailure { AppLog.w("HL/Svc", "Override start failed", it); error = it.message }
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (draft == null) return@Column
            Text(if (left >= 0) "$left" else "", style = HugeNumber, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text(draft.statement, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.countdown_app_minutes, Format.appLabel(context, draft.packageName), draft.minutes),
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(40.dp))
            OutlinedButton(onClick = { cancel() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
