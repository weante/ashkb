package com.ashkb.app.ui.components

import android.view.Window
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1.2.11（缺陷工单 B06）：`SecureWindow` 只还原本 effect 拥有的 FLAG_SECURE。
 *
 * 用真 `Window`（Robolectric 造一个 Activity 的 window）：这条缺陷的全部内容就是
 * 「窗口上那个标志位到底有没有被清掉」，纯逻辑替身证明不了。`Window.attributes.flags`
 * 是 Robolectric 下可读可写的真实字段。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SecureFlagScopeTest {

    private val secure = WindowManager.LayoutParams.FLAG_SECURE

    private fun newWindow(): Window =
        // 必须走 buildActivity().setup()：直接 `Activity()` 在 Robolectric 下拿不到 window
        // （`Window creation failed!`），因为它没有 attach 到 ActivityThread。
        org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java)
            .setup()
            .get()
            .window

    private fun Window.hasSecure(): Boolean = (attributes.flags and secure) != 0

    @Test
    fun `原本没有保护的窗口在释放后恢复为可截屏`() {
        val w = newWindow()
        assertFalse("前置：新窗口默认没有 FLAG_SECURE", w.hasSecure())
        val scope = SecureFlagScope()
        scope.acquire(w)
        assertTrue("acquire 后必须加上保护", w.hasSecure())
        scope.release()
        assertFalse("本 effect 加的标志，销毁时应当清掉", w.hasSecure())
    }

    @Test
    fun `父页面已有的保护不会被弹窗销毁解除`() {
        // 模拟：备份页自己给 Activity 窗口加了保护
        val activityWindow = newWindow()
        activityWindow.addFlags(secure)

        // 弹窗打开：它的 effect 同时 acquire Activity 窗口与自己的窗口
        val dialogWindow = newWindow()
        val scope = SecureFlagScope()
        scope.acquire(activityWindow)
        scope.acquire(dialogWindow)

        // 弹窗关闭：effect 销毁
        scope.release()

        assertTrue(
            "B06 的核心：弹窗从没加过 Activity 的保护，就不能在销毁时把它解掉——" +
                "否则关掉恢复码弹窗后，备份页立刻变成可截屏",
            activityWindow.hasSecure(),
        )
        assertFalse("弹窗自己的标志应当清掉", dialogWindow.hasSecure())
    }

    @Test
    fun `重复 acquire 同一窗口不会丢掉原始状态`() {
        val w = newWindow()
        val scope = SecureFlagScope()
        scope.acquire(w)
        scope.acquire(w) // 第二次：此时标志已由本 effect 加上
        scope.release()
        assertFalse(
            "若第二次 acquire 把「已被自己改过」的状态当成本来就有，保护会泄漏到进程结束",
            w.hasSecure(),
        )
    }

    @Test
    fun `原本就有保护的窗口释放后仍然受保护`() {
        val w = newWindow()
        w.addFlags(secure)
        val scope = SecureFlagScope()
        scope.acquire(w)
        scope.release()
        assertTrue("原本就有的保护不该被清除", w.hasSecure())
        assertEquals(secure, w.attributes.flags and secure)
    }
}
