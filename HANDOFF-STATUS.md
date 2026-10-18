# ASHKB 状态交接文档

> **时点快照**：截至 **v1.2.10（versionCode 122）· 2026-10-19 · 审查第二批 / 第三批 + 裁决项 3、6、7 + §6.7 + 按需用药每天一次 + UI 2.0 批次 B–E + §06 双主题验收 + 真机启动崩溃修复**，**已全部落地、门禁全绿、已推送并发布为 GitHub Latest**（release id `416558911`，资产 `ashkb-1.2.10-release.apk` 5,213,292 B；逐批取证见文末六节）；本版把此前积压的六批改动一次合并发布，**无库结构变更**（仍 v19），可覆盖安装。上一版 **v1.2.9（versionCode 121）· 2026-10-10 · 裁决项 1、2 + 首页补剂卡**（release id `410782041`），更早 **v1.2.8（versionCode 120）· 2026-10-09 · UI 2.0 批次 A「整份换色」**（release id `408053469`）。
> 本文按「**已完成 / 未验证 / 未实现 / 踩坑**」四桶组织，用于快速交接。
> ⚠️ **权威来源**：工程约定、交付流程、签名、GitHub 同步、逐版本回归清单以 `HANDOFF.md` 为准；版本历史以 `CHANGELOG.md` 为准；版本号以 `app/build.gradle.kts` 为准（v1.2.9 起**不再把单测条数抄进文档**，见根 `build.gradle.kts` 里 `verifySpecSync` 的注释）。
> **本文是快照，不是副本**——新增版本时只需更新本文件的「快照头」与第 2 节，**不要**在此重复维护约定性内容，避免又多一处需要同步的副本。

---

## 0. 交付状态速览

| 项 | 结果 |
|---|---|
| 工作树 | **干净，全部已在 `origin/main`**：v1.2.10 发布提交 `45b0435`（106 files / +7097 / −2044）；此前 `5ecbf33`（v1.2.9 裁决项 1）、`6285d69`、`86e50df`、`c0ee3b1`、`f68350b`、`b1781c8`（裁决项 3）。附注标签 `v1.2.10`（tag 对象 `0649fb2`）→ `45b0435`，`v1.2.9` → `c0ee3b1`（远端 `refs/tags/v1.2.10^{}` / `v1.2.9^{}` 均已核对）。发布记录回填（本行所在提交）随后推送 |
| 全量单测 | **全绿，0 失败 / 0 错误 / 0 跳过**（约 102 个测试文件；**实际条数以 CI 为准，文档不记数**——v1.2.9 起如此）；含 13 条 `MigrationPathProofTest`（起点 4..16 → 19）；androidTest 源集首个用例 `RealDatabaseSchemaTest` |
| 构建 | `testDebugUnitTest assembleRelease assembleDebug` → **BUILD SUCCESSFUL** |
| APK 版本 | **v1.2.10 已构建并发布**：release 经 `aapt dump badging` 核对为 versionCode 122 / versionName 1.2.10，正式证书 `38ca80a6…d7d`（未回退 debug 签名），5,213,292 B，sha256 `109909E0…0186`。上一版 v1.2.9 为 versionCode 121 / versionName 1.2.9 |
| 审查裁决进度 | **裁决项 1、2 已落地并发布（v1.2.9）；裁决项 3 已落地（未发布）**。① 对乙酰氨基酚 `MedClass.NSAID` → 新增 `MedClass.ANALGESIC`，`DrugInteractionKeys` 的 `nsaid`/`nsaid_longterm` 摘除 acetaminophen，新增 2 条回归（`MedicationInteractionChainTest`）；②「已结算」统一到 `AdherenceCalc.SETTLED_STATUSES` 唯一口径——删掉 `MedicationRepository.doneSlotRefs`、`ReminderReceiver` 改引 `settledSlotRefs`、`TodayItem` 新增 `partial`/`isSettled`、今日页 partial 不再显示成「漏服」也不计入待办，连带按铁律重写 4 条 detekt 复杂度（不进基线）并清掉 2 条签名过期的基线条目；③ **口服不再提供「每两周」**——新增 `ui/me/MedEditSections.kt` 的 `routeVisible`（chips 按 route 过滤 + 保留当前值逃生口）、`MedEditScreen.onPrimary()` 提交拦截 + `med_route_frequency_error` 红字，**domain 层刻意不动**（`ScheduleCalc.isCycleGatedBy` 只加 KDoc），新增 10 条回归（`MedRouteFrequencyGuardTest`）。其余 5 项待裁决；其中**裁决项 6、7 与审查第二、三批（含 §6.7）已落地、门禁全绿、未发版**（见文末「批次 B」）；另按维护者指令（m00936）落地**按需用药常驻首页 + 每天只记一次**（见文末「批次 C」） |
| B6 二维码残留 | grep `QrCode` / `QrImage` / `EmergencyQr` / `KeychainQr` / `emergency_qr` → **零命中**（回退干净） |
| GitHub 正式版（Latest） | **v1.2.10**（2026-10-19，release id `416558911`，资产 `ashkb-1.2.10-release.apk` 5213292 B，sha256 `109909e0…0186`）；上一版 **v1.2.9**（id `410782041`，5224936 B，`413f82b8…`）、**v1.2.8**（id `408053469`，5220504 B，`ece175f8…`）、**v1.2.7**（id `407916739`，5220504 B，`e09e6990…`）、**v1.2.6**（id `407329934`，5219392 B）与 **v1.2.5**（id `407329822`，5084704 B）——六条资产摘要与本地 `Get-FileHash` **逐字节一致** |
| GitHub 预发布 | 无（v1.2.0 / v1.2.2 / v1.2.3 / v1.2.4 / v1.2.5 / v1.2.6 / v1.2.7 / v1.2.8 均已转正式版；历史预发布 v1.0.61 ~ v1.0.70 各页带「已被取代」横幅） |

> **最关键的一句话**：真机走查（Xiaomi 15 Pro / Android 16，被测制品 = v1.0.70 发布资产，SHA-256 逐字节一致）已完成：
> **Android 16 WebDAV 反射回归 / 8 通道 / 电池白名单 / 锁屏紧急卡 / 测试提醒 / 冷启动五 Tab 全部结项**；
> 走查发现的 3 处缺陷与新增的「删除已停用药品」随 **v1.0.71** 发布，真机复验 4 项全部正常，已转正（现已被 v1.0.72 取代）。
> **v1.0.72（现行 Latest）**：按小米官方《适配常见问题》§9/§10/§11/§12 落地权限引导与官方 action；并把真机定论的「**悬浮通知**（横幅）/「**在锁定屏幕上 → 显示通知及其内容**」（锁屏）」两个**每通道开关**路径写进 App；提醒自检整块移入二级页。
> **2026-09-27 22:10 追加**：**末级强提醒全屏已在小米上闭环**（锁屏亮屏全屏 + 通知挂 `fullscreenIntent` + 闹钟正常消耗）；横幅与锁屏可见由用户打开小米通道页两个开关后恢复，且**扛住版本升级**。
> 尚未走完的项：久坐窗口 / 免打扰静默与折叠 / 极简模式 / 运动页两块视觉确认 / 骶髂分期在档案卡与 PDF 的显示 + 档案 JSON 往返。

---

## 1. 已完成 · 已验证 · 已发布

**正式版基线 = v1.0.60**。

| 版本 | 内容 | 验证 |
|---|---|---|
| v1.0.58 | 趋势页拖动读数报出同日全部值 + 小多图收尾 | ☑ 2026-09-25 用户确认转正 |
| v1.0.59 | B5 多源提醒（复诊 / BASDAI / 运动三源独立 Scheduler + 3 通道 + 提醒设置面板） | ☑ 转正 |
| v1.0.60 | B8 免打扰时段 + 同时段多提醒合并推送（`DndWindow` 跨午夜纯函数 + 静默通道 + 通知组 summary） | ☑ 转正 |
| v1.0.43 / v1.0.52 | 升级链「取消-重建」修复 / 计划用药时间改时间选择器 | ☑ 已验（历史版本） |
| v1.0.35 / v1.0.36 | 附件接入 WebDAV 备份（v3 格式）/ 远端校验与补传 | ☑ 已验 |

**规划缺口池已全部结项**（逐条台账见 `HANDOFF-ARCHIVE.md` §三）：

- **A 类安全**：A1 紧急卡当前用药（v1.0.26）· A2 备份恢复码 v2 信封双密钥槽（v1.0.27）· A3 KDF 决策文档化（不引入 Argon2id，架构已铺路）
- **B 类功能**：B2 / B3 / B4 / B5 / B7 / B8 / B9 / B10 / B11 / B12 / B13 **全部落地**；**B1 已取消**（用户拍板）
- **C 类弱化实现**：C1 ~ C12 **全部落地**
- **D 类种子挂点悬空**：**已全部了结**（v1.0.64 补齐 `edu-003` 置顶）

---

## 2. 已完成实现 · **未真机验证**（本仓最大的一块待办）

以下 10 个版本**代码已写完、单测已覆盖、APK 已构建并上传 Release**，但都挂着 Pre-release。

| 版本 | 功能 | 真机验证要点 | 结构变更 |
|---|---|---|---|
| **v1.0.61** | B9 升级链第三级「强提醒（全屏）」 | 用药链末级是否拉起全屏 Activity；**Android 14+ 需 `USE_FULL_SCREEN_INTENT` 授权**，未授权应优雅降级为横幅；DND 静默时段**不应**升级全屏 | 无 |
| **v1.0.62** | C11 提醒可靠性收口 | 电池白名单真实状态 + 一键加入；各厂商自启动跳转（小米 / 华为 / 荣耀 / OPPO / vivo / 三星 / 魅族…）；**「发送测试提醒」10 秒后到点**端到端；从系统设置返回后自检状态应刷新 | 无 |
| **v1.0.63** | C12 首启免责声明门禁 | 首次安装全屏声明，**确认前不得进主界面**；确认后不再出现；三处自动提示统一前缀 | 无 |
| **v1.0.64** | B13 生活方式画像 | 建档表单可采集吸烟 / 久坐 / 睡眠 / 运动习惯；运动页出现「生活方式提示」块；登记吸烟后知识库「全部」视图置顶 `edu-003` | 无 |
| **v1.0.65** | B12 极简模式状态机 | 连续 3 天未记症状 → 弹原因询问（**不可点外部关闭**）；选「身体不适 / 住院」进极简（横幅 + 退出按钮，隐藏运动入口、保留告警与用药）；`ui_mode` 与 `minimal_since` 成对维护 | ⚠️ **Room v15→v16** |
| **v1.0.66** | B6a 锁屏紧急信息 | 开关（默认关）开启后，**锁屏界面**应看到常驻紧急通知（公开可见、不响不震）；关掉或无可显示内容则撤下；改数据后即时同步 | 无 |
| **v1.0.67** | C1 骶髂关节影像分期 | 建档表单 chips（未评估 + 0–IV）；「我的」档案卡 + 复诊报告 PDF 显示「III 中度」等；**档案 JSON 导出再导入不丢该字段** | ⚠️ **Room v16→v17** |
| **v1.0.68** | C8a 久坐起身提醒 | 提醒设置开开关（默认关）+ 间隔 30/45；**仅在 9:00–18:00 窗口内触发**；改窄窗口后陈旧闹钟应静默丢弃但**链不能断**；重启手机后仍连续 | 无 |
| **v1.0.69** | C8b 晨僵热身序列 | 昨日晨僵 <15 min 不展示；15–29 min 给一轮 L1 轻柔项（≤4 条）；≥30 min 加「明显延长 + 常提示炎症活动」；处方无 L1 项时给非处方通用提示 | 无 |
| **v1.0.70** | C8c 姿势 / 睡姿建议 | 运动页处方 hero 出现「姿势 / 睡姿建议」块（日常姿势 2 条 + 睡姿卧具 3 条 + 来源行 + 免责前缀）；睡眠时长登记后知识库置顶 `edu-005` | 无 |

**转正命令**（真机确认后）：

```powershell
gh release edit v1.0.70 --prerelease=false --latest
```

> ⚠️ `--latest` **必须显式加**——从预发布翻转时 GitHub **不会**自动接管 Latest 标记（v1.0.35 踩过）。

**另一批「本机无法完成、必须真机」的验证项**（`HANDOFF.md` §9.8）：

- **Android 15 / 16 WebDAV 反射回归**——`WebDavClient.forceMethod()` 反射改 `HttpURLConnection.method` 内部字段，换新设备须验 PROPFIND / MKCOL（无替代方案，属设计取舍）
- P5 手册 A 组（三机型提醒可靠性）/ B 组（全模块走查）/ C 组（数据自主与安全）完整回填
- **BASDAI 打分滑杆手势**——`ScoreInput` 的拖动 / 点按轨道 / 点按当前值 / 慢拖路径，**只能真机验证**

---

## 3. 未实现 / 未完成

### 3.1 B6「钥匙扣二维码模板」——本轮尝试后**已回退**

唯一被打回的功能项。B6 主项「锁屏紧急信息」已于 v1.0.66 落地，只剩二维码模板。

- **需求**：紧急卡生成可打印 / 贴钥匙扣的二维码
- **硬约束**：零第三方依赖 → 必须**从零手写 QR 编码器**

**已做到**（代码已回退，结论保留）：字节模式编码、字符计数、GF(256) Reed-Solomon（本原多项式 `0x11D`）、8 种掩码、格式信息 BCH(15,5)、版本信息 BCH(18,6)；**23 条权威单测通过**（含 ISO/IEC 18004 表 C.1 / D.1 黄金向量）。

**被打回原因**：与参考实现 **segno 1.6.6 逐格比对**时，多字节输入存在**数据码字级差异**——图案「看起来像二维码但扫不出来」。医疗急救卡上扫不出的二维码不可接受。

**本轮定位到的 4 处真实缺陷（下次重做的起点）**：

1. `placeSeparators` 覆盖不全——只在左上 + 部分右 / 下带打分隔符，漏了右上 / 左下带 → v1 保留格 219（应 233）、数据模块 222（应 208），**数据位流整体错位**
2. `writeFormatInfo` 位置映射错误——第一 / 二份格式信息位摆放与规范不符（读回 `decMask=1` 而编码 `encMask=3`）
3. `penaltyRule3`（N3）口径错误——应查 7 模块核心 `1011101` + 判断贴边或前后 4 格全浅；原按 11 模块完整模式匹配
4. `penaltyRule4`（N4）口径错误——应 `k = floor(|占比百分比-50|/5)`、得分 `k×10`；原用「相邻 5% 档到 50 的距离取小」

> **方法论教训**：自写编解码器「自己编、自己解」回读自测**无法排除「编解码犯同一个错」**。验证必须用**独立参考实现**（segno）逐格比对。

### 3.2 其他未实现项

| 项 | 状态 | 说明 |
|---|---|---|
| **R16 批量 CSV / JSON 导入**（模板 + 导入预检） | **待需求方拍板** | v2.1 定为 P1，但 **v3 需求表已不继承**（R14 / R15 / R16 均未继承）。按 v3 可判「不在本期范围」 |
| **趋势页方案 B「总览 + 展开」** | 未做 | 顶部「指标总览」卡（每指标一行：名称 + 当前值 + 变化 + sparkline），点行进详情大图。方案 A 已落 v1.0.45、C 已落 v1.0.54；若继续做 B，**必须基于 C 已实现的「点格展开大图」收敛**，否则两套详情入口重复 |
| **Compose 性能审查剩余结构性重构** | 未做（改动面大） | ① MedEdit / Backup 字段级 Composable 拆分 ② 6 个屏幕顶层 Flow 收集下沉到叶子 ③ `BackupViewModel` / `ReportViewModel` 合并单一 UiState ④ `AppShell` 按需创建 VM（**其余 10 个 VM 仍一次性创建**，仅 Recipes / ExercisePlans 已改）⑤ `DividerList` 展平进父 `LazyListScope` ⑥ Sheet 内嵌套 `verticalScroll` 冲突 ⑦ `DateProvider` 抽象（统一 7 个 VM 的日期 ticker 样板） |
| **S1b 滑杆手势回归测试** | ❌ **已实测证伪，不落地** | Robolectric 能注入触摸，但**裸 M3 `Slider` 也拖不动**——环境能力边界，不是应用缺陷。**勿再起这个坑** |
| **知识库完整编辑器 / 内容导入导出** | 后移 | 明确「后移阶段 2 之后」 |
| **文档不一致** | 已修（本次） | 见本文件末尾「本次复验修正」 |

---

## 4. 明确「取消 / 不做 / 后移」——**勿重复盘**

**整体不做**：M8 家属协作全部（家属端 / 共享子集 / 设备令牌 / 命令协议 / 冲突裁决）、R13 + M11 打卡连击与阶段目标激励、R23 豁免日、M2 三餐打卡、WebDAV 升级通知家属、iOS、紧急警报短信通道。

**系统边界六条**：不做诊断决策 / 不做医生侧 / **不上架商店** / 无账号与自建服务器 / 不做医疗器械级监测 / 无社交商业化。

**流程项（非代码）**：安全内容主治医师线下过目。

**已拍板的技术「不做」**：

- **B1 AS 专项体征**（胸廓扩张度 / 枕墙距 / 指地距 / Schober）——四项均须诊室测量，自测误差大易误导，用户 2026-09-21 拍板取消
- **FTS4 中文检索**——实测对中文子串**零命中**（详见踩坑表 D 组）
- **Argon2id KDF**——Android 无内置 + BouncyCastle 冲突史 + native +1MB APK；v2 信封每槽自带 KDF 参数，将来可无破坏切换

---

## 5. 项目中遇到的坑（分类速查）

> 完整叙述与判据见 `HANDOFF.md` §7（约 50 条）。此处按类别给出**可检索的摘要 + 一句话对策**。

### A. 构建 / 工具链

| 坑 | 对策 |
|---|---|
| Gradle 守护进程原生内存崩溃（`malloc failed` → `daemon disappeared`），**且 assemble 可能已拷出旧 APK** | 构建前看可用内存；出包后**必须 `aapt dump badging` 核对 versionCode**，不能只看 BUILD SUCCESSFUL |
| 增量编译误判 up-to-date（删了 `outputs/apk/debug` 仍报成功却不产 APK） | `--rerun-tasks` 强制重跑；或删 `outputs/apk/debug` + `intermediates/apk/debug` |
| Kotlin 编译守护进程启动失败（exit 1 / 错误码 268435466） | `gradle.bat --stop` **不回收 Kotlin 守护进程**；按命令行含 `org.jetbrains.kotlin` 结束它 |
| APK 产物走 junction 路径 | 以 `6aabebcc1ea7bba8391224c2\patient-health-app\app\build\outputs\apk\...` 为准（注意 `6aabeb**cc**1` 两个 c） |
| R8 混淆后崩溃直读 `K1.n` | `mapping.txt` 反查；`dexdump` 判断类是否真在 dex；**不要拿「字符串里出现过该类型」当证据** |

