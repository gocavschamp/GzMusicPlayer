package com.example.litcompose.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 歌词/封面补全缓存：记录某首歌是否已通过接口匹配并保存歌词与封面。
 * - linesJson：List<LyricLine> 的 Moshi JSON；"[]" 表示匹配成功但接口无歌词
 * - artworkPath：已保存到应用私有目录的封面 file:// URI，可为 null
 * trackId 与 Track.id 一一对应（本地歌曲 "local:x"、远程 "coco:p:id" / "remote:x"）
 */
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val trackId: String,
    val linesJson: String?,
    val artworkPath: String?,
    val updatedAtMs: Long,
)
