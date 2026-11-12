package com.family.puzzlepop.play

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.family.puzzlepop.render.ColorGroup
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.puzzlepop.R
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The tray's thickness across the screen edge it sits on. */
const val TRAY_THICKNESS_DP = 132f

/** A labelled tray button (icon over a short label): at least 48 dp each way. */
const val TRAY_BUTTON_H_DP = 50f
const val TRAY_BUTTON_W_LANDSCAPE_DP = 60f
const val TRAY_BUTTON_W_PORTRAIT_DP = 52f
const val TRAY_BUTTON_GAP_DP = 2f

/**
 * The header strip (labelled filter, sort and hide buttons, then the colour dots while they are open): on top
 * in landscape (three rows of two buttons), on the left in portrait (two rows of three). Its length along the
 * tray grows by [TRAY_DOTS_DP] while the colour dots show.
 */
const val TRAY_HEADER_LANDSCAPE_DP = 3 * TRAY_BUTTON_H_DP + 2 * TRAY_BUTTON_GAP_DP + 10f
const val TRAY_HEADER_PORTRAIT_DP = 3 * TRAY_BUTTON_W_PORTRAIT_DP + 2 * TRAY_BUTTON_GAP_DP + 10f
const val TRAY_DOTS_DP = 2 * 32f + 4f


/** The collapsed tray: a handle this thick (and [TRAY_HANDLE_LENGTH_DP] long) with the tray icon and count. */
const val TRAY_HANDLE_DP = 40f
const val TRAY_HANDLE_LENGTH_DP = 96f

private const val LANE_PAD_DP = 6f
private const val PANEL_INSET_DP = 4f
private const val PANEL_RADIUS_DP = 18f
private const val SLOT_INSET_DP = 3f
private const val SLOT_RADIUS_DP = 10f

/** Fraction of a slot left empty on each side of the piece drawn in it. */
private const val SLOT_MARGIN = 0.10f

/**
 * Where the piece tray and its slots are on screen (canvas pixels). The tray never moves with the camera.
 *
 * In landscape the tray is a column on the right ([vertical]): the header on top, then [columns] lanes of
 * square slots that scroll vertically, filled row by row. In portrait it is a strip along the bottom: the
 * header on the left, then [columns] rows of slots that scroll horizontally, filled column by column.
 * `scroll` is how far the slots have scrolled along the tray, in pixels (0 = the first slot is showing).
 */
