package com.family.puzzlepop.library

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import com.family.puzzlepop.kid.GrownUpGateDialog
import com.family.puzzlepop.kid.StartKidModeFlow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.puzzlepop.PuzzlePopApp
import com.family.puzzlepop.R
import com.family.puzzlepop.play.formatElapsed
import com.family.puzzlepop.data.PhotoEntity
import com.family.puzzlepop.newpuzzle.NewPuzzleSheet
import com.family.puzzlepop.newpuzzle.NewPuzzleViewModel
import java.io.File

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun LibraryScreen(onOpenPuzzle: (String) -> Unit, onStartKidMode: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as PuzzlePopApp).container
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val newPuzzleVm: NewPuzzleViewModel = viewModel(factory = NewPuzzleViewModel.factory(container))
    val ui by vm.ui.collectAsStateWithLifecycle()

    val activity = checkNotNull(LocalActivity.current)
    val widthClass = calculateWindowSizeClass(activity).widthSizeClass
    val compact = widthClass == WindowWidthSizeClass.Compact
    val expanded = widthClass == WindowWidthSizeClass.Expanded

    val snackbar = remember { SnackbarHostState() }
    var sheetPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var removePuzzleId by rememberSaveable { mutableStateOf<String?>(null) } // Keep-playing puzzle awaiting remove confirmation
    var menuPhotoId by rememberSaveable { mutableStateOf<String?>(null) } // photo awaiting delete confirmation
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var startingKidMode by rememberSaveable { mutableStateOf(false) }
    var unlockingSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }
    LaunchedEffect(Unit) {
        newPuzzleVm.created.collect { id ->
            sheetPhotoId = null
            onOpenPuzzle(id)
        }
    }

    // Adding photos: the system gallery picker, or the system camera writing into our FileProvider cache.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.importAll(uris)
    }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = cameraPath
        cameraPath = null
        if (path != null) {
            val file = File(path)
            if (saved) vm.import(fileUri(context, file), deleteAfter = file) else file.delete()
        }
    }
    val hasCamera = remember {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val pm = context.packageManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0)).isNotEmpty()
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0).isNotEmpty()
        }
    }
    val pickFromGallery = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val takePhoto = {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() } // leftovers from interrupted captures
        val file = File.createTempFile("capture", ".jpg", dir)
        cameraPath = file.path
        try {
            camera.launch(fileUri(context, file))
        } catch (e: ActivityNotFoundException) {
            cameraPath = null
            file.delete()
        }
    }
    val startAddPhoto = {
        if (hasCamera) showAddDialog = true else pickFromGallery()
    }

    val empty = ui.loaded && ui.photos.isEmpty()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                FilledTonalButton(onClick = { unlockingSettings = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.settings_open), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(onClick = { startingKidMode = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(painterResource(R.drawable.ic_kid_mode), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.kid_start), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        floatingActionButton = {
            if (ui.loaded && !empty) {
                ExtendedFloatingActionButton(
                    onClick = startAddPhoto,
                    icon = { Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp)) },
                    text = { Text(stringResource(R.string.library_add_photo), fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.heightIn(min = 64.dp),
                )
            }
        },
    ) { inner ->
        when {
            !ui.loaded -> Unit
            empty -> EmptyLibrary(onAdd = startAddPhoto, importing = ui.importing, modifier = Modifier.padding(inner))
            else -> LibraryGrid(
                ui = ui,
                vm = vm,
                compact = compact,
                expanded = expanded,
                inner = inner,
                onOpenPhoto = { sheetPhotoId = it.id },
                onOpenPuzzle = onOpenPuzzle,
                onAskDelete = { menuPhotoId = it.id },
                onAskRemovePuzzle = { removePuzzleId = it },
            )
        }
    }

    if (showAddDialog) {
        AddPhotoDialog(
            onDismiss = { showAddDialog = false },
            onGallery = { showAddDialog = false; pickFromGallery() },
            onCamera = { showAddDialog = false; takePhoto() },
        )
    }

    if (unlockingSettings) {
        GrownUpGateDialog(onDismiss = { unlockingSettings = false }, onUnlocked = { unlockingSettings = false; onOpenSettings() })
    }

    if (startingKidMode) {
        StartKidModeFlow(onCancel = { startingKidMode = false }, onStarted = onStartKidMode)
    }

    val deleting = ui.photos.firstOrNull { it.id == menuPhotoId }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { menuPhotoId = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body), fontSize = 18.sp) },
            confirmButton = {
                TextButton(
                    onClick = { vm.delete(deleting); menuPhotoId = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.delete_confirm), fontSize = 18.sp, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { menuPhotoId = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.delete_cancel), fontSize = 18.sp)
                }
            },
        )
    }

    val removing = ui.inProgress.firstOrNull { it.puzzle.id == removePuzzleId }
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { removePuzzleId = null },
            title = { Text(stringResource(R.string.remove_puzzle_title)) },
            text = { Text(stringResource(R.string.remove_puzzle_body), fontSize = 18.sp) },
            confirmButton = {
                TextButton(
                    onClick = { vm.removePuzzle(removing.puzzle.id); removePuzzleId = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.remove_puzzle_confirm), fontSize = 18.sp, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removePuzzleId = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.delete_cancel), fontSize = 18.sp)
                }
            },
        )
    }

    val sheetPhoto = ui.photos.firstOrNull { it.id == sheetPhotoId }
    if (sheetPhoto != null) {
        NewPuzzleSheet(
            photo = sheetPhoto,
            photoFile = container.photoRepository.photoFile(sheetPhoto),
            thumbFile = vm.thumbFile(sheetPhoto),
            vm = newPuzzleVm,
            onDismiss = { sheetPhotoId = null },
        )
    }
}

