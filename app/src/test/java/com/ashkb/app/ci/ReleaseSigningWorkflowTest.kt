package com.ashkb.app.ci

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.11（缺陷工单 C04）：**tag / 发布构建必须要求正式签名与预期 SHA-256**。
 *
 * ### 缺陷原状
 * `.github/workflows/ci.yml` 只有一条 `build-and-test` 流水线，且无条件
 * `ASHKB_ALLOW_DEBUG_SIGNING=1`；验签步骤仅在「配置了可选 secret」时才严格比对。
 * 结果：**任何一个 tag 都能产出一个 debug 签名的"正式包"**，而 CI 是绿的——
 * 「CI 只打印签名，不能证明发布签名身份」。
 *
 * ### 本测试守什么
 * 工单验收要求「模拟 tag 时，缺 secret / 错误 digest / debug 签名均失败；正确 digest 才通过」。
 * 真正的 tag 触发只能在 GitHub 上验，但**流水线的结构**可以在本地静态守住：
 *
 * 1. 存在一个只在 `refs/tags/` 上触发的发布 job（其 `if:` 是正向 `startsWith`，不是取反）；
 * 2. 该 job **不设置** `ASHKB_ALLOW_DEBUG_SIGNING`（否则 debug 签名会被放行）；
 * 3. 该 job 里 `ASHKB_EXPECTED_CERT_SHA256` 是**必需**的（缺失即失败），而不是 `if [ -n ]`；
 * 4. 普通 main CI 仍设置 `ASHKB_ALLOW_DEBUG_SIGNING=1`（工单：「普通 main CI 不得因此变红」）；
 * 5. 文档写明「CI 冒烟 APK 不得发布」。
 *
 * 静态扫文件，不依赖 GitHub API；并按本仓惯例**断言确实读到了文件**：路径写错时大声失败，
 * 而不是悄悄跳过（否则守卫会在文件改名后静默失效）。
 *
 * ⚠️ 本测试自己踩过的坑（留作教训）：第一版用「job 文本里含不含 `refs/tags/`」来识别发布 job，
 * 结果把 `build-and-test` 认成了 tag job —— 因为它的 `if:` 是**取反**的
 * `!startsWith(github.ref, 'refs/tags/')`，子串同样命中。**判断 job 语义必须看 `if:` 行的
 * 方向（是否带取反），不能只看裸子串。**
 */
class ReleaseSigningWorkflowTest {

    private fun workflow(): File =
        listOf(File(".github/workflows/ci.yml"), File("../.github/workflows/ci.yml"))
            .firstOrNull { it.isFile }
            ?: error("未找到 .github/workflows/ci.yml（工作目录=${File(".").absolutePath}）")

    private fun text(): String = workflow().readText()

    /**
     * 去掉注释后再断言。
     *
     * **为什么必须去注释**：`ci.yml` 里专门写了「这里**刻意不设** ASHKB_ALLOW_DEBUG_SIGNING」
     * 这样的解释性注释；若直接对原文做子串断言，注释会把自己判成违规。
     * 静态守卫只应检查**真正生效的配置**，不该被说明文字左右。
     */
    private fun stripComments(body: String): String =
        body.lines()
            .filterNot { it.trimStart().startsWith("#") }
            .joinToString("\n") { it.replace(Regex("""(?<=\s)#.*$"""), "") }

    /** 把 workflow 按 job 切开：`  <job-id>:` 这种缩进两格的顶层键。 */
    private fun jobs(): Map<String, String> {
        val src = text()
        val header = Regex("""(?m)^  ([A-Za-z0-9_-]+):\s*$""").findAll(src).toList()
        if (header.isEmpty()) error("ci.yml 里没有解析出任何 job（顶层缩进两格的键）")
        return header.mapIndexed { i, m ->
            val end = header.getOrNull(i + 1)?.range?.first ?: src.length
            m.groupValues[1] to stripComments(src.substring(m.range.first, end))
        }.toMap()
    }

    private fun job(name: String): String =
        jobs()[name] ?: error("ci.yml 里没有 job「$name」，现有：${jobs().keys.joinToString()}")

    /** 取某个 job 的 `if:` 表达式（没有则返回空串）。 */
    private fun ifExpr(jobBody: String): String =
        jobBody.lines().firstOrNull { it.trimStart().startsWith("if:") }.orEmpty()