class TrayLayout(
    val trayRect: Rect,
    val slotSize: Float,
    val columns: Int,
    /** Header thickness along the scroll axis (px). */
    val header: Float = 0f,
    /** Where the collapsed handle sits. */
    val handleRect: Rect = trayRect,
    /** Rounded panel corner radius and inset (px), for drawing. */
    val panelInset: Float = 0f,
    val panelRadius: Float = 0f,
) {
    val vertical: Boolean = trayRect.height >= trayRect.width

    /** The header strip (buttons are laid over it). */
    val headerRect: Rect =
        if (vertical) Rect(trayRect.left, trayRect.top, trayRect.right, trayRect.top + header)
        else Rect(trayRect.left, trayRect.top, trayRect.left + header, trayRect.bottom)

    /** The visible scrolling area: the tray minus its header. Slots are clipped to it. */
    val slotsViewport: Rect =
        if (vertical) Rect(trayRect.left, trayRect.top + header, trayRect.right, trayRect.bottom)
        else Rect(trayRect.left + header, trayRect.top, trayRect.right, trayRect.bottom)

    /** Space between the tray's long edges and the lanes (the lanes are centred across the tray). */
    private val lanePad: Float =
        ((if (vertical) trayRect.width else trayRect.height) - columns * slotSize) / 2

    private val visibleLength: Float = if (vertical) slotsViewport.height else slotsViewport.width

    /** The square slot for display position [index]. Not clipped: it may lie outside [slotsViewport]. */
    fun slotRect(index: Int, scroll: Float): Rect {
        val lane = index % columns
        val pos = index / columns
        return if (vertical) {
            val l = trayRect.left + lanePad + lane * slotSize
            val t = slotsViewport.top + lanePad + pos * slotSize - scroll
            Rect(l, t, l + slotSize, t + slotSize)
        } else {
            val l = slotsViewport.left + lanePad + pos * slotSize - scroll
            val t = trayRect.top + lanePad + lane * slotSize
            Rect(l, t, l + slotSize, t + slotSize)
        }
    }

    /**
     * The display position of the slot under [p], or null when [p] is beside the lanes or before the first
     * slot. It does not check the visible area or how many pieces there are: callers check [slotsViewport]
     * and the count.
     */
    fun indexAt(p: Offset, scroll: Float): Int? {
        val across = if (vertical) p.x - (trayRect.left + lanePad) else p.y - (trayRect.top + lanePad)
        val along = if (vertical) p.y - (slotsViewport.top + lanePad) + scroll else p.x - (slotsViewport.left + lanePad) + scroll
        if (across < 0f || across >= columns * slotSize || along < 0f) return null
        val lane = floor(across / slotSize).toInt()
        val pos = floor(along / slotSize).toInt()
        return pos * columns + lane
    }

    /** The largest useful scroll for [count] slots: the last one then sits just inside the visible area. */
    fun maxScroll(count: Int): Float {
        if (count <= 0) return 0f
        val lines = ceil(count / columns.toFloat())
        return max(0f, 2 * lanePad + lines * slotSize - visibleLength)
    }

    companion object {
        /**
         * The tray for a play area of [size] px: on the right in [landscape], else along the bottom. It keeps
         * clear of the system navigation bar ([bottomInset] / [rightInset] px, e.g. the gesture handle).
         * [colorsOpen]: the colour dots show in the header, which is then longer.
         */
        fun forViewport(
            size: Size,
            landscape: Boolean,
            density: Float,
            bottomInset: Float = 0f,
            rightInset: Float = 0f,
            colorsOpen: Boolean = false,
        ): TrayLayout {
            val dots = if (colorsOpen) TRAY_DOTS_DP else 0f
            val right = size.width - rightInset
            val bottom = size.height - bottomInset
            val t = TRAY_THICKNESS_DP * density
            val columns = 2
            val slot = (t - 2 * LANE_PAD_DP * density) / columns
            val h = TRAY_HANDLE_DP * density
            val hl = TRAY_HANDLE_LENGTH_DP * density
            val gap = 8f * density
            return if (landscape) {
                TrayLayout(
                    trayRect = Rect(right - t, 0f, right, bottom),
                    slotSize = slot,
                    columns = columns,
                    header = (TRAY_HEADER_LANDSCAPE_DP + dots) * density,
                    handleRect = Rect(right - h, gap, right, gap + hl),
                    panelInset = PANEL_INSET_DP * density,
                    panelRadius = PANEL_RADIUS_DP * density,
                )
            } else {
                TrayLayout(
                    trayRect = Rect(0f, bottom - t, right, bottom),
                    slotSize = slot,
                    columns = columns,
                    header = (TRAY_HEADER_PORTRAIT_DP + dots) * density,
                    handleRect = Rect(gap, bottom - h, gap + hl, bottom),
                    panelInset = PANEL_INSET_DP * density,
                    panelRadius = PANEL_RADIUS_DP * density,
                )
            }
        }
    }
}

/**
 * Screen px per world unit of a piece (bitmap [drawW] x [drawH] world units, turned [rotDeg]) drawn in a
 * [slotSize] slot: its turned box fits the slot with [SLOT_MARGIN] on each side. The piece is centred in the slot.
 */
fun slotPieceScale(slotSize: Float, drawW: Float, drawH: Float, rotDeg: Float): Float {
    val inner = slotSize * (1f - 2 * SLOT_MARGIN)
    var bw = drawW
    var bh = drawH
    if (rotDeg != 0f) {
        val rad = Math.toRadians(rotDeg.toDouble())
        val c = abs(cos(rad)).toFloat()
        val sn = abs(sin(rad)).toFloat()
        bw = drawW * c + drawH * sn
        bh = drawW * sn + drawH * c
    }
    return min(inner / bw, inner / bh)
}

