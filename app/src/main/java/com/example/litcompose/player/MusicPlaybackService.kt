package com.example.litcompose.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.litcompose.LitComposeApp

/**
 * 通知栏播放服务：包裹全局唯一的 ExoPlayer，展示带当前歌曲信息与
 * 播放控制（暂停 / 上一曲 / 下一曲）的通知。
 *
 * 通过重写 [onUpdateNotification] 完全接管通知的构建与发布：
 * - 播放状态 / 曲目切换时 Media3 会回调该方法，用同 ID 持续刷新通知；
 * - 通知按钮点击通过自定义 Service Action 在 [onStartCommand] 中转发给 player。
 *
 * 注意：player 的生命周期由 AppContainer 中的 Media3PlayerController 管理，
 * 服务销毁时只释放 MediaSession，不释放 player，避免 UI 侧继续操作已释放的播放器。
 */
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    /**
     * 耳机断开（蓝牙/有线）时系统会发 ACTION_AUDIO_BECOMING_NOISY，
     * 兜底暂停播放并打日志，避免音频从耳机切到外放。
     * （ExoPlayer 的 setHandleAudioBecomingNoisy 也会处理，这里双保险）
     */
    private val becomingNoisyReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    val player = mediaSession?.player
                    Log.d(TAG, "onReceive: ACTION_AUDIO_BECOMING_NOISY 耳机断开 -> 自动暂停 isPlaying=${player?.isPlaying}")
                    player?.pause()
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        val app = application as LitComposeApp
        val player = (app.appContainer.playerController as Media3PlayerController).player
        mediaSession = MediaSession.Builder(this, player).build()
        // 系统广播需动态注册；Android 13+ 要求显式声明 receiver 导出标志
        ContextCompat.registerReceiver(
            this,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_EXPORTED,
        )
        Log.d(TAG, "onCreate: session built, playerState=${player.playbackState} isPlaying=${player.isPlaying}")
        Log.d(
            TAG,
            "onCreate: notifEnabled=${NotificationManagerCompat.from(this).areNotificationsEnabled()} " +
                "channelImportance=${channelImportance()}",
        )
        // 立即进入前台：startForegroundService 必须在 5 秒内调用 startForeground()，
        // 不能依赖 Media3 的状态驱动（网络慢/时序竞态时可能超时崩溃）。
        // 此时直接展示当前播放状态的真实通知，避免先弹占位再替换。
        mediaSession?.let { updateNotification(it, startInForegroundRequired = true) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // 播放中时保留前台服务与通知，允许后台继续播放；否则停止服务
        if (player == null || !player.isPlaying) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        Log.d(
            TAG,
            "onStartCommand: action=${intent?.action} startId=$startId " +
                "playerState=${player?.playbackState} isPlaying=${player?.isPlaying}",
        )
        when (intent?.action) {
            ACTION_PREVIOUS -> {
                Log.d(TAG, "onStartCommand: ACTION_PREVIOUS -> seekToPrevious")
                player?.seekToPreviousMediaItem()
            }

            ACTION_PLAY_PAUSE -> {
                Log.d(TAG, "onStartCommand: ACTION_PLAY_PAUSE -> toggle (playing=${player?.isPlaying})")
                if (player != null) {
                    if (player.isPlaying) player.pause() else player.play()
                }
            }

            ACTION_NEXT -> {
                Log.d(TAG, "onStartCommand: ACTION_NEXT -> seekToNext")
                player?.seekToNextMediaItem()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: releasing media session")
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    /** Media3 在播放状态、曲目、元数据变化时回调，用于持续刷新通知 */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val player = session.player
        Log.d(
            TAG,
            "onUpdateNotification: startInForeground=$startInForegroundRequired " +
                "playerState=${player.playbackState} isPlaying=${player.isPlaying} " +
                "title=${player.mediaMetadata.title} artist=${player.mediaMetadata.artist} " +
                "hasArtwork=${player.mediaMetadata.artworkData != null}",
        )
        updateNotification(session, startInForegroundRequired)
    }

    private fun updateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val notification = buildMediaNotification(session)
        Log.d(
            TAG,
            "updateNotification: posting via ${if (startInForegroundRequired) "startForeground" else "notify"} " +
                "id=$NOTIFICATION_ID channel=$CHANNEL_ID",
        )
        if (startInForegroundRequired) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildMediaNotification(session: MediaSession): Notification {
        val player = session.player
        val metadata = player.mediaMetadata
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "轻听"
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() } ?: "未知歌手"
        val artwork = metadata.artworkData?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

        createChannel()

        val contentIntent: PendingIntent? =
            session.sessionActivity
                ?: packageManager
                    .getLaunchIntentForPackage(packageName)
                    ?.let {
                        PendingIntent.getActivity(
                            this,
                            0,
                            it,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    }
        val actionPendingIntent =
            { action: String, requestCode: Int ->
                PendingIntent.getService(
                    this,
                    requestCode,
                    Intent(this, MusicPlaybackService::class.java).setAction(action),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(artwork)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "上一曲",
                    actionPendingIntent(ACTION_PREVIOUS, 1),
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    if (player.isPlaying) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    },
                    if (player.isPlaying) "暂停" else "播放",
                    actionPendingIntent(ACTION_PLAY_PAUSE, 2),
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "下一曲",
                    actionPendingIntent(ACTION_NEXT, 3),
                ),
            )
            .build()
            .also {
                Log.d(
                    TAG,
                    "buildMediaNotification: title=$title artist=$artist " +
                        "artwork=${artwork != null} isPlaying=${player.isPlaying} " +
                        "state=${player.playbackState} sessionActivity=${session.sessionActivity != null} " +
                        "actions=${it.actions.size}",
                )
            }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 使用 IMPORTANCE_DEFAULT：部分厂商 ROM（如 ColorOS）会隐藏 LOW 重要性通知，
            // 且 channel 重要性创建后不可修改，因此采用独立 channel id
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "播放控制",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** 当前 channel 的实际重要性（-1=不存在/被删） */
    private fun channelImportance(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0
        return getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
            ?.importance
            ?: -1
    }

    private companion object {
        const val TAG = "LitComposeNotif"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "playback_media_controls"
        const val ACTION_PREVIOUS = "com.example.litcompose.action.PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.example.litcompose.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.litcompose.action.NEXT"
    }
}
