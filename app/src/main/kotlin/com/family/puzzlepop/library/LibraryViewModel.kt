package com.family.puzzlepop.library

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.family.puzzlepop.AppContainer
import com.family.puzzlepop.R
import com.family.puzzlepop.data.PhotoEntity
import com.family.puzzlepop.data.PhotoRepository
import com.family.puzzlepop.data.PuzzleEntity
import com.family.puzzlepop.data.PuzzleRepository
import com.family.puzzlepop.settings.BestsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** An unfinished puzzle together with the photo it is made from. */
data class ContinueItem(val puzzle: PuzzleEntity, val photo: PhotoEntity)

data class LibraryUi(
    /** False until the first database read, so the empty state doesn't flash up while loading. */
    val loaded: Boolean = false,
    /** Favorites first, then newest first. */
    val photos: List<PhotoEntity> = emptyList(),
    /** Newest played first. */
    val inProgress: List<ContinueItem> = emptyList(),
    val importing: Boolean = false,
    /** photoId -> best time (ms) for the preset puzzle finished most recently on that photo. */
    val bestByPhoto: Map<String, Long> = emptyMap(),
)

class LibraryViewModel(
    private val photos: PhotoRepository,
    private val puzzles: PuzzleRepository,
    bests: BestsStore,
) : ViewModel() {

    private val importCount = MutableStateFlow(0)

    val ui: StateFlow<LibraryUi> = combine(
        photos.observePhotos(),
        puzzles.observeInProgress(),
        importCount,
        combine(bests.observeAll(), bests.observeLatest()) { all, latest ->
            latest.mapNotNull { (photoId, key) -> all[key]?.let { photoId to it } }.toMap()
        },
    ) { photoList, puzzleList, importing, bestMap ->
        val byId = photoList.associateBy { it.id }
        LibraryUi(
            loaded = true,
            photos = photoList.sortedByDescending { it.favorite }, // stable: keeps newest-first inside each group
            inProgress = puzzleList.mapNotNull { p -> byId[p.photoId]?.let { ContinueItem(p, it) } },
            importing = importing > 0,
            bestByPhoto = bestMap,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUi())

    private val _messages = Channel<Int>(Channel.BUFFERED)

    /** One-off friendly messages (string resource ids) for the snackbar. */
    val messages = _messages.receiveAsFlow()

    fun thumbFile(photo: PhotoEntity): File = photos.thumbFile(photo)

    fun import(uri: Uri, deleteAfter: File? = null) {
        viewModelScope.launch {
            importCount.value += 1
            try {
                photos.importFromUri(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "import failed", e)
                _messages.trySend(R.string.library_import_failed)
            } finally {
                deleteAfter?.delete()
                importCount.value -= 1
            }
        }
    }

    /** Imports several photos one after another (parallel decodes could exhaust memory); one bad photo doesn't stop the rest. */
    fun importAll(uris: List<Uri>) {
        viewModelScope.launch {
            importCount.value += 1
            var failed = 0
            try {
                for (uri in uris) {
                    try {
                        photos.importFromUri(uri)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.e(TAG, "import failed", e)
                        failed++
                    }
                }
            } finally {
                importCount.value -= 1
            }
            if (failed > 0) _messages.trySend(R.string.library_import_failed)
        }
    }

    fun toggleFavorite(photo: PhotoEntity) {
        viewModelScope.launch { photos.setFavorite(photo.id, !photo.favorite) }
    }

    fun toggleKidApproved(photo: PhotoEntity) {
        viewModelScope.launch { photos.setKidApproved(photo.id, !photo.kidApproved) }
    }

    fun removePuzzle(id: String) {
        viewModelScope.launch { puzzles.delete(id) }
    }

    fun delete(photo: PhotoEntity) {
        viewModelScope.launch { photos.delete(photo.id) }
    }

    companion object {
        private const val TAG = "PuzzlePopLibrary"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container.photoRepository, container.puzzleRepository, container.bests) }
        }
    }
}
