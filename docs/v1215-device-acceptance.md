# v1.2.15 真机验收清单（照着点、照着记）

> 目的：把「只有真机能定论」的 9 项排队成一小时内能走完的顺序，每项给出**可判定判据**，避免又一次「装上、点两下、说不清有没有问题」。
>
> 制品：**GitHub Release v1.2.15 的 CI 产物**（`ashkb-1.2.15-release.apk`，5,325,000 B，sha256 `0a9214b7c557d3ae66390d484cf721ff687872153f3ab9db255d4a0f5513cf3f`）。**不要用本地 `app-release.apk`**——两者差 4 字节（签名块时间戳 + 打包顺序），本地那份不是发布件。
>
> 判定纪律：每项只记「通过 / 失败 / 无法判定（原因）」。**「好像没问题」按「无法判定」记**。

---

## 0. 准备（5 分钟，只做一次）

### 0.1 装机与版本确认

```powershell
$adb = "E:\ASHKB\build-env\android-sdk\platform-tools\adb.exe"
& $adb devices                     # 需要出现 "<序列号>\tdevice"，出现 unauthorized 就去手机上点「允许」
& $adb shell getprop ro.product.model
& $adb shell getprop ro.build.version.release   # minSdk 31 ⇒ 至少 Android 12（API 31）
```

安装（**覆盖安装，不用卸载**：v1.2.15 无库结构变更，`ASHKB_DB_VERSION` 仍 21，直接覆盖不会跑迁移）：

```powershell
& $adb install -r "E:\ASHKB\.tmp-ghdist\ashkb-release-v1.2.15\app-release.apk"
& $adb shell dumpsys package com.ashkb.app | Select-String "versionName|versionCode"
# 必须看到 versionName=1.2.15、versionCode=127
```

如果 `.tmp-ghdist` 已经清掉，重新拉：

```powershell
gh release download v1.2.15 --repo weante/ashkb --pattern "ashkb-1.2.15-release.apk" --dir E:\ASHKB\.tmp-ghdist
Get-FileHash "E:\ASHKB\.tmp-ghdist\ashkb-1.2.15-release.apk" -Algorithm SHA256
# 必须是 0A9214B7C557D3AE66390D484CF721FF687872153F3AB9DB255D4A0F5513CF3F
```

### 0.2 三条通用观察命令（每个用例都会用到）

```powershell
# A) 清空并持续抓 logcat（崩溃栈只在这里有）
& $adb logcat -c
& $adb logcat "*:E" "AndroidRuntime:E" > E:\ASHKB\ui2-shots\v1215-device\run.log
#   ⚠️ "*:E" 必须加引号，否则 PowerShell 有把 `*` 当通配符处理的风险

# B) 抓当前界面的无障碍树（备份页截图会全黑，只能靠这个）
& $adb shell uiautomator dump /sdcard/ui.xml
& $adb pull /sdcard/ui.xml E:\ASHKB\ui2-shots\v1215-device\ui.xml

# C) 坐标点击（用 B 抓到的 bounds 中心）
& $adb shell input tap <x> <y>
```

**两个可复用的助手**（贴进会话就行，后面直接调）：

```powershell
$adb = "E:\ASHKB\build-env\android-sdk\platform-tools\adb.exe"
$out = "E:\ASHKB\ui2-shots\v1215-device"
New-Item -ItemType Directory -Force $out | Out-Null

# 抓一份 dump 到本地，返回本地路径
function Get-UiDump([string]$name) {
  & $adb shell uiautomator dump "/sdcard/$name.xml" | Out-Null
  & $adb pull "/sdcard/$name.xml" "$out\$name.xml" | Out-Null
  "$out\$name.xml"
}

# 在某个 dump 里按文字找控件，输出中心坐标与 enabled
function Find-UiNode([string]$dumpPath, [string]$text) {
  [xml]$ui = Get-Content $dumpPath
  $n = $ui.SelectNodes("//node") | Where-Object { $_.text -like "*$text*" } | Select-Object -First 1
  if (-not $n) { return "NOT FOUND: $text" }
  if ($n.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
    $cx = ([int]$matches[1] + [int]$matches[3]) / 2
    $cy = ([int]$matches[2] + [int]$matches[4]) / 2
    return [pscustomobject]@{ Text = $n.text; X = [int]$cx; Y = [int]$cy
                              Enabled = $n.enabled; Bounds = $n.bounds }
  }
  return "BAD BOUNDS: " + $n.bounds
}
```

