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
 *  1. 四个文件里的 **versionCode / versionName** 必须与 `app/build.gradle.kts` 一致
 *  2. 三个文档里的**单测条数**必须与 `app/build/test-results/testDebugUnitTest` 的实际条数一致
 *  3. `AppDatabase.kt` 的 `ASHKB_DB_VERSION` 必须有对应的 `app/schemas/.../<n>.json`
 *
 * 用法：`gradle verifySpecSync`（需先跑过 `testDebugUnitTest`，CI 里顺序即如此）
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
        val handoff = read("HANDOFF.md")
        val status = read("HANDOFF-STATUS.md")
        val problems = mutableListOf<String>()

        // ---- 1. 版本号 ----
        fun expectVersion(doc: String, name: String, needle: String) {
            if (!doc.contains(needle)) {
                problems += "$name 缺少或不匹配：「$needle」（当前代码为 v$versionName / versionCode $versionCode）"
            }
        }
        expectVersion(readme, "README.md", "当前 `v$versionName`（versionCode $versionCode）")
        expectVersion(handoff, "HANDOFF.md", "**v$versionName（versionCode $versionCode）")
        expectVersion(status, "HANDOFF-STATUS.md", "截至 **v$versionName（versionCode $versionCode）")
        expectVersion(status, "HANDOFF-STATUS.md", "versionCode $versionCode / versionName $versionName")

        // ---- 2. 单测条数（以实际测试报告为准） ----
        val resultsDir = File(root, "app/build/test-results/testDebugUnitTest")
        if (!resultsDir.exists()) {
            problems += "找不到单测报告目录 app/build/test-results/testDebugUnitTest——请先运行 testDebugUnitTest"
        } else {
            var tests = 0
            resultsDir.listFiles { f -> f.name.endsWith(".xml") }?.forEach { f ->
                val m = Regex("""tests="(\d+)"""").find(f.readText())
                if (m != null) tests += m.groupValues[1].toInt()
            }
            if (tests == 0) {
                problems += "单测报告里没有用例（tests=0）——报告可能未生成"
            } else {
                if (!readme.contains("$tests 条 JVM 单元测试")) {
                    problems += "README.md 的单测条数与实际不一致（实际 $tests 条，应写「$tests 条 JVM 单元测试」）"
                }
                if (!handoff.contains("**$tests 条单测全绿**")) {
                    problems += "HANDOFF.md 的单测条数与实际不一致（实际 $tests 条，应写「**$tests 条单测全绿**」）"
                }
                if (!status.contains("**$tests 条，0 失败")) {
                    problems += "HANDOFF-STATUS.md 的单测条数与实际不一致（实际 $tests 条，应写「**$tests 条，0 失败」）"
                }
                logger.lifecycle("verifySpecSync：实际单测 $tests 条，代码版本 v$versionName（$versionCode）")
            }
        }

        // ---- 3. 库版本 ↔ schema 导出文件 ----
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
                    "\n（发版前请同步 README / HANDOFF / HANDOFF-STATUS 的版本号与单测条数）"
            )
        }
        logger.lifecycle("✅ verifySpecSync 通过：版本号与单测条数在代码与文档之间一致。")
    }
}
