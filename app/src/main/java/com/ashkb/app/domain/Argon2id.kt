package com.ashkb.app.domain

/**
 * Argon2（RFC 9106）—— 纯 Kotlin、零第三方依赖。
 *
 * ### 为什么自己写
 * 本项目的硬约束是零第三方依赖，而 `minSdk 31` 的 `javax.crypto` 与 Android
 * 平台都没有 Argon2 实现（只有 PBKDF2/Scrypt 这类）。口令派生是加密存储的
 * 地基，不能因为「没有库」就退回 PBKDF2，所以自带一份。
 *
 * ### 验证方式（上一轮二维码翻车的直接教训）
 * 上一版二维码用「自己编、自己解」做自测，全绿但真机扫不出来——因为编解码
 * 犯的是同一个错。自证无法排除这种错误。因此本实现的每条断言的期望值都来自
 * **独立参考实现** `@noble/hashes` 1.7.1（MIT，纯 JS），生成脚本见
 * `app/src/test/resources/argon2/vectors.txt` 的头部说明；该参考实现本身能
 * 逐字节复现 RFC 9106 §5.1/§5.2/§5.3 的三组官方 Tag，证明它可信。
 *
 * ### 分层
 * · 本文件 [Argon2id]          参数校验、H0、首块填充、分段调度、收尾
 * · 本文件 [Argon2Blake2b]     BLAKE2b-512 与变长哈希 H'
 * · 本文件 [Argon2Core]        压缩函数 G、fill_block、参考块索引
 *
 * ### 只对外暴露 Argon2id
 * 内部按 RFC 的通用结构写（`type` 是参数），所以 Argon2d/Argon2i 只是换一个
 * 类型号；[hashWithType] 仅用于测试期跑 RFC 的 d/i 官方向量，生产请用 [hash]。
 *
 * ### 线程安全
 * 每次调用自建 [Argon2Workspace]（持有整块内存与暂存区），因此无共享可变状态，
 * 可并发调用。多 lane 是**顺序**填充而非真并行：RFC 保证同一 slice 内各 lane
 * 互不引用，顺序填充与并行填充逐字节等价，只是慢一点。
 */
internal object Argon2id {

    /**
     * 计算 Argon2id 摘要。
     *
     * 默认参数取 RFC 9106 §4 的第二推荐档（`m=19 MiB, t=2, p=1`）——它是
     * 「内存受限环境」的推荐值，适合移动端；OWASP 的 `m=64 MiB, t=3, p=4`
     * 在本机 JVM 上实测约数秒量级，放到中低端手机上会明显卡顿，故不作默认。
     *
     * @param password 口令的原始字节（调用方负责先做 UTF-8 编码，不要先哈希）。
     * @param salt 盐，至少 8 字节；每次注册/改密都必须重新生成。
     * @param memoryKiB 内存开销，单位 KiB。内部会向下取整到 `4 * parallelism` 的
     *   整数倍（RFC 9106 §3.2 的 `m'`），所以 `100` 与 `96`（p=2 时）等价。
     * @param iterations 遍数 `t`，至少 1。
     * @param parallelism 并行度 `p`，至少 1。只影响速度与内存布局，不影响正确性。
     * @param tagLength 输出长度，至少 4；`<= 64` 与 `> 64` 走 H' 的两条不同分支。
     * @param secret 可选的「胡椒」（RFC 9106 的 `K`），长度 0 表示不使用。
     *   与 salt 的区别：secret 不进数据库，只存于进程内存/密钥库。
     * @param associatedData 可选的关联数据（RFC 9106 的 `X`），参与预哈希但不进摘要。
     * @return 长度为 [tagLength] 的摘要。
     * @throws IllegalArgumentException 任一参数越界时。
     */
    fun hash(
        password: ByteArray,
        salt: ByteArray,
        memoryKiB: Int = DEFAULT_MEMORY_KIB,
        iterations: Int = DEFAULT_ITERATIONS,
        parallelism: Int = DEFAULT_PARALLELISM,
        tagLength: Int = DEFAULT_TAG_LENGTH,
        secret: ByteArray = ByteArray(0),
        associatedData: ByteArray = ByteArray(0),
    ): ByteArray = hashWithType(
        type = TYPE_ARGON2ID,
        options = Argon2Options(memoryKiB, iterations, parallelism, tagLength),
        password = password,
        salt = salt,
        secret = secret,
        associatedData = associatedData,
    )

    /**
     * 带显式类型号的入口，仅供测试（[Argon2idTest] 用它跑 RFC 9106 §5.1/§5.2
     * 的 Argon2d/Argon2i 官方向量）。生产代码不应直接调用。
     */
    internal fun hashWithType(
        type: Int,
        options: Argon2Options,
        password: ByteArray,
        salt: ByteArray,
        secret: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        validate(type, options, salt)
        val workspace = Argon2Workspace(type, options)
        val seed = initialHash(type, options, password, salt, secret, associatedData)
        fillFirstBlocks(workspace, seed)
        for (pass in 0 until options.iterations) {
            for (slice in 0 until SYNC_POINTS) {
                for (lane in 0 until options.parallelism) {
                    fillSegment(workspace, pass, slice, lane)
                }
            }
        }
        return finalizeTag(workspace, options.tagLength)
    }

