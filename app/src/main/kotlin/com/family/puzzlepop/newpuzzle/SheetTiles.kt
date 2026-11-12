package com.family.puzzlepop.newpuzzle

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One toy colour: [fill] for a lit tile, [deep] for its lip, outline and text on a light tint. */
internal data class Tint(val fill: Color, val deep: Color)

/** The sheet's toy-box palette. Hues follow the icon palette in res/values/icon_colors.xml. */
internal object SheetColors {
    val Tiny = Tint(Color(0xFF2EAD4B), Color(0xFF1E7A35))
    val Easy = Tint(Color(0xFF14A596), Color(0xFF0B6B62))
    val Medium = Tint(Color(0xFF2F7FE6), Color(0xFF1A55A8))
    val Hard = Tint(Color(0xFFF57C1F), Color(0xFFB0520A))
    val Expert = Tint(Color(0xFFE8436A), Color(0xFFA51D45))
    val Custom = Tint(Color(0xFF8257E8), Color(0xFF5530B5))
    val Chunky = Tint(Color(0xFFFF6F61), Color(0xFFC93A33))
    val Classic = Tint(Color(0xFF3AA5F0), Color(0xFF1B6FC0))
    val Wobbly = Tint(Color(0xFF3FBF55), Color(0xFF26853C))
    val Ribbon = Tint(Color(0xFF22B8A8), Color(0xFF127A70))
    val WavyRibbon = Tint(Color(0xFFFFB627), Color(0xFFA86A00))
    val Freeform = Tint(Color(0xFF6C7BF5), Color(0xFF3F4DC4))
    val Hearts = Tint(Color(0xFFFF5FA2), Color(0xFFB82A68))
    val Party = Tint(Color(0xFFA472F2), Color(0xFF6B3FC2))
    val Sun = Tint(Color(0xFFFFB627), Color(0xFFA86A00))

    /** Few pieces to many: the slider track runs through the difficulty colours. */
    val Track = listOf(Tiny.fill, Easy.fill, Medium.fill, Custom.fill, Expert.fill)
    val Start = listOf(Color(0xFF7B4FE0), Color(0xFFB04FD8), Color(0xFFFF6F7D))
}

@Composable
@ReadOnlyComposable
internal fun isDarkSheet(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** A light wash of the tint for unlit tiles (drawn over the card). */
internal fun Tint.soft(dark: Boolean): Color = fill.copy(alpha = if (dark) 0.24f else 0.14f)

/** Text and icon colour on [soft]. */
internal fun Tint.onSoft(dark: Boolean): Color = if (dark) lerp(fill, Color.White, 0.45f) else deep

/**
 * A chunky toy button: a rounded tile sitting on a darker "lip", lit with its colour when [selected],
 * a soft tint when not. It squashes a little while pressed and pops with a spring when it lights up.
 * Scaling happens in the graphics layer, so nothing around it moves.
 */
@Composable
internal fun ToyTile(
    selected: Boolean,
    tint: Tint,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    toggle: Boolean = false,
    role: Role = if (toggle) Role.Switch else Role.RadioButton,
    corner: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
    content: @Composable ColumnScope.(contentColor: Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "press")
    val pop = remember { Animatable(1f) }
    val seen = remember { BooleanArray(1) }
    LaunchedEffect(selected) {
        // No pop when the sheet first appears; only when a tile lights up afterwards.
        if (!seen[0]) { seen[0] = true; return@LaunchedEffect }
        if (selected) {
            pop.snapTo(0.86f)
            pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 500f))
        }
    }
    val dark = isDarkSheet()
    val shape = RoundedCornerShape(corner)
    val contentColor = if (selected) Color.White else tint.onSoft(dark)
    val face: Brush = if (selected) {
        Brush.verticalGradient(listOf(lerp(tint.fill, Color.White, 0.12f), tint.fill, lerp(tint.fill, tint.deep, 0.3f)))
    } else {
        SolidColor(tint.soft(dark))
    }
    val lip = if (selected) tint.deep else tint.fill.copy(alpha = if (dark) 0.4f else 0.26f)
    val action = if (toggle) {
        Modifier.toggleable(selected, interaction, null, role = role, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected, interaction, null, role = role, onClick = onClick)
    }
    Box(
        modifier
            .graphicsLayer {
                val s = pop.value * press.value
                scaleX = s
                scaleY = s
            }
            .then(action)
            .clip(shape)
            .background(lip),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 4.dp)
                .clip(shape)
                .background(face)
                .padding(contentPadding),
        ) { content(contentColor) }
    }
}

/** A soft, tinted, rounded card grouping one part of the sheet, with an optional icon and title. */
@Composable
internal fun SectionCard(
    tint: Tint,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isDarkSheet()
    val base = MaterialTheme.colorScheme.surfaceContainerLowest
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(lerp(base, tint.fill, if (dark) 0.12f else 0.07f))
            .padding(14.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) {
                    Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(26.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = tint.onSoft(dark),
                )
            }
        }
        content()
    }
}

/** A coloured icon on a white disc, so it reads on both a lit tile and a soft one. */
@Composable
internal fun IconBadge(@DrawableRes icon: Int, size: Dp, iconSize: Dp, faded: Boolean = false) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(iconSize).graphicsLayer { alpha = if (faded) 0.45f else 1f },
        )
    }
}
