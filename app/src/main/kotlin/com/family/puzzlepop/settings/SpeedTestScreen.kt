package com.family.puzzlepop.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.metrics.performance.JankStats
import com.family.puzzlepop.PuzzlePopApp
import com.family.puzzlepop.R
import com.family.puzzlepop.engine.PieceStyle
import com.family.puzzlepop.engine.PuzzleGenerator
import com.family.puzzlepop.engine.PuzzleSpec
import com.family.puzzlepop.engine.game.PuzzleGame
import com.family.puzzlepop.play.Camera
import com.family.puzzlepop.play.CanvasDriver
import com.family.puzzlepop.play.PlayViewModel
import com.family.puzzlepop.play.PuzzleCanvas
import com.family.puzzlepop.play.tableFor
import com.family.puzzlepop.render.renderPuzzle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TEST_W = 4000
private const val TEST_H = 3000
private const val TEST_PIECES = 1000
private const val RUN_NANOS = 5_000_000_000L

/** What one run measured. */
private data class SpeedResult(
    val bitmapMs: Long,
    val buildMs: Long,
    val frames: Int,
    val smoothPct: Int,
    val slowestMs: Float,
    val refreshHz: Float,
)

/**
 * Hidden speed test (grown-up settings, tap the title 7 times). Builds a 1000-piece puzzle from a generated
 * picture, shows it in the real [PuzzleCanvas] and, for 5 s, moves 20 pieces in circles and pans/zooms the
 * camera from code every frame (no touch events), while JankStats counts the frames. Nothing is saved: the
 * play view model is created in its ephemeral mode, which has no stored puzzle, so every save is a no-op.
 */