### B. 数据 / 备份 / 加密

| 坑 | 对策 |
|---|---|
| **长期保存的密文不能用「每次随机」的密钥**（v2 随机 DEK → 改口令后云端附件全废） | 稳定 vault key（Keystore 保护、永不更换）+ 备份 v3 密钥槽包装；payload 与附件**共用同一密钥域** |
| **密钥「存在」≠「可用」**（只判 `prefs.contains` → Keystore 解不开时静默生成新密钥 = 数据销毁） | 区分 `ABSENT / PRESENT / UNREADABLE`，仅 `ABSENT` 才生成 |
| 恢复时密钥采纳必须「仅当本机没有」 | `adoptIfAbsent` 不覆盖，否则旧备份会冲掉本机 key |
| 新增列与备份恢复的兼容判据 | **该列是否参与 WHERE 过滤**：参与（`search_text`）→ 恢复后必须回填；仅展示（`user_note` / `weight_target_*`）→ NULL 是合法业务态，**无需回填** |
| 新增表无需处理 | 备份走 `sqlite_master` 动态发现 + 白名单自动放行 |
| **外键列存在 ≠ 有关系**（`lab_results.checkup_id` 自 v4 就有但写入写死 `null` → 死列） | **新增外键列必须同时落地写入路径**；核对时 grep 写入点而非只看实体定义 |
| 「以本地记录为准删远端」必须限定形状 | 清理集合过 `isManagedRemotePath`（合法日期目录 + `.enc`），否则会误删用户在网页端放的文件 |

### C. 平台 / 系统 API

| 坑 | 对策 |
|---|---|
| **WebDAV HTTPS 反射**（`method` 在 `delegate` 上；直接反射包装类会命中**无效影子字段**并静默失败 → MKCOL 按 GET 发出报 404） | `forceMethod()` 先解引用 delegate 再沿类层级找字段；**Android 15+ 仍需回归** |
| `uploads.github.com` 上 `Invoke-RestMethod` 被断连 | 必须 `curl.exe` |
| Android 14+ `USE_FULL_SCREEN_INTENT` 默认不授予 | 自检卡给授权引导，未授予优雅降级为横幅（v1.0.61） |
| **Android 无面向普通应用的自定义锁屏控件**（锁屏 Widget 已废弃） | `VISIBILITY_PUBLIC` 常驻通知是唯一受支持途径（v1.0.66） |
| 系统无公开「自启动是否开启」查询接口 | 只能按厂商组件名尽力跳转，**刻意不做状态行**（v1.0.62） |

### D. Android 运行时 vs JVM（最贵的一类）

| 坑 | 对策 |
|---|---|
| **⚠️ 正则存在 Java 与 Android(ICU) 方言差异**：孤立 `}` 在 ICU 是语法错误 → `PatternSyntaxException` → `ExceptionInInitializerError` → 启动期被 `runCatching` 吞掉 → 二次触碰 `NoClassDefFoundError`（表现为闪退，看不到正则）。**曾白烧两轮误判成 R8** | ① 解析自己产出的固定格式**手写解析**不用正则 ② 正则花括号只能是合法量词 ③ **不要用 JVM 的 `Pattern.compile` 去「验证」正则可用性**——那正是误判来源 ④ 永久守卫：`RegexLiteralGuardTest` 静态扫描 |
| Robolectric 的 `Pattern` 走 JVM 语义 | 用它做探针检出率为 **0**——「装上工具」≠「工具能解决这个问题」 |
| `java.time` 挂钟不可控（`ShadowSystemClock` 只管 `SystemClock`） | 被测对象**注入「现在」**（带默认值参数） |
| **不要用 `assumeTrue` 静默跳过时间敏感测试** | 整类 9 条悄悄 skipped 而「全绿」结论毫无异常——**回归锁静默失效比红灯更危险**；宁可用会失败的断言 |
| Robolectric 驱动不了 M3 `Slider` 拖动（裸 Slider 也拖不动） | 环境能力边界；判断「环境能否承担验证」必须做**对照探针** |

### E. Kotlin / Compose

| 坑 | 对策 |
|---|---|
| **⚠️ `init` 块读「声明在其后」的字段 = 构造期 NPE**（`viewModelScope` 用 `Main.immediate` + `StateFlow` 首发射不挂起），**149 条单测完全拦不住** | `init` 要用的字段声明在 `init` **之前**；更稳：逻辑放 `delay(...)` 之后或 `LaunchedEffect` |
| **`HorizontalPager` 回收销毁普通 `remember`**（动画每次切回重播） | 跨页签状态一律 `rememberSaveable`；动画 key 用**数据内容 hash** 而非 List 实例 |
| **同级重叠只有 z 序最高者算命中**（透明覆盖层让滑杆拖动**失效潜伏 29 版**） | 旁听手势挂**祖先**，不挂同级；拖动判定用**累计**位移 |
| `LazyColumn` key 不要用字符串拼接 | 用 `key = { a to b }`（Pair 有稳定 equals/hashCode） |
| VM 内不要缓存 StateFlow（无界增长 / 非线程安全） | VM 返回冷 `Flow`，调用点 `remember(id) { vm.flowFor(id) }` |
| 同包重名遮蔽是静默的（报 `Unresolved reference` 指向成员名而非遮蔽） | 新增顶层类型前 grep 同名符号（含嵌套类） |
| 把 composable 抽成独立函数会丢作用域（`weight` 等是 `RowScope`/`ColumnScope` 成员） | 声明成作用域扩展 `private fun RowScope.XxxRow(...)` |
| Kotlin 块注释可嵌套（注释里写 URL 触发 `Unclosed comment`，行号指向文件末尾） | 注释里写 `/*` `*/` 要留意 |
| Compose Strong Skipping 在 Kotlin 2.0.20+ **已默认开启** | **勿再**加 `composeCompiler { featureFlags = ... }`，也**勿**手工 memoize lambda |
| `Canvas` 绘制 lambda 内新建 `Path`/`Brush`/`textMeasurer.measure` | 必须 `remember` 到绘制外 |

### F. 「静默的谎言」类业务逻辑坑（本项目最多的一类）

| 坑 | 通用判据 |
|---|---|
| 时间序列横轴用数组下标 | 横轴必须由**日期**决定；脏日期回退等距 |
| 小多图各格按自己范围铺开横轴 | 先问「各图坐标轴是不是同一把尺子」；窗口由日期**并集**算一次全格共用 |
| 共享时间轴但采样频率差 1~2 个数量级 | 先问「采样频率在同一量级吗」，不在就分区各自一根轴 |
| 靠枚举「写法」永远会漏（漏了真实数据「红细胞沉降率测定」） | 用**有界规则**（剥尾限定词 + 去括号）覆盖整类，但**仍命中显式别名表** |
| 同一蛋白的不同检测合并（hs-CRP 0.4 与 CRP 36.33 同日合并 → 图上画成 0.4） | 量级差一个数量级的检测**必须各自成序列** |
| **「从多条里挑一条」这个动作本身可能就是错的**（连修三版才到位） | 先问「能不能不取？把冲突原样呈现出来是不是更好？」**用户的数据决定让用户自己做** |
| 数据结构从「一对一」放宽到「一对多」后消费方未同步改（线画对了但读数仍只报最小那条，**看起来像修好了**） | 放宽后 grep 所有「按 key 取单值」的调用点 |
| 「图上没有」被读成「没测过」 | 凡「因为不确定所以没画」的**必须把它存在过这件事说出来** |
| 对「图上已经看得到」的事实再加一行文字 | 文字补注只用于**图上看不出来的事** |
| **给「安全相关」参数配默认值 = 允许调用方忘记它**（`doneSlotRefs = emptySet()` → BootReceiver 漏传 → 已服药仍被追问） | 凡「缺失导致**静默错误行为**（而非编译失败）」的，**一律不给默认值** |
| 「只重排未来」会顺手清掉「今天稍后还要用的」 | 「取消未来 + 重排」的入参必须能区分**已过点未完成 / 已过点已完成 / 未到点**三态 |
| 从「在用」流里按 id 取单条 = 记录归档后永久挂起（且重试保存产生重复药） | 编辑态一律**按 id 直查**，不限「在用」 |
| 给只读列表加「编辑」入口未定义字段间不变量（出现「已服 + 原因=遗忘」；`@Upsert` 是 REPLACE，改身份字段撞键会**静默删记录**） | 编辑 = 把**写入侧不变量**重走一遍；身份字段设为只读 |
| 未在本表单呈现的字段被静默重置为实体默认值 | grep 该实体所有构造点逐一核对，未呈现字段从 `initial` 透传 |
| 字段只在一条分支里渲染 → 另一条分支被静默写入表单默认值 | `buildXxx()` 里每个字段都要能回答「用户在哪里能看到 / 改到它」 |
| 用「固定候选 + 选中态」表达可自由取值的字段（值不在候选里就完全看不出来） | 可自由取值的字段，UI 必须把**当前值作文本直接显示** |
| 从 ISO 时刻串**尾部**截取时刻（串长在 19/23/26 浮动 → 「已服 19981」） | 要么解析、要么**从头截**；凡按固定位置截取都要问「这个串长度固定吗」 |
| 整数除法截断吃掉边界（48h+1min → 误判仍在窗口内） | 阈值比较**保留原始精度单位** |
| 枚举 `key` 属于数据格式（改了老记录映射不到中文且不报错） | 必须有「存库键稳定性」单测；映射不到时**回退显示原始值** |
| `if (dao.count() > 0) return` 让老设备永远拿不到种子更新（完全静默） | `KB_SEED_VERSION` 闸门 + 按 id 比对；每次增补 / 修订种子**必须 bump 版本** |
| 崩溃留档只记未捕获异常会**丢掉被 `runCatching` 吞掉的根因**；摘要按未混淆包名过滤在 release 下**恒为空** | `recordNonFatal()` 也落盘；摘要 = 异常首行 + `Caused by` 首行 + 前 4 条栈帧；**任何 catch 兜底要么留档要么上抛** |
| 崩溃留档的隐私边界 | 凭据一律不入盘；`allowBackup=false`、永不上报 |

### G. 发布流程 / PowerShell

| 坑 | 对策 |
|---|---|
| **⚠️ PS 5.1 按 GBK 解析无 BOM 的 .ps1（三个变体）**：① 内联中文变乱码写进 release body ② **注释行末中文字尾字节吃掉换行符** → 下一行被并进注释 → `Unexpected token '}'` 行号指向无关位置 ③ 合并管道 `gh … 2>$null \| ConvertFrom-Json` 在 EAP=Stop 下**静默吞掉 stdout**（退出码 0、无异常） | **铁律：`.ps1` 一律 ASCII-only（注释也不许中文）**；中文走外部 UTF-8 文件运行时读入；gh/curl 调用后**显式判 `$LASTEXITCODE`**；写操作后**必须回读比对**；`"$t: 文本"` → 用 `"${t}: …"` |
| `gh release create --title "中文"` 标题乱码（argv 走 GBK） | 标题走 `gh api -X PATCH --input <utf8 json>`；读 gh 输出前设 `[Console]::OutputEncoding = UTF8` |
| **批量修正不要做成泛化改写**（「修标题」时按规则把 v1.0.1–34 正常标题一起覆盖） | 先**显式列出目标集合**并保留原值，只改目标项；改完全量核对，越界即整体回滚 |
| 资产名替换漏项（v1.0.70 release 上曾挂 `ashkb-1.0.69-*.apk`） | 派生脚本后逐字核对资产名；传完下载回来**逐字节比对 SHA256** |
| `$id` 被 jq `.[]` 遮蔽 | 改用 `$relid` |
| **从预发布转正不会自动接管 Latest** | `--prerelease=false` **还须再加 `--latest`** |
| github.com 直连超时（`api.github.com` 可通） | 走 gh API 逐文件上远端（blobs → trees → commits → PATCH refs）；**tree SHA 必须与本地 commit 的 tree 一致**，否则文件清单有漏 |
| 请求体走 stdin 会损坏（PS 5.1 长 JSON → 全部 400） | 一律写临时文件 + `gh api --input` |
| 网络偶发 500 / 超时 / 422 | 重试即过；422「tag already exists」说明其实已成功 |

### H. 文档 / 流程

| 坑 | 对策 |
|---|---|
| **测试条数有多处副本** | 改一处必须同改；**交付前用 `app\build\test-results\testDebugUnitTest\*.xml` 汇总实际条数再回填** |
| README 里写 `./gradlew`——本仓库不存在 | 用 `build-env\gradle-8.7\bin\gradle.bat -p patient-health-app` |
| `.kt` 是 **LF**、`strings.xml` 是 **CRLF**（批量脚本按 `\r\n` 切分会让替换静默不生效；行尾转换制造误导性 diff） | 先归一化再处理、写完还原；`strings.xml` 必须写 `\r\n`；用 `git diff --numstat` 验证（期望纯增、0 删） |
| 一个文件可能有两段 import 块 | 按块处理会给两块各插一次 → import 重复 |
| 用户在 TRAE 手机端看不到工作区文件、也收不到聊天附件 | **唯一可下载渠道是 GitHub Release 资产链接**；release 必须挂在已推送的 commit 上 |

---

## 6. 本次复验修正（2026-09-27）

`HANDOFF.md` 中以下过期 / 不一致事实已就地修正：

| 位置 | 原值 | 改为 |
|---|---|---|
| §8 首行 | 「最新版：v1.0.58（versionCode 63）」 | v1.0.70 / versionCode 75，并注明 GitHub 正式版仍为 v1.0.60 |
| §8 测试基线 | 「45 个测试文件」 | 「48 个测试文件」 |
| §9.6 标题 | 「v1.0.20 → v1.0.28」 | 「v1.0.20 → v1.0.58；v1.0.29 起逐条标注」 |
| §9.8 | 「v1.0.25 / 26 / 27 / 28 装机结果待反馈」 | v1.0.61 ~ v1.0.70 为预发布待真机 |

---

## 7. 接手建议（按优先级）

1. **推 v1.0.70 转正**——真机走一遍 v1.0.61 ~ v1.0.70 的关键路径（尤其**跨设备提醒**：强提醒全屏权限、免打扰、久坐窗口、锁屏通知）；确认后 `gh release edit v1.0.70 --prerelease=false --latest`
2. **跑 P5 手册 A / B / C 三组**（`HANDOFF.md` §9 可勾选清单）
3. **B6 二维码**若要重做：从 §3.1 的 4 处已定位缺陷入手，且**验证方法必须是「与 segno 逐格比对」**
4. **R16 批量导入**需需求方拍板是否纳入 v3
---

## v1.0.73 批次 1「看不见的失效」——待真机人工验证的 7 项

> 客观项已由 agent 在 Xiaomi 15 Pro / Android 16 上跑完（覆盖安装 ✓ / versionCode 78 ✓ / 冷启动 216ms 无崩溃 ✓ / WAKE_LOCK: allow; time=+8s ago 证明唤醒锁真被申请 ✓ / 在册闹钟均 xactAllowReason=permission ✓ / 4 条提醒链齐备 ✓ / 设备 ase.apk 与交付 APK 逐字节一致 ✓）。以下 7 项**只能人工点按**：

| # | 操作 | 预期 |
|---|---|---|
| 1 | 「我的 → 提醒可靠性自检」 | 第 5 行「闹钟注册（最近一次重排）」显示「尝试注册 N 个 · 无失败」；入口摘要为 
/5（release 包不可 
un-as，台账文件无法脚本读取） |
| 2 | 复诊页新增疫苗记录 | 「医生确认」默认＝**待确认**；存**活疫苗**后应出现 high 级安全警报（改前永不触发） |
| 3 | 体征弹层：留空保存 / 填 体温 99 | 保存按钮禁用 + 提示；越界被拦（改前空表单可直接覆盖当天数据） |
| 4 | 打开备份页（恢复码弹窗 / WebDAV 口令 Sheet） | 该窗口 FLAG_SECURE 生效（dumpsys window 可查），离开后清除 |
| 5 | 点一次「测试提醒」 | 10 秒后正常收到（回归确认通知链未被本批改动破坏） |
| 6 | 临时加 **23:55** 测试药（验完删） | 次日 00:25 / 00:55 两个跨零点升级闹钟在册，且锚在**当天**槽位日（P1-4 主验收点） |
| 7 | 若出现末级全屏，点「稍后」 | 真排 **+15 分钟** snooze 闹钟（改前该按钮无任何副作用） |
### v1.0.74（2026-10-01，**已发布为 GitHub 正式版 / Latest**）

> **含四项**：① 跨零点追问链重建（v1.0.73 引入的回归，**已由维护者真机实证**：时钟跳到 00:24 触发
> TIME_SET 全量重排后，00:25 的追问仍准时弹出）；② 「关于」卡不再缓存版本号；③ **新增今日页
> 「昨天还有 N 剂未记录」补记卡**（写入槽位所属日）；④ PendingDoses 的 startDate 防假阳性。
> 单测 **527 条全绿**；APK shkb-1.0.74-release.apk（sha256 606f70aa…）。

- 修 v1.0.73 引入的回归：cancelAllFuture 扫 	oday-1 后，昨天跨零点的追问链会被重排清空且不重建 → 
escheduleAll 新增第 3 步重建「昨天尚未到点的升级重查」
- 设备现状：versionCode **79** / versionName 1.0.74，APK shkb-1.0.74-release.apk（sha256 9a62e73…）；GitHub Latest 仍是 v1.0.73
- 待验：今晚 23:55 测试药 → 00:10 打开应用 → 00:25 / 00:55 仍应追问；打卡记录应落在 09-30
### 批次 2 完成（v1.0.75，2026-10-01）

