package com.family.puzzlepop.newpuzzle

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.family.puzzlepop.AppContainer
import com.family.puzzlepop.data.PhotoEntity
import com.family.puzzlepop.data.PuzzleRepository
import com.family.puzzlepop.engine.PieceStyle
import com.family.puzzlepop.engine.PuzzleSpec
import com.family.puzzlepop.engine.options.PlayOptions
import com.family.puzzlepop.play.createPuzzle
import com.family.puzzlepop.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The custom choices remembered for next time: the options, plus piece count and style when known. */
@kotlinx.serialization.Serializable
data class LastCustom(val options: PlayOptions, val count: Int? = null, val style: PieceStyle? = null)

class NewPuzzleViewModel(
    private val puzzles: PuzzleRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** The preset chip used last time ("CUSTOM", a Preset name) or null; read once when the sheet opens. */
    suspend fun lastPreset(): String? = settings.lastPreset.first()

    /** The custom options used last time, or null (never, or unreadable). */
    suspend fun lastCustom(): LastCustom? = try {
        settings.lastCustom.first()?.let {
            try {
                json.decodeFromString<LastCustom>(it)
            } catch (e: Exception) {
                // Older saves held just the options.
                LastCustom(json.decodeFromString<PlayOptions>(it), null, null)
            }
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { null }


    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private val _created = Channel<String>(Channel.BUFFERED)

    /** The id of each puzzle that was just made; the screen navigates to it. */
    val created = _created.receiveAsFlow()

    /** Makes a puzzle from [photo]. Does nothing while another one is already being made (double taps). */
    fun start(photo: PhotoEntity, targetCount: Int, style: PieceStyle, options: PlayOptions) {
        if (_creating.value) return
        _creating.value = true
        _failed.value = false
        viewModelScope.launch {
            try {
                val spec = PuzzleSpec(photo.width, photo.height, targetCount, style, System.nanoTime())
                settings.setLastPreset(options.preset?.name ?: "CUSTOM")
                if (options.preset == null) settings.setLastCustom(json.encodeToString(LastCustom(options, targetCount, style)))
                _created.send(createPuzzle(puzzles, photo.id, spec, options))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "creating puzzle failed", e)
                _failed.value = true
            } finally {
                _creating.value = false
            }
        }
    }

    fun clearFailure() {
        _failed.value = false
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private const val TAG = "PuzzlePopNewPuzzle"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { NewPuzzleViewModel(container.puzzleRepository, container.settings) }
        }
    }
}
