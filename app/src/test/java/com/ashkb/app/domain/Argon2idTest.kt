package com.ashkb.app.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Argon2id 的黄金向量测试。
 *
 * ### 为什么期望值必须来自外部
 * 上一轮二维码栽过一次：自己编、自己解，测试全绿，真机却扫不出来——因为
 * 编解码犯的是同一个错，自证根本排除不了它。密码派生是同一个陷阱：若用
 * 本项目自己的 Kotlin 实现生成期望值，那就等于拿一份可能错误的答案给同一
 * 份错误判卷。
 *
 * 所以本文件里每一条 `tag_hex` 都出自独立参考实现 **@noble/hashes 1.7.1**
 * （MIT，纯 JS、零依赖），生成时本仓库代码完全没有参与。该参考实现本身又
 * 逐字节复现了 RFC 9106 §5.1/§5.2/§5.3 的三组官方 Tag，可信度有独立锚点。
 *
 * 唯一例外是 `tag65`：noble 的 `Hp()` 末尾 `return u32(out)` 而 `u32()` 按
 * `floor(byteLength / 4)` 建视图，输出长度不是 4 的倍数时会丢掉尾字节，所以它
 * 生成不了 65 字节的向量。该组改用 Node.js v24 内置的 `crypto.argon2Sync`
 * （OpenSSL 的 C 实现），两个独立实现互证：noble 返回的 64 字节恰是它的前缀。
 * 详见 `app/src/test/resources/argon2/vectors.txt` 的文件头。
 *
 * ### 负向对照（实际做过的记录，不是计划）
 * 为确认这些断言不是空转，实现完成后做过两次故意破坏，每次都用
 * `:app:testDebugUnitTest --tests com.ashkb.app.domain.Argon2idTest` 实跑，
 * 并读 `app/build/test-results/testDebugUnitTest/` 的 XML 判定，不看退出码：
 *
 * 1. 改 `Argon2id.kt:363` 的 `while (outLen - produced > BLAKE2B_MAX_OUT_BYTES)`
 *    为 `>=`（H' 的分块边界错一格）→ `tests=8 failures=2 errors=0 skipped=1`，
 *    变红的是「全部黄金向量必须与参考实现逐字节一致」与「RFC 9106 三组官方
 *    向量必须逐字节一致」。
 * 2. 改 `Argon2id.kt:553` 的 `return if (sameLane) done + index - 1 else ...`
 *    为 `done + index`（参考区大小多算一个块，索引算法的一处偏移）→ 同样
 *    `tests=8 failures=2 errors=0 skipped=1`，同样这两条变红。
 *
 * 两次破坏都用独立驱动先确认过 20 组全错（`cases=20 mismatched=20`），恢复后
 * 与备份逐字节比对 `IDENTICAL`，再跑得 `tests=8 failures=0 errors=0 skipped=1`。
 * 关键结论：测试确实会因为实现错误而变红，断言不是只验证「能跑通」。
 */
internal class Argon2idTest {

    @Test
    fun `黄金向量文件必须完整解析出全部 20 组`() {
        val vectors = Argon2GoldenVectors.load()
        assertEquals("向量组数（改向量文件时必须同步改这个断言）", EXPECTED_VECTOR_COUNT, vectors.size)
        val names = vectors.map { it.name }
        assertEquals("存在重名的 case", names.size, names.toSet().size)
        assertTrue("每一组的期望摘要都不应为空", vectors.all { it.expectedTag.isNotEmpty() })
    }

