package com.example.litcompose.data.db

import androidx.room.TypeConverter
import com.example.litcompose.domain.model.RemoteTrackMeta
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** RemoteTrackMeta 与 JSON 字符串互转，用于 Room 持久化 */
class Converters {
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(RemoteTrackMeta::class.java)

    @TypeConverter
    fun remoteMetaToJson(meta: RemoteTrackMeta?): String? = meta?.let { adapter.toJson(it) }

    @TypeConverter
    fun jsonToRemoteMeta(json: String?): RemoteTrackMeta? =
        json?.takeIf { it.isNotBlank() }?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
}
