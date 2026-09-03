package com.joanpa.smsaviso.sms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Progress(
    val running: Boolean = false,
    val paused: Boolean = false,
    val finished: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val pending: Int = 0,
    val nextInSec: Int = 0,
    val waitingReason: String? = null,
    val lastEvent: String? = null
)

/** Canal simple servicio -> UI. */
object SenderBus {
    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    fun update(transform: (Progress) -> Progress) {
        _progress.value = transform(_progress.value)
    }

    fun reset() {
        _progress.value = Progress()
    }
}
