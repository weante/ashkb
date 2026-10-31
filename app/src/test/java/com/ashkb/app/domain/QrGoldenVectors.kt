package com.ashkb.app.domain

/**
 * 读取测试资源里的二维码黄金向量。
 *
 * 向量由**独立参考实现**（Nayuki QR-Code-generator，MIT）生成，不是本仓库代码的产物——
 * 这是关键：上一版 B6 用「自己编、自己解」做回读自测，编解码犯同一个错时照样全绿，
 * 真机却扫不出来。只有拿另一份实现的模块矩阵逐格比，才能证明图案是对的。
 *
 * 向量文件 `app/src/test/resources/qr/vectors.txt` 的格式（由生成器写出）：
 * ```
 * # text=<base64(UTF-8)> ecc=<L/M/Q/H 的名字> version=<n> size=<n> mask=<n>
 * <size 行，每行 size 个 0/1，1 = 深色>
 * ```
 *
 * 生成器用 `boostEcl = false`、`mask = -1`（自动选掩码），所以比对时也必须传同样的参数，
 * 否则版本会被自动提升、逐格必然对不上。
 */
internal object QrGoldenVectors {

    /** 一组黄金向量。 */
    data class Vector(
        val text: String,
        val ecc: QrEcc,
        val version: Int,
        val size: Int,
        val mask: Int,
        /** `rows[y][x] == true` 表示深色。 */
        val rows: List<BooleanArray>,
    ) {
        /** 便于失败信息里定位差异：返回第一处不同的坐标，全同则返回 null。 */
        fun firstDifference(other: QrCode): String? {
            if (other.size != size) return "边长不同：向量 $size，实现 ${other.size}"
            if (other.version != version) return "版本不同：向量 $version，实现 ${other.version}"
            if (other.mask != mask) return "掩码不同：向量 $mask，实现 ${other.mask}"
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (rows[y][x] != other.isDark(x, y)) {
                        return "首个不同的模块在 (x=$x, y=$y)：向量 ${asChar(rows[y][x])}，实现 ${asChar(other.isDark(x, y))}"
                    }
                }
            }
            return null
        }

        private fun asChar(dark: Boolean): String = if (dark) "深" else "浅"
    }

    private const val PREFIX = "# text="
    private const val HEADER_PREFIX = "#"
    private const val HEADER_SPACE = "# "
    private const val DARK = '1'

    /** 按出现顺序返回全部向量；解析失败直接抛，避免测试静默少跑几组。 */
    fun load(): List<Vector> {
        val stream = QrGoldenVectors::class.java.classLoader
            ?.getResourceAsStream("qr/vectors.txt")
            ?: error("找不到测试资源 qr/vectors.txt")
        val lines = stream.bufferedReader(Charsets.UTF_8).use { it.readLines() }
        return parse(lines)
    }

    fun parse(lines: List<String>): List<Vector> {
        val vectors = ArrayList<Vector>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.startsWith(PREFIX)) {
                require(line.startsWith(HEADER_PREFIX)) { "第 ${i + 1} 行既不是组头也不是注释：$line" }
                i++
                continue
            }
            val fields = parseFields(line.removePrefix(HEADER_SPACE))
            val size = fields.getValue("size").toInt()
            val rows = ArrayList<BooleanArray>(size)
            for (r in 1..size) {
                val row = lines[i + r]
                require(row.length == size) { "第 ${i + r + 1} 行长度 ${row.length} ≠ size $size" }
                rows.add(BooleanArray(size) { c -> row[c] == DARK })
            }
            vectors.add(
                Vector(
                    text = String(decodeBase64(fields.getValue("text")), Charsets.UTF_8),
                    ecc = QrEcc.valueOf(fields.getValue("ecc")),
                    version = fields.getValue("version").toInt(),
                    size = size,
                    mask = fields.getValue("mask").toInt(),
                    rows = rows,
                ),
            )
            i += size + 1
        }
        require(vectors.isNotEmpty()) { "向量文件里一组数据都没有" }
        return vectors
    }

    /** `text=... ecc=... version=... size=... mask=...` → Map。 */
    private fun parseFields(rest: String): Map<String, String> =
        rest.split(' ')
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val at = pair.indexOf('=')
                require(at > 0) { "字段格式不对：$pair" }
                pair.substring(0, at) to pair.substring(at + 1)
            }

    private fun decodeBase64(text: String): ByteArray = java.util.Base64.getDecoder().decode(text)
}