private fun fileUri(context: android.content.Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

@Composable
private fun LibraryGrid(
    ui: LibraryUi,
    vm: LibraryViewModel,
    compact: Boolean,
    expanded: Boolean,
    inner: PaddingValues,
    onOpenPhoto: (PhotoEntity) -> Unit,
    onOpenPuzzle: (String) -> Unit,
    onAskDelete: (PhotoEntity) -> Unit,
    onAskRemovePuzzle: (String) -> Unit,
) {
    val dir = LocalLayoutDirection.current
    val side: Dp = if (compact) 16.dp else 32.dp
    val gap: Dp = if (compact) 12.dp else 20.dp
    val now = remember(ui.inProgress) { System.currentTimeMillis() }
    LazyVerticalGrid(
        // Phones: ~2 columns. Tablets get bigger photos so a 12" screen is not mostly empty space.
        columns = GridCells.Adaptive(if (compact) 150.dp else if (expanded) 260.dp else 210.dp),
        contentPadding = PaddingValues(
            start = inner.calculateStartPadding(dir) + side,
            end = inner.calculateEndPadding(dir) + side,
            top = inner.calculateTopPadding() + 12.dp,
            bottom = inner.calculateBottomPadding() + 112.dp, // room for the Add photo button
        ),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painterResource(R.drawable.ic_puzzle_piece),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (ui.importing) {
            item(key = "importing", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.library_importing), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        if (ui.inProgress.isNotEmpty()) {
            item(key = "continue-title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.library_keep_playing))
            }
            item(key = "continue-row", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items(ui.inProgress, key = { it.puzzle.id }) { item ->
                        ContinueCard(
                            item = item,
                            thumb = vm.thumbFile(item.photo),
                            now = now,
                            width = if (compact) 170.dp else if (expanded) 320.dp else 240.dp,
                            onClick = { onOpenPuzzle(item.puzzle.id) },
                            onRemove = { onAskRemovePuzzle(item.puzzle.id) },
                        )
                    }
                }
            }
        }
        item(key = "pick-title", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 8.dp)) {
                SectionTitle(stringResource(R.string.library_pick_photo))
                Text(
                    stringResource(R.string.library_pick_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(ui.photos, key = { it.id }) { photo ->
            PhotoTile(
                photo = photo,
                thumb = vm.thumbFile(photo),
                bestMs = ui.bestByPhoto[photo.id],
                onClick = { onOpenPhoto(photo) },
                onToggleFavorite = { vm.toggleFavorite(photo) },
                onToggleKid = { vm.toggleKidApproved(photo) },
                onDelete = { onAskDelete(photo) },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(item: ContinueItem, thumb: File, now: Long, width: Dp, onClick: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val time = relativeTime(item.puzzle.updatedAt, now)
    val a11y = stringResource(R.string.library_continue_a11y, item.puzzle.placedCount, item.puzzle.pieceCount, time)
    Box {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .width(width)
            .semantics(mergeDescendants = true) { contentDescription = a11y }
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
            ThumbImage(thumb, Modifier.fillMaxSize())
            LinearProgressIndicator(
                progress = { item.puzzle.placedCount / item.puzzle.pieceCount.coerceAtLeast(1).toFloat() },
                trackColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = 8.dp),
            )
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                stringResource(R.string.play_pieces_placed, item.puzzle.placedCount, item.puzzle.pieceCount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        MenuRow(
            icon = R.drawable.ic_delete,
            text = stringResource(R.string.remove_puzzle_menu),
            onClick = { menuOpen = false; onRemove() },
        )
    }
    }
}

@Composable
private fun relativeTime(then: Long, now: Long): String {
    val minutes = ((now - then).coerceAtLeast(0) / 60_000).toInt()
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> stringResource(R.string.time_just_now)
        hours < 1 -> pluralStringResource(R.plurals.time_minutes_ago, minutes, minutes)
        days < 1 -> pluralStringResource(R.plurals.time_hours_ago, hours, hours)
        else -> pluralStringResource(R.plurals.time_days_ago, days, days)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    photo: PhotoEntity,
    thumb: File,
    bestMs: Long?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleKid: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val a11y = stringResource(if (photo.favorite) R.string.library_photo_favorite_a11y else R.string.library_photo_a11y)
    Box {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onLongClickLabel = stringResource(R.string.library_more_options),
                )
                .semantics { contentDescription = a11y },
        ) {
            Box(Modifier.fillMaxSize()) {
                ThumbImage(thumb, Modifier.fillMaxSize())
                if (bestMs != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_trophy), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.library_best, formatElapsed(bestMs)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (photo.favorite) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_favorite),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuRow(
                icon = if (photo.favorite) R.drawable.ic_favorite_off else R.drawable.ic_favorite,
                text = stringResource(if (photo.favorite) R.string.menu_unfavorite else R.string.menu_favorite),
                onClick = { menuOpen = false; onToggleFavorite() },
            )
            MenuRow(
                icon = R.drawable.ic_kid_mode,
                text = stringResource(if (photo.kidApproved) R.string.menu_kid_hide else R.string.menu_kid_show),
                onClick = { menuOpen = false; onToggleKid() },
            )
            MenuRow(
                icon = R.drawable.ic_delete,
                text = stringResource(R.string.menu_delete),
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

@Composable
private fun MenuRow(@DrawableRes icon: Int, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 18.sp) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp)) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 56.dp),
    )
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit, importing: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 32.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_empty_library),
                    contentDescription = stringResource(R.string.cd_empty_library),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(112.dp),
                )
                Text(
                    stringResource(R.string.library_empty_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.library_empty_body),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                if (importing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Button(onClick = onAdd, modifier = Modifier.heightIn(min = 64.dp)) {
                        Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.library_add_photo), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPhotoDialog(onDismiss: () -> Unit, onGallery: () -> Unit, onCamera: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(stringResource(R.string.add_photo_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SourceTile(R.drawable.ic_gallery, stringResource(R.string.add_photo_gallery), onGallery)
                    SourceTile(R.drawable.ic_camera, stringResource(R.string.add_photo_camera), onCamera)
                }
            }
        }
    }
}

@Composable
private fun SourceTile(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .size(width = 140.dp, height = 132.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(56.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
    }
}