    @Test
    fun `全部黄金向量必须与参考实现逐字节一致`() {
        val vectors = Argon2GoldenVectors.load()
        val failures = mutableListOf<String>()
        for (vector in vectors) {
            val actual = runCatching { compute(vector) }.getOrNull()
            if (actual == null) {
                failures += "${vector.name}: 计算抛出异常"
                continue
            }
            if (!actual.contentEquals(vector.expectedTag)) {
                failures += "${vector.name} (${vector.note})\n" + describeDifference(vector.expectedTag, actual)
            }
        }
        assertTrue(
            "Argon2id 与参考实现不一致，共 ${failures.size}/${vectors.size} 组失败：\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `RFC 9106 三组官方向量必须逐字节一致`() {
        val official = Argon2GoldenVectors.load().filter { it.name.startsWith(RFC_CASE_PREFIX) }
        assertEquals("RFC 官方向量组数", EXPECTED_RFC_COUNT, official.size)
        for (vector in official) {
            val actual = compute(vector)
            assertArrayEquals(
                "${vector.name} 与 RFC 9106 原文不符（type=${vector.type}）",
                vector.expectedTag,
                actual,
            )
        }
    }

    @Test
    fun `参数越界必须抛出 IllegalArgumentException`() {
        val password = "password".toByteArray()
        val salt = "somesalt".toByteArray()
        val invalidCalls = listOf<Pair<String, () -> ByteArray>>(
            "salt 只有 7 字节" to { Argon2id.hash(password, ByteArray(SEVEN_BYTES)) },
            "tagLength 为 3" to { Argon2id.hash(password, salt, tagLength = BELOW_MIN_TAG) },
            "iterations 为 0" to { Argon2id.hash(password, salt, iterations = 0) },
            "parallelism 为 0" to { Argon2id.hash(password, salt, parallelism = 0) },
            "memory 小于 8p" to { Argon2id.hash(password, salt, memoryKiB = BELOW_MIN_MEMORY, parallelism = TWO) },
        )
        val missed = invalidCalls.mapNotNull { (label, call) ->
            // 只认 IllegalArgumentException；其它异常（如 NPE）说明校验没落在参数上。
            if (runCatching(call).exceptionOrNull() is IllegalArgumentException) null else label
        }
        assertTrue("这些非法参数没有被拒绝：$missed", missed.isEmpty())
    }

    /**
     * p 只影响内存布局与速度，绝不能被忽略。用两组 m 相同、p 不同的参数，
     * 结果必须不同——否则说明实现把 parallelism 当摆设了。
     */
    @Test
    fun `parallelism 必须真正参与运算`() {
        val password = "password".toByteArray()
        val salt = "somesalt".toByteArray()
        val lane1 = Argon2id.hash(password, salt, memoryKiB = SHARED_MEMORY, parallelism = 1)
        val lane4 = Argon2id.hash(password, salt, memoryKiB = SHARED_MEMORY, parallelism = FOUR)
        assertNotEquals("p=1 与 p=4 得到了相同摘要", lane1.toHex(), lane4.toHex())
    }

    @Test
    fun `相同输入必须可重复且彼此独立`() {
        val password = "病患健康档案-口令".toByteArray()
        val salt = "盐值salt-16字节".toByteArray()
        val first = Argon2id.hash(password, salt, memoryKiB = SMALL_MEMORY, iterations = ONE)
        val second = Argon2id.hash(password.copyOf(), salt.copyOf(), memoryKiB = SMALL_MEMORY, iterations = ONE)
        assertArrayEquals("同一输入的两次调用结果不同，说明内部有未重置的共享状态", first, second)
        assertNotNull(first)
    }

    /**
     * 性能实测：默认跳过（`assumeTrue`），只有在 `ARGON2_BENCH=1` 时运行，
     * 免得日常单测被几十秒的基准拖慢。结果打印到 stdout，由 test-results XML
     * 的 system-out 取回。
     *
     * 注意这是 **JVM（桌面 OpenJDK 21）** 的数字，不是 Android ART 上的数字，
     * 不能直接当设备性能看。ART 上一般还要再慢一截。
     */
    @Test
    fun `性能实测`() {
        assumeTrue("设置环境变量 ARGON2_BENCH=1 才运行性能实测", System.getenv(BENCH_ENV) == "1")
        val password = "benchmark-password".toByteArray()
        val salt = "benchmark-salt-16".toByteArray()
        val combos = listOf(
            Triple(64 * 1024, 3, 4),
            Triple(32 * 1024, 3, 4),
            Triple(19456, 2, 1),
            Triple(64 * 1024, 1, 1),
        )
        // 用 getProperty 而不是 Runtime.version()：后者是 JDK 9+ 的 API，
        // 在 Android 的 java.lang.Runtime 上不存在，单测虽跑在 JVM 上，
        // 但同一份源码要能被 androidTest 编译。
        val runtime = System.getProperty("java.version") ?: "unknown"
        val report = StringBuilder("=== Argon2id JVM benchmark (java.version=$runtime) ===\n")
        for ((memory, iterations, lanes) in combos) {
            // 先跑一次让 JIT 热起来，再测第二次的耗时。
            Argon2id.hash(password, salt, memoryKiB = memory, iterations = iterations, parallelism = lanes)
            val startedAt = System.nanoTime()
            Argon2id.hash(password, salt, memoryKiB = memory, iterations = iterations, parallelism = lanes)
            val elapsedMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI
            report.append("m=${memory}KiB t=$iterations p=$lanes -> ${elapsedMs}ms\n")
            assertTrue("耗时不应为负", elapsedMs >= 0)
            println("BENCH m=${memory} t=$iterations p=$lanes ${elapsedMs}ms")
        }
        println(report)
    }

    private fun compute(vector: Argon2GoldenVectors.Vector): ByteArray =
        Argon2id.hashWithType(
            type = vector.type,
            options = Argon2Options(
                memoryKiB = vector.memoryKiB,
                iterations = vector.iterations,
                parallelism = vector.parallelism,
                tagLength = vector.tagLength,
            ),
            password = vector.password,
            salt = vector.salt,
            secret = vector.secret,
            associatedData = vector.associatedData,
        )

    /** 把第一处不一致的位置、双方长度与十六进制都写出来，避免「就是不对」这种无用报错。 */
    private fun describeDifference(expected: ByteArray, actual: ByteArray): String {
        val head = ByteArray(minOf(expected.size, DIFF_PREVIEW_BYTES))
        expected.copyInto(head, 0, 0, head.size)
        val actualHead = ByteArray(minOf(actual.size, DIFF_PREVIEW_BYTES))
        actual.copyInto(actualHead, 0, 0, actualHead.size)
        val firstDiff = (0 until minOf(expected.size, actual.size)).firstOrNull { expected[it] != actual[it] }
        return buildString {
            append("  期望长度=${expected.size} 实际长度=${actual.size}\n")
            append("  首个不一致字节偏移=$firstDiff\n")
            append("  期望前 ${head.size} 字节=${head.toHex()}\n")
            append("  实际前 ${actualHead.size} 字节=${actualHead.toHex()}")
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val EXPECTED_VECTOR_COUNT = 20
        const val EXPECTED_RFC_COUNT = 3
        const val RFC_CASE_PREFIX = "rfc9106-"

        /** 这两组都用 m=32 KiB，唯一差别是 p（m' 均为 32，p=4 时每 lane 8 块）。 */
        const val SHARED_MEMORY = 32
        const val SMALL_MEMORY = 64
        const val ONE = 1
        const val TWO = 2
        const val FOUR = 4
        const val SEVEN_BYTES = 7
        const val BELOW_MIN_TAG = 3
        const val BELOW_MIN_MEMORY = 8
        const val DIFF_PREVIEW_BYTES = 16
        const val NANOS_PER_MILLI = 1_000_000L
        const val BENCH_ENV = "ARGON2_BENCH"
    }
}

/**
 * 读取并解析 `app/src/test/resources/argon2/vectors.txt`。
 *
 * 单独成 object 是为了让 [Argon2idTest] 只关心断言；解析逻辑若混进测试方法，
 * 解析错误会伪装成实现错误，误导排查方向。这与 `QrGoldenVectors` 的分工一致。
 */
internal object Argon2GoldenVectors {

    private const val RESOURCE_PATH = "/argon2/vectors.txt"
    private const val CASE_PREFIX = "[case "
    private const val CASE_SUFFIX = "]"
    private const val COMMENT_PREFIX = "#"
    private const val KEY_VALUE_SEPARATOR = "="
    private const val HEX_RADIX_SHIFT = 4
    private const val HEX_NIBBLE_MASK = 0xF
    private const val HEX_CHARS_PER_BYTE = 2
    private const val HEX_RADIX = 16

    /** 一条向量。`type` 是 RFC 的类型号：0 = Argon2d，1 = Argon2i，2 = Argon2id。 */
    data class Vector(
        val name: String,
        val note: String,
        val type: Int,
        val parallelism: Int,
        val memoryKiB: Int,
        val iterations: Int,
        val tagLength: Int,
        val password: ByteArray,
        val salt: ByteArray,
        val secret: ByteArray,
        val associatedData: ByteArray,
        val expectedTag: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = name.hashCode()
    }

    fun load(): List<Vector> = parse(readText())

    private fun readText(): String =
        Argon2GoldenVectors::class.java.getResourceAsStream(RESOURCE_PATH)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("找不到黄金向量资源：$RESOURCE_PATH")

    private fun parse(text: String): List<Vector> = splitCases(text).map { parseCase(it) }

    /**
     * 按 `[case name]` 切块。返回的每块第一行是 case 名，其余是 `key=value`。
     * 空行与 `#` 注释在此一次性丢弃，后续解析不必再关心它们。
     */
    private fun splitCases(text: String): List<List<String>> {
        val cases = mutableListOf<MutableList<String>>()
        // 先滤掉空行与注释，主循环里就只剩「case 头」和「key=value」两种情况，
        // 不需要在循环体里 continue（detekt 的 LoopWithTooManyJumpStatements）。
        val meaningful = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(COMMENT_PREFIX) }
        for (line in meaningful) {
            if (isCaseHeader(line)) {
                cases += mutableListOf(line.removePrefix(CASE_PREFIX).removeSuffix(CASE_SUFFIX))
            } else {
                require(cases.isNotEmpty()) { "在第一个 [case] 之前出现了内容：$line" }
                cases.last() += line
            }
        }
        require(cases.isNotEmpty()) { "向量文件里没有任何 [case]" }
        return cases
    }

    private fun isCaseHeader(line: String): Boolean =
        line.startsWith(CASE_PREFIX) && line.endsWith(CASE_SUFFIX)

    private fun parseCase(lines: List<String>): Vector {
        val name = lines.first()
        val fields = lines.drop(1).associate { line ->
            val at = line.indexOf(KEY_VALUE_SEPARATOR)
            require(at > 0) { "case $name 里有无法解析的行：$line" }
            line.substring(0, at).trim() to line.substring(at + 1).trim()
        }
        fun required(key: String): String =
            fields[key] ?: error("case $name 缺少字段 $key")
        return Vector(
            name = name,
            note = fields["note"] ?: "",
            type = parseType(name, required("type")),
            parallelism = required("parallelism").toInt(),
            memoryKiB = required("memory_kib").toInt(),
            iterations = required("iterations").toInt(),
            tagLength = required("tag_length").toInt(),
            password = hexToBytes(required("password_hex")),
            salt = hexToBytes(required("salt_hex")),
            secret = hexToBytes(fields["secret_hex"] ?: ""),
            associatedData = hexToBytes(fields["ad_hex"] ?: ""),
            expectedTag = hexToBytes(required("tag_hex")),
        )
    }

    private fun parseType(name: String, text: String): Int = when (text) {
        "id" -> TYPE_ARGON2ID
        "i" -> TYPE_ARGON2I
        "d" -> TYPE_ARGON2D
        else -> error("case $name 的类型 $text 无法识别（只支持 id/i/d）")
    }

    /** 空串解析成空数组；奇数长度视为写错，直接报错而不是静默截断。 */
    internal fun hexToBytes(hex: String): ByteArray {
        require(hex.length % HEX_CHARS_PER_BYTE == 0) { "十六进制串长度必须是偶数：$hex" }
        val out = ByteArray(hex.length / HEX_CHARS_PER_BYTE)
        for (i in out.indices) {
            val high = Character.digit(hex[i * HEX_CHARS_PER_BYTE], HEX_RADIX)
            val low = Character.digit(hex[i * HEX_CHARS_PER_BYTE + 1], HEX_RADIX)
            require(high >= 0 && low >= 0) { "不是合法的十六进制：$hex" }
            out[i] = ((high shl HEX_RADIX_SHIFT) or (low and HEX_NIBBLE_MASK)).toByte()
        }
        return out
    }

    private const val TYPE_ARGON2D = 0
    private const val TYPE_ARGON2I = 1
    private const val TYPE_ARGON2ID = 2
}
