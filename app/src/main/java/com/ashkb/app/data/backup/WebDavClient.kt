package com.ashkb.app.data.backup

import java.io.ByteArrayOutputStream
import java.lang.reflect.Field
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

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

    private fun normalizedRoot(): String = serverUrl.trim().trimEnd('/')

    private fun url(path: String): URL {
        val root = normalizedRoot()
        val full = if (path.isEmpty()) root else "$root/$path"
        return URL(full.replace(" ", "%20"))
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
     */
    fun rotate(keepDaily: Int = 7, keepWeekly: Int = 4, keepMonthly: Int = 6): List<String> {
        val today = java.time.LocalDate.now()
        val keep = buildSet {
            (0 until keepDaily).forEach { add(today.minusDays(it.toLong())) }
            (0 until keepWeekly).forEach { add(today.minusWeeks(it.toLong())) }
            (0 until keepMonthly).forEach { add(today.minusMonths(it.toLong())) }
        }.map { "ashkb-backup-${it}.ashkb" }.toSet()
        val existing = listBackupFileNames()
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

    companion object {
        /** 从 PROPFIND 多状态响应中提取本应用的备份文件名（纯 JVM 可单测）。 */
        fun parseBackupFileNames(xml: String): List<String> =
            Regex(">([^<>]*ashkb-backup-[^<>]*\\.ashkb)<").findAll(xml)
                .map { m -> m.groupValues[1].substringAfterLast('/').substringBefore('?') }
                .filter { it.startsWith("ashkb-backup-") && it.endsWith(".ashkb") }
                .distinct()
                .toList()
    }
}
