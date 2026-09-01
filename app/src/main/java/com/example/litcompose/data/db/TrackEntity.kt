package com.example.litcompose.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.litcompose.domain.model.RemoteTrackMeta

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val sourceUri: String,
    val artworkUrl: String?,
    val isLocal: Boolean,
    val remote: RemoteTrackMeta? = null,
)

