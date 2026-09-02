package com.example.litcompose.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/** DeepSeek 对话补全接口（OpenAI 兼容协议） */
interface DeepSeekApi {
    @POST("chat/completions")
    suspend fun chatCompletion(@Body request: DeepSeekChatRequest): DeepSeekChatResponse
}
