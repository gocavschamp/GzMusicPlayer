package com.example.litcompose.data.local

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import com.example.litcompose.domain.model.Track

class MediaStoreDataSource(
    private val contentResolver: ContentResolver,
) {
    fun getLocalTracks(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val result = ArrayList<Track>(128)
        contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol).orEmpty()
                val artist = cursor.getString(artistCol).orEmpty()
                val durationMs = cursor.getLong(durationCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()

                result += Track(
                    id = "local:$id",
                    title = title,
                    artist = artist,
                    durationMs = durationMs,
                    sourceUri = uri,
                    artworkUrl = null,
                    isLocal = true,
                )
            }
        }
        return result
    }
}