    // ------------------------------ 参数校验 ------------------------------

    private fun validate(type: Int, options: Argon2Options, salt: ByteArray) {
        require(type == TYPE_ARGON2D || type == TYPE_ARGON2I || type == TYPE_ARGON2ID) {
            "未知的 Argon2 类型号：$type（只支持 0=d / 1=i / 2=id）"
        }
        require(salt.size >= MIN_SALT_BYTES) { "salt 至少 ${MIN_SALT_BYTES} 字节，实际 ${salt.size}" }
        require(options.tagLength >= MIN_TAG_BYTES) {
            "tagLength 至少 ${MIN_TAG_BYTES} 字节，实际 ${options.tagLength}"
        }
        require(options.iterations >= 1) { "iterations 至少 1，实际 ${options.iterations}" }
        require(options.parallelism >= 1) { "parallelism 至少 1，实际 ${options.parallelism}" }
        // RFC 9106 §3.1：m 必须 >= 8p，否则 m' 会退化成 0 个 block。
        require(options.memoryKiB >= MIN_MEMORY_PER_LANE * options.parallelism) {
            "memoryKiB 至少 ${MIN_MEMORY_PER_LANE * options.parallelism}（8 * p），实际 ${options.memoryKiB}"
        }
    }

    // ------------------------------ 预哈希 H0 ------------------------------

    /**
     * H0 = BLAKE2b-512(LE32(p) ‖ LE32(τ) ‖ LE32(m) ‖ LE32(t) ‖ LE32(v) ‖ LE32(y)
     *                  ‖ LE32(|P|) ‖ P ‖ LE32(|S|) ‖ S ‖ LE32(|K|) ‖ K ‖ LE32(|X|) ‖ X)
     *
     * 注意这里写进 H0 的是**用户请求的 m**，不是对齐后的 m'：参考实现同样如此，
     * 若写成 m' 则所有参数非 4p 整数倍的向量都会对不上。
     */
    private fun initialHash(
        type: Int,
        options: Argon2Options,
        password: ByteArray,
        salt: ByteArray,
        secret: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val total = PREAMBLE_FIELDS * UINT32_BYTES +
            inputFieldSize(password) + inputFieldSize(salt) +
            inputFieldSize(secret) + inputFieldSize(associatedData)
        val out = ByteArray(total)
        var at = 0
        at = putLe32(out, at, options.parallelism)
        at = putLe32(out, at, options.tagLength)
        at = putLe32(out, at, options.memoryKiB)
        at = putLe32(out, at, options.iterations)
        at = putLe32(out, at, VERSION_13)
        at = putLe32(out, at, type)
        at = putBytes(out, at, password)
        at = putBytes(out, at, salt)
        at = putBytes(out, at, secret)
        putBytes(out, at, associatedData)
        return Argon2Blake2b.blake2b(BLAKE2B_MAX_OUT_BYTES, out)
    }

    private fun inputFieldSize(data: ByteArray): Int = UINT32_BYTES + data.size

    private fun putBytes(out: ByteArray, at: Int, data: ByteArray): Int {
        var cursor = putLe32(out, at, data.size)
        data.copyInto(out, cursor)
        cursor += data.size
        return cursor
    }

    // ---------------------------- 首块 B[i][0..1] ----------------------------

    /**
     * B[i][0] = H'^1024(H0 ‖ LE32(0) ‖ LE32(i))，B[i][1] 同理但第二字段是 1。
     * H' 输出 1024 字节，正好一个 block。
     */
    private fun fillFirstBlocks(workspace: Argon2Workspace, seed: ByteArray) {
        val input = ByteArray(seed.size + 2 * UINT32_BYTES)
        seed.copyInto(input)
        for (lane in 0 until workspace.parallelism) {
            for (index in 0 until 2) {
                putLe32(input, seed.size, index)
                putLe32(input, seed.size + UINT32_BYTES, lane)
                val blockBytes = Argon2Blake2b.blake2bLong(BLOCK_SIZE_BYTES, input)
                storeBlock(blockBytes, workspace.memory, (lane * workspace.laneLength + index) * WORDS_IN_BLOCK)
            }
        }
    }

    // ------------------------------ 分段填充 ------------------------------

