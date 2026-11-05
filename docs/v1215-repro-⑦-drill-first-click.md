# 待办 ⑦「恢复演练首次点击无反应」——静态排查与真机验证矩阵

- 排查日期：2026-11-05
- 基线：`9510d25`（v1.2.15 / versionCode 127）
- 状态：**静态排查完成，结论待真机验证**。本文件不含任何「已修复」断言。
- 原始现象（`HANDOFF-STATUS.md:1402` 逐字）：
  > 「执行恢复演练」首次点击无反应：连点 3 次无反应、进程存活（pid 未变）、无崩溃日志，第 4 次成功。可能与 `uiautomator dump` 抢焦点有关，未能定论。

---

## 一、先纠正一个会误导排查的前提

上次记录的「**无崩溃日志**」不能当作「点击没进到代码」的证据。实测：

```
BackupRepository.kt : Log 调用 0 处
BackupViewModel.kt  : Log 调用 0 处
```

全项目只有 4 个文件用了 `Log.*`（`AshkbApplication.kt` 3 处、`CrashLogger.kt` 1 处、`DateProvider.kt` 1 处、`NotificationHelper.kt` 1 处）。**备份/恢复链路整条没有一行日志**，所以无论点击有没有生效，`logcat` 里都不会有任何痕迹。这条证据是空的，必须换观测手段（见第三节）。

---

## 二、读代码确认的事实（可直接作为结论引用）

| # | 事实 | 位置 |
|---|---|---|
| F1 | `drill()` **没有重入守卫**。函数体第一行就是 `viewModelScope.launch { _busy.value = true; ... }`，没有 `if (_busy.value) return` | `app/src/main/java/com/ashkb/app/ui/backup/BackupViewModel.kt:495-507` |
| F2 | 同页其它两个长操作入口**都有**守卫：`doRestore()` 首行 `if (_busy.value) return`；`undoFromPreRestoreSnapshot()` 首行同样 | `BackupViewModel.kt:416`、`BackupViewModel.kt:390` |
| F3 | 演练**从不写 `_stage`**。全文件 `_stage.value =` 只出现在 WebDAV 备份（`:233`/`:237`）与 WebDAV 下载（`:264`/`:277`） | `BackupViewModel.kt:233,237,264,277` |
| F4 | 演练期间的进度块因此显示的是通用文案 `backup_processing` =「处理中…（加密 / 网络操作可能需要数十秒）」——与「演练」无关，读起来像卡住 | `app/src/main/res/values/strings.xml` `backup_processing`；`BackupLedgerSection.kt:119-124` |
| F5 | 进度块 `BusyFooter` 在滚动容器的**最末尾**（第 9 个区块），而演练按钮在**第 7 个**区块 —— 两者在同屏滚动的上下两端，演练时那个进度块基本在视口外 | `app/src/main/java/com/ashkb/app/ui/backup/BackupScreen.kt:298`（BusyFooter）、`:259-269`（演练卡片） |
| F6 | 演练按钮 `Button(onClick = { vm.drill() }, enabled = !busy)` —— 点击后**唯一的即时可见变化就是它自己变灰** | `BackupScreen.kt:268` |
| F7 | 结果只通过 `_message` → 页面 `LaunchedEffect(message)` → `GlobalMessages.post` → `AppShell` 的 Snackbar 呈现，**必须等 drill 全部跑完才有** | `BackupScreen.kt:166-171`、`AppShell.kt:174-176`、`:231` |
| F8 | 备份页有 `SecureWindow()`（FLAG_SECURE）——**这页截屏是全黑的**，`screencap` 和 `screenrecord` 都拿不到画面。上次排查可能因此只能靠 `uiautomator dump` | `BackupScreen.kt:72`、`app/src/main/java/com/ashkb/app/ui/components/SecureWindow.kt:30` |
| F9 | `viewModelScope` 跑在 `Dispatchers.Main.immediate`，所以 `_busy.value = true` 在点击的**同一帧内同步生效**；不存在「协程还没调度到」的空窗（这一点与 P1-4 附件同步那两个入口不同，那两个是把 busy 写在 launch 体内的可挂起上下文里） | 由 F1 的函数体推出 |
| F10 | 演练本身很重：`PRAGMA wal_checkpoint(TRUNCATE)` → 整库复制副本 → Room 打开副本 → 全库导出 → 加密 → 解密 → 副本上恢复写入 → 再导出比对 → 写台账 → 删副本。任何一步失败都会 `catch` 成一条 Snackbar | `app/src/main/java/com/ashkb/app/data/repo/BackupRepository.kt:425-473` |