用法：`$btn = Find-UiNode "$out\ui0-before.xml" "执行恢复演练"`，然后 `& $adb shell input tap $btn.X $btn.Y`。


先把输出目录建好：`New-Item -ItemType Directory -Force E:\ASHKB\ui2-shots\v1215-device`

> **为什么备份页截图是黑的**：`BackupScreen.kt:72` / `BackupRecoverySection.kt:155` / `BackupSheets.kt:57` 调了 `SecureWindow()`（FLAG_SECURE）。`screencap` / `screenrecord` 一律全黑，这是**设计如此、不是 bug**；`uiautomator dump` 不受影响。

### 0.3 内网 WebDAV 端点（本机靶机已经跑起来）

> **`<TUNNEL_URL>` / `<TEST_PASSWORD>` 是本仓库的占位符，不是漏填**。这份文档进的是公开仓库，所以靶机的临时隧道地址与测试口令已抹掉；值只存在于 `E:\ASHKB\release-tooling\dav-harness\`（本地目录，不入库）。真机走查时按下面两条命令现取——隧道每次重启都换新地址，口令在 `wsgidav.yaml` 的 `simple_dc.user_mapping` 里。

靶机在 `E:\ASHKB\release-tooling\dav-harness\`，两条后台进程（wsgidav 监听 `127.0.0.1:39001` + cloudflared quick tunnel 提供真证书 HTTPS）。**隧道地址每次重启都会变**，用前先取：

```powershell
Select-String -Path E:\ASHKB\release-tooling\dav-harness\tunnel.log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches |
  ForEach-Object { $_.Matches.Value } | Select-Object -Unique
```

当前地址（2026-11-05 07:05 实测有效）：`<TUNNEL_URL>`

| 项 | 值 |
|---|---|
| URL | `<TUNNEL_URL>`（**不带尾斜杠**） |
| 用户名 | `ashkb` |
| 密码 | `<TEST_PASSWORD>`（见 `dav-harness\wsgidav.yaml` 的 `simple_dc.user_mapping`） |

已实测的服务端能力（本机探针，非 App）：`PROPFIND 207` / `MKCOL 201` / `PUT 201` / `GET 200` / `DELETE 204`；20 MB 往返 sha256 逐字节一致（上传 8.5 s、下载 35.5 s）。

> 隧道没起来时（`tunnel.log` 里没有 URL）就重启那两条后台进程，**别去找自签名证书方案**——app 用默认 `SSLContext` 且 `targetSdk=34`，用户安装的 CA 不被信任，自签名必然 `SSLHandshakeException`。cloudflared 给的 `*.trycloudflare.com` 是真证书。

---

## 用例清单（9 项，按「失败代价从高到低」排）

| # | 用例 | 预计 | 依赖 |
|---|---|---|---|
| 1 | ⑦ 恢复演练首次点击 | 10 分钟 | 无 |
| 2 | ⑧ WebDAV 20 MB 附件往返 | 15 分钟 | 0.3 靶机 |
| 3 | restoreMutex 真机双击 | 5 分钟 | 先做 #1（同页） |
| 4 | 升级兼容（覆盖安装后库迁移与数据完好） | 5 分钟 | 必须先做，且要在 #5 改档案之前 |
| 5 | 紧急二维码「不完整」提示 | 8 分钟 | 无 |
| 6 | 报表三态（Loading / Error / Content） | 5 分钟 | 只能验到 Content 与 Loading；Error 需能造失败 |
| 7 | 大字体 + 窄屏（320 dp / 2.0×） | 12 分钟 | 无 |
| 8 | 深色观感（V-1 只做了显式化） | 8 分钟 | 无 |
| 9 | 逐日数值列表（V-3 新组件） | 5 分钟 | 无 |

> 顺序上的两条硬约束：**#4 必须在改数据之前做**（#3 会真覆盖库、#5 会改档案）；**#3 与 #1 同页**，一起做完省一次滚动。
>
> **实际执行顺序：4 → 1 → 3 → 5 → 2 → 6 → 7 → 8 → 9**（下面仍按失败代价编号，方便查；按上面这条顺序走）。

---

### 用例 1 —— ⑦「恢复演练首次点击无反应」（本轮最高优先）

**路径**：底栏「我的」→「备份与数据」→ 往下滚到「恢复演练（一键自证）」→ 点「执行恢复演练」。

**为什么先做这个**：`HANDOFF-STATUS.md:1402` 记着「首次点击无反应」，但上一轮排查发现 `BackupRepository.kt` 与 `BackupViewModel.kt` 的 `Log.*` 调用数是 **0** —— 所以当年「logcat 里没看到异常」是**空证据**，这条缺陷至今没有任何判定依据。

**已核实的代码事实**（决定了观测方法）：

- `drill()`（`app/src/main/java/com/ashkb/app/ui/backup/BackupViewModel.kt:495-507`）**没有重入守卫**，函数体第一行就是 `viewModelScope.launch { _busy.value = true … }`；同页 `doRestore()`（`:416`）与 `undoFromPreRestoreSnapshot()`（`:390`）都有 `if (_busy.value) return`。
- 演练期间**不写 `_stage`**，进度文案只有通用的 `backup_processing` =「处理中…（加密 / 网络操作可能需要数十秒）」，而 `BusyFooter` 在滚动容器**最末尾**（`app/src/main/java/com/ashkb/app/ui/backup/BackupScreen.kt:298`），按钮在 `:259-269` ⇒ 进度块基本在视口外。
- 点击后**唯一即时可见的变化是按钮自己变灰**（`BackupScreen.kt:268` `enabled = !busy`）。
- 结果只经 `_message` → Snackbar，须等整条链路跑完。
- `viewModelScope` 跑 `Dispatchers.Main.immediate`，`_busy` 翻转与点击**同帧**，不存在调度空窗。
- 演练链路很重：`PRAGMA wal_checkpoint(TRUNCATE)` → 整库复制 → Room 开副本 → 全库导出 → `VaultCipher.encryptV3` → 解密 → 副本写回 → 再导出比对 → 写台账 → 删副本（`BackupRepository.kt:425-473`）。

**步骤**（严格按顺序，每步都记时间戳）：

```powershell
# 1) 先回首页再进备份页，确保是「首次进入」状态
& $adb shell am force-stop com.ashkb.app
& $adb shell monkey -p com.ashkb.app -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 3
# 手工：底栏「我的」→「备份与数据」→ 滚到最底（备份页会黑屏，这是 FLAG_SECURE，正常）
# 滚到底用：& $adb shell input swipe 540 1600 540 600 300   （多来几次）

