package com.example.litcompose.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import okhttp3.ResponseBody

/** COCO 支持的搜索引擎 */
object CoCoProviders {
    data class Provider(
        val id: String,
        val name: String,
    )

    val all: List<Provider> =
        listOf(
            Provider("netease", "云音乐"),
            Provider("qq", "QQ音乐（VKeys）"),
            Provider("kugou", "酷狗音乐（90svip）"),
            Provider("gequhai", "歌曲海"),
            Provider("bodian", "波点"),
            Provider("qqmp3", "QQMP3"),
            Provider("migu", "咪咕"),
            Provider("livepoo", "力音"),
            Provider("jianbin-netease", "煎饼-1"),
            Provider("jianbin-qq", "煎饼-2"),
            Provider("jianbin-kugou", "煎饼-3"),
            Provider("jianbin-kuwo", "煎饼-4"),
        )
}

/** COCO 音乐下载站（cocodownloader.markqq.com）接口 */
interface CoCoApi {
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("provider") provider: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): CoCoSearchResponse

    /** 解析播放链接 */
    @GET("api/url")
    suspend fun resolveUrl(
        @Query("id") id: String,
        @Query("provider") provider: String,
        @Query("extra") extra: String,
    ): CoCoUrlResponse

    /** 歌词 */
    @GET("api/lyric")
    suspend fun lyric(
        @Query("id") id: String,
        @Query("provider") provider: String,
        @Query("extra") extra: String,
    ): CoCoLyricResponse

    /** 下载音频（可能被服务端禁用，需回退 url） */
    @Streaming
    @GET("api/download")
    suspend fun download(
        @Query("id") id: String,
        @Query("provider") provider: String,
        @Query("filename") filename: String,
        @Query("extra") extra: String,
    ): Response<ResponseBody>
}
