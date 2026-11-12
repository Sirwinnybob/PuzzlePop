package com.family.puzzlepop.kid

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.puzzlepop.PuzzlePopApp
import com.family.puzzlepop.R
import com.family.puzzlepop.data.PhotoEntity
import com.family.puzzlepop.library.ThumbImage
import kotlinx.coroutines.launch

/** The cheerful palette for kid screens: (fill, border) pairs from res/values/icon_colors.xml. */
@Composable
private fun kidPalette(): List<Pair<Color, Color>> = listOf(
    colorResource(R.color.icon_coral_light) to colorResource(R.color.icon_coral),
    colorResource(R.color.icon_sun_light) to colorResource(R.color.icon_sun),
    colorResource(R.color.icon_sky_light) to colorResource(R.color.icon_sky),
    colorResource(R.color.icon_leaf_light) to colorResource(R.color.icon_leaf),
    colorResource(R.color.icon_grape_light) to colorResource(R.color.icon_grape),
)

/**
 * Kid mode's home: big picture tiles, a "Keep playing" row, and (after a tap on a picture) three big
 * puzzle-size buttons. Almost no text. System Back does nothing; the only way out is the grown-up
 * button, which unpins the screen and goes to the library.
 *
 * @param onOpenPuzzle opens a puzzle in kid play.
 * @param onLeave called after the gate was passed and kid mode has been switched off.
 */
@Composable
fun KidHomeScreen(onOpenPuzzle: (String) -> Unit, onLeave: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as PuzzlePopApp).container
    val vm: KidHomeViewModel = viewModel(factory = KidHomeViewModel.factory(container))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pickedId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler {} // she can't back out of kid mode
    EnsureKidModeOn()
    LaunchedEffect(vm) { vm.created.collect { id -> pickedId = null; onOpenPuzzle(id) } }

    val palette = kidPalette()
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colorResource(R.color.icon_sky_light), colorResource(R.color.icon_sun_light)))),
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
        LazyVerticalGrid(
            columns = GridCells.Adaptive(260.dp),
            contentPadding = PaddingValues(
                start = insets.calculateStartPadding(dir) + 32.dp,
                end = insets.calculateEndPadding(dir) + 32.dp,
                top = insets.calculateTopPadding() + 16.dp,
                bottom = insets.calculateBottomPadding() + 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(end = 64.dp), // clear of the grown-up button
                ) {
                    Icon(painterResource(R.drawable.ic_kid_mode), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(72.dp))
                    Text(
                        stringResource(R.string.app_name),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorResource(R.color.icon_grape_dark),
                    )
                }
            }
            if (ui.loaded && ui.photos.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_empty_library), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(160.dp))
                        Text(
                            stringResource(R.string.kid_home_empty),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = colorResource(R.color.icon_ink),
                        )
                    }
                }
            }
            if (ui.inProgress.isNotEmpty()) {
                item(key = "continue-title", span = { GridItemSpan(maxLineSpan) }) {
                    KidSectionTitle(stringResource(R.string.library_keep_playing))
                }
                item(key = "continue-row", span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        itemsIndexed(ui.inProgress, key = { _, it -> it.puzzle.id }) { index, item ->
                            val (fill, edge) = palette[(index + 3) % palette.size]
                            KidTile(
                                thumb = vm.thumbFile(item.photo),
                                fill = fill,
                                edge = edge,
                                a11y = stringResource(R.string.library_keep_playing),
                                progress = item.puzzle.placedCount / item.puzzle.pieceCount.coerceAtLeast(1).toFloat(),
                                onClick = { onOpenPuzzle(item.puzzle.id) },
                                modifier = Modifier.width(280.dp),
                            )
                        }
                    }
                }
            }
            if (ui.photos.isNotEmpty()) {
                item(key = "pick-title", span = { GridItemSpan(maxLineSpan) }) {
                    KidSectionTitle(stringResource(R.string.library_pick_photo))
                }
                itemsIndexed(ui.photos, key = { _, it -> it.id }) { index, photo ->
                    val (fill, edge) = palette[index % palette.size]
                    KidTile(
                        thumb = vm.thumbFile(photo),
                        fill = fill,
                        edge = edge,
                        a11y = stringResource(R.string.kid_photo_a11y),
                        onClick = { pickedId = photo.id },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        val exit = rememberKidExitController(onLeave)
        GrownUpButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            onUnlocked = {},
            onHeld = { exit.request(alreadyGated = false) },
        )
        exit.Gate()
    }

    val picked = ui.photos.firstOrNull { it.id == pickedId }
    if (picked != null) {
        SizePicker(
            photo = picked,
            ui = ui,
            thumb = vm.thumbFile(picked),
            onPick = { count -> vm.start(picked, count) },
            onDismiss = { if (!ui.creating) { pickedId = null; vm.clearFailure() } },
        )
    }
}