# 2) 抓点击前状态（按钮 enabled 吗？坐标多少？）
$d0 = Get-UiDump "ui0-before"
$btn = Find-UiNode $d0 "执行恢复演练"
$btn | Format-List          # 必须 Enabled=True 且拿到 X/Y，否则脚本自动化不适用，改用人工点击

# 3) 点一下，然后每 500 ms 抓一次，共 8 次（12 秒窗口）
$t0 = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
& $adb shell input tap $btn.X $btn.Y
$observed = @()
for ($i = 1; $i -le 8; $i++) {
  Start-Sleep -Milliseconds 500
  $d = Get-UiDump "ui$i"
  $b = Find-UiNode $d "执行恢复演练"
  $t = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - $t0
  $line = "t+${t}ms  enabled=" + $(if ($b -is [string]) { "GONE" } else { $b.Enabled })
  $observed += $line
  $line
}

# 4) 追加第二次点击（验重入，同时供用例 3 参考）
& $adb shell input tap $btn.X $btn.Y

# 5) 等演练结束，看台账新增了几条
Start-Sleep -Seconds 30
$dEnd = Get-UiDump "ui-end"
Select-String -Path $dEnd -Pattern '演练' | ForEach-Object { $_.Line.Trim() }
# 默认只显示前 5 条（BackupLedgerSection.kt:54），必要时在页面点「显示全部」再 dump 一次
```

**关键判断**：`$observed` 里第一次出现 `enabled=False` 的时间点，就是「按钮变灰」的时刻。把它和 `$t0` 相减得到**首反馈延迟**——这一个数字直接区分 H1 与 H5。
```

**判定表**（这是本用例的全部价值，照着填）：

