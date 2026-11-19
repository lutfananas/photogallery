package com.lutfan.photogallery.data

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val bucketId: Long,
    val bucketName: String,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val mime: String,
) {
    /** width / height, clamped to something sensible for grid tiles. */
    val aspect: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

data class Album(
    val id: Long,
    val name: String,
    val cover: Uri,
    val count: Int,
)

/** Where a viewer was opened from, so swiping stays within that set. */
sealed interface PhotoSource {
    data object All : PhotoSource
    data object Favorites : PhotoSource
    data class InAlbum(val bucketId: Long) : PhotoSource

    fun encode(): String = when (this) {
        All -> "all"
        Favorites -> "fav"
        is InAlbum -> "album_$bucketId"
    }

    companion object {
        fun decode(s: String): PhotoSource = when {
            s == "fav" -> Favorites
            s.startsWith("album_") -> InAlbum(s.removePrefix("album_").toLong())
            else -> All
        }
    }
}
