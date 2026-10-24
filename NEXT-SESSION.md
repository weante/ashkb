# 下次接手从这里开始（v1.2.10 发布后 · 2026-10-23 更新）

> **本文件是仓库内唯一权威的接手文档**，随仓库一起分发。
> `E:\ASHKB\NEXT-SESSION.md`（仓库根之外、**不被 git 跟踪**）是会话工作日志，记的是过程细节与踩坑现场；两者内容会有出入，**以本文件为准**。
> 上一版本文件停在 `v1.1.0 / versionCode 102 / 单测 736 条`，落后 10 个版本，已于 2026-10-23 全面重写。

---

**当前状态**：`v1.2.10`（versionCode **122**）已发布为 GitHub **Latest**，已装维护者真机。

- 发布提交 `45b0435` → 修复 `0c33bf4` → 记账 `3302ebb` → 取证 `faaf538`；`origin/main` = `faaf538`，**工作树干净、ahead/behind 0/0**。
- Release `v1.2.10`：id `416558911`，`target_commitish` = `0c33bf4`，资产 `ashkb-1.2.10-release.apk` **5,213,292 B**，sha256 `9C03C518…FF89`；附注标签 `acaedcf` → `0c33bf4`（已核对远端 `^{}` 剥壳）。
- 单测 **993 条 / 116 个文件全绿**；detekt **0**；lint **184 条 = 0 error**（176 Warning + 8 Information）；`verifySpecSync` 通过。门禁日志 `E:\ASHKB\ui2-fix-basdai-gate1.log`（3m 18s）。
- `ASHKB_DB_VERSION` = **19**（无迁移，可覆盖安装）。
- CI（GitHub Actions `ci.yml`）：最近 5 次 run 全部 `success`。
- ⚠️ v1.2.10 首发版带出一个 UI 回归（报表「趋势」页 BASDAI 卡标题被截断成「BASDAI 总…」，因「稳定期」胶囊被塞进约 170dp 的两列迷你格），**已修**：胶囊改挂概览页全宽 `SectionCard(action = …)`，`SmallTrendChart.trailing` / `MiniSeries.badge` 全部删除，detekt 基线两条签名同步回退（`2 2` 行往返、零净增）。真机双主题复验通过后**就地重做**了 Release（版本号不变）。

---

## 零、OPEN-RISK（已知未修的高危——每轮开工先看这一节）

> 只列「会让用户**吃到错的药 / 看到错的数 / 丢数据**」的条目。观感类问题排在它后面。
> （采纳第三方审查报告 §四 的流程建议，2026-10-03 起生效）

| # | 风险 | 现状 |
|---|---|---|
| ~~R1~~ | ~~大字号 + 窄屏（2.0× / 320dp）时急救卡「当前用药」的剂量说明会被挤到 0 宽~~ | **已修（2026-10-24）**：维护者裁决「胶囊换行」⇒ 药名行由 `Row` 改 `FlowRow`（`ui/emergency/EmergencyScreen.kt:490` 起），放不下时胶囊折到下一行，药名按固有宽测量不再被挤压。代价：药名短时胶囊不再右对齐——急救场景下「读得到药名」优先。**仍未真机复验**（320dp × 2.0× 四张截图） |
| ~~R2~~ | ~~`postponeInjection` 的旧幽灵槽位不会自动消失~~ | **已修（2026-10-24，维护者 m15462 明确同意）**：v1.1.1 之前顺延只改锚点不清行，那些行日期如今都在过去、`deleteOfMedFrom(today)` 永远碰不到，会被报表永久计为漏服。新增 `domain/PostponeGhostSlots.kt` 判 `[顺延当天, 生效日)` 半开区间 + `PlannedSlotDao.deleteOfMedInRange` + `MedicationRepository.purgePostponeGhostSlots()`，由 `AshkbApplication` 启动期物化**之前**跑一次（`app_prefs` 的 `ghost_slot_purged_v1` 闸门）。判据见该文件类注释；为什么不按「当前锚点重算对不上」判也写在那里 |