- **Room schema 导出并入库**：pp/schemas/com.ashkb.app.data.db.AppDatabase/17.json（90 KB）；AppDatabase 早有 xportSchema = true 却从未配 
oom.schemaLocation
- **迁移链单一来源**：顶层 ASHKB_DB_VERSION + AppDatabase.ALL_MIGRATIONS
- **两条机器可判检查**：MigrationCoverageTest（4 条，链完整/无缺口/schema 存在/版本一致）、SchemaDriftTest（Robolectric，28 张表 DDL 与导出 schema 逐字一致 + 反向查多余表）
- **androidTest 源集落地**：首个用例 RealDatabaseSchemaTest（生产工厂 + 真实库文件）；基础设施 runner / room-testing / schemas 挂 assets 均已就位
- **CI 四道门**：erifySpecSync（版本号与条数四处一致）、schema 漂移（git diff app/schemas）、验签、detekt（312 条历史问题进基线）+ Android Lint
- **缺正式签名不再静默产包**：ssembleRelease 无 keystore 直接失败，CI 用 ASHKB_ALLOW_DEBUG_SIGNING=1 放行并告警；配套 
elease-tooling/verify-release.ps1
- **本机绕行**：官方 Maven 不可达 → 用户级 ~/.gradle/init.gradle 指向国内镜像（项目文件保持官方地址）
- 待办（诚实边界）：历史 16 个版本的 schema 需按 git 历史逐版本导出补录，之后才能真正测试「从旧版本升级」路径
### 批次 3b + 批次 4（逻辑部分）完成并发布（v1.0.77，2026-10-01）

- **库 v17 → v18**：新表 planned_slots（唯一索引 (date,med_id,slot_key) 保幂等）；迁移 SQL 与导出的 18.json 逐字一致，由新增 MigrationTableParityTest 在真实 SQLite 上比对锁定；物化窗口「昨天..+7」来自单一来源，调用点 4 处
- **「用药完成度（计划剂量口径）」**：漏记现在计为「未记录」；与记录口径并列并明写分母差异；无快照时「—（暂无计划快照）」
- **漏服补发**：启动/开机各查一次，每天最多一条（按归属日去重）、静默投递、复用既有通道；「已结算」定义与今日页补记卡统一为 done/partial/skipped
- **运动引擎 fail-closed**：解析失败不再默认放行；矩阵缺失改降级；实现种子在用的 lways / mplitude_half 两个颈椎键
- **AI 异常值不再压制本地判读**；**BASDAI 4.0 收敛**到 ClinicalThresholds.basdaiHigh()（含 Double 重载，避免 	oInt() 截断）；新增 DateInput 拒绝不存在的日期
- 单测 **540 → 585 条全绿**（59 个测试类）
- **待做（v1.0.78）**：非法日期的表单提示文案；i_abnormal 列（库 v19）以实现「AI 标记与本地判定并列展示」；周月报/PDF 是否也显示计划口径
### v1.0.78 真机验证（2026-10-01，Xiaomi 15 Pro / Android 16）

- **覆盖安装**：1.0.77 → **1.0.78**（ersionCode 83），db install -r 成功
- **库迁移真机首跑通过** ✅：v17 → v18 → v19 三次迁移后应用正常打开，logcat **无 FATAL / 无 Room/SQLite 异常**
  - 直接证据（uiautomator 读屏）：药单 → 依那西普 → 用药记录弹层显示
    「**用药完成度（计划剂量口径 · 近 90 天）** … 完成 1 · 部分 0 · 跳过 0 · 未记录 0（**共 1 剂计划**）」
    —— 「共 1 剂计划」说明 planned_slots（v18 新表）**真的被物化写入**，不是空表
  - 两个口径并列与「分母不同」说明行均正常渲染；「记录内完成度（90 天）100%（完成 5 …共 5 条记录）」与历史记录一致
- **日志噪声甄别**：logcat 里的 observeForever on a background thread 属于小米 ML::MirrorCastManager，
  **非本应用**（全仓主代码无 observeForever 调用，已 grep 确认）
- **仍未真机验证**：i_abnormal 的**并列展示**需要一条「AI 标记与本地判读不一致」的真实化验记录才会出现
  （可复制 AI 导入模板造一条：AI 标「正常」而数值超出参考范围）；漏服补发的汇总通知需等次日开机/启动才会触发
### 历史 Room schema 补录完成（2026-10-01）

- 新增 `app/schemas/com.ashkb.app.data.db.AppDatabase/4.json` ~ `16.json`（13 个，均为**当时提交上真实构建导出**，非逆向编造）
- 用真的 `MigrationTestHelper` 实测：**起点 4..16 → 19 共 13 条升级路径 13/13 通过**（含按 19.json 的结构校验）
- **起点 1/2/3 永久不可覆盖**（根提交已是 v4，全历史与远端/本地 bundle 均无 1/2/3；伪造会给出虚假通过，故不做）
- 主线 `testDebugUnitTest --rerun`：**597 tests / 0 failures**（新增 JSON 未破坏任何测试）
- ✅ **已收进主线**（v1.0.79）：`MigrationPathProofTest` 在 JVM 单测里跑 13 条路径，schema 挂 main assets（实测 APK +93 KB）
### v1.0.78 真机验证（续）：`ai_abnormal` 并列展示 + 本地优先，两项均通过（2026-10-01 晚）

用「AI 标记与本地判读**故意冲突**」的化验记录实测（复诊管理 → 化验 → AI 导入化验单，粘贴）：

```
日期: 2026-10-01
项目, 结果, 单位, 参考范围, 标记
C-反应蛋白(CRP), 12.5, mg/L, 0-8, 正常
```

uiautomator 读屏结果（Xiaomi 15 Pro / Android 16，v1.0.78）：

```
AI 导入化验单 2026-10-01  1 项 · 1 项异常
C-反应蛋白(CRP)
12.5 mg/L 偏高                                  ← 本地参考范围判读优先（AI 说「正常」未能压制）
AI 标记：正常；本地参考范围判读：偏高             ← v1.0.78 新增的并列展示
```

结论：
- **① 本地优先端到端生效**：12.5 > 0-8 → 标为「偏高」；旧实现（仅当 AI 未给标记才本地判读）会把它藏成「正常」
- **② `ai_abnormal` 真的写入并渲染**：AI 原始标记「正常」被保留并与本地判读并列显示
- **③ v18 → v19 迁移后的新数据路径正常**；异常计数按**本地口径**（1 项异常）
- 注：v1.0.78 之前导入的历史记录 `ai_abnormal` 为 NULL（**按设计不回填**），故老记录不会出现该行小字——这是正确行为，不是缺陷
### CI 从未通过：Lint 40 error（**待修，方案已定**）

`gh run list` 显示 CI 自建立起**每次都是 failure**（最近 6 次全红）——失败在 `:app:lintDebug`，不是签名守卫。
根因两类，维护者 2026-10-01 裁定「真修（方案 A）」，不冻结 baseline：

**① `StringFormatMatches` 31 处（统一模式）**：字符串把**数字**声明成 `%N$s`，调用点传 Int/Long。
- 例：`vm_backup_local_done`「…%2$s 表 %3$s 行…」← `BackupViewModel.kt:115` 传 `out.tableCount` / `out.rowTotal`
- 例：`vm_restore_verify_ok`「schema v%1$s…约 %3$s 行」、`vm_profile_export_done`「%1$s 字节」、`vm_dav_downloaded`「%2$s 字节」
- 例：`ReportPdfWriter.kt` 侧 23 处（`pdf_value_*` / `pdf_section_*` / `pdf_page_no` / `pdf_labs_more` / `pdf_med_inj_cycle` …）
- 修法：把接收数字的占位符改 `%N$d`（**声明侧**，调用点多数不动）。⚠️ **逐个核对**——同一字符串若有多个调用点传不同类型，改声明会让 `%d` 收到 String 而抛 `IllegalFormatConversionException`。
- 涉及字符串名（lint 报告）：`pdf_value_med_checkin` `pdf_value_exercise` `pdf_lab_ref_range` `vm_backup_local_done` `pdf_value_prn` `pdf_section_labs` `vm_restore_verify_ok` `pdf_section_contacts` `pdf_section_basdai` `pdf_section_meds` `vm_restore_done` `pdf_page_no` `pdf_value_eye_days` `pdf_page_footer` `pdf_med_inj_cycle` `pdf_labs_more` `vm_dav_downloaded` `vm_profile_export_done` `vm_profile_import_done` `pdf_section_checkups` `pdf_section_vaccines`

**② `MissingPermission` 9 处**：`reminder/NotificationHelper.kt` 的 `runCatching { nm.notify(…) }`（含 130 / 238 / 296 …）。
`runCatching` 只兜住崩溃，**没做显式权限检查**——Android 13+ 未授 `POST_NOTIFICATIONS` 时 `notify` 静默失败，
「提醒没响」会被误当成「没到点」。修法：加一个 `canNotify(context)`（`checkSelfPermission` + `areNotificationsEnabled`）
并在每个 notify 前显式判断（未授权时跳过并留可诊断的痕迹），保留 `runCatching` 作双保险。

**执行顺序**：等批次 6（改删能力）子代理收工后再动——lint 修复主要落在 `strings.xml`，与批次 6 重叠，并行会互相覆盖。
修完跑 `testDebugUnitTest :app:detekt :app:lintDebug` 全绿 → CI 变绿（失败邮件自然停止）→ 随 v1.0.80 发布。
### CI 首次通过（2026-10-01，v1.0.80）

- **事实订正**：CI 自建立起**从未通过**——此前文档把它当"门"来描述是**不准确的**。失败点一直是 `:app:lintDebug`（40 个 error：31 `StringFormatMatches` + 9 `MissingPermission`），每次 push 都会因此发一封失败邮件。
- v1.0.80 真修（**未冻结 baseline**）后：`gh run list` → **`completed success`**（run `36878380610`，5m27s）。
- 前置的 lint 修复由子代理在**独立 `git worktree`** 里单独验证过（只有它的两个文件时全绿），并用真实 JVM `String.format` 跑过全部 21 个改动字符串确认不会抛 `IllegalFormatConversionException`。
- ⚠️ 记一个**承重细节**：`NotificationHelper` 的权限检查必须有 `SDK_INT >= 33` 判断——API 26–32 上 `POST_NOTIFICATIONS` 未定义、`checkSelfPermission` 恒为 DENIED，无条件检查会让这三个大版本的**所有提醒静默失效**。
### 真机验证：批次 7 与批次 8（2026-10-01 深夜，Xiaomi 15 Pro / Android 16）

维护者逐项确认 + 我 adb 读屏核对：

| 项 | 结果 |
|---|---|
| **批次 7 补剂详情弹层**（v1.0.81 新界面） | ✅ **维护者真机确认可用**：「有删除按钮」——点补剂条目弹出详情、记录行可删，这是本批唯一无自动化测试的部分（仓库无 compose-ui-test 依赖） |
| **批次 8 ① 概览页**（v1.0.82） | ✅ 读屏核对：「覆盖起点」行**已删除**；口径说明已是压缩版「计划口径分母 = 已到点的计划剂量（漏记算未记录）；记录口径分母 = 已记录条数」；补剂依从徽标文字完整为「达标」 |
| **批次 8 ② 化验页提示条截断** | ✅ 维护者确认「正常了」——根因是 `StatusChip` 高度钉死 28dp（非 maxLines/padding），改 `heightIn(min=…)` 后两行不再被裁 |
| **批次 8 ② 补充：贴太紧** | ✅ 维护者反馈「顶端死死贴着上层」→ v1.0.83 加 `Spacing.xs`（4dp，项目刻度里注释为「chip 间距」的值）。**未采用 1–2px**：低于视觉阈值会看着像渲染错位而非留白，且 `Spacing.xxs`(2dp) 的注释明确限定为「组件内部：图标与文字之间」 |
| **批次 8 ③ 补剂依从「达标」挤压** | 已修（说明列改 `weight(1f, fill = false)`，徽标按内容宽度优先测量）；读屏确认徽标文字完整，**像素级观感待维护者最终确认** |
### v1.0.85（BackupScreen 拆分）的验收：**本机条件不具备，未能验收**

- 该批的验收标准是「**附件同步进行中，去输密码不再被进度 tick 打断**」——但维护者真机上**目前没有任何附件**，那三条高频 Flow（`attachSyncStage` / `attachSyncMsg` / `attachBytes`）不会产生数据，**因此这个体感验收做不了**。
- 已完成的替代证据（**不等于体感验收**）：① 反汇编编译产物确认 `SectionCard { … }` 的 lambda 是 replaceable group 而非 restart group，故改前 lambda 内的 state 读确实算在整个屏幕上；② 编译器报告确认新 section 均 `restartable skippable`，动作 lambda 被 remember 化；③ 根级 Flow 19 → 3（每条留下的都写了理由）。
- **诚实结论**：该批的**体感收益未被验证**；机制层面已被证据支持。若日后设备上有了附件，可随时补验（验收动作：同步中输密码）。
- 同类待验：**v1.0.86 的 `DateProvider` 广播注册**（"跨零点收到 `ACTION_DATE_CHANGED`"）同样需要真机跨零点或手动改时钟才能验，目前只覆盖了纯 JVM 语义。
### 级联删除：真机全路径验证通过（2026-10-02，Xiaomi 15 Pro / Android 16，v1.0.86）

维护者用**自己新加的假数据**（项目「MRI」+ 一条复诊记录 + 指派的 4 项化验单）走完整条路径：

| 环节 | 实测 | 结论 |
|---|---|---|
| 确认框 · 0 条变体 | 「删除复诊记录「MRI」？/ **该记录名下没有化验 / 影像 / 附件。** / 删除后无法恢复（可从备份中还原）。」 | ✅ 不报"0 条"，而是明说没有；0 条的类别不显示 |
| 确认框 · N 条变体 | 「该记录名下还有以下内容，将一并删除：/ **4 条化验结果**」 | ✅ **报数与实际子记录一致**（4 项化验单 = 4） |
| 确认后 · 记录 | MRI 的记录卡消失 | ✅ |
| 确认后 · 子记录 | **2026-08-02 那条 4 项化验单消失** | ✅ **级联真的删掉了子记录**（这是本次最关键的证据） |
| 确认后 · 项目 | 项目「MRI」**仍在** | ✅ 符合裁决：项目只「停用」不物理删除 |

**仍未真机验证的部分**：附件（行 + **磁盘文件**）的级联删除——本次记录名下没有附件；该路径由 19 条 Robolectric 测试覆盖（其中一条专门断言附件行与磁盘文件都真的没了）。

**顺带发现的待查项**：健康页摘要「化验 100 条」在删除 4 条后**未变化**，但该数字与化验页实际条目数（1 + 4 + 56 = 61 项）本就不符 → 口径不明，**不能当证据用**。待查两种可能：①该摘要未随数据刷新（陈旧值）；②它统计的是别的口径（含隐藏/历史行或上限值）。**已列为待办**。

**同类待验**：v1.0.85 的 BackupScreen 拆分体感（设备上无附件，见上）、v1.0.86 的 `DateProvider` 广播注册（需真机跨零点或改时钟）。
### 批次 13 真机验证（2026-10-02，v1.0.87/1.0.88）

| 项 | 实测 | 结论 |
|---|---|---|
| ① **复诊「项目」行可点跳转 + 按项目筛选** | 维护者确认：「**直接跳到「记录」tab 并只显示该项目的记录，顶部有「仅显示：MRI ✕」可清除**」 | ✅ |
| ② **准备清单可折叠** | 维护者确认：「**记录 tab 里的准备清单收起来了，记录卡出现在首屏**」 | ✅ 修掉了"以为记录不存在"的困惑 |
| ③ **「化验 N 条」口径** | 读屏实测：健康页由恒定的「化验 100 条」变为「**化验 171 项**」 | ✅ 真 bug 修好（原查询上限即 100），单位也由「条」改为更准确的「项」 |
| **v1.0.87 回归**：补剂卡片按钮全消失 | 维护者真机截图发现 → v1.0.88 热修（`RowScope` + `weight(1f)`） | ✅ 已修；**按钮是否恢复待维护者目视确认** |

**教训（写下来避免重犯）**：v1.0.87 装到真机后，我只读屏核对了"健康页化验条数"就收工，**没有把改动到的补剂卡片也逐屏读一遍** → 一个"按钮全被挤成零宽"的回归靠维护者截图才发现。**此后凡改 UI，必须把改动到的界面逐个读屏一遍再报"已装"。**

### 批次 14（v1.2.7）：清扫 OPEN-RISK 六项 —— 静态 + 单测，**真机验证 0 项**

来源是 `NEXT-SESSION.md` 的 R1–R8 高危表（每一条都在 v1.2.6 / versionCode 118 的代码上用只读审计核实过）：
**R1 已在 v1.2.2 修复**（剂量已独占整行，胶囊只与药名同行）；**R2 经维护者裁决「不动历史行、保持现状」不做**；本批做 R3–R8。

| # | 修复 | 回归测试 | 真机 |
|---|---|---|---|
| R3 | 周报「下次复诊日」恒 null → 新增 `CheckupRecordDao.upcomingByNextDate`（按 `next_date` 查） | `NextCheckupDateQueryTest` 4 条 | ❌ 未装机 |
| R4 | WebDAV 轮换加 `isSafeBackupName` 全匹配白名单（`%2f` 也拦） | `BackupEngineTest` +2 条 | ❌ 未装机 |
| R5 | 密钥采纳从 verify 移到 `restore()` 内、pre-restore 快照前；兜底错误文案资源化 | 无（顺序约束靠注释 + 代码评审） | ❌ 未装机 |
| R6 | `GlobalMessages` 改 `Channel`（无订阅者期间不丢） | `GlobalMessagesTest` 1 条 | ❌ 未装机 |
| R7 | 级联删除改「事务内删行 → 提交后删字节」 | `RecordDeletionCascadeTest` +2 条中间态 | ❌ 未装机 |
| R8 | 知识卡弹窗改用注入日期（四个调用方） | 无（`today` 由编译期强制传参） | ❌ 未装机 |

**⚠️ 未验证清单（如实记录）**：

1. **六项全部没有装机**——构建机 `adb devices -l` 与 `adb mdns services` **均为空**（无线调试未开或设备不在线）。
2. 按上面的教训，本批**唯一改到的界面**是知识卡详情弹窗的「已过期」胶囊（`KbDetailDialog`）——需要维护者从
   **知识库 / 紧急卡 / 症状 / 运动**四个入口各点开一张知识卡，核对胶囊与页面顶部日期一致（跨零点前后尤其要看）。
