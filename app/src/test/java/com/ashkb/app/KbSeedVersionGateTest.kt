package com.ashkb.app

import com.ashkb.app.AshkbApplication.Companion.KB_SEED_VERSION
import com.ashkb.app.AshkbApplication.Companion.SEED_FILES
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.2（批次 18）：**「改了种子必须同时 bump `KB_SEED_VERSION`」的门**（第四份审查报告 §九）。
 *
 * ### 为什么需要这道门
 * `AshkbApplication.importKbSeedIfNeeded` 整体受 `prefs.getInt(KEY_SEED_VERSION, 0) >= KB_SEED_VERSION`
 * 门控：版本没提升，**已安装用户永远不会再核对一次**。种子刷新本身实现得谨慎
 * （`KbSeedRefresh` 字段级 diff、`user_note` 拷回），但「改了 JSON 忘了 bump 常量」这一件事
 * 没有任何拦截——结果是用户永久保留旧的（可能是错的）文本，且**不报任何错**。
 *
 * ### 这道门为什么不会假红
 * 它比对的是**两个必然同批改动的东西**：`KB_SEED_VERSION`（本次内容对应的版本号）
 * 与 [FINGERPRINTS] 里登记的内容指纹。二者若不同批更新，断言就红：
 *   · 改了种子、没 bump → 当前指纹对不上 `FINGERPRINTS[KB_SEED_VERSION]`（还是旧指纹）→ 红；
 *   · bump 了、没改表 → `FINGERPRINTS[新版本]` 不存在 → 红；
 *   · 两者都改 → 绿。
 * 指纹覆盖 [SEED_FILES] 的**全部**文件（与 `importKbSeedIfNeeded` 同一份清单），
 * 所以「只改一个文件」也算改动。
 *
 * ### 下一个人怎么用
 * 改了 `app/src/main/assets/kb_seed_*.json` 之后：
 * ① 把 `KB_SEED_VERSION` +1；② 在 [FINGERPRINTS] 里加一行 `新版本 to 新指纹`
 * （新指纹跑一次本测试失败时，断言消息里会打印当前值，复制即可）。
 */
class KbSeedVersionGateTest {

    /**
     * 版本 → 该版本对应的**内容指纹**。
     *
     * 只登记当前及历史版本，不预登记未来的——预登记等于把门提前拆掉。
     *
     * ⚠️ 同一份内容**可以**登记在两个版本下：`3` 就是这种「补闸」——
     * v1.0.70 往 `kb_seed_edu.json` 加了 `edu-005`、另一次提交改写了 `exc-004` 的文案，
     * 两次都**没有** bump `KB_SEED_VERSION`，于是所有已安装用户至今没跑过那一次核对
     * （`importKbSeedIfNeeded` 见到 `prefs >= 2` 就直接 return）：
     * `edu-005` 从未进过他们的库，`exc-004` 仍是旧文案。
     * 把闸门补开到 3，内容一个字节没改，但**这一批已安装用户会被重新核对一次**。
     */
    private val fingerprints: Map<Int, String> = mapOf(
        // 2 = v1.0.44 起的增量刷新版本；本表建立时的内容指纹。
        2 to "ecb509ea7b4ef0f671317e870993daa49a2cc8dd27b9c5967d4292dbe268c9ae",
        // 3 = v1.1.2（批次 18）补闸：把闸门补开到「当前内容」，让漏 bump 的那批用户被重新核对。
        3 to "ecb509ea7b4ef0f671317e870993daa49a2cc8dd27b9c5967d4292dbe268c9ae",
        // 4 = v1.1.2：`exc-004` 的 `source_url` 改指 Internet Archive 上**同一份 PDF** 的存档
        // （原 Versus Arthritis 链接已 302 到 /error/404），机构名随之改为 Arthritis UK。
        // 只有 `kb_seed_exc.json` 变了，其余四个种子文件字节不变。
        4 to "96e6771fa065b88e5f37e11c563f66b4611838d3cd5f55e6adbdd4d78df5c266",
        // 5 = v1.1.3（批次 19）：落实维护者的 5 项医学内容裁决 + 4 项无需医学判断的修订。
        // 五个种子文件**全部**有改动：itx（`itx-002` 类级化、`itx-012` 按 ACR 2022 拆两条）/
        // exc（`exc-001`/`exc-003` 诚实口径、`exb-004` 降 S4）/ fdg（`fdg-004` 降 S4）/
        // edu（两条阈值条目改标 SYS、`edu-th-002` 文案对齐实现）；emr 字节未变。
        5 to "e593d2ad60ecf1d53a084ee7bd1cb3b195aa75db3d7dd962162bcf3c11c3036c",
        // v1.1.4（批次 20）：分级重构——12 条 source_tier 按项目自定定义修正
        // （11 条患者组织/NHS 教育页 S1/S2 → S3；emr-005 是 PMC 期刊文，S1 → S2）
        6 to "1af5b57bc2c23b7b1e1bbe9cb8a0dd1c059103a938ccfbb62db3ca320a289b19",
    )

    /** 单测工作目录是模块目录（`app/`），与 `MedicationInteractionChainTest` 同一约定。 */
    private fun assetFile(name: String): File {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }
            ?: error("找不到种子文件 $name（单测工作目录=${File(".").absolutePath}）")
    }

    /** 全部种子文件的 SHA-256（文件名单独并入摘要，改名也要被看见）。 */
    private fun fingerprint(): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (name in SEED_FILES) {
            md.update(name.toByteArray())
            md.update(assetFile(name).readBytes())
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Test
    fun `改了种子内容必须同时 bump KB_SEED_VERSION`() {
        val recorded = fingerprints[KB_SEED_VERSION]
        assertNotNull(
            "KB_SEED_VERSION = $KB_SEED_VERSION 在指纹表里没有登记。" +
                "改了种子内容就必须 bump 版本并加一行指纹（第四份审查报告 §九）",
            recorded,
        )
        val current = fingerprint()
        assertEquals(
            "种子内容与 KB_SEED_VERSION = $KB_SEED_VERSION 不匹配（当前指纹 $current）。" +
                "若你确实改了种子：先把 KB_SEED_VERSION +1，再把 $current 登记进 fingerprints。" +
                "若你没改种子：说明这次内容变更是别处漏了 bump。",
            recorded,
            current,
        )
    }

    /**
     * 指纹表自身的形状：版本从 2 起**连续**、指纹是 64 位小写十六进制、
     * 且**不预登记未来版本**（`KB_SEED_VERSION` 必须是表里最大的那个）。
     *
     * 「预登记」为什么危险：表里有了 `4 to ...`，就等于允许先改内容、后补版本号——
     * 而那正是这道门要拦的事。连续性则保证「每 bump 一次就登记一行」，不会跳号。
     */
    @Test
    fun `指纹表连续且不预登记未来版本`() {
        assertTrue("指纹表为空", fingerprints.isNotEmpty())
        val versions = fingerprints.keys.sorted()
        assertEquals("指纹表的版本号应从 2 起连续：$versions", (2..versions.last()).toList(), versions)
        for ((version, digest) in fingerprints) {
            assertTrue("版本 $version 的指纹不是 64 位小写十六进制：$digest", digest.matches(HEX64))
        }
        assertEquals(
            "KB_SEED_VERSION 不能大于指纹表里最大的已登记版本（别预登记未来的版本）",
            versions.last(),
            KB_SEED_VERSION,
        )
    }

    private companion object {
        val HEX64 = Regex("^[0-9a-f]{64}$")
    }
}
