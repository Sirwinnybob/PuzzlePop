package com.family.puzzlepop.kid

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import com.family.puzzlepop.AppContainer
import com.family.puzzlepop.BuildConfig
import com.family.puzzlepop.PuzzlePopApp
import com.family.puzzlepop.kid.PlayTimeKeeper.Status
import com.family.puzzlepop.settings.KidSettings
import com.family.puzzlepop.settings.SettingsRepository
import com.family.puzzlepop.settings.Usage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Owns the [PlayTimeKeeper] for the whole activity (kid home and kid play share it). A loop ticks it every
 * second; [status] is what the screens react to. Usage is saved every [SAVE_EVERY_TICKS] seconds, whenever
 * the status kind changes (so a break survives a relaunch) and on [persistNow] (activity stop).
 *
 * Everything runs on the main thread (viewModelScope), so the keeper needs no locking.
 */
class KidTimeViewModel(private val settings: SettingsRepository) : ViewModel() {
    private val _status = MutableStateFlow<Status>(Status.Playing)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _napping = MutableStateFlow(false)

    /** True while the kid-mode screen nap is showing; puzzles save when it turns on. */
    val napping: StateFlow<Boolean> = _napping.asStateFlow()

    private val _now = MutableStateFlow(System.currentTimeMillis())

    /** The clock the keeper sees, in epoch ms (sped up by 60 with the debug fast clock). Drives countdowns. */
    val now: StateFlow<Long> = _now.asStateFlow()

    private var keeper: PlayTimeKeeper? = null
    private var kidSettings = KidSettings()
    private var fast = false
    private var playing = false
    private var isNap = false
    private var piecesLeft = Int.MAX_VALUE
    private var lastVirtual = 0L
    private var lastSaved: Usage? = null
    private var ticksSinceSave = 0
    private var foreground = true

    init {
        viewModelScope.launch {
            val fastFlow = if (BuildConfig.DEBUG) settings.fastClock else flowOf(false)
            var first = true
            val persisted = settings.usage.first()
            lastSaved = persisted
            launch {
                combine(settings.kidSettings, fastFlow) { s, f -> s to f }.collect { (s, f) ->
                    val old = keeper
                    val fastChanged = !first && f != fast
                    fast = f
                    kidSettings = s
                    val usage = when {
                        first -> persisted
                        fastChanged -> Usage(LocalDate.now().toString(), 0, 0, null, false) // clocks differ: start clean
                        else -> old!!.usage
                    }
                    keeper = PlayTimeKeeper(s, usage).also { it.piecesLeftChanged(piecesLeft) }
                    lastVirtual = virtualNow()
                    first = false
                    refresh()
                }
            }
            while (true) {
                delay(1_000)
                if (!foreground) continue // nothing to count or show while the app is in the background
                refresh()
                if (++ticksSinceSave >= SAVE_EVERY_TICKS) persistNow()
            }
        }
    }

    private fun virtualNow(): Long = System.currentTimeMillis().let { if (fast) it * 60 else it }

    /** Advances the keeper to the clock now (in 5 s steps when sped up, since a tick adds at most 5 s). */
    private fun refresh() {
        val k = keeper ?: return
        val target = virtualNow()
        if (fast) {
            if (lastVirtual == 0L || target < lastVirtual) lastVirtual = target
            while (lastVirtual + STEP_MS < target) {
                lastVirtual += STEP_MS
                k.tick(lastVirtual, playing && !isNap)
            }
        }
        lastVirtual = target
        val st = k.tick(target, playing && !isNap)
        _now.value = target
        setStatus(st)
    }

    private fun setStatus(st: Status) {
        val prev = _status.value
        _status.value = st
        if (prev::class != st::class) {
            // Break / done / back to playing: write it now, so a relaunch finds the same state.
            persistNow()
        }
    }

    /** The activity is started; the 1 s loop idles while it is not. */
    fun setForeground(on: Boolean) {
        foreground = on
        if (on) refresh()
    }

    /** True only while a kid puzzle screen is resumed; only then does time count. */
    fun setPlaying(on: Boolean) {
        if (playing == on) return
        refresh() // close off the stretch before the change
        playing = on
        if (!on) {
            keeper?.playStopped(virtualNow())
            persistNow() // so the gap survives the app being killed
        }
    }

    /** The screen nap is on: napping counts as not playing. */
    fun setNapping(on: Boolean) {
        if (isNap == on) return
        refresh() // close off the stretch before the change
        isNap = on
        _napping.value = on
    }

    /** Pieces still to place; [kidSettings] `finishAllowance` decides whether she may finish past the limit. */
    fun piecesLeft(n: Int) {
        piecesLeft = n
        val k = keeper ?: return
        k.piecesLeftChanged(n)
        refresh()
    }

    fun grownUpAddTime(min: Int) {
        val k = keeper ?: return
        k.addTime(min)
        refresh()
        persistNow()
    }

    fun grownUpEndBreak() {
        val k = keeper ?: return
        k.endBreak()
        refresh()
        persistNow()
    }

    /** Saves the usage if it changed since the last save. */
    fun persistNow() {
        ticksSinceSave = 0
        val u = keeper?.usage ?: return
        if (u == lastSaved) return
        lastSaved = u
        viewModelScope.launch { settings.saveUsage(u) }
    }

    companion object {
        private const val SAVE_EVERY_TICKS = 10
        private const val STEP_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { KidTimeViewModel(container.settings) }
        }
    }
}

/** The one [KidTimeViewModel] for the activity, shared by every screen. */
@Composable
fun rememberKidTimeViewModel(): KidTimeViewModel {
    val context = LocalContext.current
    val container = (context.applicationContext as PuzzlePopApp).container
    val owner = checkNotNull(LocalActivity.current as? ComponentActivity)
    return viewModel(viewModelStoreOwner = owner, factory = KidTimeViewModel.factory(container))
}