| `_busy` 是否翻转（ui1~ui8 里按钮 enabled 是否变 false） | 台账「演练」条数 | 结论 |
|---|---|---|
| 是（≤ 500 ms 内变灰） | 1 | **通过** —— 旧记录的「无反应」是观感问题（反馈在视口外 + 只有按钮变灰），不是功能失效 |
| 是 | 2 | **失败（真缺陷）** —— 无守卫导致重复演练；修法 = `drill()` 首行加 `if (_busy.value) return` |
| 否，且 8 个 dump 里按钮始终 enabled | 0 | **失败（真缺陷）** —— 点击根本没进函数，需要 logcat 全量（不只是 `*:E`）+ 可能要在 `drill()` 首行临时加日志，另开一轮 |
| 否，但 8 个 dump 还没覆盖到完成 | 1 | **无法判定** —— 演练比 12 秒慢，重跑并把窗口拉到 60 秒 |

**同时记录 H5 的关键数字**：点击瞬间（`$t0`）与按钮首次变灰（哪个 `ui$i.xml`）之间差多少毫秒。若 > 2000 ms，说明 `wal_checkpoint(TRUNCATE)` 同步阻塞足以让人以为「没反应」，这本身就是可修的观感缺陷（把 checkpoint 挪进 `withContext(Dispatchers.IO)`）。

**记录到**：`$out\drill-result.md`，把判定表那一行原样抄下来，附 `ui1`~`ui8` 的文件名与时间戳。

---

### 用例 2 —— ⑧ WebDAV 真实网络 20 MB 附件往返

**路径**：「我的」→「备份与数据」→「WebDAV」→ 填 0.3 的 URL / 账号 / 密码 → 保存并探测 → 返回上一级 → 附件同步区块。

**为什么重要**：`WebDavClient.kt:407 ATTACHMENT_RESPONSE_LIMIT = 21 MiB`、`:209` 用它拉附件；`BackupEngine.kt:87 PICK_LIMIT_BYTES = 64 MiB` 是选择上限。**21 MiB 这条上限从来没在真实网络上验证过**——靶机侧已证明 20 MB 能在 8.5 s 内上传、35.5 s 内下载且字节一致，剩下的问题是 app 自己的超时、进度、内存与校验。

**步骤**：

1. 先在手机里准备一个 ≥ 20 MB 的 PDF（或任意文件）放进「文档」。
2. WebDAV 配置页：URL 填 `<TUNNEL_URL>`（**不带尾斜杠**），账号 `ashkb`，密码 `<TEST_PASSWORD>` → 保存并探测 → 期望探测通过。
3. 附件同步区块 → **先打开「同步附件到 WebDAV」开关**（不开的话按钮禁用并提示「请先开启…」，`attach_sync_need_enable`；没配 WebDAV 时提示「请先配置 WebDAV 服务器」，`attach_sync_need_webdav`）→ 点「同步附件」→ **计时**，并每 5 秒抓一次 `uiautomator dump` 记录进度文案变化。
   - 校验入口是「校验远端」（`attach_verify_note`：「比对服务器与本地记录：远端缺失的自动补传，服务器上多余的（本地已无记录）一并清理」）。
4. 同步完成后到服务端验证（**文件名里不该出现原始文件名**，这是 `AttachmentPath` 刻意设计的防泄露）：

   ```powershell
   $h = "E:\ASHKB\release-tooling\dav-harness"
   Get-ChildItem "$h\data\ashkb\attachments" -Recurse -File |
     Select-Object @{n='Rel';e={$_.FullName.Replace("$h\data\","")}}, Length
   # 期望：ashkb/attachments/<YYYY-MM-DD>/<id>.enc
   ```

5. 反向验证：把手机上的本地附件删掉 → 点「校验远端」→ 期望从远端拉回并校验通过（此时会走 `ATTACHMENT_RESPONSE_LIMIT = 21 MiB` 这条路径）。

**判定**：

| 观察 | 结论 |
|---|---|
| 上传成功、服务端 `*.enc` 的字节数 > 20 MB、手机端校验通过 | **通过** |
| 上传成功但服务端字节数明显小于源文件 | **失败** —— 分块/流写截断 |
| 下载阶段 `OutOfMemoryError` 或 `IOException`，或界面卡死不恢复 | **失败** —— 21 MiB 全量读进内存的假设不成立，需改为流式落盘 |
| 30 秒内失败、logcat 有 `SocketTimeoutException` | **失败** —— app 侧超时小于 35.5 s 的实测下载耗时 |

**注意**：本次是**单设备单账号**，只验传输与校验；「换设备恢复」之类跨实例语义不在本用例范围。

---

