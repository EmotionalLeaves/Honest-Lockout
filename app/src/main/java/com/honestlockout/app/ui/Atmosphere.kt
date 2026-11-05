package com.honestlockout.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/*
 * Depth and motion. Everything here is subtle on purpose: a soft accent glow where the eye lands,
 * a one-pixel light edge on cards, a slight press-scale on buttons, and content that settles in
 * with a short stagger rather than popping.
 */

/** A soft radial accent glow anchored near the top-left; draws behind content. */
fun Modifier.accentGlow(color: Color, alpha: Float = 0.14f, radiusFraction: Float = 0.9f): Modifier = drawBehind {
    val r = size.width * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = Offset(size.width * 0.15f, -r * 0.35f), radius = r),
        radius = r, center = Offset(size.width * 0.15f, -r * 0.35f),
    )
}

/** Hairline border with a slightly brighter top edge, so cards read as raised glass. */
@Composable
fun Modifier.cardEdge(shape: Shape = RoundedCornerShape(20.dp)): Modifier {
    val outline = MaterialTheme.colorScheme.outline
    val top = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    return this.border(1.dp, Brush.verticalGradient(0f to top, 0.25f to outline, 1f to outline), shape)
}

/** The same edge as [cardEdge], as a BorderStroke for Card(border = …). */
@Composable
fun cardBorder(): BorderStroke {
    val outline = MaterialTheme.colorScheme.outline
    val top = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    return BorderStroke(1.dp, Brush.verticalGradient(0f to top, 0.25f to outline, 1f to outline))
}

/** Press feedback: scales to 97 % while pressed. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")
    return this.scale(scale)
}

/** Children appear one after another, fading up, the first time this is composed. */
@Composable
fun Staggered(modifier: Modifier = Modifier, stepMs: Int = 50, content: @Composable StaggerScope.() -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(modifier) { StaggerScope(shown, stepMs, this).content() }
}

class StaggerScope(private val shown: Boolean, private val stepMs: Int, private val column: ColumnScope) {
    private var index = 0

    @Composable
    fun Item(content: @Composable () -> Unit) {
        val i = index++
        with(column) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(260, delayMillis = i * stepMs, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(320, delayMillis = i * stepMs, easing = FastOutSlowInEasing)) { it / 10 },
            ) { content() }
        }
    }
}

/** A glowing status orb: a filled dot with a soft halo, for "on / off" states. */
@Composable
fun StatusOrb(color: Color, size: Int = 12) {
    Box(Modifier.size((size * 3).dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size((size * 3).dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent))),
        )
        Box(Modifier.size(size.dp).clip(CircleShape).background(color))
    }
}
