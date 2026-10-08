package com.ashkb.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.ashkb.app.R
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * v1.2.4（i18n）：按当前应用语言解析「月日 + 星期」的日期格式。
 *
 * 为什么要抽出来：调用点原先写的是
 * `DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_month_day_week), Locale.CHINESE)`。
 * 模式串来自资源（会随语言换成英文模式），但 [Locale.CHINESE] 写死了——英文环境下
 * `EEEE` 仍会渲染成「星期三」，模式与语言不匹配。
 *
 * 为什么用 [LocalConfiguration] 而不是 `Locale.getDefault()`：应用级语言（Android 13+
 * 的「每应用语言」）改的是 Configuration / Resources，进程级默认 Locale 未必同步；
 * 而 LocalConfiguration 就是资源解析所依据的那份配置，与 [stringResource] 的取词口径一致。
 *
 * @return 已按当前语言缓存好的 formatter；模式串或语言一变即重建。
 */
@Composable
fun rememberDateFormatter(): DateTimeFormatter {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(R.string.date_pattern_month_day_week)
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}