/** Paints and scratch rects for [drawTray], made once per canvas. */
class TrayPaints(density: Float) {
    val panel = Paint(Paint.ANTI_ALIAS_FLAG)
    val slot = Paint(Paint.ANTI_ALIAS_FLAG)
    val hover = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    val piece = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val slotInset = SLOT_INSET_DP * density
    val slotRadius = SLOT_RADIUS_DP * density
    val rect = RectF()
}

/**
 * Draws the expanded tray in screen space: the rounded panel, then the visible slots holding
 * `shown[0 until count]` (piece ids), each piece scaled to fit its slot with a margin, keeping its shape.
 * [hover] outlines the panel while a piece that can be stored is held over it. A turned piece is drawn at its
 * angle ([game]'s rotation), scaled so its turned box fits the slot.
 */
fun drawTray(
    nc: Canvas,
    tl: TrayLayout,
    scroll: Float,
    shown: IntArray,
    count: Int,
    bitmaps: Array<Bitmap>,
    drawW: FloatArray,
    drawH: FloatArray,
    p: TrayPaints,
    hover: Boolean,
    game: com.family.puzzlepop.engine.game.PuzzleGame,
) {
    val r = p.rect
    val tr = tl.trayRect
    val inset = tl.panelInset
    r.set(tr.left + inset, tr.top + inset, tr.right - inset, tr.bottom - inset)
    nc.drawRoundRect(r, tl.panelRadius, tl.panelRadius, p.panel)
    if (hover) nc.drawRoundRect(r, tl.panelRadius, tl.panelRadius, p.hover)
    if (count == 0) return

    val vp = tl.slotsViewport
    nc.save()
    nc.clipRect(
        max(vp.left, tr.left + inset), max(vp.top, tr.top + inset),
        min(vp.right, tr.right - inset), min(vp.bottom, tr.bottom - inset),
    )
    // Only the slots that can be visible.
    val s = tl.slotSize
    val firstLine = max(0, floor(scroll / s).toInt() - 1)
    val visibleLines = ceil((if (tl.vertical) vp.height else vp.width) / s).toInt() + 2
    val from = firstLine * tl.columns
    val to = min(count, (firstLine + visibleLines) * tl.columns)
    for (i in from until to) {
        val slot = tl.slotRect(i, scroll)
        r.set(slot.left + p.slotInset, slot.top + p.slotInset, slot.right - p.slotInset, slot.bottom - p.slotInset)
        nc.drawRoundRect(r, p.slotRadius, p.slotRadius, p.slot)
        val id = shown[i]
        val rot = game.rotation(id)
        val k = slotPieceScale(s, drawW[id], drawH[id], rot)
        val w = drawW[id] * k
        val h = drawH[id] * k
        val cx = slot.left + s / 2
        val cy = slot.top + s / 2
        r.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        if (rot != 0f) {
            nc.save()
            nc.rotate(rot, cx, cy)
            nc.drawBitmap(bitmaps[id], null, r, p.piece)
            nc.restore()
        } else {
            nc.drawBitmap(bitmaps[id], null, r, p.piece)
        }
    }
    nc.restore()
}

/**
 * The tray's Compose controls, laid over the canvas: the header (labelled filter, sort and hide buttons, and
 * the colour dots under Colours) when open, or just the handle (tray icon and count) when collapsed. Each value is read in here, so
 * a change recomposes only these controls, never the canvas.
 */