3. R7 的两种「可接受崩溃态」只在 Robolectric 里造过（`deleted_at` 墓碑、用非空目录卡住删字节）；
   **附件级联删除（行 + 磁盘文件）从 v1.0.86 起就一直没在真机上验过**（设备上没有附件，原因同上）。
4. R4 的白名单只做了单元测试；**真实 WebDAV 服务端**上「轮换到底删了哪几个文件」未复验。
5. R5 的「取消恢复后密钥不再被采纳」需要一个**携带不同 vault key 的备份**才能真机验证（换机恢复场景），本批未做。

### 批次 A（v1.2.8）：UI 2.0 换色 —— 只换颜色，装机双主题截图已核对

来源是维护者提供的设计稿「**澄序 CLARITY · ASHKB UI 2.0**」（`ashkb-ui2-clarity.html` + `ashkb-ui2-handoff.md`，基于 v1.2.7 出具）。
设计稿自己把落地分成四批（①换色 ②换形 ③换件 ④换图），维护者裁决**先做批次 A**——只换颜色，出一个能装机的最小对比包，看完效果再决定后面三批。**v1.2.8 就是这个对比包**。

改动面 **3 个文件**，与交接包 §5「明确不改动的文件」完全一致：屏幕结构 / 组件 API / `domain/` / `data/` / 任何 ViewModel / 导航图**一行未动**。

| 文件 | 改了什么 |
|---|---|
| `app/src/main/java/com/ashkb/app/ui/theme/Color.kt` | 48 个角色 × 浅 / 深两套**整份**换成设计稿的值；文件头的生成参数换成设计稿口径（并注明仓库里没有那个生成器，避免下次重生成时静默回退旧暖色板）；`object Glass` 六值改冷调——`borderLight` 的**语义从"白高光"变成"冷发线"** |
| `app/src/main/java/com/ashkb/app/ReminderFullScreenActivity.kt` | 强提醒全屏页「已服」按钮写死 `Color.White` → `MaterialTheme.colorScheme.onError`（深色 `error` 变成高明度红后白字只有 **2.02:1**；`onError` = **7.99:1**），删掉因此不再使用的 `Color` 导入 |
| `app/build.gradle.kts` | `versionCode` 119 → **120** / `versionName` 1.2.7 → **1.2.8** |

**静态校验**（`E:\ASHKB\verify-palette-contrast.ps1`，只读、可复跑）：**48 × 2 个角色与设计稿表逐条一致**（0 缺失 / 0 不符 / 0 多余）；**18 组文本对全部 ≥ AA**，最低 **4.85:1**（浅色 `onSurfaceVariant` `#5A6B78` on `surfaceVariant` `#EDF1F4`），深色最低 6.67:1，浅色 `onPrimary`/`primary` = 5.62:1。

**门禁**：`cleanTestDebugUnitTest testDebugUnitTest :app:detekt :app:lintDebug` → **BUILD SUCCESSFUL in 3m14s**；单测全绿（约 100 个文件；v1.2.8 当时实测 874 条，**v1.2.9 起文档不再记数**），detekt 干净、lint 0 error。
中途被 detekt 拦下过一次（`ReminderFullScreenActivity.kt` 改动首版超出 `MaxLineLength`，拆行后通过）；**本版没有向 detekt 基线添加任何条目**。
构建：`assembleRelease assembleDebug verifySpecSync` → **BUILD SUCCESSFUL in 2m34s**，`verifySpecSync` 通过（"版本 v1.2.8（120）"；当时该任务还校验单测条数，v1.2.9 起已去掉）。
release APK = `ashkb-1.2.8-release.apk`（5220504 B，sha256 `ECE175F8CF4A3602CC10BAD7A8867D944E45727CB4B15DB5E6C49819E0064399`），`apksigner verify --print-certs` → 正式证书 `38ca80a6…`（**未回退 debug 签名**）。

**真机验证**（Xiaomi 15 Pro / Android 16 / API 36 / 1440×3200，无线调试）：`adb install -r` 覆盖安装 → `versionCode=120 / versionName=1.2.8`，**数据未丢**（知识库 48 条 / 化验 171 项 / 档案 / BASDAI / 症状记录均在）。按 §7 的教训，**改到的界面逐个读屏 + 截双主题图**：

| 界面 | 浅色 | 深色 |
|---|---|---|
| 今日 | ✅ 瓷白底 `#F3F6F8` + 白卡 + 冷蓝「添加药品」+ 冷白 Dock（选中胶囊 `#DCEEF7`） | ✅ 冷黑底 `#0C1218` + 卡片 `#151D26` + 浅蓝文字 / 胶囊 `#124F6E` |
| 健康管理 | ✅ 白色卡片 + 冷蓝图标，无零宽元素、无不可读文字 | ✅ 深色卡片 + 浅蓝图标，文字清晰 |

截图：`E:\ASHKB\ui2-shots\before\today-{light,dark}.png`（v1.2.7 旧配色基准）与 `E:\ASHKB\ui2-shots\after\{today,health}-{light,dark}.png`（同一台机、同一页）。

**⚠️ 未验证清单（如实记录）**：

1. **强提醒全屏页的「已服」按钮没有真机截图**——该页只在**锁屏 / 息屏**且升级链走完（`ReminderScheduler.MAX_ESCALATION = 2`，需 +30 / +60 两跳）时才出现，本轮没有制造这个场景；依据只有 WCAG 复算（2.02:1 → 7.99:1）。
2. 深色下**卡片比页面底略暗**（`surfaceContainerLowest` `#0A0F14` on `background` `#0C1218`；全仓 **12 处**把 `surfaceContainerLowest` 当"卡片底"用）——**与旧配色同构**（旧 `#130D09` on `#18120E`），**不是本版引入**；设计稿本意是"卡片比底亮"，这条属于**批次 C 的"角色取色"问题**。
3. 本版**只换色**：形（圆角 `10/14/18/28`、Dock 圆角 20、数据字号上限 36sp）、件（卡片发线 / 42dp 图标瓦片 / 分段控件 / 表单凹陷填充）、图（Today 用药卡时间轨）、**启动图标**（`ic_launcher_background.xml` 仍是旧钢青 `#416476`）全部保持原样 —— 逐条列在 `CHANGELOG.md` 的 v1.2.8「本版没有做的事」。
4. **发布事实**：`git push` 远端 main = `e21e4d0` → 附注标签 `v1.2.8` → `e4320d8`（远端 `refs/tags/v1.2.8^{}` 已核对）→ `gh api -X POST releases --input rel-v128.json`（`make_latest=true`）→ 资产 `ashkb-1.2.8-release.apk`（5220504 B，sha256 `ece175f8…`）与本地 `Get-FileHash` **逐字节一致**（release id `408053469`，资产 id `625371828`）。

### 批次 A2（v1.2.9）：裁决项 1、2 收尾 + 首页补剂卡 —— 静态全绿，**真机验证 0 项**

三件事捆在一版：审查裁决项 1（对乙酰氨基酚改 `ANALGESIC`，已先行提交 `5ecbf33`）、审查裁决项 2（「已结算」口径统一，用户 m01814 裁决 = A）、以及维护者报告的新缺陷「补剂提醒在首页看不到」（m02219）。逐项动机与改动清单见 `CHANGELOG.md` 的 v1.2.9 节，此处只记**验证事实**。

**裁决项 2 的核心**：`medication_logs.status` 的三态 done / partial / skipped 自 v1.0.77 起就定「三者都算已结算」，唯一来源 `domain/AdherenceCalc.kt:56 val SETTLED_STATUSES: Set<String> = setOf(DONE, PARTIAL, SKIPPED)`；但 `MedicationRepository.doneSlotRefs`（只认 done）、`ReminderReceiver`（只认 done||skipped）、`TodayScreen.isMissed`（无 partial）各算了一遍。用户可见后果是一条链：记「部分完成」→ 首页显示红色「漏服」+ 漏服指引 → Hero 仍算进「待办 N」→ 重排当没交代重建 → +30/+60 一路升级到 `MAX_ESCALATION` 全屏锁屏轰炸。修法是**统一到唯一口径并删掉 `doneSlotRefs`**，而不是给 partial 另定规则。

**首页补剂卡**：改动前补剂只有「到点弹通知」和「健康 → 营养与骨健康」两个出口，今日页一个字都不提，用户设了提醒时刻也无从确认、打卡要进二级页。今日页新增一张自收流量的卡（`TodaySupplementCard`），数据面在 `TodayViewModel`（不依赖营养页是否被创建过），状态口径复用 `SupplementLogStatus`，时刻展示用 `SupplementReminderScheduler.parseTimes`（与排程同一份解析）。

**门禁**：`cleanTestDebugUnitTest testDebugUnitTest :app:detekt :app:lintDebug` → **BUILD SUCCESSFUL in 2m32s**，单测**全绿 0 失败 / 0 错误**（101 个文件；**条数不落文档**，v1.2.9 起如此），detekt **0 条**、lint **0 error**。`verifySpecSync` → **BUILD SUCCESSFUL**。

**本版按铁律改代码、不进 detekt 基线**，并**删掉**了两条签名已过期的基线条目（`config/detekt/baseline.xml` 里 `rescheduleAll` 的 `CyclomaticComplexMethod` 与 `NestedBlockDepth` 仍引用着已改名的 `doneSlotRefs`）：

| detekt 条目（改前） | 修法 |
|---|---|
| `ReminderScheduler.kt:77` `rescheduleAll` CyclomaticComplexMethod 24 + NestedBlockDepth | `rescheduleAll` 瘦身为三行调用（`scheduleFirstReminders` / `rescheduleTodayEscalations` / `rescheduleCrossMidnightEscalations`），每个再拆出「单 med / 单槽位」函数并改用 `forEach { … }` + `return`——detekt 的 `NestedBlockDepth` 阈值是 3，三层循环 + 循环体本身就是 4 层 |
| `TodayScreen.kt:730` `medStatusOf` complexity 16（阈值 15） | 新增 `TodayItem.isSettled`（`done \|\| partial \|\| skipped`，`SETTLED_STATUSES` 的「按槽位」视图）+ 把抄了两遍的跳因解析收成 `skipReasonText(item)` |
| `TodayScreen.kt:792` `isMissed` ComplexCondition 4（阈值 4） | 首行改 `if (item.isPrn || item.isSettled) return false` |
| `TodayViewModel.kt:362` NewLineAtEndOfFile | 文件补末尾换行 |

**回归测试**：`ReminderSchedulerTest.kt` 新增 `` `今日已结算的槽位不再重建升级重查——partial 与 skipped 同理` ``（对三态各跑一遍；先断言未结算时升级闹钟**确实存在**，让「不存在」有判据，再断言结算后 `escAt(1/2, pastBase)` 均不存在且未来 7 天首次提醒数仍为 7——证明结算只影响今日升级重查、不误伤正常排程）。新增 `app/src/test/java/com/ashkb/app/ui/today/TodaySupplementCardWiringTest.kt` **5 条接线守卫**：这个缺陷的本质是「功能存在但首页没有入口」，不是逻辑算错——逻辑错会有别的测试红，入口缺失不会（`TodayViewModel` 一条不动、`SupplementReminderScheduler` 九条全绿，用户照样看不到）。本模块没有 `androidx.compose.ui:ui-test-junit4` 依赖，起不了 `createComposeRule`，故沿用 `SupplementSkipWiringTest` 的源码文本扫描先例；断言不锁实现细节，只守「卡被登记 / 有三个入口 / 写的是补剂不是药品且打卡后重排 / 复用营养页状态口径并展示提醒时刻」。

**⚠️ 未验证清单（本批已销账，保留原始记录）**：

1. ~~本批改动全部没有装机~~ → **已装机验证**：`assembleRelease` → `adb install -r` 覆盖安装成功，`aapt dump badging` = versionCode 121 / versionName 1.2.9，`apksigner verify` 正式证书 `38ca80a6…d7d`（未回退 debug 签名；**注意 debug 包签名与设备上已装的 release 签名不匹配，必须装 release 包**，否则报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。数据未丢。
2. ~~首页补剂卡需要真机看两件事~~ → **两项均已验**：①今日页显示「维生素 D3 / 4000IU · 07:00」+「已服」+「撤销」——**提醒时刻直接印在首页**（m02219 的核心诉求）；②打卡后 `dumpsys alarm` 里 `SupplementReminderReceiver` 恰好 7 条（10-13 至 10-19 每天 07:00），**今天 10-12 的 07:00 已撤干净**。撤销 / 打卡 / 「管理」跳转三条路径也都点过（截图 `E:\ASHKB\ui2-shots\v129-{today,undo,checkin,manage}.png`）。
3. ~~裁决项 2 的用户可见效果只用单测覆盖了重排一侧~~ → **维护者已实机确认**（User said (m02832)：「我测试了，记部分完成不再显示红色漏服，这个正常。」）。
4. **存量数据**（仍开放）：药单以枚举 name 字符串存库，升级后已保存的泰诺林**仍是旧分类 `NSAID`**（不崩，但仍是旧类），需手动改一次分类。刻意不做自动迁移——「哪一条是泰诺林」只能靠名称猜，猜错会改坏用户的药单。
5. ~~本版未构建 APK、未推送、未发布~~ → **已发布**：GitHub Release `v1.2.9`（release id `410782041`，Latest，非预发布），资产 `ashkb-1.2.9-release.apk` 5224936 B / sha256 `413f82b8…ce57`；提交 `5ecbf33` / `6285d69` / `86e50df` / `c0ee3b1` / `f68350b` 全部在 `origin/main`。

---

### 批次 A3（未发布）：裁决项 3 —— 口服不再提供「每两周」

维护者指示（m02842）：「你先做3，做完先别发」。**无库结构变更，可覆盖安装。**

**缺陷**：`domain/ScheduleCalc.kt:113-115 isCycleGatedBy` 里 Q2W 只在 `route == "injection"` 时按周期出卡，口服 Q2W **走「每日」**；同时 `ui/me/MedEditSections.kt:93 cycleAnchorVisible` 对口服 Q2W 也不给周期入口。于是「口服 + 每两周」在 UI 上无处填周期、排程侧却天天出卡——14 天吃 **14 剂**而不是 7 剂（口服允许一天多时刻），用户以为隔周服、实际每天吃。

**处置**：**A 方案（UI 层禁止）**，`domain` 层刻意不动（B 方案要改 `ScheduleCalc` 语义，而该语义被 `ScheduleCalcCycleTest.kt`「口服 每两周 仍按每日出卡」钉住）。

| 文件 | 改动 |
|---|---|
| `ui/me/MedEditSections.kt` | 新增 `internal fun routeVisible(frequency, route)`（口服下 Q2W 不可选）；chips 过滤加它 + 保留 `\|\| f == frequency.value` 逃生口；`MedScheduleSection` / `MedRouteDoseSection` 各加 `routeError` 形参与红字 |
| `ui/me/MedEditScreen.kt` | 新增 `routeFrequencyError` 状态位；`onPrimary()` 在 step 1 拦 `!routeVisible(frequency.value, route.value)` 并亮 `med_route_frequency_error` |
| `res/values/strings.xml` + `values-en/strings.xml` | 新增 `med_route_frequency_error`（中英同序，紧跟 `med_mtx_frequency_error`） |
| `domain/ScheduleCalc.kt` | **只加 KDoc**（`:112-119`），说明本判定刻意不动、配套处置在 UI 层 |
| `domain/ScheduleCalcCycleTest.kt` | **只加注释**（`:113-119`），说明本用例仍必须绿、被改红说明动错了层 |

**两条防线必须同时存在**：① chips 过滤让「每两周」在口服下不再出现；② 提交拦截——**① 挡不住「先选注射 + Q2W、再切回口服」这条路径**（此时 `frequency.value` 仍是 Q2W），只做 ① 的话禁止只是没画出来而已、照样存得进库。配套的**逃生口** `|| f == frequency.value` 保证编辑存量「口服 + Q2W」时频次区不会一片空白（与 `hidden` 每 8 小时的既有逃生口同理）。

**门禁**：`cleanTestDebugUnitTest testDebugUnitTest :app:detekt :app:lintDebug verifySpecSync` → **EXITCODE=0 / BUILD SUCCESSFUL in 1m25s**，单测 **892 条 / 102 文件全绿**（本批 +10 条 / +1 文件），detekt **0 条**、lint **0 error**。

**回归测试**：新增 `app/src/test/java/com/ashkb/app/ui/me/MedRouteFrequencyGuardTest.kt` **10 条**——6 条纯函数断言（口服不给 Q2W / 注射仍给 / 其余频次两途径都不受影响，防顺手扩大打击面 / v1.2.4 放开给口服的 CUSTOM 与 MONTHLY 不得被一起收回 / Q8H 归 `hidden` 管、本判定不碰 / 未知途径按不提供处理）+ 4 条接线守卫（chips 确实过 `routeVisible` / 逃生口确实在 / `onPrimary` 确实拦提交且亮可见错误 / 改途径或改频次确实清零错误位）。

**⚠️ 未验证清单**：本批**未装机**，依据只有门禁 + 单测。真机需要看三件事：①添加药品时途径选「口服」，频次 chips 里**不应出现「每两周」**；②选「注射」后「每两周」**应出现**；③编辑一张存量的「口服 + 每两周」药单时，频次区**应仍显示当前值**（不被清空），且点「下一步」**应被拦住并显示红字**。

---

### 批次 B（审查第二批 + 第三批 + 裁决项 6、7 + §6.7）：门禁全绿，已回填 CHANGELOG，未 bump 版本号

> **本节补记于 2026-10-14**，原因是原会话崩溃：`session-6f36587f-5b0a-41c0-bbf7-9d6f5ee64617`（工作目录 `E:\ASHKB`）在 2026-10-13 23:28 连续三个回合以 `413 preflight_compress_failed` 失败（核心文本约 6.56 MB / ≥ 约 875k tokens > 模型窗口 750k），期间本通道的 dsh 原生压缩被代理拒绝（`dsh_compaction_refused`：*bili owns compression on this lane*），**连 compress 工具也因预检不过而无法执行**；维护者随后输入「停止」并让原 goal 作废。**崩溃未损坏任何文件**——本批全部改动仍在工作树上，只是从未构建、从未发版、从未写进 CHANGELOG。
> 触发本批的原 goal（`goal-322ef44a-7f97-474b-a467-75a870c83b67`，已于 23:27 由维护者 clear）：*完成 ASHKB v1.2.10 的剩余审查整改：裁决项 6/7/8、审查第二批、审查第三批、UI 2.0 批次 B–E，每批跑通门禁并回填 CHANGELOG.md 与 HANDOFF-STATUS.md；未经用户明确指示不推送、不发版。* 其中 **UI 2.0 批次 B–E（换形 / 换件 / P1 项 / 换图 + 启动图标底色）本次一行未动**——`app/src/main/java/com/ashkb/app/ui/theme` 在工作树中零改动。
> 依据：`release-tooling/review-2026-10-09-reply.md` §4（裁决表）与 §5（三批排期）。**无库结构变更**：`AppDatabase.ASHKB_DB_VERSION` 仍为 19，`app/schemas/…/` 4..19.json 全部未动，可覆盖安装。

