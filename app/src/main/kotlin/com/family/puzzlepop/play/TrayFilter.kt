package com.family.puzzlepop.play

import com.family.puzzlepop.render.ColorGroup

/** Which tray pieces are shown. */
sealed interface TrayFilter {
    data object All : TrayFilter
    data object Edges : TrayFilter
    data object Inside : TrayFilter
    data class Color(val group: ColorGroup) : TrayFilter

    /** True when a piece with these traits is shown under this filter. */
    fun accepts(isEdge: Boolean, group: ColorGroup): Boolean = when (this) {
        All -> true
        Edges -> isEdge
        Inside -> !isEdge
        is Color -> this.group == group
    }
}