### 用例 3 —— restoreMutex 真机双击（并发恢复互斥）

**前提**：先做用例 1（同页），并确保页面上已经有一份**可恢复的备份文件**（本机全量备份一次即可）。

**已核实的代码事实**：`doRestore()`（`BackupViewModel.kt:416`）与 `undoFromPreRestoreSnapshot()`（`:390`）**首行**都有 `if (_busy.value) return`；互斥闸本身是 `RestoreGate`（`app/src/main/java/com/ashkb/app/domain/RestoreGate.kt`），已有 9 条 JVM 测试证明「至多一个临界区」。**真机要验的是闸之外那一层**：UI 双击时 `_busy` 是否足够快地挡住第二次。

**步骤**：

```powershell
# 走完「选文件 → 旁路解密与校验 → 出现待确认块」后，先定位「执行恢复」按钮
$dR0 = Get-UiDump "ui-r0-before"
$ok = Find-UiNode $dR0 "执行恢复"
$ok | Format-List          # Enabled=True 且拿到 X/Y 才继续

# 连点三次（三条 input tap 之间不加 sleep，让它们挤进同一帧附近）
& $adb shell input tap $ok.X $ok.Y
& $adb shell input tap $ok.X $ok.Y
& $adb shell input tap $ok.X $ok.Y

Start-Sleep -Seconds 45
$dR = Get-UiDump "ui-restore"
Select-String -Path $dR -Pattern '恢复' | ForEach-Object { $_.Line.Trim() }
# 台账里「恢复」条数应为 1
```

**判定**：台账「恢复」= 1 条 ⇒ 通过；≥ 2 条 ⇒ 失败（`_busy` 守卫或 `RestoreGate` 真机路径有问题，属严重）。若界面出现「口令已过期」之类提示，记下来——那说明第二次点击穿过了守卫但被后续检查拦住，仍算**观感缺陷**而非数据损坏。

> ⚠️ 这条会**真的覆盖当前库**。做之前先在手机上导出一份「档案 JSON」到本地，或确认台账里有 pre-restore 快照可用。

---

### 用例 4 —— 升级兼容（覆盖安装）

**要验的**：v1.2.15 的 `ASHKB_DB_VERSION` 仍是 21，从 v1.2.13/v1.2.14 覆盖上来**不应触发迁移**；数据应完好。

```powershell
# 安装前
& $adb shell run-as com.ashkb.app ls -l /data/data/com.ashkb.app/databases
& $adb shell dumpsys package com.ashkb.app | Select-String "versionName"
# 覆盖安装 v1.2.15（见 0.1），启动后
& $adb logcat -d | Select-String "Room|SQLite|Migration|FATAL"
```

**判定**：logcat 无 `Room` / `SQLite` / `Migration` 异常、无 `FATAL`；打开「今日」「健康」「报表」三页数据都在 ⇒ **通过**。若出现 `IllegalStateException: Room cannot verify the data integrity` ⇒ 失败，立刻停止后续用例并导出数据库。

> 若手上只剩 v1.2.12 或更早，**升级前先做备份**：那条路径会跑库结构 20→21 迁移。

---

### 用例 5 —— 紧急二维码「不完整」提示（P0-3 的真机观感）

**要验的**：过敏条目 > 4 时，PNG 侧 Snackbar 与 PDF 侧那行说明是否都真的出现、文案是否读得通。

**代码事实**：`EmergencyQr.MAX_ALLERGIES = 4`，`payloadWithMeta` 返回 `omittedAllergies`；PNG 侧 `generateEmergencyQrPng` 的 `onPartial` 回调先于 `onReady` 触发（因为分享面板会盖住 Snackbar）；PDF 侧在二维码图下方用 `emergency_qr_partial_note`（zh：「二维码只放得下前 %2$d 项过敏，本次省略了 %1$d 项。本页上方印的是完整过敏清单。」）加一行 `warn = true` 的说明。

**步骤**：