    /**
     * 填充单个 (pass, slice, lane) 段。内容对应 RFC 9106 §3.4 的算法 3。
     *
     * 前两个 block 在 [fillFirstBlocks] 里已生成，所以 pass 0 / slice 0 从
     * index 2 开始；其余段都从 0 开始。
     */
    private fun fillSegment(workspace: Argon2Workspace, pass: Int, slice: Int, lane: Int) {
        val dataIndependent = isDataIndependent(workspace.type, pass, slice)
        val startIndex = if (pass == 0 && slice == 0) 2 else 0
        prepareAddressBlock(workspace, dataIndependent, pass, slice, lane)

        var curr = lane * workspace.laneLength + slice * workspace.segmentLength + startIndex
        for (index in startIndex until workspace.segmentLength) {
            // 每遍第一段的起点正好是 lane 的第 0 块，此时「前一块」要绕到同 lane
            // 的尾部（RFC 9106 §3.4 算法 3 的数据依赖步骤 1）。判断放在循环内
            // 而不是循环前：绕尾只影响这一个 block，`prev` 不能跟着累加。
            val prev = if (curr % workspace.laneLength == 0) {
                curr + workspace.laneLength - 1
            } else {
                curr - 1
            }
            val pseudoRand = if (dataIndependent) {
                Argon2Core.addressWord(workspace, index)
            } else {
                workspace.memory[prev * WORDS_IN_BLOCK]
            }
            val refLane = referenceLane(pseudoRand, pass, slice, lane, workspace.parallelism)
            val refBlock = Argon2Core.referenceIndex(
                pass = pass,
                slice = slice,
                index = index,
                pseudoRand = pseudoRand and MASK_32,
                sameLane = refLane == lane,
                laneLength = workspace.laneLength,
                segmentLength = workspace.segmentLength,
            )
            Argon2Core.fillBlock(
                workspace = workspace,
                prevOffset = prev * WORDS_IN_BLOCK,
                refOffset = (laneLengthOf(refLane, workspace) + refBlock) * WORDS_IN_BLOCK,
                currOffset = curr * WORDS_IN_BLOCK,
                withXor = pass != 0,
            )
            curr++
        }
    }

    private fun laneLengthOf(refLane: Int, workspace: Argon2Workspace): Int = refLane * workspace.laneLength

    /**
     * 参考块所在 lane：伪随机值的高 32 位对 p 取模。
     * pass 0 / slice 0 时禁止跨 lane 引用（RFC 9106 §3.4.1.2）。
     */
    private fun referenceLane(pseudoRand: Long, pass: Int, slice: Int, lane: Int, parallelism: Int): Int {
        if (pass == 0 && slice == 0) return lane
        val high = (pseudoRand ushr HIGH_WORD_SHIFT) and MASK_32
        return (high % parallelism.toLong()).toInt()
    }

    private fun isDataIndependent(type: Int, pass: Int, slice: Int): Boolean = when (type) {
        TYPE_ARGON2I -> true
        // Argon2id 只有第一遍的前半段（slice 0、1）用数据无关寻址，其余数据相关。
        TYPE_ARGON2ID -> pass == 0 && slice < SYNC_POINTS / 2
        else -> false
    }

    /**
     * 数据无关寻址需要一个「地址块」，其内容由 (pass, lane, slice, m', t, y) 决定。
     * 这些字段写进 input block 的前 6 个字，第 7 个字是调用计数器。
     */
    private fun prepareAddressBlock(
        workspace: Argon2Workspace,
        dataIndependent: Boolean,
        pass: Int,
        slice: Int,
        lane: Int,
    ) {
        if (!dataIndependent) return
        val at = workspace.inputOffset
        for (i in 0 until WORDS_IN_BLOCK) workspace.memory[at + i] = 0L
        workspace.memory[at] = pass.toLong()
        workspace.memory[at + 1] = lane.toLong()
        workspace.memory[at + 2] = slice.toLong()
        workspace.memory[at + 3] = workspace.memoryBlocks.toLong()
        workspace.memory[at + 4] = workspace.passes.toLong()
        workspace.memory[at + 5] = workspace.type.toLong()
        // 首段的前两个 block 已存在，索引从 2 开始，`index % 128 == 0` 不会在
        // 第一次循环触发，所以这里必须先手动生成一个地址块。
        if (pass == 0 && slice == 0) Argon2Core.nextAddresses(workspace)
    }

    // -------------------------------- 收尾 --------------------------------

    /**
     * TAG = H'^τ( B[0][q-1] ⊕ B[1][q-1] ⊕ … ⊕ B[p-1][q-1] )，
     * 即各 lane 最后一个 block 逐字异或后再做变长哈希（RFC 9106 §3.4 算法 4）。
     */
    private fun finalizeTag(workspace: Argon2Workspace, tagLength: Int): ByteArray {
        val last = LongArray(WORDS_IN_BLOCK)
        val base = workspace.laneLength - 1
        for (i in 0 until WORDS_IN_BLOCK) last[i] = workspace.memory[base * WORDS_IN_BLOCK + i]
        for (lane in 1 until workspace.parallelism) {
            val offset = (lane * workspace.laneLength + workspace.laneLength - 1) * WORDS_IN_BLOCK
            for (i in 0 until WORDS_IN_BLOCK) last[i] = last[i] xor workspace.memory[offset + i]
        }
        val bytes = ByteArray(BLOCK_SIZE_BYTES)
        loadBlock(last, bytes, 0)
        return Argon2Blake2b.blake2bLong(tagLength, bytes)
    }
}

/**
 * 一次计算的参数集合。
 *
 * 单独成类而不是散着传，是为了让内部函数的形参个数保持在 detekt 的
 * `LongParameterList.functionThreshold = 8` 之下——这不是形式主义：Argon2 的
 * 内部函数本来就只关心「布局」，把布局打包后签名反而更能说明意图。
 */
internal class Argon2Options(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val tagLength: Int,
)

