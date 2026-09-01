package com.example.litcompose.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val collectionId: Long = 0L,
    val name: String,
    val createdAtMs: Long,
)