**工作树现状**（**2026-10-15 更新**）：`git status --porcelain` = 63 modified + 12 untracked；`git diff --shortstat` = 63 files changed, 1596 insertions(+), 374 deletions(-)。HEAD = `b1781c8`「v1.2.10: 口服不再提供「每两周」（审查裁决项 3）」，`app/build.gradle.kts:31-32` 仍 `versionCode = 121 / versionName = "1.2.9"`。**`CHANGELOG.md` 已补入「未发布 — 审查第二批 + 第三批 + 裁决项 6、7」一节**；`HANDOFF.md`、`README.md`、版本文件仍零改动（版本号未 bump，故 `verifySpecSync` 的字面量校验仍按 v1.2.9 通过）。

#### 一、第一批（口径统一）——已落地

| 项 | 文件 · 关键改动 |
|---|---|
| CRITICAL-1 补剂时刻 JSON 解析 | `domain/SupplementTiming.kt:62/:90` 改按 JSON 解析（`minByOrNull` 替 `distinctBy`，即 L4）；`ui/wellness/WellnessScreen.kt:829` 口径换成 `ScheduleCalc.parseTimes`；`reminder/SupplementReminderScheduler.kt:70` 直接委托 `ScheduleCalc.parseTimes`；`domain/ScheduleCalc.kt:41` 抽出 `takeTimesOf`；`domain/SupplementLimits.kt:19` 同步 |
| §6.5 运动「算完成」唯一判据 | 新增 `domain/ExerciseLogStatus.kt`；`data/db/Daos.kt:406` 改传状态过滤参数（不再硬编码 `status='done'`）；`data/repo/ReportRepository.kt:147/:248`、`ui/exercise/ExerciseViewModel.kt:127` 改引它 |
| §6.10 依从率 / 报告分母分叉 | `domain/AdherenceCalc.kt:134`、`data/repo/ReportRepository.kt:113/:225`、`data/db/Daos.kt:112/:144` 统一口径 |
| L1 整数运算 | `domain/AdherenceCalc.kt:91` |
| L5 注释订正 | `domain/PendingDoses.kt:24`、`ui/today/TodayViewModel.kt:243` |

#### 二、第二批（内存与列表）——已落地

| 项 | 文件 · 关键改动 |
|---|---|
| CRITICAL-2 本地备份体积上限 | `data/backup/BackupEngine.kt:87 PICK_LIMIT_BYTES = 64 MiB`、`:90 withinPickLimit`、`:112 readBounded`；`ui/backup/BackupScreen.kt:185-200` 先 `querySize` 预检再 `withContext(Dispatchers.IO)` 读入；`ui/backup/BackupViewModel.kt:284` 二次守卫。新增 `test/data/backup/BackupPickLimitTest.kt` |
| HIGH-3 待恢复状态泄漏 | `ui/backup/BackupViewModel.kt:349-351` 成功/失败**两路**都清 `_pendingRestore` / `_pendingFileName` / `wipeRestorePassword`；`:364-371 override fun onCleared()` 兜底；`ui/backup/BackupScreen.kt:280-285` `DisposableEffect { onDispose { pickedBytes = null } }` |
| HIGH-5 冷启动 I/O | `ui/AppShell.kt:172-173` 移入 `LaunchedEffect`；`CrashLogger.kt:135 suspend fun takeLast`（`withContext(Dispatchers.IO)`）、`:53 MAX_NONFATAL_CHARS = 32 * 1024`、`:110-114` 超限保留尾部 |
| M5 分享失败提示 | 新增 `ui/components/Share.kt:30-32 shareOrReport`（`runCatching{…}.onFailure{ GlobalMessages.post(getString(report_share_fail)) }`）；`ui/report/ReportExportPage.kt:63/:85`、`ui/backup/BackupScreen.kt:355/:546` 两处目标点均已接入（原 BackupScreen 连 `onFailure` 都没有） |
| M6 预填只跑一次 | `ui/me/MedEditScreen.kt:159 val prefillDone = rememberSaveable(editId){ mutableStateOf(false) }`、`:173-174` |
| M7 底部弹层可滚 | `ui/backup/BackupScreen.kt:1070` 加 `.verticalScroll(rememberScrollState())`（第二处 `:1148/:1155` 同） |
| M8 90 天用药史 | 新增 `domain/MedHistory.kt:30 MED_HISTORY_WINDOW_DAYS = 90`、`:38 MAX_ROWS = 60`、`:46 visible = logs.take(MAX_ROWS)`、`:53 isTruncated`；`ui/me/MedsScreen.kt:532/:535-537/:565` 用截断 + `med_history_truncated` 文案（照 `SupplementHistory` 先例，未虚拟化） |
| §5-HIGH-4 档案导出补 7 字段 | `data/repo/BackupRepository.kt:459-468` 导出 `spine_mobility` / `comorbidities` / `lifestyle` / `emergency_med_summary` / `emergency_note` / `weight_target_low` / `weight_target_high`；`:527-538` 导入侧 `optString(...).ifBlank { existing?.x }`、`has(...)` 保 existing |
| §5-MEDIUM-6 导入按 name_key 复用药品 id | `data/repo/BackupRepository.kt:565-570 upsertImportedMedication`：`nameKey → medicationDao().activeByNameKey → id = existing?.id ?: Ids.new("med")` |
| §6.9 fromKey 区分 null / 无法识别 | `data/entity/Entities.kt:80 fromKeyOrNull`、`:93 fromKey = fromKeyOrNull(k) ?: DAILY`、`:99 labelResOrNull`、`:141 SkipReason.fromKey` 返回可空、`:163 Reaction.fromKey`、`:857-866 EmergencyScene.fromKeyOrNull`；消费方 `ui/emergency/EmergencyEventSheet.kt:95/:165-168 UnrecognizedSceneHint`、`:272` 保留原始串。新增 `test/data/entity/EnumFromKeyTest.kt` |

#### 三、第三批（文案与格式清理）——除两项外已落地

| 项 | 文件 · 关键改动 |
|---|---|
| HIGH-2 统一 Locale.US | 现 **54 处 / 约 20 文件**（`WellnessVitalsSections.kt`、`ExerciseScreen.kt`、`MedEditSections.kt`、`WellnessScreen.kt`、`TodayScreen.kt`、`SymptomScreen.kt`、`SymptomDialogs.kt`、`TrendChart.kt`、`SmallTrendChart.kt`、`MissedDose.kt`、`ReportPdfWriter.kt`、`ReportScreen.kt`、`AttachmentSheet.kt`、`BackupScreen.kt`、`CheckupLabForms.kt`、`CheckupLabImaging.kt`、`HealthRepository.kt` 等，多数带「v1.2.10 审查 HIGH-2」注释）；资源侧 `%f → %s`（`values/strings.xml pdf_lab_ref_range`、`values/strings_ui.xml` 总分×2 + 僵硬分），res 中已无 `%f`。新增 `test/i18n/FormatLocaleGuardTest.kt` 静态守卫（只守 `"…".format(`，放行 `%x`） |
| M1 周期天数下界 + 导入钳制 | `domain/ScheduleCalc.kt:26-27 MIN_CYCLE_DAYS = 1 / MAX_CYCLE_DAYS = 999`、`:36 clampCycleDays`；提交侧 `ui/me/MedEditScreen.kt:229`；导入侧 `data/repo/BackupRepository.kt:586`；红字 `ui/me/MedEditSections.kt:403` |
| L2 DndWindow 容错 | `domain/DndWindow.kt:38-39 runCatching { LocalTime.parse(...) }.getOrNull() ?: return false` |
| L6 skip()/checkIn 对称 | `data/repo/MedicationRepository.kt:319-329`（`skip(..., date: LocalDate = LocalDate.now())` + 跨零点 KDoc） |
| L8 Ids.new 随机尾加长 | `data/db/AppDatabase.kt:557 RANDOM_TAIL_CHARS = 8`、`:562`（36⁸ ≈ 2.8 万亿） |
| §6.4 reaction 统一 | `data/entity/Entities.kt:163`（`fromKey` 大小写不敏感）；写入侧默认值改 `Reaction.NONE.name`：`ui/today/TodayViewModel.kt:176`、`data/repo/MedicationRepository.kt:280`；历史行靠 `fromKey` 兼容 |
| §6.6 vaccine_live_pending | `ui/symptom/SymptomCards.kt:68` 文案 `vaccine_live_pending_alert`、`:78 Icons.Rounded.Vaccines`；`domain/DerivedAlerts.kt:29` |
| §5-MEDIUM-5 csvToJson 转义 | 实现移到新增 `domain/ProfileLists.kt:24-28`（`JSONArray(items).toString()` 转义）、`:36-40`（解析失败原样返回）；`ui/me/ProfileEditScreen.kt:318/:323` 改为调用 |
| 裁决项 6 零引用枚举标注「预留」 | `data/entity/Entities.kt:106`（CheckStatus）、`:512`（FeedbackChange），注释「当前零引用（预留）」 |
| 裁决项 7 写读往返测试 | 新增 `test/domain/WriteReadRoundTripTest.kt`（覆盖 `take_times` / `times` / `allergies` / `lifestyle` / `comorbidities`） |
| §6.2 迁移起点说明 | 已由既有文字满足：`HANDOFF.md:450`「从 v4 起的所有升级路径都有自动化覆盖」、`:459`「没有 1/2/3.json 就建不出对应旧库…故不做」——**非本次新增**，工作树无 `.md` 改动 |

**新增 12 个文件**：`domain/ExerciseLogStatus.kt`（§6.5 唯一判据）· `domain/MedHistory.kt`（90 天窗 / ≤60 行纯函数）· `domain/ProfileLists.kt`（档案 allergies/comorbidities 唯一编解码）· `ui/components/Share.kt`（M5 落地）· `test/data/backup/BackupPickLimitTest.kt` · `test/data/entity/EnumFromKeyTest.kt` · `test/data/repo/MedAdherenceParityTest.kt`（L1 对拍）· `test/domain/ExerciseLogStatusTest.kt` · `test/domain/MedHistoryTest.kt` · `test/domain/WriteReadRoundTripTest.kt` · `test/i18n/FormatLocaleGuardTest.kt` · `test/ui/emergency/EmergencyEventSheetFieldsTest.kt`。

#### 四、本批未落地

- ~~**§6.7**（`StopReason` 的 `"postpone"`）~~ → **2026-10-15 已落地**（本批最后一处代码缺陷）。`data/entity/Entities.kt` 的 `StopReason` 补 `POSTPONE(..., hidden = true)` 与 `pickable()`（口径同 `MedFrequency.Q8H` 的 `hidden`：枚举项保留、不可选）；`data/repo/MedicationRepository.kt:618` 由字面量 `"postpone"` 改为 `StopReason.POSTPONE.name.lowercase()`（**落库值逐字不变，无需数据迁移**，历史行仍靠 `fromKey` 兼底）；`ui/me/MedsScreen.kt:314` 停药弹层单选改 `StopReason.pickable()`，否则「顺延」会混进患者的停药原因列表；`values/strings.xml` 与 `values-en/strings.xml` 各补一条 `enum_stop_reason_postpone`（插在 `_other` 之后以保持中英顺序一致）。回归：`domain/StopWarningTest.kt` 新增 1 条（`fromKey("postpone") == POSTPONE`、`hidden` 为真、不在 `pickable()` 内、`OTHER` 兜底不变），`data/repo/PostponeInjectionSlotTest.kt` 顺延用例补 `reason` 断言。
- **L7（可选）**：`ui/emergency/EmergencyScreen.kt:97-99` 的 `tel:` 无格式校验（审查答复标「可选」）。
- **M7 残留（可选升级）**：`ui/backup/BackupScreen.kt:1097` 仍是 `DividerList`（非惰性），只修了滚动 / 尾部可达。
- **裁决项 5**：`domain/ClinicalThresholds.kt:34-35` 目前只有 `const val LAB_NEAR_BOUNDARY_RATIO = 0.05f` 与 KDoc，**「临界」显示未实现**；该文件在「子代理不得改」清单内，须维护者本人动手或明确授权。
- **裁决项 8**：报告 §九 需维护者提供原件。
- **UI 2.0 批次 B–E**：换形 / 换件 / P1 项 / 换图 + 启动图标底色，**本次零改动**。

#### 五、门禁现状

- **2026-10-15 已重跑并全绿**：`cleanTestDebugUnitTest testDebugUnitTest :app:detekt :app:lintDebug verifySpecSync` → **BUILD SUCCESSFUL in 4m 1s**（`E:\ASHKB\v1210-gate1.log`，42 actionable tasks: 27 executed, 15 up-to-date）。单测 **965 条 / 110 文件全绿**（failures=0 / errors=0）；detekt **0 条**；lint **0 error**（184 条全为 Information/Warning）；`verifySpecSync` 通过。
- **本次修掉的门禁红项**：09:28 之后那批未过门禁的改动引入了 `ui/me/MedEditScreen.kt:256:9 Function onPrimary has 7 return statements which exceeds the limit of 6. [ReturnCount]`（`config/detekt/detekt.yml:46-47 ReturnCount: max: 6`）。按铁律**在代码里修、不进基线**：把 7 个 `return` 的校验链收成表达式体 `fun stepOneBlocked(): Boolean = when { … }`，分支次序与三处错误位副作用逐字保留，`onPrimary` 只剩 2 个 `return`。`MedRouteFrequencyGuardTest.kt:138` 钉的 `!routeVisible(frequency.value, route.value)` 字面量原样保留。
- **历史门禁基线**：上一批最后一次完整门禁 = 2026-10-13 09:28（`E:\ASHKB\batch2-gate2.log`：`BUILD SUCCESSFUL in 2m / 42 actionable tasks: 17 executed, 25 up-to-date`）；同批 `batch1-gate2.log`（08:10，2m45s）、`adj3-gate2.log`（00:14，1m25s）、`detekt-fix.log`（08:07，13s，只跑 `compileDebugUnitTestKotlin`，无 lint）均 SUCCESSFUL。**09:30 → 21:07 那约 12 小时改动的门禁缺口，已由本次重跑补上**（含 21:06–21:07 的运动判读反馈文案与 `ExerciseEngineTest.kt`）。
- ⚠️ **构建必须在沙箱外运行**：workspace-write 沙箱下 Gradle 无法初始化 native services（`Failed to load native library 'native-platform.dll'`，因 `%USERPROFILE%\.gradle\native\…\.dll.lock` 被拒写）。命令：`. E:\ASHKB\build-env\env.ps1; & "E:\ASHKB\build-env\gradle-8.7\bin\gradle.bat" -p E:\ASHKB\patient-health-app <tasks> --console=plain`，且**不要覆盖 `GRADLE_USER_HOME`**（AGP 8.5.2 / Robolectric 4.13 缓存与 `%USERPROFILE%\.m2` 的 android-all-instrumented(API 34) 都在默认位置，覆盖会失去离线能力）。

#### 六、⚠️ 未验证清单（本批）

1. **未装机、未构建 APK、未推送、未发布**；维护者指示「未经明确指示不推送、不发版」。版本号仍是 `1.2.9 / versionCode 121`。
2. **门禁缺口已补**（见上），但依据仍只有门禁 + 单测，无真机证据。
3. 真机看点：本地备份选文件的 64 MiB 上限提示；档案导出 / 导入新增 7 字段往返（尤其 `spine_mobility`）；用药史弹层 90 天窗 + ≤60 行截断文案；急诊事件草稿各字段都有输入格；运动判读反馈文案已无 `exc-010` / `L1`；**停药弹层里不应出现「顺延」**；顺延注射后台账 `reason` 存的是 `postpone`。
4. 存量数据：药单以枚举 name 存库，**历史 `"postpone"` 行、旧分类行**都靠 `fromKey` 兼底，升级后不崩但仍是旧值（与 A3 节第 4 条同类）。
5. 接手时注意：`session-43a23810-8482-4dfa-ac2e-3c11cbbddf19`（13.58 MB / 216 轮）**也在 `E:\ASHKB\patient-health-app` 上推进另一条「四批次工程改造」goal**，动手前先确认主线，避免两个会话互相覆盖。
### 批次 C（未发布 · 未入 CHANGELOG 之外 · 未 bump 版本号）：按需用药常驻首页 + 每天只记一次

**维护者指示**（m00936，逐字）：「按需用药可否直接永久在主页上显示，但是限制只能每天一次。」

**缺陷**（两个半边，缺一不可）：

1. **读侧**：`data/repo/MedicationRepository.kt` 的 `buildTodayItems` 给按需卡**恒传 `log = null`** → `TodayItem.done` 永远为 false → 卡片永远显示「记录使用」按钮、永远没有「已服 HH:mm」。
2. **写侧**：`checkIn` 定位旧行靠 `MedicationLogDao.find(..., slot_key = :slotKey)`，而按需行的 `slot_key` 是 NULL——SQL 里 `NULL = NULL` 不为真 → 守卫永远匹配不到 → **每点一次插一行**（旧注释把这条写成了「按需用药可多次记录」的设计意图，实际后果是"点几次落几行，而卡上什么都看不出来"）。

「常驻首页」这半本来就成立（`buildTodayItems` 对每条在用按需药每天发一张卡），缺的是**已记录状态**与**每天一次的守卫**。这与 v1.1.1（HIGH-1）修掉的**补剂打卡**缺陷是同一个根因，当时只修了补剂一侧。

**处置**：