/**
 * 一次计算的完整内存布局与暂存区。
 *
 * `memory` 在数据区之后额外挂了 3 个 block（zero / input / address），这样
 * [Argon2Core.fillBlock] 可以对所有块统一用「字偏移」寻址，不必再写一份
 * 针对小数组的重载。
 */
internal class Argon2Workspace(type: Int, options: Argon2Options) {

    val type: Int = type
    val passes: Int = options.iterations
    val parallelism: Int = options.parallelism

    /** 对齐后的块数 `m' = 4p · ⌊m / 4p⌋`（RFC 9106 §3.2）。 */
    val memoryBlocks: Int = SYNC_POINTS * options.parallelism * (options.memoryKiB / (SYNC_POINTS * options.parallelism))

    /** 每个 lane 的块数 `q = m' / p`。 */
    val laneLength: Int = memoryBlocks / options.parallelism

    /** 每段块数 `q / 4`。 */
    val segmentLength: Int = laneLength / SYNC_POINTS

    val zeroOffset: Int = memoryBlocks * WORDS_IN_BLOCK
    val inputOffset: Int = zeroOffset + WORDS_IN_BLOCK
    val addressOffset: Int = inputOffset + WORDS_IN_BLOCK

    val memory: LongArray = LongArray((memoryBlocks + 3) * WORDS_IN_BLOCK)
    val scratchR: LongArray = LongArray(WORDS_IN_BLOCK)
    val scratchTmp: LongArray = LongArray(WORDS_IN_BLOCK)
}

/**
 * BLAKE2b（RFC 7693）与 Argon2 的变长哈希 H'（RFC 9106 §3.3）。
 *
 * 只实现无密钥、单块模式：Argon2 只用到这一种。输出长度可变（1..64 字节），
 * 由 `h[0] ^= 0x01010000 ^ (kk << 8) ^ nn` 里的 `nn` 编码，这一步漏掉会导致
 * 所有向量在第一个字节就错。
 */
internal object Argon2Blake2b {

    /** H' 的两条分支：`T <= 64` 直接一次哈希，否则要分块迭代。 */
    fun blake2bLong(outLen: Int, message: ByteArray): ByteArray {
        val prefixed = ByteArray(UINT32_BYTES + message.size)
        putLe32(prefixed, 0, outLen)
        message.copyInto(prefixed, UINT32_BYTES)
        if (outLen <= BLAKE2B_MAX_OUT_BYTES) return blake2b(outLen, prefixed)

        val out = ByteArray(outLen)
        var block = blake2b(BLAKE2B_MAX_OUT_BYTES, prefixed)
        block.copyInto(out, 0, 0, HPRIME_CHUNK_BYTES)
        var produced = HPRIME_CHUNK_BYTES
        // 注意：这里刻意**不**照抄 RFC 9106 §3.3 的 `r = ceil(T/32) - 1`。那句
        // 文字与所有实际实现（C 参考 argon2、libsodium、@noble/hashes）不等价：
        // 实测实现在「剩余 > 64 字节」时才再切一个 32 字节块，于是 T=65 得到
        // 32|33 而不是 32|32|1，T=100 得到 32|32|36 而不是 32|32|32|4。两者在
        // T 为 32 的整数倍时才重合。本实现以「与参考实现逐字节一致」为准，
        // 因此采用贪心切块；最初按 RFC 文字写的那版 20 组向量全红。
        while (outLen - produced > BLAKE2B_MAX_OUT_BYTES) {
            block = blake2b(BLAKE2B_MAX_OUT_BYTES, block)
            block.copyInto(out, produced, 0, HPRIME_CHUNK_BYTES)
            produced += HPRIME_CHUNK_BYTES
        }
        val tail = blake2b(outLen - produced, block)
        tail.copyInto(out, produced)
        return out
    }

    /** 计算 `outLen` 字节的 BLAKE2b 摘要。 */
    fun blake2b(outLen: Int, message: ByteArray): ByteArray {
        val state = BLAKE2B_IV.copyOf()
        // 参数块：digest_length | key_length<<8 | fanout<<16 | depth<<24 = 0x01010000 | nn
        state[0] = state[0] xor (BLAKE2B_PARAM_BASE or outLen.toLong())
        val blockBytes = ByteArray(BLAKE2B_BLOCK_BYTES)
        val blockWords = LongArray(WORDS_PER_ROW)
        var consumed = 0
        var counter = 0L
        // 留出最后一块单独处理，因为它要带 last 标志且需要零填充。
        while (message.size - consumed > BLAKE2B_BLOCK_BYTES) {
            message.copyInto(blockBytes, 0, consumed, consumed + BLAKE2B_BLOCK_BYTES)
            counter += BLAKE2B_BLOCK_BYTES
            compress(state, loadWords(blockBytes, blockWords), counter, false)
            consumed += BLAKE2B_BLOCK_BYTES
        }
        blockBytes.fill(0)
        val rest = message.size - consumed
        message.copyInto(blockBytes, 0, consumed, consumed + rest)
        counter += rest.toLong()
        compress(state, loadWords(blockBytes, blockWords), counter, true)
        val digest = ByteArray(BLAKE2B_MAX_OUT_BYTES)
        loadWordsInto(state, digest, BLAKE2B_MAX_OUT_BYTES)
        return digest.copyOf(outLen)
    }

