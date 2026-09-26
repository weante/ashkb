package com.ashkb.app.data.repo

import android.content.Context

/**
 * v1.0.65 B12：「连续未记录」原因询问的**去重**——记录最近一次询问的日期。
 *
 * 存 `app_prefs`（与 [DisclaimerStore] / `notif_permission_asked` 同一份普通 prefs）。
 * 同一天只问一次：用户答完（无论选什么）当天不再打扰；
 * 次日若仍无核心记录，才会再问（此时缺失天数继续累积）。
 */
object MinimalPromptStore {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LAST_ASKED = "minimal_prompt_asked_date"

    /** 上次询问的日期（`YYYY-MM-DD`）；从未问过返回 null。 */
    fun lastAskedDate(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_ASKED, null)

    fun markAsked(context: Context, date: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_ASKED, date).apply()
    }
}
