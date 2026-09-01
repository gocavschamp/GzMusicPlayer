package com.example.litcompose.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE trackId = :trackId LIMIT 1")
    suspend fun getById(trackId: String): TrackEntity?
}

