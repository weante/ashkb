package com.ashkb.app.reminder

import android.content.Context
import com.ashkb.app.data.db.AppDatabase
import com.ashkb.app.data.repo.EmergencyLockscreenStore
import com.ashkb.app.domain.EmergencyLockscreen
import com.ashkb.app.domain.EmergencyMeds
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
            val meds = EmergencyMeds.summarize(
                db.medicationDao().listActive(), LocalDate.now().toString(),
            )
            val content = EmergencyLockscreen.build(profile, contacts, meds)
            if (content.lines.isEmpty()) {
                // 无可显示内容（如未建档且无用药与联系人）→ 不留空卡
                NotificationHelper.cancelLockscreenEmergencyCard(context)
            } else {
                NotificationHelper.postLockscreenEmergencyCard(context, content.title, content.lines)
            }
        }
    }

    /** 关闭开关并撤下通知。 */
    suspend fun disable(context: Context) {
        EmergencyLockscreenStore.setEnabled(context, false)
        runCatching { NotificationHelper.cancelLockscreenEmergencyCard(context) }
    }
}
