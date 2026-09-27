# v1.0.70 真机走查 · 回填记录（2026-09-27 首轮）

> 设备：**Xiaomi 15 Pro（2410DPN6CC）· Android 16 (API 36) · HyperOS OS3.0.308.0.WOBCNXM**
> 被测制品：手机上安装的 `base.apk` SHA256 = `F3EB52D1481A8F1F9BD5D2333ACA956E4E39C000F50AB8CCCBCD469EEA698BA4`
> → **与 GitHub pre-release v1.0.70 资产摘要逐字节相同**（即：本次验证的对象就是将要转正的那个制品）
> 安装形态：`firstInstallTime=2026-09-15 21:17:56` / `lastUpdateTime=2026-09-27 14:19:52` → **就地覆盖升级**，Room v15→v16→v17 迁移链已执行
> 走查手段：adb 无线调试（`device-prep.ps1` / `device-audit.ps1` / `device-logs.ps1`）+ 人工确认
> 配套工具不随仓库发布，留在工作区 `release-tooling/`（另有 `cmp_apk.py`：本地构建 vs GitHub 资产逐条目比对）

---

## A. 客观通过项（无需肉眼，机器读取）

| # | 项 | 证据 | 结论 |
|---|---|---|---|
| A1 | 制品同一性 | `pm path` → pull → SHA256 = `F3EB52D1…8BA4` | ✅ 与发布资产同字节 |
| A2 | 覆盖升级 + 迁移链 | `lastUpdateTime` > `firstInstallTime`；升级后 App 正常使用 | ✅ Room 15→17 未被回退 |
| A3 | 电池白名单（v1.0.62） | `dumpsys deviceidle whitelist` → `user,com.ashkb.app,10490` | ✅ 已在白名单 |
| A4 | 通知通道（v1.0.59/60/61/66/68） | `dumpsys notification` 中 `med_reminders` / `sys_notices` / `checkup_reminders` / `questionnaire_reminders` / `exercise_reminders` / `reminder_silent` / `emergency_lockscreen` / `sedentary_reminders` **8/8 FOUND** | ✅ 通道齐备 |
| A5 | 提醒链已武装 | `dumpsys alarm` → 4 条待触发（3× `ReminderReceiver` + 1× `BasdaiReminderReceiver`） | ✅ 链在设备上真实存在 |
| A6 | 崩溃 | `logcat -d` 无 `AndroidRuntime` / `FATAL EXCEPTION` | ✅ 无崩溃 |
| A7 | **Android 16 WebDAV 反射回归（§9.8）** | 「测试连接」通过 + 「从 WebDAV 恢复」**列出远端备份**（PROPFIND）+ 「备份到 WebDAV」**上传成功**（MKCOL + PUT） | ✅ **结项**：`forceMethod()` 在 Android 16 上仍有效 |
| A8 | 强提醒权限的**授予后自检刷新**（v1.0.62） | 授权前 App 显示「未授予」且 `appops` Uid mode=`ignore`（判断正确）；授权后 App 自动刷新为「已授予」，`appops` Uid mode=`allow` | ✅ 判断与 ON_RESUME 刷新均正确 |
| A9 | 测试提醒端到端（v1.0.62） | 点「发送测试提醒」→ 退出 App → 10 秒后收到通知 | ✅ 整链可用 |
| A10 | 锁屏紧急卡的系统级属性（v1.0.66） | `dumpsys notification` → `id=100003`：`flags=ONGOING_EVENT\|ONLY_ALERT_ONCE`（不可划掉/只提醒一次）、`visibility=PUBLIC`（锁屏公开可见）、`importance=2`（不响不震） | ✅ 三项设计属性全部正确 |

## B. 本轮发现的缺陷（待决定是否出 v1.0.71 修）

### D1（中危，用户可见）：「允许强提醒」按钮在本 ROM 上是死按钮

- **现象**：自检卡点「允许强提醒」**无任何反应**（无跳转、无提示、不崩溃）
- **代码**：`app/src/main/java/com/ashkb/app/ui/me/MeScreen.kt:287`
  ```kotlin
  runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)) }
  ```
  两处问题：① **漏 `data = "package:<pkg>"`**；② `runCatching` **静默吞掉** `ActivityNotFoundException`
- **取证**：
  - logcat：`ActivityTaskManager: START u0 {act=android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT xflg=0x4} … callingPackage com.ashkb.app … result code=-91`（发了，但**没有 `dat=`**，且之后无任何设置页被拉起）
  - `cmd package resolve-activity -a android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT` → **`No activity found`**
  - 加 `-d package:com.ashkb.app` → 解析到 `com.android.settings/.AppManageFullScreenIntent` ✅（我用 adb 这样拉起后，用户成功授权）
- **判定**：符合 `HANDOFF.md` §7「任何 `catch`/`runCatching` 兜底要么留档要么上抛，不能静默」+「新增跳转必须验证目标可解析」
- **修法（建议，未实施）**：补 `data = Uri.parse("package:$packageName")`；`resolveActivity` 为空时**兜底到应用详情页**（本机可解析）；再不行给**文字路径提示**（而非静默）

### D2（低危，体验降级）：精确闹钟设置跳到了「全部应用」列表页

- **代码**：`MeScreen.kt:280` `context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))` —— 同根因**漏 data**
- **取证**：无 data → `Settings$AlarmsAndRemindersActivity`（全应用列表）；带 data → `Settings$AlarmsAndRemindersAppActivity`（本应用专属页）
- **判定**：不是死按钮（能跳），但用户需在列表里自己找 ASHKB；与 D1 同一修法

