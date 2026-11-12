package com.family.puzzlepop.kid

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.family.puzzlepop.AppContainer
import com.family.puzzlepop.data.PhotoEntity
import com.family.puzzlepop.data.PhotoRepository
import com.family.puzzlepop.data.PuzzleEntity
import com.family.puzzlepop.data.PuzzleRepository
import com.family.puzzlepop.engine.GridSizer
import com.family.puzzlepop.engine.PuzzleSpec
import com.family.puzzlepop.engine.options.PlayOptions
import com.family.puzzlepop.play.createPuzzle
import com.family.puzzlepop.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

/** An unfinished puzzle made from a kid-approved photo. */
data class KidContinueItem(val puzzle: PuzzleEntity, val photo: PhotoEntity)

data class KidHomeUi(
    val loaded: Boolean = false,
    val photos: List<PhotoEntity> = emptyList(),
    val inProgress: List<KidContinueItem> = emptyList(),
    /** The three puzzle sizes: the grown-up's min, the middle, and max piece counts. */
    val small: Int = 12,
    val medium: Int = 30,
    val big: Int = 48,
    val creating: Boolean = false,
    val failed: Boolean = false,
)

class KidHomeViewModel(
    private val photos: PhotoRepository,
    private val puzzles: PuzzleRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val creating = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)

    val ui: StateFlow<KidHomeUi> = combine(
        photos.observeKidPhotos(),
        puzzles.observeInProgress(),
        settings.kidSettings,
        creating,
        failed,
    ) { kidPhotos, inProgress, s, busy, fail ->
        val byId = kidPhotos.associateBy { it.id }
        KidHomeUi(
            loaded = true,
            photos = kidPhotos,
            inProgress = inProgress.mapNotNull { p ->
                byId[p.photoId]?.takeIf { photo ->
                    p.pieceCount <= GridSizer.choose(s.maxPieces, photo.width, photo.height).count
                }?.let { KidContinueItem(p, it) }
            },
            small = s.minPieces,
            medium = (s.minPieces + s.maxPieces) / 2,
            big = s.maxPieces,
            creating = busy,
            failed = fail,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KidHomeUi())

    private val _created = Channel<String>(Channel.BUFFERED)

    /** The id of the puzzle just made; the screen opens it in kid play. */
    val created = _created.receiveAsFlow()

    fun thumbFile(photo: PhotoEntity): File = photos.thumbFile(photo)

    /** Makes a puzzle in the grown-up's chosen kid shape of [count] pieces, scattered on the table (no tray), and reports it on [created]. */
    fun start(photo: PhotoEntity, count: Int) {
        if (creating.value) return
        creating.value = true
        failed.value = false
        viewModelScope.launch {
            try {
                // Resolved once, with a fresh random (not the puzzle seed); the spec stores the result.
                val style = settings.kidSettings.first().kidShape.resolve(Random.Default)
                val spec = PuzzleSpec(photo.width, photo.height, count, style, System.nanoTime())
                _created.send(createPuzzle(puzzles, photo.id, spec, PlayOptions(helper = true, boxPicture = true), kid = true))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "creating kid puzzle failed", e)
                failed.value = true
            } finally {
                creating.value = false
            }
        }
    }

    fun clearFailure() {
        failed.value = false
    }

    companion object {
        private const val TAG = "PuzzlePopKidHome"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { KidHomeViewModel(container.photoRepository, container.puzzleRepository, container.settings) }
        }
    }
}
