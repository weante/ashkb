package com.ashkb.app.data.repo

import android.content.Context

/**
 * v1.0.63 C12：首启免责声明的接受状态。
 *
 * 存 `app_prefs`（与 [com.ashkb.app.MainActivity] 的 `notif_permission_asked` 同一份普通 prefs）。
 * prefs 不跨备份恢复——换机后需重新确认，这正是合规上期望的行为。
 */
object DisclaimerStore {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_ACCEPTED = "disclaimer_accepted"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCEPTED, false)

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ACCEPTED, true).apply()
    }
}
