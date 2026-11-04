package com.honestlockout.app.ui.nav

import android.net.Uri

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.honestlockout.app.AppGraph
import com.honestlockout.app.ui.blocklist.AppPickerScreen
import com.honestlockout.app.ui.blocklist.BlockListScreen
import com.honestlockout.app.ui.blocklist.BlockRuleEditorScreen
import com.honestlockout.app.ui.blocklist.ScheduleEditorScreen
import com.honestlockout.app.ui.history.HistoryScreen
import com.honestlockout.app.ui.home.HomeScreen
import com.honestlockout.app.ui.override.CountdownScreen
import com.honestlockout.app.ui.override.DeclareScreen
import com.honestlockout.app.ui.override.ExtendEntry
import com.honestlockout.app.ui.override.PurposePickerScreen
import com.honestlockout.app.ui.settings.BreakGlassScreen
import com.honestlockout.app.ui.settings.InspectorScreen
import com.honestlockout.app.ui.settings.PendingChangesScreen
import com.honestlockout.app.ui.settings.SettingsScreen
import com.honestlockout.app.ui.settings.TemplateEditorScreen
import com.honestlockout.app.ui.setup.SetupWizardScreen

/** Route names. Arguments use {name}; helpers build concrete routes. */
object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
    const val BLOCKLIST = "blocklist"
    const val APPPICKER = "apppicker"
    const val BLOCKRULE = "blockrule/{pkg}?new={new}"
    const val SCHEDULE = "schedule/{id}"
    const val OVERRIDE_PICK = "override/pick?pkg={pkg}"
    const val OVERRIDE_DECLARE = "override/declare/{templateId}?pkg={pkg}&extend={extend}&stmt={stmt}"
    const val OVERRIDE_EXTEND = "override/extend"
    const val OVERRIDE_COUNTDOWN = "override/countdown"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PENDING = "pending"
    const val TEMPLATE = "template/{id}"
    const val INSPECTOR = "inspector"
    const val BREAKGLASS = "breakglass"

    fun blockRule(pkg: String, isNew: Boolean = false) = "blockrule/$pkg?new=$isNew"
    fun schedule(id: Long?) = "schedule/${id ?: "new"}"
    fun pick(pkg: String? = null) = if (pkg == null) "override/pick" else "override/pick?pkg=$pkg"
    fun declare(templateId: Long, pkg: String?, extend: Boolean = false, statement: String? = null) =
        "override/declare/$templateId?pkg=${pkg.orEmpty()}&extend=$extend&stmt=${Uri.encode(statement.orEmpty())}"
    fun template(id: Long) = "template/$id"

    /** The lock screen opens "override/<package>"; it lands on the purpose picker for that app. */
    fun fromExternal(route: String): String = when {
        route == OVERRIDE_EXTEND -> route
        route.startsWith("override/") && !route.startsWith("override/pick") && !route.startsWith("override/declare") &&
            route != OVERRIDE_COUNTDOWN -> pick(route.removePrefix("override/"))
        else -> route
    }
}

@Composable
fun AppNavHost(pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val settings by AppGraph.settings.data.collectAsState(initial = null)
    val s = settings ?: run { Box(Modifier.fillMaxSize()); return }
    val nav = rememberNavController()
    val start = if (s.setupComplete) Routes.HOME else Routes.SETUP

    NavHost(
        navController = nav, startDestination = start,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 } },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 6 } },
    ) {
        composable(Routes.SETUP) {
            SetupWizardScreen(onFinished = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.SETUP) { inclusive = true } }
            })
        }
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.BLOCKLIST) { BlockListScreen(nav) }
        composable(Routes.APPPICKER) { AppPickerScreen(nav) }
        composable(
            Routes.BLOCKRULE,
            arguments = listOf(
                navArgument("pkg") { type = NavType.StringType },
                navArgument("new") { type = NavType.BoolType; defaultValue = false },
            ),
        ) {
            BlockRuleEditorScreen(nav, it.arguments?.getString("pkg").orEmpty(), it.arguments?.getBoolean("new") ?: false)
        }
        composable(Routes.SCHEDULE, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            ScheduleEditorScreen(nav, it.arguments?.getString("id")?.toLongOrNull())
        }
        composable(
            Routes.OVERRIDE_PICK,
            arguments = listOf(navArgument("pkg") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { PurposePickerScreen(nav, it.arguments?.getString("pkg")?.takeIf { p -> p.isNotEmpty() }) }
        composable(
            Routes.OVERRIDE_DECLARE,
            arguments = listOf(
                navArgument("templateId") { type = NavType.LongType },
                navArgument("pkg") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("extend") { type = NavType.BoolType; defaultValue = false },
                navArgument("stmt") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            DeclareScreen(
                nav,
                templateId = it.arguments?.getLong("templateId") ?: 0L,
                presetPackage = it.arguments?.getString("pkg")?.takeIf { p -> p.isNotEmpty() },
                extend = it.arguments?.getBoolean("extend") ?: false,
                presetStatement = it.arguments?.getString("stmt")?.takeIf { p -> p.isNotEmpty() },
            )
        }
        composable(Routes.OVERRIDE_EXTEND) { ExtendEntry(nav) }
        composable(Routes.OVERRIDE_COUNTDOWN) { CountdownScreen(nav) }
        composable(Routes.HISTORY) { HistoryScreen(nav) }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(Routes.PENDING) { PendingChangesScreen(nav) }
        composable(Routes.TEMPLATE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            TemplateEditorScreen(nav, it.arguments?.getLong("id") ?: 0L)
        }
        composable(Routes.INSPECTOR) { InspectorScreen(nav) }
        composable(Routes.BREAKGLASS) { BreakGlassScreen(nav) }
    }

    LaunchedEffect(pendingRoute, s.setupComplete) {
        val route = pendingRoute ?: return@LaunchedEffect
        if (s.setupComplete) navigateExternal(nav, Routes.fromExternal(route))
        onRouteConsumed()
    }
}

private fun navigateExternal(nav: NavHostController, route: String) {
    runCatching {
        nav.navigate(route) { popUpTo(Routes.HOME); launchSingleTop = true }
    }
}
