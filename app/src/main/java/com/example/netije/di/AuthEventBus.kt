package com.example.netije.di

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AuthEventBus {
    private val _sessionExpired = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    fun emitSessionExpired() {
        _sessionExpired.tryEmit(Unit)
    }
}
