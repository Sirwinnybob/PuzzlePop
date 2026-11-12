package com.family.puzzlepop.newpuzzle

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.family.puzzlepop.R
import com.family.puzzlepop.engine.PieceStyle

/** The sample-piece icon for a piece style. */
@DrawableRes
fun styleIcon(style: PieceStyle): Int = when (style) {
    PieceStyle.CHUNKY -> R.drawable.ic_style_chunky
    PieceStyle.CLASSIC -> R.drawable.ic_style_classic
    PieceStyle.WOBBLY -> R.drawable.ic_style_wobbly
    PieceStyle.RIBBON -> R.drawable.ic_style_ribbon
    PieceStyle.WAVY_RIBBON -> R.drawable.ic_style_wavy_ribbon
    PieceStyle.FREEFORM -> R.drawable.ic_style_freeform
    PieceStyle.HEARTS -> R.drawable.ic_style_hearts
    PieceStyle.PARTY -> R.drawable.ic_style_party
}

/** The display name of a piece style. */
@StringRes
fun styleName(style: PieceStyle): Int = when (style) {
    PieceStyle.CHUNKY -> R.string.style_chunky
    PieceStyle.CLASSIC -> R.string.style_classic
    PieceStyle.WOBBLY -> R.string.style_wobbly
    PieceStyle.RIBBON -> R.string.style_ribbon
    PieceStyle.WAVY_RIBBON -> R.string.style_wavy_ribbon
    PieceStyle.FREEFORM -> R.string.style_freeform
    PieceStyle.HEARTS -> R.string.style_hearts
    PieceStyle.PARTY -> R.string.style_party
}

/** The one-line hint under a style's name. */
@StringRes
fun styleHint(style: PieceStyle): Int = when (style) {
    PieceStyle.CHUNKY -> R.string.style_chunky_hint
    PieceStyle.CLASSIC -> R.string.style_classic_hint
    PieceStyle.WOBBLY -> R.string.style_wobbly_hint
    PieceStyle.RIBBON -> R.string.style_ribbon_hint
    PieceStyle.WAVY_RIBBON -> R.string.style_wavy_ribbon_hint
    PieceStyle.FREEFORM -> R.string.style_freeform_hint
    PieceStyle.HEARTS -> R.string.style_hearts_hint
    PieceStyle.PARTY -> R.string.style_party_hint
}