### D3 → **已澄清：非 App 缺陷**（锁屏紧急信息）

- **初次现象**：用户开启开关后锁屏「无显示」
- **取证过程**：第一次 `dumpsys notification` 中**无** `id=100003` 记录 → 判断为未投递；请用户重新「关-开」开关后**立刻**复查
- **复查结果（系统级证据，投递正确）**：
  ```
  id=100003  channel=emergency_lockscreen  importance=2(LOW)
  flags=ONGOING_EVENT|ONLY_ALERT_ONCE      → 不可划掉 + 只提醒一次 ✅
  visibility=PUBLIC                        → 锁屏公开可见 ✅
  ```
- **结论**：App 投递、通道、可见性、常驻标记**全部符合 v1.0.66 设计**；首次「无显示」的原因为**开关当时未生效**（用户侧）
- **附带确认**：`EmergencyLockscreen.build()` 只要有档案就必产生「血型…｜诊断…」一行 → 不存在「误判为无可显示内容而撤下」的路径（代码核实）
- **唯一残留观察**（非缺陷，属设计取舍）：投递只在「应用启动 / 紧急卡页数据变化 / 切换开关」三个时机发生；若进程被系统回收后**重启手机**，卡片要等下一次 App 进程启动（`BootReceiver` 会拉起进程 → `Application.onCreate` → refresh），已由 A5 的闹钟链与本次复查间接覆盖

### D4（中危，用户可见）：骶髂关节分期 chip 组溢出屏幕

- **现象**：编辑档案页该行「是一个变形的选择框，里面无文字，且**无重度**」
- **代码**：`app/src/main/java/com/ashkb/app/ui/me/ProfileEditScreen.kt:205`
  ```kotlin
  Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
      FilterChip(... Labels.sacroiliitisGrade(null) ...)        // 未评估
      Labels.SACROILIITIS_KEYS.forEach { k -> FilterChip(...) } // 0/I/II/III/IV → 共 6 个
  }
  ```
  **6 个 chip 用 `Row` 且无换行/滚动** → 最后一个（IV 重度）被挤到屏幕外，边缘 chip 被裁切
- **关键**：**同一个坑项目里已经踩过并修过**——`TodayScreen.kt:628` 原注释「FlowRow：6 个 chip 一行放不下会自动换行（**旧 Row 会把后面的选项截在屏幕外**）」；`CheckupForms.kt:205` 的 `ChipGroup` 助手已是 FlowRow + `heightIn(min = Size.touchMin)`。v1.0.67 新增该分组时**回退成了 Row**，且**漏了最小触摸目标**（违反 §6「chip 组用 FlowRow 防窄屏截断」「可点击元素 ≥48×48dp」）
- **现场截图**：`release-tooling/shots/D4-sacroiliitis-chips.png`（2026-09-27 17:13；截图留在工作区 `release-tooling/shots/`，未入库）
  - 可见 `未评估 / 0 正常 / I 可疑 / II 轻度` 后，最右侧仅剩一个**无文字的细长圆角残片**（被裁掉的「III 中度」左边缘），**「III 中度」与「IV 重度」均在屏幕外**
  - **同页对照组**：「颈椎受累」只有 4 个 chip → 完整显示，证明是「数量 × Row 不换行」导致
- **修法（建议，未实施）**：`Row` → `FlowRow` + `verticalArrangement = Arrangement.spacedBy(Spacing.sm)`（照 `TodayScreen` 的成例）；补 `Modifier.heightIn(min = Size.touchMin)`；可顺手把 WellnessScreen / CheckupForms 两处重复的私有 `ChipGroup` 收拢到 `ui/components/`

### 观察项 O1（非缺陷，待 ② 结果定性）

- `MIUIOP(10020)` / `MIUIOP(10021)` / `MIUIOP(10022)` 在本机为 **`ignore`**（MIUI/HyperOS 的「后台弹出界面 / 锁屏显示」一类开关）。
- 若「强提醒全屏」在**锁屏状态**下不亮屏覆盖，优先怀疑这三个 MIUI 开关，而非 AOSP 的 `USE_FULL_SCREEN_INTENT`（后者已 = `allow`）。

## C. 回填状态

| # | 项 | 版本 | 状态 |
|---|---|---|---|
| ② | 强提醒全屏升级链（+30 → +60 全屏，锁屏下） | v1.0.61 | ⏳ 链已起，等待中 |
| ④ | 锁屏紧急信息开关 + 锁屏可见 + 即时同步 | v1.0.66 | ☐ |
| ⑤ | 骶髂关节分期（表单 /「我的」/ 复诊报告 PDF / 档案 JSON 往返） | v1.0.67 | ☐ |
| ⑥ | 晨僵热身序列（<15 / 15–29 / ≥30 三档）+ 姿势睡姿建议块 | v1.0.69 / v1.0.70 | ☐ |
| ⑦ | 生活方式画像采集 + 运动页提示 + 知识库置顶 `edu-003`/`edu-005` | v1.0.64 / v1.0.70 | ☐ |
| ⑧ | 极简模式询问与横幅（含「不可点外部关闭」） | v1.0.65 | ☐ |
| ⑨ | 久坐提醒窗口 / 陈旧闹钟兜底 / 重启存活 | v1.0.68 | ☐ **须等 ② 结束后再做**（需改系统时钟，会触发重排） |
| ⑩ | 免打扰静默投递 + 同时段折叠 | v1.0.60 | ☐ **同上，须等 ② 结束** |