**由 F1+F5+F6+F7 直接推出的判定**：无论点击是否生效，**用户在这页几乎看不到任何即时反馈**。所以「点击无反应」这个描述本身不能区分「没进」和「进了但没反馈」。

---

## 三、五个假设与各自的否证手段

按「若成立则现象最自然」排序。真机到场后**按顺序逐条否证**，不要跳。

### H1（默认假设）点击已进入，只是没有可见反馈
- 主张：第一次点击就进了 `drill()`，`_busy` 立即 true、按钮变灰（`ScreenTopBar` 以下整页按钮都灰），但进度块在视口外（F5），结果要等整条链路跑完才出（F7）。用户连点后看到的"成功"其实是**第一次**的完成。
- 否证/证实：
  1. 只点**一次**，然后每 500ms 做一次 `uiautomator dump`，看 `text="执行恢复演练"` 那个节点的 `enabled` 属性。
     - 变为 `false` ⇒ **点击进入了**（H1 成立）。
     - 一直是 `true` ⇒ 点击没进入，转查 H4/H5。
  2. 同一次点击后立刻 `adb shell top -H -p <pid> -n 1`：应看到 `InMemoryDexClassLoader`/IO 类线程 CPU 抬升、`RenderThread` 抖动。
  3. 演练结束后数台账：**恰好 1 条 `演练`** ⇒ 只跑了一次，与 H1 一致。
- 若成立：这不是功能缺陷，是**反馈设计缺陷**。修法方向（成本递增）：① `drill()` 写 `_stage`（如「演练中：正在导出」），并把这个进度块移到演练卡片内部；② 点击后立刻 `info(getString(R.string.vm_drill_started))` 给一条"已开始"的 Snackbar；③ 按钮文字随 busy 变为「演练中…」。

### H2 无守卫导致真的并发了多次演练
- 主张：F1 成立 ⇒ 若用户的第 2/3 次点击落在按钮重组的空档（极快连点、或 dump/tap 批量注入），第 2/3 个 `drill()` 也会进。多个演练并发各复制一份库、各开一个 Room 实例、各写一条台账；`_busy` 由**先完成者**复位，于是按钮在中途又亮起来，再点又是一次。
- 否证/证实：数台账里 `演练` 的**条数**与 `createdAt` 时间戳。
  - 多条且时间戳相近 ⇒ H2 成立（且是真缺陷，不只是观感问题）。
  - 恰好 1 条 ⇒ H2 否掉。
- 注意：台账默认只显示前 5 条（`BackupLedgerSection.kt:54` `ledger.take(5)`），条数不够时要先点「展开全部」。
- 若成立：修法与 F2 对齐——`drill()` 首行加 `if (_busy.value) return`。这是一处 3 行的改动，可以配一条和 `BackupBatch2GuardTest` 同风格的源码守卫测试。

### H3 uiautomator 注入的坐标/焦点问题（工装假象，不是应用缺陷）
- 主张：备份页是长滚动列表，`input tap` 用的是屏幕绝对坐标；若在 `dump` 与 `tap` 之间列表发生滚动（或软键盘/IME 残留、`SecureWindow` 的窗口层级差异），坐标落到按钮之外。
- 否证/证实：
  1. 改用 `input tap` 固定坐标（不用 uiautomator 的 `click()`），点击前后各 dump 一次，比较 `bounds` 是否漂移。
  2. 点击后 dump，看是否有任何节点 `enabled` 从 true 变 false —— 完全无变化且 H1 的 busy 也没翻转 ⇒ 疑似 tap 落空。
  3. 换 `adb shell input keyevent` 走焦点 + `KEYCODE_ENTER` 路径再试一次作交叉。
- 若成立：结论是「上次的现象无法复现为应用缺陷」，需要在报告里明确区分，不能写成"已修"。

### H4 被前一个未完成的长操作占住了 `_busy`
- 主张：进页面后若某个长操作（WebDAV 备份、附件同步、导出 JSON）刚好还在跑，`_busy` 为 true，演练按钮是灰的，点击自然无反应；等它结束就正常。
- 否证/证实：点击时 dump 按钮的 `enabled`。若点之前就是 `false`（灰），H4 成立。同时看页面顶部/底部有没有别的进度块。
- 若成立：不是缺陷，是「按钮禁用但禁用原因不可见」——与外部审查报告 U-5 的「必须禁用时禁用原因要始终可见」是同一类问题。