    /** 只在 tag 上触发的 job：`if:` 里正向出现 `startsWith(github.ref, 'refs/tags/')`。 */
    private fun tagJob(): Map.Entry<String, String> =
        jobs().entries.firstOrNull { (_, body) ->
            val e = ifExpr(body)
            !e.contains("!") && e.contains("startsWith") && e.contains("refs/tags/")
        } ?: error(
            "ci.yml 里没有任何 job 由 refs/tags/ 正向触发，tag 构建无从强制正式签名。" +
                "现有 job 的 if：${jobs().map { (k, v) -> "$k=${ifExpr(v).trim()}" }}",
        )

    @Test
    fun `存在只在 tag 上触发的发布 job`() {
        val tag = tagJob()
        assertTrue(
            "发布 job 的 if 必须以 startsWith(github.ref, 'refs/tags/') 收窄，实际：${ifExpr(tag.value).trim()}",
            ifExpr(tag.value).contains("startsWith") && ifExpr(tag.value).contains("refs/tags/"),
        )
    }

    @Test
    fun `tag job 不得放行 debug 签名`() {
        val tag = tagJob()
        assertFalse(
            "发布 job「${tag.key}」绝不能设置 ASHKB_ALLOW_DEBUG_SIGNING（否则 debug 签名被放行）",
            tag.value.contains("ASHKB_ALLOW_DEBUG_SIGNING"),
        )
    }

    @Test
    fun `tag job 必须要求预期签名摘要而不是可选比对`() {
        val tag = tagJob()
        assertTrue(
            "发布 job「${tag.key}」必须读取 ASHKB_EXPECTED_CERT_SHA256",
            tag.value.contains("ASHKB_EXPECTED_CERT_SHA256"),
        )
        // 关键：不能是「没配就跳过」的写法。判据必须按**语义**而不是某个具体变量名——
        // 负向对照曾用 $EXPECTED 绕过变量名判据，是真实漏洞。
        //
        // 精确判据：违规形态是「把存在性判断当成条件分支」，即 `if [ -n "$VAR" ]; then ...`。
        // 不能用「job 里出现 [ -n ... ]」这种宽判据：release job 里合法的
        // `[ -n "$DIGEST" ] || { echo "::error::读不到签名摘要"; exit 1; }`
        // 也是 [ -n ]，但它是**失败即退出**的健全性检查，不是可选比对（宽判据会误报）。
        val skipIfConfigured = Regex("""(?m)^\s*if\s+\[\s*-n\s+"\$[A-Za-z_]+"\s*\]""")
        assertFalse(
            "发布 job 不得用 `if [ -n \"\$VAR\" ]` 让缺失的 secret 静默跳过比对（变量名无关）",
            skipIfConfigured.containsMatchIn(tag.value),
        )
        // 必须显式判空并失败
        val requiredGuard = Regex("""\[\s*-z\s+"\$[A-Za-z_]+"\s*\]""").containsMatchIn(tag.value)
        assertTrue(
            "发布 job 必须显式拒绝缺失的 secret（[ -z \"\$VAR\" ] ⇒ ::error:: ⇒ exit 1）",
            requiredGuard,
        )
        assertTrue(
            "发布 job 判空后必须打印 ::error:: 并失败",
            tag.value.contains("::error::") && tag.value.contains("exit 1"),
        )
    }

    @Test
    fun `普通 main CI 仍用 debug 签名冒烟且不因此变红`() {
        val mainJob = job("build-and-test")
        assertTrue(
            "main CI 必须保留 ASHKB_ALLOW_DEBUG_SIGNING=1（工单：普通 main CI 不得因此变红）",
            mainJob.contains("ASHKB_ALLOW_DEBUG_SIGNING"),
        )
        // main job 的 if 是取反的，即"非 tag 才跑"——不能是正向 tag 触发
        assertTrue(
            "main job 必须显式排除 tag（if 里出现 !startsWith(...refs/tags/)），实际：${ifExpr(mainJob).trim()}",
            ifExpr(mainJob).contains("!") && ifExpr(mainJob).contains("refs/tags/"),
        )
        // 可选的严格比对可以留在 main，但必须是「配了才比」而不是「没配就红」
        assertTrue(
            "main job 里的摘要比对必须保留可选语义（[ -n \"\$ASHKB_EXPECTED_CERT_SHA256\" ]）",
            mainJob.contains("[ -n \"\$ASHKB_EXPECTED_CERT_SHA256\" ]"),
        )
    }

    @Test
    fun `文档明确 CI 冒烟 APK 不得发布`() {
        val wf = text()
        assertTrue(
            "ci.yml 必须写明 CI 冒烟 APK 不得发布",
            wf.contains("不得对外发布") || wf.contains("不得发布") || wf.contains("not for release"),
        )
    }
}