| 文件 | 改动 |
| --- | --- |
| `data/db/Daos.kt` | `MedicationLogDao` 新增 `findPrn(medId, date)`：`... AND slot_key IS NULL ORDER BY recorded_at DESC LIMIT 1`——**必须写 `IS NULL`**（`= NULL` 恒不成立）；取 `recorded_at` 最新那条，与今日卡展示同源 |
| `data/repo/MedicationRepository.kt` | ① `checkIn` 定位旧行改为 `if (slotKey != null) find(...) else findPrn(...)`——按需药找到当天那条就**更新它**，不再新增行；② `buildTodayItems` 把当天按需记录（`recordedAt` 最大者）挂到卡上，`item.done` 由此成立 |
| `ui/today/TodayViewModel.kt` | `checkIn` 显式传 `date = todayDate.value`（与 `skip` 的 L6 同口径）——跨零点时按需药记到页面显示的那一天；「每天一次」按同一天判定 |
| `ui/today/TodayScreen.kt` | 按需卡文案随状态分叉：未记 → `med_prn_note`；已记 → `med_prn_done_note`。按钮仍由 `!item.done` 控制 |
| `res/values/strings.xml` + `values-en/strings.xml` | 各补 `med_prn_done_note`（「今天已记录，明天可再记一次」/「Logged for today — you can log again tomorrow」），插在 `med_prn_note` 之后保持中英顺序一致 |
| `test/data/repo/PrnDailyCapTest.kt`（新增） | 5 个用例（见下） |

**口径边界**（刻意不动）：上限是「**每天一次**」不是「一共一次」（次日可再记）；**不改写存量数据**（旧版本历史上落的同日多行原样保留，卡与写入都取最新那条）；依从率口径不变（`AdherenceCalc.prnCount` 单独报数、`prnFlag` 仍排除在完成度分母外）；提醒链不变（`ScheduleCalc.slotsFor` 对 PRN 返回空）。

**回归测试**（新增 `test/data/repo/PrnDailyCapTest.kt`，Robolectric + 真库）：

1. 同一天连点三次「记录使用」只留一行（并锁 `prn_flag` / `slot_key IS NULL` / `status = done`）；
2. 上限是每天一次——**次日可以再记**，两条日志主键不同；
3. 今日卡挂上当天记录后 `done` 成立、仍认得出 `isPrn`、且 `isSettled`（不进待办计数）；
4. 存量同一天多行时，卡与写入**都取 `recorded_at` 最新那条**（不新增第三行）；
5. `find(..., null)` 查不到按需行、`findPrn` 查得到——把 `NULL = NULL` 这个根因钉死在测试里。

**门禁**：`cleanTestDebugUnitTest testDebugUnitTest :app:detekt :app:lintDebug verifySpecSync` → **BUILD SUCCESSFUL in 2m 48s**（`E:\ASHKB\prn-gate1.log`，42 actionable tasks: 12 executed, 30 up-to-date）。单测 **970 条 / 111 文件全绿**（failures=0 / errors=0）；detekt **0 条**；lint **0 error**（184 条全为 Information/Warning）；`verifySpecSync` 通过。

**真机验证（2026-10-17，Xiaomi 2410DPN6CC / Android，无线调试，v1.2.9+批次 B/C 的 release APK 覆盖安装）**：

制品：`app/build/outputs/apk/release/app-release.apk`（5,248,644 B，2026-10-16 21:02:42），`adb install -r` → `Success`，装后 `versionCode=121 / versionName=1.2.9`、`lastUpdateTime=2026-10-16 21:03:47`；与手机上旧包的证书 SHA-256 一致（`38CA80A6…012D7D`，即 `ashkb-release.jks`），属原地升级。**已核对 APK 比批次 C 全部源码新**（`Daos.kt` / `MedicationRepository.kt` / `TodayViewModel.kt` / `TodayScreen.kt` / 两个 `strings.xml` 均为 10-15 21:2x），故该包确实含本批改动。

走查方式：`uiautomator dump` 逐屏读 XML + 截图，A/B 对比点按前后的同一块卡片（不是只看一眼）。

| 真机看点 | 结果 |
| --- | --- |
| ①按需卡常驻首页（新建一支口服 · 按需 · 400mg · 通用名键 `ibuprofen` · 按需原因 `backup`） | ✅ 首页出现 `ibuprofen 400mg` / `按需 · backup` / `计划 按需` / `按需药不设提醒，记录实际使用即可` + 「记录使用」按钮 |
| ②点一次「记录使用」→ 变已服态、按钮消失、文案变「今天已记录，明天可再记一次」 | ✅ 同一卡片区域 A/B 对比：`计划 按需` → `已服 21:06`；`按需药不设提醒…` → `今天已记录，明天可再记一次`；「记录使用」按钮（`[120,1734][527,1914]`）消失 |
| ③同日再无入口可再记 | ✅ 已服态下卡片内**没有任何可点节点**（可点区域只剩底部导航与「今日补剂 · 管理」），按需卡上不再有「记录使用」入口 |
| ④药单详情里该记录可见、未被改写 | ✅ 我的 → 药单管理 → 该药详情：`按需用药 · 近 90 天记录 1 次`，列表一行 `2026-10-17 / 已服 / 按需`（`MedHistory` 90 天窗与按需标记都按预期） |
| ⑤次日恢复可记 | ✅ **2026-10-18 补验通过**。设备无 root（`date` 设置被拒、`setprop` 失败），改用 `service call alarm 3 s16 Pacific/Kiritimati` 把内核时区前推到 10-18（**不是**改 App 时钟，`DateProvider.refreshIfStale()` 读到的是真实的新系统日期）；重启 App 后首页变 `10月18日 星期日`，按需卡回到未记录态（`计划 按需` + 「记录使用」），点一次后变 `已服 03:50` + `今天已记录，明天可再记一次`。⇒ **「每天一次」是每天一次，不是一共一次**。验完立刻把设备时区恢复为 `Asia/Hong_Kong` |
| ⑥存量同日多行未被改写 | ⬜ **仍未验**（设备上没有旧版本遗留的多行按需记录；单测用例 4 覆盖）。设备上两天各 1 行共存已验：药单详情 `按需用药 · 近 90 天记录 2 次`，两行 `2026-10-18 / 已服 / 按需`、`2026-10-17 / 已服 / 按需` |
| ⑦（顺带）停用弹层不含「顺延」 | ✅ 批次 B 的 §6.7 顺带真机确认：停用原因弹层正好 **9 个**单选项（医嘱计划停 / 医嘱调整 / 自行停药 / 副作用 / 检查结果调整 / 感染发热 / 准备手术 / 经济原因 / 其他），**没有「顺延」** ⇒ `StopReason.POSTPONE` 的 `hidden` + `pickable()` 生效 |

**⚠️ 未验证清单（本批）**：

1. **未推送、未发布**；版本号仍是 `1.2.9 / versionCode 121`。（**已构建 release APK 并装机**——见上表。）
2. 真机看点 ①②③④⑤⑦ 已验（见上表）；**只有 ⑥存量同日多行未被改写仍未验**（设备上没有旧版本遗留的多行按需记录），依据是单测用例 4。
3. 走查期间无线调试三度掉线（手机 Wi-Fi 断/重连导致 `adb` 目标消失），⑤的补验因此拖到 10-18 才做（用 `service call alarm 3 s16 <zone>` 前推内核时区，验完已恢复 `Asia/Hong_Kong`）。
4. 走查期间在真机上建过一支测试药 `ibuprofen`，**已连同其 2 条打卡记录删除**，首页与药单已回到走查前状态（设备自带数据「依那西普 25mg / 停用于 2026-10-08 · 原因：经济原因」与维生素 D3 记录均未触碰）。

---

### 批次 B–E（UI 2.0「换形 / 换件 / 换图」+ 启动图标底色，未发布 · 未 bump 版本号）：门禁全绿，已回填 CHANGELOG

**维护者指令（m02212）：「UI 2.0 批次 B–E（换形/换件/换图 + 启动图标底色）——ui/theme 工作树零改动，完全没开始；release-bodies/v1.2.9.md:143 也写着「批次 B–E 仍待定」。先做这个UI的。」**

设计源：`ashkb-ui2-handoff.md`（369 行交接包）+ `ashkb-ui2-clarity.html`（1443 行设计稿）。**无库结构变更、无版本号变更**（仍 `versionCode 121 / versionName "1.2.9"`），可覆盖安装。

**批次 B「换形」**：`Shape.kt` 四档圆角收敛（small 8 / medium 12 / large 16 / extraLarge 24）⇒ 卡片 16、按钮·输入框·行 12、弹层 24；`Dimens.kt` `dockCorner 20→22`，新增 `Spacing.huge 40`、`Size.iconTile 42`；`Type.kt` `DataHero 36→40`、`DataLarge 28→30`、`headlineLarge 28→26`，新增 `Overline`（12sp/16sp/Medium/0.12sp 字距，按 CJK 重新推导，不入 `Typography`）。

**批次 C「换件」**（九项全部落地）：

1. `SectionCard` 描边化（`shapes.large` + 1dp `outlineVariant`，保持 `surfaceContainerLowest`，**无阴影**）；
2. `NavRow` 42dp 图标瓦片（`primaryContainer` 底 + 22dp `primary` 图标）；
3. `ScreenTopBar` 去底色（`containerColor` / `scrolledContainerColor` 均 `background`）；
4. 标签页拆分：新增 `SegmentedTabs` 与 `FilterChips`，**旧 `TabChips` 已删除**（`ReportScreen` 用前者、`KnowledgeScreen` 用后者）；
5. `StatusChip` 前导矢量图标 → 6dp 状态点（`Size.statusDot`），其余样式不变；
6. `AlertBanner` KDoc 追加「⚠️ 容器永不透明、永不毛玻璃」（结构零改动）；
7. Dock：选中胶囊 → `primaryContainer`，**未选中图标补 `onSurfaceVariant`**（原来两态同色）；**未引入 `Modifier.shadow`**，继续 `drawBehind` 淡描边；
8. `EmptyState` 56dp `primaryContainer` 圆托底；`LoadingBlock` 转圈 → 骨架屏（签名不变）；
9. **`AshkbTextField` + 全仓 99 处调用点迁移**——`ui/components/Forms.kt` 新增「凹陷填充」输入框（常态 `surfaceVariant`、聚焦升 `surface` + 1.5dp `primary` 描边、12dp 圆角、label 常驻顶部、值 15sp `bodyMedium`），`OutlinedTextField` 的 99 处调用 + 18 处 import 全部替换（含 `TodayScreen.kt` 两处全限定调用）。**两处有意偏离交接包**：圆角取 12（设计稿 §4.2/§5.9 两处都写 12）、label 取 12sp（设计稿 §4.4 label 梯度 15/13/12 + `Type.kt`「12sp 是硬下限」，交接包的 11.5sp 与之冲突）。

**批次 D「换图」**：

- `TrendChart`：2dp 圆头折线、面积渐变 16%→12%、**网格虚线→实线 hairline**、**阈值线 `warning→danger`** 并移入左侧轴槽、端点**白环**、读数气泡**深色实心**（`#084F73` + 白字）；顺带把 `tickLabel`/`fmtValue` 改为 `String.format(Locale.US, …)` 消除 locale 小数分隔符差异。轴标签**未**降到 9.5sp 例外（12sp 是全 App 硬下限）。
- `StatTile`（`DataDisplay.kt`）：新增 `delta` + `deltaTone`，补齐设计稿 §5.8 的**增量三件套**（label 12sp + 数据 `DataMedium` + 增量文字）；**方向着色由调用点按医学语义传入**，组件不猜涨跌。
- `MedCheckCard` 重构为 **DoseCard 时间轨**：52dp 恒宽右对齐时刻列（`Size.doseRailWidth` + 新增 `TimeRail` 13sp Bold tnum）+ 2dp 脊柱线（`Row(Modifier.height(IntrinsicSize.Min))` + `fillMaxHeight()`，复用 `AlertBanner` 唯一先例）+ 8dp 状态点（`Size.doseDot`；已服 success / 待服 primary / 漏服 danger，跳过 `onSurfaceVariant`、部分完成 `warning` 为设计稿未命名状态的显式补充）。**v1.2.10 批次 C 的按需「每天只记一次」行为未回归**。
- `SmallTrendChart` 对齐同一规范（阈值 `warning→danger`、渐变 18%→12%、主线 1.5dp→2dp、末点白环）；**「横轴由共享窗口决定」的约定与其纯函数一字未动**。

**批次 E 启动图标底色**：`res/drawable/ic_launcher_background.xml` 旧钢青 `#416476` → 品牌梯度 `linear-gradient(135deg, #06283A 0%, #084F73 55%, #0B6E9E 100%)`（与设计稿 Hero 同源），`aapt:attr` 实现；自适应图标安全区与前景脊柱未动。

**§06 双主题验收（2026-10-18）—— 照出一个批次 A 遗留真缺陷并已修复**：

按验收要求对五个 L1 页（今日 / 健康 / 报表 / 知识 / 我的）逐页取浅色 + 深色截图，与设计稿 §06 比对，证据留档 `E:\ASHKB\ui2-shots\ui20\{light,dark}\01-today … 05-me`（png + `uiautomator` dump）。

**深色一帧当场暴露**：卡片正确变深，但**每张卡周围都是白边**，页面底色仍是 `#FAFAFA`。

| | |
| --- | --- |
| 症状 | 深色页面底 `#FAFAFA`（浅），卡片是深色 ⇒ 每张卡一圈白边 |
| 根因 1 | `ui/AppShell.kt` 的 `Scaffold(containerColor = Color.Transparent)`（v1.1.6 为消手势区横带而定）⇒ 页面底**由窗口背景兜底**；`Theme.Ashkb` 的 parent 是 `android:Theme.Material.Light.NoActionBar`，其 `android:windowBackground` = `background_material_light` = `#FAFAFA` |
| 根因 2 | 全仓 `ui/` 下只有 `ReminderFullScreenActivity` / `DisclaimerScreen` / `ScreenTopBar` 三处绘制 `colorScheme.background`，**没有任何 L1 页绘制它** ⇒ 批次 A「换色」把 `backgroundLight/Dark` 改成瓷白 `#F3F6F8` / 深墨 `#0C1218` **从未真正生效**（浅色下 `#FAFAFA` 与瓷白极接近，肉眼看不出来） |
| 根因 3 | `values-night/themes.xml` 是「深色主题挂浅色 parent」，只靠 `windowLightStatusBar=false` 打了个单点补丁 |

**修法**（零结构改动；刻意不碰交接包 §5「明确不改」的 `ui/theme/Theme.kt`，也不动 `AppShell.kt` 的 L2/L3 early return 结构）：

1. 新增 `res/values/colors.xml`（`ashkb_window_background` = `#FFF3F6F8`）与 `res/values-night/colors.xml`（`#FF0C1218`），与 `Color.kt` 的 `backgroundLight` / `backgroundDark` 同值；
2. 两个 `themes.xml` 都加 `android:windowBackground` → `@color/ashkb_window_background`，并补 `android:windowLightNavigationBar`（三键导航机型在深色下原本会拿到深色图标，压在深底上不可见）；
3. `values-night/themes.xml` parent 由 `android:Theme.Material.Light.NoActionBar` → `android:Theme.Material.NoActionBar`。

**防漂移测试**：同一组颜色现在有 Kotlin 令牌 + XML 资源**两份副本**，故新增 `app/src/test/java/com/ashkb/app/ui/theme/WindowBackgroundParityTest.kt`（4 条）：浅/深两份 XML 色值与 `Color.kt` 令牌逐字节相等、两个主题都引用同一个 `@color`、深色主题的 parent 不含 `Light`。这 4 条正是单测从 970 → **974** 的增量。

**真机复核**（Xiaomi 2410DPN6CC / Android 16 / 1440×3200，无线调试）：重新 `:app:assembleRelease` → `app-release.apk` **5,212,848 B @ 2026-10-18 11:59:43** → `adb install -r` Success → `cmd uimode night yes`。深色页面底实测 **`#0D1217`**（≈ `backgroundDark` `#0C1218`）、浅色 **`#F4F6F8`**（≈ `backgroundLight` `#F3F6F8`），白边消失；五个 L1 页浅/深各 5 张全部重拍并覆盖留档。**顺带修正**：浅色页面底由 Material 默认 `#FAFAFA` 变为设计稿要求的冷调瓷白 `#F3F6F8`——批次 A 本来就想改这个值。

**门禁**：深色修复后重跑 `cleanTestDebugUnitTest testDebugUnitTest :app:detekt :app:lintDebug verifySpecSync` → **BUILD SUCCESSFUL in 2m 24s**（`E:\ASHKB\ui2-gate2.log`，42 actionable tasks: 12 executed, 30 up-to-date）。单测 **974 条 / 112 文件全绿**；detekt **0 条**；lint **0 error**（184 条全为 Information/Warning）；`verifySpecSync` 通过。

**⚠️ 未验证清单（本批）**：

1. **未推送、未发版**；版本号仍是 `1.2.9 / versionCode 121`（**未 bump**）。release APK 已构建并装机用于验收（5,212,848 B @ 2026-10-18 11:59:43）。
2. 五个 L1 页双主题截图**已拍、已留档、已比对**，但**是否达到发版标准需维护者本人过目**。
3. 批次 D 图表的动态交互（气泡选中、端点白环在高密度屏的观感）只做静态截图核验，未逐点走查。
4. §06 之外的设计稿分节（§5.5–§5.7 等本批未触及的组件）未逐帧比对。
5. 本轮新暴露的 detekt 问题（`AshkbTextField` 的 `CyclomaticComplexMethod` 19>15，以及 3 条随字段改名产生的 `MaxLineLength`）**已全部改代码修掉，未进基线**——做法是把三组状态 `when` 抽成 `private fun fieldVisuals(...)` 返回 `FieldVisuals`，行为零变化。

---

### 真机崩溃修复（2026-10-18，release 包间歇启动闪退 · 未发布 · 未 bump 版本号）

**现象**：release（R8 混淆）包冷启动偶发闪退，首页顶部浮出「上次异常：…」遮罩（`AppShell.kt:168-176` 读 `filesDir/last_crash.txt`）：

```
java.lang.NullPointerException: Attempt to invoke virtual method
'java.lang.Class java.lang.Object.getClass()' on a null object reference
	at W1.I.u(Unknown Source:353)
	at e2.a.s(Unknown Source:7)
	at x2.D.run(Unknown Source:112)
	at C2.i.run(Unknown Source:34)
	at E2.j.run(Unknown Source:2)
	at E2.a.run(Unknown Source:91)
	Suppressed: C2.g: [m0{Cancelling}@6b25eb, Dispatchers.Main.immediate]
```

首现 2026-10-18 12:30:52（PID 5331），制品 `versionCode=121 / versionName=1.2.9`（12:00:16 安装）。

