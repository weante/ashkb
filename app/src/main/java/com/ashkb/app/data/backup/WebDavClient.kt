package com.ashkb.app.data.backup

import java.io.ByteArrayOutputStream
import java.lang.reflect.Field
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

import com.ashkb.app.domain.AttachmentPath

/**
 * WebDAV 客户端（零依赖实现：HttpURLConnection）。
 * 协议 §4 探针：PROPFIND(0) 地址检查 → MKCOL 确保目录 → PUT 写探针 → GET 回读 → DELETE 清探针；
 * 写入即验证：PUT 后 GET 回读比对 SHA-256；轮换：按文件名日期计算保留集后 DELETE。
 *
 * HttpURLConnection 不接受 PROPFIND / MKCOL 标准方法集，用反射覆写 method 字段
 * （Android 的 HttpURLConnectionImpl 为公开类字段，非隐藏 API，失败则降级跳过）。
 */
class WebDavClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String,
) {
    class DavException(msg: String) : Exception(msg)

    /** W4：远程备份条目——name 含备份日期，size 供列表展示（服务器未报告时为 -1）。 */
    data class DavBackupFile(val name: String, val size: Long, val modified: String)

    /** v1.0.36：PROPFIND Depth:1 的一项——名字 + 是否集合（目录）。 */
    data class DavEntry(val name: String, val isDir: Boolean)

    private fun normalizedRoot(): String = serverUrl.trim().trimEnd('/')

    private fun url(path: String): URL {
        val root = normalizedRoot()
        val full = if (path.isEmpty()) root else "$root/$path"
        val u = URL(full.replace(" ", "%20"))
        // v1.0.43：客户端自校验 scheme。此前唯一防线是 BackupRepository.requireHttps，而它是
        // 「拦 http://」的黑名单——任何非 http:// 的畸形基址都能带着 Basic 凭据发出去。
        if (!u.protocol.equals("https", ignoreCase = true))
            throw DavException("WebDAV 基址必须是 https:// 地址（当前为 ${u.protocol}://）")
        return u
    }

    private fun open(path: String, method: String, depth: Int? = null): HttpURLConnection {
        val conn = url(path).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        val auth = Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        conn.setRequestProperty("Authorization", "Basic $auth")
        conn.setRequestProperty("User-Agent", "ASHKB-Backup/1.0")
        if (depth != null) conn.setRequestProperty("Depth", depth.toString())
        try {
            conn.requestMethod = method
        } catch (_: java.net.ProtocolException) {
            forceMethod(conn, method)
        }
        if (conn is HttpsURLConnection) runCatching { conn.sslSocketFactory } // 触发默认 SSL 初始化
        return conn
    }

    /**
     * 反射覆写非 RFC 标准方法（PROPFIND / MKCOL）。
     * HTTPS 连接的 method 字段不在包装类（HttpsURLConnectionImpl）上，而在其 delegate
     * 字段指向的 HttpURLConnectionImpl 上——对包装类直接 getDeclaredField("method") 会
     * 找到继承自 java.net.HttpURLConnection 的无效影子字段，覆写后请求仍按 GET 发出
     * （表现为「MKCOL 失败：HTTP 404」）。须先解引用 delegate，再沿类层级向上找字段。
     */
    private fun forceMethod(conn: HttpURLConnection, method: String) {
        var target: Any? = conn
        while (target != null) {
            val df = findField(target.javaClass, "delegate")
            if (df != null) {
                df.isAccessible = true
                target = df.get(target)
                continue
            }
            val mf = findField(target.javaClass, "method")
            if (mf != null) {
                mf.isAccessible = true
                mf.set(target, method)
                return
            }
            break
        }
        throw DavException("当前 Android 网络栈无法发送 $method 方法（反射覆写失败），WebDAV 目录操作不可用")
    }

    private fun findField(start: Class<*>, name: String): Field? {
        var c: Class<*>? = start
        while (c != null) {
            try {
                return c.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
            }
            c = c.superclass
        }
        return null
    }

    /**
     * 探针：PROPFIND 地址检查 → MKCOL 确保目录 → PUT 写探针 → GET 回读 → DELETE 清理。
     * 报错区分地址不存在（404）/ 认证失败（401）/ 无权限（403）/ 需跳转（3xx）。
     */
    fun probe(): String {
        val root = normalizedRoot()
        // 1. PROPFIND 根地址。部分服务器不接受空 body 的 PROPFIND（返回 400/501），此时跳过检查。
        val rc = propfindCode()
        when {
            rc in 200..299 -> {}
            rc == 401 -> throw DavException(
                "认证失败（HTTP 401）：WebDAV 账号或密码错误。" +
                    "坚果云需用注册邮箱 + 应用密码（在网页端「账户信息 → 安全选项」生成，不是登录密码）"
            )
            rc == 403 -> throw DavException("认证通过但无权限访问该目录（HTTP 403）")
            rc == 404 -> throw DavException(
                "服务器上不存在此地址（HTTP 404）：$root\n" +
                    "请检查 WebDAV 地址是否正确：坚果云为 https://dav.jianguoyun.com/dav/ ，" +
                    "其他服务以服务商说明为准。地址须指向服务器上已存在的目录，应用会在其下自动创建 ashkb/backup"
            )
            rc in 300..399 -> throw DavException(
                "服务器要求跳转（HTTP $rc）：请改用跳转后的最终地址作为 WebDAV 地址"
            )
        }
        // 2. MKCOL 目录（已存在返回 405，忽略）
        mkcol("ashkb")
        mkcol("ashkb/backup")
        // 3. PUT 写探针
        val probe = "ashkb-probe-${System.currentTimeMillis()}"
        put("ashkb/backup/.probe-$probe", probe.toByteArray(Charsets.UTF_8))
        // 4. GET 回读比对
        val back = get("ashkb/backup/.probe-$probe")
        if (back.decodeToString() != probe) throw DavException("写探针回读不一致（目录可写但读取异常）")
        // 5. DELETE 清探针
        delete("ashkb/backup/.probe-$probe")
        return "连接成功：地址检查 / 目录创建 / 探针写入 / 回读 / 清理全部通过"
    }

    private fun propfindCode(): Int {
        val conn = open("", "PROPFIND", depth = 0)
        return try { conn.responseCode } finally { conn.disconnect() }
    }

    private fun mkcol(path: String) {
        val conn = open(path, "MKCOL")
        try {
            val code = conn.responseCode
            if (code !in 200..299 && code != 405)
                throw DavException(
                    "无法创建目录（HTTP $code）：${url(path)}\n" +
                        "403 = 账号无创建文件夹权限；404 = 上级目录不存在"
                )
        } finally { conn.disconnect() }
    }

    fun upload(name: String, data: ByteArray): String {
        put("ashkb/backup/$name", data)
        // 写入即验证：回读比对 SHA-256（协议 §4）
        val back = get("ashkb/backup/$name")
        val shaUp = BackupEngine.sha256Hex(data)
        val shaBack = BackupEngine.sha256Hex(back)
        if (shaUp != shaBack) {
            delete("ashkb/backup/$name")
            throw DavException("上传后回读校验失败（已删除该份）")
        }
        return "上传成功且回读一致（SHA-256 $shaUp.take(12)…）"
    }

    fun download(name: String): ByteArray = get("ashkb/backup/$name")

    // ======================= v12（v1.0.35）附件同步 =======================
    //
    // 附件**逐个**上传（不打包）：单文件远小于服务商单文件上限，且天然是增量——只传新增，
    // 流量最省。目录 ashkb/attachments/<YYYY-MM-DD>/，命名口径见 domain/AttachmentPath。

    /** 确保附件日期目录存在。mkcol 对 405（已存在）不报错，天然幂等。 */
    fun ensureAttachmentDir(dateFolder: String) {
        mkcol("ashkb")
        mkcol(AttachmentPath.DIR)
        mkcol("${AttachmentPath.DIR}/$dateFolder")
    }

    /**
     * 上传附件密文。**刻意不做上传后回读**——DB 备份会回读比对（协议 §4），
     * 但附件逐个回读会让流量翻倍（几十个附件就是双倍），改为记录本地密文 SHA-256，
     * 恢复/懒下载时再校验。
     */
    fun uploadAttachment(remotePath: String, data: ByteArray) {
        requirePath(remotePath)
        put(AttachmentPath.fullPathOf(remotePath), data)
    }

    fun downloadAttachment(remotePath: String): ByteArray {
        requirePath(remotePath)
        return get(AttachmentPath.fullPathOf(remotePath))
    }

    /** 删除远端附件；404 视为已不存在（幂等，重试安全）。 */
    fun deleteAttachment(remotePath: String) {
        requirePath(remotePath)
        delete(AttachmentPath.fullPathOf(remotePath))
    }

    /** 拼 URL 前的最后一道防线：只放行本应用生成的路径形状，防备份脏值拼出越权路径。
     *  v1.0.43：改用 [AttachmentPath.isManagedRemotePath]（含日期目录校验），与「可清理形状」同强度。 */
    private fun requirePath(remotePath: String) {
        if (!AttachmentPath.isManagedRemotePath(remotePath))
            throw DavException("非法附件路径：$remotePath")
    }

    /**
     * v1.0.36：列出远端全部附件相对路径（`<日期>/<附件id>.enc`）。
     *
     * 先 Depth:1 列 `ashkb/attachments/` 得日期子目录，再逐个列文件——坚果云等对
     * Depth:infinity 支持不一，两级 Depth:1 最稳。目录不存在（404）返回空集：
     * 「从未同步过」是正常态而非错误。只收 `isManagedRemotePath` 认可的形状。
     */
    fun listAttachmentRemotePaths(): Set<String> {
        val rootXml = propfind("${AttachmentPath.DIR}/") ?: return emptySet()
        val folders = parseDavEntries(rootXml)
            .filter { it.isDir && AttachmentPath.isValidFolder(it.name) }
            .map { it.name }
        val out = mutableSetOf<String>()
        folders.forEach { folder ->
            val xml = propfind("${AttachmentPath.DIR}/$folder/") ?: return@forEach
            parseDavEntries(xml)
                .filter { !it.isDir && it.name.endsWith(AttachmentPath.EXT) }
                .forEach { e ->
                    val p = "$folder/${e.name}"
                    if (AttachmentPath.isManagedRemotePath(p)) out.add(p)
                }
        }
        return out
    }

    /** PROPFIND Depth:1；404（目录不存在）返回 null，其余非 2xx 抛错（校验必须知道失败原因）。 */
    private fun propfind(path: String): String? {
        val conn = open(path, "PROPFIND", depth = 1)
        return try {
            val code = conn.responseCode
            when {
                code == 404 -> null
                code !in 200..299 -> throw DavException("服务器拒绝列出目录（HTTP $code）——无法校验远端附件")
                else -> conn.inputStream.use { it.readBytes().decodeToString() }
            }
        } finally { conn.disconnect() }
    }

    private fun put(path: String, data: ByteArray) {
        val conn = open(path, "PUT")
        try {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(data.size)
            conn.outputStream.use { it.write(data) }
            val code = conn.responseCode
            if (code !in 200..299 && code != 201 && code != 204)
                throw DavException("PUT 失败：HTTP $code ${conn.responseMessage}")
        } finally { conn.disconnect() }
    }

    private fun get(path: String): ByteArray {
        val conn = open(path, "GET")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw DavException("GET 失败：HTTP $code")
            val buf = ByteArrayOutputStream()
            conn.inputStream.use { it.copyTo(buf) }
            return buf.toByteArray()
        } finally { conn.disconnect() }
    }

    private fun delete(path: String) {
        val conn = open(path, "DELETE")
        try {
            val code = conn.responseCode
            if (code !in 200..299 && code != 404) throw DavException("DELETE 失败：HTTP $code")
        } finally { conn.disconnect() }
    }

    /**
     * 轮换清理（协议 §4：日 7 + 周 4 + 月 6）。
     * W1：PROPFIND Depth:1 一次列目录，只 DELETE 服务器上真实存在且超窗的备份——
     * 原实现逐日盲发 173 个 DELETE（多数 404 也各要一次完整 TLS 握手），坚果云上
     * 数分钟无反馈；列目录失败（服务器禁列）则本轮跳过，下轮再清，不阻塞备份。
     * X2：文件名带时间戳后同日可有多份——按日分组，保留集合内每天只留最晚一份。
     */
    fun rotate(keepDaily: Int = 7, keepWeekly: Int = 4, keepMonthly: Int = 6): List<String> {
        val today = java.time.LocalDate.now()
        val keepDates = buildSet {
            (0 until keepDaily).forEach { add(today.minusDays(it.toLong())) }
            (0 until keepWeekly).forEach { add(today.minusWeeks(it.toLong())) }
            (0 until keepMonthly).forEach { add(today.minusMonths(it.toLong())) }
        }.map { it.toString() }.toSet()
        val existing = listBackupFileNames()
        val keep = dailyKeep(existing, keepDates)
        val removed = mutableListOf<String>()
        existing.filter { it !in keep }.forEach { name ->
            val conn = open("ashkb/backup/$name", "DELETE")
            try {
                if (conn.responseCode in 200..299) removed.add(name)
            } catch (_: Exception) { /* 网络抖动忽略，下次再清 */ }
            finally { conn.disconnect() }
        }
        return removed
    }

    /** PROPFIND Depth:1 列 backup 目录；任何失败都返回空（轮换跳过，不影响备份主流程）。 */
    private fun listBackupFileNames(): List<String> {
        val conn = open("ashkb/backup/", "PROPFIND", depth = 1)
        return try {
            if (conn.responseCode !in 200..299) return emptyList()
            val xml = conn.inputStream.use { it.readBytes().decodeToString() }
            parseBackupFileNames(xml)
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * W4：PROPFIND Depth:1 列 backup 目录的备份文件（含大小/修改时间）。
     * 与轮换用的 listBackupFileNames 不同——恢复场景用户必须知道失败原因，异常直接抛出。
     */
    fun listBackupFiles(): List<DavBackupFile> {
        val conn = open("ashkb/backup/", "PROPFIND", depth = 1)
        return try {
            val code = conn.responseCode
            if (code !in 200..299)
                throw DavException("服务器拒绝列出目录（HTTP $code）——无法获取远程备份列表")
            val xml = conn.inputStream.use { it.readBytes().decodeToString() }
            parseDavBackups(xml)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** 从 PROPFIND 多状态响应中提取本应用的备份文件名（纯 JVM 可单测）。 */
        fun parseBackupFileNames(xml: String): List<String> =
            Regex(">([^<>]*ashkb-backup-[^<>]*\\.ashkb)<").findAll(xml)
                .map { m -> m.groupValues[1].substringAfterLast('/').substringBefore('?') }
                .filter { it.startsWith("ashkb-backup-") && it.endsWith(".ashkb") }
                .distinct()
                .toList()

        /**
         * X2：备份文件名 → 可比较的规范化 key。
         * 新格式（带 HHmmss）取原样；旧按日格式补 -000000（视为当日 00:00:00，
         * 避免裸字典序里 '.' > '-' 把旧文件排到新文件之后）。
         */
        fun backupSortKey(name: String): String {
            val stem = name.removePrefix("ashkb-backup-").removeSuffix(".ashkb")
            return if (stem.length > 10) stem else "$stem-000000"
        }

        /**
         * X2：按日分组的轮换保留集合——保留日期内每天只留最晚一份（同日多份时间戳不
         * 互相覆盖，但在下次轮换时旧的清掉，维持「日 7 + 周 4 + 月 6」的总量语义）。
         */
        fun dailyKeep(existing: List<String>, keepDates: Set<String>): Set<String> =
            existing
                .groupBy { it.removePrefix("ashkb-backup-").take(10) }
                .mapNotNull { (day, names) ->
                    if (day in keepDates) names.maxByOrNull { backupSortKey(it) } else null
                }
                .toSet()

        /**
         * W4：按 response 块解析远程备份条目（href + getcontentlength + getlastmodified；
         * 纯 JVM 可单测）。目录项与非 .ashkb 干扰文件被过滤；同名去重，按备份时间倒序（最新在前）。
         */
        fun parseDavBackups(xml: String): List<DavBackupFile> {
            val block = Regex("<(?:\\w+:)?response[^>]*>(.*?)</(?:\\w+:)?response>", RegexOption.DOT_MATCHES_ALL)
            val href = Regex("<(?:\\w+:)?href>([^<]+)<")
            val len = Regex("<(?:\\w+:)?getcontentlength>(\\d+)<")
            val mod = Regex("<(?:\\w+:)?getlastmodified>([^<]+)<")
            return block.findAll(xml).mapNotNull { m ->
                val b = m.groupValues[1]
                val name = href.find(b)?.groupValues?.get(1)
                    ?.substringAfterLast('/')?.substringBefore('?') ?: return@mapNotNull null
                if (!name.startsWith("ashkb-backup-") || !name.endsWith(".ashkb")) return@mapNotNull null
                DavBackupFile(
                    name = name,
                    size = len.find(b)?.groupValues?.get(1)?.toLongOrNull() ?: -1L,
                    modified = mod.find(b)?.groupValues?.get(1)?.trim() ?: "",
                )
            }.distinctBy { it.name }
                .sortedByDescending { backupSortKey(it.name) }
                .toList()
        }

        /**
         * v1.0.36：解析 PROPFIND 多状态响应为 (名字, 是否目录)。
         *
         * 集合判定同时看 href 尾斜杠与 `<resourcetype><collection/>`——部分服务器只给其一；
         * 名字取 href 末段（去尾斜杠、去查询串），父目录自身条目（名字为空）被滤掉。
         * 纯 JVM 可单测。
         */
        fun parseDavEntries(xml: String): List<DavEntry> {
            val block = Regex("<(?:\\w+:)?response[^>]*>(.*?)</(?:\\w+:)?response>", RegexOption.DOT_MATCHES_ALL)
            val href = Regex("<(?:\\w+:)?href>([^<]+)<")
            val collection = Regex("<(?:\\w+:)?collection")
            return block.findAll(xml).mapNotNull { m ->
                val b = m.groupValues[1]
                val raw = href.find(b)?.groupValues?.get(1)?.substringBefore('?')?.trim()
                    ?: return@mapNotNull null
                if (raw.isEmpty()) return@mapNotNull null
                val name = raw.trimEnd('/').substringAfterLast('/')
                if (name.isEmpty()) return@mapNotNull null
                DavEntry(name = name, isDir = raw.endsWith("/") || collection.containsMatchIn(b))
            }.distinctBy { it.name to it.isDir }.toList()
        }
    }
}
