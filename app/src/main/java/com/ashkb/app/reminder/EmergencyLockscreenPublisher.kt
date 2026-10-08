package com.ashkb.app.reminder

import android.content.Context
import com.ashkb.app.R
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.repo.EmergencyLockscreenStore
import com.ashkb.app.domain.EmergencyLockscreen
import com.ashkb.app.domain.EmergencyMeds
import com.ashkb.app.domain.ResText
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * v1.0.66 B6a：锁屏紧急信息的**投递器**。
 *
 * 单一职责：读库 → 交给纯函数构建内容 → 交给 [NotificationHelper] 发常驻通知。
 * 开关关闭或无可显示内容时**取消**通知（不留下过期信息——急救场景里错误信息比没有信息更危险）。
 *
 * **刷新时机（刻意不做常驻后台同步）**：
 *  - 应用启动（`AshkbApplication`）：兜住「用户改完数据后一直没进紧急卡页」的情况
 *  - 紧急卡页打开期间数据变化：用户正在编辑联系人 / 档案 / 药单时即时同步
 *  - 用户切换开关时
 *  本应用无后台服务与 WorkManager（零第三方依赖 + 离线优先），故不做定时轮询。
 */
object EmergencyLockscreenPublisher {

    /** 读库并同步锁屏通知。任何异常都不该影响调用方（启动期 / UI）。 */
    suspend fun refresh(context: Context) {
        runCatching {
            if (!EmergencyLockscreenStore.enabled(context)) {
                NotificationHelper.cancelLockscreenEmergencyCard(context)
                return
            }
            val db = AppDatabase.get(context)
            val profile = db.profileDao().get()
            val contacts = db.contactDao().observeAll().first()
            // v1.2.5（i18n）：频次文案按系统语言解析（急救卡用 plainRes，见 EmergencyMeds KDoc）
            val meds = EmergencyMeds.summarize(
                db.medicationDao().listActive(), LocalDate.now().toString(),
                freqLabel = { context.getString(it.plainRes) },
                // v1.2.6（i18n）：注射周期文案同样按系统语言解析
                injCycleLabel = { context.getString(R.string.ui_emergency_meds_inj_cycle, it) },
                // v1.2.6（i18n）：商品名括号随语言换全角 / 半角
                brandParen = { context.getString(R.string.ui_brand_paren, it) },
            )
            val content = EmergencyLockscreen.build(
                profile, contacts, meds,
                listSeparator = context.getString(R.string.dom_lock_list_separator),
            )
            if (content.lines.isEmpty()) {
                // 无可显示内容（如未建档且无用药与联系人）→ 不留空卡
                NotificationHelper.cancelLockscreenEmergencyCard(context)
            } else {
                // i18n：domain 层只给资源 id + 参数，落地在这里（本对象非 Composable，故用 getString）
                NotificationHelper.postLockscreenEmergencyCard(
                    context,
                    context.getString(content.titleRes),
                    content.lines.map { context.resTextOf(it) },
                )
            }
        }
    }

    /** 关闭开关并撤下通知。 */
    suspend fun disable(context: Context) {
        EmergencyLockscreenStore.setEnabled(context, false)
        runCatching { NotificationHelper.cancelLockscreenEmergencyCard(context) }
    }

    /**
     * v1.2.6（i18n）：domain 层的 [ResText] 只给「资源 id + 参数」，语言在这里落地。
     * 本工程的片段最多 3 个参数，故显式展开（`*args.toTypedArray()` 会触发 detekt SpreadOperator）。
     */
    private fun Context.resTextOf(r: ResText): String = when (r.args.size) {
        0 -> getString(r.res)
        1 -> getString(r.res, r.args[0])
        2 -> getString(r.res, r.args[0], r.args[1])
        else -> getString(r.res, r.args[0], r.args[1], r.args[2])
    }
}
