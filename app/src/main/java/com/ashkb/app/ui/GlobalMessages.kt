package com.ashkb.app.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 全局一次性消息总线。
 *
 * ViewModel 的"提示 / 操作结果"类消息经此投递到 `SnackbarHost`，
 * 替代此前"每个操作都要点一次知道了"的阻塞式 AlertDialog。
 * Snackbar 自带无障碍播报，也顺带修掉备份恢复结果完全静默的问题。
 *
 * ### v1.2.7（批次 14 / R6）：由 `MutableSharedFlow(replay = 0)` 改为 `Channel`
 *
 * `replay = 0` 的 SharedFlow **只在有订阅者时有缓冲**：订阅者加入之前的 `tryEmit` 直接丢掉，
 * 且 `tryEmit` 返回的 `false` 没人看（原实现连返回值都没接），失败是无声的。
 * 而唯一的订阅者是 `AppShell` 里的 `LaunchedEffect`——它在首帧之后才挂上，
 * ViewModel 完全可能在那之前就投递完（启动即失败的路径），消息**静默消失**，
 * 用户看到的是"点了没反应"，正是这个总线当初要消灭的那种现象。
 *
 * `Channel` 的缓冲与订阅者是否存在**无关**：值先落进缓冲，后来的 `receiveAsFlow()`
 * 逐条取走——既不丢（缓冲未满），也不像 `replay = 1` 那样在重组重订阅时把旧提示再播一次。
 * 容量与溢出策略沿用原来那两个数（8 / DROP_OLDEST）。
 */
object GlobalMessages {
    private val _channel = Channel<String>(
        capacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<String> = _channel.receiveAsFlow()

    /**
     * 投递一条提示。约定：**单一消费端**（`AppShell` 的 SnackbarHost）。
     *
     * 用 `trySend` 而不是 `send`：调用点分散在 Composable 回调与协程里，其中不少在主线程上，
     * 这里绝不能挂起等消费者。缓冲满时丢最旧的一条——提示是锦上添花，不能反过来阻塞操作。
     */
    fun post(text: String) {
        _channel.trySend(text)
    }
}
