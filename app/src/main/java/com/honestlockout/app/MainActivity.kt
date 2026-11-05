package com.honestlockout.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.honestlockout.app.ui.nav.AppNavHost
import com.honestlockout.app.ui.theme.HonestLockoutTheme
import com.honestlockout.app.work.PendingChangeWorker
import kotlinx.coroutines.launch

/** Single Activity hosting the Compose NavHost. Services open it with a route extra. */
class MainActivity : ComponentActivity() {

    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugSeed.maybeSeed(intent) // no-op in release builds (src/release)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Only on a fresh start: after rotation the route was already consumed.
        if (savedInstanceState == null) pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            HonestLockoutTheme {
                AppNavHost(
                    pendingRoute = pendingRoute.value,
                    onRouteConsumed = { pendingRoute.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching { PendingChangeWorker.applyDue(System.currentTimeMillis()) }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ROUTE_EXTEND = "override/extend"

        fun overrideRoute(packageName: String) = "override/$packageName"

        fun routeIntent(context: Context, route: String?): Intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (route != null) putExtra(EXTRA_ROUTE, route)
            }
    }
}