    private fun compress(state: LongArray, message: LongArray, counter: Long, last: Boolean) {
        val v = LongArray(2 * BLAKE2B_STATE_WORDS)
        for (i in 0 until BLAKE2B_STATE_WORDS) {
            v[i] = state[i]
            v[i + BLAKE2B_STATE_WORDS] = BLAKE2B_IV[i]
        }
        // RFC 7693 §3.2：低 128 位 v[12..13] 与计数器 t 异或；末块翻转 v[14]。
        v[BLAKE2B_COUNTER_WORD] = v[BLAKE2B_COUNTER_WORD] xor counter
        if (last) v[BLAKE2B_LAST_WORD] = v[BLAKE2B_LAST_WORD].inv()
        for (round in 0 until BLAKE2B_ROUNDS) {
            val s = (round % SIGMA_ROWS) * WORDS_PER_ROW
            for (i in 0 until GROUPS_PER_ROUND) {
                val at = i * INDEXES_PER_GROUP
                val entry = s + i * SIGMA_PER_GROUP
                g(
                    v,
                    BLAKE2B_G_INDEXES[at],
                    BLAKE2B_G_INDEXES[at + 1],
                    BLAKE2B_G_INDEXES[at + 2],
                    BLAKE2B_G_INDEXES[at + 3],
                    message[SIGMA[entry]],
                    message[SIGMA[entry + 1]],
                )
            }
        }
        for (i in 0 until BLAKE2B_STATE_WORDS) {
            state[i] = state[i] xor v[i] xor v[i + BLAKE2B_STATE_WORDS]
        }
    }

    /** BLAKE2b 的 G：普通 64 位加法，与 Argon2 压缩函数里的 fBlaMka 版不同。 */
    private fun g(v: LongArray, a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
        var va = v[a]
        var vb = v[b]
        var vc = v[c]
        var vd = v[d]
        va += vb + x
        vd = rotR(vd xor va, ROT_32)
        vc += vd
        vb = rotR(vb xor vc, ROT_24)
        va += vb + y
        vd = rotR(vd xor va, ROT_16)
        vc += vd
        vb = rotR(vb xor vc, ROT_63)
        v[a] = va
        v[b] = vb
        v[c] = vc
        v[d] = vd
    }

    /** BLAKE2b 初始向量：SHA-512 同源常量（RFC 7693 §2.6）。 */
    private val BLAKE2B_IV = longArrayOf(
        0x6a09e667f3bcc908L, -0x4498517a7b3558c5L, 0x3c6ef372fe94f82bL, -0x5ab00ac5a0e2c90fL,
        0x510e527fade682d1L, -0x64fa9773d4c193e1L, 0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L,
    )

    /** `0x01010000`：digest_length=nn、key_length=0、fanout=1、depth=1 的参数块高位。 */
    private const val BLAKE2B_PARAM_BASE = 0x01010000L

    /** 计数器写进 v[12]（v 的后半段起点 8 再加 4）。RFC 7693 §3.2。 */
    private const val BLAKE2B_COUNTER_WORD = BLAKE2B_STATE_WORDS + 4

    /** 末块标志位翻转 v[14]（8 再加 6）。 */
    private const val BLAKE2B_LAST_WORD = BLAKE2B_STATE_WORDS + 6

    /** 每轮 8 次 G，每次消耗 SIGMA 里相邻的 2 个索引。 */
    private const val SIGMA_PER_GROUP = 2

    /**
     * BLAKE2b 一轮里 8 次 G 的取字下标，每 4 个一组。
     *
     * 前 4 组是列方向的 4 条对角线，后 4 组是行方向的对角线（RFC 7693 §3.1
     * 的 G 调用顺序）。与 [Argon2Core] 的列/行表形状一致，便于对照。
     */
    private val BLAKE2B_G_INDEXES = intArrayOf(
        0, 4, 8, 12,
        1, 5, 9, 13,
        2, 6, 10, 14,
        3, 7, 11, 15,
        0, 5, 10, 15,
        1, 6, 11, 12,
        2, 7, 8, 13,
        3, 4, 9, 14,
    )

    /** sigma 只有 10 行，第 11、12 轮分别复用第 1、2 行。 */
    private const val SIGMA_ROWS = 10

    private val SIGMA = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3,
        11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4,
        7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8,
        9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13,
        2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9,
        12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11,
        13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10,
        6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5,
        10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0,
    )
}

/**
 * Argon2 的压缩函数 G 与参考块索引（RFC 9106 §3.5 / §3.4.1.3）。
 *
 * G 与 BLAKE2b 的 round 形似而神不似：它的混合用 `fBlaMka(x, y) = x + y +
 * 2·lo32(x)·lo32(y)` 而不是单纯加法。这是 Argon2 抗 GPU 的关键——把乘法塞进
 * 每一轮，使电路面积与延迟都上不去。写成普通 BLAKE2b 加法会得到一条**完全
 * 不同但看起来合理**的摘要，这正是必须用外部向量对拍的原因。
 */
