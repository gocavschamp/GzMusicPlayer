package com.example.litcompose.data.remote

import com.squareup.moshi.Json

data class ItunesSearchResponseDto(
    @Json(name = "resultCount") val resultCount: Int,
    @Json(name = "results") val results: List<ItunesTrackDto>,
)

data class ItunesTrackDto(
    @Json(name = "trackId") val trackId: Long?,
    @Json(name = "trackName") val trackName: String?,
    @Json(name = "artistName") val artistName: String?,
    @Json(name = "trackTimeMillis") val trackTimeMillis: Long?,
    @Json(name = "previewUrl") val previewUrl: String?,
    @Json(name = "artworkUrl100") val artworkUrl100: String?,
)

