package com.example.litcompose.core

import android.content.Context
import androidx.room.Room
import com.example.litcompose.data.db.AppDatabase
import com.example.litcompose.data.local.MediaStoreDataSource
import com.example.litcompose.data.local.LastPlaybackStore
import com.example.litcompose.data.local.LyricsEnricher
import com.example.litcompose.data.local.TrackCache
import com.example.litcompose.data.local.TrackDownloader
import com.example.litcompose.data.remote.NetworkClient
import com.example.litcompose.data.repository.DefaultMusicRepository
import com.example.litcompose.domain.player.PlayerController
import com.example.litcompose.domain.repository.MusicRepository
import com.example.litcompose.player.Media3PlayerController

class AppContainer private constructor(
    appContext: Context,
) {
    val dispatchers: DispatcherProvider = DefaultDispatcherProvider
    val eventBus: AppEventBus = AppEventBus()

    private val database: AppDatabase =
        Room.databaseBuilder(appContext, AppDatabase::class.java, "litcompose.db")
            .addMigrations(
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .build()

    private val favoriteTrackDao = database.favoriteTrackDao()
    private val trackDao = database.trackDao()
    private val collectionDao = database.collectionDao()
    private val lyricsCacheDao = database.lyricsCacheDao()
    private val mediaStoreDataSource = MediaStoreDataSource(appContext.contentResolver)
    private val itunesApi = NetworkClient.itunesApi()
    private val cocoApi = NetworkClient.cocoApi()
    private val trackCache = TrackCache(appContext)

    /** DeepSeek AI 问答接口（Chat tab 使用） */
    val deepSeekApi = NetworkClient.deepSeekApi()

    val lyricsEnricher: LyricsEnricher = LyricsEnricher(appContext, cocoApi, lyricsCacheDao)

    val musicRepository: MusicRepository =
        DefaultMusicRepository(
            mediaStoreDataSource = mediaStoreDataSource,
            itunesApi = itunesApi,
            cocoApi = cocoApi,
            trackCache = trackCache,
            favoriteTrackDao = favoriteTrackDao,
            trackDao = trackDao,
            collectionDao = collectionDao,
        )

    val trackDownloader: TrackDownloader = TrackDownloader(appContext, cocoApi)

    val playerController: PlayerController =
        Media3PlayerController(
            context = appContext,
            lastPlaybackStore = LastPlaybackStore(appContext),
            repository = musicRepository,
        )

    companion object {
        fun create(context: Context): AppContainer = AppContainer(context.applicationContext)
    }
}
