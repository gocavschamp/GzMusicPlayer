package com.example.litcompose.data.db

data class CollectionWithCount(
    val collectionId: Long,
    val name: String,
    val createdAtMs: Long,
    val trackCount: Int,
)