internal object Argon2Core {

    /**
     * next = P(prev ⊕ ref) ⊕ (prev ⊕ ref) [⊕ next]，其中 P 对 8×8 的 16 字节
     * 寄存器矩阵先按行、再按列各做一轮 BLAKE2b 的无消息 round。
     *
     * @param withXor pass > 0 时为 true：新块与**原有内容**异或（RFC 9106 §3.4
     *   的「覆盖 vs 异或」区别）。pass 0 是覆盖，之后是异或。
     */
    fun fillBlock(workspace: Argon2Workspace, prevOffset: Int, refOffset: Int, currOffset: Int, withXor: Boolean) {
        val memory = workspace.memory
        val r = workspace.scratchR
        val tmp = workspace.scratchTmp
        for (i in 0 until WORDS_IN_BLOCK) {
            val mixed = memory[prevOffset + i] xor memory[refOffset + i]
            r[i] = mixed
            tmp[i] = if (withXor) mixed xor memory[currOffset + i] else mixed
        }
        // 列的 8 轮：每次取连续的 16 个字。
        for (row in 0 until ROWS_IN_BLOCK) roundContiguous(r, row * WORDS_PER_ROW)
        // 行的 8 轮：每次取 8 组「相邻两个字」，组间步长 16 个字。
        for (row in 0 until ROWS_IN_BLOCK) roundStrided(r, row * WORDS_PER_PAIR)
        for (i in 0 until WORDS_IN_BLOCK) memory[currOffset + i] = tmp[i] xor r[i]
    }

    /**
     * 生成下一个地址块：`address = G(zero, G(zero, input))`，每次调用先把
     * input 的第 7 个字加一——它就是 RFC 里那个「地址块序号」。
     */
    fun nextAddresses(workspace: Argon2Workspace) {
        workspace.memory[workspace.inputOffset + ADDRESS_COUNTER_WORD]++
        fillBlock(workspace, workspace.zeroOffset, workspace.inputOffset, workspace.addressOffset, false)
        fillBlock(workspace, workspace.zeroOffset, workspace.addressOffset, workspace.addressOffset, false)
    }

    /** 取第 `index` 个伪随机值；每到 128 的整数倍就先刷新地址块。 */
    fun addressWord(workspace: Argon2Workspace, index: Int): Long {
        val slot = index % ADDRESSES_IN_BLOCK
        if (slot == 0) nextAddresses(workspace)
        return workspace.memory[workspace.addressOffset + slot]
    }

    /**
     * 参考块索引（RFC 9106 §3.4.1.3）。
     *
     * 三步：① 按 pass/slice 算出「允许引用的块数」；② 用 `J1²>>32` 把均匀的
     * J1 压成偏向小值的分布（这是 RFC 的刻意设计，防止攻击者只保留高频块）；
     * ③ 平移到绝对位置并绕回本 lane。
     */
    fun referenceIndex(
        pass: Int,
        slice: Int,
        index: Int,
        pseudoRand: Long,
        sameLane: Boolean,
        laneLength: Int,
        segmentLength: Int,
    ): Int {
        val areaSize = referenceAreaSize(pass, slice, index, sameLane, laneLength, segmentLength)
        var relative = (pseudoRand * pseudoRand) ushr HIGH_WORD_SHIFT
        relative = (areaSize - 1).toLong() - ((areaSize.toLong() * relative) ushr HIGH_WORD_SHIFT)
        val start = if (pass == 0 || slice == SYNC_POINTS - 1) 0 else (slice + 1) * segmentLength
        return ((start.toLong() + relative) % laneLength).toInt()
    }

    private fun referenceAreaSize(
        pass: Int,
        slice: Int,
        index: Int,
        sameLane: Boolean,
        laneLength: Int,
        segmentLength: Int,
    ): Int {
        if (pass == 0) {
            if (slice == 0) return index - 1
            val done = slice * segmentLength
            return if (sameLane) done + index - 1 else done - (if (index == 0) 1 else 0)
        }
        val tail = laneLength - segmentLength
        return if (sameLane) tail + index - 1 else tail - (if (index == 0) 1 else 0)
    }

    /** 连续的 16 个字（列）。 */
    private fun roundContiguous(v: LongArray, base: Int) {
        for (i in 0 until GROUPS_PER_ROUND) {
            gByIndexes(v, COLUMN_INDEXES, i * INDEXES_PER_GROUP, base)
        }
    }

    /**
     * 8 组「相邻两个字」、组间步长 16 个字（行）。
     *
     * 对应 RFC 9106 §3.5 的第二个循环：第 i 次取
     * `(i, i+1), (i+16, i+17), (i+32, i+33), …` 这 16 个字喂给 P，i 步进 2。
     * 这是最容易写错的一段——写成别的步长不会崩溃、长度也对，只是摘要完全
     * 不同；本实现最初就在这里错了两对参数，靠 20 组外部向量才暴露。
     *
     * 下标之所以摊平成一张常量表而不是逐行硬编码，是因为这里一旦打错一个字
     * 不会有任何编译期或运行期提示：BLAKE2b 的 G 是对称运算，错位的下标仍会
     * 产出一条长度正确、看起来正常的摘要。表的形状按 RFC 原文的 8 行 × 4 列
     * 排布，对照原文逐格核验比对照 8 行函数调用容易得多。
     */
    private fun roundStrided(v: LongArray, base: Int) {
        for (i in 0 until GROUPS_PER_ROUND) {
            gByIndexes(v, ROW_INDEXES, i * INDEXES_PER_GROUP, base)
        }
    }

