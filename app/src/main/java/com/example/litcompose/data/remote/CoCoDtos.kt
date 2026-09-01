package com.example.litcompose.data.remote

import com.squareup.moshi.Json

/** COCO 音乐下载站搜索响应 */
data class CoCoSearchResponse(
    @Json(name = "items") val items: List<CoCoTrackDto> = emptyList(),
)

/** COCO 搜索结果条目（字段都可能缺失，解析时容错） */
data class CoCoTrackDto(
    val id: String = "",
    val title: String = "",
    val artist: String? = null,
    val album: String? = null,
    val cover: String? = null,
    val duration: String? = null,
    val provider: String = "",
    val extra: Map<String, Any?>? = null,
)

/** 播放链接解析响应 */
data class CoCoUrlResponse(
    val url: String?,
    val type: String?,
    val bitrate: String?,
    val cover: String?,
)

/** 下载接口响应（可能被禁用，回退使用 url 字段） */
data class CoCoDownloadResponse(
    val error: String?,
    val url: String?,
)

/** 歌词响应 */
data class CoCoLyricResponse(
    val songid: String?,
    val provider: String?,
    val lines: List<CoCoLyricLineDto> = emptyList(),
)

data class CoCoLyricLineDto(
    val time: Double,
    val text: String,
)
