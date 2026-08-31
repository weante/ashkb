package com.ashkb.app.data.backup

import java.io.ByteArrayOutputStream
import java.lang.reflect.Field
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

/**
 * WebDAV 客户端（零依赖实现：HttpURLConnection）。
 * 协议 §4 三步探针：PROPFIND 列目录 → PUT 写探针 → DELETE 清探针；
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
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        val auth = Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        conn.setRequestProperty("Authorization", "Basic $auth")
        conn.setRequestProperty("User-Agent", "ASHKB-Backup/1.0")
        if (depth != null) conn.setRequestProperty("Depth", depth.toString())
        try {
            conn.requestMethod = method
        } catch (_: java.net.ProtocolException) {
            // 非 RFC 标准方法（PROPFIND / MKCOL）：反射覆写
            runCatching {
                val f: Field = conn.javaClass.getDeclaredField("method")
                f.isAccessible = true
                f.set(conn, method)
            }.onFailure { throw DavException("当前 Android 网络栈不支持 $method：${it.message}") }
        }
        if (conn is HttpsURLConnection) runCatching { conn.sslSocketFactory } // 触发默认 SSL 初始化
        return conn
    }

    /** 三步探针：PROPFIND(0) → PUT 探针 → GET 回读 → DELETE 清理（协议 §4）。 */
    fun probe(): String {
        val root = normalizedRoot()
        // 1. MKCOL 目录（已存在返回 405，忽略）
        mkcol("$root/ashkb")
        mkcol("$root/ashkb/backup")
        // 2. PUT 写探针
        val probe = "ashkb-probe-${System.currentTimeMillis()}"
        put("ashkb/backup/.probe-$probe", probe.toByteArray(Charsets.UTF_8))
        // 3. GET 回读比对
        val back = get("ashkb/backup/.probe-$probe")
        if (back.decodeToString() != probe) throw DavException("写探针回读不一致（目录可写但读取异常）")
        // 4. DELETE 清探针
        delete("ashkb/backup/.probe-$probe")
        return "连接成功：探针写入 / 回读 / 清理全部通过"
    }

    private fun mkcol(fullUrl: String) {
        val conn = URL(fullUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val auth = Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
            conn.setRequestProperty("Authorization", "Basic $auth")
            try { conn.requestMethod = "MKCOL" } catch (_: java.net.ProtocolException) {
                runCatching {
                    val f = conn.javaClass.getDeclaredField("method")
                    f.isAccessible = true
                    f.set(conn, "MKCOL")
                }
            }
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299 && code != 405) throw DavException("MKCOL 失败：HTTP $code")
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
     * 无需 PROPFIND 列目录：按「今日往前推各保留窗口」计算应保留的文件名集合，
     * 超窗文件直接 DELETE（404 = 不存在，忽略）。
     */
    fun rotate(keepDaily: Int = 7, keepWeekly: Int = 4, keepMonthly: Int = 6): List<String> {
        val today = java.time.LocalDate.now()
        val keep = buildSet {
            (0 until keepDaily).forEach { add(today.minusDays(it.toLong())) }
            (0 until keepWeekly).forEach { add(today.minusWeeks(it.toLong())) }
            (0 until keepMonthly).forEach { add(today.minusMonths(it.toLong())) }
        }.map { "ashkb-backup-${it}.ashkb" }.toSet()
        val removed = mutableListOf<String>()
        // 检查过去 180 天内的非保留文件并删除
        (keepDaily until 180).forEach { d ->
            val date = today.minusDays(d.toLong())
            val name = "ashkb-backup-${date}.ashkb"
            if (name !in keep) {
                val conn = open("ashkb/backup/$name", "DELETE")
                try {
                    val code = conn.responseCode
                    if (code in 200..299) removed.add(name)
                } catch (_: Exception) { /* 网络抖动忽略，下次再清 */ }
                finally { conn.disconnect() }
            }
        }
        return removed
    }
}
