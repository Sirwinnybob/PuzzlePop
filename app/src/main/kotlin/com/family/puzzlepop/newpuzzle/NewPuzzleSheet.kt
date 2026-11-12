package com.family.puzzlepop.newpuzzle

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.puzzlepop.R
import com.family.puzzlepop.data.PhotoEntity
import com.family.puzzlepop.data.decodeSampled
import com.family.puzzlepop.engine.GridSizer
import com.family.puzzlepop.engine.PieceStyle
import com.family.puzzlepop.engine.options.Ghost
import com.family.puzzlepop.engine.options.PeekMode
import com.family.puzzlepop.engine.options.PlayOptions
import com.family.puzzlepop.engine.options.Preset
import com.family.puzzlepop.engine.options.Presets
import com.family.puzzlepop.engine.options.RotationMode
import com.family.puzzlepop.engine.options.Snap
import com.family.puzzlepop.library.ThumbImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPuzzleSheet(
    photo: PhotoEntity,
    photoFile: File,
    thumbFile: File,
    vm: NewPuzzleViewModel,
    onDismiss: () -> Unit,
) {
    val creating by vm.creating.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    var stepIndex by rememberSaveable { mutableIntStateOf(PieceSteps.indexOf(PieceSteps.DEFAULT_COUNT)) }
    var style by rememberSaveable { mutableStateOf(PieceStyle.CHUNKY) }
    // Which tile is lit: a preset, or null for Custom. The options always hold the values that will be used.
    var preset by rememberSaveable { mutableStateOf<Preset?>(Preset.EASY) }
    var options by rememberSaveable(stateSaver = OptionsSaver) { mutableStateOf(Presets.options(Preset.EASY)) }
    var restored by rememberSaveable { mutableStateOf(false) }
    // Set as soon as the grown-up changes anything. The last-used choice loads in the background; if it
    // arrives after a touch it must not overwrite what they just picked (it used to snap the slider back).
    var touched by rememberSaveable { mutableStateOf(false) }
    // The Custom options panel opens only when the Custom tile is tapped. Moving the slider or picking a shape
    // just lights the Custom tile: opening the panel then would push the slider away from the finger mid-drag.
    var customOpen by rememberSaveable { mutableStateOf(false) }
    fun choose(p: Preset) {
        preset = p
        customOpen = false
        options = Presets.options(p)
        style = Presets.style(p)
        stepIndex = PieceSteps.indexOf(Presets.targetCount(p)).coerceAtLeast(0)
    }
    fun goCustom() {
        touched = true
        if (preset != null) {
            preset = null
            options = options.copy(preset = null)
        }
    }
    LaunchedEffect(Unit) {
        if (restored || touched) return@LaunchedEffect
        val last = vm.lastPreset()
        val saved = if (last == "CUSTOM") vm.lastCustom() else null
        if (touched) return@LaunchedEffect
        restored = true
        if (last == "CUSTOM") {
            preset = null
            options = (saved?.options ?: options).copy(preset = null)
            saved?.count?.let { c -> PieceSteps.indexOf(c).takeIf { it >= 0 }?.let { stepIndex = it } }
            saved?.style?.let { style = it }
        } else {
            Preset.entries.firstOrNull { it.name == last }?.let { choose(it) } ?: choose(Preset.EASY)
        }
    }
    val count = PieceSteps.counts[stepIndex]
    LaunchedEffect(Unit) { vm.clearFailure() }
    val grid = remember(count, photo.width, photo.height) { GridSizer.choose(count, photo.width, photo.height) }
    // What each preset really makes from this photo, for the tiles' "36 pieces" lines.
    val presetCounts = remember(photo.width, photo.height) {
        Preset.entries.associateWith { GridSizer.choose(Presets.targetCount(it), photo.width, photo.height).count }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!creating) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 1100.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 700.dp
            val compact = maxWidth < 480.dp
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            val controls: @Composable ColumnScope.() -> Unit = {
                SectionCard(SheetColors.Custom, icon = R.drawable.ic_trophy, title = stringResource(R.string.preset_title)) {
                    PresetTiles(
                        selected = preset,
                        counts = presetCounts,
                        compact = compact,
                        onPreset = { touched = true; choose(it) },
                        onCustom = { goCustom(); customOpen = !customOpen },
                    )
                }
                AnimatedVisibility(
                    visible = preset == null && customOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    CustomOptions(options) { options = it }
                }
                SectionCard(SheetColors.Medium) {
                    PieceCountPicker(
                        stepIndex = stepIndex,
                        onStepChange = { stepIndex = it; goCustom() },
                        pieces = grid.count,
                        rows = grid.rows,
                        cols = grid.cols,
                    )
                }
            }
            val shapes: @Composable () -> Unit = {
                SectionCard(SheetColors.Chunky, icon = R.drawable.ic_style_classic, title = stringResource(R.string.sheet_style_title)) {
                    StyleChooser(style = style, compact = compact, onStyleChange = { style = it; goCustom() })
                }
            }
            val startButton: @Composable () -> Unit = {
                StartArea(
                    creating = creating,
                    failed = failed,
                    onStart = { vm.start(photo, count, style, options.copy(preset = preset)) },
                )
            }
            if (wide) {
                // Tablet: the title, photo and piece shapes on the left; difficulty, piece count and Start on the right.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                ) {
                    Column(
                        Modifier.weight(1f).heightIn(max = (screenHeight - 100.dp).coerceAtLeast(240.dp)),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SheetHeader()
                        Column(
                            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Preview(
                                photo = photo, photoFile = photoFile, thumbFile = thumbFile,
                                rows = grid.rows, cols = grid.cols,
                                // Leaves room for the header and both rows of shape tiles (about 410dp) with no scrolling.
                                maxHeight = (screenHeight - 570.dp).coerceIn(160.dp, 520.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            shapes()
                        }
                    }
                    Column(
                        Modifier.weight(1.25f).heightIn(max = (screenHeight - 100.dp).coerceAtLeast(240.dp)),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(
                            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) { controls() }
                        startButton()
                    }
                }
            } else {
                // Phone: stacked. The choices scroll if the screen is short; the title and Start stay in view.
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    SheetHeader()
                    Spacer(Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    ) {
                        Preview(
                            photo = photo, photoFile = photoFile, thumbFile = thumbFile,
                            rows = grid.rows, cols = grid.cols,
                            maxHeight = (screenHeight * 0.34f).coerceAtLeast(160.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        controls()
                        shapes()
                    }
                    Spacer(Modifier.height(12.dp))
                    startButton()
                }
            }
        }
    }
}

