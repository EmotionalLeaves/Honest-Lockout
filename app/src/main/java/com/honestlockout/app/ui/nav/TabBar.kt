package com.honestlockout.app.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.honestlockout.app.R

data class Tab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

val TABS = listOf(
    Tab(Routes.HOME, R.string.tab_today, Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
    Tab(Routes.BLOCKLIST, R.string.tab_apps, Icons.Outlined.Apps, Icons.Filled.Apps),
    Tab(Routes.HISTORY, R.string.tab_history, Icons.Outlined.History, Icons.Filled.History),
)

/** A quiet tab bar: hairline on top, filled icon for the current tab, no pill indicator. */
@Composable
fun TabBar(current: String?, onSelect: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TABS.forEach { tab ->
                val selected = current == tab.route
                val tint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, label = "tab",
                )
                Column(
                    Modifier.weight(1f).height(60.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (!selected) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(tab.route) }
                        }
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        }
    }
}
