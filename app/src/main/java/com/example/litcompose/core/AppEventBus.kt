package com.example.litcompose.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

sealed interface AppEvent {
    data class ShowSnackbar(val message: String) : AppEvent
}

class AppEventBus {
    private val _events =
        MutableSharedFlow<AppEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val events: SharedFlow<AppEvent> = _events

    fun tryEmit(event: AppEvent) {
        _events.tryEmit(event)
    }
}

