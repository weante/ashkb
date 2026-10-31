package com.ashkb.app.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未发布（审查报告 §十 第 4 问）：锁屏只承认「这张卡存在」，不印任何用户数据。
 *
 * 为什么用源码静态断言而不是 Robolectric 起通知再读 `Notification.publicVersion`：
 * 这里真正要钉住的是**取舍本身**——「锁屏上露出的那版只含两条固定文案」。
 * 一次 `build()` 之后能断言的是对象状态，而下一个人把 `setPublicVersion` 删掉、
 * 或把 `VISIBILITY_PRIVATE` 改回 `PUBLIC`，对象状态都还是「能构建」。
 * 静态断言直接盯住这两处字面量与 publicVersion 的内容来源，改哪一处都会红。
 *
 * 三条断言分别对应三件会出人命后果的事：
 * ① 主通知必须声明 PRIVATE（否则锁屏按通道默认走）；
 * ② 必须挂上 publicVersion（不挂的话部分 ROM 会直接显示主通知全文）；
 * ③ publicVersion 里**不得**出现任何用户数据行（血型 / 诊断 / 过敏 / 用药 / 家属 / 医生）。
 */
class LockscreenPrivacyTest {

    private fun grep(): String = readSource("app/src/main/java/com/ashkb/app/reminder/NotificationHelper.kt")

    @Test
    fun `锁屏急救卡主通知声明为 PRIVATE`() {
        val src = grep()
        assertTrue(
            "主通知必须 setVisibility(VISIBILITY_PRIVATE)：PUBLIC 会把诊断与家属电话明文印在锁屏上",
            src.contains(".setVisibility(NotificationCompat.VISIBILITY_PRIVATE)"),
        )
        assertFalse(
            "急救卡不得再出现 VISIBILITY_PUBLIC——注释里提到它没关系，但代码里不行",
            src.contains(".setVisibility(NotificationCompat.VISIBILITY_PUBLIC)"),
        )
    }

    @Test
    fun `锁屏急救卡必须挂 publicVersion`() {
        assertTrue(
            "没有 setPublicVersion 时，部分 ROM 会在锁屏直接渲染主通知全文",
            grep().contains(".setPublicVersion("),
        )
    }

    @Test
    fun `publicVersion 只含两条固定文案，不含任何用户数据行`() {
        val src = grep()
        val block = src.substringAfter("private fun lockscreenPublicVersion(", "")
            .substringBefore("\n    }\n")
        assertTrue("publicVersion 构建块没找到——测试已失效，请同步更新", block.isNotBlank())

        // `lines` / `body` 是主通知里含诊断、过敏、家属电话的正文变量。
        // 注意不能粗查 "title"：`dom_lock_public_title` 这个资源名本身就含 title，
        // 那样断言会自己把自己绊倒（第一版就踩过）。
        for (carrier in listOf("lines", "body")) {
            assertFalse(
                "publicVersion 不得引用 `$carrier`（那是含诊断/过敏/家属电话的正文）",
                block.contains("`$carrier`") || block.contains("($carrier)") ||
                    block.contains("$carrier.") || block.contains("= $carrier"),
            )
        }
        // 这五条资源是把用户数据搬上锁屏的唯一途径，publicVersion 一条都不许出现。
        for (dataRes in listOf(
            "dom_lock_blood",
            "dom_lock_allergy",
            "dom_lock_meds",
            "dom_lock_family",
            "dom_lock_doctor",
        )) {
            assertFalse("publicVersion 不得引用 $dataRes（那是用户数据行）", block.contains(dataRes))
        }
        assertTrue(
            "锁屏那版必须用不含数据的固定标题",
            block.contains("R.string.dom_lock_public_title"),
        )
        assertTrue(
            "锁屏那版必须用不含数据的固定说明",
            block.contains("R.string.dom_lock_public_text"),
        )
    }

    @Test
    fun `两条锁屏文案本身不含任何占位符`() {
        // 带占位符就意味着将来会被塞进用户数据——这是本条隐私取舍唯一的防线。
        for (file in listOf("app/src/main/res/values/strings_domain.xml", "app/src/main/res/values-en/strings_domain.xml")) {
            val xml = readSource(file)
            for (name in listOf("dom_lock_public_title", "dom_lock_public_text")) {
                val value = xml.substringAfter("<string name=\"$name\">", "")
                    .substringBefore("</string>")
                assertTrue("$file 缺少 $name", value.isNotBlank())
                assertFalse("$file 的 $name 不得含占位符：$value", value.contains("%"))
            }
        }
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        return File("../")
    }

    private fun readSource(relative: String): String = File(repoRoot(), relative).readText()
}