@Composable
private fun SheetHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            painterResource(R.drawable.ic_puzzle_piece),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(44.dp).rotate(-8f),
        )
        Text(
            stringResource(R.string.sheet_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * The photo as a slightly tilted snapshot on a strip of tape, with faint lines showing how it will be cut
 * ([rows] × [cols]) so the grown-up sees the pieces change as they slide.
 */
@Composable
private fun Preview(
    photo: PhotoEntity,
    photoFile: File,
    thumbFile: File,
    rows: Int,
    cols: Int,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    // Show the thumbnail at once, then swap in a sharper decode of the real photo.
    val sharp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photoFile) {
        value = try {
            decodeSampled(photoFile, 1600).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    val aspect = photo.width.toFloat() / photo.height.toFloat()
    Box(modifier.heightIn(max = maxHeight).padding(horizontal = 10.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
        val card = RoundedCornerShape(18.dp)
        Box(
            Modifier
                .graphicsLayer { rotationZ = -2f }
                .shadow(10.dp, card, clip = false)
                .background(Color.White, card)
                .padding(10.dp),
        ) {
            val frame = Modifier.aspectRatio(aspect).clip(RoundedCornerShape(10.dp)).cutLines(rows, cols)
            val bitmap = sharp
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = stringResource(R.string.cd_photo_preview),
                    contentScale = ContentScale.Fit,
                    modifier = frame,
                )
            } else {
                ThumbImage(thumbFile, frame, contentDescription = stringResource(R.string.cd_photo_preview), contentScale = ContentScale.Fit)
            }
            // A strip of sunny tape holding the snapshot.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-20).dp)
                    .rotate(5f)
                    .size(76.dp, 24.dp)
                    .background(SheetColors.Sun.fill.copy(alpha = 0.78f), RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** Faint cut lines over the photo: a dark hairline under a white one, so they show on light and dark pictures. */
private fun Modifier.cutLines(rows: Int, cols: Int) = drawWithContent {
    drawContent()
    val under = Color.Black.copy(alpha = 0.22f)
    val over = Color.White.copy(alpha = 0.8f)
    val wide = 3.dp.toPx()
    val thin = 1.4.dp.toPx()
    for (pass in 0..1) {
        val color = if (pass == 0) under else over
        val stroke = if (pass == 0) wide else thin
        for (c in 1 until cols) {
            val x = size.width * c / cols
            drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
        }
        for (r in 1 until rows) {
            val y = size.height * r / rows
            drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

private data class PresetLook(@StringRes val name: Int, val tint: Tint, val spice: Int)

private fun presetLook(p: Preset?): PresetLook = when (p) {
    Preset.TINY -> PresetLook(R.string.preset_tiny, SheetColors.Tiny, 1)
    Preset.EASY -> PresetLook(R.string.preset_easy, SheetColors.Easy, 2)
    Preset.MEDIUM -> PresetLook(R.string.preset_medium, SheetColors.Medium, 3)
    Preset.HARD -> PresetLook(R.string.preset_hard, SheetColors.Hard, 4)
    Preset.EXPERT -> PresetLook(R.string.preset_expert, SheetColors.Expert, 5)
    null -> PresetLook(R.string.preset_custom, SheetColors.Custom, 0)
}

/** Six chunky tiles in two rows of three: the five presets, then Custom. */
@Composable
private fun PresetTiles(
    selected: Preset?,
    counts: Map<Preset, Int>,
    compact: Boolean,
    onPreset: (Preset) -> Unit,
    onCustom: () -> Unit,
) {
    val all: List<Preset?> = Preset.entries + listOf(null)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in all.chunked(3)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in row) {
                    val look = presetLook(p)
                    ToyTile(
                        selected = selected == p,
                        tint = look.tint,
                        onClick = { if (p == null) onCustom() else onPreset(p) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { c ->
                        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
                            if (p == null) IconBadge(R.drawable.ic_settings, 26.dp, 19.dp) else Spice(look.spice, c)
                        }
                        Text(
                            stringResource(look.name),
                            color = c,
                            fontSize = if (compact) 15.sp else 17.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        Text(
                            if (p == null) stringResource(R.string.preset_custom_hint)
                            else stringResource(R.string.sheet_pieces_count, counts[p] ?: Presets.targetCount(p)),
                            color = c.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** A row of five dots, [level] of them lit: how tricky the preset is. */
@Composable
private fun Spice(level: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val lit = i < level
            Box(
                Modifier
                    .size(if (lit) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (lit) color else color.copy(alpha = 0.3f)),
            )
        }
    }
}

private fun styleTint(style: PieceStyle): Tint = when (style) {
    PieceStyle.CHUNKY -> SheetColors.Chunky
    PieceStyle.CLASSIC -> SheetColors.Classic
    PieceStyle.WOBBLY -> SheetColors.Wobbly
    PieceStyle.RIBBON -> SheetColors.Ribbon
    PieceStyle.WAVY_RIBBON -> SheetColors.WavyRibbon
    PieceStyle.FREEFORM -> SheetColors.Freeform
    PieceStyle.HEARTS -> SheetColors.Hearts
    PieceStyle.PARTY -> SheetColors.Party
}

/**
 * All eight shapes. Wide sheets show two rows of four; compact ones scroll a single row of fixed-width tiles.
 * Tile heights are fixed by their text (names one line, hints two), so nothing moves while the slider drags.
 */
@Composable
private fun StyleChooser(style: PieceStyle, compact: Boolean, onStyleChange: (PieceStyle) -> Unit) {
    val all = PieceStyle.entries
    if (compact) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(IntrinsicSize.Min).selectableGroup(),
        ) {
            all.forEach { s -> StyleTile(s, style, true, Modifier.width(132.dp).fillMaxHeight(), onStyleChange) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
            all.chunked(4).forEach { rowStyles ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    rowStyles.forEach { s -> StyleTile(s, style, false, Modifier.weight(1f).fillMaxHeight(), onStyleChange) }
                }
            }
        }
    }
}

@Composable
private fun StyleTile(
    value: PieceStyle,
    selected: PieceStyle,
    compact: Boolean,
    modifier: Modifier,
    onSelect: (PieceStyle) -> Unit,
) {
    ToyTile(
        selected = value == selected,
        tint = styleTint(value),
        onClick = { onSelect(value) },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
    ) { c ->
        IconBadge(styleIcon(value), if (compact) 48.dp else 56.dp, if (compact) 34.dp else 40.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(styleName(value)), color = c, lineHeight = 21.sp,
            fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
            // Long names ("Wavy ribbon") shrink to fit on one line instead of losing a word.
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 17.sp),
        )
        Text(
            stringResource(styleHint(value)),
            color = c.copy(alpha = 0.9f),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
        )
    }
}

/** The big gradient Start pill; it squashes when pressed and dims while the puzzle is being made. */
@Composable
private fun StartArea(creating: Boolean, failed: Boolean, onStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (failed) {
            Text(
                stringResource(R.string.sheet_start_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale = animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.4f, stiffness = 700f), label = "start")
        val shape = RoundedCornerShape(50)
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .shadow(if (creating) 0.dp else 8.dp, shape, spotColor = SheetColors.Custom.deep, ambientColor = SheetColors.Custom.deep)
                .background(Brush.horizontalGradient(SheetColors.Start), shape, alpha = if (creating) 0.7f else 1f)
                .clickable(interaction, null, enabled = !creating, role = Role.Button, onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            // A soft shine across the top half, like a plastic toy button.
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(shape)
                    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.5f to Color.Transparent)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (creating) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.sheet_starting), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                } else {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_play),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(34.dp).offset(x = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.sheet_start), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/** Saves [PlayOptions] across rotation as plain strings (the sheet is recreated when the screen turns). */
private val OptionsSaver = Saver<PlayOptions, List<String>>(
    save = {
        listOf(
            it.preset?.name ?: "", it.rotation.name, it.ghost.name, it.peek.name, it.snap.name,
            it.startInTray.toString(), it.boxPicture.toString(), it.helper.toString(),
            it.hintButton.toString(), it.timer.toString(),
        )
    },
    restore = {
        PlayOptions(
            preset = Preset.entries.firstOrNull { p -> p.name == it[0] },
            rotation = RotationMode.valueOf(it[1]), ghost = Ghost.valueOf(it[2]), peek = PeekMode.valueOf(it[3]),
            snap = Snap.valueOf(it[4]), startInTray = it[5].toBoolean(), boxPicture = it[6].toBoolean(),
            helper = it[7].toBoolean(), hintButton = it[8].toBoolean(), timer = it[9].toBoolean(),
        )
    },
)

/** Every custom option: four "pick one" cards, then on/off toggle tiles. */
@Composable
private fun CustomOptions(o: PlayOptions, onChange: (PlayOptions) -> Unit) {
    SectionCard(SheetColors.Custom) {
        ChoiceCard(R.drawable.ic_turn, R.string.opt_rotation, SheetColors.Chunky, o.rotation, listOf(
            RotationMode.OFF to R.string.opt_rotation_off, RotationMode.QUARTER to R.string.opt_rotation_quarter,
            RotationMode.ANY to R.string.opt_rotation_any,
        )) { onChange(o.copy(rotation = it)) }
        ChoiceCard(R.drawable.ic_gallery, R.string.opt_ghost, SheetColors.Classic, o.ghost, listOf(
            Ghost.STRONG to R.string.opt_ghost_strong, Ghost.FAINT to R.string.opt_ghost_faint, Ghost.OFF to R.string.opt_ghost_off,
        )) { onChange(o.copy(ghost = it)) }
        ChoiceCard(R.drawable.ic_peek, R.string.opt_peek, SheetColors.Sun, o.peek, listOf(
            PeekMode.UNLIMITED to R.string.opt_peek_always, PeekMode.LIMITED to R.string.opt_peek_limited,
            PeekMode.NEVER to R.string.opt_peek_never,
        )) { onChange(o.copy(peek = it)) }
        ChoiceCard(R.drawable.ic_pieces, R.string.opt_snap, SheetColors.Wobbly, o.snap, listOf(
            Snap.VERY_FORGIVING to R.string.opt_snap_very_forgiving, Snap.FORGIVING to R.string.opt_snap_forgiving,
            Snap.NORMAL to R.string.opt_snap_normal, Snap.PRECISE to R.string.opt_snap_precise,
        )) { onChange(o.copy(snap = it)) }
        val toggles = listOf(
            Toggle(R.drawable.ic_tray, R.string.opt_tray, SheetColors.Medium, o.startInTray) { onChange(o.copy(startInTray = it)) },
            Toggle(R.drawable.ic_box_closed, R.string.opt_box, SheetColors.Hard, o.boxPicture) { onChange(o.copy(boxPicture = it)) },
            Toggle(R.drawable.ic_puzzle_piece, R.string.opt_helper, SheetColors.Custom, o.helper) { onChange(o.copy(helper = it)) },
            Toggle(R.drawable.ic_hint, R.string.opt_hint, SheetColors.Sun, o.hintButton) { onChange(o.copy(hintButton = it)) },
            Toggle(R.drawable.ic_timer, R.string.opt_timer, SheetColors.Easy, o.timer) { onChange(o.copy(timer = it)) },
        )
        for (row in toggles.chunked(3)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in row) ToggleTile(t, Modifier.weight(1f).fillMaxHeight())
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private class Toggle(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    val tint: Tint,
    val on: Boolean,
    val onChange: (Boolean) -> Unit,
)

/** An on/off option as a tile: lit with its colour when on, soft with a faded icon when off. */
@Composable
private fun ToggleTile(t: Toggle, modifier: Modifier) {
    ToyTile(
        selected = t.on,
        tint = t.tint,
        toggle = true,
        onClick = { t.onChange(!t.on) },
        modifier = modifier,
        corner = 18.dp,
    ) { c ->
        IconBadge(t.icon, 40.dp, 28.dp, faded = !t.on)
        Text(
            stringResource(t.title),
            color = c,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
        )
        Text(
            stringResource(if (t.on) R.string.opt_on else R.string.opt_off),
            color = c.copy(alpha = 0.9f),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** A white card for one "pick one" option: its icon and name, then a row of toy pills. */
@Composable
private fun <T> ChoiceCard(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    tint: Tint,
    selected: T,
    choices: List<Pair<T, Int>>,
    onSelect: (T) -> Unit,
) {
    val dark = isDarkSheet()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(26.dp))
            Text(stringResource(title), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = tint.onSoft(dark))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
        ) {
            for ((value, label) in choices) {
                val on = value == selected
                ToyTile(
                    selected = on,
                    tint = tint,
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                    corner = 14.dp,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                ) { c ->
                    Text(
                        stringResource(label),
                        color = c,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