@Composable
fun TrayControls(
    layout: TrayLayout,
    collapsed: () -> Boolean,
    hover: () -> Boolean,
    count: () -> Int,
    filter: StateFlow<TrayFilter>,
    onFilter: (TrayFilter) -> Unit,
    sortByColor: StateFlow<Boolean>,
    onSortByColor: (Boolean) -> Unit,
    groupMask: StateFlow<Int>,
    plusOne: StateFlow<Int>,
    onCollapsed: (Boolean) -> Unit,
    /** Whether the colour dots show (the header is longer then; see [TrayLayout.forViewport]). */
    colorsOpen: () -> Boolean,
    onColorsOpen: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    if (collapsed()) {
        val n = count()
        val a11y = stringResource(R.string.tray_open_a11y, n)
        val shape = if (layout.vertical) RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
        else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        Surface(
            onClick = { onCollapsed(false) },
            shape = shape,
            color = colors.surfaceContainer,
            shadowElevation = 3.dp,
            border = if (hover()) BorderStroke(3.dp, colors.primary) else null,
            modifier = Modifier.placeAt(layout.handleRect, density).semantics { contentDescription = a11y },
        ) {
            val icon = @Composable {
                Icon(painterResource(R.drawable.ic_tray), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(26.dp))
            }
            val label = @Composable { Text("$n", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            if (layout.vertical) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { icon(); label() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { icon(); label() }
            }
        }
    } else {
        val active by filter.collectAsState()
        val sorted by sortByColor.collectAsState()
        val mask by groupMask.collectAsState()
        val flash by plusOne.collectAsState()
        val dotsOpen = colorsOpen()
        // "+1" shows on the active button for 1.2 s after a stored piece the filter hides.
        var showPlus by remember { mutableStateOf(false) }
        // Only count bumps that happen while the tray is open, so reopening it never replays an old one.
        val flashOnOpen = remember { flash }
        // A colour with no pieces left in the tray falls back to showing everything; a picked colour keeps the dots open.
        LaunchedEffect(mask, active) {
            val a = active
            if (a is TrayFilter.Color && mask and (1 shl a.group.ordinal) == 0) onFilter(TrayFilter.All)
            else if (a is TrayFilter.Color) onColorsOpen(true)
        }
        LaunchedEffect(flash) {
            if (flash > flashOnOpen) {
                showPlus = true
                delay(PLUS_ONE_MS)
                showPlus = false
            }
        }
        val activeButton = when (active) {
            TrayFilter.All -> 0
            TrayFilter.Edges -> 1
            TrayFilter.Inside -> 2
            else -> 3
        }
        val bw = if (layout.vertical) TRAY_BUTTON_W_LANDSCAPE_DP.dp else TRAY_BUTTON_W_PORTRAIT_DP.dp
        val filters = listOf(
            Triple(TrayFilter.All, R.drawable.ic_tray, R.string.tray_label_all to R.string.tray_filter_all),
            Triple(TrayFilter.Edges, R.drawable.ic_edges, R.string.tray_label_edges to R.string.tray_filter_edges),
            Triple(TrayFilter.Inside, R.drawable.ic_inside, R.string.tray_label_inside to R.string.tray_filter_inside),
        )
        val buttons: List<@Composable () -> Unit> = listOf(
            *filters.mapIndexed { i, f ->
                @Composable {
                    TrayButton(
                        f.second, stringResource(f.third.first), stringResource(f.third.second), bw,
                        selected = activeButton == i, plus = showPlus && activeButton == i,
                    ) {
                        onColorsOpen(false)
                        onFilter(f.first)
                    }
                }
            }.toTypedArray(),
            @Composable {
                TrayButton(
                    R.drawable.ic_colors, stringResource(R.string.tray_label_colors), stringResource(R.string.tray_filter_colors), bw,
                    selected = activeButton == 3 || dotsOpen, plus = showPlus && activeButton == 3,
                ) {
                    // Closing the dots while a colour is picked goes back to everything.
                    if (dotsOpen && active is TrayFilter.Color) onFilter(TrayFilter.All)
                    onColorsOpen(!dotsOpen)
                }
            },
            @Composable {
                TrayButton(
                    R.drawable.ic_sort_color, stringResource(R.string.tray_label_sort), stringResource(R.string.tray_sort_color), bw,
                    selected = sorted, plus = false,
                ) { onSortByColor(!sorted) }
            },
            @Composable {
                // The icon points right (the landscape tray folds away to the right); turn it down in portrait.
                TrayButton(
                    R.drawable.ic_tray_collapse, stringResource(R.string.tray_label_hide), stringResource(R.string.tray_collapse), bw,
                    selected = false, plus = false, iconTurn = if (layout.vertical) 0f else 90f,
                ) { onCollapsed(true) }
            },
        )
        val dots = @Composable {
            if (dotsOpen) {
                // Touch cells are 32 dp, so four fit across the 132 dp tray: the dots wrap into groups of four.
                val groups = ColorGroup.entries.filter { mask and (1 shl it.ordinal) != 0 }.chunked(4)
                val dot = @Composable { g: ColorGroup ->
                    val selected = (active as? TrayFilter.Color)?.group == g
                    ColorDot(g, selected, stringResource(colorGroupName(g))) { onFilter(TrayFilter.Color(g)) }
                }
                if (layout.vertical) {
                    Column { for (grp in groups) Row { for (g in grp) dot(g) } }
                } else {
                    Row { for (grp in groups) Column { for (g in grp) dot(g) } }
                }
            }
        }
        val gap = TRAY_BUTTON_GAP_DP.dp
        Box(Modifier.placeAt(layout.headerRect, density), contentAlignment = Alignment.Center) {
            if (layout.vertical) {
                // Landscape: three rows of two, then the dots.
                Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                    for (row in buttons.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(gap)) { for (b in row) b() }
                    dots()
                }
            } else {
                // Portrait: two rows of three, then the dots.
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        for (row in buttons.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(gap)) { for (b in row) b() }
                    }
                    dots()
                }
            }
        }
    }
}

