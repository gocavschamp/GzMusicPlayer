package com.example.litcompose.domain.model

/** 需要二次解析播放链接的远程歌曲元信息（COCO 渠道） */
data class RemoteTrackMeta(
    val provider: String,
    val songId: String,
    val extraJson: String,
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val sourceUri: String,
    // 默认 null：Moshi 序列化 null 时不会写出该键，反序列化需默认值兜底，
    // 否则历史存档（无该字段）会导致整个列表解析失败，播放条无法恢复
    val artworkUrl: String? = null,
    val isLocal: Boolean,
    val remote: RemoteTrackMeta? = null,
)

/** 歌词行（时间戳为毫秒） */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