### 已修销账（原 R3–R8，全部在 v1.2.7 批次 14 修掉，勿再重复提）

| 原编号 | 风险 | 修法落点 |
|---|---|---|
| R3 | 周报「下次复诊日」恒空 | 改为**按下次复诊日**取数，不再按就诊日（`Daos.kt:756` / `ReportRepository.kt:261` 有完整 KDoc） |
| R4 | 备份文件名只过滤前后缀就拿去做破坏性 DELETE | 新增 `WebDavClient.isSafeBackupName` + `BACKUP_NAME_PATTERN = Regex("ashkb-backup-[0-9A-Za-z._-]+\\.ashkb")`（整串匹配，排除 `%` `/` `\` `?` `#`），`parseBackupFileNames` 出口过滤 |
| R5 | `verifyBackup` 半提交 + 原始异常文本直接给用户看 | **半提交已修**：采纳 vault key 从「校验」挪进 `restore`，且必须在 pre-restore 快照之前（`BackupRepository.kt:273-330`）。**异常原文未修**：`BackupEngine.kt:288` 仍 `"恢复写入失败：${e.message}"`，见小尾巴 1 |
| R6 | `GlobalMessages` 一次性事件无订阅者即丢 | `MutableSharedFlow(replay = 0)` 改为 `Channel`（`GlobalMessages.kt:15-27`） |
| R7 | 附件级联删除在事务外销毁字节 | 改为「事务内只动行 → 提交后才删字节」；成文取舍：崩溃只会落在「什么都没变」或「孤儿字节占空间」，都比「行在、文件没了」好（`HealthRepository.kt:805-847`） |
| R8 | `KbDetailDialog` 的第四个时钟源 | 改为注入 `today: String`，不再读系统时钟（`KbDetailDialog.kt:42-60`）；基线签名字串已同步（既存豁免，未新增） |

---

## 一、第一优先级：真机复验与维护者决策

**1. 真机复验（2026-10-24：前两笔已完成，只剩第三笔）**

| # | 事项 | 现状 |
|---|---|---|
| 1 | ~~`dark-05-me.png` 是 **0 字节损坏文件**~~ | ✅ **2026-10-24 已重拍**（246,280 B，深色「我的」页正常：健康档案 KeyValueRow + 药单管理 NavRow + Dock 选中态）。`E:\ASHKB\ui2-shots\ui20\v1210\dark-05-me.png` |
| 2 | ~~「上次异常」遮罩项**证据无效**~~ | ✅ **2026-10-24 解锁亮屏下重跑通过**（`E:\ASHKB\npe-mask-verify2.log`）：30 轮 `validDumps=30 / invalidDumps=0 / maskYes=0 / maskNo=30 / lockedRounds=0 / fatalCount=0`，每轮 dump 包名均为 `com.ashkb.app` ⇒ **遮罩 30 轮零复现**。复现条件改用 `dumpsys deviceidle force-idle`（**不碰电源键**，故不上锁），代替原先按电源键关屏的错误做法 |
| 3 | 批次 C ⑥「存量同日多行不被改写」 | 设备上没有旧版遗留的多行数据，只有单测 `PrnDailyCapTest` 用例 4 覆盖 |

> ⚠️ **教训**：在**带密码锁**的设备上做「屏幕关闭态」复验，**不要用 `input keyevent 26` 关屏** —— 那必然重新上锁，之后 `swipe` / `keyevent 82` 都无法真正解锁（`82` 只拉通知栏），`deviceLocked=0` 偶尔出现只是巧合。正确做法是 `dumpsys deviceidle force-idle` 压出 Doze 条件，全程保持解锁亮屏。

**2. 三项只属于维护者本人**（在「子代理禁改清单」里）

