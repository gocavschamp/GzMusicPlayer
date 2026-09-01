package com.example.litcompose.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litcompose.core.DispatcherProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel<S : Any, E : Any>(
    initialState: S,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    protected val mutableState: MutableStateFlow<S> = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState

    private val effectChannel: Channel<E> = Channel(Channel.BUFFERED)
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    protected fun updateState(reducer: (S) -> S) {
        mutableState.value = reducer(mutableState.value)
    }

    protected fun emitEffect(effect: E) {
        effectChannel.trySend(effect)
    }

    protected fun launchIO(block: suspend () -> Unit) {
        viewModelScope.launch(dispatchers.io) { block() }
    }

    protected fun launchMain(block: suspend () -> Unit) {
        viewModelScope.launch(dispatchers.main) { block() }
    }
}

