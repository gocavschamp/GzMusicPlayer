package com.example.litcompose.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litcompose.data.remote.DeepSeekApi
import com.example.litcompose.data.remote.DeepSeekChatRequest
import com.example.litcompose.data.remote.DeepSeekMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** AI 对话 UI 消息（role: user / assistant） */
data class ChatMessage(
    val role: String,
    val content: String,
) {
    val isUser: Boolean get() = role == "user"
}

class ChatViewModel(
    private val deepSeekApi: DeepSeekApi,
) : ViewModel() {
    private val _messages =
        MutableStateFlow(
            listOf(ChatMessage(role = "assistant", content = "你好！我是 AI 助手，有什么可以帮你？")),
        )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    fun send(rawText: String) {
        val input = rawText.trim()
        if (input.isEmpty() || _sending.value) return
        _messages.update { it + ChatMessage(role = "user", content = input) }
        _sending.value = true
        viewModelScope.launch {
            runCatching {
                deepSeekApi.chatCompletion(
                    DeepSeekChatRequest(
                        model = MODEL,
                        // 携带完整上下文（含刚追加的用户消息）
                        messages = _messages.value.map { DeepSeekMessage(role = it.role, content = it.content) },
                    ),
                )
            }.onSuccess { resp ->
                val reply = resp.choices.firstOrNull()?.message?.content?.trim()
                if (!reply.isNullOrEmpty()) {
                    _messages.update { it + ChatMessage(role = "assistant", content = reply) }
                } else {
                    _messages.update { it + ChatMessage(role = "assistant", content = "（没有收到回复，请稍后重试）") }
                }
            }.onFailure { e ->
                _messages.update {
                    it + ChatMessage(role = "assistant", content = "请求失败：${e.message ?: "未知错误"}，请检查网络后重试")
                }
            }
            _sending.value = false
        }
    }

    fun clear() {
        _messages.value =
            listOf(ChatMessage(role = "assistant", content = "你好！我是 AI 助手，有什么可以帮你？"))
    }

    companion object {
        private const val MODEL = "deepseek-v4-flash"
    }
}