### H5 首次点击延迟到数秒后才出现第一个可见变化
- 主张：`PRAGMA wal_checkpoint(TRUNCATE)`（`BackupRepository.kt:429`）在 WAL 模式下需要等待所有读事务结束；此刻页面上的 `ledger` Flow 与附件统计恰好有活跃订阅，checkpoint 可能要等到超时。它是**同步阻塞**在 IO 线程上的（`runCatching` 只吞结果，不减少等待），所以从点击到"有动静"可能有好几秒。
- 否证/证实：点击后立刻 `top -H`，看 IO 线程是否已在跑；用 `adb shell date +%s%3N` 在点击瞬间与按钮变灰瞬间各取一次时间戳，差值就是首个可见变化的延迟。
- 若成立：把 checkpoint 挪到不阻塞首个反馈的位置（先给 Snackbar/进度，再做 checkpoint），或改成 `PASSIVE`。

---

## 四、真机操作序列（照做即可，勿临场改）

```powershell
# 0. 一次性准备
$adb = "E:\ASHKB\build-env\android-sdk\platform-tools\adb.exe"
$shot = "E:\ASHKB\ui2-shots\v1215-drill"      # 注意：备份页 FLAG_SECURE，截图必然全黑，只作对照证据
New-Item -ItemType Directory -Force $shot | Out-Null
& $adb shell am force-stop com.ashkb.app
& $adb shell monkey -p com.ashkb.app -c android.intent.category.LAUNCHER 1

# 1. 进备份页（底部 dock → 我的 → 备份与数据自主）
#    手动导航到此页，然后记录 pid
$pid = (& $adb shell pidof com.ashkb.app).Trim()
"pid=$pid"

# 2. 点前 dump：确认按钮在屏幕上 + enabled 状态 + 记下 bounds
& $adb shell uiautomator dump /sdcard/d0.xml; & $adb pull /sdcard/d0.xml $shot\d0.xml
Select-String -Path "$shot\d0.xml" -Pattern '执行恢复演练' -AllMatches

# 3. 点一次（用 dump 给出的 bounds 中心坐标，不要用 click-by-text）
& $adb shell input tap <X> <Y>
$t0 = (& $adb shell date +%s%3N).Trim()

# 4. 连做 6 次快照：看 enabled 是否翻转（这是 H1/H3 的分水岭）
1..6 | ForEach-Object {
  Start-Sleep -Milliseconds 500
  & $adb shell uiautomator dump /sdcard/d$_.xml 2>$null | Out-Null
  & $adb pull /sdcard/d$_.xml $shot\d$_.xml 2>$null | Out-Null
  $en = (Select-String -Path "$shot\d$_.xml" -Pattern '执行恢复演练[^/]*enabled="(true|false)"' -AllMatches).Matches.Value
  "$($_)  $(((& $adb shell date +%s%3N).Trim()))  $en"
}

# 5. 线程活动（H1/H5）
& $adb shell top -H -p $pid -n 1 -b > $shot\top-during.txt

# 6. 等结果出来（Snackbar 的文案在 dump 里也能看到——FLAG_SECURE 只挡截图，不挡无障碍树）
& $adb shell uiautomator dump /sdcard/dr.xml; & $adb pull /sdcard/dr.xml $shot\dr.xml
Select-String -Path "$shot\dr.xml" -Pattern '恢复演练|演练未完全通过|演练失败'

# 7. 数台账（H2 的判据）：先展开全部，再 dump
& $adb shell uiautomator dump /sdcard\dl.xml; & $adb pull /sdcard\dl.xml $shot\dl.xml
(Select-String -Path "$shot\dl.xml" -Pattern '演练' -AllMatches).Matches.Count
```

**判定表**

| busy 翻转 | 台账演练条数 | 结论 |
|---|---|---|
| 是 | 1 | H1 成立 —— 反馈设计缺陷，不是功能缺陷 |
| 是 | >1 | H2 成立 —— 缺重入守卫，真缺陷 |
| 否（一直 true 之前先变灰不了） | — | H4（被占住）或 H5（延迟） |
| 否（一直 enabled） | 0 | H3 成立 —— tap 落空，工装假象 |

---

## 五、无论哪条成立都要补的两件事（暂不动代码，等真机定论）

1. **重入守卫**：`drill()` 加 `if (_busy.value) return`，与 F2 两个入口对齐。改动 3 行 + 一条源码守卫测试，风格照 `app/src/test/java/com/ashkb/app/ui/backup/BackupBatch2GuardTest.kt`。
2. **演练的可观测性**：`drill()` 写 `_stage`（至少三个阶段：`正在导出` / `正在加密校验` / `正在比对`），并把 `BusyFooter` 在演练期间也渲染在**演练卡片内部**（而不是列表末尾）。这条同时解决 H1 与 H5 的观感。

为什么现在不改：改动会改变 H1/H2 的观测结果。真机先按现状取一次证据，再改——否则事后无法说清"当时到底是什么状态"。