    /** 按 [indexes] 里连续 4 个偏移取字，喂给 [g]。两个轮函数共用同一套取值逻辑。 */
    private fun gByIndexes(v: LongArray, indexes: IntArray, at: Int, base: Int) {
        g(v, base + indexes[at], base + indexes[at + 1], base + indexes[at + 2], base + indexes[at + 3])
    }

    /** Argon2 特化的 G：混合步用 [fBlaMka] 而非加法。 */
    private fun g(v: LongArray, a: Int, b: Int, c: Int, d: Int) {
        var va = v[a]
        var vb = v[b]
        var vc = v[c]
        var vd = v[d]
        va = fBlaMka(va, vb)
        vd = rotR(vd xor va, ROT_32)
        vc = fBlaMka(vc, vd)
        vb = rotR(vb xor vc, ROT_24)
        va = fBlaMka(va, vb)
        vd = rotR(vd xor va, ROT_16)
        vc = fBlaMka(vc, vd)
        vb = rotR(vb xor vc, ROT_63)
        v[a] = va
        v[b] = vb
        v[c] = vc
        v[d] = vd
    }

    /** 地址块里第 7 个字（下标 6）是计数器。 */
    private const val ADDRESS_COUNTER_WORD = 6

    /**
     * 行轮每次的 base 步长：2 个字。
     *
     * RFC 9106 §3.5 第二个循环的 `i` 步进 2（`i` 取 0,2,…,14），第 `i` 次取
     * `(i, i+1), (i+16, i+17), …` 共 16 个字。所以 base 是 `row * 2`，不是
     * `row * 16`——这一点与列轮相反，写错不会报错，只会得到另一条摘要。
     */
    private const val WORDS_PER_PAIR = 2

    /**
     * 列轮的下标：16 个字摊成 4×4 方阵，先走 4 条列、再走 4 条对角线。
     *
     * 保留成表格而不是 32 个命名常量：抽成常量会丢掉「哪 4 个字成一组」的形状，
     * 反而更难对照 RFC。detekt 的 `ignorePropertyDeclaration` 让表内数字免报
     * MagicNumber，同时这里是 `val` 而非 `const val`，语义上也确实是数组。
     */
    private val COLUMN_INDEXES = intArrayOf(
        0, 4, 8, 12,
        1, 5, 9, 13,
        2, 6, 10, 14,
        3, 7, 11, 15,
        0, 5, 10, 15,
        1, 6, 11, 12,
        2, 7, 8, 13,
        3, 4, 9, 14,
    )

    /**
     * 行轮的下标，对应 RFC 9106 §3.5 第二个循环的 8 次 P 调用。
     *
     * 每 4 个数是一次 `g()` 的四个入参（相对 [roundStrided] 的 base）。排成
     * 8 行 × 4 列后可以逐行对照 RFC 原文核验——这里错一个字只会得到一条
     * 「长度正确、看着正常、其实完全不同」的摘要，没有任何运行期提示。
     */
    private val ROW_INDEXES = intArrayOf(
        0, 32, 64, 96,
        1, 33, 65, 97,
        16, 48, 80, 112,
        17, 49, 81, 113,
        0, 33, 80, 113,
        1, 48, 81, 96,
        16, 49, 64, 97,
        17, 32, 65, 112,
    )
}

/* ============================ 文件级常量 ============================ */

/**
 * 取 64 位字的高 32 位时右移的位数。
 *
 * 两处用它：一是伪随机值的高 32 位决定参考 lane（RFC 9106 §3.4.1.2 的 `l = J2
 * mod p`）；二是 §3.4.1.3 里把 `J1` 压成偏小分布的 `J1² >> 32`。两处都是「取
 * 一个 64 位乘积/值的高半」，同一个数，故共用一个常量而不是写两个名字。
 */
private const val HIGH_WORD_SHIFT = 32

/** 一次 round 里有 8 组 G（BLAKE2b 与 Argon2 的压缩函数都是这个形状）。 */
private const val GROUPS_PER_ROUND = 8

/** 每组 G 吃 4 个字，所以下标表里每 4 项是一组。 */
private const val INDEXES_PER_GROUP = 4

/** 一个 Argon2 block 的 64-bit 字数：1024 字节 ÷ 8。 */
private const val WORDS_IN_BLOCK = 128

/** 一个 block 的字节数（RFC 9106 §3.2）。 */
private const val BLOCK_SIZE_BYTES = 1024

/** 每个 pass 切成 4 个同步段（RFC 9106 的 `SL`）。 */
private const val SYNC_POINTS = 4

/** 数据无关寻址时一个地址块能提供 128 个伪随机值。 */
private const val ADDRESSES_IN_BLOCK = 128