/**
 * Kid mode stays on across restarts: marks it active (this screen can be reached by "always open in kid
 * mode" alone) and asks the system to pin the app if it is not pinned yet.
 */
@Composable
internal fun EnsureKidModeOn() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val settings = (context.applicationContext as PuzzlePopApp).container.settings
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { settings.setKidModeActive(true) }
    LifecycleResumeEffect(Unit) {
        if (activity != null) {
            scope.launch { if (!KidModeExit.pinSuppressed(settings)) ScreenPinning.ensurePinned(activity) }
        }
        onPauseOrDispose { }
    }
}

@Composable
private fun KidSectionTitle(text: String) {
    Text(text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = colorResource(R.color.icon_ink))
}

/** A big rounded picture with a thick coloured border. [progress] adds a bar along the bottom. */
@Composable
private fun KidTile(
    thumb: java.io.File,
    fill: Color,
    edge: Color,
    a11y: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .aspectRatio(4f / 3f)
            .clip(shape)
            .background(fill)
            .border(BorderStroke(8.dp, edge), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = a11y },
    ) {
        ThumbImage(thumb, Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(24.dp)))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                color = edge,
                trackColor = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp).height(14.dp).clip(RoundedCornerShape(7.dp)),
            )
        }
    }
}

/** The picture with three big size buttons (small / medium / big); a tap starts the puzzle at once. */
@Composable
private fun SizePicker(photo: PhotoEntity, ui: KidHomeUi, thumb: java.io.File, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(40.dp), color = colorResource(R.color.icon_white), modifier = Modifier.padding(24.dp).widthIn(max = 640.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(28.dp)) {
                    ThumbImage(
                        thumb,
                        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                        SizeButton(R.drawable.ic_size_small, stringResource(R.string.kid_size_small), R.color.icon_coral_light, R.color.icon_coral, !ui.creating, { onPick(ui.small) }, Modifier.weight(1f))
                        SizeButton(R.drawable.ic_size_medium, stringResource(R.string.kid_size_medium), R.color.icon_sun_light, R.color.icon_sun, !ui.creating, { onPick(ui.medium) }, Modifier.weight(1f))
                        SizeButton(R.drawable.ic_size_big, stringResource(R.string.kid_size_big), R.color.icon_leaf_light, R.color.icon_leaf, !ui.creating, { onPick(ui.big) }, Modifier.weight(1f))
                    }
                    if (ui.failed) {
                        Text(stringResource(R.string.kid_start_failed), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colorResource(R.color.icon_coral_dark))
                    }
                }
                if (ui.creating) CircularProgressIndicator(Modifier.size(72.dp), color = colorResource(R.color.icon_grape), strokeWidth = 8.dp)
            }
        }
    }
}

@Composable
private fun SizeButton(
    @DrawableRes icon: Int,
    label: String,
    @androidx.annotation.ColorRes fill: Int,
    @androidx.annotation.ColorRes edge: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .height(150.dp)
            .clip(shape)
            .background(colorResource(fill))
            .border(BorderStroke(6.dp, colorResource(edge)), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(104.dp))
    }
}