@Composable
fun SpeedTestScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    val container = (context.applicationContext as PuzzlePopApp).container
    val vm: PlayViewModel = viewModel(key = "speedtest", factory = PlayViewModel.ephemeralFactory(container, context))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val activity = checkNotNull(LocalActivity.current)
    val view = LocalView.current
    var run by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf(Stage.BUILDING) }
    var result by remember { mutableStateOf<SpeedResult?>(null) }
    // Set when a build finishes; the run effect keys on it (and never writes it, so it is not cancelled).
    var readyRun by remember { mutableIntStateOf(-1) }
    var driver by remember { mutableStateOf<CanvasDriver?>(null) }
    var bitmapMs by remember { mutableStateOf(0L) }
    var buildMs by remember { mutableStateOf(0L) }

    // Build: picture, layout, pieces. Restarts with every "Run again".
    LaunchedEffect(run) {
        stage = Stage.BUILDING
        result = null
        vm.clearEphemeral() // drop the previous run's pieces before building new ones
        val t0 = SystemClock.uptimeMillis()
        val photo = withContext(Dispatchers.Default) { plasma(TEST_W, TEST_H) }
        val t1 = SystemClock.uptimeMillis()
        val spec = PuzzleSpec(TEST_W, TEST_H, TEST_PIECES, PieceStyle.CLASSIC, 12345L + run)
        try {
            val layout = withContext(Dispatchers.Default) { PuzzleGenerator.generate(spec) }
            val game = PuzzleGame.newGame(layout, tableFor(layout), spec.seed)
            val rendered = renderPuzzle(photo, layout)
            buildMs = SystemClock.uptimeMillis() - t1
            bitmapMs = t1 - t0
            vm.attachEphemeral(game, rendered)
        } finally {
            photo.recycle()
        }
        stage = Stage.SHOWING
        readyRun = run
    }

    // Run: wait for the canvas to have a camera, then script 5 s.
    LaunchedEffect(readyRun) {
        if (readyRun < 0) return@LaunchedEffect
        var d = driver
        while (d == null || d.camera == null) {
            withFrameNanos { }
            d = driver
        }
        delay(600) // let the first frames (fit camera, mask warm-up) settle
        stage = Stage.RUNNING
        val frameMs = ArrayList<Float>(600)
        var jank = 0
        val lock = Any()
        var stats: JankStats? = null
        try {
            stats = JankStats.createAndTrack(activity.window) { fd ->
                synchronized(lock) {
                    frameMs += fd.frameDurationUiNanos / 1_000_000f
                    if (fd.isJank) jank++
                }
            }
            stats.isTrackingEnabled = true
            script(vm, d)
        } finally {
            stats?.isTrackingEnabled = false
        }
        delay(150)
        val (n, j, slow) = synchronized(lock) { Triple(frameMs.size, jank, frameMs.maxOrNull() ?: 0f) }
        val hz = view.display?.refreshRate ?: 0f
        result = SpeedResult(bitmapMs, buildMs, n, if (n == 0) 0 else ((n - j) * 100f / n).toInt(), slow, hz)
        stage = Stage.DONE
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val rendered = ui.rendered
        if (rendered != null && stage != Stage.BUILDING) {
            androidx.compose.runtime.key(run) {
                PuzzleCanvas(vm, rendered, peeking = { false }, modifier = Modifier.fillMaxSize(), onDriver = { driver = it })
            }
        }
        if (stage == Stage.BUILDING || stage == Stage.SHOWING || stage == Stage.RUNNING) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
            ) {
                Text(
                    stringResource(if (stage == Stage.RUNNING) R.string.speedtest_running else R.string.speedtest_building),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        val r = result
        if (stage == Stage.DONE && r != null) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                modifier = Modifier.align(Alignment.Center).widthIn(max = 560.dp).padding(16.dp),
            ) {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.speedtest_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    ResultLine(R.string.speedtest_smooth, "${r.smoothPct} %")
                    ResultLine(R.string.speedtest_slowest, "%.1f ms".format(r.slowestMs))
                    ResultLine(R.string.speedtest_build, "${r.buildMs} ms")
                    ResultLine(R.string.speedtest_picture, "${r.bitmapMs} ms")
                    ResultLine(R.string.speedtest_frames, r.frames.toString())
                    ResultLine(R.string.speedtest_refresh, "%.0f Hz".format(r.refreshHz))
                    ResultLine(R.string.speedtest_device, "${Build.MANUFACTURER} ${Build.MODEL}")
                    if (com.family.puzzlepop.BuildConfig.DEBUG) {
                        Text(stringResource(R.string.speedtest_debug_note), style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.Gray)
                    }
                    Text(stringResource(R.string.speedtest_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Button(onClick = { run++ }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.speedtest_again), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(onClick = onDone, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.settings_done), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private enum class Stage { BUILDING, SHOWING, RUNNING, DONE }

@Composable
private fun ResultLine(labelRes: Int, value: String) {
    Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(labelRes), fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 5 s of frame-by-frame motion that mirrors real play: for the first half one loose piece is lifted and moved
 * along a smooth path through the view model (the same onDragStart/onDrag a finger drives); for the second half
 * the camera pans gently with a small zoom change (up to 10 %, so the resting layer is not re-recorded
 * mid-gesture). The piece is put down with no drop, so nothing locks, joins or is saved.
 */
private suspend fun script(vm: PlayViewModel, d: CanvasDriver) {
    val game = vm.game
    val n = game.layout.pieces.size
    val id = (n / 2 until n).firstOrNull { !game.isLocked(it) && !game.isInTray(it) } ?: return
    val base = d.camera ?: return
    val vp: Size = d.viewport
    val boardW = game.layout.spec.imageWidth.toFloat()
    val boardH = game.layout.spec.imageHeight.toFloat()
    val c0 = Offset((vp.width / 2 - base.offsetX) / base.scale, (vp.height / 2 - base.offsetY) / base.scale)
    val x0 = game.x(id)
    val y0 = game.y(id)
    val radius = 450f
    vm.onDragStart(id)
    d.lift(id, 20f, 20f)
    try {
        var start = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (start < 0) start = now
            val t = (now - start).toFloat() / RUN_NANOS
            if (t >= 1f) break
            if (t < 0.5f) {
                // One smooth lap of a circle that starts where the piece is.
                val a = (t / 0.5f * 2 * PI).toFloat()
                vm.onDrag(id, x0 + radius * (cos(a) - 1f) - game.x(id), y0 + radius * sin(a) - game.y(id))
            } else {
                val u = (t - 0.5f) / 0.5f
                val ease = (1f - cos((u * PI).toFloat())) / 2f // 0 -> 1 -> smooth
                val s = base.scale * (1f + 0.10f * sin((u * PI).toFloat()))
                val px = c0.x + boardW * 0.18f * sin((u * PI).toFloat() * 1f) * 1f + boardW * 0.1f * (ease - 0.5f)
                val py = c0.y + boardH * 0.10f * sin((u * 2 * PI).toFloat())
                d.setCamera(Camera(s, vp.width / 2 - px * s, vp.height / 2 - py * s))
            }
        }
        d.setCamera(base)
    } finally {
        d.release(id)
    }
}

/** A colourful plasma-like picture: a gradient base plus a few hundred soft radial blobs. Memory only. */
private fun plasma(w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF1B4F9C.toInt(), 0xFFE0782B.toInt(), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    val rnd = Random(7)
    repeat(260) {
        val r = 120f + rnd.nextFloat() * 520f
        val x = rnd.nextFloat() * w
        val y = rnd.nextFloat() * h
        val col = android.graphics.Color.HSVToColor(floatArrayOf(rnd.nextFloat() * 360f, 0.55f + rnd.nextFloat() * 0.4f, 0.55f + rnd.nextFloat() * 0.45f))
        val inner = (col and 0x00FFFFFF) or (0xB0 shl 24)
        val outer = col and 0x00FFFFFF
        p.shader = RadialGradient(x, y, r, inner, outer, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
    }
    p.shader = null
    return bmp
}
