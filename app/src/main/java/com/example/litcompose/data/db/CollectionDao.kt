package com.example.litcompose.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query(
        """
        SELECT c.collectionId, c.name, c.createdAtMs, COUNT(ct.trackId) AS trackCount
        FROM collections c
        LEFT JOIN collection_tracks ct ON c.collectionId = ct.collectionId
        GROUP BY c.collectionId
        ORDER BY c.createdAtMs DESC
        """,
    )
    fun observeCollectionsWithCount(): Flow<List<CollectionWithCount>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CollectionEntity): Long

    @Query("SELECT * FROM collections WHERE collectionId = :collectionId LIMIT 1")
    suspend fun getById(collectionId: Long): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCrossRef(ref: CollectionTrackCrossRef)

    @Query("DELETE FROM collection_tracks WHERE collectionId = :collectionId AND trackId = :trackId")
    suspend fun removeTrack(collectionId: Long, trackId: String)

    @Query(
        """
        SELECT t.*
        FROM tracks t
        INNER JOIN collection_tracks ct ON ct.trackId = t.trackId
        WHERE ct.collectionId = :collectionId
        ORDER BY ct.position ASC, ct.addedAtMs DESC
        """,
    )
    fun observeTracksInCollection(collectionId: Long): Flow<List<TrackEntity>>

    /** 更新歌单内某首歌的排序位置 */
    @Query(
        "UPDATE collection_tracks SET position = :position WHERE collectionId = :collectionId AND trackId = :trackId",
    )
    suspend fun updateTrackPosition(collectionId: Long, trackId: String, position: Int)

    /** 所有歌单中的全部歌曲（跨歌单按 trackId 去重），供“一键获取歌词封面”批量任务使用 */
    @Query(
        """
        SELECT DISTINCT t.*
        FROM tracks t
        INNER JOIN collection_tracks ct ON ct.trackId = t.trackId
        ORDER BY t.title
        """,
    )
    suspend fun getAllTracks(): List<TrackEntity>
}