private const val DOT_DP = 32 // touch cell; the dot itself is drawn smaller
private const val PLUS_ONE_MS = 1200L

/** A tray button: a colourful icon over a short label, highlighted while [selected]. [description] is for TalkBack. */
@Composable
private fun TrayButton(
    icon: Int,
    label: String,
    description: String,
    width: Dp,
    selected: Boolean,
    plus: Boolean,
    iconTurn: Float = 0f,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        checked = selected,
        onCheckedChange = { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.primaryContainer else Color.Transparent,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier.size(width, TRAY_BUTTON_H_DP.dp).semantics { contentDescription = description },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                if (plus) {
                    Text("+1", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = colors.primary)
                } else {
                    Icon(
                        painterResource(icon), contentDescription = null, tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp).rotate(iconTurn),
                    )
                }
            }
            Text(
                label,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColorDot(group: ColorGroup, selected: Boolean, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(DOT_DP.dp).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(if (selected) 14.dp else 11.dp)
                .background(dotColor(group), CircleShape)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline, CircleShape),
        )
    }
}

private fun colorGroupName(g: ColorGroup): Int = when (g) {
    ColorGroup.RED -> R.string.color_red
    ColorGroup.ORANGE -> R.string.color_orange
    ColorGroup.YELLOW -> R.string.color_yellow
    ColorGroup.GREEN -> R.string.color_green
    ColorGroup.BLUE -> R.string.color_blue
    ColorGroup.PURPLE -> R.string.color_purple
    ColorGroup.LIGHT -> R.string.color_light
    ColorGroup.DARK -> R.string.color_dark
}

private fun dotColor(g: ColorGroup): Color = when (g) {
    ColorGroup.RED -> Color(0xFFE53935)
    ColorGroup.ORANGE -> Color(0xFFFB8C00)
    ColorGroup.YELLOW -> Color(0xFFFDD835)
    ColorGroup.GREEN -> Color(0xFF43A047)
    ColorGroup.BLUE -> Color(0xFF1E88E5)
    ColorGroup.PURPLE -> Color(0xFF8E24AA)
    ColorGroup.LIGHT -> Color(0xFFF5F5F5)
    ColorGroup.DARK -> Color(0xFF3A3A3A)
}

/** Places a composable exactly over screen rect [r] (canvas pixels). */
private fun Modifier.placeAt(r: Rect, density: Density): Modifier = with(density) {
    offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }.size(r.width.toDp(), r.height.toDp())
}
