package com.ashkb.app.ui

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.2.7（批次 14 / R6）：**没有订阅者期间的投递不能丢**。
 *
 * 老实现是 `MutableSharedFlow(replay = 0, extraBufferCapacity = 8)` + `tryEmit`：
 * `replay = 0` 的缓冲**只对已在场的订阅者生效**——没人订阅时 `tryEmit` 的值直接消失，
 * 而唯一的订阅者（`AppShell` 的首帧 `LaunchedEffect`）恰恰是最后一个到场的。
 * 于是启动路径上那些"秒失败"的提示（备份校验没过、恢复口令不对）**静默消失**，
 * 用户看到的是"点了没反应"——正是这个总线当初要消灭的现象。
 *
 * 这里刻意**先投递、后订阅**，把那个时间窗钉死。`Channel` 的缓冲与订阅者是否在场无关，
 * 所以本用例在 `replay = 0` 的 SharedFlow 下会一直收不到（`withTimeout` 失败），
 * 换成 `Channel` 后立刻通过。
 *
 * `GlobalMessages` 是纯 kotlinx.coroutines 的 object（无 Android 依赖），故不需要 Robolectric。
 */
class GlobalMessagesTest {

    @Test
    fun `没有订阅者时投递的消息不会丢`() = runBlocking {
        val sentinel = "批次14-R6-投递时还没有订阅者"
        GlobalMessages.post(sentinel)

        val got = withTimeout(3_000) {
            GlobalMessages.events.filter { it.endsWith("投递时还没有订阅者") }.first()
        }

        assertEquals("投递时没有 collector，提示被静默丢弃", sentinel, got)
    }
}
