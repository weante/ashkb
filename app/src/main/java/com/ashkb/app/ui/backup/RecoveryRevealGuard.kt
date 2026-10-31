package com.ashkb.app.ui.backup

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent

/**
 * v1.2.13（审查报告 S-3）：**再显示恢复码之前先验一次身份**。
 *
 * 背景：恢复码此前在 App 内可随时无口令、无生物认证地重新显示——它是解锁云端备份的
 * 第二个凭据，与备份口令等权，明文长期躺在 Keystore 加密空间里，任何人拿到解锁状态的
 * 手机点两下就能抄走。审查给出的修法是「再显示前要求口令/生物认证」。
 *
 * **为什么用「设备凭证」而不是「备份口令」**（这条很关键，改动前先读）：
 * 备份口令本身不落盘（这正是加密的前提），所以想验它只能拿一份真实备份去试解——
 * 而恢复码存在的**全部意义**就是「忘了口令时还有退路」。若查看恢复码要先输对口令，
 * 忘了口令的人就再也看不到恢复码，这条路等于自己堵死。设备凭证（PIN / 图案 / 密码）
 * 验的是「你是能解锁这台手机的人」，与「你记得住备份口令」无关，恰好是对的那个门槛，
 * 而且它是平台 API（[KeyguardManager.createConfirmDeviceCredentialIntent]），
 * 不需要引入 androidx.biometric。
 *
 * 刻意不做的两件事：
 *  - **不给 Keystore 密钥加 `setUserAuthenticationRequired`**：那把密钥 `ashkb_prefs_key`
 *    同时解密 WebDAV 账号密码，加认证要求会让所有凭据读取都弹一次系统验证；且已生成的
 *    密钥不能原地改属性，要轮换（等于让旧 WebDAV 密码与旧恢复码全部作废）。这道改造
 *    的代价远超本项要解决的问题。
 *  - **不做「没设锁屏就拒绝查看」**：手机没设锁屏时本机根本不存在任何身份屏障，
 *    应用自身也没有应用锁，整个病历库都能直接打开；单拦恢复码不增加任何安全性，
 *    只会制造「自己的码自己看不到」。调用方在这种情况下直接放行，见 [deviceCredentialPrompt]。
 */
internal object RecoveryRevealGuard {

    /** 本机是否设了锁屏（PIN / 图案 / 密码）。没设时无从验证身份。 */
    fun hasSecureLock(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * 构造系统「确认设备凭证」界面。
     *
     * @return 需要启动的 Intent；本机没设锁屏（或取不到 KeyguardManager）时返回 **null**，
     *   调用方据此直接放行——理由见类注释最后一条。
     *
     * 调用方用 `ActivityResultContracts.StartActivityForResult` 启动，`RESULT_OK`
     * 才代表验证通过；`RESULT_CANCELED`（用户按返回）与异常都不放行。
     */
    fun deviceCredentialPrompt(context: Context, title: String, description: String): Intent? {
        val manager = context.getSystemService(KeyguardManager::class.java) ?: return null
        if (!manager.isDeviceSecure) return null
        return manager.createConfirmDeviceCredentialIntent(title, description)
    }
}
