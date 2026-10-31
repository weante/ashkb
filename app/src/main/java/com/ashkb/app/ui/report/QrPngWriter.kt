package com.ashkb.app.ui.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.ashkb.app.domain.QrCode
import java.io.File
import java.io.FileOutputStream

/**
 * 把 [QrCode] 画成位图（v1.2.13 · B6 重做）。
 *
 * ### 为什么单独一层
 * [QrCode] 是**纯 Kotlin、无 Android 依赖**的编码器（与 `EmergencyMeds` / `ExerciseEngine` 同层），
 * 它的正确性靠与独立参考实现逐格比对来保证；位图只是「把布尔矩阵变成像素」的搬运，
 * 放在这里不污染编码器的可测性。全仓此前没有任何 `Bitmap` 使用，本文件是第一次碰位图输出。
 *
 * ### 静区（quiet zone）不是可选项
 * ISO/IEC 18004 §6.3.6 要求二维码四周留 4 模块宽的白边：没有它，扫码器无法定位符号边界，
 * 贴在钥匙扣上会直接扫不出来。因此位图尺寸是 `(size + 4×2) × 每模块像素`。
 */
internal object QrPngWriter {

    /** 静区宽度（模块数）——规范固定值，不是可调参数。 */
    const val QUIET_ZONE_MODULES = 4

    /** 默认每模块像素：49 模块的 v8 在 +8 静区后约 456px，够扫码也不至于爆内存。 */
    const val DEFAULT_MODULE_PX = 8

    /** 生成位图（白底黑块，ARGB_8888，供分享或并入 PDF）。 */
    fun render(
        qr: QrCode,
        modulePx: Int = DEFAULT_MODULE_PX,
        quietZone: Int = QUIET_ZONE_MODULES,
    ): Bitmap {
        require(modulePx > 0) { "每模块像素必须为正: $modulePx" }
        require(quietZone >= 0) { "静区不能为负: $quietZone" }
        val side = (qr.size + quietZone * 2) * modulePx
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = false
        }
        val offset = quietZone * modulePx
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (!qr.isDark(x, y)) continue
                val left = (offset + x * modulePx).toFloat()
                val top = (offset + y * modulePx).toFloat()
                canvas.drawRect(left, top, left + modulePx, top + modulePx, paint)
            }
        }
        return bitmap
    }

    /** 渲染并写成 PNG，落在 `filesDir/exports/` 下（与 PDF 同一目录，`file_paths.xml` 已覆盖）。 */
    fun writePng(context: Context, qr: QrCode, fileName: String): File {
        val dir = File(context.filesDir, EXPORT_DIR).apply { mkdirs() }
        val target = File(dir, fileName)
        val bitmap = render(qr)
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        }
        bitmap.recycle()
        return target
    }

    /** 位图边长（像素）——PDF 侧按它算缩放，避免在不同版本下画变形。 */
    fun pixelSide(qr: QrCode, modulePx: Int = DEFAULT_MODULE_PX, quietZone: Int = QUIET_ZONE_MODULES): Int =
        (qr.size + quietZone * 2) * modulePx

    private const val EXPORT_DIR = "exports"

    /** PNG 是无损格式，质量参数被忽略，但 `compress` 必须给值。 */
    private const val PNG_QUALITY = 100
}
