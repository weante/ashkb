package com.ashkb.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * S-6（审查报告第 129 行）：系统云备份 / 设备迁移必须**整域排除**，不能靠逐项列举。
 *
 * ### 缺陷形态
 * 改动前 `res/xml/backup_rules.xml` 只有一条 `<exclude domain="database" path="ashkb.db" />`，
 * `res/xml/data_extraction_rules.xml` 同样是两条同样的排除。含义是「除了 `ashkb.db`，
 * 其它全都会随系统备份走」——而 `shared_prefs/vault_config`（恢复码密文）、
 * `shared_prefs/webdav_config`（WebDAV 地址与账号）、`files/backups/`（加密备份本体）、
 * `files/checkup_attachments/`（**明文**化验与影像附件）、`files/exports/`（**明文**报告 PDF
 * 与二维码 PNG）、`ashkb.db-wal` / `-shm` 旁文件全在覆盖范围内。
 *
 * 当前靠 `android:allowBackup="false"` 挡着，所以这不是「已经泄露」而是「一道闸失效即泄露」：
 * 只要有人把 allowBackup 改回 true、用 debug 构建、或上架要求打开备份，PHI 就整体上云。
 *
 * ### 为什么用静态解析而不是 Robolectric 跑真备份
 * 没有可在单测里触发的系统备份 API（`BackupManager` 需要真实传输后端），
 * 而这里的风险恰恰是**规则文件写漏**——那件事只有读文件本身才看得见。
 * 解析 XML 而不是字符串匹配，是为了让注释里出现 `domain="file"` 这类举例不会误判。
 *
 * ### 钉住的三件事
 * 1. 两个规则文件对五个域全覆盖，且每条都是 `path="."`（整域），不是某个具体文件；
 * 2. 不得出现 `<include>`——按 AOSP 语义，`include` 会覆盖同一域上的 `exclude`，
 *    等于把排除规则作废（这是"改对了却仍漏"的最隐蔽写法）；
 * 3. `AndroidManifest.xml` 仍写着 `allowBackup="false"` 且仍引用这两个规则文件——
 *    否则规则文件写了也没人读。
 */
class BackupRulesCoverageTest {

    private val fullBackup = parse("app/src/main/res/xml/backup_rules.xml")
    private val extraction = parse("app/src/main/res/xml/data_extraction_rules.xml")
    private val manifest = readSource("app/src/main/AndroidManifest.xml")

    @Test
    fun `全量备份规则覆盖全部五个域且都是整域排除`() {
        val excludes = fullBackup.getElementsByTagName("exclude")
        val covered = (0 until excludes.length)
            .map { excludes.item(it) as Element }
            .associate { it.getAttribute("domain") to it.getAttribute("path") }

        assertEquals(
            "full-backup-content 必须逐个域整域排除，漏一个域就等于把那个域交给系统",
            DOMAINS.associateWith { WHOLE_DOMAIN },
            covered,
        )
    }

    @Test
    fun `云备份与设备迁移两段规则都要覆盖全部域`() {
        for (section in listOf("cloud-backup", "device-transfer")) {
            val nodes = extraction.getElementsByTagName(section)
            assertEquals("$section 段必须恰好一个", 1, nodes.length)
            val excludes = nodes.item(0).let { it as Element }.getElementsByTagName("exclude")
            val covered = (0 until excludes.length)
                .map { excludes.item(it) as Element }
                .associate { it.getAttribute("domain") to it.getAttribute("path") }

            assertEquals(
                "$section 段必须覆盖全部五个域（换新手机整包搬迁同样会搬走明文附件）",
                DOMAINS.associateWith { WHOLE_DOMAIN },
                covered,
            )
        }
    }

    @Test
    fun `不许出现 include 元素`() {
        // include 会覆盖同一域上的 exclude，排除规则直接作废——这是"改对了却仍漏"的最隐蔽写法。
        for ((name, doc) in listOf("backup_rules.xml" to fullBackup, "data_extraction_rules.xml" to extraction)) {
            assertEquals(
                "$name 不得含 <include>：它会覆盖同一域的 <exclude>，等于把排除规则作废",
                0,
                doc.getElementsByTagName("include").length,
            )
        }
    }

    @Test
    fun `清单仍关闭 allowBackup 且仍引用两个规则文件`() {
        assertTrue(
            "第一道闸必须是 allowBackup=false——规则文件是第二道防线，不是替代品",
            manifest.contains("android:allowBackup=\"false\""),
        )
        assertTrue(
            "fullBackupContent 必须指向 @xml/backup_rules，否则本文件写什么都不生效",
            manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""),
        )
        assertTrue(
            "dataExtractionRules 必须指向 @xml/data_extraction_rules",
            manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""),
        )
    }

    @Test
    fun `规则文件里的域取值都在 Android 支持的五个之内`() {
        // 写错域名（例如 sharedprefs、preference）不会报错，那条规则会被静默忽略。
        for ((name, doc) in listOf("backup_rules.xml" to fullBackup, "data_extraction_rules.xml" to extraction)) {
            val excludes = doc.getElementsByTagName("exclude")
            for (i in 0 until excludes.length) {
                val domain = (excludes.item(i) as Element).getAttribute("domain")
                assertTrue(
                    "$name 里的 domain=\"$domain\" 不是平台支持的取值（$DOMAINS），这条规则会被静默忽略",
                    domain in DOMAINS,
                )
            }
        }
    }

    private fun parse(relative: String): org.w3c.dom.Document {
        val file = resolve(relative)
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newDocumentBuilder().parse(file)
    }

    private fun resolve(relative: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return File("../$relative")
    }

    private fun readSource(relative: String): String = resolve(relative).readText()

    private companion object {
        /** 覆盖 `AndroidManifest.xml` 的 `allowBackup="false"` 之后，S-6 要求的五个域。 */
        val DOMAINS = listOf("database", "sharedpref", "file", "root", "external")

        /** `path="."` 表示整个域，而不是域里的某一个文件。 */
        const val WHOLE_DOMAIN = "."
    }
}