**定位（mapping + dex 双证，可复用）**：

| 步 | 做法 | 结论 |
| --- | --- | --- |
| 1 | `Select-String 'W1\.I' app/build/outputs/mapping/release/mapping.txt` | 唯一命中 `:358303 com.ashkb.app.ui.today.TodayViewModel$refreshYesterdayPending$2 -> W1.I:`；`:358392` 有 `353:356:java.lang.Object invokeSuspend(java.lang.Object):255:255 -> u` |
| 2 | 同文件 `TodayViewModel -> W1.U:` 块 | `MutableStateFlow _yesterdayPending -> o`（`residualsignature LA2/Z;`，`A2.Z` = `StateFlowImpl`） |
| 3 | 从 `app-release.apk` 解出 `classes.dex` → `dexdump.exe -d` | `LW1/I.u`：`0x30` 先 `LW1/U;.e:(LW1/U;)Ljava/time/LocalDate;` 取 `date`（v5 = `this$0`），`0x6b` 才 `iget-object v5, v5, LW1/U;.o:LA2/Z;` 换成 `_yesterdayPending`，至 `0x161` 空检查前未被覆写 |

⇒ NPE 接收者 = `_yesterdayPending` 为 **null**，崩溃点 = `ui/today/TodayViewModel.kt:255` 的 `_yesterdayPending.value = PendingDoses.unsettledOn(…)`。

> ⚠️ **取证溯源（复现须知，2026-10-18 独立复核者提出）**：上表第 1 步引用的 `mapping.txt` 是**构建产物、不入 git**，且**已被 14:58 含修复的构建覆盖**——同一帧现在映射为 `:266:266`（`6:47 → :260`），旧的 `:255:255` 已无法从盘上复现。**复现请以崩溃 APK 本体为准**：副本 `C:\Users\weant\AppData\Local\Temp\ashkb-dex\app.zip`（5,212,848 B，mtime 11:59:43，SHA-256 `0DBEB9D06498E41B2F6F022460E955033228E4872003AB7B06C4E44CBF4550D3`），与修复后的 `app-release.apk`（SHA-256 `F64E90D4768EAFB49EDA6011667C57F8960F2829822BBC825FCE7152B758E388`，14:58:37）**不同一份**。下表第 3 步（`dexdump` 逐指令）与「独立对抗性复核」一节的全部结论都只依赖这份崩溃 dex，不依赖已丢失的旧 mapping。

**独立对抗性复核（2026-10-18，fresh 队友 `crash-analyst` 对着崩溃 APK 的 dex 反汇编，未改文件、未跑 Gradle）**：

| 复核项 | 结论 | 关键证据 |
| --- | --- | --- |
| 协程同步内联语义 | **成立** | `viewModelScope` = `CloseableCoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`；主线程上 `isDispatchNeeded()==false` ⇒ `launch`（默认 `CoroutineStart.DEFAULT`）**就地执行**到第一个真挂起点（`withContext(Dispatchers.IO)`）后返回。崩溃 dex：`W1/U.<init>` 于 pc `0x0036` 调 `Lx2/y;.r(...)`（launch），而 `_yesterdayPending`（字段 `o`）直到 pc `0x0120` 才 `iput-object v0, v0, LW1/U;.o:LA2/Z` —— 中间约 234 字节构造工作（5 条 `stateIn` 链 + DAO/DB 初始化），IO 线程有整段窗口先跑到 `_yesterdayPending.value =` |
| `getClass()` 形态 NPE 的来源 | **接收者已钉死；R8 具体 pass 不确定** | 崩点是一条**返回值被丢弃**的 `Object.getClass()`：`0x0161: invoke-virtual {v5}, Ljava/lang/Object;.getClass`，即 R8 为 `_yesterdayPending.value = …` 生成的**接收者空检查**；紧随 `0x0165: invoke-virtual {v5, v1(=null), v0}, LA2/Z;.l:(...)Z` 即内联后的 `StateFlowImpl.setValue → updateState`（对应 mapping `357:359:setValue:316 -> u`）。`v5` 由 `0x006b` 的 `iget-object v5, v5, LW1/U;.o:LA2/Z` 载入后至崩点前无任何写入。**Kotlin `!!` / `checkNotNull` / `requireNotNull` 只会编成 `if-eqz` + `throw NullPointerException(<文案>)`，不会出现 `Object.getClass()`** ⇒ 不能按「某处 `!!` 命中 null」收口。唯一未闭合点：R8 为何选 getClass 形态，不影响结论 |
| 修复是否充分 | **就本 NPE 充分** | 字段上移到 `init` 之前后，构造器在调 `launch` 前已完成 `o` 赋值 ⇒ IO 线程不可能读到 null；`refreshYesterdayPending()` 另只读 `date`(→`dateProvider`)、`repo`、`app`，全是构造参数或 init 前已赋值字段。**重入亦安全**：日期回调由 `DateProvider.onDateKnown` 在**主线程**触发（`start()` / 广播 / `refreshDateIfStale` 均主线程），无法与同在主线程的构造器交错，且回调体不依赖任何 init 后字段；`onCleared` 读的 `unregisterDateListener` 本就可空 |
| 同类形状复核 | **无其他未修点** | 复核者独立核过 `ui/backup/BackupViewModel.kt:72`（只写 `_davUrl:51` / `_davUser:52`，均在前）、`ui/exercise/ExerciseViewModel.kt:74`（`library` 在 `:71`，在 init 之前）、`ui/symptom/SymptomViewModel.kt:56`（只注册回调，回调读的 `_selectedDate` 在 `:47`）、`ui/DateProvider.kt:56`（只读 `scope.coroutineContext`） |
| 新旧归属 | **pre-existing，非本批引入** | `git log -S '_yesterdayPending'` → 字段由 `022892e v1.0.74` 引入；本批对 `TodayViewModel.kt` 的改动（KDoc、`Reaction.NONE.name`、`date = todayDate.value`）不触碰字段声明与 L249–261 函数体 ⇒ 与崩溃无因果 |

**根因**：`_yesterdayPending` / `yesterdayPending` 原先声明在 `init { … }` **之后**。Kotlin 按声明顺序初始化属性；`init` 里 `viewModelScope.launch { refreshYesterdayPending() }` 在 `Dispatchers.Main.immediate` 上**同步内联**到第一句 `withContext(Dispatchers.IO)` 就派发 IO 协程并返回，此时构造还没走到字段声明 ⇒ IO 线程读到 null。**该顺序自 v1.0.74 起一直如此，不是本批引入**：作者已在 `unregisterDateListener` 的 KDoc 写明「声明在 init 之前」，却漏了 `_yesterdayPending`（工作树与 HEAD `b1781c8` 同形，`git show 022892e` 亦同）。**为什么间歇**：屏关 / Doze 下 IO 派发被推迟，构造窗口拉长，命中率上升——复现实验里浅色 5 次、深色 5 次、20 次快速 force-stop 冷启动均 0 次，**只有「屏幕关闭状态下冷启动」8 轮中崩 1 次**。

**修法**：把 `private val _yesterdayPending = MutableStateFlow<List<PendingDoses.Pending>>(emptyList())` 与 `val yesterdayPending: StateFlow<List<PendingDoses.Pending>> = _yesterdayPending` 从 `:234`/`:237` 上移到 `init` 之前（现 `:66-80`），补 ⚠️ KDoc 记录崩溃与规则。**行为零变化**（该字段仍是同一实例、同一初值）。

**回归**：新增两个守卫测试，共 6 条断言。

1. `app/src/test/java/com/ashkb/app/ui/today/TodayViewModelInitOrderTest.kt`（3 条，逐点钉住 TodayViewModel）：① `_yesterdayPending` 不在 `init {` 之后；② `yesterdayPending` 不在 `init {` 之后；③ `init` 块与 `refreshYesterdayPending()` 不得引用任何「声明在 `init` 之后、缩进正好 4 空格」的类成员（自动扫描，防下次新增字段再踩）。
2. `app/src/test/java/com/ashkb/app/ui/ViewModelInitOrderGuardTest.kt`（3 条，**全仓扫描**）：① 遍历 `app/src/main` 下全部 `*ViewModel.kt`，凡有 `    init {` 的，其块内不得出现任何「声明在 init 之后、缩进正好 4 空格」的成员名（另含「扫到的 ViewModel 数 ≥ 10」「至少扫到一个带 init 的 ViewModel」两条自检，防止守卫因目录/标记假设失效而静默通过）；② 具名钉住今天全仓唯一带 `init` 的四个 ViewModel（`ui/backup/BackupViewModel.kt:72`、`ui/exercise/ExerciseViewModel.kt:74`、`ui/symptom/SymptomViewModel.kt:56`、`ui/today/TodayViewModel.kt:88`）仍在扫描范围内；③ `SymptomViewModel` 的 `private val _selectedDate` 仍声明在 `init` 之前。

> **全仓核过的结论**：12 个 ViewModel 里只有上述 4 个有 `init` 块。`BackupViewModel` 的 init 只写 `_davUrl` / `_davUser`（均声明在 init 之前）⇒ 安全；`ExerciseViewModel` 的 `private val library = MutableStateFlow<List<KbEntry>>(emptyList())` 在 `:71`，**在 init `:74` 之前** ⇒ 安全；`SymptomViewModel` 的 init 只**注册日期回调**（回调不是同步执行的，与 `viewModelScope.launch` 的同步内联有本质区别）⇒ 安全。⇒ **`TodayViewModel` 是唯一真受害者**，同类形状全仓已无第二处。

`readSource` / `repoRoot` 上溯找 `settings.gradle.kts` 的写法抄自 `ui/me/MedRouteFrequencyGuardTest.kt:102-111`。

> ⚠️ **教训（负向对照失败）**：想证明守卫会报错，临时在 `init` 之后插了一份重复声明——结果 `:app:compileDebugKotlin` **先失败**（`Overload resolution ambiguity` / `Unresolved reference 'value'`），Gradle 中止、测试根本没跑，而读到的 `TEST-*.xml` 是**上一轮**留下的全绿。⇒ 静态源码守卫不能用「插入重复声明」做负向对照（Kotlin 编译先拦）；要造能编译的违规形状（把声明挪到 init 之后并删掉重复）才能真正验证。**凡是 `--tests` 过滤运行，结论必须读 `TEST-*.xml`，不能只看 exit code。**

**门禁**：`E:\ASHKB\npe-gate2.log`（含全仓守卫）→ **BUILD SUCCESSFUL in 2m 48s**（42 tasks: 7 executed）。单测 **980 条 / 114 文件全绿**；detekt **0**；lint **0 error**（184 = 176 Warning + 8 Information）；`verifySpecSync` 通过。（修复当轮为 `E:\ASHKB\npe-gate1.log`：4m 7s，单测 977 / 113。）release APK 已用修复后代码重建：`app-release.apk` **5,212,848 B @ 2026-10-18 14:58:37**（**未装机复验**——间歇缺陷无法用有限次启动证明已消，只能靠代码级证据 + 守卫测试）。

> ⚠️ `app/build/reports/lint-results-debug.html` 本次是 UP-TO-DATE **未重写**（mtime 仍是 12:06:50）——判断某次门禁结果要看 `lint-results-debug.xml`，不要看这个 HTML 的 mtime。

---

### UI 2.0 §06 ④⑤ 收尾：报表「稳定期」结论胶囊 + 知识库类别徽章（2026-10-18 · 未发布 · 未 bump 版本号）

**背景**：§06 双主题验收比对设计稿还剩两块缺口——④ 报表页 BASDAI 走势卡缺设计稿要求的「稳定期」状态胶囊（当时只有 ≥4.0 的高危胶囊），⑤ 知识库条目的类别标签渲染成了卡片内左上角的胶囊，而设计稿要的是**行右侧的软底小徽章**。维护者裁决（m05521）两项都做。**复核后推翻了此前「这两块要先解除 `domain/`+`data/` 禁改」的判断**：所需数据（`KbEntry.category`、`BasdaiRecord.total`）与阈值常量（`ClinicalThresholds.BASDAI_HIGH` / `basdaiHigh`）**在现有模型里全都已经存在**，两块都只改 `ui/` 层。

| 项 | 设计稿 §06 要求 | 落地 | 证据 |
| --- | --- | --- | --- |
| ④「稳定期」胶囊 | 报表 BASDAI 走势卡标题行右侧一个 `st st-ok` 胶囊（`ok-soft` 底 + `ok-on` 字 + 前置圆点） | `SmallTrendChart` 新增末位可选形参 `trailing: (@Composable () -> Unit)? = null`，在标题行 `Row(…, verticalAlignment = Alignment.Bottom)` 的 `if (lastLabel != null)` 之后 `trailing?.invoke()`；`MiniSeries` 加 `badge: String? = null`（存**文案**不存资源 id），`MiniSeriesCell` 传 `trailing = { StatusChip(text = s.badge, tone = StatusTone.Success) }` | `ui/components/SmallTrendChart.kt:72`（形参）、`:130`（调用）；`ui/report/ReportScreen.kt` 的 `MiniSeries` / `MiniSeriesCell` |
| ④ 判据 | 走势给出「当前区间」结论 | `val basdaiStable = t.basdai.lastOrNull()?.let { !ClinicalThresholds.basdaiHigh(it.total) } == true` ⇒ 取**最近一次** BASDAI 与 `BASDAI_HIGH (4.0)` 比较；同时把 BASDAI 高危胶囊原来的裸字面量 `total >= 4.0` 收敛为 `ClinicalThresholds.basdaiHigh(...)`，两处同源 | 新增串 `report_basdai_stable`（`稳定期` / `Stable`），**插在 `report_basdai_section` 与 `report_basdai_trend` 之间**，中英同名同序 |
| ④ 为何不复用既有串 | —— | ⚠️ `stage_stable`（`缓解期` / `Remission`）是 profile 的**病情分期**字段，与「BASDAI 走势当前区间」不是同一概念；`dom_thr_adherence_good`（`达标`）是**用药依从率 ≥90%** 的判定 ⇒ 两者都不可复用，必须新增 | 全仓此前无「稳定期」串 |
| ⑤ 类别徽章位置 | 条目**行右侧**的软底小徽章（`.kb` flex 行 + `.tier` 尾部 `flex:none`） | `KnowledgeScreen` 新增 `@Composable private fun CategoryBadge(category: String)`；`KbListCard` 把类别胶囊**从 `FlowRow` 移出**，改为标题行 `Row(verticalAlignment = Alignment.CenterVertically)`：标题 `Text(…, Modifier.weight(1f))` + 徽章非权重排在其后 | `ui/knowledge/KnowledgeScreen.kt` 的 `CategoryBadge` 与 `KbListCard` |
| ⑤ 徽章形态 | `.tier{font-size:10px; font-weight:800; padding:3px 8px; border-radius:6px}` 三色（指南 brand-soft / 相互作用 warn-soft / 应急 bad-soft） | `Box(clip(shapes.extraSmall).background(tone.colors().first).padding(horizontal = Spacing.sm, vertical = Spacing.xxs))` + `labelSmall.copy(fontWeight = FontWeight.Bold)`；颜色一律走 `StatusTone.colors()`（`Tone.kt` KDoc 明令「禁止各屏幕自己写 `when (tone)`」）。**字号取 12sp 而非设计稿 10px**（`Type.kt` 12sp 是硬下限）；**刻意不复用 `StatusChip`**（它是 `CircleShape` + 28dp 下限的胶囊，挂标题行右侧会比标题还重、并顶起行高） | 设计稿三色与项目 `successContainerLight`/`onSuccessContainerLight` 等**逐字节同值** ⇒ 零新增颜色令牌 |
| ⑤ 保留项 | 设计稿要加类别徽章，不是替换证据等级 | 条目底部 `"${tierShortLabel(entry.sourceTier)} · ${entry.sourceName}"` 证据等级行**原样保留**；`FlowRow` 现只剩三枚状态胶囊（高风险 / 有备注 / 已过复核日） | —— |
| 附带还掉一处基线豁免 | —— | `categoryLabel` 原来 6 支 `when` 有 3 支挤在同一行（超 140 字符，是 `baseline.xml` 的一条 `MaxLineLength`），本次重排为**每支一行** ⇒ 该旧条目因文本形态改变而失配，且新行都远短于 140 ⇒ **不会被重新上报** | —— |

**回归**：新增 `app/src/test/java/com/ashkb/app/ui/Ui2ConclusionBadgesTest.kt`（6 条静态源码断言，沿用 `HealthHubVitalsPanelTest.kt` 的 `readSource` 上溯 `settings.gradle.kts` 写法）：① 标题行保留结论槽且 `trailing?.invoke()` 落在标题行内、权重标题列在其**之前**；② 「稳定期」只在 `!basdaiHigh(it.total)` 时挂上；③ 高危胶囊判据已收敛到 `basdaiHigh` 且**裸字面量 `total >= 4.0` 不得回潮**；④ 类别徽章在标题行右侧、标题 index < 徽章 index，且 `FlowRow` 内不得再有 `categoryLabel(entry.category)`；⑤ 徽章走 `shapes.extraSmall` + `labelSmall`+Bold + `categoryTone(category).colors()`，**徽章实现内不得出现 `StatusChip(`**；⑥ 证据等级行原样保留。

> ⚠️ **负向对照已做两轮（都是「能编译的违规形状」，即 `:799` 教训的正解）**：把 `CategoryBadge(entry.category)` 换成 `CategoryBadge("")` ⇒ 6 条里**正好 1 条 FAIL**、正是「行右侧徽章」那条；把 `trailing?.invoke()` 注释掉 ⇒ **正好 1 条 FAIL**、正是「trailing 必须真的渲染」那条。两轮其余 5 条仍 pass ⇒ 守卫钉住目标形状且无过宽误报，标记随后已核为零命中。

**detekt 基线记账（重要：这是「同步既存条目签名」，不是「新增豁免」）**：首轮门禁 `E:\ASHKB\ui2-45-gate1.log` 在 detekt 失败（BUILD FAILED in 21s）——`SmallTrendChart.kt:60:5` 报 `LongMethod (131 > 80)` 与 `CyclomaticComplexMethod (complexity 17 > 15)`。**这两条本来就是 `config/detekt/baseline.xml:31` / `:80` 的既存豁免**，只因加了 `trailing` 形参使**签名漂移**而失配、于是老问题重新上报（`trailing?.invoke()` 让复杂度 16→17）。按 `release-tooling/release-bodies/v1.2.7.md:86-92` 的既有先例（`KbDetailDialog` 加 `today: String` 后同步更新基线签名字串、**未新增任何豁免条目**），本次只把那两条 ID 的**签名字符串同步**加上 `trailing: (@Composable () -> Unit)? = null,`。对照改动前的备份 `E:\ASHKB\_baseline-before.xml`：**diff 恰为这 2 行**，基线 316 → 316 行、42502 → 42596 B、**零新增条目**（核对通过后该临时备份已删除）。同时把 `trailing` 的文档从「形参内嵌注释」改为规范 `@param`（KDoc 内嵌注释会**逐字进入基线 ID**，把文档写进基线不可接受）。