- **裁决项 5「临界」显示**：`domain/ClinicalThresholds.kt:35` 的 `const val LAB_NEAR_BOUNDARY_RATIO = 0.05f` **全仓零使用点**（只声明、无引用），化验判读只有 `high` / `low` / `normal` 三态，**没有 near-boundary 分支**。该文件禁改，须维护者本人动手或明确授权。
- **审查报告 §十 的澄清问题 —— 实为 5 条**（文档此前一直误记为 3 条）。原件 `C:\Users\weant\Documents\Qoder\2026-09-28\a6159f90\ASHKB-代码审查报告.md:260-266`：
  ①审查目的的权重（自用可靠性 / 上架前安全审计 / 交接新维护者 / 改进 AI 协作流程）
  ②医疗规则处置权（只报不改 / 允许把安全默认值改保守 / 先把散落阈值外移并加校验）
  ③备份威胁模型（仅防设备丢失 / 不信任 WebDAV 服务商 / 需考虑恶意备份文件 / 完全不用 WebDAV）—— 决定 S-1/S-2/S-3 是 P0 还是 P2
  ④锁屏急救卡（S-5 隐私换可用是刻意取舍，保留还是改成需解锁查看详情）
  ⑤改造预算（愿为「测试安全网」投入多少，第 2 批约 1 天，直接决定第 3 批医学规则敢不敢动手）
- **批次 C 的发版**：按需药「每天只记一次」已过门禁、已装机验证 ①②③④，**已随 v1.2.10 发布**（release id `416558911`）。

**3. UI 2.0 §06 六帧验收 —— 代码侧缺口已全部清零**

