package com.example.litcompose.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "collection_tracks",
    primaryKeys = ["collectionId", "trackId"],
    indices = [
        Index("trackId"),
    ],
)
data class CollectionTrackCrossRef(
    val collectionId: Long,
    val trackId: String,
    val addedAtMs: Long,
    /** 歌单内排序位置（手动排序用，默认按加入时间） */
    val position: Int = 0,
)

