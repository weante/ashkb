package com.ashkb.app.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 全局一次性消息总线。
 *
 * ViewModel 的"提示 / 操作结果"类消息经此投递到 `SnackbarHost`，
 * 替代此前"每个操作都要点一次知道了"的阻塞式 AlertDialog。
 * Snackbar 自带无障碍播报，也顺带修掉备份恢复结果完全静默的问题。
 */
object GlobalMessages {
    private val _events = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<String> = _events

    fun post(text: String) {
        _events.tryEmit(text)
    }
}