③ 健康 Hub「今日体征」面板（两列两行，顺序 血压/心率 → 体温/体重，未记录不隐藏）+ 紧急卡 **danger-soft** 瓦片：**真机通过**。
⑤ 知识库类别徽章（「教育」在标题行右侧、软底小圆角，底部证据等级行原样保留）：**真机通过**。
④ 报表「稳定期」胶囊：**首版有窄格截断回归，已修复并真机复验通过**（现挂概览页全宽卡标题行右侧，深色深绿底 / 浅色浅绿底 `● 稳定期`）。
深色页面底四帧均匀、**无白边**（批次 A 的 `windowBackground` 缺陷修复仍有效）。
截图留档 `E:\ASHKB\ui2-shots\ui20\v1210\`（`light-*` / `dark-*` / `fix-*`）。

**4. 裁决项 8（审查报告 §九 七条）—— 已结项，两条真机尾巴已补完**

第 3 条**跨主机重定向不转发 `Authorization` 已由 Android 真机实测确认**（`RedirectLab` dex 走 `dalvikvm`，B 端实收头里无 Authorization，`final url` 已跳到 `localhost:8092`、`auto-follow=true`）。
第 6 条**无法取证**（`flags=0x0` 非 debuggable、`run-as` 被拒、`shared_prefs` Permission denied、shell 无 root）⇒ 与审查报告「缺文件」判定一致，只能靠代码审读 + 单测。
其余五条（MIUI appops / CI 签名 / Keystore 背书 / PBKDF2 耗时 / 应用锁）均已取证并回填 `HANDOFF-STATUS.md` 的「裁决项 8 结项」一节。

---

## 二、三处"我没能验证"的地方（维护者若碰到，优先看）

| # | 未验项 | 为什么没验 | 怎么验 |
|---|---|---|---|
| 1 | **`MedEditScreen` 保存路径** | 拆了 9 个 section（v1.0.92），我的自动读屏导航连续失败 | 我的 → 药品 → 添加/编辑 → 填完**点保存**，看是否真存下、字段有没有丢 |
| 2 | **Wellness 下半屏**（饮食画像 / 忌口 / 时间线） | 自动滚动误触拨号盘两次，我不再用盲手势 | 健康 → 营养与骨健康 → 往下滚（**补剂区维护者已确认正常** ✅） |
| 3 | **`KeyValueRow` 新对齐**（v1.0.95/96） | 同上 | 健康档案 / 身体指标 / 饮食画像应**靠右**；周月报长值**从固定位置折行**；急救卡剂量说明**不再 6 行** |

**尚未真机验证的高危**（改完没上机，属"未验证"而非"已知坏"）：R1 那条的运行时宽度（需 320/360dp × 1.0×/2.0× 四张截图）· 恢复备份的重入保护（`restoreMutex` 已就位，需真机双击）· WebDAV 真实网络下 20MB 附件下载（`AttachmentDownloadLimitTest` 已覆盖上限逻辑）。

**批次 B 新增、同样未装机的真机看点**：本地备份选文件的 64 MiB 上限提示、档案导出/导入新增 7 个字段（含 `spine_mobility`）往返、用药史弹层 90 天窗 / 最多 60 行截断、急诊事件草稿各字段都有输入格、运动判读反馈文案已去掉 `exc-010` / `L1` 代号、**停药弹层里不应出现「顺延」**（已真机确认 ✅）、顺延注射后台账 `reason` 存的是 `postpone`。

---

## 三、A 组剩余（性能拆分）—— **已全部收掉（2026-10-24）**

> 三屏根级读点数已于 2026-10-24 逐文件实测复核。**本节已结项**，保留是为了记住「为什么这么拆」。

| 屏幕 | 2026-10-23 实测 | 2026-10-24 现态 |
|---|---|---|
| `ReportScreen` | 835 行，根函数收 8 个 `collectAsState` | ✅ **已拆**：各页子 composable 自收 flow，根函数只留 2 个读点 |
| `EmergencyScreen` | 772 行，根函数收 6 个 `collectAsState` | ✅ **已拆**：拆成 `EmergencyCallBar` / `EmergencySceneCards` / `EmergencyLockscreenCard` / `EmergencyContactsCard` / `EmergencyProfileCard` / `ProfileKeyValues` / `EmergencyMedsList` / `EmergencyEventsCard` 八个子 composable，每个自收所需 flow；根函数只留 `Scaffold` 骨架 + `ContactFormDialog` 开关 |
| `BackupScreen` | 1216 行，全文件 ×26 但根级只剩 3 个 | ❌ **不欠**（旧记法「19→3」按此理解：根级已达标，26 是**全文件**计数） |

**附带项已收**：`BackupScreen.kt` 两处 `DividerList`（台账 `:989`、DAV 备份弹层 `:1097`）**没有做真虚拟化** —— 取证后确认项目内所有长列表的既有先例都是「默认截断 + 显式展开」而非 `LazyColumn`（`MedHistory` / 事件列表 / 药品历史皆如此），且这两处都不是无限滚动列表。已按既有先例改截断，**避免为一个非滚动列表引入 `LazyColumn` 带来的嵌套滚动与 `RowScope` 签名冲突**。

**拆完后还掉的基线豁免**：`EmergencyScreen` 的 `LongMethod` 与 `CyclomaticComplexMethod` 两条既存豁免已不再需要，按 `release-bodies/v1.2.2.md:64-65` 先例**删掉**（基线 316 → 314 行，42596 → 42165 B），而不是留着占位。

**照已完成的先例做**：`MedsScreen` 标杆 / `TodayScreen` 8→3 / `WellnessScreen` 10→0 / `MedEditScreen` 根级读点 24→0 / `BackupScreen` 根级 3 / 本轮 `ReportScreen` 8→2、`EmergencyScreen` 6→0。

---

## 四、小尾巴（2026-10-24 已收 3 条，剩 3 条）

**已收（勿再重复提）**

1. ~~`BackupEngine.kt:288` 把原始异常文本透给用户~~ —— **已修**：恢复失败改为只报固定中文串，原始异常走日志。
2. ~~补剂「部分完成」写入路径只差 UI~~ —— **已修**：首页 `TodayScreen.kt` 与营养页 `WellnessScreen.kt` 两处各加「部分」按钮，均写入 `AdherenceCalc.PARTIAL`；文案复用既有 `R.string.med_history_status_partial`。首页四个回调收进 `SupplementCheckInActions`，参数由 9 压回 5（收拢而不是放宽 detekt `LongParameterList` 阈值）。守卫 `SupplementPartialEntryTest` 5 条。
3. ~~16 条孤儿字符串~~ —— **已删**（中英成对，`EnglishStringsParityTest` 仍绿）；删后 lint 总数由 184 降到 166（`UnusedResources` 随之消失）。

**仍欠（都需要真机或维护者输入）**

4. **`DateProvider` 的广播注册无测试**（精确表述）：纯 JVM 部分已有 `DateProviderTest.kt` **5 条**（同日不通知 / 变更只通知一次 / 注销后不通知 / 共享同一 flow / 日期回退也传播）；**没测的是 `start/stop` 的广播注册**，需真机跨零点或改时钟。
5. **级联删除的附件分支未真机验**（行 + 磁盘文件）：`app/src/test` 下 cascade/级联 相关断言实测 **28 处**（旧记法「19 条 Robolectric」已过时）。
6. **v1.0.85 的体感收益未验**（附件同步时输密码不再被打断）—— 设备上**没有附件**，那 3 条 Flow 不发射。
---

## 五、必须记住的硬约束（踩过的坑）

- ⚠️ **本机内存不是瓶颈**（31.7 GB 总 / 常驻 8 GB+ 可用 ✅）。2026-10-04 两次子代理"被杀"**并非内存爆掉，而是 API 额度耗尽**（维护者确认 ✅）。此前误判为"并发构建内存爆掉"并写进本文件，**该归因已更正** ✅。仍建议**串行**构建的理由是**日志与结果归属清晰**、避免多个任务抢同一份 `build/` 产物 ✅，**不是**因为会崩机器。
- **`strings.xml` 是混合换行（CRLF+LF）**：必须字节安全改写（UTF8 无 BOM 读入 → 逐条唯一匹配断言 → Replace → 同编码写回），**禁止整文件重排**。⚠️ `ReportScreen.kt` 是**纯 CRLF**，用 `[System.IO.File]` 改写时锚点必须带 `` `r`n ``。
- ⚠️ **`[System.IO.File]` 一律写绝对路径**：`.NET` 的当前目录是**进程启动时的目录**，不随 PowerShell `cd` 变。用相对路径 `ReadAllText` 会抛 "Could not find file"，随后 `WriteAllText` 会在**错误的位置建一个 0 字节同名文件**（本项目已踩两次）。
- **改版本号必须同时同步四份文档**，否则 `verifySpecSync` 会 FAILED（它**正确拦住过**一次）。机器门的确切要求（`build.gradle.kts:34-86`）：`README.md` 必须含 `` 当前 `v$versionName`（versionCode $versionCode） ``；`HANDOFF-STATUS.md` 必须含 `截至 **v$versionName（versionCode $versionCode）` **且** `versionCode $versionCode / versionName $versionName`；`HANDOFF.md` **不再**被版本门检查；`ASHKB_DB_VERSION` 对应的 `app/schemas/…/$dbVersion.json` 必须存在。
- **子代理不得改**：版本号 / 四份文档 / lint+detekt 基线 / `ExerciseEngine.kt` / `DateInput.kt` / `ClinicalThresholds.kt` / `AdherenceCalc.kt`。
- **`lintDebug` 0 error 是红线**；中途报出的 detekt 新问题**一律改代码、不进基线**。
- **detekt 基线按「文件 + 完整签名」记账**（连 `private` 都写死）→ 被登记过的函数不能换文件、不能改形参表。**但「不得新增基线条目」≠「不得同步既存条目的签名」**：按 `release-bodies/v1.2.7.md:86-92` 先例**同步那一条 ID 的签名字串**即可，改动前备份、改后 `Compare-Object` 确认 diff 恰为目标几行。⚠️ **KDoc/注释不要写进形参列表** —— 形参内嵌注释会**逐字进入基线 ID**。
- **静态断言 ≠ 验收**（2026-10-21 真机教训）：源码静态守卫只能证明「槽位存在、顺序正确」，**证明不了「放得下」**。§06 ④ 的胶囊初版挂在约 170dp 的迷你格，6 条断言全绿，真机上标题已被压成「BASDAI 总…」。**凡新增「标题 + 数值 + 徽章」三段式布局，必须真机看一眼**；结论类徽章挂**宽卡标题行**，不要塞进迷你格。
- **发布流程（v1.2.10 实测版，替代旧写法）**：①本地 `git tag -a vX.Y.Z -m "…" <commit>` + `git push origin refs/tags/vX.Y.Z` → ②`gh release create vX.Y.Z --repo weante/ashkb --title … --notes-file … --latest`（**不要再带 `--target`**：`--target` 建的是**轻量标签**，v1.2.10 因此被迫 `--force` 重推附注标签）→ ③`gh release upload vX.Y.Z <apk> --clobber` → ④`gh api -X PATCH repos/weante/ashkb/releases/<id> -f target_commitish=<sha>`（**`upload --clobber` 只换资产、不改 target**）→ ⑤`gh api repos/weante/ashkb/releases/tags/vX.Y.Z` 核对 id / 资产大小 / `state=uploaded`，并查 `releases/latest` 是否已接管 → ⑥`git ls-remote --tags origin` 确认 `refs/tags/vX.Y.Z` 是 tag 对象、`refs/tags/vX.Y.Z^{}` 是 commit（v1.2.2 曾因代理静默失败把 tag 指到旧提交）。全链路需 `HTTP_PROXY`/`HTTPS_PROXY` = `http://127.0.0.1:7897`；`git ls-remote` 偶发 `TLS connect error … unexpected eof`，重试 2–3 次即可。
- **GitHub 必须 HTTP/1.1**：`git -c http.version=HTTP/1.1 push origin main`。
- **pwsh 两个坑**：`git rev-parse v1.2.10^{}` 的 `^{}` 会被吃掉 → **必须加引号** `'v1.2.10^{}'`；`gh api --jq .body` 含换行时会被拆成字符串数组 → **必须 `-join "`n"`**。
- **崩溃恢复**：`Could not read workspace metadata from ...transforms-4\...\metadata.bin` = Gradle 转换缓存被写坏 → **停守护进程 + 整个删掉 `transforms-4`** 再重建（只删单个条目无效）。
- **构建必须在沙箱外**：workspace-write 下 Gradle 无法初始化 native services（`Failed to load native library 'native-platform.dll'`）。命令：`. E:\ASHKB\build-env\env.ps1; & "E:\ASHKB\build-env\gradle-8.7\bin\gradle.bat" -p E:\ASHKB\patient-health-app <tasks> --console=plain`，且**不要覆盖 `GRADLE_USER_HOME`**。`apksigner` 前必须先 `. env.ps1` 设 `JAVA_HOME`。
- **门禁结果看 XML，不看 HTML mtime**：`app/build/reports/lint-results-debug.html` 常 UP-TO-DATE 未重写；以 `lint-results-debug.xml`（total/Error）与 `detekt.xml`（issues）为准。凡 `--tests` 过滤运行，结论**必须读 `TEST-*.xml`（含 mtime）**，不能只看 exit code。
- **真机读屏**：`uiautomator dump` + `adb pull`；点击要打**最近的可点击祖先**（文本节点常不可点）；⚠️ **别在含电话号码的页面上盲滑动**（已误触拨号盘两次）。
- **无线调试**：IP 与端口每轮都变（本轮见过 `.180` → `.64`，端口 44507/40961/38435/40329/43043/44767）。`adb kill-server` → `start-server` → `adb mdns services` 取 `_adb-tls-connect._tcp` 的 `IP:port`；若只有 `_adb-tls-pairing` 则需 `adb pair <IP>:<port> <配对码>`。**手机侧 VPN 地址（如 `172.19.0.1`）宿主不可达**，用 mdns 广播的 LAN 地址。设备掉线 = 无线调试被关，**宿主侧不可修复**。

---

## 六、已裁决"不做"（避免重复提）

复诊项目**物理删除**（保持"只停用"）· 化验行内胶囊 28dp 触达区（不动）· Strong Skipping 配置（2.0.20 已默认开启）· skippable 计数 CI 门（脆弱门比没有门更糟）· 迁移起点 **1/2/3 永久不可覆盖**（伪造会让测试虚假通过）· `gradlew`（官方分发 URL 本机不可达）