1. 「我的」→「健康档案」→ 编辑 → 过敏史填 **6 条**，**用逗号分隔**（`EmergencyQr.kt:139 ALLERGY_SPLITTER_REGEX = Regex("[,，]")` 只认半角/全角逗号，**顿号 `、` 不算分隔符**，会被当成一条 6 合 1 的过敏名）→ 保存。
2. 「报表」→「报告导出」页 → 找「钥匙扣二维码」卡（`emergency_qr_png_title`）→ 点「生成钥匙扣二维码」→ **看是否弹 Snackbar 说明省略了 2 条**（`emergency_qr_partial_note`；注意它会先于分享面板弹出，因为 `onPartial` 在 `onReady` 之前调用）。
3. 同页找「紧急卡打印版（PDF）」卡 → 点「生成紧急卡 PDF」→ 打开 → 二维码下方**应有那行中文说明**，且页面上方「过敏」一栏印的是**完整的 6 条**（`ReportPdfWriter.kt` 的患者信息段印全文、只有二维码里的 JSON 被截断）。
4. 反向：把过敏删到 3 条 → 两个入口都**不应**再出现任何省略提示。

**判定**：2、3 都出现提示、4 不出现 ⇒ 通过。任一处缺 ⇒ 失败并截图（导出页不受 FLAG_SECURE 影响，可以截）。

---

### 用例 6 —— 报表三态（Loading / Error / Content）

**要验的**：外部审查 U-3 的落地在真机上的样子——**首次失败必须明说失败并给「重试」**，不能永远显示「统计加载中…」；**已有数据时刷新失败要保留旧数据 + 顶部「这次刷新失败了…」**。

**能否造出失败**：可以，但要**用库文件动手脚，不要用飞行模式**——`ReportRepository.overview()` / `trends()` 全是本地 Room 查询（`ReportRepository.kt:107` 起 `withContext(Dispatchers.IO) { db.…Dao().between(...) }`），飞行模式**不会**让它失败。

**要造失败就在 dump 之外动数据库**（做完立刻还原）：

```powershell
# 备份库文件
& $adb shell run-as com.ashkb.app cp /data/data/com.ashkb.app/databases/ashkb.db /data/data/com.ashkb.app/databases/ashkb.db.bak
# ⚠️ 只在没有未备份数据的前提下做；更安全的替代：直接把进程杀掉后立刻重启，观察 Loading 时间
```

> 老实说：**这条路径风险高于收益**（`restore()` 与备份都在同一个库里，弄坏了就要走恢复）。建议做法是**只验 Content 与 Loading 两态，Error 态记「无法判定（本轮不构造）」**，并把这条欠账写进 CHANGELOG——它本来也是"看到过一次就行"的观感项，不值得为它冒数据风险。

**能验的部分**（不需要造失败）：

1. 冷启动直接进「报表」：应先看到「统计加载中…」或骨架，随后出现数据。
2. 数据出来后点右上「刷新」：按钮应在刷新期间变灰，数据保持在位。
3. 切换「概览 / 趋势 / 周月报 / 报告导出」四个页签，每页首屏不应有超过 1 s 的空白。

**判定**：三条都正常 ⇒ **部分通过**（错误态未验）；第 1 条出现永久「统计加载中…」且超过 10 s ⇒ **失败**。

---

### 用例 7 —— 大字体 + 窄屏（U-2 的真机验收）

**要验的**：外部审查 §五 的验收标准——**320 dp 宽 + 2.0× 字号下主要值完整可读、主要操作不被挤出屏幕、键盘弹出后仍能到保存按钮**。

```powershell
# 造 320dp 宽 + 2.0 字号（不改系统设置，用完可恢复）
& $adb shell wm size 720x1280
& $adb shell wm density 360
& $adb shell settings put system font_scale 2.0
# 用完恢复
& $adb shell wm size reset; & $adb shell wm density reset; & $adb shell settings put system font_scale 1.0
```

**逐项点查 6 处**（对应已落地的 5 处 + 弹层滚动）：

| 位置 | 期望 |
|---|---|
| 报表 →「趋势」页的小多图 | **变成单列**（`GridCells.Adaptive`，360 dp 屏两列、320 dp 屏一列） |
| 健康页顶部「体温 / 血压 / 心率」三格 | 变**上下排**，不缩字、不截断（`stackThreeStats` 判据） |
| 今日页某条待服药卡片的「服用 / 跳过 / 顺延」 | 变**折行**，不被裁掉（`FlowRow`） |
| 「我的」→「提醒可靠性自检」页 | 能**滚动到最底**（原无滚动） |
| 化验详情弹层 / 紧急联系人弹层 / 运动打卡弹层 | 正文能滚动；**键盘弹出后仍能点到保存/确定** |
| 底部 dock 五个页签 | 文字不截断、图标不变形 |

