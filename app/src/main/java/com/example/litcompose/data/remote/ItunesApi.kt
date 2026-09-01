package com.example.litcompose.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesApi {
    @GET("search")
    suspend fun searchSongs(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 30,
    ): ItunesSearchResponseDto
}

