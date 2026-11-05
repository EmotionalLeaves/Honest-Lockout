package com.honestlockout.app.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.honestlockout.app.R
import com.honestlockout.app.ui.PrimaryButton
import com.honestlockout.app.ui.Staggered
import com.honestlockout.app.ui.accentGlow
import com.honestlockout.app.ui.theme.Primary

/** First launch: the brand mark, one line on what the app does, and Get started. */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().accentGlow(Primary, alpha = 0.22f, radiusFraction = 1.1f).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Staggered(stepMs = 90) {
            Item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = Modifier.size(96.dp))
                    Spacer(Modifier.height(28.dp))
                }
            }
            Item { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center) }
            Item {
                Text(
                    stringResource(R.string.welcome_tagline), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp, start = 16.dp, end = 16.dp),
                )
            }
            Item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    WelcomePoint(stringResource(R.string.welcome_point_1))
                    WelcomePoint(stringResource(R.string.welcome_point_2))
                    WelcomePoint(stringResource(R.string.welcome_point_3))
                }
            }
        }
        Spacer(Modifier.weight(1.2f))
        PrimaryButton(stringResource(R.string.welcome_start), onClick = onStart)
        Text(
            stringResource(R.string.welcome_private), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun WelcomePoint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 3.dp))
}