**判定**：6 处全对 ⇒ 通过；任一处出现截断/遮挡/点不到 ⇒ 失败，记下是哪一处。

> 注意：`TodayScreen.kt:1046` 有一条既有约束——`FlowRow` 不能放进 `Row(Modifier.height(IntrinsicSize.Min))`（foundation 1.7.3 下整行量出 0 高、卡片被 Surface 裁掉）。本次已把动作按钮整块搬到外层，**这条正是要在这里验的**：如果药卡高度异常或按钮整块消失，就是这条约束被破坏了。

---

### 用例 8 —— 深色观感（V-1 只做了「显式化」，没做「修复」）

**必须说清的前提**：V-1 的改动是给根 `Scaffold` 补 `contentColor = MaterialTheme.colorScheme.onBackground`。**这不能证明深色对比度问题被修好了**——没有截图、也没有复现路径，`onBackgroundDark = 0xFFE7EEF3` 与 `onSurface` 的明度差本来就很小。所以本用例是**取证**，不是验收。

**要看的三处**（外部审查点名的）：

```powershell
& $adb shell cmd uimode night yes     # 切深色
& $adb shell cmd uimode night no      # 切回
```

1. **底部导航文字**（五个页签，尤其未选中态）。
2. **报表的原始标题**（`PageHeader` 的 overline / title / subtitle 三层）。
3. **分隔线**（`HorizontalDivider` 在深色下是否几乎看不见）。

**判定**：不提「通过/失败」，只记**「有无肉眼可辨的对比不足」+ 截图**（`screencap` 在这三处可用，不涉及备份页）。若发现具体问题，下一轮再定。

---

### 用例 9 —— 逐日数值列表（V-3 新组件）

**要验的**：`DailyValuesList` 在真机上的展开/收起、滚动、单位排版。

**路径 A**：报表 →「趋势」→ 点某个小多图 → 弹出的 sheet 里，图表**下方**应有「逐日数值（N 天）」按钮 → 点开后是逐行「月-日　数值单位」。
**路径 B**：健康页 → 体重卡 → 图表下方同样有该按钮。

**判定**：

| 观察 | 结论 |
|---|---|
| 默认**收起**；点开出现与图表同数量的行；再点收起 | 通过 |
| 行数比图表数据点少 | 失败（数据源不一致） |
| 单位显示成「120  mmHg」（双空格）或「7 /10」（多余空格） | 失败（`dailyValueText` 的 `unit.trim()` 逻辑回归） |
| 展开后列表撑爆 sheet、需滚很久才见按钮 | 失败（`LIST_MAX_HEIGHT = 220.dp` 上限失效） |

> 列表有 `heightIn(max = 220.dp) + verticalScroll`，所以行数多时应该**在列表内部滚**，不要把整个 sheet 顶长。

---

## 收尾

1. 把 9 项结果汇总成一张表，写到 `E:\ASHKB\ui2-shots\v1215-device\SUMMARY.md`，每项一行：`用例 | 结果（通过/失败/无法判定） | 证据文件 | 一句话`。
2. **失败项一律进 CHANGELOG 的「未发布」节**（本仓库惯例：改动必须留痕，即使是「发现但未修」也要写）。
3. 恢复设备状态：`wm size reset` / `wm density reset` / `font_scale 1.0` / `uimode night no`。
4. 靶机用完可以停：两条后台进程 kill 掉即可，`data\` 留空目录不占空间。

## 附：本清单不覆盖的东西（避免误以为验完了）

- **`restore()` 的五步业务**（`BackupRepository.kt:339` 起：vault key 域校验 → pre-restore 快照 → 覆盖写入 → 双校验 → 台账）。受 `AndroidKeyStore` 限制 JVM 跑不了，真机也只验到「互斥 + 端到端结果对」，**中间每一步的失败注入没有验**。
- **`webdav_config` 明文凭据迁移**（`BackupRepository.kt:62-81`）：仍零测试覆盖。
- **跨设备恢复**（换机、携带不同 vault key 的备份）。
- **附件级联删除的磁盘分支**（v1.0.86 起一直没在真机验过，历史原因是设备上没有附件；做完用例 2 之后**第一次具备条件**，可顺带验：删一条带附件的化验记录，确认行与远端/本地文件都消失）。
- **`DateProvider` 的 `start`/`stop` 广播注册分支**（需真机跨零点或手动改时钟）。