/** 类型编号 `y`：0 = Argon2d，1 = Argon2i，2 = Argon2id。 */
private const val TYPE_ARGON2D = 0
private const val TYPE_ARGON2I = 1
private const val TYPE_ARGON2ID = 2

/** 版本号 19（0x13），RFC 9106 定稿版本。 */
private const val VERSION_13 = 0x13

/** BLAKE2b 输出上限、块大小、内部状态字数、轮数（RFC 7693）。 */
private const val BLAKE2B_MAX_OUT_BYTES = 64
private const val BLAKE2B_BLOCK_BYTES = 128
private const val BLAKE2B_STATE_WORDS = 8
private const val BLAKE2B_ROUNDS = 12

/** H' 的分块宽度：每块 32 字节（RFC 9106 §3.3）。 */
private const val HPRIME_CHUNK_BYTES = 32

/** RFC 9106 §3.1 的下界。 */
private const val MIN_SALT_BYTES = 8
private const val MIN_TAG_BYTES = 4
private const val MIN_MEMORY_PER_LANE = 8

/** block 是 8×16 个 64-bit 字。 */
private const val ROWS_IN_BLOCK = 8
private const val WORDS_PER_ROW = 16

/** 一个 u32 占 4 字节；H0 的前导有 6 个 u32 字段。 */
private const val UINT32_BYTES = 4
private const val PREAMBLE_FIELDS = 6

/** 位运算常数。 */
private const val BITS_IN_LONG = 64
private const val BITS_PER_BYTE = 8
private const val BYTE_MASK = 0xFF
private const val MASK_32 = 0xFFFFFFFFL

/** 旋转量（RFC 9106 §3.5 的四个固定值）。 */
private const val ROT_32 = 32
private const val ROT_24 = 24
private const val ROT_16 = 16
private const val ROT_63 = 63

/** 默认参数：RFC 9106 §4 的第二推荐档，面向内存受限的移动端。 */
private const val DEFAULT_MEMORY_KIB = 19456
private const val DEFAULT_ITERATIONS = 2
private const val DEFAULT_PARALLELISM = 1
private const val DEFAULT_TAG_LENGTH = 32

/** 循环右移 64 位字。 */
private fun rotR(x: Long, n: Int): Long = (x ushr n) or (x shl (BITS_IN_LONG - n))

/** Argon2 的混合函数：`x + y + 2·lo32(x)·lo32(y)`，全部模 2^64。 */
private fun fBlaMka(x: Long, y: Long): Long =
    x + y + 2 * (x and MASK_32) * (y and MASK_32)

/** 小端写入一个 u32，返回写入后的游标。 */
private fun putLe32(out: ByteArray, at: Int, value: Int): Int {
    out[at] = (value and BYTE_MASK).toByte()
    out[at + 1] = ((value ushr BITS_PER_BYTE) and BYTE_MASK).toByte()
    out[at + 2] = ((value ushr (2 * BITS_PER_BYTE)) and BYTE_MASK).toByte()
    out[at + 3] = ((value ushr (3 * BITS_PER_BYTE)) and BYTE_MASK).toByte()
    return at + UINT32_BYTES
}

/** 把 [count] 字节小端读成 64-bit 字。 */
private fun loadWords(bytes: ByteArray, out: LongArray): LongArray {
    for (i in out.indices) {
        var word = 0L
        for (k in 0 until BITS_PER_BYTE) {
            word = word or ((bytes[i * BITS_PER_BYTE + k].toLong() and BYTE_MASK.toLong()) shl (BITS_PER_BYTE * k))
        }
        out[i] = word
    }
    return out
}

/** 把 64-bit 字小端写回字节。 */
private fun loadWordsInto(words: LongArray, out: ByteArray, byteCount: Int) {
    for (i in 0 until byteCount / BITS_PER_BYTE) {
        val word = words[i]
        for (k in 0 until BITS_PER_BYTE) {
            out[i * BITS_PER_BYTE + k] = ((word ushr (BITS_PER_BYTE * k)) and BYTE_MASK.toLong()).toByte()
        }
    }
}

/** 把一个 1024 字节块整块读进 `memory` 的指定字偏移。 */
private fun storeBlock(bytes: ByteArray, memory: LongArray, at: Int) {
    for (i in 0 until WORDS_IN_BLOCK) {
        var word = 0L
        for (k in 0 until BITS_PER_BYTE) {
            word = word or ((bytes[i * BITS_PER_BYTE + k].toLong() and BYTE_MASK.toLong()) shl (BITS_PER_BYTE * k))
        }
        memory[at + i] = word
    }
}

/** 把 `memory` 的一段整块写回 1024 字节。 */
private fun loadBlock(words: LongArray, bytes: ByteArray, at: Int) {
    for (i in 0 until WORDS_IN_BLOCK) {
        val word = words[i]
        for (k in 0 until BITS_PER_BYTE) {
            bytes[at + i * BITS_PER_BYTE + k] =
                ((word ushr (BITS_PER_BYTE * k)) and BYTE_MASK.toLong()).toByte()
        }
    }
}