**门禁**：`E:\ASHKB\ui2-45-gate2.log` → **BUILD SUCCESSFUL in 3m 17s**（42 tasks: 15 executed, 27 up-to-date）。单测 **993 条 / 116 文件全绿**（`failures=0 errors=0 skipped=0`；987 → 993 的增量就是新增的 6 条）；detekt **0**；lint **0 error**（184 = 176 Warning + 8 Information）；`verifySpecSync` 通过。

**仍未做**：这两块的**真机观感**需维护者本人过目（设备侧无线调试未开，本轮未装机）；**未推送、未发版**；版本号仍 `versionCode 121 / versionName "1.2.9"`。release APK 已随本批重建：`app/build/outputs/apk/release/app-release.apk` **5,213,288 B @ 2026-10-18 22:17:53**，SHA-256 `4F0FC93B890F830FC93624D985762738BFCF531E5FB6E7B753634DC40CFFD8F8`（日志 `E:\ASHKB\ui2-45-apk1.log`，4m 8s）。

---

### 健康 Hub「今日体征」面板 + 紧急卡 danger-soft 瓦片（2026-10-18，UI 2.0 §06 ③ 收尾 · 未发布 · 未 bump 版本号）

**背景**：§06 双主题验收比对设计稿时，健康 Hub（③）还差两块——首屏没有「今日体征」面板，三个入口瓦片里**紧急卡没做 danger-soft**。维护者裁决（m05132）只做这两项、**不碰 `domain/` 与 `data/`**，其余缺口（报表「稳定期」胶囊、知识库信源等级徽章）暂缓。

| 项 | 设计稿 §06 ③ 要求 | 落地 | 证据 |
| --- | --- | --- | --- |
| 「今日体征」面板 | Hub 首屏新增四格（血压 / 心率 / 体温 / 体重），不点进二级页也能看到核心数值 | `ui/AppShell.kt` 的 `HealthHub` 在标题栏 item 之后插入 `item { VitalsPanel(…) }`；文件末新增 `@Composable private fun VitalsPanel(vitals: Vitals?, weight: WeightLog?, onOpen: () -> Unit)`，复用既有 `SectionCard` + `StatTile`（零新组件、零新令牌），**两列 × 两行**、格子顺序照设计稿 `grid-template-columns:1fr 1fr`（血压 / 心率 → 体温 / 体重） | `AppShell.kt:539-546`（调用点）、`:599` 起（`VitalsPanel`） |
| 四格判色 | 与体征二级页同一口径 | 抽成三个纯函数 `temperatureTone` / `bloodPressureTone` / `heartRateTone`，**逐支照抄** `WellnessScreen.VitalsHero`，全部引用 `domain/ClinicalThresholds` 具名常量（`FEVER_ALERT 38.5` / `FEVER_LOW 37.3` / `BP_HIGH_SYS 140` / `BP_HIGH_DIA 90` / `BP_LOW_SYS 90` / `HR_LOW 60` / `HR_HIGH 100`）⇒ UI 层无魔数；**未记录给 Neutral**（不染警示色） | 抽出纯函数的理由：三组 `when` 内联会让 `CyclomaticComplexMethod` 超阈值 15，与批次 C `AshkbTextField`（19>15）同一失败模式 |
| 未记录态 | —— | 没记的格显示「未记录」而**不隐藏**：面板的价值是让用户看出「哪项今天还没量」，留白反而看不出缺口；四格取数统一 `?: notRecorded` | 复用 `common_not_recorded` |
| 浮点渲染 | —— | `String.format(Locale.US, "%.1f", …)`（`FormatLocaleGuardTest` 只拦 `"…".format(` 隐式 locale，显式 Locale.US 才保证法语等区域不出现逗号小数点）；单位沿用项目既有硬编码惯例 `"℃"` / `"kg"`（同 `WellnessVitalsSections.kt` 的 `WeightCard`）；血压 `"$sys/$dia"` | —— |
| 紧急卡 danger-soft | 紧急卡用 danger-soft 瓦片提示其特殊地位 | `HealthHub` 的紧急卡 `NavRow` 加 `tone = StatusTone.Danger` ⇒ 走 `Tone.kt:23` 的 `dangerContainer to onDangerContainer`，**零新令牌**（`NavRow` 的 `tone` 参数本就是为这类「入口本身有语义」的场合而加） | `AppShell.kt:574` |
| 另两个入口 | —— | 营养与骨健康 / 复诊管理**保持默认 primaryContainer 配色**——语义色只留给紧急卡，否则「特殊地位」不成立（已加**反向断言**钉住） | —— |

**零新增字符串资源**：四格标题与「记录 / 编辑」按钮全部复用既有串（`vitals_today_title` / `vitals_temperature_short` / `vitals_bp` / `vitals_heart_rate` / `vitals_today_weight` / `vitals_bpm_unit` / `common_not_recorded` / `common_edit` / `common_record`）⇒ `EnglishStringsParityTest` / `SeedStringsParityTest` 天然不受影响；也未改任何 XML ⇒ `WindowBackgroundParityTest` / `MigrationTableParityTest` 不受影响。

**回归**：新增 `app/src/test/java/com/ashkb/app/ui/HealthHubVitalsPanelTest.kt`（7 条静态源码断言，`readSource` 上溯 `settings.gradle.kts` 的写法抄自 `ui/me/MedRouteFrequencyGuardTest.kt:102-111`）：① `HealthHub` 里真的调用了 `VitalsPanel` 且标题用 `vitals_today_title`；② 四格标题资源都在；③ 四格取数都有 `?: notRecorded` 回退；④ 判色引用全部 7 个 `ClinicalThresholds` 具名常量且含 `StatusTone.Neutral`；⑤ 紧急卡 `NavRow` 含 `tone = StatusTone.Danger`；⑥ **反向断言**另两个入口不含 `tone =`；⑦ 体温/体重走 `String.format(Locale.US, "%.1f", it)`。

> ⚠️ **负向对照已做（这次做对了，正是上一节教训的正解）**：按 `:799` 记下的教训——静态守卫不能用「插入重复声明」做负向对照（Kotlin 编译会先拦），要造**能编译的违规形状**。做法是临时删掉紧急卡那行 `tone = StatusTone.Danger`（可编译），过滤跑 `--tests HealthHubVitalsPanelTest` ⇒ **7 条里正好 1 条 FAIL、就是「紧急卡是 danger-soft 瓦片」那条**（结果文件 tests=7 failures=1 errors=0），其余 6 条仍 pass ⇒ 守卫确实钉住了目标形状、且没有过宽误报。随后恢复该行（`NEGATIVE-CONTROL-TEMP` 标记已核为零命中）。

**门禁**：`E:\ASHKB\ui2-hub-gate3.log` → **BUILD SUCCESSFUL in 1m 56s**（42 tasks: 16 executed）。单测 **987 条 / 115 文件全绿**（`failures=0 errors=0 skipped=0`；980 → 987 的增量就是新增的 7 条）；detekt **0**；lint **0 error**（184 = 176 Warning + 8 Information）；`verifySpecSync` 通过。（首次落地后未加守卫那轮为 `E:\ASHKB\ui2-hub-gate1.log`：3m 46s，单测 980 / 114；加守卫后改布局前为 `E:\ASHKB\ui2-hub-gate2.log`：1m 43s，单测 987 / 115。）

> ⚠️ 同上节：判断门禁结果看 `lint-results-debug.xml`，不要看 `lint-results-debug.html` 的 mtime（该 HTML 本轮仍 UP-TO-DATE 未重写）。

**仍未做**：这两个缺口的**真机观感**需维护者本人过目（设备侧链路断，本轮未装机）；**未推送、未发版**；版本号仍 `versionCode 121 / versionName "1.2.9"`。release APK 当时为 **5,212,848 B @ 2026-10-18 17:50:49**、SHA-256 `32A844FA018BA4713A58F525A7C75E0E99DADD0BD123404FC765CEF93DE980DF`（`E:\ASHKB\ui2-hub-apk1.log`，1m 47s）；**其后 §06 ④⑤ 那批又重建过一次，当前盘上 APK 以 `:829` 的 5,213,288 B / `4F0FC93B…` 为准**。

---

### 裁决项 8 结项：审查报告 §九「无法从代码判定的事项」七条答复（2026-10-18）

**背景**：审查报告原件 `C:\Users\weant\Documents\Qoder\2026-09-28\a6159f90\ASHKB-代码审查报告.md:248-256`（维护者 m04098 提供），其 §九 列出 7 条「仅凭代码无法判定」的事项。此前文档里一直记「报告正文只有『见对话正文』，需要原件」；**原件已到手，七条全部给出取证结论，裁决项 8 据此销项。**

| # | 报告原文（§九） | 取证结论 | 证据 / 方法 |
| --- | --- | --- | --- |
| 1 | 目标设备（Xiaomi/HyperOS）上 MIUI appops 10014/10020/10021 的**实际**默认值与拦截时机；对齐唤醒/Doze 顺延策略；force-stop 后闹钟是否被清除 | `MIUIOP(10020)=allow`、`MIUIOP(10021)=allow`；**10014 在该机型上不存在**；应用已在电池优化白名单内；闹钟全部为 `RTC_WAKEUP`；**force-stop 后闹钟未被清除**（HyperOS 只杀进程） | 真机 `appops get com.ashkb.app` / `dumpsys deviceidle whitelist`（含 `user,com.ashkb.app,10490`）/ `dumpsys alarm` 过滤本应用 |
| 2 | 出厂 APK 是否真用正式 keystore 构建（CI 不验签，无从证明） | **CI 产物确为 debug 签名**：`.github/workflows/ci.yml:14-15` 显式设 `ASHKB_ALLOW_DEBUG_SIGNING=1`，且只打印证书不严格比对；本机 release 用 `ashkb-release.jks`，SHA-256 `38ca80a61760208e1f0c9d81c2caf5c7c90da0e3bd759150ccdf641ec9012d7d`（DN `CN=ASHKB, OU=Personal, O=weante, L=Shenzhen, ST=Guangdong, C=CN`）⇒ 已发布的正式版资产须以本地 `Get-FileHash` 与 `apksigner verify --print-certs` 为准，不能信 CI | 读 `.github/workflows/ci.yml`；`keytool`/`apksigner` 查本机 keystore |
| 3 | Android 目标 ROM 上 `HttpURLConnection` 跨主机重定向是否转发 `Authorization`（S-8 因此标记为待验证） | **跨主机重定向不转发 `Authorization`**：本机双 loopback 端口实验（8091 → 302 → 8092 回显请求头）证明 `HttpURLConnection` 默认 follow 重定向但**丢弃** `Authorization`；且 `WebDavClient.kt:68-83` 从未调用 `setInstanceFollowRedirects` ⇒ S-8 的「重定向后凭据丢失」成立（**同主机**重定向不受影响） | 宿主双端口 HTTP 回显实验（可复用；Android 侧 dex 因设备掉线未补跑，见下方未完成项） |
| 4 | Keystore 密钥是否硬件背书 / 是否可证明（无 attestation） | **无 attestation、无 StrongBox 请求**：`KeystoreCipher.kt` 只 `setBlockModes(GCM)` / `setKeySize(256)` / `setRandomizedEncryptionRequired`；而设备 `pm list features` 同时具备 `hardware_keystore=300` 与 `strongbox_keystore=300` ⇒ **硬件能力具备，但代码不可证明**（要证明须加 `setIsStrongBoxBacked(true)` + attestation 链校验） | 读 `KeystoreCipher.kt`；真机 `pm list features` |
| 5 | 120k 次 PBKDF2 在目标 SoC 上的真实耗时（影响是否敢继续提高迭代数） | **实测 136 ms**（SM8750 真机，120k 迭代）；200k = 226 ms、600k = 687 ms；宿主 x86 为 46 ms ⇒ **迭代数有充足余量可提高**，远未逼近 `BackupRepository.kt:193` 的 `withTimeout(120_000)` | 一次性 Java 基准 dex：`javac --release 17` → `d8.bat --lib <android.jar> --min-api 31` → push → `dalvikvm -cp /data/local/tmp/x/classes.dex <Main>`（⚠️ 用 `dalvikvm`，`app_process` 会崩） |
| 6 | `webdav_config` 历史明文凭据的迁移是否曾对真实用户跑过（缺文件） | **迁移代码存在但零测试覆盖**：`BackupRepository.kt:62-81` 从旧键 `url`/`user`/`pass` 迁移（**仅 https 才迁移**，随后删旧键）；全仓无任何测试触及该分支；release 包不可调试 ⇒ 真机 prefs 读不到 ⇒ **与报告「缺文件」的判定一致**，属于「有代码、无证据」 | 读 `BackupRepository.kt:62-81`；grep 测试目录无命中；真机 `run-as` 不可用（release） |
| 7 | 是否存在应用锁 / 生物解锁（代码中未找到） | **不存在**：全仓 grep `BiometricPrompt` / `KeyguardManager` / `setUserAuthenticationRequired` **零命中**；`SecureWindow.kt` 只用 `FLAG_SECURE` 禁截屏，**那是防泄露不是身份认证** ⇒ 报告判定成立 | 全仓 grep |

**未完成的两条尾巴**（设备侧链路断，非代码问题）：

1. **第 3 条的 Android 侧复核**：宿主实验已证明 `HttpURLConnection` 语义，但「目标 ROM 上是否同样」需在真机跑同一实验；dex 已备好 `E:\ASHKB\.tmp-soC\dex3\classes.dex`，**设备回来后补跑**。
2. **第 6 条的真机 prefs 取证**：release 包不可调试、设备亦掉线；须用 debug 包 + `run-as` 或 root 才能读到 `webdav_config` 历史键，**设备回来后补跑**。

> 上述 7 条中，1 / 4 / 5 是**真机实测**，2 / 3 / 6 / 7 是**代码 + 宿主实验**。第 3、6 条的真机侧补跑不改变结论方向（宿主实验与代码已足够定性），只影响「目标 ROM 是否与宿主一致」的强度。

---

### v1.2.10 发布记录（2026-10-19 · 已发布为 GitHub Latest）

**一次把六批积压改动合并发版**：审查第二/三批 + 裁决项 3、6、7 + §6.7、按需用药每天一次、UI 2.0 批次 B–E、§06 三块收尾（③ 体征面板 + danger-soft 瓦片、④ 稳定期胶囊、⑤ 类别徽章）、真机启动崩溃修复。**上线理由：线上 v1.2.9 带着那个间歇启动崩溃。**

| 项 | 事实 |
| --- | --- |
| 发布提交 | `45b0435c617cd946a8eb1cb7e64ec129ee04ba41`「v1.2.10: 合并发布积压的七批改动（含真机启动崩溃修复）」——**106 files / +7097 / −2044**；`f68350b..45b0435` 已推 `origin/main` |
| 版本 | `app/build.gradle.kts` `versionCode 122` / `versionName "1.2.10"`；`ASHKB_DB_VERSION` 仍 **19**（无库结构变更，可覆盖安装） |
| 门禁 | `E:\ASHKB\v1210-release-gate1.log` → **BUILD SUCCESSFUL in 2m 54s**；单测 **116 文件 / 993 条**（failures=0 errors=0 skipped=0）；detekt **0**；lint **184 = 0 Error** + 176 Warning + 8 Information；`verifySpecSync` 通过 |
| APK | `ashkb-1.2.10-release.apk` **5,213,292 B**；`aapt dump badging` = versionCode 122 / versionName 1.2.10；`apksigner verify --print-certs` = `CN=ASHKB, OU=Personal, O=weante, L=Shenzhen, ST=Guangdong, C=CN`，证书 SHA-256 `38ca80a6…d7d`（未回退 debug 签名）；`Get-FileHash -Algorithm SHA256` = `109909E04EE16320B7976E1AFC19D840FD10CD9BD040BDC54B2C18E8F1170186`，仓库根副本与 `app/build/outputs/apk/release/app-release.apk` **逐字节一致** |
| GitHub Release | release id **`416558911`**，**Latest**，非预发布、非 draft；`target_commitish` = `45b0435`；唯一资产 `ashkb-1.2.10-release.apk`（5,213,292 B，sha256 `109909e0…0186`，`state=uploaded`） |
| 标签 | 附注标签 `v1.2.10`（tag 对象 `0649fb2`）→ `45b0435`；远端 `refs/tags/v1.2.10` = `0649fb2`、`refs/tags/v1.2.10^{}` = `45b0435`（已核对，与 v1.2.8/v1.2.9 记账形态一致） |
| Release notes | `E:\ASHKB\release-tooling\release-bodies\v1.2.10.md`（本版正文，五节 + 测试 + 已知范围说明） |

⚠️ **本次踩到并修好的一个坑**：`gh release create --target <sha>` 建的是**轻量标签**（远端 `refs/tags/v1.2.10` 直接指向 commit），而历史版本全是**附注标签**（远端多一条 `^{}`）。已本地 `git tag -a v1.2.10 -m "…" 45b0435` 补建并 `push --force origin refs/tags/v1.2.10` 覆盖。**下次发版：先本地 `git tag -a`、`git push origin <tag>`，再 `gh release create --verify-tag` 或直接不带 `--target`。**

⚠️ **网络**：直连 `github.com` 超时（20s），但 `api.github.com` 直连 200；推送与 Release 全程走系统代理 `http://127.0.0.1:7897` + `--http1.1`。一次 `ls-remote` 报 `TLS connect error: unexpected eof while reading`，**重试即成功** ⇒ 代理链路偶发，失败先重试再判定。

**本版仍未做**（与上一节「UI 2.0 §06 ④⑤ 收尾」的记载一致）：§06 六帧的**真机观感**待维护者本人过目（设备侧无线调试未开）；裁决项 5（`domain/ClinicalThresholds.kt` 在子代理禁改清单）与审查报告 §十 三条澄清仍挂账。

---
