import java.io.File

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    // 批次 2（测试安全网）：静态分析。只声明不应用，由 :app 模块启用（源码在那里）。
    alias(libs.plugins.detekt) apply false
}

/**
 * 批次 2（工程门）：**规范一致性校验**。
 *
 * 为什么需要：这个项目每次发版都要人工核对「版本号 / 单测条数」在 4 个地方是否一致
 * （`app/build.gradle.kts`、`README.md`、`HANDOFF.md`、`HANDOFF-STATUS.md`），
 * 而第四份审查报告正是把「文档与代码漂移」列成了独立问题（README 急救场景名不符、
 * HANDOFF 头部还写着 v1.0.70/versionCode 75）。人工核对迟早会漏，故做成机器门。
 *
 * 检查项：
 *  1. `README.md` 与 `HANDOFF-STATUS.md` 里的 **versionCode / versionName** 必须与
 *     `app/build.gradle.kts` 一致（`HANDOFF.md` 不参与——见下面 §1 的注释）
 *  2. `AppDatabase.kt` 的 `ASHKB_DB_VERSION` 必须有对应的 `app/schemas/.../<n>.json`
 *
 * **v1.2.9 起不再校验「单测条数」**：条数是**构建产物**（`app/build/test-results/` 被 .gitignore
 * 忽略，`gradle clean` 即消失），把它抄进人写的文档只会造出一条必然腐烂的副本——历史上它曾在
 * README 同页并存 114 / 205 两个互相矛盾的数，也长期停在 264 而别处已到 339。而 CI 上一步的
 * `testDebugUnitTest` 已经证明测试通过，文档再存一遍是**重复且更易腐**的。副产品：本任务不再
 * 依赖任何构建产物，可脱离构建独立运行。
 *
 * 用法：`gradle verifySpecSync`
 */
tasks.register("verifySpecSync") {
    group = "verification"
    description = "校验版本号与单测条数在代码与文档之间是否同步"

    doLast {
        val root = projectDir
        fun read(rel: String) = File(root, rel).let { if (it.exists()) it.readText() else "" }

        val appGradle = read("app/build.gradle.kts")
        val versionCode = Regex("""versionCode\s*=\s*(\d+)""").find(appGradle)?.groupValues?.get(1)
            ?: throw GradleException("在 app/build.gradle.kts 里找不到 versionCode")
        val versionName = Regex("""versionName\s*=\s*"([^"]+)"""").find(appGradle)?.groupValues?.get(1)
            ?: throw GradleException("在 app/build.gradle.kts 里找不到 versionName")

        val readme = read("README.md")
        val status = read("HANDOFF-STATUS.md")
        val problems = mutableListOf<String>()

        // ---- 1. 版本号 ----
        fun expectVersion(doc: String, name: String, needle: String) {
            if (!doc.contains(needle)) {
                problems += "$name 缺少或不匹配：「$needle」（当前代码为 v$versionName / versionCode $versionCode）"
            }
        }
        expectVersion(readme, "README.md", "当前 `v$versionName`（versionCode $versionCode）")
        expectVersion(status, "HANDOFF-STATUS.md", "截至 **v$versionName（versionCode $versionCode）")
        expectVersion(status, "HANDOFF-STATUS.md", "versionCode $versionCode / versionName $versionName")
        // HANDOFF.md 不再校验版本号：v1.2.9 起它是**按主题组织、不按版本追加**的长期工程约定，
        // 已刻意删掉「记录截至 vX」那一行（它历史上烂在 v1.0.73 没人回改）。
        // 版本号单一事实来源是 app/build.gradle.kts，展示口径是 README 与 HANDOFF-STATUS。

        // ---- 2. 库版本 ↔ schema 导出文件 ----
        val dbSrc = read("app/src/main/java/com/ashkb/app/data/db/AppDatabase.kt")
        val dbVersion = Regex("""ASHKB_DB_VERSION\s*=\s*(\d+)""").find(dbSrc)?.groupValues?.get(1)
        if (dbVersion == null) {
            problems += "AppDatabase.kt 里找不到 ASHKB_DB_VERSION"
        } else {
            val schema = File(root, "app/schemas/com.ashkb.app.data.db.AppDatabase/$dbVersion.json")
            if (!schema.exists()) {
                problems += "缺少库版本 $dbVersion 的导出 schema：${schema.path}（请构建并提交 app/schemas）"
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "规范一致性校验未通过（${problems.size} 项）：\n" +
                    problems.joinToString("\n") { "  · $it" } +
                    "\n（发版前请同步 README / HANDOFF / HANDOFF-STATUS 的版本号）"
            )
        }
        logger.lifecycle("✅ verifySpecSync 通过：版本号与库 schema 在代码与文档之间一致。")
    }
}
