package com.lutfan.photogallery

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lutfan.photogallery.data.Album
import com.lutfan.photogallery.data.MediaRepository
import com.lutfan.photogallery.data.Photo
import com.lutfan.photogallery.data.PhotoSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    val repo = MediaRepository(app)
    private val prefs = app.getSharedPreferences("gallery", Context.MODE_PRIVATE)

    var photos by mutableStateOf<List<Photo>>(emptyList())
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var favorites by mutableStateOf(
        prefs.getStringSet(KEY_FAV, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    )
        private set
    var loading by mutableStateOf(true)
        private set

    /** Multi-select (long-press a photo). Empty = not selecting. */
    var selection by mutableStateOf<Set<Long>>(emptySet())
        private set
    val selecting get() = selection.isNotEmpty()

    fun toggleSelect(id: Long) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun clearSelection() {
        if (selection.isNotEmpty()) selection = emptySet()
    }

    /** Selected photos that still exist, in gallery order. */
    fun selectedPhotos(): List<Photo> = photos.filter { it.id in selection }

    fun setFavorite(ids: Collection<Long>, favorite: Boolean) {
        favorites = if (favorite) favorites + ids else favorites - ids.toSet()
        prefs.edit { putStringSet(KEY_FAV, favorites.map { it.toString() }.toSet()) }
    }

    private var refreshJob: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh(debounce = true)
    }

    init {
        app.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
        )
    }

    fun refresh(debounce: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (debounce) delay(400)
            reloadNow()
        }
    }

    suspend fun reloadNow() {
        val list = runCatching { repo.loadPhotos() }.getOrDefault(emptyList())
        photos = list
        if (selection.isNotEmpty()) {
            val alive = list.mapTo(HashSet()) { it.id }
            selection = selection.filterTo(HashSet()) { it in alive }
        }
        albums = list.groupBy { it.bucketId }
            .map { (id, items) -> Album(id, items.first().bucketName, items.first().uri, items.size) }
            .sortedByDescending { it.count }
        loading = false
    }

    fun photo(id: Long): Photo? = photos.firstOrNull { it.id == id }

    fun photosFor(source: PhotoSource): List<Photo> = when (source) {
        PhotoSource.All -> photos
        PhotoSource.Favorites -> photos.filter { it.id in favorites }
        is PhotoSource.InAlbum -> photos.filter { it.bucketId == source.bucketId }
    }

    fun isFavorite(id: Long) = id in favorites

    fun toggleFavorite(id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit { putStringSet(KEY_FAV, favorites.map { it.toString() }.toSet()) }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    private companion object {
        const val KEY_FAV = "favorites"
    }
}
