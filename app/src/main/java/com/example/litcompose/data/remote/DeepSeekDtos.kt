package com.example.litcompose.data.remote

import com.squareup.moshi.Json

/** DeepSeek 对话消息（OpenAI 兼容格式） */
data class DeepSeekMessage(
    val role: String,
    val content: String,
)

data class DeepSeekChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val stream: Boolean = false,
)

data class DeepSeekChatResponse(
    val choices: List<DeepSeekChoice> = emptyList(),
)

data class DeepSeekChoice(
    val message: DeepSeekMessage? = null,
    @Json(name = "finish_reason") val finishReason: String? = null,
)
