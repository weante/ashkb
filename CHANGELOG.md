# Changelog

ASHKB（Ankylosing Spondylitis Health Knowledge Base）版本变更记录。面向强直性脊柱炎患者的离线优先个人健康管理应用。

> ⚠️ **免责声明**：本应用为个人健康管理记录工具，不构成任何医疗建议，不能替代医生诊疗。用药与治疗方案请始终遵医嘱。

## [v1.0.87] — 2026-10-02

**批次 12 + 13：补剂「跳过」· `TodayScreen` 拆分 · 复诊可发现性 · 化验计数口径（含一个真 bug）。** 无库结构变更，可覆盖安装。

### 批次 12 · A：补剂打卡支持「跳过」

- 卡片未打卡时给**两个动作**：「打卡」与「跳过」（跳过**不弹表单、不填理由**，写 `AdherenceCalc.SKIPPED`）
- **历史流改为同时包含 done 与 skipped**：`SupplementLogDao.observeHistoryFor` 的 `status = 'done'` → `status IN (:statuses)`，过滤值直接引用 `AdherenceCalc.SETTLED_STATUSES`（**不再在 SQL 里抄第二份**）
- 详情胶囊新增 `domain/SupplementLogStatus`（`loggedToday` / `labelRes` / `tone`）作为唯一实现，与药品侧逐字同词；跳过行用新串「跳过记录于 %1$s」（跳过的 `takenAt` 为 null，沿用旧句会把跳过说成服用）
- **撤销**天然覆盖两种状态（原逻辑按 `supId` 过滤、不按状态过滤）
- **依从率与药品完全同口径**：跳过**进分母、不进分子、不算漏服**（`ratePct(done, partial, done+partial+skipped)`，与药品 `doseCompletion` 的 `SKIPPED -> Unit` 一致），**未改任何公式**
- **补剂根本不在漏服通知路径上**（`MissedDoseReminder` 只读计划槽位与用药记录）→「跳过不算漏服」天然成立
- **老数据回归锁三重**：纯算术把旧补剂分支原样跑一遍逐位比对 · 真库 SQL 断言条数/倒序不变 · 未知状态仍不进历史

### 批次 12 · B：`TodayScreen` 拆分（根级 Flow 8 → 3）

- 下移 `alerts` / `symptomRecorded` / `exerciseDone` / `minimalPrompt` / `yesterdayPending` 五条；消除派生值 `yesterdayDate`
- **留根级三条各有硬理由**：`profile`（三处共用 + 派生 `isMinimal`）· **`todayDate`**（Hero 标题 / 漏服判定 / 补记卡必须是**同一个「今天」**——批次 9 刚修过"两处各读系统时钟→跨零点差一天"，绝不允许各 section 各收一份）· `today`（药品列表内容，须由根 `LazyColumn` 亲自发出）
- **实测出的硬边界**：四个弹层 target **下不去**——`LazyListScope.item { }` 只在列表作用域解析得到，而 `remember`/`collectAsStateWithLifecycle` 只在组合上下文可用（标普通扩展报"必须加 @Composable"，标 `@Composable LazyListScope.` 又在 `LazyColumn` 内容 lambda 里报"调用点不是组合上下文"；两条绕法——嵌套 LazyColumn、整列表塞一个 item——都被否掉）。它们是 `remember` 槽位、**不是 Flow**
- 新增静态守卫 `TodayScreenStateScopeTest`（白名单 + 下移反查 + 根级 `mutableStateOf` 恰好 4 个 + 日期流全页只收一次 + 全文件只允许一个 `LazyColumn`）；**它只证明"代码里怎么写的"，不证明"运行时只重组那块"**，且只认 `collectAsStateWithLifecycle` 一种写法（已写进用例注释）

### 批次 13：三处"找得到东西 / 数字说得准"

1. **复诊「项目」行可点** → 跳到「记录」tab 并**按该项目筛选**（此前整行不可点，点「MRI」毫无反应）；筛选时顶部显示可清除的「仅显示：MRI ✕」，筛选状态 `rememberSaveable`
2. **「准备清单」可折叠**：不再把复诊记录卡挤出首屏（此前维护者因此以为"记录不存在"）
3. **「化验 100 条」的真 bug**：该摘要用的查询**上限就是 100**——`Daos.kt` 注释自己写着「库里行数 ≥ 窗口时它**恒等于窗口值（初值 100）**」→ 只要化验 ≥ 100 条就永远显示 100（维护者删掉 4 条后数字不变，正是这个原因）。新增真 `COUNT`（`HealthRepository.observeLabCount()` → `labResultDao.observeCount()`），`AppShell` 改用 `checkupVm.labTotal`

### 另修：一处自相矛盾的文案

`DestructiveAction` 新增 `confirmLabel` 参数（**默认仍是「删除」**，其它调用点行为不变）；复诊项目的停用确认改说「**停用**」——此前标题问「停用该项目？」而按钮写「删除」，用户会以为整条项目连同历史都被删掉。

### 测试与构建

- 单测 **691 → 728 条全绿**；`detekt` 通过（批次 12 过程中报出的 4 条新问题**全部改代码修掉**）；**`:app:lintDebug` 0 error**
- release / debug 均 versionCode **92 / 1.0.87**

### 未做 / 残余

- **本模块无 Compose UI 测试依赖**：`TodayScreen` 拆分与三处可发现性改动的**运行时行为无法用组合测试证明**，替代证据是静态守卫 + 编译证据；真机目视由维护者进行
- 补剂 `partial`（部分完成）**没有写入路径**（卡片只给已服/跳过，与药品一致）——若日后要加，`SupplementLogStatus` 与依从率已就位
- 老数据为**空**时补剂报表仍给 `0`（`ratePct`），而「记录内完成度」对空数据给 `null`——两者是不同指标，已在 `SupplementAdherenceParityTest` 里单列用例写明边界
## [v1.0.86] — 2026-10-02

**批次 11：冷启动/IO 小优化 6 项 + WebDAV 读取上限（D6）。** 无库结构变更，可覆盖安装。

### B 组：冷启动与 IO

- **B1 报表不再"白查库"**：`ReportViewModel` 原在 `init` 里就 `refresh()`，而 `AppShell` **应用启动即 eager 创建它** → 即使从不看报表，冷启动也查一次库。现删 `init`、新增 `loadOnce()`（判据 `overview == null && !busy`，并发双击也不会放两次查询），由报表页 `LaunchedEffect` 触发；**并把该 VM 下沉到报表路由内**
- **B2 AppShell 的 10 个 VM 下沉 6 个**（Today / Symptom / Exercise / Knowledge / Report / Backup）——判据三条全中才动：①只被一个路由使用 ②构造期有真实代价 ③下沉后创建时机仍在路由作用域内、返回栈语义不变。**保留 4 个**（Me / Wellness / Checkup / Emergency）：被多个路由共用（如 HealthHub 读其摘要副标题），下沉会分裂成多份实例；且构造期代价在 B3 之后已归零
- **B3 `DateProvider` 单例：7 份重复 ticker 收敛为 1，并真正修掉深睡坑**：原先各 VM 用协程 `delay` → Android 落 `Handler.postDelayed`，按 **uptimeMillis** 计时、**深睡不计时** → 夜里手机睡着时跨零点不触发，可能早上才补跑。现改为**订阅系统日期变更广播**（`ACTION_DATE_CHANGED` / `TIME_CHANGED` / `TIMEZONE_CHANGED`，API 33+ 带 `RECEIVER_NOT_EXPORTED`）——**系统在跨日那一刻主动送达**，而非"醒来后补偿"；`ON_RESUME` 的 `refreshIfStale()` 保留为第二道防线。**7 个 VM 逐一核对日期语义：全部是「今天」，无「窗口起点」，语义未变**；Today/Symptom 在 `onCleared` 反注册（日期源是进程级单例，否则会持有已销毁的 VM）
- **B4** `MedEditScreen` 的 `DrugKeyCatalog.suggest` 包 `remember`（key 取单一字符串——它已把"用哪个字段查"折进自身，两个输入任一变必然变 key；用字符串而非 Pair，避免每次重组新建 Pair 让 remember 失效）
- **B5** `loadPeriodic` 参数级去重：`loadedPeriodDays`（手上这份数据是哪段）与 `_periodDays`（用户现在选的）分开；同窗口跳过、**参数变了必重载**、**失败不推进缓存**（下次仍重试）；另加慢查询保护（快速连点 7→30 时不让旧窗口数据覆盖新窗口）
- **B6 报告此条不成立**：`AttachmentSheet` 该处**已有 key**（L236 `rows.forEach { a -> key(a.id) { … } }`，注释写明是 v1.0.43 的修复）；报告指的 L231 是空态 `Text`。**未改动**

### F 组：WebDAV 读取大小上限（D6）

- **上限**：二进制响应（GET 单文件）**6 MiB**；XML/文本（PROPFIND 列表）**1 MiB**。依据：DB 备份是"全库导出 + AES-256-GCM 的 JSON 文本"，正常几百 KB；附件实测区间 100 KB–5 MB（另有 20 MB 硬闸门）。6 MiB 覆盖"真实备份 + 真实大附件"并把"远端被换成超大文件"挡在读入内存之前。**说明**：本机无真机 `/data/data` 访问权，体积为**仓库内既有口径的量级估算**，代码注释已如实标注
- **绝不静默截断**：新增 `DavResponseTooLargeException(path, actualBytes, limitBytes)`，实读超限**立刻抛出**；判定完全基于**实读字节数**（不报长度、谎报长度都挡得住）
- **覆盖全部读路径**（`grep readBytes` 已 0 命中）：备份下载 / 附件懒下载 / 上传后回读校验（共用同一收口）· 附件目录列举 · 恢复列表 · 轮换列举——**其中轮换列举原有 `catch(Exception) { emptyList() }` 会把超限吞成"远端一份都没有"**，已改为显式重抛
- 错误文案走 `strings.xml`（无 BOM 断言 + 3 条锚点唯一匹配 + 同一编码写回；CRLF +6 只来自新增行，**LF-only 600 不变**）；附件懒下载返回类型 `Boolean` → `Int?`，UI 可区分"太大"与"云端没有"

### 测试与构建

- 单测 **678 → 691 条全绿**；`detekt` 中途报的 10 条新问题**全部改代码修掉而非进基线**；**`:app:lintDebug` 0 error**
- 新增 `WebDavBoundedReadTest` 8 条 · `DateProviderTest` 5 条
- release / debug 均 versionCode **91 / 1.0.86**

### 未做 / 残余风险

- **`DateProvider.start()` 的广播注册未写 Robolectric 测试**（只测纯 JVM 推进/通知语义）：注册路径需真机验证"跨零点收到 `ACTION_DATE_CHANGED`"，已在注释写明残余风险 + `ON_RESUME` 兜底
- **B5 取舍**：同窗口内数据变了不会自动重载（参数变了才重载）；报表页"刷新"按钮行为未变
- **F 组上限依据是量级估算而非真机实测**；若手上有实际备份体积建议核对一次 6 MiB
## [v1.0.85] — 2026-10-02

**批次 10：`BackupScreen` 拆分（第二批第 1 块）——根级 Flow 19 → 3。** 纯重构，无库结构变更、无行为改动，可覆盖安装。

### 为什么先做这个屏幕

第四轮性能审查报告 P1-6：附件同步期间 `attachSyncStage` / `attachSyncMsg` / `attachBytes` 三条 Flow **每秒发射多次**，而它们在**屏幕根部**收集 → **每个进度 tick 都重组整个 876 行屏幕**（含两个密码输入区）→ **输密码时被进度 tick 打断**。这是报告里**唯一用户可感知**的卡顿。

### 做了什么（唯一改动文件 `ui/backup/BackupScreen.kt`）

按 section 收口，各自在**内部**收集自己需要的 Flow：
`AttachSyncSection`（7 条，★主目标，内部再拆 Toggle / Stats / Actions / Status 四个纯值子块）· `RecoveryCodeSection`（2）· `LocalBackupSection`（0 条 Flow，收的是**逐字输入的密码 state**）· `WebDavSection`（2）· `RestoreSection`（5，含逐字输入的恢复口令 state）· `LedgerSection`（1）· `BusyFooter`（1）· `WebDavSheet`（2）

**根级只留 3 条，各有硬理由**：
- `busy`：8 处共用的**整屏锁**，只在开/停两次翻转，留在根部让"整屏在忙"只有一处真相
- `message`：屏级 Snackbar 通道，一次性事件，必与 busy 翻转同帧
- `davPicked`：跨分区胶水（远程下载完成 → 喂恢复区 + 关远程列表 sheet），天然不属于任何单一 section

### 一条硬证据（顺带验证了报告诊断是否成立）

反汇编本文件编译产物后确认：`SectionCard { … }` 这类**非 inline composable lambda 是 replaceable group（只有 startReplaceGroup/endReplaceGroup），不是 restart group**（`BackupScreenKt$AttachSyncSection$1` 等的 invoke 里没有任何 startRestartGroup/updateScope）。所以 lambda 内的 state 读会算到**最近的外层 restartable 函数**——**改前确实是整个屏幕**，报告的说法成立，本次拆分真修掉了它。改后 7 条 Flow 的读点落在 `AttachSyncSection`（编译器报告确认该函数 `restartable skippable`）。

### 行为等价性怎么保证的（本模块无 compose-ui-test 依赖，故是推理而非断言）

1. **首帧值**：19 条全是 `StateFlow`，刻意沿用**与原来逐字相同的无参重载** `vm.x.collectAsStateWithLifecycle()`（初始值即首帧 `flow.value`），未手写 initialValue；所有 section 在同一次组合中急切组合（普通 `Column + verticalScroll`，非 Lazy）
2. **节点序**：按 20 个区块做「归一化代码行多重集比对 + 逐块顺序比对」，差异只有回调外提与参数改名两类
3. **逻辑等价**：`running = stage != null` 的取反、两个逐字输入 state 的读写点仍只在各自区内
4. **结构等价**：新 section 均 `restartable skippable`，AttachSyncSection 的 3 个动作 lambda 被编译器 remember 化 → tick 时只有 AttachSyncStatus 重组

**明确无法确认的**（不回避）：无 UI 自动化测试 → **渲染等价是推理不是断言**；重组次数未实测（无 Layout Inspector/tracing）；`LaunchedEffect(recoveryReveal)` 移入子 section 后 4 条 effect 的注册顺序变了；`davUrl` 现在 4 处各自 collect（同帧竞态窗口极小，未实测）；`BusyFooter` 现在**无条件**收集 `stage`（读 StateFlow 无副作用，但收集生命周期确实变了）。

### 判断「不该拆」的地方

加密说明卡 / 恢复演练卡 / 档案 JSON 卡（只吃已在根级的 `busy`，拆出去是 0 收益纯搬运；JSON 卡的导入 launcher 在根部注册，搬进去要连 launcher 一起搬）；`LaunchedEffect(Unit) { refreshAttachStats() }` 留在根级（"进页面"事件、不读 state、零重组代价）；`DavBackupPickerSheet` 本就内部自收。

### 测试与构建

- 单测 **678 条全绿**（数量不变——纯重构）；`detekt` 0 findings；**`:app:lintDebug` 0 error**（`BackupScreen.kt` 命中 0 条）
- release / debug 均 versionCode **90 / 1.0.85**
- 文件 876 → 1118 行（+242 来自 section 签名/入参与「为什么」注释；根函数 164 行）——**本批目标是重组范围，不是行数**
## [v1.0.84] — 2026-10-02

**批次 9：Compose 性能第一批（第四轮性能审查报告的前 5 项 ROI）。** 无库结构变更，可覆盖安装。

### ① 修掉一个真实的跨零点错误（正确性，不只是性能）

`TodayScreen.kt:100` 的 `yesterdayDate = remember { LocalDate.now().minusDays(1)… }` **无 key → 永不重算**：跨零点后「昨日待补」指向的是**前天**。v1.0.74 修过 ViewModel 的日期 ticker，Screen 层这行漏了。

- 改用 VM 既有 ticker 驱动的日期流（**没有新起 ticker**——那正是报告 #12 批评的「7 份重复 ticker」），并新增可单测的纯函数 `yesterdayIso(today)`
- **额外加固（超出报告，已采纳）**：VM 的跨零点 ticker 是协程 `delay` → Android 上落 `Handler.postDelayed`，按 **uptimeMillis** 计时、**深睡不计时**——夜里手机睡着（本应用常态）时跨零点不会触发，可能早上才补跑。故加 `refreshDateIfStale()` + `ON_RESUME` 刷新（复用既有写法，非新 ticker）

### ② 消除组合期重复磁盘 IO

`ReminderCheckScreen` 里 `readReminderCheckState(context)` 内部已调过 `ReminderHealth.snapshot(context)`（SharedPreferences + AlarmManager），渲染时**又单独调一次** → 每次组合两次**主线程同步 IO**。改为随状态带回快照、`remember(refreshTick)` 只读一次。

### ③ `SmallTrendChart` 每帧解析日期 + 分配 Path

`ReportScreen` 趋势网格有 10+ 个 mini cell，滚动时每帧 10×(每个点 3 次 `LocalDate.parse` + 2 个 `Path` + 1 个 List)。照搬 `TrendChart` 既有的零分配模式：坐标与 Path 提到 Canvas 外 `remember`，draw 里只剩乘加 + `reset()` 重填。

**视觉等价用「逐点数学对拍」保证**（本模块无 Compose UI 测试依赖）：新函数与**旧的逐帧公式**在同一运算顺序下逐点比对，另加 6 条单测（含脏点跳过、窗口夹边、y 轴朝向、量程退化不产 NaN）。

### ④ 两个小项

- `RecipeView` 加 `@Immutable`：字段逐一核对为不可变类型（编译器报告确认 `RecipeCard` 的参数从 unstable view 变为 stable view）。**收益边界要说清**：因为 Strong Skipping 本就开着，改前改后都可 skip；真实收益是参数变 stable 后**用 `equals` 而非实例相等比较**，于是 Room 重发整个列表时内容未变的卡片能真正 skip
- `ExercisePlansScreen` 的 `expandedIds`：**`mutableStateSetOf` 在 compose-runtime 1.7.3 不存在**（BOM 2024.09.03 锁 1.7.3，该 API 是 1.8 才有，已用 javap 核实）→ 按 fallback 用 `mutableStateMapOf`。**诚实说明**：实际收益是「每次点击不再整份复制 Set」（省分配），**不是**重组范围的改变——不要当成重组优化

### ⑤ 核实结论：审查报告的 ROI 第 1 条（开启 Strong Skipping）**无效**

**Kotlin 2.0.20 的 Compose 编译器已默认开启 Strong Skipping**，无需任何配置：
- 编译器自己的 metrics 记录 `"featureFlags": { "StrongSkipping": true, … }`——**而我们没有配任何 flag**
- 带**不稳定参数**的 composable 已被标 skippable（如 `SmallTrendChart(stable title, unstable points: List<TrendPoint>)`）；非 Strong Skipping 下这不可能
- 旁证：插件里 `enableStrongSkippingMode` 已 `@Deprecated`，而 `ComposeFeatureFlag` 只暴露 `.disabled()`——2.0.20 只能「关」

**因此未加任何 featureFlags 配置**。另外**刻意没做**报告建议的「skippable 计数 CI 门」：metrics 只反映**最后一次**编译，增量编译 81 total / 全量 1210 total（实测差 15 倍），阈值必然抖动——**脆弱门比没有门更糟**。

（`app/build.gradle.kts` 保留了 `composeCompiler { reportsDestination/metricsDestination }` 诊断块，让上述结论可复算；报告写在 build/ 下、不入库。）

### 测试与构建

- 单测 **668 → 678 条全绿**（70 个 suite）；`detekt` 通过；**`:app:lintDebug` 0 error**
- release / debug 均 versionCode **89 / 1.0.84**

### 未做

- 第 3 项**无像素级比对**（本模块无 compose-ui-test 依赖）；保证来自「公式逐点等价 + 绘制顺序不变 + 单测对拍」
- 第 1 项跨零点行为**无 UI 层测试**（只覆盖纯函数）；「日期流变化 → 重组」由 remember key 保证，本环境无法断言
- 审查报告建议的**大文件拆分**（WellnessScreen 1123 行等）留待后续：本项目无 UI 自动化测试，纯重构除了眼睛没有别的验证手段，故**一次一个屏幕、一版一个**
## [v1.0.83] — 2026-10-01

**批次 8 补充：化验页提示条与上方元素贴太紧。** 无库结构变更，可覆盖安装。

- v1.0.82 把 StatusChip 的高度从**固定**改为**下限**后，两行提示不再被裁，但**紧贴上方元素**（维护者真机反馈）
- 给该提示条上方加 **Spacing.xs（4dp）**——这是项目刻度里注释为「chip 间距」的值；**不用 1–2px**：低于视觉阈值会看着像渲染错位而非留白，且 Spacing.xxs（2dp）的注释明确是「仅组件内部：图标与文字之间」
- 单测 **668 条全绿**（数量不变，纯布局）；detekt 0 findings；:app:lintDebug 0 error
## [v1.0.82] — 2026-10-01

**批次 8：界面观感修复（维护者逐张截图反馈）。** 无库结构变更，无口径逻辑改动，可覆盖安装。

### ① 报表「概览」页用药完成度卡太啰嗦

- **删掉重复标题**：卡片头部已有「用药完成度（计划剂量口径 · 近 30 天）」，卡内**又印了一遍同样标题**。给共用组件加 showTitle 参数——报表侧传 false，**药单弹层侧保持显示**（弹层顶部只有「药名 + 剂量」，不交代百分比口径，标题必须由组件自印）
- **删掉「覆盖起点：自 v1.0.77（批次 3b）起…」整行**（含字符串资源）
- **口径说明从 85 字压到 39 字**（**没有整段删除**——删了两个百分比会互相打架）：
  > 计划口径分母 = 已到点的计划剂量（漏记算未记录）；记录口径分母 = 已记录条数

### ② 化验页黄色提示条文字被截断

**根因不是 maxLines 或 padding**，而是共用组件 StatusChip 把高度**钉死在 28dp**，而两行文案需要约 36dp → 第二行被硬约束裁掉（截图里「接比较」被切一半）。
修法：height(Size.chipHeight) → heightIn(min = Size.chipHeight) + 内层加水平/垂直内边距。**单行 chip 高度分毫不变**（28dp 下限托底），多行按内容长高；**其它长文案 chip 的同类潜在截断一并消除**。顺带精简文案。

### ③ 「补剂依从」卡的绿色「达标」标签被挤压

**根因**：该行里中间的说明列**不参与权重分配**，会按内容宽度吃掉整行，右侧徽标只剩被挤压的残宽 → 两字被迫竖排后被裁。
修法：说明列改为 Modifier.weight(1f, fill = false)（按剩余空间收缩），徽标前的 Spacer(weight(1f)) 换成固定间距——**徽标按自身内容宽度优先测量**，窄屏不再被挤没。

### 测试与构建

- 单测 **668 条全绿**（数量不变——本批只改展示）；detekt 0 findings；**:app:lintDebug 0 error**
- release / debug 均 versionCode **87 / 1.0.82**

### 未做

- 三处均为**纯布局/文案**改动，**无 UI 自动化测试**（仓库无 compose-ui-test 依赖）；真机截图复核由维护者进行
- 附带发现（未改，属既存问题）：StatusChip 可点击变体（化验行内高/低值胶囊）单行仍 28dp，低于 48dp 触达区建议；改动会影响化验列表行密度，留待后续裁决
## [v1.0.81] — 2026-10-01

**批次 7：补剂的「最近服用记录」与逐条删除（维护者需求）。** 无库结构变更，可覆盖安装。

### 需求原话与问题本质

> 「在补剂档案 维生素D3 —— 编辑隔壁的删除，提示删除整个补剂，后续都不能打卡，我的需求其实是点击补剂弹出最近服用记录，可以删除具体哪天的服用记录。」

问题不只是缺功能，而是**两个删除的语义没有区分开**：列表行上的删除是**删整个补剂条目**，而用户要的是**删某一天的那条记录**。本版补齐并把两者在文案上彻底分开。

### 做了什么

- **点击补剂条目 → 「补剂详情」弹层**：显示最近服用记录（日期倒序，**最近 90 天、最多 30 条**，超出时提示「仅列出最近 N 条」）；无记录时显示空态
- **记录行右侧删除 → 二次确认 → 只删该条**：「仅删除该日期的这一条服用记录；补剂本身与其它日期的记录不受影响」
- **删整个补剂条目**：确认框现在会**报出将连带删除的记录条数**（「已有的服用记录也会一并删除（共 N 条）」），与 v1.0.80 复诊记录的级联删除同口径；条数未取到前按钮禁用
- **级联范围不限 90 天窗口**：删条目会删掉它**全部**历史记录（有测试专门钉住这点，避免"窗口外残留"）
- 保留 v1.0.80 的「撤销打卡」（针对今天）
- 删除旧文案 
utrition_supplement_delete_note——它写的是「已产生的服用记录仍保留」，与本版实现的**级联删除正好相反**，留着会误导

### 测试与构建

- 单测 **644 → 668 条全绿**（65 → 69 个测试文件）；detekt 0 条；**:app:lintDebug 0 error**；erifySpecSync 门通过
- 新增：SupplementDeletionTest 4（文案变体与负数兜底）· SupplementHistoryTest 5（90 天窗口、30 条截断、空列表仍为空）· SupplementDeletionCascadeTest 8（**删单条只删该条**、删条目级联删全部含窗口外、不越界、报数=实删数）· SupplementHistoryQueryTest 7（空态、倒序、同日按记录时间倒序、窗口边界、sup_id 为空按名称快照兜底）
- release / debug 均 versionCode **86 / 1.0.81**

### 未做

- **UI 层无自动化测试**：本仓库无 compose-ui-test 依赖，弹层渲染与点击只做了代码审查 + 数据层覆盖；**真机肉眼验证由维护者进行**
- 补剂**打卡**仍只有「已服」一态，故不支持"改成跳过"（v1.0.80 已记录该裁决）
## [v1.0.80] — 2026-10-01

**批次 6「所有录入记录都能修改与删除」+ CI 首次变绿（Lint 40 error 修复）。**

⚠️ 无库结构变更。可覆盖安装，数据保留。

### A. 全量记录可改可删（维护者需求）

| 记录类型 | 改 | 删 |
|---|---|---|
| 复诊记录 / **化验** / **影像** / **疫苗** | ✅ 新 | ✅ 新（复诊为**级联**） |
| 症状 / BASDAI / 发作 / 运动打卡 | ✅ | ✅ 新 |
| 紧急联系人 / 紧急事件 / 忌口 / 身体围度 | ✅ 新 | ✅ |
| 复诊项目 | ✅ 新 | ⛔ 仅「停用」（见下） |
| 补剂打卡 | ⛔ 仅「撤销」 | ✅ 新 |
| 药品 / 用药记录 / 补剂档案 / 体征 / 体重 / 画像 / 食谱 / 附件 | ✅ 原有 | ✅ 原有（食谱补了二次确认） |

**级联规则**（删复诊记录时逐类报数，0 条不显示；条数取到前确认按钮禁用）：
「将一并删除：N 条化验结果 / N 条影像记录 / N 个附件（**连磁盘文件一起删除**）」，并统一追加「删除后无法恢复」。

**删除会连带撤销由它触发的警报**（这是本批最容易被忽略的部分）：
- 疫苗 → 重算该接种日的「活疫苗待确认」（当天还有另一针则保留）；改成灭活或「医生同意」也会撤销
- 症状 → 清该日未确认的红旗警报（**已 ack 的保留**——删了等于销毁用户已确认的信息）
- BASDAI → 清该日未确认的高活动度警报 + 重排提醒
- 发作 → 清该发作窗口内、且不被其它发作覆盖的「已第 7 天」警报
- 运动打卡 → 重排运动提醒

**编辑会重算派生数据**：改化验数值/参考范围 → 走 saveLabResult（本地判读优先；表单刻意不回传 bnormal，iAbnormal 只读带回）；改药品用法/时间 → 清今日起计划槽位 + 重排提醒 + 重物化。

### B. 顺手修掉的四个真实缺陷（都是在实现改删时暴露出来的）

1. **改药品时刻后旧闹钟成孤儿**：闹钟 request code 由槽位（含时刻）派生，只用新药单算不出旧时刻的码 → 旧闹钟会继续按旧时刻响。现在先按**改动前**药单取消一次
2. **物化会补出「假计划」**：materializePlannedSlots 现在**不物化药档最后修改日之前的日期**（否则昨天会被按新定义补出一剂 → 假漏服 + 假补发通知）。取舍：宁可少催一次，也不凭空多出一剂
3. **疫苗警报只有「加」没有「撤」**：新增/编辑统一走 efreshVaccineLiveAlert（改成灭活或「医生同意」即撤销）
4. **身体围度同日改一次多一行**：改为同日覆盖

### C. CI 首次变绿（Lint 40 error）

CI 自建立起**从未通过**（此前 HANDOFF 把它当"门"来描述是不准确的）：失败在 :app:lintDebug 的 40 个 error，每次 push 都会因此发一封失败邮件。本版真修（**不冻结 baseline**）：
- **31 处 StringFormatMatches**：字符串把数字声明成 %N 而调用点传 Int/Long → 改为 %N（**逐个核对全部调用点**，避免 %d 收到 String 抛 IllegalFormatConversionException）。唯一偏差：pdf_lab_ref_range 两个参数是 Double，改用 %1$.2f（渲染 参考 3.50–9.00，与同行化验值的格式一致）
- **9 处 MissingPermission**：NotificationHelper 的 
otify 缺显式权限检查——Android 13+ 未授权时**静默失败**，「提醒没响」会被误当成「没到点」。9 个通知点收敛到一个 postNotification(...)，在同一方法体内做规范检查
  - ⚠️ **SDK_INT >= 33 判断是承重的**：API 26–32 上 POST_NOTIFICATIONS 未定义、checkSelfPermission 恒返回 DENIED，**无条件检查会让这三个大版本上的所有提醒静默失效**

### 测试与构建

- 单测 **610 → 644 条全绿**（65 个测试类）；detekt 通过；**:app:lintDebug 0 error**；erifySpecSync 门通过
- 新增测试：RecordDeletionRulesTest 12 · RecordDeletionCascadeTest 19（Robolectric 真库：级联真删到**附件文件**、改化验重判且不覆盖 AI 标记、同日 BASDAI 不变量、警报撤销/保留、围度同日一行）· MedEditDerivedDataTest 3（改时刻后槽位与闹钟都跟着变）
- release / debug 均 versionCode **85 / 1.0.80**

### 本批未做（维护者裁决项）

- **复诊项目不做物理删除**：checkup_records.item_id 弱引用它、历史靠 item_name 快照自持，物理删会让「按项目查记录」出现指向空项目的行；「停用」已是其生命周期终点。要真删需同时决定历史记录的 item_id 如何处置
- **补剂打卡不做「改成跳过」**：打卡 UI 只有「已服」一态，改成 skipped 等于替用户声称「我决定不吃」；撤销回未记录态才是误点的正确语义
## [v1.0.79] — 2026-10-01

**批次 5「工程化与文档一致性」收口 + 历史迁移实证进 CI。无运行时行为变化。**

⚠️ **APK 体积增加约 93 KB**（实测 2,581,526 → 2,674,592 B）：16 个 Room schema JSON 作为 assets 打进包里（未压缩合计 1.34 MB，APK 内压缩后约 90 KB）——见下。可覆盖安装，数据保留。

### A. 历史 Room schema 补录（批次 2 遗留的诚实边界）

- 新增 pp/schemas/…/4.json ~ 16.json（13 个），**每个都是在「该版本号存续期最后一个提交」上真实构建导出**，非逆向编造
- **起点 1/2/3 是永久缺口，不是待办**：根提交就已是 ersion = 4；119 个提交只出现 4–17；本地 bundle、GitHub 远端（最早 tag v1.0.1）、全盘 7 处 AppDatabase.kt 均无 1/2/3。**伪造 schema 会让迁移测试虚假通过，比没有测试更糟**——故记为永久不可覆盖（HANDOFF §9.8b 已改写，避免以后重复尝试）

### B. 迁移实证进 CI

- 新增 MigrationPathProofTest：用真的 MigrationTestHelper 对**起点 4..16 各自** createDatabase(N) → unMigrationsAndValidate(19, …)
- **13 条升级路径 13/13 通过**，含 Room 按 19.json 的结构校验
- 为让它在 **JVM 单测**（而非只能真机跑的 androidTest）里可用，schema 同时挂到 **main assets**——这是 **APK +93 KB** 的原因（实测：未压缩 1.34 MB，压缩后 90 KB，JSON 压缩比约 15×）；维护者选择用体积换 CI 覆盖
- 现在**可测**：起点 4–18 → 19 全覆盖；**不可测**：起点 1/2/3

### C. version catalog（依赖版本单一来源）

- 新增 gradle/libs.versions.toml：根构建脚本 **6/6** 插件、app 构建脚本 **25/25** 依赖全部改走 libs.*，脚本里**硬编码版本号归零**
- 此前 16 个版本号散落在 plugins/dependencies 两处，"哪个库配哪个版本"只存在于人脑里

### 测试与构建

- 单测 **597 → 610 条全绿**（62 个测试类）；detekt 通过；erifySpecSync 门通过
- release / debug 均 versionCode **84 / 1.0.79**

### 本批未做

- 起点 1/2/3 的迁移覆盖（**永久不可能**，原因见 A）
## [v1.0.78] — 2026-10-01

**批次 4 收尾：AI 标记与本地判读「并列展示」+ 表单日期校验落地。**

⚠️ **含数据库结构变更：Room v18 → v19**（lab_results 新增 i_abnormal 列，**加列型迁移，不动既有数据**）。可覆盖安装，数据保留。

### A. i_abnormal：AI 原始标记只读留档，与本地判读并列展示

v1.0.77 已让「本地参考范围判定优先」，但当时**把 AI 的原始标记覆盖掉了**——而维护者批的口径是「本地优先 + **AI 标记并列展示**」。本版补完：

- LabResult 新增 i_abnormal 列（库 v19）；只有 **AI 导入路径**写它，手工录入恒为 NULL
- saveLabResult 的本地判读**只覆盖 bnormal，绝不碰 iAbnormal**；无参考范围时 bnormal 沿用 AI 值兜底
- **旧行不回填**：NULL = 非 AI 导入或 AI 未给标记（合法业务态）；回填会把本地判读伪造成「AI 当初也这么标」
- **界面并列展示**（LabRow，化验列表与详情弹窗共用）：仅当 AI 标记与本地判读**不一致**时显示一行小字「AI 标记：正常；本地参考范围判读：偏高」；一致或缺一侧**整行不渲染**（不占位、无噪声）
- 刻意**不加**的地方及理由：日期分组的「N 项异常」计数（按本地口径统计，正是要的结论）；AI 导入确认页（入库前两者必然相同，该页要展示的就是 AI 原文）；PDF 化验节（刻意「只列异常项」的医生向结论文本，塞入导入链路中间态会引入医生无法解释的概念）；趋势图（只用数值，无标记可并列）

### B. 表单日期校验（7 处自由文本日期输入，改了 5 处零校验的）

- 复诊记录表单：检查日期（必填）/ 下次日期（可选）；疫苗表单：接种日期（必填）/ 加强日期（可选）；药单编辑：注射周期锚点日期（必填）
- 统一走 DateInput.normalizeOrNull：**输入框提示与保存按钮 enabled 共用同一判定**（不会出现「提示说不行、按钮还能点」），非法时红框 + supportingText + 保存置灰 + onClick 二次拦截，落库前规范化为 ISO
- 可选字段**留空合法**（存 NULL）；必填字段留空视为非法
- 未改 EmergencyScreen / TodayScreen 两处：它们本就有 isError + supportingText + nabled，且用严格 ISO 解析，与其提示文案自洽（改成宽松口径只会扩大行为变更面）

### 测试与构建

- 单测 **585 → 597 条全绿**（61 个测试类）；detekt **0 code smells**；erifySpecSync 门通过
- 新增：LabAbnormalPriorityTest 4 条（真 Room + 真仓库：不一致时两列都保留 / 本地判读不覆盖 AI 留档 / 无参考范围沿用 AI 兜底 / 手工录入不产生 AI 标记）、DateFieldRulesTest 6 条、迁移逐列一致 1 条、parser 留档 1 条
- 迁移自证：MigrationCoverageTest（链 1→19 无缺口）+ SchemaDriftTest（实体↔新库建表逐表一致）+ 新增「加列型迁移与 19.json 逐列一致」（按 18.json 建老库 → 跑 18→19 → PRAGMA table_info 与 19.json 声明逐列比对）
- release / debug 均 versionCode **83 / 1.0.78**

### 本批未做

- version catalog（gradle/libs.versions.toml）已写好但**尚未被构建脚本引用**——切换需改 uild.gradle.kts，留待下一版一并验证
- 历史 16 个版本的 schema 补录（方案见 HANDOFF.md §9.8b）
## [v1.0.77] — 2026-10-01

**批次 3b「计划槽位快照」+ 批次 4「医学规则（仅安全默认值方向）」的合并发布。**

⚠️ **含数据库结构变更：Room v17 → v18**（新增 planned_slots 表 + 2 个索引），可覆盖安装，**数据保留**（迁移只建新表，不动既有表）。

### 批次 3b：真正的「用药完成度」与漏服补发

- **新增 planned_slots 表（Room v18）**：把「计划剂量」物化成快照——(date, medId, slotKey) 唯一索引保证幂等；窗口 昨天 .. +7 天，与提醒的 HORIZON_DAYS 一致
  - 物化调用点 4 处：应用启动、开机广播、今日页重排、药单页重排（与 escheduleAll 同一批调用方）
  - 迁移 SQL 与 Room 导出的 18.json **逐字一致**，由新增的 MigrationTableParityTest 守着
- **新增「用药完成度（计划剂量口径）」**：AdherenceCalc.doseCompletion(planned, logs)——分母是**计划剂量**（不再受漏记影响），与 v1.0.76 的「记录内完成度」并列展示；快照覆盖不足时显示「—（暂无计划快照）」，不给百分比与判定
  - 用户明确「跳过」的剂量既不计完成、**也不计漏服**
- **漏服补发汇总提醒**：启动与开机时，若昨天有计划剂量未记录 → 发**一条**汇总通知（点击进应用补记）；**每天最多一条**（按归属日去重落盘）、免打扰时段静默投递、复用既有通道（不新建通道，避免用户重新授权一遍）
- 新增测试：DoseCompletionTest 18 条、MissedDosesTest 11 条、MigrationTableParityTest 1 条

### 批次 4（仅安全默认值方向）：把「读不懂的数据」一律往保守方向落

- **运动引擎 fail-closed**：payload 解析失败**不再退化成空 JSON**（那会让红榜拿到默认 llow，**禁忌动作反被推荐**）；现在按「禁止」处理并给出可见提示
- **矩阵缺失不再默认放行**：红榜缺 grade_matrix → **降级执行**（种子里 15 条运动条目全部带该键，缺失只可能是数据损坏）
- **实现两个此前被静默忽略的颈椎键**（种子在用）：cervical_condition: "always" → 颈椎受累者**一律拦截**（与分期无关）；"amplitude_half" → **至多降级**，且不会把已更保守的判定改宽
- **AI 异常值不再压制本地判读**：saveLabResult 只要**有参考范围**就一律本地判读（原 bnormal == null 才判 → AI 说「正常」就再也不判，真实异常值从趋势图与 PDF 消失）
- **阈值收敛**：4 处散落的 >= 4.0（报告页 / PDF / 症状弹窗 / 症状卡）全部改走 ClinicalThresholds.basdaiHigh()，删除重复常量 BASDAI_THRESHOLD；补 Double 重载以免调用点 	oInt() 静默截断
- **日期边界校验**：新增 domain/DateInput.kt——2026-13-45、2026-02-31、平年 2/29 **一律拒绝**（原实现正则取数后 %02d 直接拼，脏日期可入库）；兼容「日期带时刻」（2026-07-31 14:30）；导入路径改走它

### 测试与构建

- 单测 **540 → 585 条全绿**（59 个测试类）；detekt 通过；erifySpecSync 门通过
- release / debug 均 versionCode **82 / 1.0.77**

### 本批未做（留待 v1.0.78）

- 表单里**非法日期的用户提示文案**（需要新增字符串资源）
- 「AI 标记与本地判定**并列展示**」需要新增一列 i_abnormal（库 v19）——当前只做到「AI 不压制本地」
- 补剂指标是否统一改名仍待维护者口径
## [v1.0.76] — 2026-10-01

**批次 3a「依从率口径」：让指标名副其实——显示分母、零分母不给判定、按需用药移出。**

⚠️ **无数据库结构变更**（仍为 Room v17），可覆盖安装。含 v1.0.44 ~ v1.0.75 全部内容。

依据：维护者 2026-09-29 的口径裁决（PRN 移出依从率单独统计）+ 本人审查报告对「指标虚高」的复核。

### 为什么要改

原「服药依从」**分母是"已记录条数"而不是"计划剂量数"**：完全漏记（一次都没打卡）不会让它下降，
指标只会虚高；零记录时还会给出 0% 或 100% 与「达标」绿标——**没有数据却给结论**。
按需用药（PRN）一天多次打卡是正常行为，混在同一指标里既抬高又稀释。

### 改了什么

- **改名**：「服药依从（N 天）」→「**记录内完成度（N 天）**」（药单弹层 / 报表卡片 / 周月报 / PDF 四处同步）
- **显示分母**：卡片「共 N 条记录 · 部分完成按 0.5 计」；弹层「完成 x · 部分 y · 跳过 z（共 N 条记录）」；周月报「85%（完成 3 · 部分 1 · 跳过 0，共 4 条记录）」；PDF「3 完成 / 1 部分 / 0 跳过（共 4 条记录）」
- **零分母**：显示「**—（暂无记录）**」+「没有已记录的用药打卡，暂无法计算完成度」，**不给百分比、不给进度条、不给达标/需关注标**
  - 领域层表达为 Completion.ratePct: Int?（无记录 = 
ull，不是 0 也不是 100）+ ClinicalThresholds.completionLabel(Int?)（null 入参 → null）
- **PRN 移出**：领域层按记录自带的 prnFlag 排除（过滤规则只此一处），取数侧用 SQL prn_flag 谓词保持同源
  - 按需药本身：「按需用药 · 近 90 天记录 N 次」（不给百分比/判定）；有历史计划打卡另加一行说明
  - 普通药混有按需记录：追加「另有按需用药记录 N 次，不计入完成度」；周月报与 PDF 各单列一行
- 附带一致性：删除确认框「历史依从率会随之变化」→「历史完成度统计会随之变化」

### 测试

- AdherenceTest **10 → 18 条**（新增 13 条中文用例：零分母不给百分比/不给判定/不等于全跳过、PRN 不计入、混合时只统计计划打卡、分母是记录条数而非计划剂量数、未知状态计入分母不计完成、与顺序无关、90/70 边界各降一档等）
- 单测 **532 → 540 条全绿**；detekt 通过（新指标位抽成独立 @Composable，未引入基线外告警）

### 本批未做（留待 3b）

- **计划槽位快照**（Room v18）：有了它才能算出真正的「用药完成度」（分母＝计划剂量数），本批只是把现有口径改名并显式化
- **漏服补发汇总提醒**（重启/开机后对「已过点未记录」的槽位补一条汇总提醒）
- 补剂指标仍叫「补剂依从」（同一套公式，零分母已不给判定）——是否统一改名待维护者口径
## [v1.0.75] — 2026-10-01

**批次 2「测试安全网」：把「迁移是否真的改对了库」变成机器可判，并给 CI 装上四道门。**

⚠️ **无数据库结构变更**（仍为 Room v17），可覆盖安装。含 v1.0.44 ~ v1.0.74 全部内容。

依据：第三份审查报告 P0-4 / P1-12 / P1-16 / S-13 与第四份报告的 CI 规范门建议；维护者已批准「投入半天到一天 + 允许 test-only 依赖」。

### 1. Room schema 导出并入库（P0-4 的核心）

- pp/build.gradle.kts 增加 ksp { arg("room.schemaLocation", …) }——此前 xportSchema = true 但**从未配置位置**，仓库里一个 schema 文件都没有
- 导出 pp/schemas/com.ashkb.app.data.db.AppDatabase/17.json（90 KB）并入库：此后任何实体改动都会在 git 里留下结构 diff
- 迁移清单与版本号收敛为**单一来源**：顶层常量 ASHKB_DB_VERSION + AppDatabase.ALL_MIGRATIONS（此前内联在 ddMigrations(...) 里，测试看不到，也无法校验完整性）

### 2. 迁移安全网（两条机器可判的检查）

- **MigrationCoverageTest（4 条，纯 JVM）**：迁移链 1→17 逐级无缺口、无重复/乱序、当前版本 schema 已导出、schema 内版本号与常量一致
- **`SchemaDriftTest`（Robolectric 单测，进 CI）**：新建库后把**真实建表语句**与导出 schema 逐表比对（28 张表逐字一致，仅抹平 `IF NOT EXISTS` 这类语义等价的书写差异）——「实体改了、迁移没跟上」从「用户升级时才崩」提前到**每次 CI**。另在 androidTest 源集落地首个用例 `RealDatabaseSchemaTest`，用**生产工厂 + 真实库文件**再验一遍
- 诚实边界：历史 16 个版本的 schema 无法凭空重建，故「从旧版本升级」的路径仍需按 git 历史逐版本导出补录（方案见 HANDOFF.md §9）

### 3. CI 四道门 + 静态分析

- **规范一致性门 erifySpecSync**（新增 Gradle 任务）：版本号 / 单测条数在 pp/build.gradle.kts、README.md、HANDOFF.md、HANDOFF-STATUS.md 四处必须一致，库版本必须有对应 schema 文件——把每次发版的人工核对变成机器门
- **schema 漂移门**：CI 里 git diff --exit-code app/schemas，改了实体不提交 schema 直接失败
- **验签门**：CI 打印签名摘要；配置 ASHKB_EXPECTED_CERT_SHA256 后严格比对
- **detekt**：继承官方规则集 + 项目化让步，历史问题全部进基线（config/detekt/baseline.xml），**基线之外的新问题让构建失败**
- **Android Lint** 纳入 CI（错误即失败）
- 配套 elease-tooling/verify-release.ps1：交付前把 APK 的签名证书摘要与 local.properties 里的期望值比对

### 4. 不再静默用 debug 签名（S-13）

- 此前 local.properties 缺 keystore 时，ssembleRelease 会安静产出一个 **debug 签名的「正式包」**：它无法覆盖安装正式版（用户只能卸载重装、丢数据），而交付者不逐字节验签根本发现不了
- 现在产出 release 包时缺签名**直接构建失败**；CI 显式设置 ASHKB_ALLOW_DEBUG_SIGNING=1 放行并打印醒目告警

### 5. 本机构建绕行（环境说明，不入库）

- 本机到官方 Maven 仓库（repo1 / dl.google.com / plugins.gradle.org）连接被重置，改用**用户级** ~/.gradle/init.gradle 指向国内镜像；**项目文件保持官方仓库地址**（可移植，不影响 CI）

### 测试与构建

- 单测 **527 → 532 条全绿**（新增 `MigrationCoverageTest` 4 条 + `SchemaDriftTest` 1 条）；androidTest 源集首个用例 `RealDatabaseSchemaTest`
- release / debug 均 versionCode **80 / 1.0.75**
## [v1.0.74] — 2026-09-30

**批次 1 补漏：跨零点的追问链不再被一次重排清空。**

⚠️ 无数据库结构变更，可覆盖安装。**本版尚未发布**（GitHub Latest 仍为 v1.0.73）。

### 背景：这是 v1.0.73 自己引入的回归

v1.0.73 修 D2（孤儿闹钟）时把 cancelAllFuture 的扫描窗口从 	oday..+7 扩到 **	oday-1..+7**——这本是对的，但 escheduleAll 只重建 **today 起**的槽位，于是：

> 23:55 那剂药的 00:25 / 00:55 两个升级闹钟，会在**任何一次重排**（打开应用、打卡任何一剂、改药单、开机）时被取消，**且不再重建**。
> 用户在 00:10 打开一次应用，跨零点的追问链就彻底消失——**比修复前的孤儿闹钟更糟**（旧行为至少还能响一次）。

这是自用可靠性最不能接受的一类失效：**沉默地少提醒一次**。

### 修复

- ReminderScheduler.rescheduleAll 新增第 3 步：重建**昨天跨零点过来的、尚未到点的**升级重查（slotDate = 昨天）
- 刻意**不新增**「昨天已打卡槽位」参数：是否该静默由接收器在触发时刻按 slotDate 查库决定（ReminderReceiver 的 settled 判定）——避免 4 处调用点各算一遍、再犯 v1.0.44「漏传导致误提醒」的 N1 教训。代价是可能为已结算槽位多排一个闹钟，触发时静默取消
- 与「漏服补发」裁决一致：昨天漏服的药，其追问会跨越零点继续

### 新增：跨零点「昨天未记录」补记入口（2026-09-30 真机实测暴露的缺口）

**实测场景**：23:55 那剂的追问落在**次日 00:25**，用户被提醒后**回到应用**打卡——但今日页显示的是
**今天**的计划，于是记录被写成「10-01 · 计划 23:55」（提前 23 小时），而**昨天那剂仍然算没吃**，
且应用里**没有任何入口**能补记（只有通知上的「已服用」会写回槽位所属日，v1.0.73 修的正是那条路径）。

- 新增 domain/PendingDoses.kt（纯函数）：列出某日「已到点却既未已服也未跳过」的槽位
- 新增 MedicationRepository.settledSlotRefs(date)：**已结算**口径（done + skipped）——用 done-only 会让
  用户昨天明确跳过（写了原因）的剂量天天挂卡催补
- 今日页顶部新增「**昨天还有 N 剂未记录**」卡 + 每剂一个「补记已服」按钮，**写入槽位所属日**
- 防假阳性：按 startDate 过滤——ScheduleCalc.slotsFor 刻意不按日期过滤（调度器只排未来），
  若不判，**今天新建的药会让今日页立刻冒出「昨天还有 1 剂未记录」**（构建过程中自查发现并锁进测试）
- 测试：PendingDosesTest 8 条（归属日、已结算、未到点、PRN、排序、脏时刻、startDate 正反两面）

### 顺带修掉：「关于」卡版本号会显示过期（维护者 2026-09-30 实测发现）

覆盖安装后，若进程**未被系统杀掉**（HyperOS 上 `adb install -r` 实测如此：装机时刻 22:07:32，而进程启动于 21:56:21），
已组合过的「关于」卡因 `remember` 缓存会**继续显示旧版本号**——维护者据此以为「没装上 v1.0.74」。
现改为**逐次现取**（`getPackageInfo` 走 PackageManager 进程内缓存，代价可忽略），版本号不可能再过期。

> 📌 **由此得到一条装机纪律**（已写入 `HANDOFF.md` §7）：**覆盖安装后必须 `adb shell am force-stop <pkg>` 再启动**，
> 否则可能仍在跑旧代码——「装了新版却没生效」这类误判几乎都源于此。

### 验证

- 单测 **519 条仍全绿**（含 5 个调度器测试）；已装机（versionCode **79**，Xiaomi 15 Pro / Android 16，冷启动正常）
- 真机验收点：今晚 23:55 加一条测试药，**00:10 左右打开一次应用**（关键动作），00:25 / 00:55 仍应各收到一条追问；在其上点「已服用」，记录日期应为**槽位所属日 09-30**（而非次日）
## [v1.0.73] — 2026-09-29

**批次 1「看不见的失效」：把静默失效改成可见、把安全默认值改回保守、把跨零点身份锚回槽位。**

⚠️ **无数据库结构变更**（仍为 Room v17），可覆盖安装。**含 v1.0.44 ~ v1.0.72 全部内容。**

依据：三份外部审查报告的 P0/P1 条目 + 维护者 2026-09-29 的 9 项裁决（自用可靠性优先；医学规则**只改安全默认值方向**；备份威胁模型＝防设备丢失；锁屏卡保留现状；跨零点日志记**槽位所属日**；PRN 移出依从率、体征保留多条；漏服要补发；测试安全网投入半天到一天；AI 异常值**本地优先**）。

### P0-1 闹钟注册失败不再静默（新增可观测层）

- 新增 `reminder/AlarmRegister.kt`：6 处重复的 `runCatching { setExactAndAllowWhileIdle / setWindow }` 收敛为**唯一注册入口**——失败即 `CrashLogger.recordNonFatal` 留档 + 计数 + 来源标签（med / checkup / basdai / exercise / sedentary / test）
- 新增 `reminder/ReminderHealth.kt`：注册台账（尝试 / 失败 / 最近错误 / 时间）落 SharedPreferences，**纯本地不上报**
- 自检页新增第 5 项「**闹钟注册（最近一次重排）**」：无失败显示「尝试注册 N 个 · 无失败」，有失败显示红项 + 最近错误 + 排查指引；「我的」入口摘要随之变为 `n/5`
- **为什么必须做**：`canScheduleExactAlarms()` 返回 true **不等于系统放行**——小米 `MIUIOP(10014)`（精确闹钟）默认 `ignore`（见 `WALKTHROUGH-v1.0.72.md`），此前会出现「自检页显示已授权、用户一条提醒都收不到」的自相矛盾状态

### P0-2 活疫苗安全警报的「默认值反转」（三处一起修）

- `Entities.kt`：`doctorConfirm` 默认值由小写 `"pending"` 改为 `DoctorConfirm.PENDING.name`（消除裸字符串契约，杜绝再次漂移）
- `CheckupForms.kt`：表单默认由 `CONFIRMED` 改为 **`PENDING`**——此前患者登记活疫苗时**不动 chip 就被静默记成「医生已同意」**，high 级安全警报形同虚设（README 还把这条当卖点）
- 判定抽到 `domain/VaccineSafety.kt`（纯函数、枚举比较、未知取值**保守兜底**为待确认），`HealthRepository` 改为调用它
- 新增 `VaccineSafetyTest` 7 条——其中「小写 `pending` 也触发警报」正是被修掉的回归点

### P0-3 末级强提醒不再「假装能全屏」

- 投递前检查 `canUseFullScreenIntent()`（API 34+）：未授予时**不再挂一个会被系统静默忽略的 FSI**，改为**明确降级**——常驻提醒（`setOngoing`，不处理就不消失）+ 文案写明「未授予全屏权限、已用常驻提醒代替」+ 指向自检页
- 打卡 / 跳过 / 全屏页「已服用」三条路径都会撤掉它，故不会赖着不走

### P1-1 / P1-2 / P1-3

- **P1-1**：升级链判据由 `status == "done"` 改为 **done 或 skipped 都算已结算**——用户主动「跳过（有原因）」后不再被 +30/+60 加急（原路径是「骚扰 → 关通知权限 → 提醒彻底失效」）；判定走 `AdherenceCalc` 常量
- **P1-2**：「稍后」不再是无副作用按钮——真排一次 **snooze**（默认 +15 分钟、esc=1「仍未确认」文案、**不升级全屏、不再上链**）；`cancelAllFuture` 一并取消 snooze 闹钟
- **P1-3**：新增 `WAKE_LOCK` 权限与 `reminder/WakeLock.kt`；`ReminderReceiver` / `CheckInActionReceiver` / `BootReceiver` 三个 `goAsync()` 接收器**持锁**（60 秒上限）并在 `finally` 释放——此前若 CPU 在协程中途休眠，通知会丢失**且下一级升级闹钟排不上**，链条一直断到下次打开应用

### P1-4 跨零点身份锚回「槽位所属日」（本批最实质的结构性修复）

- `ReminderScheduler`：排程新增 `slotDate` 参数并随 intent 透传（`EXTRA_SLOT_DATE`）；升级链 / 通知动作 / 全屏页 / 打卡写入**全部按槽位所属日归集**
- `ReminderReceiver`：已结算校验改查 **`slotDate` 那天**——原用 `LocalDate.now()`，跨零点时已是次日 → 查不到 23:50 槽位的打卡 → **对已服的药补发提醒**，而打卡日志又被写到次日 → 原槽位永远空缺、依从率虚低
- `MedicationRepository.checkIn` / `checkInByMedId`：新增 `date` 参数（默认今天＝应用内打卡；通知路径传**槽位日**）
- `cancelAllFuture`：扫描窗口由 `today..+7` 扩为 **`today-1..+7`**——跨零点的 +30/+60 属于**前一天**的槽位，此前永远取消不掉（孤儿闹钟，每次冷启动都清不掉）

### P1-5 闹钟身份改为 SHA-256 派生

- `reqCode` 由 `String.hashCode()` 改为 **SHA-256 前 32 位**，身份锚定「medId + slotKey + **槽位日期** + 级数 + snooze 标志」：分布均匀，且与取消路径落在同一维度上（原实现把 `fireAt` 的日期当身份，跨零点即错位）

### D3 / D4（本人审查发现，三份外部报告均未覆盖）

- **D3**：复诊报告 / 急救卡 PDF 生成移入 `Dispatchers.IO`——原先在 `viewModelScope`（＝Main.immediate）里做多页 Canvas 绘制与文件写入
- **D4**：体征录入加**保存门禁**——至少一项数值，且体温 30–45 ℃ / 收缩压 50–300 / 舒张压 30–200 / 心率 20–250 / 收缩压 > 舒张压；并以 `remember(current?.id)` 修正冷启动回填竞态。此前空表单可直接提交，而 `saveVitals` 是**同日覆盖** → 一次误提交即抹掉当天已录数据

### P1-17 重排离开主线程

- 今日页打卡 / 跳过 / 顺延后的重排**折进 ViewModel 同一协程**（消除「写入未提交就读 `doneSlotRefs`」的竞态），并下 IO；`MeViewModel` 的保存 / 停药重排同样下 IO；UI 侧 5 处独立的 `vm.reschedule(context)` 调用删除

### 口令 / 恢复码页面禁截屏（维护者口径：只加在这类页面）

- 新增 `ui/components/SecureWindow.kt`——同时覆盖 **Activity 窗口**与 **AlertDialog / ModalBottomSheet 自身窗口**（后两者是独立窗口，给 Activity 设标志管不到），离开组合即清除
- 应用于备份页、恢复码展示弹窗、WebDAV 口令 Sheet

### 测试与构建

- 新增 `VaccineSafetyTest` 7 条；单测总数 **512 → 519 条全绿**；release / debug 均 versionCode **78**、真密钥签名

### 刻意不做（本批边界）

- 未把通知 id 全量换新（会让已存在的旧 id 通知无法被取消）；`notifId` 保持原方案，仅闹钟 requestCode 换为 SHA-256 派生
- 依从率口径改造（PRN 移出 / 计划槽位快照 / 漏服补发 / 无数据态）与迁移测试安全网属**批次 2 / 3**，本批不动数据模型
- 其余 4 个 VM（复诊 / 运动 / 运动计划 / BASDAI）的重排仍在主线程，随批次 2 一并处理

## [v1.0.72] — 2026-09-27

**小米 / HyperOS 适配收口：依据小米官方文档补齐「锁屏显示 / 后台弹出界面 / 自启动」引导，自启动跳转改用官方 action。**

⚠️ **无数据库结构变更**（仍为 Room v17），可覆盖安装。**含 v1.0.44 ~ v1.0.71 全部内容。**

### 依据（小米官方《开发最佳实践与兼容性建议（适配常见问题）》）

> 抓自 `dev.mi.com/docs/appsmarket/technical_docs/adaptation_FAQ/`（真机走查后逐条核对原文）：
> 「9、为什么不能在锁屏显示 Activity」「10、如何获取某项权限是否开启？」「11、为什么我的 Alarm 不太精确？」「12、我的应用为什么不能自启动？」

| 官方条目 | 原文要点 | 对本应用的含义 |
|---|---|---|
| **§9 不能在锁屏显示 Activity** | MIUI 引入了**锁屏显示窗口权限控制**，**默认不能在锁屏上显示 Activity**（`WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD`），需要用户主动授予 | v1.0.61 的**末级强提醒全屏**（锁屏上拉起 `ReminderFullScreenActivity`）与 v1.0.66 的**锁屏紧急信息**都受此约束 |
| **§10 如何获取某项权限是否开启** | **暂时没有这个查询接口**……可引导用户跳转应用权限管理页面手动开启：`miui.intent.action.APP_PERM_EDITOR` + `extra_pkgname` | 只能**给指引 + 跳转按钮**，**不做状态行**（与 v1.0.62 对自启动的立场一致） |
| **§12 我的应用为什么不能自启动** | MIUI 上自启动由用户控制，**默认不开放**；自启动**包含开机自启动与接收系统广播** | `BootReceiver` 重排闹钟依赖它 → 跳转改用官方 action `miui.intent.action.OP_AUTO_START` |
| **§11 为什么我的 Alarm 不太精确** | Google 与 MIUI 均启用**对齐唤醒**，会把一小段时间内的 Alarm 对齐到某个时间点一起执行（省电） | 解释真机走查里「末级提醒晚了几分钟」——属系统省电策略，**非缺陷** |

真机实测补充（`adb shell cmd appops get <pkg>`）：MIUI 私有 appops **`MIUIOP(10020)` = 锁屏显示内容**、**`MIUIOP(10021)` = 后台弹出界面**、`MIUIOP(10014)` = 精确闹钟，**默认均为 `ignore`**。也就是说小米上要把「锁屏上显示 Activity」跑通，除 AOSP 的 `USE_FULL_SCREEN_INTENT` 之外**还得过 MIUI 这两道私有开关**，而它们**没有查询接口**。

### 改动

- 新增 `domain/XiaomiCompat.kt`（纯函数）：小米系判定（Xiaomi / Redmi / POCO，品牌或厂商任一命中，大小写与空白无关）+ 需要用户手动开启的开关清单（锁屏显示 → 后台弹出界面 → 自启动，顺序即重要性）
- `reminder/SystemSetupGuides.kt`
  - 新增 `openMiuiPermissionEditor()`：按官方写法用 `miui.intent.action.APP_PERM_EDITOR` + `CATEGORY_DEFAULT` + `extra_pkgname`；**先 `resolveActivity` 判断**（不按品牌硬判，换皮 ROM 也能命中），解析不到兜底应用详情页
  - `openAutoStartSettings()`：**先试官方 action `miui.intent.action.OP_AUTO_START`**，再走原有厂商组件名，最后兜底
  - 抽出 `openAppDetails()` 供四处复用（原为两处各自内联）
- `ui/me/MeScreen.kt`（提醒可靠性自检卡）：小米设备上新增「小米 / HyperOS 额外设置」区块——一段说明 + 「打开小米权限管理」按钮
- `ui/emergency/EmergencyScreen.kt`（锁屏紧急信息块）：小米专属一句提示 + 同一按钮
- `strings.xml` 新增 4 条文案（`reminder_miui_title` / `reminder_miui_hint` / `reminder_miui_open` / `emergency_lockscreen_miui_hint`）

### 改动（UI：自检整块移入二级页）

「我的」页此前把**提醒可靠性自检**整块摊平：4 行状态 + 最多 5 个跳转按钮 + 自启动引导 + 测试提醒 + 小米区块——都是**一次性设置**，却占着主页最显眼的位置（用户反馈「塞太多无用的一次性测试权限列表」）。现改为：

- 新增二级页 `ui/me/ReminderCheckScreen.kt`（路由 `ReminderCheck`）：原有内容与行为**逐项照搬**（v1.0.61 强提醒授权引导、v1.0.62 回前台刷新与测试提醒、v1.0.71 的 `data=package:` 修复、v1.0.72 的小米专属区块），只是换了承载位置
- 「我的」页只留**一行入口**（`NavRow`，图标 + 「提醒可靠性自检」+ 摘要「n/4 项已就绪 · 点开逐项处理」），未就绪时带数量 badge
- 状态读取抽成 `rememberReminderCheckState()`（`internal`），入口行与二级页**共用**，避免两处各写一遍而漂移；回前台重读（v1.0.62 的修复）随之复用
- `MeScreen.kt`：**424 → 227 行**，清掉 17 个因搬迁而失效的 import（`getValue`/`setValue` 保留——属性委托编译期必需）
- 新增文案 `reminder_check_states`（各项状态）、`reminder_selfcheck_summary`（`%1$d/%2$d 项已就绪 · 点开逐项处理`）

刻意**没有**改变任何权限判定逻辑与跳转目标——本次纯粹是信息架构调整。

### 追查：不弹横幅 + 不上锁屏 —— 定论与修复路径（用户亲测）

**定论**：两个症状来自**同一页里的两个小米通道开关**，而它们**默认都不给第三方应用开**：

> **设置 → 应用设置 → ASHKB → 通知管理 → 锁屏紧急信息**
> ① 打开 **「悬浮通知」** ⇒ **横幅恢复**
> ② 把 **「在锁定屏幕上」** 设为 **「显示通知及其内容」** ⇒ **锁屏恢复**
>
> （用户 2026-09-27 依次打开，两项均恢复正常；此前两者都没有。）

**为什么 App 侧改不动**：`NotificationHelper` 早在 v1.0.66 就写了 `lockscreenVisibility = PUBLIC`，但 `dumpsys notification` 里该通道**改动前后都是 `mLockscreenVisibility=-1000`（NO_OVERRIDE）**——MIUI 把每通道的这两项存在**它自己的存储**里，既不读也不回写 AOSP 的通道字段（通道 `mImportance=4` 同样是 MIUI 自己提的）。⇒ 当通道参数与通知 `vis=PUBLIC` 全部正确、却既不弹横幅又不上锁屏时，**别再查通道参数**，直接引导用户去这一页开两个开关。

**背景机制（非决定性闸门）：MIUI 的「通知过滤」分级（FBO）**

| 证据 | 内容 |
|---|---|
| `settings get secure KEY_FBO_DATA` | `{"groupIndex":2,"level1":[微信/QQ/微博/小红书/mi health/WhatsApp…],"level2":[…支付宝/滴滴…],"level3":[…QQ音乐…],"startLevelTime":[3 个时间戳]}` —— 每个 level 恰好 9 个包、带轮换时间戳；**不在任何 level 的包按「不重要」处理**（本应用即属此类） |
| SystemUI 原文日志 | `strings MiuiSystemUI.apk \| grep -i unimportant` → `"No heads up: unimportant notification:"` |
| 系统聚合条目 | `NotificationRecord(pkg=com.android.systemui … tag=UNIMPORTANT channel=id_aggregate)`，对应 `unimportant_entrance` / `unimportant_notification.xml` / `ClickSetUnimportant(pkg=…)` / `miui.util.NotificationFilterHelper` |
| 时间线吻合 | 21:11:03 点「发送测试提醒」→ 同一秒生成该聚合条目 |

⇒ 这解释了「为什么本应用会被判定为次要、通知会被折叠」，但**通道页那两个开关可以直接覆盖它**——所以它不是不可绕过的闸门，此处只作背景记录。

本版据此改动：

- `emergency_lockscreen_miui_hint`：写明上面**两个开关**与验证结论
- `reminder_miui_hint`：同样写明，并区分「横幅＝悬浮通知」「锁屏＝在锁定屏幕上」
- `emergency_lockscreen_desc`：不再承诺「显示在锁屏上，无需解锁即可查看」，改为说明由系统决定
- `NotificationHelper` 通道创建处加注释：AOSP 的 `lockscreenVisibility` 在 MIUI 上不生效（保留仍正确——Pixel 等原生系统按此显示）

**一处自我纠错**：早先把 `mUserLockedFields=4` 读作「MIUI 锁定了重要性」是**误读**——该位是 `USER_LOCKED_VIBRATION`（振动）。「重要性被 MIUI 提到 4」仍成立（`mImportance=4` vs `mOriginalImp=2`），但两者都与本问题无关。

**刻意不做**：不把通知伪装成 `CATEGORY_MESSAGE` / conversation 去骗过系统分类器（语义不实，且属对抗系统行为）。

**✅ 追加结论（2026-09-27 22:10 实测，v1.0.72 装机后）**：这道过滤**不会压掉 `fullScreenIntent`**——末级强提醒在小米上**闭环**：闹钟被正常消耗、通知记录确实挂着 `fullscreenIntent=PendingIntent{… startActivity}`、用户看到 **22:10 亮屏全屏**（「该服药了 / 测试-全屏 1000mg / 计划时间 21:10」+「已服用」「稍后处理」）。前提是通道页那两个开关已打开；且该设置**扛住了版本升级**（v1.0.71 → v1.0.72 覆盖安装后仍生效）。
### 刻意不做

- **不做「小米权限是否已开」的状态行**：官方明确**没有查询接口**，假装能查到就是欺骗用户
- **不改通知通道参数**：`emergency_lockscreen` 通道已随 v1.0.66 发布，通道重要性一经创建即由用户掌控；本轮先给指引，待真机确认「锁屏可见性是否还受通道重要性影响」后再决定是否新建通道

### 测试

- 新增 `XiaomiCompatTest` 8 条（小米/红米/POCO 命中、厂商兜底、大小写与空白不敏感、其它厂商不命中、空值不命中、开关清单恰为三项、锁屏显示排首位）
- 单测总数 **504 → 512 条全绿**

## [v1.0.71] — 2026-09-27

**v1.0.70 真机走查修复版：两处系统设置跳转缺陷（含一个「点了没反应」的死按钮）+ 一处 chip 组溢出缺陷；另按需求新增「删除已停用药品」。**

⚠️ **无数据库结构变更**（仍为 Room v17），可覆盖安装。**含 v1.0.44 ~ v1.0.70 全部内容。**

### 真机走查背景

在 **Xiaomi 15 Pro / Android 16 (API 36) / HyperOS OS3.0.308** 上，对 GitHub pre-release v1.0.70 资产
（与手机内 `base.apk` 的 SHA-256 逐字节一致）做了逐项走查。**已通过项**：Android 16 的 WebDAV 反射回归
（PROPFIND / MKCOL / PUT 全部正常，`HANDOFF.md` §9.8 结项）、8 个通知通道、电池白名单、锁屏紧急卡
（系统级核实 `ONGOING_EVENT` + `VISIBILITY_PUBLIC`）、测试提醒端到端、冷启动与五 Tab。

### 修复（按影响分级）

| 级别 | 问题 | 根因 | 修法 |
|---|---|---|---|
| **中危** | 「允许强提醒」按钮点了**毫无反应**（无跳转、无提示、不崩溃） | 原实现 `runCatching { startActivity(Intent(ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)) }`：① **漏 `data=package:`**（该 action 的 intent-filter 要求 package 数据，不带即 `No activity found`）② 异常被 `runCatching` **静默吞掉** | 新增 `SystemSetupGuides.openFullScreenIntentSettings()`：带 package 数据 → 失败兜底应用详情页 → 仍失败**给文字提示**，绝不静默 |
| **低危** | 「申请精确闹钟」跳到「**全部应用**」的闹钟列表，需自己找 ASHKB | 同根因（漏 `data=package:`）：不带 → `AlarmsAndRemindersActivity`；带上 → `AlarmsAndRemindersAppActivity` | 同上，新增 `openExactAlarmSettings()` |
| **中危** | 骶髂关节影像分期 **6 个 chip 溢出**：「III 中度」「IV 重度」被挤出屏幕外，**根本选不到** | 该组用 `Row` 且无换行/滚动（6 项合计超屏宽）。**同一个坑项目里修过一次**（`TodayScreen` 注射部位那组的注释即为「旧 Row 会把后面的选项截在屏幕外」），v1.0.67 新增该分组时回退成了 `Row`，且漏了最小触摸目标 | `Row` → `FlowRow` + `verticalArrangement`，补 `heightIn(min = Size.touchMin)` |

### 新增：删除已停用药品（用户需求）

- 「已停用药品」折叠区每行新增「删除」入口（**只有在用→停用过的药才可删**，DAO 里另有 `is_archived = 1` 的 SQL 门禁）
- **连带删除**该药的打卡记录与变更记录（事务内四步：计数 → 删日志 → 删变更 → 删药档）
- 确认框**按有无打卡记录分两版**，有记录时**必须报出条数**，并与「历史依从率会随之变化」一并说明——
  不可逆操作不把「删掉多少」讲清楚就是骗用户（判定见 `domain/MedDeletion`）
- 边界：仍在用的药**没有入口**（必须先走「停用」，留下停药原因与生效日）

### 实现细节

- `data/db/Daos.kt`：`MedicationDao.deleteArchived`（带 `is_archived = 1` 门禁）、`MedicationLogDao.countOfMed` / `deleteOfMed`、`MedicationChangeDao.deleteOfMed`
- `data/repo/MedicationRepository.kt`：`deleteArchivedMedication(medId): Int?`（`withTransaction`；仍在用或不存在 → 返回 null 且**不做任何改动**）；`ArchivedMedication` 增 `logCount`
- `domain/MedDeletion.kt`：纯函数（`canDelete` / `variant` / `normalizeCount`）
- `ui/me/MedsScreen.kt`：`ArchivedRow` 接入既有破坏性操作形态 `DestructiveAction`
- `reminder/SystemSetupGuides.kt`：两个设置跳转 + 统一私有 `openAppSettings`
- `ui/me/ProfileEditScreen.kt`、`ui/me/MeScreen.kt`：上表三处修复

### 测试

- 新增 `MedDeletionTest` 6 条（在用不可删 / 已停用可删 / 有记录走报条数变体 / 无记录走另一变体 / 负数按 0 处理且不进报条数分支 / 正数原样返回）
- 单测总数 **498 → 504 条全绿**

## [v1.0.70] — 2026-09-27

**C8c 姿势 / 睡姿建议：C8 最后一项子需求收口——建议以带出处的知识条目落地，不硬编码文案。**

⚠️ **无数据库结构变更**（仍为 Room v17），可覆盖安装。**含 v1.0.44 ~ v1.0.69 全部内容。**

### 姿势 / 睡姿建议

- 运动页处方 hero 新增「姿势 / 睡姿建议」块（处方非空即展示）：
  - **日常姿势**：保持脊柱中立位、避免长时间固定一种屈曲姿势（含胸驼背久坐 / 低头看手机）；坐硬椅 + 腰后小枕维持腰椎前凸
  - **睡姿 / 卧具**：优先仰卧或侧卧；枕头不宜过高（侧卧约与单侧肩宽相当，仰卧用薄枕）；床垫宜较硬；**不建议俯卧**（趴睡需扭转颈部且不利于脊柱伸展）
- 附**来源行**（NASS axSpA 体位教育）与统一免责前缀，与运动处方其余提示一致

### 落地方式（不新造医学建议）

- 正文与依据落在知识库条目 **`edu-005`**（`assets/kb_seed_edu.json`，`payload.key = posture_sleep_advice`，来源 NASS axSpA 运动与体位患者教育 / ASAS-EULAR 2022）
- `domain/PostureAdvice.kt` **只做展示位拆分**（日常姿势 2 条 / 睡姿卧具 3 条 + `shouldShow(plan)`），不新增任何医学说法；完整正文与出处由知识库条目承载
- 知识库联动：`Lifestyle.pinnedKbIds()` 在**睡眠时长登记**后追加置顶 `edu-005`（睡姿是该条目核心内容），与既有吸烟置顶 `edu-003` 同一机制

### 测试

- 新增 `PostureAdviceTest` 9 条（条目 id 一致性 / DAILY 与 SLEEP 拼接稳定 / 要点去重 / 明确包含避免俯卧 / 处方空与非空判定 / 睡眠画像置顶与不置顶）
- 单测总数 **498 条全绿**

## [v1.0.69] — 2026-09-26

**C8b 晨僵时长驱动的起床热身序列：把此前只当「判读依据」展示的晨僵真正接进运动处方。**

⚠️ **无数据库结构变更**（仍为 Room v17），可覆盖安装。**含 v1.0.44 ~ v1.0.68 全部内容。**

### 热身序列

- 运动页处方 hero 新增「起床热身序列」块（仅在有参考价值时出现——晨僵 <15 分钟不打扰）
- **动作不新造**：直接取当日处方里的 **L1 轻柔项**（R27 矩阵已判定「今日可做」且已排除 pause / 拦截项），最多列 4 条
- 处方里没有 L1 项时只给**非处方的通用提示**（缓慢起床 / 室内慢走，以疼痛不加重为界）

### 阈值分档（App 启发式，非指南硬指标）

| 昨日晨僵 | 提示 |
|---|---|
| 未记录 / <15 min | 不展示（避免噪音） |
| 15–29 min | 「起床后先做一轮轻柔项，等僵硬缓解再进入当日处方」 |
| ≥30 min | 加「明显延长」标记 + **常提示炎症活动**，并提示连续多日如此需复诊时告知医生 |

- 与既有 `ExerciseEngine.interpretFeedback` 的「晨僵加重——可能提示炎症活动」**同一口径**，不引入新说法

### 实现细节

- 新增 `domain/MorningWarmup.kt` 纯函数 `build(minutes, plan)`（阈值分档 + 只取 L1 + 封顶 4 条）
- `ExerciseViewModel` 新增 `warmup` 流（`combine(uiState, yesterdaySymptom)`）
- `ExerciseScreen` 的 `PrescriptionHero` 接受 `warmup` 并渲染
- 单测 480 → 489 条（`MorningWarmupTest` 9）
- ⚠️ **C8 只剩「姿势 / 睡姿建议」一项未做**

## [v1.0.68] — 2026-09-26

**C8a 久坐起身提醒：活动时段内按固定间隔提醒起身走动（提醒链第四源）。**

⚠️ **无数据库结构变更**（仍为 Room v17），可覆盖安装。**含 v1.0.44 ~ v1.0.67 全部内容。**

### 久坐起身提醒

- 「提醒设置」面板新增开关（**默认关闭**——一天最多十几次，必须用户显式开启）+ 间隔选择（每 30 / 45 分钟）
- 只在**活动时段 9:00–18:00** 内触发；按固定网格：9:00 / 9:45 / … / 17:30
- 免打扰时段（v1.0.60 B8）内**静默投递**，与其它提醒口径一致
- 通知为**固定 ID 单条覆盖**——十几次提醒叠成一堆没有意义

### 链式单发调度

- 任何时刻**只有一个待触发闹钟**；触发后由 Receiver 排下一个，窗口走完则排**次日窗口起点**
  → 天然连续，不需要每日重新武装
- 不用 `setRepeating`：API 19 起一律不精确，且改配置后取消不干净
- **陈旧闹钟兜底**：用户改窄窗口后，早先排下的闹钟可能落在窗口外——
  Receiver 验真发现不在窗口内就静默丢弃，但**仍要把下一棒交出去**（否则链断掉、永久失效）
- `AshkbApplication` 启动 + `BootReceiver`（开机 / 改时钟 / 换时区 / 权限回授）各重排一次

### 实现细节

- 新增 `domain/SedentaryReminder.kt`（纯函数 `nextFire` / `isWithinWindow` / `isValidWindow`）
- 新增 `reminder/SedentaryReminderScheduler.kt`（链式单发）+ `reminder/SedentaryReminderReceiver.kt`
- `ReminderConfigRepository` 加 4 项配置（开关 / 间隔 / 起止小时）；间隔与起止小时**已备好**
  但本版 UI 只暴露开关与间隔，活动时段固定 9:00–18:00 只读展示
- `NotificationHelper` 加通道 `sedentary_reminders`（IMPORTANCE_DEFAULT）
- 单测 471 → 480 条（`SedentaryReminderTest` 9）
- ⚠️ **C8 只落「久坐起身提醒」一项**；「姿势 / 睡姿建议」「晨僵时长驱动起床热身序列」仍未做

## [v1.0.67] — 2026-09-26

**C1 骶髂关节影像分期：补齐规划 M0「诊断信息全量」缺的最后一项档案字段。**

⚠️ **含数据库迁移 Room v16 → v17**（`profile` 新增 `sacroiliitis_grade` 列），**可覆盖安装**，迁移自动执行。**含 v1.0.44 ~ v1.0.66 全部内容。**

### 档案新字段

- 建档 / 编辑表单新增「骶髂关节影像分期」（改良纽约标准 mNY，X 线 **0–IV**）+ 分级说明
- 选项：未评估 / 0 正常 / I 可疑 / II 轻度 / III 中度 / IV 重度（强直）
- 「我的」档案卡展示该字段
- **复诊报告 PDF** 基本信息区新增该行（诊断信息全量，便于复诊沟通）

### 刻意不加的地方

- **打印版应急卡 PDF 不加**——急救场景只保留即刻相关信息（血型 / 过敏 / 用药 / 联系人）；影像分期属慢性期资料，加进去只是噪音
- **锁屏紧急卡同样不加**（同上理由）

### 档案 JSON 导入导出同步

- `档案 JSON 明文导出 / 导入`（换机建档用）是**独立于 DB 快照的一份字段清单**，本次同步补上 `sacroiliitis_grade`——漏掉它会导致「导出再导入后分期丢失」

### 实现细节

- `Profile` 加 `sacroiliitis_grade`；`MIGRATION_16_17`
- `Labels` 加 `SACROILIITIS_KEYS` + `sacroiliitisGrade()`（key 不出现在 UI，罗马数字在标签里）
- `BackupRepository` 档案 JSON 双方向各加一行
- 单测 467 → 471 条（`LabelsTest` 4）

## [v1.0.66] — 2026-09-26

**B6a 锁屏紧急信息：把急救最需要的信息放到锁屏上，无需解锁即可查看。**

⚠️ **无数据库结构变更**（仍为 Room v16），可覆盖安装。**含 v1.0.44 ~ v1.0.65 全部内容。**

### 锁屏可见的常驻紧急卡

- 紧急卡页新增「锁屏显示紧急信息」开关（**默认关闭**）
- 开启后以**常驻通知**显示：血型 · 诊断 ｜ 过敏 ｜ 关键用药（免疫抑制类标注感染风险）｜ 家属 / 医生电话
- 通知为 `VISIBILITY_PUBLIC`，**锁屏上直接显示完整内容、无需解锁**；`setOngoing` 不可被划掉（避免家人误清）
- 通道 `emergency_lockscreen`（IMPORTANCE_LOW）：**不响不震**——常驻卡不是"提醒"

### 为什么用常驻通知

Android 没有面向普通应用的「自定义锁屏控件」（锁屏 Widget 早已废弃）；
官方唯一受支持的途径就是 `VISIBILITY_PUBLIC` 的常驻通知——锁屏直读、各厂商 ROM 行为一致。

### 刷新时机（不做后台轮询）

- **应用启动**：兜住「改完数据后一直没进紧急卡页」的情况
- **紧急卡页打开期间数据变化**：编辑联系人 / 档案 / 药单时即时同步
- **切换开关时**
- 本应用无后台服务与 WorkManager（离线优先 + 零第三方依赖），故不做定时轮询
- 开关关闭或无可显示内容时**撤下通知**——急救场景里过期/错误信息比没有信息更危险

### 隐私取舍

健康信息上锁屏属敏感操作，因此：**默认关闭** + 页面上有明确隐私提醒（红色小字）+ 用户可随时关闭。

### 实现细节

- 新增 `domain/EmergencyLockscreen.kt`（纯函数构建文案：JSON 数组展平为顿号、免疫抑制标记、用药封顶 4 条并报总数、家属优先于医生、非紧急联系人不进锁屏）
- 新增 `data/repo/EmergencyLockscreenStore.kt`（`app_prefs` 开关）+ `reminder/EmergencyLockscreenPublisher.kt`（读库 → 构建 → 投递）
- `NotificationHelper` 加通道 + `postLockscreenEmergencyCard` / `cancelLockscreenEmergencyCard`
- `AshkbApplication` 启动期同步一次；`EmergencyScreen` 加开关与页面内同步
- 单测 458 → 467 条（`EmergencyLockscreenTest` 9）
- ⚠️ **本版只落 B6 的「锁屏集成」一项；「钥匙扣二维码模板」仍未做**（QR 生成需自写编码器，留作独立增量）

## [v1.0.65] — 2026-09-26

**B12 极简模式状态机（红线三 e2：发作期输入减负）：连续 3 天无核心记录 → 问原因 → 身体不适/住院切极简。**

⚠️ **含数据库迁移 Room v15 → v16**（`profile` 新增 `minimal_since` 列），**可覆盖安装**，迁移自动执行。**含 v1.0.44 ~ v1.0.64 全部内容。**

### 状态机

- **判定**：连续 **3 天**没有「核心记录」→ 触发询问
- **核心记录 = 症状日记录**（`symptom_daily`）。理由：它是本 App 唯一「每日必填且与用药清单无关」的自评入口，客观可判定。**刻意不选「用药打卡」**——无在用药品时它天然为空，会把「没药可吃」误判成「没记录」；也不选「运动打卡」——运动本就非每日必做
- **询问**：三项 —— 身体不适 / 住院 / 其他原因
- **切换**：只有「身体不适 / 住院」进极简模式；「其他原因」**不改界面**——否则会掩盖真实的数据缺口
- **去重**：同一天只问一次（`app_prefs`），次日若仍无记录继续累积

### 极简模式界面

- 今日页顶部横幅：「极简模式 · 自 YYYY-MM-DD 起」+ **退出极简模式** 按钮
- 输入减负：隐藏「运动」快捷入口，只留核心的「症状记录」；告警与用药打卡**照常保留**（安全相关不降级）

### 数据一致性（关键）

- `ui_mode` 与 `minimal_since` **成对维护**：极简态必须有进入时刻，退出必须清空。`MinimalMode.isConsistent` 把这条不变式写成可测断言
- **建档表单补透传** `minimalSince`——否则编辑档案会把极简态重置成「有 ui_mode 无 minimal_since」的不一致状态

### 实现细节

- 新增 `domain/MinimalMode.kt`（纯函数状态机 + 封顶计数的连续缺失判定）+ `data/repo/MinimalPromptStore.kt`
- `HealthRepository` 加 `symptomDatesBetween`（区间日期集合）与 `setMinimalMode`（成对写入）
- `TodayViewModel` 加 `app`（读 prefs）+ `minimalPrompt` 流 + `answerMinimalPrompt` / `exitMinimalMode`
- `AppDatabase` 加 `MIGRATION_15_16`；备份/恢复引擎**无需改动**（表名走 `sqlite_master` 动态发现、列名走 `PRAGMA table_info` 动态校验）
- 单测 450 → 458 条（`MinimalModeTest` 8）

## [v1.0.64] — 2026-09-26

**B13 生活方式画像：采集「吸烟 / 久坐 / 运动习惯 / 睡眠」并驱动运动处方个性化 + 修掉知识库悬空挂点。**

⚠️ **无数据库结构变更**（仍为 Room v15，复用闲置的 `profile.lifestyle` 列），可覆盖安装。**含 v1.0.44 ~ v1.0.63 全部内容。**

### 采集与展示

- 建档 / 编辑表单新增「生活方式」区块：吸烟（从不 / 已戒 / 现吸 / 不确定）、运动习惯（无 / 偶尔 / 规律 / 不确定）、久坐与睡眠（小时/天，可留空）
- 「我的」档案卡新增「生活方式」一行摘要（只列已登记项，全空显示「未填」）
- `profile.lifestyle` 此前**只被透传、从未采集**（列存在但读不出内容）——本版补齐闭环

### 驱动运动处方个性化

- 新增 `domain/LifestylePrescription.kt`：画像 → 个性化提示（戒烟 / 每 30–45 分钟起身 / 睡眠不足不宜过量 / 从 L1 起步或按进展原则加量）
- 运动页处方 hero 在「判读依据」下新增「生活方式提示」区块
- **边界（刻意）**：生活方式**只影响提示，不改红黑榜过滤**——过滤依据是疾病分期与脊柱活动度（医学判据）；吸烟 / 久坐 / 睡眠不构成排除某项运动的依据，硬塞进过滤器属无依据的医学决策

### 修掉知识库悬空挂点（HANDOFF §D 最后一项）

- `kb_seed_edu.json` 的 `edu-003`（吸烟条目）`applicable_scene` 写着「profile 吸烟状态登记后知识库置顶」，但 `lifestyle` 从未采集 → **该联动此前永远不触发**
- 现登记吸烟（现吸或已戒）后，知识库「全部」视图将 `edu-003` 置顶；检索结果与分类视图**不置顶**（保留用户自己的排序意图）

### 实现细节

- 新增 `domain/Lifestyle.kt`（扁平 JSON 手写编解码 + `pinnedKbIds()`；零第三方依赖，domain 纯 JVM）
- `KnowledgeViewModel` 的 `uiState` 由 4 路 `combine` 扩为 5 路（并入 `observeProfile()`）
- `ExerciseUiState` 加 `lifestyleNotes`；运动页提示块复用 v1.0.63 的 `DisclaimerNote` 统一免责前缀
- 单测 435 → 450 条（`LifestyleTest` 7 + `LifestylePrescriptionTest` 8）

## [v1.0.63] — 2026-09-26

**C12 免责声明收口：首启声明门禁（显著位置）+ 自动提示统一前缀。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.62 全部内容。**

### 首启声明门禁

- 首次启动全屏展示免责声明，**点「我已阅读并理解」前不进入应用本体**；确认后写入 `app_prefs`
- 四条要点：非医疗建议 / 不替代诊疗 · 用药与调整以主治医师医嘱为准 · 数据仅存本机不上传 · 不是医疗器械
- **门禁在创建任何 ViewModel 之前**——未确认前不会打开数据库
- prefs 不跨备份恢复，换机后需重新确认（合规上期望如此）

### 自动提示统一前缀

- 新增唯一文案来源 `domain/Disclaimer.kt`（`PREFIX` = 「仅供参考，以主治医师医嘱为准。」），
  与 `RecipeSources.DISCLAIMER` 同理放 domain：**合规底线措辞必须能被单测锁定**
- 新增共享组件 `DisclaimerNote`，应用于三处**自动生成**的健康提示：
  漏服处理指引 / 复诊准备清单 / 跨院化验提示
- 连带把三处提示各自的「以…为准」措辞收敛，避免与统一前缀重复：
  `missed_dose_disclaimer`（去「与主治医师医嘱」——前缀已覆盖）、
  `checkup_prep_disclaimer`（去「与主治医师」）、
  `lab_unit_group_hint`（去「仅供参考」）

### 保留不变（刻意）

- 知识库 / 食谱的**条目级**声明与 PDF 页脚**保持自足完整文本**——它们要脱离 App 被阅读，
  不能依赖统一前缀
- 「关于」卡原有的 `me_license_note`（含完整免责声明）不动

### 实现细节

- 新增 `domain/Disclaimer.kt` + `data/repo/DisclaimerStore.kt` + `ui/DisclaimerScreen.kt` + `ui/components/DisclaimerNote.kt`
- `AppShell` 顶部加门禁早返回（`rememberSaveable` + prefs）
- 单测 429 → 435 条（`DisclaimerTest` 6 条锁定措辞）

## [v1.0.62] — 2026-09-26

**C11 提醒可靠性收口：电池白名单 / 自启动引导 + 端到端「测试提醒」验证。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.61 全部内容。**

### 电池白名单

- 自检卡新增「电池白名单」一行，用官方接口（`PowerManager.isIgnoringBatteryOptimizations`）显示**真实状态**
- 未加入时给「加入电池白名单」按钮：优先官方直连弹窗，ROM 不支持则兜底到电池优化列表页
- Manifest 加 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

### 自启动引导

- 新增「自启动」区块 + 「打开自启动设置」按钮：按厂商（小米 / 华为 / 荣耀 / OPPO / 一加 / realme / vivo / iQOO / 三星 / 魅族）组件名**尽力跳转**，全部失败兜底到应用详情页
- **刻意不做状态行**——系统未提供任何公开查询接口，假装能查到状态是欺骗用户

### 测试提醒（端到端链路验证）

- 新增「发送测试提醒」按钮：**真排一个 10 秒后的精确闹钟** → `TestReminderReceiver` → 发通知
- 验证的是整条链路（权限 + 精确闹钟 + 通知通道 + 投递），而不只是权限状态——「权限全给了但厂商后台策略掐掉闹钟」这类故障只有这样才暴露
- 同样尊重免打扰时段（DND 时静默投递）

### 其他修复

- **自检卡状态从系统设置页返回后自动刷新**：此前各项状态只在首次组合时读一次，用户刚授予权限、切回来仍是旧值（用 `ON_RESUME` 生命周期观察者触发重读）

### 实现细节

- 新增 `reminder/ReminderTest.kt`（`nowMs` 可注入）+ `reminder/TestReminderReceiver.kt` + `reminder/SystemSetupGuides.kt`
- `NotificationHelper.postTestReminder`（走 `sys_notices` 通道，**刻意不归入提醒折叠组**）
- 动作按钮由横向 `Row` 改纵向满宽 `Column`（按钮从 2 个增到最多 4 个，横排会溢出）
- 单测 425 → 429 条（`ReminderTestSchedulerTest` 4 条）

## [v1.0.61] — 2026-09-26

**B9 升级链第三级「强提醒」：用药漏服的最后一道防线——末级升级改用全屏 Intent 唤醒锁屏。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.60 全部内容。**

### 强提醒（全屏）

- 用药升级链的**末级**（+60 分钟仍未确认）从「普通重复提醒」升级为**强提醒**：以 `fullScreenIntent` 拉起全屏界面，锁屏时可直接唤醒亮屏并覆盖锁屏
- 全屏界面显示药名 + 剂量 + 计划时间，两个操作：**已服用**（免开应用直接写库打卡）/ **稍后处理**
- **仅用药链启用**——BASDAI / 运动为非紧急源，全屏会过度打扰
- **免打扰时段内不升级全屏**（V1.0.60 DND 的静默投递优先级更高）

### Android 14 适配

- 新增 `USE_FULL_SCREEN_INTENT` 权限；Android 14+ 该系统权限**默认不授予**非通话/闹钟类应用
- 「提醒可靠性自检」卡新增「强提醒（全屏）」一行，未授予时给「允许强提醒」按钮跳系统设置（`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`）
- 未授予时**优雅降级**为横幅（HIGH 通道仍有效），不报错、不丢提醒

### 实现细节

- 新增 `ReminderFullScreenActivity`（`showWhenLocked` + `turnScreenOn` + 独立任务栈不进最近任务；API 27 边界守卫）
- `ReminderScheduler.isStrongEscalation(escalation)` 纯函数判定末级（含单测）
- `NotificationHelper.postMedReminder` 加 `strong` 参数 + `setFullScreenIntent`
- 单测 424 → 425 条

## [v1.0.60] — 2026-09-26

**B8 免打扰时段 + 同时段多提醒合并推送：四源提醒叠加后的体验补丁。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.59 全部内容。**

### 免打扰时段

- 我的页「提醒设置」新增免打扰开关 + 起止时间选择（默认 22:00–07:00，跨午夜）
- 时段内所有提醒**静默投递**：不响铃、不震动，通知栏仍可见（用户醒后可见）
- 用药提醒也静默投递（不顺延，避免漏服窗口问题）
- 开关 / 时间变更只写 prefs，**无需重排闹钟**——Receiver 触发时实时读配置

### 同时段多提醒合并推送

- 所有提醒通知归入同一通知组 `ashkb_reminders`
- 2+ 条提醒同时存在时自动折叠为 summary（"您有 N 条待处理提醒"），仅 summary 发声
- 单条提醒时无 summary，自身正常发声

### 实现细节

- 新增 `domain/DndWindow.kt` 纯函数（跨午夜支持，左闭右开），12 条单测
- 新增静默通道 `reminder_silent`（IMPORTANCE_LOW，无振动无声音）
- `NotificationHelper.postXxxReminder` 加 `silent` 参数 + `setGroup` + `updateGroupSummary`
- 4 个 Receiver 触发时调用 `NotificationHelper.isInDndNow(context)` 决定通道
- 单测 412 → 424 条

## [v1.0.59] — 2026-09-26

**B5 多源提醒：提醒链从「仅用药」扩到「用药 + 复诊 + BASDAI 问卷 + 运动」四源同台。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.58 全部内容。**

### 三源新提醒

- **复诊提醒**：`nextDate` 提前 1 天（09:00）+ 当日（09:00）各 1 次，无升级链
- **BASDAI 问卷**：评估日 20:00 + +1/+2 天 20:00 升级重查（周期可配：每周 / 每两周 / 每四周（默认）/ 每八周 / 每十二周）
- **运动提醒**：18:00 首次 + 20:00 / 22:00 升级（今日已打卡则不排）

### 三源独立通道

`checkup_reminders`（HIGH）/ `questionnaire_reminders`（DEFAULT）/ `exercise_reminders`（DEFAULT）——让用户可分别静音。

### 设置入口

「我的」页新增「提醒设置」卡：复诊 / 运动开关 + BASDAI 周期单选。每次变更即时写入并重排（开关关闭则 cancelAllFuture）。

### 实现细节

- 三源 Scheduler 各为独立 object，复用 `setExactAndAllowWhileIdle` 降级 `setWindow` 模式
- requestCode 命名空间隔离（`chk|` / `bas|` / `exc|`），通知 ID 前缀防与用药撞
- `BootReceiver` / `AshkbApplication` 启动期 + 4 个 ViewModel 数据变更点（saveCheckupRecord / saveBasdai / checkIn / activate-deactivate）即时 reschedule
- 18 条单测（CheckupReminderScheduler 6 + BasdaiReminderScheduler 5 + ExerciseReminderScheduler 7），覆盖 7 天窗口 / 升级链 / 幂等 / 取消语义

## [v1.0.58] — 2026-09-25

**v1.0.57 的收尾：拖动读数不再只报一条（同日多值全部报出）；并按用户要求撤掉小图下那行「同日多条数值」说明。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.57 全部内容。**

### 缺陷一：拖到 03-13 仍只读到 0.4

v1.0.57 已把同日的两条 CRP（36.33 与 0.4）**都画了出来**（图上是一段竖线），
但**拖动读数气泡仍只报一条**，且报的恰是较小的 0.4（用户实测：「手指拖动到 3.13 的时候还是显示 0.4」）。

根因：`nearestIndex` 找最近点用**严格小于**比较，命中同日**第一个**下标；
而同日多值在 `points` 里按数值**升序**排列 —— 于是永远报到较小的那条。

**这说明「把图改对」不等于「把读数也改对」**：一个 x 对应多个 y 之后，所有「先用 x 定位、再取第 i 个点」
的消费方都得同步改，否则会留下「线画对了、读数还是错的」这种一半对一半错的混合状态。

修法：新增纯函数 `sameDateIndices(points, index)`，一次取出**该日的全部下标**，
高亮（该日每个值各画一个圆点）与读数都不再只报一条；气泡文案由纯函数 `selectionText` 生成，
同日多值**全部列出且从大到小**——与图上竖线自上而下一致，偏高的异常值先被看到。

### 缺陷二：小图下那行说明破坏整体性

v1.0.57 在化验小图下加了一行「有 N 个日期存在多条不同数值（均已画出）」。
但那些值**本来就已经全部画出来了**——图上一段竖线已把话说清楚；
再挂一行文字只会把该格撑高、与左侧「血沉」格不齐，破坏 2 列小多图的整齐（用户实测反馈）。

故撤掉该文案，并删除只服务于它的 `LabTrend.conflictDates` 与字符串 `report_lab_same_date_conflict`。
`hasCaveat` 只保留「有数据被排除在外」（单位认不出 / 缺失）——那才是**图上看不出来**、必须说明的情况。

**判据沉淀**：文字补注只应用于「图上看不出来的事」；能被图形本身表达的，就不要再写一遍。

### 验证

- 单测 **394 条全过，0 skipped**（389 + 5）。新增 5 条覆盖两个新纯函数：
  `same date indices returns every point of that day`、`same date indices handles empty list and out of range index`、
  `selection text lists every value of the day largest first`、`selection text stays a single value when the day has one point`、
  `selection text shows the full date across year boundary`。
  `LabTrendTest` 相应去掉 `conflictDates` 断言，回归锁仍锁定「36.33 与 0.4 两个值都必须在图上」。
  （气泡文案断言刻意避开小数分隔符，防止 locale 差异造成假红。）
- `aapt dump badging` 核实 `versionCode=63 / versionName=1.0.58`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，与历史版本一致，可覆盖升级。

## [v1.0.57] — 2026-09-24

**真正的修法：同日多值不再「取一条」，全部画出；并按用户要求移除 hs-CRP。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.56 全部内容。**

### 前两版为什么没修对

用户反馈「3.13 的数据 C反应蛋白明显是 36.33，折线图里却不是」。
- v1.0.54：把 **hs-CRP** 并入 CRP 序列 → 猜错方向（真正原因是同日有**两条 CRP**）。
- v1.0.56：把 hs-CRP 拆成独立指标 + 加了「同日不同值」计数提示。**提示确实生效了**
  （用户截图里出现了「同日有 1 条不同数值，已取最近录入的一条」），
  但**数值仍然是 0.4** —— 因为 v1.0.54–v1.0.56 一直沿用同一条规则：
  「同一日期保留 `recordedAt` 最新的一条」，而那一版选中了 0.4、丢掉了 36.33。

**根子在于：「从多条里挑一条」这个动作本身就是错的。** 无论挑哪一条，都可能与用户手上的
化验单不一致；而用户既不知道该日有两条记录，也无从判断我们挑了哪条。加提示只解决了
「看不出来」，没解决「数值仍然是错的」。

### 修法

- **同日多值全部画出，一个不丢**：`LabTrends.buildOne` 去掉「取其一」，
  同一日期出现多个不同数值时**全部保留**（完全相同数值仍只留一个——同 x 同 y，无信息损失）。
  图为在同一横坐标上表现出一段竖线，用户一眼能看出「这天有两条记录」。
  **要不要清理重复记录是用户的数据决定，不由我们替他做。**
- `LabTrend`: `sameDateConflict`（条数）改为 `conflictDates`（**日期数**），文案相应改为
  「有 N 个日期存在多条不同数值（**均已画出**）」——语义从「我们丢了 N 条」变成「这天有多个值」。
  参考上限仍取该日最新一条自带的 `refHigh`（只用于画阈值线，不参与取值）。
- **移除 hs-CRP**（用户要求「hs-CRP 不要」）：`LabIndicator.HSCRP` 与 `HSCRP_HIGH` 一并删除，
  CRP 别名表**不含**任何「超敏 / hs-CRP」写法 → 这类记录**不参与任何趋势**（既不混进 CRP，也不单独成格）。
  `LabIndicator` 只剩 ESR 与 CRP 两项，`alwaysShow` 参数随之删除（无使用者）。

### 验证

- 单测 **389 条全过，0 skipped**（390 → 先删 hs-CRP 相关 2 条、再加 1 条）。
  **回归锁直接用用户那组真实数据**：`同日两条不同 CRP 全部画出 36点33 不会再被顶掉`——
  同一日期 CRP 36.33 与 0.4 两个值都必须在图上、`conflictDates == 1`、阈值取单据自带的 6。
  另加：`同日多条不同值全部保留 并计数冲突日期`、`同一天重复录入同一个值合并为一个点`、
  `超敏 CRP 记录不参与任何趋势`。
- `aapt dump badging` 核实 `versionCode=62 / versionName=1.0.57`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，可覆盖升级。

## [v1.0.56] — 2026-09-24

**修一处会画出错误数值的缺陷：hs-CRP 被并进 CRP，导致趋势图上 CRP 显示 0.4、而化验单上是 36.33。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.55 全部内容。**

### 缺陷（用户实测：图与化验单直接矛盾）

用户 2026-03-13 的化验单：**C反应蛋白(CRP) = 36.33 mg/L**（参考 0–6，偏高）。
而趋势页把该日画成 **0.4**，均值 0.4、超阈值 0/4 次——**CRP 的 36.33 根本没进图**。

**根因**：同一份 43 项化验单里通常**同时**有 CRP 与**超敏 C 反应蛋白（hs-CRP）**，
而 v1.0.54 把 hs-CRP 的写法并进了 CRP 序列。于是同一日期出现两条 CRP 记录
（36.33 与 0.4），按日期去重时**选中了 hs-CRP 的 0.4**。
hs-CRP 0.4 mg/L 属正常，躺在化验单折叠的「正常项」里，用户不易察觉——
**图与单据不一致却毫无提示**，正是本项目最忌讳的那类错误。

**这是一条设计错误，不是实现失误**：hs-CRP 与 CRP 是**同一蛋白的不同检测**，
但量级差约一个数量级（炎症期 CRP 36.33 vs hs-CRP 0.4），
「同一个蛋白」不等于「同一个检测」——**量级不同的两项永远不要合并成一条序列**。

### 修法

- **hs-CRP 独立成第三项指标**（`LabIndicator.HSCRP`，`超敏C反应蛋白`）：CRP 的别名表
  移除全部「超敏 / hs-CRP」写法，HSCRP 单列自己的写法；两者**互不匹配**（单测双向锁定，
  否则同一行会被画两遍）。阈值各取自己那份化验单的 `refHigh`（本例 CRP 6、hs-CRP 1）。
- **二级指标只在有数据时占格**：新增 `LabIndicator.alwaysShow`，ESR / CRP 恒占位（让用户知道
  该功能存在），hs-CRP 只在真有记录时才出现，避免给不测这项的人留一个永久空格子。
- **同日冲突不再静默丢弃**（安全网）：同一指标同一天若出现**不同数值**，新增
  `LabTrend.sameDateConflict` 计数，UI 显示「同日有 N 条不同数值，已取最近录入的一条」。
  值相同的重复录入不计（无信息损失）。这条专治「悄悄取其一」这一类问题。
- `LabResultDao.allOrdered()` 的排序补 `recorded_at, id`，让同日多条的选择**可复现**。

### 验证

- 单测 **390 条全过，0 skipped**（386 → +4）。**回归锁直接用用户那组真实数据**：
  `同日 CRP 与超敏 CRP 各归各的 不会互相顶掉`——同一日期 CRP 36.33 + hs-CRP 0.4，
  断言 CRP 序列是 `[36.33]`、hs-CRP 序列是 `[0.4]`、阈值分别 6 与 1、且都不算冲突。
  另加：`超敏C反应蛋白自成一项 绝不再并入常规 CRP`（双向）、`超敏 CRP 是二级指标`、
  `同一天重复录入同一个值不算冲突`、`同一天多条…并如实计数冲突`。
- `aapt dump badging` 核实 `versionCode=61 / versionName=1.0.56`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，可覆盖升级。

## [v1.0.55] — 2026-09-24

**修 v1.0.54 炎症指标的四问题：化验不再套 7/30/90 天窗口、识别「红细胞沉降率测定」等真实写法、空格子文案与标题截断。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.54 全部内容。**

### 用户实测反馈（两个）

1. **抽血化验不该按 7/30/90 天画**——「我是隔三个月验血一次」。
   确实：默认 30 天窗口下，季度化验的项目**几乎永远是空的**（或只有一个点），
   而用户真正想看的是「这一年几次化验的趋势」。
2. **ESR 识别不到**——复诊管理 → 化验里存的名字是**「红细胞沉降率测定」**，
   而 v1.0.54 的别名表只有「红细胞沉降率」，于是**一个点都认不出来**。

### 修法

- **化验不设日期界**：`ReportRepository.trends` 改调 `LabResultDao.allOrdered()`，
  `LabTrends.build(rows)`（窗口参数改为**可选**，`null` = 不设界）。
- **两套独立的时间轴**（这是上一条的必要配套）：化验几个月一次、日常指标每天/每周一次，
  采样频率差 1~2 个数量级，**放在同一根轴上必然一方被压扁**
  （化验挤成右侧一个点，或日常指标压成左侧一条线）。故日常 6 格共用一个轴（跟随 7/30/90 天），
  炎症 2 格单独一个轴（展示全部记录）。两区各有自己的时间轴说明行。
- **名称识别改为「有界变形 + 命中别名表」**：先归一（大小写 / 全角括号 / 空白），
  再逐层剥掉尾限定词（`测定`/`定量`/`检测`/`检验`/`检查`/`试验`/`法`）与去括号，
  逐个形态去比别名表。**仍然不是自由 `contains` 匹配**——配错（把别的指标画进来）比漏配危险得多。
  **教训：靠枚举写法永远会漏**（v1.0.54 枚举了「血沉测定」却漏了用户真实数据里的
  「红细胞沉降率测定」），要用有限规则覆盖整类变化。
- **空格子文案按分组给**：v1.0.54 把「暂无化验数据」写死在格子组件里，
  于是**收缩压 / 心率**这些非化验指标的空格子也在说「暂无化验数据」（截图可见）。
  改为由调用方传入（日常用「暂无数据」，化验用「暂无化验数据」）。
- **格子标题不再被挤成省略号**：格子窄，「C反应蛋白 CRP」+ 右侧数值会截成
  「C反应蛋白 C...0.4 mg/L」。格子里只显示中文名，英文缩写放到**展开后的大图标题**与无障碍描述里。

### 验证

- 单测 **386 条全过，0 skipped**（381 → +5）：
  新增「尾限定词/括号的有限变形都能匹配」（11 种写法）、「不相关指标带限定词也不会被误配」、
  「变形集合的边界行为」、「不传窗口时取全部记录」、「红细胞沉降率测定与其它真实写法都能成图」。
- `aapt dump badging` 核实 `versionCode=60 / versionName=1.0.55`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，可覆盖升级。

## [v1.0.54] — 2026-09-24

**趋势页方案 C：8 个指标压成 2 列小多图同屏（共享时间轴），并新增 ESR / CRP 客观炎症指标。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.53 全部内容。**

### 为什么做这个

趋势页原本 6 个指标各占一张大图，要滚很久才能对「这几周是不是一起动的」有个大概印象——
而那恰恰是趋势页最该回答的问题。更关键的是**炎症指标根本没进趋势页**：
ESR / CRP 一直存在 `lab_results` 里，却是强直随访最该看的客观指标，
此前只能在复检记录里逐条翻。

### 小多图（2 列同屏）

- 6 个原有指标 + 2 个炎症指标，共 8 格同屏；每格保留**末值、阈值虚线、逐点标记**。
- **共享时间轴**（本方案成败所在）：窗口由**全部序列日期的并集**算一次、全格共用，
  相对坐标按 `(日期 − 窗口起) / 窗口长度` 定位。若各格按自己的数据范围铺开横轴，
  各格 0%–100% 对应的日期就不同，「同一时间点上下对齐着看」这个前提直接失效——
  **而图画出来依然很好看**，属于本项目最忌讳的那类「静默说谎」。
- 左槽只标「上界 / 下界」两个数（小图不标全刻度）。
- **代价与补偿**：小图不能拖动读数。为不丢 v1.0.45 的能力，**点任一格展开成大图**
  （原 `TrendChart`，含拖动读数与读数摘要）。

### 新增炎症指标（ESR / CRP）

- 新 `domain/LabIndicator`：**指标目录 + 名称归一 + 单位换算**。
  此前 `lab_results.test_name` 是自由文本、`observeTrend` 是精确等值匹配，
  于是 `"血沉(ESR)"` 与 `"ESR"` 会变成两条互不相干的序列。
  归一处理大小写、全角括号、中英文空格；**刻意不做模糊匹配**——
  宁可漏配（表现为「暂无数据」）也不要配错（把别的指标画进来）。
- 新 `domain/LabTrend`：`lab_results` 行 → 序列的纯函数。三类容易静默出错的口径都在这里挡住：
  - **单位**：CRP 的 `mg/dL` 按 ×10 换算成 `mg/L`（不换算会凭空多出一次「骤降」）；
    认不出的单位**不纳入**并**计数**；单位缺失的按规范单位计也**单独计数**——
    两种计数都会显示在图下（「另有 N 条…未纳入」），绝不让人把「图上没有」读成「没测过」。
  - **窗口**：闭区间过滤；只有文字结果（无数值）的行不生成点。
  - **同日多条**：只留 `recordedAt` 最新的一条（重复抽血 / 重复导入）。
- 阈值：优先用**化验单自带的 `refHigh`**（取最新一条非空），否则回退
  `ClinicalThresholds.ESR_HIGH / CRP_HIGH`。界值随实验室、性别、检测方法而变
  （超敏 CRP 的界值远严于常规 CRP），用死值判「超标」会把正常结果标成异常。
- 炎症指标的**值域**独立（各格各自量程），与共享横轴不冲突。

### 验证

- 单测 **381 条全过，0 skipped**（348 → **+33**）：指标名归一（该认的 12 种写法 / 不该认的 8 个近似名 /
  **断言不做模糊匹配**）、单位换算（含 `mg/dL`→`mg/L`、大小写与结尾点）、认不出与缺失单位分别计数、
  窗口闭区间、同日去重、升序输出、`refHigh` 优先级与兜底、共享窗口并集（含脏日期）、
  窗口相对坐标（含单日窗口除零、跨月、越界夹紧）。
- `aapt dump badging` 核实 `versionCode=59 / versionName=1.0.54`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，可覆盖升级。

## [v1.0.53] — 2026-09-24

**S1 落地：提醒链第一次有了自动化回归测试（Robolectric + `AlarmManager`）。S1b 手脚势测试已实测证伪、不予落地。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.52 全部内容。**

### 背景：为什么提醒链一直没有测试

`ReminderScheduler` 的全部行为都落在 `AlarmManager` 上，而纯 JVM 单测
（`isReturnDefaultValues = true`）下 `android.*` 是空实现——闹钟排了没排、排了几次、
有没有被清掉，**一个字都验不了**。于是 A1（开机后误提醒）、N1（开机广播漏传 `doneRefs`
导致已服药槽位仍排升级提醒）这类缺陷只能靠真机发现。S1 就是补上这一层。

### 落地内容

- **引入 Robolectric 4.13**（仅测试期依赖）。已确认其 instrumented `android-all(API 34)`、
  `shadows-framework`、`androidx.test:monitor` 等全部命中本机 Gradle / Maven 缓存，可离线跑。
- **`ReminderSchedulerTest`，9 条**，断言「闹钟触发时刻集合」这一对外唯一可观察效果：
  未来 7 天各排首次提醒 / 今日已过点未打卡**会**重建 +30 与 +60 / 今日**已打卡**的槽位
  **不再**重建（N1 回归锁）/ 未到点的槽位不排升级重查 / 重复重排幂等（不累积）/
  `cancelAllFuture` 能**精确清空** `rescheduleAll` 排出的全部闹钟（取消-重建对称性）/
  停药显式取消后不留残留（R6）/ Q2W 注射药只在注射日排 / `slotRef` 维度正确。
- **`rescheduleAll` / `cancelAllFuture` 增加可注入的 `now` / `today` 参数**（均有默认值，
  对生产调用方零影响）：本方法的可观察效果完全由「现在」决定，而 Robolectric **改不动
  `java.time` 的挂钟**（`ShadowSystemClock` 只影响 `SystemClock`，实测 `advanceBy` 后
  `LocalDateTime.now()` 不变）。注入「现在」后测试可在**任何时刻**运行且结果一致——
  这与 `ScheduleCalc.slotsFor(med, date)` 显式传日期的既有风格一致。

### 单测 348 条全过（339 → +9）

### S1b（滑杆手势回归）——**已实测证伪，不予落地**

原计划用 Robolectric + `compose-ui-test` 真实派发触摸事件，锁住「拖动彻底失效」
（横跨 v1.0.17–v1.0.46 共 29 个版本）那类手势缺陷。写完 4 条断言后**3 条失败**，
于是做了两个探针来区分「应用缺陷」与「环境假红」：

| 探针 | 结果 |
|---|---|
| 最小 `pointerInput` 盒子能否收到注入的 down | ✅ 收到（`downs>0`）→ 本环境**能**注入触摸事件 |
| **裸 M3 `Slider`**（无 `ScoreInput` 包装、无祖先旁听）能否被拖动 | ❌ 拖不动（`v` 恒为 0） |

**结论**：Robolectric 能注入触摸，但**驱动不了 M3 `Slider` 的拖动**，与 `ScoreInput`
的写法无关——那 3 条失败是**环境假红**，**不能**据此判定应用缺陷。故 S1b 不落地，
并在 `build.gradle.kts` 就地记明原因，避免后人重蹈。**滑杆手势仍只能真机验证。**

### 注意（需真机确认）

本版改动了提醒调度的生产代码（只加了有默认值的参数，行为应完全不变）。
请重点确认**提醒仍然照常触发**：打卡一条药、进药单改一支药的时刻，
然后确认到点提醒与 +30 分钟升级提醒都正常。

## [v1.0.52] — 2026-09-23

**「计划用药时间」改用真正的时间选择器（不再给固定候选），且始终显示当前已设时刻。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.51 全部内容。**

### 背景

v1.0.51 给注射类补上了「计划用药时间」，但用的是一排固定候选 chip
（06:30 / 08:00 / 12:00 / 18:00 / 21:00）+ 一个「自定义 HH:mm」输入框。用户反馈两点：

1. **不要固定候选，要一个单独的时间选项**；
2. **编辑已有药品时要能显示当前设置的时间点**——第 2 点其实是旧实现的一个真实缺陷：
   当前值是用 chip 的**选中态**表达的，只要存的时刻**不在那 5 个候选里**（如 07:30），
   编辑页上**没有任何 chip 被选中**，用户看不到自己设过什么，也无从判断该不该改。

### 修复

- **改用 M3 `TimePicker`（24 小时制）**，点开即选任意时刻；固定候选 chip 与
  「自定义 HH:mm」输入框一并删除（`med_custom_time` 字符串已无引用，随之移除）。
- **当前时刻始终以文本显示**：注射类只有一行（点它即改，`08:00` 这样的值直接看得见）；
  口服可多选，已选时刻逐个列出——**点它改、点 ✕ 删，另有「添加时刻」**。
- **选择器初始值 = 该行当前已设时刻**，新增 `ScheduleCalc.timeParts()` 容错解析
  （`07:30` 这类不在候选里的值当然能显示；脏数据 `25:99` 则夹回默认，不让 `TimePicker`
  收到 25 点 / 99 分）。
- **统一默认时刻为唯一常量** `ScheduleCalc.DEFAULT_PLAN_TIME`：此前表单默认写 08:00、
  而 `slotsFor` 兜底 09:00，两者不一致——编辑一支没存过时刻的老药时，表单显示的
  并不是它实际生效的时刻。现两处共用同一常量（值取 09:00，即原 `slotsFor` 的兜底）。

### 验证

- 单测 **339 条全过**（336 → +3：`timeParts` 如实解析任意已设时刻 / 解析不了与越界回退默认 /
  默认时刻本身合法，其中第一条即本次缺陷的回归锁）。
- `aapt dump badging` 核实 `versionCode=57 / versionName=1.0.52`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，可覆盖升级。

### 注意（需真机确认）

`TimePicker` 的**表盘**需要约 256dp 宽，放在 `AlertDialog` 里是官方推荐用法，
但在**很窄的屏幕**（≤320dp）上可能偏挤。请在真机上确认表盘完整可见、可拖动；
若确实偏挤，下一版改为底部弹层承载（弹层是整屏宽）。

## [v1.0.51] — 2026-09-23

**注射类药品补上「计划用药时间」（此前既设不了、也看不到）；口服那个字段改名，便于找到。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.50 全部内容。**

### 背景：用户问「计划用药时间在哪部分？我添加药品也没见到」

排查后确认这是**两个叠加的问题**，用户的感受是准确的：

1. **注射类药品根本没有「计划用药时间」入口**。添加/编辑药品表单里，时刻选择只在
   `route == "oral"` 分支中渲染；选「注射」后，表单只给「注射周期 + 周期锚点日期」
   （且仅 Q2W / 自定义周期才有），或一句说明。而 `buildMed()` 对注射仍会写
   `takeTimes = times.take(1)`——`times` 是表单默认值 `["08:00"]`，**用户从未见过它**。
   于是每一支注射药的计划时刻都被静默固定为 **08:00**。
2. **该时刻在全应用都无处可见**。今日卡的 chip 显示 `PlanSlot.label`，而注射槽位的
   label 被写死为「注射」（口服则是时刻本身），所以卡片上只有一个「注射」标签；
   药单行也只显示频次 + 「· 注射」。结果：**计划时刻只参与「是否晚点」的计算，从不显示**。

另外，注射表单里那句说明写的是「默认 09:00 提醒」，但实际生效的是 08:00（表单默认值），
**文案与行为不一致**——按说明去找也找不到能改 09:00 的地方。

### 修复

- **注射类新增「计划用药时间」**：把口服的时刻选择器抽成共用组件 `PlanTimePicker`，
  注射分支同样渲染（放在周期参数之后）。口服可多选（一天多次），**注射单选**——
  一针只有一个时刻，且单选态下点「已选中」的时刻**不做取消**，避免退化成「零个时刻」
  后 `take_times` 变 null、计划时刻静默回退到 09:00。
- **字段改名**：`服药时刻（本地提醒时刻）` → **`计划用药时间（到点提醒）`**。
  用户找的是「计划用药时间」这个词，原来的名字里没有它。
- **注射槽位 label 改为计划时刻**：今日卡上直接显示 `08:00` 这类时刻，
  与口服卡片一致；「注射」二字由卡片上原有的给药途径 chip 承担，不再重复。
- **修正不实文案**：「默认 09:00 提醒」→「提醒时刻取上方『计划用药时间』」。
- 顺带：自定义时刻的校验由 `\d{2}:\d{2}` 改为 `ScheduleCalc.TIME_PATTERN`（合法 00:00–23:59）。
  原来会放行 `25:99`——它被加进「已选时刻」看起来生效了，存库后又被 `takeTimesOf` 过滤掉，
  **用户以为设了时刻，其实那个槽位根本不存在**，且毫无提示。

### 验证

- 单测 **336 条全过**（332 → +4：注射槽位标签 = 计划时刻（Q2W 自定义时刻 / 无时刻回退 /
  BIW 两针）/ 口服槽位标签同为计划时刻，其中第一条即本次缺陷的回归锁）。
- `aapt dump badging` 核实 `versionCode=56 / versionName=1.0.51`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，可覆盖升级。

### 对既有数据的影响

已存在的注射药，其 `take_times` 仍是当初被静默写入的 `["08:00"]`（**没有丢数据**），
现在可以直接在编辑页看到并修改它。若不改，行为与之前完全一致。

## [v1.0.50] — 2026-09-23

**修复今日页「已服」后面显示一串数字（如「已服 19981」）。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.49 全部内容。**

### 缺陷

今日页已服药卡片上写着「**已服 19981**（晚于计划 30 分钟以上）」——`19981` 不是任何有意义的值，
它就是**小数秒的数字**被当成时刻显示了出来。

**根因**：`nowIso()` 用 `DateTimeFormatter.ISO_LOCAL_DATE_TIME` 写库，该格式在
**纳秒非零时会追加小数秒**，于是 `taken_at` 的串长在 19 / 23 / 26 之间浮动：

| 实际时刻 | 存库串 | 旧实现 `takeLast(5)` |
|---|---|---|
| 22:31:15.000000000 | `2026-09-23T22:31:15` | `31:15` ✅ |
| 22:31:15.019981 | `2026-09-23T22:31:15.019981` | `19981` ❌ |
| 22:31:15.199 | `2026-09-23T22:31:15.199` | `5.199` ❌ |

而展示侧是 `stringResource(med_taken_prefix) + takenAt.takeLast(5)`——**从串尾截 5 个字符**
来「取 HH:mm」。串长一旦浮动，截到的就是小数秒。已用 JDK 实测复现：
`LocalDateTime.of(2026,9,23,22,31,15,19981000).format(ISO_LOCAL_DATE_TIME)` →
`2026-09-23T22:31:15.019981` → `takeLast(5)` = **`19981`**（与截图完全一致）。

因为 `LocalDateTime.now()` 的纳秒几乎从不为 0，这个显示**长期就是坏的**，只是不报错、不影响逻辑，
所以一直没被发现（`isLate` 走的是 `LocalDateTime.parse`，解析正确，所以「晚于计划 30 分钟以上」是对的）。

### 修复

新增 `ScheduleCalc.hhmm(iso)`：**解析**后再格式化成 `HH:mm`，解析不了返回 null；
今日页改用它，且解析不出时刻时不再留下悬空的「已服 」前缀。

顺带把 `isLate` 里的 `java.time.LocalDateTime` 全限定名改为已 import 的短名（同文件内一致）。

**判据（已写入交接文档 §7）**：凡是要从 ISO 时刻串里取「时刻」，
必须**解析**或**从头截**（`take(16)` 取到分钟），**不能从尾截**——
`ISO_LOCAL_DATE_TIME` 的串长不固定。全库排查确认：其余取用点
（`WellnessScreen` 的 `take(16)`、`BackupScreen` 的 `take(19)`、`AttachmentPath` 的 `take(10)`）
都是**从头截**，安全；`TrendChart` 的 `takeLast(5)` 作用于纯日期串（无小数秒），也安全。

### 验证

- 单测 **332 条全过**（328 → +4：`hhmm` 秒精度 / 带小数秒（6·3·9 位）/ 前导零 / 非法输入，
  其中带小数秒那条即本次缺陷的回归锁）。
- `aapt dump badging` 核实 `versionCode=55 / versionName=1.0.50`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，可覆盖升级。

## [v1.0.49] — 2026-09-23

**用药记录可手动修正；注射部位改显示中文。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 ~ v1.0.48 全部内容。**

### 新增 1：用药记录里可以改（记错了手动修正）

v1.0.48 刚把「用药记录」做出来，但那是**只读**的——记错一条（比如明明是跳过却记成了已服、
注射部位选错）就只能将错就错。本版每条记录右侧加铅笔图标，可改：

- **服药状态**：已服 / 部分 / 跳过（此前 App 从不写「部分」，只有报表在读它，这次给了写入入口）；
- **原因**（状态为部分 / 跳过时出现，单选，含「其他 + 补充说明」）；
- **注射部位**（该药是注射类且状态为「已服」时出现）；
- **备注**。

**归属日与计划时刻是只读的**，弹层里明确写了原因：`(date, med_id, slot_key)` 是唯一索引，
改它们等于换一条记录，而 `upsert` 是 REPLACE 语义——**撞键会静默删掉被撞的那条**，
等于把用户的另一条记录吃掉。

### 新增 2：注射部位显示中文

记录里的注射部位此前显示的是**存库值**（`thigh_l`、`abdomen_r` 这种英文键），
因为「键 → 中文」这层映射只存在于「今日打卡」的部位选择器里（`TodayScreen` 的私有 `injSites()`），
药单看不到它。

现收拢为 [InjSite] 枚举（`key` = 存库值，`label` = 中文），选择侧与展示侧共用一份映射；
随之删掉了 `med_site_*` 这 6 条字符串资源（已无引用）与那个私有函数。
未知键（老数据 / 手工导入）回退显示原始字符串，**不猜成某个部位**——猜错等于替用户改了注射部位。

### 顺带：把「编辑」的不变量抽成纯函数

编辑是最容易破坏数据不变量的入口：把「跳过」改成「已服」若不把原因清掉，
报表里就会出现「已服 + 原因=遗忘」这种自相矛盾的行；反过来把「已服」改成「跳过」却不填原因，
就会出现一条无原因的跳过（违反红线三）。两者都会污染依从率口径。

故新增 `domain/MedLogEdit`，把「已服不留原因 / 部分·跳过必填原因 / 部位仅已服有意义」
写成纯函数，弹层只负责收集、不自己发明规则；保存按钮也据此禁用（没选原因就不给存）。

### 验证

- 单测 **328 条全过**（315 → +13：编辑不变量 9 条 + 注射部位映射 4 条，
  含「存库键稳定性」的回归锁——改了键历史记录就再也映射不到中文）。
- `aapt dump badging` 核实 `versionCode=54 / versionName=1.0.49`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，可覆盖升级。

## [v1.0.48] — 2026-09-23

**药单新增「用药记录」与「已停用药品」——把此前查不到的用药流水补上。**

⚠️ **无数据库结构变更**（仍为 Room v15，只新增只读查询），可覆盖安装。
**含 v1.0.44 / v1.0.45 / v1.0.46 / v1.0.47 全部内容。**

### 背景：药品此前看不到「流水」

补剂早就能逐条回看（营养页点某个补剂 → 近 90 天服用记录），**药品却只能看报表汇总**：
看不到「哪天哪一次打了没有、跳过的原因是什么」。而药单只列**在用**药品，一停用就查无此药，
停用时填的原因也没地方回看。本版补齐这两块。

### 新增 1：点药名 → 「用药记录」弹层

- 药单里点任一条药（含已停用的）→ 弹出近 **90 天**逐条记录，倒序：
  **日期 + 状态（已服 / 部分 / 跳过）+ 计划时刻 + 注射部位 + 跳过原因 + 备注**；
- 顶部给出该药 90 天**依从率**与完成/部分/跳过拆分，配色阈值与报表同一套（90 / 70）；
- 状态「跳过」用**中性色**而非红色——遵医嘱暂停不算「错误」，不该报警；
- 药名后带一个历史图标作可点提示（否则「点药名能看记录」完全不可发现），
  并给 `clickable` 设了 `onClickLabel`，读屏会念出「查看用药记录」。

### 新增 2：药单底部「已停用药品」折叠区

- 显示**停药日期 + 停药原因 + 备注**（原因取自 `medication_changes` 的 stop 记录——
  它不在 `medications` 表上，那里只有 `is_archived` 一个布尔）；
- 点任一条同样可打开该药的用药记录（归档不改写历史日志，所以停用前的流水能查全）；
- 默认收起；**无归档药时整段不显示**（「已停用（0）」只是噪声）；
- 该区放在列表层级而非「在用」分支内——**全部药都停用时它仍要显示**，
  否则停药后就再也找不到那条药了；
- 查不到停药变更记录时只写「停用原因无记录」，不臆测原因（老数据 / 恢复的旧备份）。

### 顺带：把依从率公式收拢成唯一实现

「依从率 =（完成 + 部分×0.5）÷ 已打卡数」这句此前在 `ReportRepository` 内联了 **4 遍**
（概览的用药与补剂、周月报的用药与补剂），本次药单又要第 5 遍——同一指标写在多处必然漂移
（改一处忘一处，两个页面给出不同百分比）。现收拢为 `domain/AdherenceCalc`，报表四处一并改用它。

口径未变：只在**已打卡**的槽位上计算（未打卡不计入，不惩罚漏记）；部分完成按 0.5 计；
**无打卡时返回 0 而非 100**（「没有记录」不能被显示成「完全依从」）。

### 验证

- 单测 **315 条全过**（305 → +10：依从率与汇总口径，含「与收拢前内联公式逐例对齐」的回归锁）。
- `aapt dump badging` 核实 `versionCode=53 / versionName=1.0.48`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，可覆盖升级。

### 过程中踩到并已修掉的两个编译期错误（都是真问题，不是风格问题）

1. **同名遮蔽**：新对象命名为 `Adherence` 时，`ReportRepository` 内部的嵌套 `data class Adherence`
   （汇总 DTO）会**遮蔽**它，导致仓库里 `Adherence.ratePct` 解析失败。改名 `AdherenceCalc`
   （后缀风格与既有 `ScheduleCalc` 一致），并把原因写进该对象的 KDoc，避免后人再踩。
2. **`Modifier.weight` 越域**：把行内容抽成独立 composable 后 `RowScope` 不在作用域内，
   `Modifier.weight(1f)` 编译失败。改为 `RowScope.ArchivedRow` / `RowScope.MedicationLogRow` 扩展。

### 教训

- **同名遮蔽是静默的**：`import` 不会报冲突，只是让调用点解析到另一个类型——错误信息
  （「Unresolved reference 'ratePct'」）指向成员名而不指向遮蔽本身，容易误判成「新文件没编译」。
  给新类型起名前，先 `grep` 一下同名符号是否存在（含嵌套类）。
- **抽 composable 会丢掉作用域**：`weight` / `align` / `matchParentSize` 这些
  `RowScope` / `ColumnScope` 成员，一旦把内容搬进独立函数就失效——需要把函数声明成作用域扩展，
  或者保留内联。这也解释了为什么补剂那套历史是内联写的。

## [v1.0.47] — 2026-09-22

**修复打分滑杆「完全无法拖动」（v1.0.17 引入，已存在 29 个版本）。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 / v1.0.45 / v1.0.46 全部内容。**

### 缺陷

临床评分滑杆（BASDAI 自评、症状与自评里的 0–10 打分）**横向拖动完全无反应**，
只能靠 +/− 按钮或点一下轨道改值。

**根因**：v1.0.17（`d546494`，提交信息为「BASDAI滑杆0直选（旁听手势）」）为了修「点按落在当前值
上不回调」，在滑杆**同级**叠了一层 `matchParentSize()` 的透明覆盖层并挂上 `pointerInput`。
但 Compose 的命中测试规则是——**同一层级上只有 z 序最高的可组合项算命中**
（[官方文档·事件调度和点击测试](https://developer.android.google.cn/develop/ui/compose/touch-input/pointer-input/understand-gestures)）：

- 覆盖层（后绘制 → z 序更高）**独占了命中**，滑杆自身**收不到任何指针事件**，拖动因此彻底失效；
- 覆盖层自己只处理点按（判定「位移小于阈值」才提交），拖动它什么也不做 → 拖了等于没拖；
- 点按之所以一直正常，是因为覆盖层自己实现了点按 → **这正是缺陷潜伏 29 个版本未被发现的原因**；
- 原注释里「从不 consume，所以与滑杆互不干扰」的前提是错的：**消费与否发生在命中测试之后**，
  没被命中就谈不上消费。

### 修复（方案 B：把旁听手势从「同级覆盖层」改为「祖先旁听」）

- **删除同级透明覆盖层**，滑杆恢复为父 `Box` 的直接子项 → 拖动恢复（子节点正常收到事件）。
- **点按旁听改挂在滑杆的父 `Box`（祖先）上**：祖先与子节点同处一条命中链，
  子节点先处理事件，父节点只旁听、**从不 consume**，这才是旁听手势的正确写法。
- 旁听只补 M3 滑杆**不产生任何回调**的那一种情形（点按落在当前值上，`dispatchRawDelta` 值相等
  即丢弃），即落点换算值恰等于当前值时才显式提交，用于把该题标记为已作答。
  落点值不同时 M3 自带的点按跳转已经提交过，此时不再写第二次——避免与 M3 的坐标换算打架
  （两者对同一点的换算可能差一格，重复写会造成「点拇指却跳一格」）。
- 顺带修掉原旁听逻辑的一处判定缺陷：拖动判定从「逐事件位移 > 触摸阈值」改为
  **「相对按下点的累计位移 > 触摸阈值」**——慢拖时单个事件的位移可能始终小于阈值，
  旧判定会把拖动误判成点按。

### 关于 M3 滑杆「点按跳转」的核实（不靠猜）

反编译本地 material3 **1.3.0**（BOM 2024.09.03）确认：

- `sliderTapModifier(Modifier, SliderState, MutableInteractionSource, boolean)` **存在** →
  滑杆自带点按跳转，删掉覆盖层不会丢「点一下改值」的能力；
- 但 `SliderState.valueFromOffset` 与 `rememberSliderState` **不是公开 API**
  （反编译仅见 `getCoercedValueAsFraction$material3_release` 等 internal 成员），
  因此旁听层无法复用 M3 的精确换算，只能用「按宽度比例换算 + 仅补 M3 不响应的情形」这一公开 API 方案。

### 已知残留（可接受，且有兜底）

点按位置恰落在「本层比例换算」与「M3 轨道换算」相差一格的窄带上、且 M3 恰好算出当前值时，
该次点按无动作（需再点一次）。0 / 满分两端不受影响——v1.0.18 起「无 / 最严重」两个标签可点，
是 100% 可靠的入口。

### 验证

- 单测 **305 条全过**（本缺陷属手势命中行为，纯 JVM 单测覆盖不到；见下「待办」）。
- `aapt dump badging` 核实 `versionCode=52 / versionName=1.0.47`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，可覆盖升级。
- **待办**：补一条 Compose UI 回归测试（Robolectric + `performTouchInput { swipe() }` 断言滑杆值
  真的变化）。本次未落地——Robolectric 需在测试期联网下载 `android-all-instrumented` 大包，
  本机 `api.github.com` / Maven 当晚多次抖动，故先交付真机验证版，测试项记入 HANDOFF §8。

### 教训

- **「不消费事件」不等于「不干扰」**：Compose 的命中测试在事件分发**之前**完成，
  同级重叠时只有 z 序最高的可组合项进入命中链。想在别人的手势上「旁听」，
  必须挂在**祖先**（同处命中链）上，不能挂在**同级**覆盖层上。
- **覆盖层的隐蔽性来自「它自己也能用」**：覆盖层实现了点按，于是「点一下能改值」让缺陷看起来
  像「只是拖不动」。评估手势缺陷时要逐条列出**每种手势路径**（拖动 / 点按 / 长按 / 两端标签），
  而不是「能改值就算正常」。
- **潜伏期长 ≠ 改动新**：本缺陷横跨 29 个版本，只有 `git log -S` 定位到引入点才敢下结论；
  「我记得之前可以」是有效线索，但必须用提交历史坐实到具体版本。

## [v1.0.46] — 2026-09-22

**v1.0.44 / v1.0.45 的代码审查修复：v1.0.45 趋势图改造的 4 处缺陷 + 1 处竞态。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。**含 v1.0.44 / v1.0.45 全部内容，
两者（均为未验证预发布）请直接跳过本版。**

> 审查范围：v1.0.43（`b4dd74f`）已被第三轮审查报告逐条核验过，本轮审其后的全部增量——
> v1.0.44（N1–N3 / S3–S5 落地，核对无误）与 v1.0.45（趋势页改造，**4 处真缺陷全部在后者**）。

### 修复（均为 v1.0.45 引入的缺陷）

1. **拖动读数选错点**。X 轴改为按日期间隔定位后，`nearestIndex` 仍按「序号比例」映射——
   日期间隔不均时（如偏移 [0,1,2,20]），手指按在 60% 宽度处会选中画在 10% 位置的点，
   读数气泡跳到离手指很远的地方。改为与绘制**同一套日期定位**取最近点（新增 3 条回归单测）。
2. **入场动画期间末点数值提前出现**。折线从左到右扫出时，末点圆与数值气泡无条件绘制，
   会提前悬在终点位置。改为动画播完后（`visible >= points.size`）才画。
3. **阈值标签在量程边缘垂直溢出**。阈值恰为最大/最小值时标签会画出画布——旧 chip 实现有
   clamp，v1.0.45 重写时丢了，补回（`coerceIn(top, bottom - 字高)`）。
4. **`xAt` 对未排序日期无防御**。中段出现比末点更晚的日期会画到绘图区右边界之外
   （数据层目前均升序，纯防御性 clamp）。
5. **`setTrendDays` 过期响应竞态**。快速连点 7→30 天时，若 7 天查询后返回，会把图换成旧窗口
   数据而 chip 仍显示 30 天。查询返回后校验 `_trendDays` 未变才回写。

### 加固与清理

- **正则守卫补覆盖面告警**：`RegexLiteralGuardTest` 此前只认 `Regex("字面量")` 形态，
  出现 `.toRegex()` / `Pattern.compile` 会**静默漏检**。现改为大声失败（提示先扩展提取器），
  并修掉提取器把 `.toRegex()` 误当 `Regex(` 抓取的隐患。实测主源码当前零使用。
- 删除死资源 `report_activity_level`（v1.0.45 移除阈值 chip 文案后唯一引用即消失，全仓 0 引用）。

### 审查中核对无误的部分（未改动）

- v1.0.44：`doneSlotRefs` 唯一实现 + 无默认值、`KbSeedRefresh` 增量刷新判定（含 user_note
  保留与字段级比对）、`CrashLogger` 凭据脱敏、`VaultKeyStore` ABSENT 守卫、`BackupEngine.asNumber`——
  均与第三轮报告的建议一致，实现正确。
- v1.0.45：`dayOffsetsOf` 同日多点的堆叠行为**核实为安全**（`saveVitals` 同日仅保留最新一条，
  其余序列也均一日一行）；`niceScale` 预算下限 2 的数学依据成立（单测覆盖）。
- 预存问题（**不在本轮范围，仅记录**）：`setPeriodDays`（v1.0.38）存在与第 5 条同型的竞态，
  影响低（本地查询极快），未动。

### 验证

- 单测 **305 条全过**（新增 3 条 nearestIndex 回归：日期定位命中 / 无偏移回退 / 越界 clamp）。
- `aapt dump badging` 核实 `versionCode=51 / versionName=1.0.46`；release APK 验签
  SHA-256 `38CA80A6…012D7D`，可覆盖升级。

### 教训

- **「改了绘制定位，就必须同步改命中测试」**：X 轴换成日期定位后，拖动命中还停在序号映射——
  两套坐标系并存必然选错点。凡是「同一几何有两处消费」的，要么抽一个函数共用，要么在单测里
  把两者钉在一起。
- **重写比新写更容易丢细节**：v1.0.45 重写 TrendChart 时丢了旧实现的阈值边缘 clamp——
  重写前应先列出旧实现的**防御性分支清单**（coerce / clamp / fallback），逐条核对去向。
- **守卫类测试要防「覆盖面悄悄变窄」**：正则守卫只认一种构造形态，新代码用 `.toRegex()` 就
  完全绕过。守卫必须对「已知未覆盖的形态」大声失败，而不是假装看不见。

## [v1.0.45] — 2026-09-21

**趋势页改造（方案 A：精准修复）+ 时间范围 7 / 30 / 90 天切换。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。仅新增一个只读查询，不涉及迁移。

### 1. X 轴改为**按日期真实间隔**定位（此前在骗人）

旧实现按「第几个点」等距铺开（`i / (size-1)`），于是**「隔 3 天测一次」与「隔 3 小时测一次」
画出来一样长**——一条平坦的线可能只是这几周没记录，而不是病情平稳。
现在按各点相对首点的**天数偏移**定位；任一日无法解析为 ISO 日期时**回退等距**（不会因一行脏数据
整张图不出）。跨年、同一天多条记录都能正确处理。

### 2. 刻度数按**可用高度与实测字高**自适应

旧实现固定按 `(hi - lo) / 4` 取 4 段，与画布高度、系统字号都无关——矮画布或大字号下
5 个以上的刻度标签必然互相压叠。现在按「画布净高 ÷ (实测字高 + 间隙)」算出预算（2~5 段），
再从 nice 阶梯（1 / 2 / 2.5 / 5 / 10 × 10ⁿ）里挑满足预算的步长。

### 3. 画布高度确定化

`heightIn(min = chartHeight)` 改为 `height(chartHeight)`：不再依赖父级约束是否给足，
消除卡片里的大片不明空白（原实现的实际绘制高度会随父级约束浮动）。

### 4. 数据点可见 + 末点直接标数值

每点画标记、末点高亮并在旁边标出数值——**不拖动也能读到当前值**。
点过密时（相邻间距 < 3 倍点半径，如 90 天日更）自动只保留末点，避免圆点糊成一条粗带。

### 5. 阈值标签移出数据区

原先阈值 chip 画在绘图区**右侧、压着数据**。现在进入**左侧轴槽**（与 Y 刻度同列），
且其宽度参与轴宽计算；当某刻度与阈值同高时，让位给阈值标签（避免两层文字叠在一起）。
BASDAI 的语义（「阈值以上为高活动度」）由卡片副标题承担，轴上只留数值。

### 6. 图上方读数摘要行

新增「当前值 + 较首次变化」与「均值 · 超阈值 n/m 次」。方向**刻意不做配色**——
同一段组件既画 BASDAI 也画体重，「升」并不总是坏。

### 7. 时间范围 7 / 30 / 90 天切换

- 趋势页顶部固定一排 FilterChip（不随图滚走）；
- `ReportRepository.trends(rangeDays)` 窗口参数化，四个序列统一闭区间 `[to-(n-1), to]`；
- **顺手修了两处口径不一致**：体重原先用 `recent(60)` 取「最近 60 条」而与窗口无关
  （切到 7 天视图仍会带回更早数据）→ 改为按日期区间查询；BASDAI 原先 `.takeLast(12)`，
  90 天视图会被**静默截断**成 12 个点 → 取消该上限。
- 页面副标题去掉写死的「近 30 天」（该数字只对概览页成立，趋势页现在可切换）。

### 验证

- 单测 **302 条全过**（新增 12 条：真实日期偏移 / 跨年 / 脏日期回退 / 日期中点取刻度 /
  自适应刻度间隔数不超预算 / 阈值必入量程 / 读数口径与严格大于判定）。
- `aapt dump badging` 核实 `versionCode=50 / versionName=1.0.45`；
  release APK 验签 SHA-256 `38CA80A6…012D7D`，可覆盖升级。

### 说明与教训

- **我无法从源码解释用户截图里「图被压扁成一条黑带」的观感**：`chartHeight = 200.dp`
  自查证以来（v1.0.7）从未变过，v1.0.43 打包时也是 200dp，按此推算刻度间距本不该互压。
  因此本版没有去「猜一个原因」，而是把**刻度间距改为按实测字高与净高计算**、
  **高度改为确定值**——让这类挤压在结构上不可能发生；最终观感仍需真机核对。
- **等距铺点是一种「沉默的谎言」**：它不报错、不崩溃，只是让时间轴失去意义。
  凡是「时间序列」的图，横轴必须由日期决定，不能由数组下标决定。
- 固定刻度段数（`(hi-lo)/4`）与画布高度解耦，是把「布局可用性」写死在了数据侧——
  刻度数量应当由**可用高度 ÷ 单标签高度**导出，而不是由数据范围导出。

## [v1.0.44] — 2026-09-21

**第三轮审查报告的 N1–N3 修复 + S3/S4/S5 加固 + 正则花括号静态守卫。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。

### N1 开机广播重排漏传「已打卡槽位」→ 重启后误提醒

v1.0.43 给 `rescheduleAll` 加了 `doneSlotRefs`，但**给了 `emptySet()` 默认值**，于是 4 个调用点里
开机广播那条（`BootReceiver`）漏传：手机重启若落在「已打卡槽位的 +30 / +60 尚未到点」窗口内，
会给**已经吃过药**的槽位重建升级重查 → 用户收到「未服药」误提醒。

修复分两层：
1. 把「今日已打卡槽位」的构造抽成 `MedicationRepository.doneSlotRefs(date)` 一处实现
   （此前在 Application / TodayViewModel / MeViewModel 各写一遍，漏改是必然）；
2. **删掉 `doneSlotRefs` 的默认值**——任何新增调用点都必须显式想一次「今天哪些槽位已完成」。
   这正是「把纪律升级为代码」：默认值让「忘记」变成了合法编译。

### N3 种子 `count>0` 短路 → 老设备永远拿不到种子增补与修订

原实现 `if (dao.count() > 0) return`：库里只要有任意一条就整体跳过。后果是**任何早于首次导入的
设备，此后所有种子增补都进不来**（v1.0.20 以来知识条目多次扩充），而且完全静默。属结构性数据缺口。

改为**版本化增量刷新**（`KB_SEED_VERSION` 闸门 + 按 id 比对）：
- 本机缺失的种子 → 补入（固定 id + `INSERT IGNORE`，幂等）；
- 内容被修订的 → 只更新**种子列**，并把 `user_note` 从旧行拷回——个人备注层永不因种子更新被覆盖；
- 修订判定既看条目 `version`，也做**字段级比对**（安全网：改了文案却忘记 bump version 时仍能刷新）；
- 顺带修复「`search_text` 未回填」的历史行（旧备份恢复后可能为 NULL）。

判定逻辑放在 `domain/KbSeedRefresh`（纯函数）以便单测；导入动作依赖 Context，测不到，故只留执行。

**这条一并了结了 v1.0.43 遗留的 exc-004 文案残留**：老设备这次会拿到改写后的文案。

### N2 README 过期信息（比报告指出的多两处）

- `./gradlew ...` **在本仓库根本不存在**（无 wrapper 脚本），三条命令全是跑不了的 → 改为 `gradle`
  并说明版本要求与 wrapper 缺失；
- 测试数两处互相矛盾（`114 条` / `205 条`）→ 统一为实测值；
- 版本号 `v1.0.35 (40)` → 更新。

### S3 / S4 / S5 加固

1. **S3** `VaultKeyStore.generate()` 加 `require(state() == ABSENT)`：把「不要覆盖已有密钥」
   从注释升级为机器约束。该方法本就是「覆盖落盘」语义，与 B5 修掉的是同一类威胁——
   同一文件里留一个无防护的覆盖入口逻辑上自相矛盾。
2. **S4** `BackupEngine` 的 `(v as Number)` 改走 `asNumber(table, col, v)`：INT/REAL 列收到非数值
   时抛 `BackupException("备份数据类型不符：表.列 …")`，而不是让裸 `ClassCastException` 被外层
   包成「恢复写入失败」（安全性不变，仍在事务内整体回滚；只是可定位性大幅提升）。
3. **S5** `CrashLogger` 落盘前做**凭据脱敏**：`scheme://user:pass@host` → `scheme://***@host`。
   留档只写 app 私有目录（`allowBackup=false`）且永不上报，但医疗类应用应主动划清边界——
   凭据一律不入盘。Logcat 那一路仍是原始堆栈（瞬时、非特权读不到）。

### S1 / S2 可行性验证：**Robolectric 方案被实测证伪**

第三轮审查建议「用 Robolectric 触发含正则的 object 初始化即可暴露 v1.0.39–42 的崩溃」。
该建议依赖一个未经验证的前提：Robolectric 里的 `java.util.regex.Pattern` 走 Android ICU 语义。

**实测结论：不是。** 装上 Robolectric 4.13（依赖可正常解析，Maven Central 可达），
用**当年的真实缺陷模式**做探针，在 sdk=34 下得到：

```
PROBE sdkInt=34 java=21.0.12.1
PROBE_RESULT=JVM_LIKE          ← 含孤立 `}` 的模式正常编译通过
PROBE_SANITY_QUANTIFIER_OK=true
```

即该方案对本类 bug 的**检出率为 0**——「JVM 语义全绿」正是当年真机崩溃的根因。
探针已删除（未留在交付代码里）。

**改用静态守卫**（S2 的实际落地）：新增 `RegexLiteralGuardTest`，扫描主源码里的正则字面量，
只允许 ICU 也接受的合法量词 `{\d+}` / `{\d+,\d*}`，其余花括号一律判失败；
字符类 `[...]` 内与 `\X` 转义对内的花括号视为字面量（不误报）。
它随 `testDebugUnitTest` 一起跑，**CI 无需改动**即获得这道防线，且用历史缺陷模式做了回归用例。

### 验证

- 单测 **290 条全过**（新增 22 条：种子增量刷新判定 8 / 崩溃留档脱敏 6 / 正则花括号守卫 8）。
- release APK 验签：证书 SHA-256 `38CA80A6…012D7D`，与历史版本一致，可覆盖升级；
  `aapt dump badging` 确认 `versionCode=49 / versionName=1.0.44`；debug dex 中可见
  `KbSeedRefresh` / `scrubCredentials` / `kb_seed_version` 等新符号。

### 教训

- **给「安全相关」的参数配默认值，等于允许调用方忘记它**。`doneSlotRefs = emptySet()` 让漏传
  成了合法编译，于是 4 条路径漏了 1 条。凡参数缺失会导致**静默错误行为**（而非编译失败）的，
  一律不给默认值。
- **「装上工具」不等于「工具能解决这个问题」**：Robolectric 能装、能跑、能模拟 `AlarmManager`，
  但它**不模拟 ICU 正则**。可行性验证必须用「目标 bug 的真实样本」去测检出能力，
  而不是测「依赖能否安装」——后者是必要不充分条件。
- 同理，`java.util.regex` 的方言差异（v1.0.42）**只能靠静态检查或真机**发现；
  任何「在 JVM 上跑一遍」的方案（含 Robolectric）对它天然免疫。这道结论已固化为
  `RegexLiteralGuardTest` 里的守卫。

## [v1.0.43] — 2026-09-21

**代码审查后的 A / B 类问题集中修复，外加口令框「长按显明文」。**

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。

### A 类：功能性缺陷

1. **A1 冷启动把当天的升级提醒链清空**。`ReminderScheduler.rescheduleAll` 每次冷启动都
   `cancelAllFuture` 后只重排 `esc=0`，于是「当天已过点但**尚未打卡**」的 `+30 / +60` 追问
   被一并清掉——用户点「稍后提醒」后再冷启动，就再也收不到追问。
   现改为：调用方传入「今日已打卡槽位」集合（`slotRef = medId|slotKey`），对当天**已过点且未打卡**
   的槽位重建**尚未到时**的升级链。`MAX_ESCALATION` / `ESCALATION_STEP_MINUTES` 上提到
   `ReminderScheduler`，`ReminderReceiver` 复用同一份常量，避免两处漂移。
2. **A2 恢复码状态误报**。`hasRecoveryCode()` 原用 `vaultPrefs.contains(...)`，只要键存在就认为
   「已设置」，与「能否解密 / 是否真的写入备份」脱节。改为 `recoveryCode() != null`。
3. **A3 编辑药品可能永久挂起并产生重复药**。编辑页原用 `vm.meds.first { it.id == editId }` 从
   「在用药品流」取记录：药一旦停用 / 归档，`first` 永不返回 → 页面卡死 → 用户重试保存即产生重复。
   改为按 id 直查（`medicationById`，不限「在用」）；查不到时明确提示并禁用保存；
   保存时保留 `isArchived`，**避免把已归档的药重新激活**。
4. **A4 附件删除确认可能串行**。`AttachmentSheet` 列表未加 `key()`，`DestructiveAction` 的
   `remember` 确认态会串到相邻行。补 `key(a.id)`（含导入项）。

### B 类：健壮性与安全加固

1. **B1 远端附件路径校验收紧**。`AttachmentPath` 新增严格段白名单（`[A-Za-z0-9._-]+`，排除
   `% ? #` 与空白，防 URL 编码穿越与查询串注入）；`isManagedRemotePath` 直接复用该校验；
   `WebDavClient.requirePath` 改用 `isManagedRemotePath`。
2. **B2 备份恢复列名白名单**。`BackupEngine.insertTable` 对备份文件中的列名做白名单校验，
   未知列直接报错（原 `colTypes[col] ?: "TEXT"` 会把任意列名拼进 SQL）。
3. **B3 https 白名单**。`requireHttps` 改为显式 `https://` 前缀白名单；`WebDavClient.url()`
   自校验协议，非 https 抛 `DavException`（纵深防御，防止绕过配置层直接构造 URL）。
4. **B4 KDF 参数上下界**。`VaultCipher` 对迭代次数加 `[1e3, 1e7]` 区间校验；v1 格式补齐
   salt / iv 长度校验，异常统一转 `VaultException`（防畸形备份触发超大 KDF 迭代 DoS 或越界）。
5. **B5 本机附件密钥不再静默轮换**。`VaultKeyStore` 区分 `ABSENT / PRESENT / UNREADABLE`：
   密钥存在但**无法解密**（系统密钥库异常 / 换机丢失）时**不再生成新密钥**，而是抛出可操作错误
   「请先恢复一份本机或云端的 v3 备份以取回密钥」。原行为会静默换新密钥，导致云端已上传的附件
   **永久不可解**。

### C0：口令框长按显明文

WebDAV 配置 / 本机备份 / 恢复三处口令框统一封装为 `SecretField`：默认掩码，右侧「眼睛」图标
**长按**显示明文、松手立即恢复掩码（带无障碍描述）。用于输错口令时当场核对，避免「输错即不可挽回」。

### 验证

- 单测 **268 条全过**（新增 3 条附件路径校验用例：百分号编码穿越 / URL 分隔符与空白 / 裸 `.enc`）。
- release APK 验签通过：证书 SHA-256 `38CA80A6…012D7D`，与历史版本一致，可覆盖升级。

### 教训

- 「只重排未来」的清理逻辑很容易顺手把「今天稍后还要用的」一起清掉——重排类函数必须显式区分
  **已过点未完成** 与 **未到点** 两种状态，并把「已完成」作为入参传入。
- 从「在用」流里按 id 取单条记录做编辑，天然会在记录被归档后挂起；编辑态一律走**按 id 直查**。

## [v1.0.42] — 2026-09-21

**真正修复「康复计划 → 添加模板」闪退**（v1.0.41 的 R8 结论是错的）。

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。

### 真因：正则方言差异（Java 通过 / Android 报错）

v1.0.41 改进后的崩溃留档一次就给出了完整链条（这正是 v1.0.41 那两处留档改动的价值）：

```
java.lang.ExceptionInInitializerError
Caused by: java.util.regex.PatternSyntaxException: Syntax error in regexp pattern near index 83
```

`ExercisePlanTemplates` 的 `WEEK_RE` 模式长度为 84，**index 83 正是末尾那个未转义的 `}`**：

```
\{"week":(\d+),"grade":"((?:[^"\\]|\\.)*)","days":(\d+),"note":"((?:[^"\\]|\\.)*)"}
                                                                                  ^ index 83
```

- **Java 的 `java.util.regex.Pattern`** 把孤立的 `}` 当普通字符 ⇒ 编译通过
  （所以我先前在 JVM 上「验证通过」是**误导性**的，并据此得出了错误的 R8 结论）；
- **Android 的 ICU 正则引擎**视其为**语法错误** ⇒ `Pattern.compile` 抛 `PatternSyntaxException`。

于是：类初始化失败（`ExceptionInInitializerError`）→ 启动期被兜底吞掉 → 点「添加模板」二次触碰
抛 `NoClassDefFoundError: K1.n` → 闪退。**与 v1.0.39 的冷启动闪退是同一个根因。**

### 修复

1. **`ExercisePlanTemplates.parse` 移除正则，改为逐字符扫描**（先按深度配平找对象边界——字符串内的
   转义与花括号不参与配平——再按字段名取值）。该 JSON 由本对象自己产出、格式固定，无需正则。
   ⇒ **不再依赖任何正则引擎的方言差异。**
2. `esc` 一并转义 `\n` / `\r` / `\t`，保持单行且可逆（旧数据仍可解析）。
3. v1.0.41 的两条 `proguard -keep` **保留**（属防御性，与本根因无关），注释已更正为如实描述。
4. 复核全项目其余 16 处 `Regex(...)`：**仅此一处含孤立 `}`**，其余为成对量词（`{4}` / `{2}`）或
   不含花括号，无需改动。

### 验证

- 单测新增「说明含花括号 / 换行 / 制表」往返用例（旧正则版会漏配或崩）。
- 装机：康复计划 → 添加模板，应提示「已添加 3 个模板」且不闪退。

### 教训

- **JVM 单测无法覆盖正则方言差异**：解析「自己产出的固定格式」时优先手写解析；
  必须用正则时，避免孤立 `{` / `}` 这类 Java 与 ICU 语义不一致的写法。
- 真机崩 + JVM 测试全绿 + 异常为 `NoClassDefFoundError` 时，**不要急着归因 R8**：
  先用 `mapping.txt` 反查类名，再拿**首因异常**（`Caused by`）说话——这正是 v1.0.41 补上
  `recordNonFatal` 与「摘要带 Caused by/栈帧」后，一轮就定位的原因。

## [v1.0.41] — 2026-09-21

**修复「康复计划 → 添加模板」闪退**（真机留档：`java.lang.NoClassDefFoundError: K1.n`）。

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。

### 根因（v1.0.40 只是掩盖，并未修复）

用本次 release 的 `mapping.txt` 反查混淆名：`K1.n` = `com.ashkb.app.domain.ExercisePlanTemplates`
（B7 周期康复计划模板对象）。逐层排除：

- 该类**及其嵌套类 `$Template` / `$WeekSpec` 都在 dex 里**（`dexdump` 列出全部 4022 个类定义，
  `LK1/n;` / `LK1/l;` / `LK1/m;` 均存在）⇒ 不是「被裁剪」；
- 类内 `<clinit>` 只有 `listOf(...)` 与 `Regex(...)`，且 **JVM 单测 `ExercisePlanTemplatesTest` 全绿**
  ⇒ 不是逻辑错误。

R8 产物 `usage.txt` 显示该类的 `INSTANCE` 字段被删除、`pending` / `toJson` / `esc` / `unesc` /
`targetDays` 被**静态化**，`<clinit>` 亦进入处理清单；同时唯一以 `ExercisePlanTemplates.WeekSpec`
作签名类型的 `ExercisePlanProgress` 被 R8 **整类删除并内联**。
⇒ **R8 对 Kotlin `object` 单例（含嵌套 data class）的激进优化**，产出的类在 ART 上无法完成初始化。

失败链条（与真机现象完全吻合）：

1. **启动时** `AshkbApplication` 调用 `seedExercisePlans()` 首次触碰该类 → 初始化失败；
2. 该异常被 v1.0.40 新增的启动兜底 `runCatching{}.onFailure{Log.w}` **静默吞掉** → 进程存活，
   但 ART 已把该类标记为「错误状态」；
3. 点「添加模板」二次触碰 → 抛 `NoClassDefFoundError` → 未捕获 → 闪退。

⇒ **v1.0.40 的启动兜底把 v1.0.39 的冷启动闪退"藏"了起来**（崩溃从冷启动挪到了点击），
并非真正修复；此前将 v1.0.39 闪退归因于「数据库迁移竞态」的判断是**错误的**。

### 修复

1. **`proguard-rules.pro` 完整保留该组领域类（治本）**：`ExercisePlanTemplates` 与
   `ExercisePlanProgress` 及其嵌套类 `-keep { *; }`，禁止裁剪 / 优化 / 改名。
2. **启动期被吞掉的异常也留档**：新增 `CrashLogger.recordNonFatal()`（写 `last_nonfatal.txt`），
   `AshkbApplication.onFailure` 除 Logcat 外同步留档——不再丢掉「首次失败」。
3. **崩溃摘要带 `Caused by` 与栈帧**：release 包类名经混淆，原先按 `at com.ashkb` 过滤在混淆后
   恒为空、摘要退化成一行；现取「异常首行 + Caused by 首行 + 前 4 帧」，可用 `mapping.txt` 反查。

### 验证

- 单测 264 全绿；release 构建后复核 `usage.txt`：该组类不再出现在 R8 处理清单中。
- 装机：康复计划 → 添加模板，应正常种入 4 / 8 / 12 周模板且不闪退。

## [v1.0.40] — 2026-09-21

**热修：v1.0.39 冷启动闪退**，并加入崩溃日志留档，便于定位此类问题。

⚠️ **无数据库结构变更**（仍为 Room v15），可覆盖安装。

### 根因

v1.0.39 在 `AppShell` 组合期**即时创建** `RecipesViewModel` / `ExercisePlansViewModel`。这两个 VM 的
属性初始化会立刻建立 Room `Flow`（进而触发**数据库首次打开**），于是**主线程**与
`AshkbApplication` 启动协程（IO）**并发打开数据库并执行 v14→v15 迁移**——首次打开与迁移被两个线程
同时触发，是本次冷启动闪退的来源（v1.0.38 无迁移，同样并发但无害）。

### 修复（三层，覆盖全部冷启动失败面）

1. **两个新 VM 改为「进页面才创建」**：`viewModel(factory = ...)` 移入各自的 `composable<...>`，
   冷启动不再触碰它们 → 消除主线程与 IO 并发打开数据库
2. **启动期例行工作整体兜底**：`AshkbApplication.onCreate` 的协程体包 `runCatching`
   ——协程内的未捕获异常会直接冒泡杀进程（这正是表现为「闪退」而不是「报错」的原因）
3. **崩溃留档**：新增 `CrashLogger`——未捕获异常写入 `filesDir/last_crash.txt`（`adb pull` 可取），
   并在**下次启动**以 Snackbar 摘要提示，便于截图反馈（只写本地 + Logcat，**不上报**，与零网络红线一致）

### 测试

单测 264 条全绿（热修，未新增测试）

## [v1.0.39] — 2026-09-21

**推荐食谱库 + 周期康复计划**：B3 食谱库（10 条带出处的参考食谱，可按标签筛选 / 收藏 / 自建）；B7 4–12 周周期康复计划模板（按周递进 + 完成度追踪）。

⚠️ **含数据库结构变更（Room v14 → v15，新增 2 张表）**，可覆盖安装；旧备份恢复不受影响（新表为空属预期）。

### B3 · 推荐食谱库

- 新增 `recipes` 表 + **10 条种子食谱**（抗炎 / 胃肠友好 / 控热量三类标签），启动时按固定 id 幂等种入
- **出处只显示编号**（S1…S9），**点开某条食谱才在详情里展开完整题录**——列表不占版面；出处台账见 `domain/RecipeSources`
- 支持标签筛选、收藏置顶、自建 / 编辑 / 删除；自建食谱无出处
- 详情固定展示免责声明：参考食谱、非医疗建议、不能替代药物
- ⚠️ 内容口径经用户逐条核对：第 6 条**弱化**为「减少精制淀粉」而非「低淀粉疗法」（S2 指出 AS 膳食证据极为有限且不确定）

### B7 · 4–12 周周期康复计划

- 新增 `exercise_plans` 表 + **3 个模板**（4 周起步 / 8 周强化 / 12 周维持），启动时幂等种入
- `week_structure` 存每周「强度级别 + 目标天数 + 提示」；动作仍由 `ExerciseEngine` 按当日分期从运动库过滤生成（不与 R27 矩阵产生第二份真相）
- **完成度由 `exercise_logs` 反算**（去重日期，不另存进度）：本周进度 + 周期累计 + 百分比
- 同一时刻只启用一个计划；启用即重新起算周次
- 补齐知识库种子 `kb_seed_exc.json` 里对 `exercise_plans.week_structure` 的引用（原为悬空挂点）

### 测试

单测 244 → 264（新增 `RecipeSeedsTest` 5 / `RecipeSourcesTest` 4 / `ExercisePlanTemplatesTest` 5 / `ExercisePlanProgressTest` 6）

## [v1.0.38] — 2026-09-21

**营养素精细化 + 周月报**：补剂可量化剂量并设每日参考上限（超限警示）、与用药时间错开提醒、服药/补剂合并时间表；报表新增「周月报」页签。

⚠️ **含数据库结构变更（Room v13 → v14）**，可覆盖安装；旧备份恢复不受影响（新增列可空，无需回填）。

### B11 · 营养素上限 / 错开提醒 / 合并视图

- `supplements` 新增 `dose_amount` / `dose_unit` / `daily_max`（均可空）；补剂表单新增「单次剂量（数值）/ 单位 / 每日参考上限」
- **每日上限警示**：当日累计 = 单次剂量 × 每日次数，超过用户设定的上限时提示
  - ⚠️ **刻意不内置任何医学上限数值**——上限由用户 / 医生 / 营养师填写，App 只做算术比较，不代替专业判断
- **与用药时间错开提醒**：钙等矿物类补剂与左甲状腺素 / 四环素类 / 喹诺酮类 / 铁剂等螯合类用药同服且间隔 < 2 小时时提示
- **服药 / 补剂合并时间表**：今日药单与补剂按时刻合并成一条时间线（药 / 补标签区分）

### B4 · 报表新增「周月报」

- `ReportRepository.periodicReport(days)`：用药/补剂依从、运动天数与时长、症状均值、BASDAI、体重、发作、复诊与下次复诊
- 「报表」页新增第 4 个页签「周月报」，支持近 7 天 / 近 30 天切换
- 口径与「概览」一致（部分完成计 0.5）；无数据的指标显示「暂无」而非 0

### 测试

单测 231 → 244（新增 `SupplementLimitsTest` 6 / `SupplementTimingTest` 7）

## [v1.0.37] — 2026-09-21

**用药精细化 + 化验与筛查**：停药/漏服原因对齐规划口径、新增「减量中」服药态并豁免医嘱减量警示、报表补补剂依从、化验按单位分组 + 跨院提示、生物制剂筛查/续方节点一键种入。

⚠️ **含数据库结构变更（Room v12 → v13）**，可覆盖安装；旧备份恢复不受影响（新增列可空，无需回填）。

### C5 · 停药 / 漏服原因对齐规划口径

- 停药原因新增：**感染发热 / 准备手术 / 经济原因**，各带针对性提示（感染期需暂缓免疫抑制、术前需错开停药窗口、经济原因建议与医生沟通替代方案）
- 漏服原因新增：**遗忘 / 外出 / 药物用完**
- 旧枚举 key 全部保留，历史日志解析不受影响

### C6 · 服药三态「固定 / 按需 / 减量中」

- `medications` 新增 `dose_state` + `taper_note`（均可空）
- 药品编辑表单新增「服药状态」选择器；选「减量中」时可填减量方案备注
- **医生批准的减量方案不触发停药警示**：`domain/StopWarning` 在「减量中」且原因为「自行停药」时豁免警示（含生物制剂的强化警示），副作用 / 感染 / 手术等提示照常
- 未显式设置时按 frequency 推断（PRN → 按需，其余 → 固定），旧数据与旧备份恢复后仍正确

### C2 · 报表补「补剂（营养）依从」

- `ReportRepository.overview()` 新增 `supplement` 统计（与用药同口径：部分完成计 0.5）
- 「概览」页新增补剂依从卡片（阈值配色与用药卡一致；无记录时不摆 0% 假进度条）

### C3 · 化验按单位分组 + 跨院提示

- `domain/LabUnits` 纯函数：按「项目名 + 单位」分组、识别同一项目的多单位
- 化验列表：跨单位项目单独标注单位，并在顶部提示「同一项目存在多个单位（跨院 / 换设备），数值不可直接比较，仅供参考」
- 异常置顶与正常项折叠交互不变

### C10 · 生物制剂筛查 / 续方节点

- `domain/ScreeningSeeds` + `HealthRepository.seedBiologicScreeningItems()`：一键种入**结核 / 乙肝 / 丙肝筛查**（周期 365 天）+ **生物制剂续方 / 门诊随访**（周期 90 天）
- 幂等：按名称去重，重复点击不会建重复条目
- 复诊项目卡片内新增入口与结果反馈

### 测试

单测 215 → 231（新增 `StopWarningTest` 7 / `LabUnitsTest` 5 / `ScreeningSeedsTest` 4）

## [v1.0.36] — 2026-09-21

**附件远端校验与补传**：新增「校验远端」动作——比对服务器上 `ashkb/attachments/` 的实际文件集与本地记录，**远端缺失的自动补传**、**远端多余（本地已无记录）的一并清理**。

无数据库结构变更（沿用 Room v12）、无备份格式变更，可覆盖安装。

### 新增 · 备份页「校验远端」

解决两类远端/本地不一致：

1. **远端被手动删除**（网页端误删 / 服务商清理）→ 本地有记录、远端没有 → **重新上传**
2. **换机后远端残留**（旧设备已删附件、或恢复后本地无对应行）→ 远端有文件、本地无记录 → **清理**

顺带收尾软删除墓碑行（远端已确认不在 → 物理删行）。清理走服务器 DELETE，**坚果云可在回收站找回**。

### 安全边界（只碰自己的文件）

`ashkb/attachments/` 是专用目录，但用户完全可能在网页端往里放别的东西，因此**清理严格限定在可管理形状**：

- 只处理 `ashkb/attachments/<合法日期目录>/<附件id>.enc`
- 日期目录必须是 `YYYY-MM-DD` 或脏数据占位目录 `0000-00-00`
- 非日期目录、非 `.enc` 文件（如 `readme.txt`）**一律不碰**
- 所有远端路径先过 `AttachmentPath.isManagedRemotePath`（含路径穿越 / 绝对路径防护）

### 实现要点

- `WebDavClient.listAttachmentRemotePaths()`：两级 `PROPFIND Depth:1`（先列日期子目录，再逐个列文件）——坚果云等对 `Depth:infinity` 支持不一；目录不存在（404）返回空集（「从未同步过」是正常态）
- `WebDavClient.parseDavEntries()`：集合判定同时看 href 尾斜杠与 `<resourcetype><collection/>`（服务器实现差异）
- `AttachmentPath.reconcile()`：纯函数差集（missing / orphans），可单测
- 抽出 `clearTombstones()` 供「同步附件」与「校验远端」共用

### 测试

单测 205 → 215（`AttachmentPathTest` 增 5 条 + 新增 `WebDavClientParseTest` 5 条）

## [v1.0.35] — 2026-09-21

**附件接入 WebDAV 备份**：附件加密后**逐个**上传到服务器（按日期分目录），换机时**按需懒取回**；配套同步开关与同步删除。

⚠️ **含数据库结构变更（Room v11 → v12）** 与**备份格式升级（v2 → v3）**，可覆盖安装；**旧备份（v1/v2）永久可读**。

### 关键设计 · 稳定 vault key（为什么必须先做这一步）

原实现每次备份都**新生成随机 DEK**（`VaultCipher.encrypt`）。附件要长期存在云端，若用它加密：
改口令 / 重生成恢复码后旧附件**全部解不开**；且同一附件可能被不同密钥加密而无法管理。

因此引入**稳定 vault key**（32 字节随机，Keystore 保护，落 `vault_config` prefs，**永不更换**）：

- 备份 payload 与**所有附件共用**这把 key → 备份格式升 **v3**（`ASHKBAK3`），密钥槽包装的就是它
- 换机恢复：从备份的密钥槽解出 vault key 并采纳到本机 → **DB 与附件一起可读**
- 改口令 / 重生成恢复码 = **只重包装密钥槽**，云端附件密文不用重传
- 采纳规则：**仅当本机没有密钥时**才采纳备份里的——否则一次旧备份恢复会把本机密钥冲掉，
  当前设备已上传的附件立刻解不开
- 安全性不降：槽仍由 PBKDF2(口令/恢复码) 保护，与 v2 同构

### 新增 · 附件同步（备份页）

- **开关**「同步附件到 WebDAV」（默认**关**）：关闭时新附件不上传、删除不动远端
- **「同步附件」按钮**（批量补传）：待传队列 = 所有未上传附件；进度实时显示；**幂等可中断**（重跑继续）
- **摘要**：本地 N 个 · 已同步 M · 待传 K · 待清理 J + 本地占用
- 与 DB 备份**分开操作**——DB 备份有 120s 超时，附件几百 MB 远超，混在一起必然失败
- 台账新增 `附件同步` 类型登记

### 新增 · 远端目录与命名

```
ashkb/attachments/2026-09-21/catt-m1abc2x3y4z.enc
```

- 日期文件夹取**附件内容日期**（与 App 内列表排序一致，网页端浏览也直观）
- 文件名 = 附件 id + `.enc`，**刻意不含原始文件名**——「化验单-类风湿因子-张三.pdf」这种明文名
  即使内容加密也已泄露病情，故远端一律不可读命名，浏览走 App
- 附件密文格式：`[8B "ASHKBATT"][12B iv][ct+tag]`，AES-256-GCM，**AAD 绑定附件 id**
  （即便有人能写服务器，也无法把 A 的密文冒充成 B）
- 上传**不做回读**（逐个回读流量翻倍），改为记录本地密文 SHA-256 供后续校验

### 新增 · 懒下载与同步删除

- **懒下载**：附件列表点「查看」时，本地文件缺失但有远端副本 → 自动取回并解密再打开；
  失败明确提示（"云端未找到该附件，或网络异常"）。**恢复后无需批量下载**，按需取用
- **同步删除**：本地删除 → 有远端副本且开关开启 → 置**软删除墓碑**（`deleted_at`），
  下次同步时 `DELETE` 远端成功后物理删行；失败则留墓碑重试（坚果云回收站兜底）
- 墓碑行对用户不可见（列表/详情查询均已过滤）

### 数据库 v11 → v12

`checkup_attachments` 加 `remote_path` / `remote_sha256` / `synced_at` / `deleted_at` + `remote_path` 索引。
全部可空 → 旧备份恢复后 = 未上传 / 未删除，合法业务态，**无需回填**。

### 未做（已列入下版本计划）

「校验并补传 / 清理远端孤儿」：远端被手动删除、或本地有而远端缺时，需 PROPFIND 列目录与 DB 比对。
本版先保证主链路（上传 / 懒下载 / 同步删除）稳定。

### 验证

- 单测 **180 → 205**（新增 `VaultCipherV3Test` 18 条：v3 往返与密钥一致性 / 恢复码槽 / 错密钥与篡改拒绝 /
  v1·v2 兼容且不误采纳密钥 / 附件密文往返·随机 iv·AAD 防冒充·空文件；新增 `AttachmentPathTest` 7 条：
  日期口径 / 脏数据兜底 / 路径拼接 / **路径穿越与绝对路径拒绝**）
- release / debug 双包构建成功 + 正式签名验签
- **装机回归重点**：①备份页开启「同步附件到 WebDAV」→ 点「同步附件」→ 摘要从「待传 N」变为「已同步 N」；
  服务器 `ashkb/attachments/<日期>/` 下出现 `.enc` 文件 ②关闭开关时按钮不可点并有提示
  ③删除一个已同步附件 → 服务器上对应文件消失 ④**换机恢复**：恢复 DB 备份后附件列表完整可见，
  点「查看」能自动取回并打开 ⑤旧备份（v1/v2 文件）恢复不受影响

## [v1.0.34] — 2026-09-21

B10 附件归档**后续增强**：化验 / 影像记录可**归属**到复诊记录，并在两个 Tab 直接归档附件（用户选定的 B 方案 + 手动选择归属）。

⚠️ **含数据库结构变更（Room v10 → v11）**，可覆盖安装。**旧备份仍可恢复**（新列可空 = 尚未归属）。

### 背景 · 此前化验 / 影像与复诊记录之间没有任何关联

- `lab_results.checkup_id` 字段**存在**，但 AI 导入时**恒写 null**（`importLabReport` 里写死）
- `imaging_records` **连字段都没有**
- 后果：附件无法归档到对应复诊；且**既有缺陷**——「复诊记录 → 查看化验」永远查不到数据（该弹窗按 `checkup_id` 查，而导入的化验该列全是 null）

### 新增 · 归属复诊记录（手动选择，不按日期自动猜）

- **化验 Tab**：每个日期分组新增「归属复诊记录」→ 选择器 → 把该日全部化验归属到所选复诊；已归属时显示标记
- **影像 Tab**：每条影像记录新增「归属复诊记录」→ 选择器 → 归属该条
- **选择器**：列出最近 50 条复诊记录（`日期 · 项目名`），含「不关联」选项可解除归属；**不预选、不猜测**（用户拍板：避免按日期配错）
- **连带修复**：「记录」Tab 点「查看化验」现在能看到 AI 导入的化验数据（因为 `checkup_id` 终于被写入）

### 新增 · 化验 / 影像 Tab 直接归档附件

- 两个 Tab 的每一条都新增「附件归档」入口，进弹层即可拍照 / 相册 / PDF
- 弹层内**也带归属选择器**：若该化验/影像尚未归属，可当场选一条复诊记录——**附件与来源记录一起归属**，不会产生"孤儿附件"
- **全部附件总览**：「记录」Tab 顶部新增「附件归档」入口，打开不绑定记录的弹层（未归属附件在这里可见、可逐条补归属）——这是未归属附件唯一的可见入口，避免数据"沉底"

### 数据库 v10 → v11

- `imaging_records` 加 `checkup_id TEXT`（与 `lab_results.checkup_id` 对称）+ 索引
- 可空：旧备份无此列 → 恢复后 NULL = 尚未归属，合法业务态，**无需回填**

### 验证

- 180 条单测全过 + release / debug 双包构建成功 + 正式签名验签
- **装机回归重点**：①化验 Tab 某日期 →「归属复诊记录」→ 选一条 → 出现已归属标记；「记录」Tab 该复诊 →「查看化验」应看到这批化验 ②影像 Tab 同理 ③两个 Tab 的「附件归档」→ 拍照 / 相册 / PDF 均可用 ④**未归属时先选归属再添加附件** → 附件与化验/影像一起归入该复诊 ⑤「记录」Tab 顶部「附件归档」→ 能看到全部附件（含未归属的），可逐条补归属 ⑥旧备份恢复后数据完整

## [v1.0.33] — 2026-09-21

规划文档需求缺口**第二批**（5 项，用户指定）：C4 复诊前准备清单 / B2 知识库个人备注层 / C7 漏服处理指引 / C9 体重目标区间 / B10 复诊附件归档（支持拍照、相册、PDF）。

⚠️ **含数据库结构变更（Room v9 → v10）**，可覆盖安装（迁移自动执行，数据保留）。**旧版本备份仍可恢复**（新增列可空，恢复后为「未设置」态）。

### 新增 · B10 复诊附件归档（化验单 / 影像报告拍照或上传）

- **入口**：「复诊记录」每条记录的卡片内新增「附件归档」→ 底部弹层
- **三种来源**：拍照（`TakePicture`，经 FileProvider 写内部存储）/ 从相册选（`PickVisualMedia`）/ 选 PDF（`OpenDocument`，限 `application/pdf`）
- **列表**：显示类型、大小、时间；可「查看」（`ACTION_VIEW` 交给系统图片查看器 / PDF 阅读器）与「删除」（二次确认，文件同步移除）
- **存储**：文件落 `filesDir/checkup_attachments/`（非缓存目录，不会被系统回收），DB 只存元数据；单文件上限 20 MB（防误选超大文件）
- ⚠️ **附件不随数据库备份**：备份导出的是各表 JSON，不含二进制文件——换机恢复后附件需重新导入。这是为保住备份轻量（WebDAV 上传 120s 超时）的取舍，已在界面与文档明示

### 新增 · C4 复诊前准备清单

- 「复诊记录」页顶部新增准备卡：**距下次复诊倒计时**（从各记录的未来 `next_date` 取最近一条）+ **本次需做的检查项目**（在用复诊项目）+ **建议携带清单**
- **空腹提示**：在用项目含化验类（LAB）时自动标注「需空腹」并给出具体说明（前一晚禁食 8–12 小时）；含影像类时补「去除金属饰品、携带既往片子」
- 未设下次复诊时间时给出引导（在复诊记录里填「下次复诊」即可生成），不显示空卡

### 新增 · C7 漏服 / 延迟处理指引

- 今日页漏服（已过计划时刻且未打卡）的药品卡新增「漏服处理指引」入口
- **口服**：≤2 小时 → 可尽快补服（提示若已接近下次服药则跳过）；>2 小时 → 建议跳过本次，**明示切勿加倍剂量**
- **注射**：≤48 小时 → 窗口期内尽快补注，之后按原周期顺延；**>48 小时 → 超窗，提示先联系风湿科医生**，并给出「联系医生」红色标记
- 免疫抑制 / 生物制剂类在窗口内也附加「先咨询医生」提示
- 边界：全程只给通用安全提示，**不做个体化剂量决策**（医疗边界，v2 §1.7），弹窗内明示以说明书与主治医师医嘱为准

### 新增 · B2 知识库「个人备注层」

- 落实 v3 规划的「双层结构」：只读种子层（47 条）+ **个人备注层**（新增 `kb_entries.user_note` 列）
- 条目详情弹窗内「我的备注」区块：写 / 改 / 清除；列表行对有备注的条目标「有备注」，页面顶部显示「N 条有我的备注」
- **只更新 `user_note` 一列**——种子内容（标题 / 摘要 / payload）不受影响，条目随版本更新也不会覆盖个人备注
- 备注在知识库、紧急卡、运动处方、症状警报依据四处弹窗均可编辑（同一列）

### 新增 · C9 体重目标区间

- 档案编辑页新增「体重目标区间」上下限输入（kg，可留空，容忍上下限填反——自动交换）
- 体重卡显示状态：**在目标区间内**（绿）/ **低于 / 高于目标 x kg**（黄，附区间提示）/ 只填一侧时提示补全
- 未设目标时不显示任何提示（不打扰）

### 数据库 v9 → v10

- `kb_entries` 加 `user_note TEXT`（可空）
- `profile` 加 `weight_target_low REAL` / `weight_target_high REAL`（可空）
- 新表 `checkup_attachments`（id / checkup_id / kind / file_name / mime / size_bytes / display_name / note / created_at）+ 2 个索引
- **备份兼容**：`BackupEngine` 按 `sqlite_master` 动态发现表、按备份自身列集校验 SHA——加表加列天然兼容，旧备份可恢复。三个新列 NULL 都是合法业务态（未写备注 / 未设目标），**无需恢复后回填**（与 v9 `search_text` 不同）

### 修复 · 顺带发现的两个既有缺陷

- **`ProfileEditScreen` 保存会重置未呈现字段**：表单未包含 `lifestyle` / `uiMode` / `emergency_med_summary` / `emergency_note`，保存时未透传 → 被重置为实体默认值（潜在数据丢失，本次新增的体重目标若也漏传就会立刻显形）。已改为从 `initial` 透传
- **注射超窗判定精度**：`MissedDose` 初版先把分钟整除成小时再比较，导致 48h+1min 被截断成 48h 而误判「仍在窗口内」。已改为按分钟直接比较（单测覆盖 48h 边界两侧）

### 验证

- 单测 **149 → 180**（新增 `WeightTargetTest` 8 条 / `MissedDoseTest` 13 条 / `CheckupPrepTest` 10 条）
- release / debug 双包构建成功 + 正式签名验签
- **装机回归重点**：①档案填体重目标 → 体重卡出现状态提示 ②知识库任一条目写备注 → 列表出现「有备注」标记，杀进程重进仍在 ③复诊记录填「下次复诊」→ 准备卡出现倒计时；有化验项目时出现「需空腹」 ④今日页漏服卡片点「漏服处理指引」→ 口服 / 注射文案分级正确 ⑤复诊记录 →「附件归档」→ 拍照 / 相册 / PDF 三种方式均能保存与查看 ⑥**旧备份恢复后数据完整**（新列为「未设置」态）

## [v1.0.32] — 2026-09-20

「我的」页新增**关于卡**（用户需求）+ 记录 v1.0.31 装机验证结果（冷启动 / 五 Tab / 症状页 Chip 全部通过✓）。v1.0.31 可直接覆盖安装（无数据库结构变更）。

### 新增 · 「我的」页关于卡（版本号 + MIT 协议 + 全局免责声明）

- **版本号**：显示 `v1.0.32 (37)` 形式（versionName + versionCode），用 `PackageManager` 取值（不动 `buildFeatures.buildConfig`——AGP 8 默认关闭）；用户无需进系统设置即可核对当前版本，装机验证时直接对照
- **开源协议**：MIT + 仓库地址（github.com/weante/ashkb）
- **全局免责声明**（顺带关闭规划缺口 C12 的「显著位置全局声明」）：「本应用为个人健康管理记录工具，不构成任何医疗建议，不能替代医生诊疗；用药与治疗方案请始终遵医嘱」——此前仅 CHANGELOG / PDF 页脚 / 知识库条目级有声明，应用内无显著全局位置
- 位置：备份入口之下、页面底部

## [v1.0.31] — 2026-09-20

药单管理新增**在用药品编辑**（用户需求）。v1.0.29 可直接覆盖安装（无数据库结构变更）。本版与 v1.0.30（趋势图动画重播修复，代码已先行合入 main）一并生效——从 v1.0.29 直接升级即同时获得两版修复。

### 新增 · 在用药品编辑

- **背景**：药单管理此前只有「停用」操作——剂量调整、换时刻、改频次、注射周期变更等只能停药重建，历史打卡的 `medId` 关联随之断裂。路由 `MedEdit(id)` 其实早已预留参数位但从未接线
- **入口**：药单管理每行新增编辑按钮（铅笔图标，位于停用左侧）→ 进入既有两步表单，**全部参数预填**（药名 / 商品名 / 通用名键 / 类别 / 途径 / 剂量 / 频次 / 时刻 / 星期 / 注射周期 / 餐食关系 / PRN 原因 / 贮存 / 起始日 / 核对勾选）
- **保存语义**：仓库层 `upsert` 保留原 `id` 与 `createdAt`（`updatedAt` 自动刷新）——**历史打卡记录的关联不丢**；`interactionCheckDate` 保留原值（原为空才在过第二步时记当天）；顶栏显示「编辑药品」
- **第二步（R03 核对）同样保留**：预填原勾选状态，可改；相互作用查询按编辑后的参数重新执行
- **保存后提醒自动重排**：`saveMedication` 既有逻辑（upsert → `ReminderScheduler.rescheduleAll`）——改时刻 / 改频次 / 改周期后提醒即时按新参数生效
- 预填时刻解析复用 `ScheduleCalc.takeTimesOf`（含非法时刻过滤，脏数据不进表单）；预填仅按 `editId` 执行一次（`LaunchedEffect(editId)`），不覆盖用户后续输入；`meds` 流用 `first{}` 兜底冷直达时的加载等待
- **验收**：①药单管理 → 点铅笔 → 表单预填该药全部参数 ②改剂量 / 时刻 → 下一步 → 保存 → 列表行参数更新 ③今日页打卡计划按新时刻出现 ④提醒闹钟按新时刻触发（下次到点验证）⑤历史打卡记录不受影响（服药历史 / 报表统计不变）⑥停用流程不受影响

## [v1.0.30] — 2026-09-20

装机反馈修复（v1.0.29 装机验证结果：**0 点跨零点测试通过**✓；趋势页签发现动画回归）。v1.0.29 可直接覆盖安装（无数据库结构变更）。

### 修复 · 报表趋势图每次切换页签都重播入场动画

- **现象**：报表页切到「趋势」页签，6 个折线图的描线入场动画每次都从头播放
- **根因**：趋势页是 `HorizontalPager` 的页面——切走即滑出视口被 Pager 回收，切回时重新组合；`TrendChart` 的动画进度是普通 `remember { Animatable(0f) }`，组合销毁即丢失，回来必然从 0 重播。v1.0.25 只把 `points` remember 住了（防同屏重组重播），防不了页面级回收
- **修复**（`TrendChart` 内部一处，6 个图全生效）：「已为该数据集播过」标志用 `rememberSaveable` 跨组合保存，动画以数据**内容 hash** 为 key——页签切换 / 旋转屏回来时内容未变则直接呈现终态；数据真正变化（新记录写入）才重播入场。顺带覆盖「上游 Room 流重发内容相同的新 List 实例」场景
- **验收**：报表页在「概览 ⇄ 趋势 ⇄ 导出」间反复切换，图表应立即完整显示、无动画重播；新增一条 BASDAI / 症状记录后回到趋势页，对应图重播一次入场动画

## [v1.0.29] — 2026-09-20

**紧急热修：v1.0.28 启动即闪退。** v1.0.28 引入了严重缺陷，请务必升级本版（v1.0.28 无法降级——versionCode 已递增，Android 不允许用低版本覆盖）。数据库结构无变更，直接覆盖安装即可，数据完整保留。

### 根因 · `SymptomViewModel` 属性初始化顺序错误（NPE）

v1.0.28 为修「症状页所选日期跨零点悬空」，在 `init` 块里加了一个 `_date.collect { ... }` 去读 `_selectedDate`——但 `_selectedDate` **声明在 `init` 块之后**：

```kotlin
init {
    viewModelScope.launch { /* ticker */ }
    viewModelScope.launch {
        _date.collect { d ->            // StateFlow.collect 首个发射不挂起
            _selectedDate.value          // ← 此刻 _selectedDate 尚未初始化 = null → NPE
        }
    }
}
private val _selectedDate = MutableStateFlow(today)   // ← 声明在后面
```

- Kotlin 的属性初始化器与 `init` 块**按声明顺序执行**；
- `viewModelScope` 使用 `Dispatchers.Main.immediate`——已在主线程时 `launch` 体**同步执行到第一个挂起点**，而 `StateFlow.collect` 的首个发射**不挂起**，于是直接读到尚未赋值的字段；
- `AppShell` 在启动时一次性创建全部 10 个 ViewModel（含 `SymptomViewModel`），因此**每次冷启动必崩**。

### 修复

- `_selectedDate` 上移到 `init` 之前（语义正确）；
- 更关键的是**不再依赖声明顺序**：把跨零点回落逻辑合并进 ticker 协程内、写在 `delay(...)` **之后**——此时构造早已完成，字段必然可用。少一个常驻协程，也消除这类脆弱性。
- 顺带清理不再使用的 `kotlinx.coroutines.flow.collect` import。

### 排查过程（为何单测没拦住）

本批改动全是结构性 / 时序修复，无新增可单测的纯函数；且 149 条单测**不覆盖 ViewModel 构造**（`HealthRepository` 需 Android `Context`，测试用 `isReturnDefaultValues = true` 只跑 domain 纯逻辑）。这类「构造期 NPE」只能靠装机发现——已记入 HANDOFF §7 教训。

### 验证

- 149 条单测全过 + release / debug 双包构建成功 + 正式签名验签
- **已逐个人工核对全部 10 个 ViewModel 的 `init` 块**，确认仅 `SymptomViewModel` 存在「init 读后置字段」问题；其余 6 个带 ticker 的 VM（Today / Wellness / Exercise / Checkup / Emergency / Knowledge）`_date` 均声明在 `init` 之前且 `init` 不触碰后续字段
- 装机验证重点：**冷启动不崩** → 五 Tab 正常切换 → 症状页「今天 / 昨天」日期 Chip 正常

## [v1.0.28] — 2026-09-19

> ⚠️ **本版存在启动即闪退缺陷，已被 v1.0.29 修复，请勿安装本版。**（原因见 v1.0.29 条目：`SymptomViewModel` 属性初始化顺序 NPE）

Compose 性能审查**第二批**：按第二轮审查报告的建议顺序，先做「低风险、高收益」的一组——1 个潜在崩溃、1 个无界缓存（内存泄漏）、3 处跨零点日期错误、搜索框敲键整屏重组、1 处 O(N·M) 扫描。v1.0.27 可直接覆盖安装（无数据库结构变更）。

### 修复 · 潜在崩溃与内存泄漏

- **Today 页打卡列表 key 冲突（潜在崩溃）**：`items(key = { med.id + slotKey })` 用字符串拼接做 key——同一药品若出现重复时间槽（旧数据 / 导入数据里 `take_times` 有重复值）会产生重复 key，LazyColumn 抛 `IllegalArgumentException` 直接崩溃。改为 `med.id to slotKey`（`Pair` 有稳定 `equals`/`hashCode`，Compose 接受任意类型作 key），顺带去掉每帧新建 String 的分配
- **化验详情按复诊缓存 StateFlow 无界增长**：`CheckupViewModel.labByCheckup` 每打开一条复诊详情就新增一个 entry（浏览 100 条 = 常驻 100 个 StateFlow），且 `mutableMapOf` 非线程安全、`onCleared` 不清理。改为 VM 返回**冷 Flow**，由调用点 `remember(record.id)` 记住订阅（状态在 UI 层记住）——无泄漏、无并发写、切换记录自动重订阅

### 修复 · 跨零点日期正确性（急救 + 知识库 + 症状）

- **紧急卡「当前用药」跨零点仍显示昨天的在用判断**：`EmergencyScreen` 的 `medsSummary` 用 `remember(meds) { summarize(meds, LocalDate.now()) }`——日期被冻结在首次求值（23:59 打开，过零点后仍按昨天算「在用」）。`EmergencyViewModel` 补日期 ticker，页面改用 `vm.date` 驱动重算（急救场景日期错误不可容忍）
- **知识库「复核到期」计数跨零点错误**：`KnowledgeViewModel.today` 是构造时的 `LocalDate.now()` 字符串。补 ticker，`overdue` 改由 `combine(observeKbAll(), _date)` 驱动重算；`KnowledgeScreen` 的条目到期标记同步改用 `vm.date`
- **症状页「所选日期」跨零点悬空**：23:50 点「昨天」后过零点，`_selectedDate` 既不是今天也不是昨天 → 两个日期 Chip 都不选中。`_date` 变化时若所选日期不再合法则回落到今天

### 优化 · 重组与计算

- **知识库搜索框敲键整屏重组**：VM 里的防抖只护住了 DAO 查询，`uiState` 的 combine 仍随原始 query 立即重算 → 每敲一键，顶栏计数 / Chip 行 / 告警 Banner / 列表全部重组。搜索框改为**本地 state + 防抖后再推 VM**（`LaunchedEffect(queryText)` + `delay(DEBOUNCE_MS)`），敲键期间只有输入框自身重组
- **知识库冗余 Flow 清理**：`overdue` 的 `combine(flowOf(Unit))` 等价于 `map`，改为 `combine(observeKbAll(), _date)`（同时修了上一条的日期冻结）；`uiState` 里对分类结果的 `filter { it.category == c }` 属冗余（分类已走 `observeKbByCategory`，DAO SQL 自带 `WHERE category`），去掉
- **补剂打卡 O(N·M) 扫描**：`WellnessScreen` 每个补剂行都对全部 `supLogs` 做 `any {}` 线性扫描（15×15=225 次比较/帧）。改为外层一次性算 `doneIds: Set`，行内改 `in` 查询
- **运动处方组合在主线程**：`ExerciseViewModel.uiState` 的 `ExerciseEngine.todayPlan`（含 filter + sort）加 `.flowOn(Dispatchers.Default)`，KB 扩容后不阻塞主线程；`feedbackPending` 的双层 `flatMapLatest` 简化为 `combine(_date, feedbackRefresh)` + 单层

### 对第二轮审查报告的三处更正（实测 / 查证）

1. **Strong Skipping 无需开启**：报告建议在 `build.gradle.kts` 加 `composeCompiler { featureFlags = setOf(StrongSkipping) }`——本仓库 Kotlin **2.0.20 已默认开启 Strong Skipping**（JetBrains 2.0.20 发布说明 + Android 官方文档均确认），无需配置；lambda 已由编译器自动 memoize，报告建议的 `rememberUpdatedState` 亦非必需
2. **Today 页 key 崩溃的真实路径**：报告称「同一药品的多个 PRN 项会 key 冲突」——实测 `MedicationRepository.buildTodayItems` 对每个 PRN 药只产出 **1 条**（`slotKey = null`），不会因此冲突；真实路径是同一药品**重复时间槽**（`take_times` 有重复值，旧 / 导入数据可能造成）。修法（`Pair` key）两者都覆盖
3. **ReportViewModel 无需日期 ticker**：`ReportRepository.overview()/trends()` 内部各自取 `LocalDate.now()`，且只在 `refresh()`（含 init）时求值——不存在「冻结在组合时」的问题（跨零点不自动刷新属可接受行为，非缺陷）

### 验证

- 单测 **149 条全过**（本批为纯结构性 / 时序修复，无新增可单测的纯函数；报告建议的 PDF 运算符优先级回归测试因需 Android `Context` 暂缓）
- release / debug 双包构建成功 + 正式签名验签
- 装机回归重点：①知识库搜索框敲键是否顺滑 ②复诊详情依次打开多条（内存无持续增长）③紧急卡用药区（若有结束日为昨天的药，跨零点后应消失）④补记症状后跨零点日期 Chip 状态 ⑤旧版备份恢复不受影响
- **仍在报告待办池**（改动面大的结构性重构，留待下一批）：6 个屏幕顶层 Flow 收集下沉到叶子、`MedEditScreen` 字段级拆分、`BackupScreen` 密码框拆分、`AppShell` 按需创建 ViewModel、`DateProvider` 抽象

## [v1.0.27] — 2026-09-19

补上规划缺口 A2：**备份恢复码**——口令遗忘的最后退路（此前口令遗忘 = 数据永久不可恢复）。v1.0.26 可直接覆盖安装（无数据库结构变更）。

### 新增 · 备份恢复码（信封加密 + 双密钥槽）

- **背景**：v1 备份格式口令直接派生密钥，别无退路——规划中恢复码是砍掉设备层后仅存的两层密钥之一，但一直未实现（`恢复码` 全库零命中）
- **机制**：备份文件升级为 **v2 信封格式（magic `ASHKBAK2`）**——随机 256-bit 数据密钥 DEK 加密内容；DEK 再分别被「口令」与「恢复码」包装进两个密钥槽（类似 LUKS keyslot），**任一可解**。槽 id 进 AAD 防槽交换攻击
- **码形**：160-bit SecureRandom → Base32 恰 32 字符，按 4 字符分组展示（8 组）；熵足够对离线暴力破解免疫，且不依赖用户选择强度
- **入口**：备份页新增「备份恢复码」卡片——生成 / 查看 / 重新生成（重生成有二次确认，仅影响之后的新备份）；弹窗等宽分组展示 + 可选中复制 + 抄写提示
- **存储**：恢复码经 Android Keystore AES-256-GCM 加密落盘（同 WebDAV 凭据模式），不随备份文件上传——本机丢失后靠抄写的那份
- **恢复入口**：恢复口令框直接输恢复码即可（解密时先按原样逐槽尝试，输入形似恢复码时再按归一化形态兜底——**小写、漏连字符、多空格等任意抄写形态均可解**）
- **兼容**：v1.0.5–v1.0.26 的旧备份（`ASHKBAK1`）永久兼容读取；pre-restore 快照同样带恢复码槽（用恢复码恢复时，快照仍可用同一恢复码解开）
- 未设恢复码时新备份为 v2 单口令槽（格式统一，之后设置恢复码零格式变更）

### A3（KDF 为 PBKDF2 而非规划写的 Argon2id）同批评估结论文档化

不引入 Argon2id：Android 无内置实现，引入 Bouncy Castle 有 provider 冲突史 / argon2 native 库需 +1MB APK；且 v2 格式每槽自带 `kdf`/`iter` 参数——**将来如需升级可在新槽内无破坏切换**，旧文件按头内参数解。PBKDF2-SHA256 ×120k 对自用场景边际收益小。架构铺路已完成，实现延后（HANDOFF 有决策记录）。

### 验证

- 单测 130 → **149**（`VaultCipherTest` 9→18：双槽口令/恢复码/任意抄写形态可解、伪恢复码不放行、槽交换被拒、v1 永久兼容、v2 槽结构；新增 `RecoveryCodeTest` 10 条：码形/归一化/字母表边界/Base32 已知向量）
- release / debug 双包构建成功 + 正式签名验签
- 装机回归重点：①生成恢复码 → 抄写 → 备份 → 故意输错口令 → 输恢复码应能解密 ②小写无连字符形态的恢复码也应可解 ③旧版本备份（v1 文件）恢复不受影响 ④一键恢复演练仍通过

## [v1.0.26] — 2026-09-18

补上规划缺口 A1：**紧急卡「当前用药」**（v3 M7 要求紧急信息卡含「过敏史 / 血型 / 用药 / 诊断」）。v1.0.25 可直接覆盖安装（无数据库结构变更）。

### 新增 · 紧急卡「当前用药」（自动汇总药单）

- **背景**：规划明确要求紧急信息卡含「用药」，但此前**紧急卡页面与打印版都不显示任何用药信息**——`Profile.emergency_med_summary` 字段有定义却无任何读写，等于完全没实现。急救场景最需要知道的「患者是否在用免疫抑制剂 / 生物制剂」恰恰缺失
- **做法**（用户拍板：自动汇总，不手工维护）：从在用药单自动生成清单，**免疫抑制相关类别——生物制剂 / JAK 抑制剂 / 传统 DMARD / 糖皮质激素——置顶并标注「免疫抑制」**，急救人员一眼即可看到感染风险来源
- **收录口径**：未归档且未过结束日期（结束日当天仍算在用）；每行显示 药名（含商品名）· 剂量 · 频次，注射药附注射周期
- **两处都补**：紧急卡页面（信息卡区，此前完全无用药行）+ 紧急卡打印版 PDF（增「当前用药」分区）。`EmergencyCard` 增 `meds` 字段，`ReportViewModel` 与 `EmergencyViewModel` 两处导出同时生效
- **未建档也显示**：用药与健康档案相互独立——档案为空时仍完整显示药单（急救场景尤甚）
- **单页保护**：最多列 12 条，超出显示「另有 N 种未列出」；PDF 侧单行超长以省略号收尾，保住「打印版一页可读」的设计前提
- 存在免疫抑制类用药时，分区末尾附一行提示：「含免疫抑制 / 生物制剂类用药，感染风险较高——就诊时请告知医生。」

### 实现

- 新增纯函数 `domain/EmergencyMeds.kt`：活跃药物筛选（未归档 + 结束日期口径）/ 免疫抑制判定 / 置顶汇总 / 条数封顶，无 Android 依赖、可单测
- 新增 5 条文案：`emergency_meds_section` / `_tag_immunosuppressant` / `_empty` / `_more` / `_note`

### 验证

- 单测 114 → **130**（新增 `EmergencyMedsTest` 16 条：归档与结束日期口径、四类免疫抑制标注、置顶与截断优先、商品名拼接、剂量频次文案、空药单）
- release / debug 双包构建成功 + 正式签名验签
- 装机回归重点：①紧急卡页面应出现「当前用药」，生物制剂等条目带「免疫抑制」标签 ②打印版紧急卡 PDF 应有同一分区与提示行 ③药单清空后显示「（无在用药物记录）」 ④未建档时用药仍应显示

## [v1.0.25] — 2026-09-18

依据《ashkb Compose/UI 性能审查报告》（基线 v1.0.23）执行第一批：**用户可感知的功能性缺陷 + 低风险性能项**。v1.0.24 可直接覆盖安装（无数据库与功能变更）。

### 修复 · 跨零点日期冻结（5 个 ViewModel）

- **现象**：晚上打开 App 放到零点之后回来，「今日」页仍是**昨天**的日程 / 打卡 / 运动计划；症状、复诊页同理
- **根因**：`TodayViewModel` / `WellnessViewModel` / `ExerciseViewModel` / `CheckupViewModel` / `SymptomViewModel` 都在**构造时**求值 `val date: LocalDate = LocalDate.now()`，而 ViewModel 绑定 Activity，进程存活期间该值永不更新
- **修复**：改为 `MutableStateFlow<LocalDate>` + `init` 内的**到下一零点触发并循环**的 ticker；所有依赖日期的查询流走 `_date.flatMapLatest { ... }`。对外 `date` 仍是同名取值属性，调用点无需改动
- 注：审查报告只列了 3 个 VM，实测本库有 **5 个**（复诊、症状两处一并修掉）

### 修复 · 换药表单旋转屏幕丢输入（MedEditScreen）

- 17 个表单字段原先全是 `remember { mutableStateOf(...) }`——旋转屏幕即清空全部已填内容，医疗录入场景属严重体验缺陷
- 改为 `rememberSaveable`（共 21 处，含后续新增字段），旋转 / 系统回收后输入保留

### 修复 · 药品列表溢出屏幕且不可滚动（MedsScreen）

- 药品列表原先用普通 `Column` + `forEachIndexed`，**既无虚拟化也无滚动容器**——药品超过一屏即溢出，后面的药看不到也点不到
- 改用 `LazyColumn` + `items(meds, key = { it.id })`

### 修复 · 列表 item 缺 key（CheckupLists）

- 化验 / 复诊 / 疫苗三处 `items(...)` 未指定 key，补 `key = { it.id }`——避免列表状态错位，也为后续增删动画留出正确语义

### 改进 · 67 处 `collectAsState()` → `collectAsStateWithLifecycle()`（13 个文件）

- 原先所有屏幕用 `collectAsState()`，**不感知生命周期**：App 进后台后 Room 的 InvalidationTracker 观察者仍在收流，回前台时多条流同时发射触发重组风暴
- 依赖 `androidx.lifecycle:lifecycle-runtime-compose` 早已引入，无需新增依赖；带 `initial` 参数的调用点同步改为 `initialValue =`

### 改进 · TrendChart 绘制期零分配

- Canvas 绘制 lambda 内原先每帧新建 2 个 `Path`、1 个 `Brush`、`ticks.size` 个 `PathEffect`，并调 5 次 `textMeasurer.measure`（走 Skia paragraph 构建，最贵）——动画期 60fps 下必然掉帧，报表页 6 图并排时最明显
- 全部提到绘制外并用 `remember` 缓存（虚线样式、折线/填充 Path 复用 + `reset()`、填充 Brush、Y/X 轴刻度文本布局、阈值 chip 与选中气泡文本）；绘制结果与原实现逐字符等价，**函数签名与调用点未变**

### 改进 · 图表数据源 remember（修复折线图入场动画反复重播）

- `ReportScreen` 6 处 `TrendPoint` 映射与 `WellnessScreen` 体重点集原先未 `remember`——`List.map` 每次返回新实例，而 `TrendChart` 的入场动画以 `points` 为 key，导致任意一次父级重组都会让图表**从头重播动画**（表现为闪烁）
- 各自 `remember(源数据) { ... }` 包一层

### 改进 · 化验页派生计算 remember

- `CheckupLabImaging` 的 `groupBy` / `maxOfOrNull`（上一版新增的日期分组折叠代码）与 `partition` 原先每次重组重跑；前者还写在 `LazyListScope` 内（非 Composable 作用域），现上提到 `LazyColumn` 之外并 `remember`
- `CheckupViewModel.labResultsFor(id)` 原先每次调用返回**新冷流**，导致化验详情对话框列表在父级任何状态变化时闪一下；改为按 id 缓存的 `StateFlow`

### 审查报告事实更正（据实测）

- Room 版本：报告写「v6」，实为 **v9**（报告自身架构图处又写「v9 迁移」，前后矛盾）
- 实体数：报告写 27，实为 **25**
- 冻结日期的 ViewModel：报告列 3 个，实为 **5 个**
- 「任意一次数据变化都重组整个屏幕」略有夸大——Compose 仍会跳过参数未变的子 Composable，准确说法是父函数体每次重跑（含列表构造、闭包构造、派生计算）

### 本批未做（属结构性重构，另行排期）

MedEdit / Backup / Knowledge 的字段级 Composable 拆分、`BackupViewModel`/`ReportViewModel` 合并为单一 UiState、Compose Strong Skipping Mode、`DividerList` 展平进父 `LazyListScope`、Sheet 内嵌套滚动改造、`AppShell` 按需创建 ViewModel。

### 验证

- 既有 114 条单测全部通过；release / debug 双包构建成功 + 正式签名验签
- 装机回归重点：①零点后（或把系统时间调过零点）回 App，今日页应切到新的一天 ②换药表单填一半旋转屏幕，内容不丢 ③药品列表超过一屏时可滚动 ④报表页 6 个折线图入场动画只播一次、不闪烁
- 说明：本次改动以 UI 结构为主，属设备实测项，建议装机后按上述四点验证

## [v1.0.24] — 2026-09-18

文案资源化收尾（v1.0.10 §遗留项）：通知 / ViewModel / PDF 三层硬编码中文下沉 strings.xml；顺带修复 PDF 用药行打印 `null` 的缺陷。v1.0.23 可直接覆盖安装（无数据库与功能变更）。

### 修复 · 复诊报告 PDF 口服药行打印字面量 `null`

- **现象**：复诊报告 PDF 的「当前用药」区，成分为口服的药（以及未设注射周期的注射药）会打印成 `· 甲氨蝶呤｜10mg｜每日两次｜口服null`
- **根因**：`x + y + z?.let{…} ?: ""` 中 `+` 的优先级**高于** `?:`，实际解析为 `(x + y + z?.let{…}) ?: ""`——elvis 作用于整个拼接结果（恒非空）从不触发；`injCycleDays` 为 null 时 Kotlin 的 `String.plus(Any?)` 把字面量 `"null"` 拼了进去
- **修复**：注射周期段先落成局部值再拼接。**这是打印给医生的报告，属影响输出正确性的缺陷**

### 改进 · 文案资源化（三层共 124 条下沉 strings.xml）

- **通知层**（`NotificationHelper`，9 条，`notif_` 前缀）：渠道名称 / 描述、服药提醒标题与正文（含加急态）、「已服用」动作按钮
- **ViewModel 层**（`Backup` / `Report` / `Knowledge` / `Emergency`，36 条，`vm_` 前缀）：全部 snackbar 提示（备份 / WebDAV / 恢复五步 / 演练 / 档案 JSON / 报表与紧急卡 PDF 失败）
  - `KnowledgeViewModel` 无 Context，其分类标签 `KB_CATEGORIES` 改存资源 ID（Int），在 `KnowledgeScreen` 的 `FilterChip` 内用 `stringResource` 解析——与 v1.0.10 对「底栏 Tab / 复诊五 Tab」的处置方式一致
  - `vm_emergency_pdf_failed` 被 `ReportViewModel` 与 `EmergencyViewModel` 两处复用（原文相同，合并为一条）
- **PDF 层**（`ReportPdfWriter`，79 条，`pdf_` 前缀）：复诊报告与紧急卡的全部标题 / 字段名 / 分节标题 / 空态说明 / 免责声明，以及分期与用药频次的映射文案
- **保持不变**（非 UI 文案，属标识符而非可翻译文本）：MIME 类型、`exports` 目录与文件名、FileProvider authority、通知渠道 ID、DB 枚举比较键（`injection`/`high`/`stable` 等）、`joinToString("；")` 的数据分隔符、KDoc 与注释
- PDF 版式零影响：79 条资源的渲染结果与原字面量逐字符等价（含分隔符与空格位置），行数 / 分页点 / 折行位置均不变

### 边界说明

`data/` 与 `domain/` 层的异常消息与领域标签（如 `WebDavClient` 的 `DavException` 文案、`Labels` 枚举标签、`ClinicalThresholds` 说明、AI 导入模板）**未资源化**，原因：①domain 层按设计保持纯 JVM（无 Android 依赖、可单测），注入 Context 会破坏该边界 ②AI 导入模板是提示词文本而非界面文案 ③种子数据与枚举键是数据而非 UI。

### 验证

- 既有 114 条单测全部通过（本次为文案搬迁 + 一处缺陷修复，无新可测纯逻辑）
- release / debug 双包构建成功 + 正式签名验签
- 装机回归重点：导出复诊报告 PDF，确认「当前用药」口服药行**不再出现 `null`**；服药提醒通知（含加急重复提醒）文案正常；备份 / WebDAV / 恢复 / 档案导入导出的提示文案与 v1.0.23 一致

## [v1.0.23] — 2026-09-18

体验优化：化验 Tab 日期分组折叠（HANDOFF 待办池遗留项）。v1.0.22 可直接覆盖安装（无数据库与功能变更）。

### 改进 · 化验列表日期分组折叠

- **背景**：化验 Tab 此前按日期分组、全部展开（组内异常置顶 + 正常项折叠 + 翻页均为 v1.0.7 已做）——历史化验次数多后，每次进页面都被几十个全展开的旧日期撑得很长，翻屏找最近的报告很费劲
- **改法**：每个日期分组卡片头部新增「展开 / 收起」按钮（图标 + 文字），收起时只剩日期 + 「N 项 · M 项异常」一行摘要
- **默认展开规则贴合复诊沟通导向**：**有异常项的日期** 或 **最近一次化验日期** 默认展开，其余默认收起——进页面第一屏即是要紧内容
- 折叠状态用 `rememberSaveable` + 稳定 item key 保持：翻页加载更早记录、列表滚动回收、旋转屏后展开/收起状态不丢失
- 「记录」Tab 的化验详情弹窗（单次化验）不涉及日期分组，不受影响

### 验证

- 既有 114 条单测全部通过（纯 UI 层改动，无新可测纯逻辑，不新增测试）
- release / debug 双包构建成功 + 正式签名验签
- 装机回归重点：进化验 Tab 应见有异常/最近一次的日期展开、纯正常的旧日期收起；点「展开 / 收起」切换后，翻页加载更早记录再回来状态应保持

## [v1.0.22] — 2026-09-18

技术债清理：ViewModel 状态流封装（代码审查报告 P2 项）。v1.0.21 可直接覆盖安装（无数据库与功能变更）。

### 改进 · 清除 `as MutableStateFlow` 强转（44 处）

- **背景**：`BackupViewModel` 与 `ReportViewModel` 此前的写法是 `val busy: StateFlow<Boolean> = MutableStateFlow(false)`——声明为只读类型、实参是可变实例，写入时再 `(busy as MutableStateFlow).value = true` 向下强转。这是运行时非受检转型：一旦有人把声明改成真正的只读实现（如 `stateIn` 产物），写入处会直接抛 `ClassCastException`，而编译期毫无提示
- **改法**：统一为 MVVM 惯用的「私有可变 + 公开只读」——`private val _busy = MutableStateFlow(false)` 搭配 `val busy: StateFlow<Boolean> = _busy`，写入走 `_busy.value`。与既有 `_stage` / `_davUrl` 等正确写法对齐，外部（Screen）只能 collect 只读流，可变性收口在 VM 内部
- 涉及 `BackupViewModel`（busy / message / davBackups / pendingRestore / pendingFileName / restoreResult，28 处）与 `ReportViewModel`（overview / trends / busy / message，16 处）；纯结构性重写，所有业务逻辑、消息文案、执行顺序逐行保持不变

### 验证

- 既有 114 条单测全部通过（本项为纯内部结构改动，无新可测纯逻辑，不新增测试）
- release / debug 双包构建成功 + 正式签名验签；装机回归重点：备份 / WebDAV / 恢复 / 报表 PDF 各入口的 busy 与提示行为应与 v1.0.21 完全一致

## [v1.0.21] — 2026-09-18

技术债清理：知识库检索优化（代码审查报告 P1 项）。v1.0.20 可直接覆盖安装（Room v8→v9 自动迁移）。

### 结论先行：不迁 FTS4（实证会让中文搜索失效）

- 审查建议「迁 Room FTS4 虚表或对 title 建索引」。**经本机 SQLite 实测，FTS4 会使中文子串搜索直接失效**：FTS4 内置分词器（simple / unicode61）按「字母数字连续段」切词，中文无空格分隔，一整句会变成一个 token——对「强直性脊柱炎患者用药注意事项」执行 `MATCH '强直'` 命中 **0** 条，而现状 `LIKE '%强直%'` 命中 1 条；`MATCH '强直*'` 仅在查询恰为句首时命中
- Room 只提供 `@Fts4`（无 `@Fts5`）；FTS5 的 `trigram` 分词器虽支持 CJK 子串，但依赖 SQLite 版本，minSdk 26（Android 8）不可靠
- 另：知识库为**固定 47 条种子、无用户新增入口**，种子 JSON 合计 93 KB——原「数据量增长后会变慢」的前提不成立；真正的开销来自**逐键查库**这一调用方式，而非缺少索引（`LIKE '%x%'` 前导通配符本就用不上索引）
- 故保留 LIKE 子串语义，改从三处降开销（见下）

### 改进 · 单列检索文本替代三列 OR

- `kb_entries` 新增 `search_text` 列（`title` + `summary` + `payload` 换行拼接）——每行谓词求值由 3 次降为 1 次
- 迁移 v8→v9：`ALTER TABLE` 加列并回填存量 47 条；种子导入同步写入（口径统一收口在 `domain/KbSearch`）
- **旧备份兼容**：恢复按「备份自身的列集」按名 INSERT（R9 机制），故旧备份（无此列）仍可正常恢复；恢复流程在双校验通过后、提交前按当前口径回填 `search_text`，等价于补跑 v8→v9——不回填会导致恢复后知识库搜索整库失配
- 检索文本不预先转小写：SQLite 的 `LIKE` 默认对 ASCII 大小写不敏感（`case_sensitive_like` 默认 OFF），小写化无必要且会引入 Kotlin（Unicode 折叠）与 SQLite `lower()`（仅 ASCII）的语义漂移

### 改进 · 查询防抖 + 结果上限

- 搜索框逐键查库改为**输入停顿 250ms 后查一次**（`KnowledgeViewModel` 在 query 流上加延迟分支，清空输入不进延迟、立即回落列表/分类视图）
- 查询加 `LIMIT 200` 安全上限（知识库规模远小于此，正常不触发）

### 验证

- 单测 107 → 114（新增 `KbSearchTest` 7 条：检索文本口径、中文子串命中、ASCII 大小写不敏感、空查询/未回填不命中、换行分隔防跨字段假匹配、常量约束）
- 全部测试通过；release / debug 双包构建成功 + 正式签名验签

## [v1.0.20] — 2026-09-18

装机冒烟反馈第五批（X 系列，3 项）。v1.0.19 可直接覆盖安装（无数据库结构变更）。

### 化验 · 偏高/偏低点击看参考范围（X1）

- 化验列表与化验详情弹窗中，点击「偏高 ↑」「偏低 ↓」彩色胶囊即在该行下方展开参考范围（如「参考范围 2.9 – 8.2 mg/L」）；只有单侧界限时按「≥x / ≤x」表述；未记录参考范围时如实说明
- 复诊沟通场景：医生问「正常值是多少」点一下就有，不用翻化验单原件

### WebDAV · 备份文件名带时间戳（X2）

- **背景**：原文件名按日命名（ashkb-backup-2026-09-18.ashkb），同一天第二次备份 PUT 同名文件直接覆盖——白天多次备份只有最后一份存活
- 文件名改为 `ashkb-backup-YYYY-MM-DD-HHmmss.ashkb`，同天多份互不覆盖
- 轮换策略适配：按「日 7 + 周 4 + 月 6」保留集合内每天只留最晚一份（同日旧份在下次轮换时清理，总量语义不变）；旧按日命名的存量文件照常列出/下载/轮换，视为当日 00:00:00
- 远程恢复列表排序修正：混合新旧格式时按规范化时间键排序，旧格式不再错误地排在新文件之前

### WebDAV · 新机直接恢复引导（X3）

- 「从 WebDAV 恢复」按钮在未配置 WebDAV 时，恢复区显示引导文案：换新机先「配置 WebDAV」登录同一账号，即可直接拉取服务器历史备份恢复，无需先在本机生成任何备份（v1.0.19 已支持该流程，本次补引导消除疑惑）

### 验收

- X1：化验 Tab / 记录 Tab 化验详情 → 点偏高/偏低胶囊 → 展开参考范围
- X2：同一天两次「备份到 WebDAV」→ 服务器 /ashkb/backup/ 出现两份带时间戳的文件；「从 WebDAV 恢复」列表两份都在、排序正确
- X3：新机（或清除数据后）→ 配置 WebDAV → 「从 WebDAV 恢复」直接列出旧备份并可下载恢复

## [v1.0.19] — 2026-09-18

装机冒烟反馈第四批（W 系列续，1 项新功能）。v1.0.18 可直接覆盖安装（无数据库结构变更）。

### WebDAV · 远程备份列表恢复（W4）

- **背景**：恢复此前只支持本地选 .ashkb 文件——换机 / 本地文件丢失后，明明 WebDAV 上有备份却取不回来
- **功能**：恢复区新增「从 WebDAV 恢复」按钮（配置 WebDAV 后可用）——打开即拉取服务器 /ashkb/backup/ 下的备份列表（PROPFIND Depth:1，含文件大小，按日期倒序最新在前），点击某份即下载到本机，随后走既有旁路解密五步恢复（口令校验 → pre-restore 快照 → 覆盖写入 → 双校验）
- **安全**：下载前对文件名做形状校验（仅放行 ashkb-backup-*.ashkb 且无路径字符）；沿用强制 HTTPS 与 Basic 认证；列表/下载失败给出明确原因（非静默）
- **验收**：配置好 WebDAV → 备份到 WebDAV 生成至少一份 → 「从 WebDAV 恢复」应列出该文件 → 点击下载 → 输入备份口令旁路解密 → 执行恢复

## [v1.0.18] — 2026-09-18

装机冒烟反馈第三批（W 系列，3 项修复）。v1.0.17 可直接覆盖安装（无数据库结构变更）。

### WebDAV · 轮换清理重写 + 全程阶段反馈（W1 / W2）

- **根因**：备份到 WebDAV 尾部的轮换清理原实现逐日盲发 173 个 DELETE（过去 180 天每天一发，绝大多数 404）——每个 404 也要一次完整 TLS 握手，坚果云上耗时数分钟；期间 busy 状态卡死全部按钮（含「测试连接」入口），用户既等不到反馈也无法再次操作
- **修复**：轮换改为 PROPFIND Depth:1 一次列目录，只 DELETE 服务器上真实存在且超窗的备份（请求数 173 → 1 + 实际过期数，通常 0–2 个）；列目录失败（服务器禁列）本轮跳过清理，不阻塞备份
- **阶段反馈**：备份全程分步显示当前动作（导出快照 → AES-256-GCM 加密 → 上传 → 清理过期备份），不再是干等一条静态「处理中…」
- **兜底**：备份全链路 120 秒总超时（防任何未预期挂起），连接 / 读取超时收紧（10s / 20s）——最坏情况下两分钟内必然出结果，busy 必然复位，全部按钮恢复可用
- **验收**：正常网络备份应在数秒到数十秒内完成并显示每一步；拔网线场景 120s 内报错且可再次测试连接

### BASDAI · 未作答按 0 提交 + 「无」可点（W3）

- **背景**：滑杆「点当前值零回调」缺陷两轮手势修复（v1.0.11 / v1.0.17）真机仍不可靠——用户实测「只有疲劳感、没有疼痛感」时，其余五题答不上 0、提交按钮不亮
- **修复（提交语义）**：BASDAI 语境 0 = 无症状——未作答的题按 0 计入总分，**至少作答一题即可提交**（防空记录）；「只有疲劳感」= 答 Q1 后直接提交，其余按 0 等价于逐题答 0。对话框内明示「已作答 N/6 题，未作答的按 0（无症状）计入总分」，总分实时预览（含按 0 补全）
- **修复（输入路径）**：评分控件两端标签可点——点「无」显式答 0、点「最重」设满分（当前值命中端点时标签高亮）；提供一条不依赖滑杆手势的 100% 命中路径，每日症状评分同步受益
- **验收**：新建 BASDAI 只答 Q1 即可提交，总分 = Q1/5；点「无」立即把该题记为 0（数字位从「—」变「0」）

## [v1.0.17] — 2026-09-18

装机冒烟反馈第二批（U 系列，7 项修复）。v1.0.16 可直接覆盖安装（无数据库结构变更）。

### 补剂 · 档案删除 + 服用历史查询（U1 / U3）

- **删除**：补剂档案每项新增「删除」入口（二次确认）——补剂从档案移除；已产生的服用记录快照自持、继续保留（可在备份中追溯），今日打卡状态不受影响
- **历史**：点击补剂行打开「服用记录」sheet——近 90 天已服次数 + 按日倒序的打卡时刻列表，「我什么时候吃过」一查即知；按 sup_id 弱引用或名称快照双路匹配，补剂删除后历史依然可查

### 营养与骨健康 · 记录删除（U4，误录场景）

- 体征：录入 sheet 底部「删除今日体征记录」（确认后当日回到未记录态）
- 体重：体重卡新增「管理」入口——近 30 天记录逐条删除（每条独立确认，说明对趋势图的影响）
- 身体指标：sheet 底部「删除这条记录」（删后回退到上一条）
- 饮食画像：sheet 底部「清除饮食画像」（回到未设置态，可重填）
- 忌口清单本就有删除，保持不变

### BMI 自动计算（U2）

- 身体指标 sheet：身高 + 最新体重齐备时 BMI 自动回填（按最新一条体重记录计算，身高 50–250cm 合理区间外不参与），下方注明计算依据；仍可手动覆盖，无体重时提示先记录体重

### BASDAI 评分 0 直选（U5）

- 根因：M3 Slider 对「落在当前值上的点按」不产生任何回调——未作答态滑杆停在 0，直接点 0 永远无响应，只能先动一下再归零
- 修复：滑杆上叠一层不消费事件的旁听手势，真点按（位移小于触摸阈值）按落点换算刻度并显式提交；未作答态点滑杆最左端即答 0，症状页全部评分控件同步受益；拖动 / 点轨道跳转行为不变

### 备份口令密码键盘（U6）

- 备份口令 / 恢复口令 / WebDAV 密码三处输入框统一 `KeyboardType.Password` + 关闭自动纠错——输入法（含微信输入法中英文键盘）不再出候选词，均为单行

### 复诊报告 PDF · 化验仅列异常项（U8）

- 近 180 天化验段从「全量前 20 项」改为「仅异常项」（高 ↑ / 低 ↓ / 异常 ⚠），标题标注「异常 X / 共 Y 项」；全部正常时明确说明，医生关注点直接聚焦

## [v1.0.16] — 2026-09-18

待办池清理第二批（C 组）：release 开启 R8 混淆与资源压缩。v1.0.15 可直接覆盖安装（无数据库与功能变更）。

### 构建 · release 开启 R8 混淆 + 资源压缩（C1 · 上架前必做）

- release 构建 `isMinifyEnabled` + `isShrinkResources` 双开：代码混淆（类 / 方法重命名）+ 死代码裁剪 + 资源压缩——逆向分析门槛显著提高，医疗数据类 App 上架前标配
- keep 规则（proguard-rules.pro）三层覆盖：
  - Room 实体全保留——备份引擎按「表列名 = 实体字段名」读写、种子 JSON 按字段名解析，字段名即磁盘数据格式，混淆即损坏
  - kotlinx.serialization 官方规则——Navigation-Compose 类型安全路由（Routes.kt 全部 @Serializable）经 `$$serializer` 合成方法反射查找，混淆会在运行时导航崩溃
  - Room / Compose / Navigation 由各自 AAR 自带 consumer rules 覆盖；manifest 组件 AGP 自动 keep；枚举常量名 R8 不混淆（`valueOf` 语义）
- 产物：mapping.txt 随构建生成于 `outputs/mapping/release/`（崩溃堆栈还原用，不随 APK 分发）
- 装机冒烟验证点（上架前必验）：冷启动 → 五 Tab 导航与二级页（路由反射）→ 备份导出 / 恢复演练 → WebDAV 探针 → PDF 导出

## [v1.0.15] — 2026-09-18

待办池清理第一批（A 组小修 + README 对齐）。v1.0.14 可直接覆盖安装。

### 修复 · 恢复双校验失败无自动回滚（审查 P2）

- **风险**：双校验发生在事务提交之后——校验失败时脏数据已落库，只能靠 pre-restore 快照人工找回
- **修复**：双校验移入恢复事务内，任一表行数或 SHA-256 不匹配即不提交、事务整体回滚——库保持恢复前状态；恢复台账与结果消息同步新语义（校验失败登记 FAILED 并注明已回滚）
- **顺带修复 v1.0.14 回归**：恢复旧备份（disease_stage=active）时，归一化 UPDATE 先于校验执行导致 profile 表 SHA 必不匹配、恢复被误报为校验失败；现校验针对备份原文，归一化（与 Room 迁移 v7→v8 同义）移至校验通过后、提交前——恢复旧备份 = 恢复 + 补跑数据迁移

### 隐私 · 分享产物残留清理

- 明文档案 JSON / 复诊报告 PDF / 紧急卡 PDF 写入 files/exports 后原本无限期残留；现应用启动时自动清空该目录（分享动作本身不受影响，导出物在分享时仍存在，下次启动清除）

### 修复 · 通知权限反复弹窗

- 每次冷启动无条件申请 POST_NOTIFICATIONS，被拒后仍反复触发系统弹窗；现仅首次冷启动请求一次（落「已询问」标记），此后不再打扰，可随时在系统设置中自行开启

### 代码清理与文档

- 删除 MeViewModel.saveMedication 中未收集的死代码 Flow
- README 对齐现状：版本号 / 97 条单测 / Room v8（25 实体表） / 运动判读口径修正（疾病分期三态 + 脊柱活动度判读，疼痛 / 晨僵 / 体温为参考展示）；备份说明文案表数修正（27 → 25）

## [v1.0.14] — 2026-09-18

修复计划第三批（R1 / R8）：疾病分期三态化、恢复表名白名单。v1.0.13 可直接覆盖安装（数据库自动迁移 v7→v8）。

### 改进 · 疾病分期三态化（R1 · P0）

- **背景**：原分期只有「活动期 / 缓解期」二态，药物控制下的稳定与急性发作无法区分——两类人群被压进同一档保守过滤，控制中用户被过度限制
- **改进**：分期改为三态——**缓解期（stable）/ 控制中（controlled）/ 发作期（flare）**，档案编辑页三选一；「未评估」仍单独存在，引擎按发作期保守处理
- 运动引擎按三态取矩阵键（不再二值化）；**发作期当日处方只出 L1 轻柔项，红榜 L2/L3 一律暂停**（引擎级兜底，矩阵放行也拦）；控制中继承原「活动期」行为
- 存量数据自动迁移（Room v7→v8）：原「活动期」并入「控制中」，语义无损；旧备份恢复与档案 JSON 导入时同样归一化
- 兼容旧种子：v8 之前安装的运动知识条目矩阵只有 stable/active 两键，引擎回退按 active 键判读（行为不变）
- 「我」页、运动处方色带（三色区分）、PDF 报告同步三态文案
- **验收**：发作期当日处方只剩 L1；控制中行为与原活动期一致；新装 / 覆盖安装各验证一遍

### 安全 · 恢复表名白名单（R8 · P2）

- **风险**：恢复引擎逐表 DELETE + INSERT 时表名直接拼接 SQL——构造恶意备份携带任意表名（含反引号逃逸）可向任意表写入数据
- **修复**：恢复前校验备份中每个表名 ∈ 当前库真实表清单（sqlite_master 动态发现，随 schema 演进自动更新）；任一未知表名即整体拒绝恢复（事务开始前校验，拒绝时零写入）
- **验收**：构造含未知表名的备份恢复被拒且库无变化；正常备份恢复不受影响；本批共新增 9 条单测固化（共 97 条）

## [v1.0.13] — 2026-09-17

修复计划第二批（R2 / R3 / R4）：恢复语义二选一、WebDAV 强制 HTTPS、AI 导入未识别行提示。v1.0.12 可直接覆盖安装。

### 改进 · 恢复语义 UI 明示 + 二选一（R2 · P2）

- **背景**：原恢复为「按表覆盖」——备份里有的表整表替换，没有的表保留当前数据；旧备份恢复后并非回到备份时点，用户无感知
- **改进**：恢复确认页新增「恢复方式（二选一）」：**完整回滚**（默认——先清空全部用户表再按备份重建，真正回到备份时点）与**按表合并**（只覆盖备份里包含的表，其余保持现状 = 原行为），两种模式的后果文案写清
- 恢复台账与结果消息注明本次模式；取消恢复后选择重置为默认「完整回滚」
- pre-restore 快照两种模式均照常生成（误恢复退路不变）
- **验收**：用 v6 旧备份（无 imaging_records 表）恢复——选「完整回滚」影像记录清空、选「按表合并」影像记录保留

### 安全 · WebDAV 强制 HTTPS（R3 · P2）

- **风险**：全局 `usesCleartextTraffic="true"` 为兼容明文 http 服务器而开；WebDAV 走 Basic 认证，账号密码若经明文链路传输会被链路上任何人截获
- **修复**：①删除全局明文流量开关（targetSdk 34 默认即禁止明文）；②保存 / 测试连接 / 备份入口一律校验，`http://` 地址直接拒绝并提示「仅支持 https:// 地址」；③v1.0.5 及以前的旧明文配置若为 http:// 不再迁移，删除即失效
- README「数据与隐私」一节同步说明
- **验收**：填 `http://` 地址无法保存且提示明确；坚果云 https 链路 probe / 上传 / 回读比对 / 轮换全通

### 改进 · AI 导入「N 行未识别」提示（R4 · P2）

- **背景**：解析器对无法识别的行静默丢弃——AI 复读模板说明、格式漂移、空结果行都会被丢，用户不知道哪几项没进库
- **改进**：`parseLab` / `parseImaging` 返回结构新增 `skippedLines`（未识别行原文）；导入确认页显示「已导入 N 项 · M 行未识别」，点按可展开查看原始行，方便手动补录
- 影像解析：首个键值行之前无法归入任何字段的行计入（键行之后视为多行字段值，不计入）
- **验收**：粘贴含噪声的化验文本，M 与实际噪声行数一致且展开内容与原文一致；新增 2 条单测固化（共 88 条）

## [v1.0.12] — 2026-09-17

修复计划第一批（R6 / R5 / R7）：停药孤儿闹钟、演练写生产库、备份 schema 标签脱节。v1.0.11 可直接覆盖安装。

### 修复 · 停药后仍收服药提醒（R6 · P1，治本+兜底）

- **现象**：停用药品后，未来 7 天内仍按原计划弹出该药的服药提醒与升级重查
- **根因**：`stopMedication()` 归档后调 `rescheduleAll(活跃列表)`，而取消闹钟的 `cancelAllFuture()` 只遍历传入的活跃药——被停药品的闹钟无人取消；闹钟触发时 `medicationById` 能取到归档药（归档≠删除）、`slotsFor()` 又不检查归档标志，提醒照发
- **治本**：停药时先单独取消该药全部未来闹钟（request code 算法对归档药同样成立），再重排活跃药
- **兜底**：闹钟触发时查库发现药品已归档 → 静默取消通知并终止，防任何其他路径残留的孤儿闹钟（含重启后 BootReceiver 无法取消的场景）
- **验收**：停用每日口服药后，未来 7 天不再收到该药任何提醒与重查（`adb shell dumpsys alarm` 无该 medId 残留闹钟）

### 修复 · 恢复演练对生产库全量覆盖丢数据（R5 · P1，方案A旁路副本）

- **现象**：演练流程为「导出 → 加密 → 解密 → **真实写回生产库** → 复核」；导出到写回的窗口内，通知栏打卡 / 闹钟 / BootReceiver 的任何写入会被恢复的 DELETE 抹掉，且复核的两份快照都不含该行——**数据丢了，演练还报告成功**
- **修复**：演练前生产库 WAL checkpoint 后整库复制为临时副本（独立 Room 实例打开），导出→加密→解密→恢复→双校验→复核全程在副本上跑；结束关闭并删除副本，**生产库零写入、可正常使用**（对齐备份协议 §6「演练用旁路模式」的既有规定）
- 台账照旧登记 DRILL；演练详情注明「旁路副本，生产库零写入」
- **验收**：演练进行中从通知栏点「已服用」，打卡记录不丢；副本文件无残留

### 修复 · 备份 schema 标签与 Room 版本脱节（R7 · P2）

- **现象**：备份头写 `schema_version=6`，但 Room 已是 7（v7 加了 imaging_records 表）——标签失真，未来版本兼容判断会误判
- **修复**：标签改为运行时从 `PRAGMA user_version` 派生（即 Room 当前版本），随迁移自动演进，**杜绝再次脱节**；恢复/导入的版本闸门同步改用派生值
- 兼容性：v6 旧备份仍可正常恢复（6 ≤ 7，列集兼容逻辑已在 verifyAgainst 实现）；未来 R1 升 v8 时备份标签自动跟随
- 单测：VaultCipherTest 改用本地 schema 值（派生函数依赖 SupportSQLiteDatabase，纯 JVM 不可构造）

## [v1.0.11] — 2026-09-17

修复评分滑杆「答 0 无法提交」的输入缺陷（BASDAI 六题必须全部作答，0 选不上）。v1.0.10 可直接覆盖安装。

### 修复 · 评分滑杆点按当前值不落账

- **现象**：今日症状自评 BASDAI 每个选项都作了答、写了备注，提交按钮仍不亮；实际是每个选项都得选 1-10，「0」永远选不上
- **根因**：Material3 Slider 对「落在当前值上的点按」不回调 `onValueChange`（值未变化即丢弃）。评分未作答时滑杆显示 0，患者答 0 点在滑杆左端 → 点按被静默吞掉 → 该题保持「未记录」→ 提交按钮因校验不过而禁用
- **修复**：记录手势期最近原始值，以 `onValueChangeFinished`（任何点按 / 拖动收尾必回调）兜底提交——点在 0 上即记录为 0，与 1-10 行为一致
- **顺带**：未作答态数字位显示「—」替代「0」，「未记录」与「记录为 0」不再混淆
- 影响范围：BASDAI 六题、每日症状（夜间痛 / 整体疼痛 / 心情 / 睡眠 / 疲乏）、发作峰值疼痛

## [v1.0.10] — 2026-09-17

UI 文案资源化（UI 改版方案 PR4）：硬编码中文抽取至 strings.xml。v1.0.9 可直接覆盖安装。

### 改进 · 文案资源化（国际化基建）

- UI 层约 680 处硬编码中文文案抽取为 `stringResource` 调用，`strings.xml` 收录 650 条资源（含插值模板 `%n$s` 占位符），后续多语言 / 文案统一维护不再需要逐文件改代码
- 覆盖范围：静态文案（581 条）+ 简单插值模板（66 处，如「注射 药名」「备份台账（N）」）+ 语义描述（滑杆 contentDescription）等特例
- 结构性适配：底栏 Tab、复诊五 Tab、AI 导入枚举等由「构造期固定文案」改为 `@Composable` 标签函数，遵循 Compose 资源加载约定

### 修复 · 备份解密契约

- `VaultCipher.decrypt` 对损坏文件头的 JSON / Base64 解析异常未兜底为 `VaultException`，特定篡改下会以运行时异常逃逸（单测「AAD 绑定防跨协议改头」间歇性暴露）；现统一转译为「文件头损坏」

### 遗留

- ViewModel 层消息（snackbar 文案）、NotificationHelper 通知文案、PDF 直绘文本仍为硬编码，留待后续版本（需注入 context，模式不同）

## [v1.0.9] — 2026-09-17

UI 改版方案收尾：启动图标品牌色同步与文案细节。v1.0.8 可直接覆盖安装。

### 改进 · 启动图标品牌色同步

- v1.0.7 起应用主色已由 teal（#0E7A6E）换为深灰蓝（#416476），但启动图标背景仍为旧 teal，本次同步为新主色，桌面图标与应用内主题观感一致（UI 改版方案 §14 品牌一致性遗留项）

### 改进 · 文案细节

- 发作历史列表日期范围连接符由「→」改为「至」，进行中的发作显示「起始日 至今」（UI 改版方案 §4.1：禁止用字符排版）

## [v1.0.8] — 2026-09-17

化验单展示面向复诊沟通优化。v1.0.7 可直接覆盖安装。

### 改进 · 报表页数据备份入口

- 「数据备份（R20）」由纯文字说明改为可点击导航行，直达「备份与数据」页（原需绕道「我的」页）

### 改进 · 化验单展示（复诊 / 医生沟通）

- **异常项置顶**：每个日期分组内偏高 / 偏低指标排在最前（复诊时先看要紧的）
- **正常项默认折叠**：正常指标收进「展开正常项（N）」，点按可展开 / 收起；异常与正常区之间加分隔线
- **翻页加载**：化验列表默认显示最近 100 行，底部「加载更早的化验记录」每次追加 100 行，全部加载完按钮自动隐藏（数据本身全量永久保存在库里，仅列表有显示窗口）
- 「化验」Tab 各日期分组与「记录」Tab 的化验详情弹窗同步生效；AI 导入的解析预览保持报告原顺序（便于与纸质单核对）

## [v1.0.7] — 2026-09-16

按《ASHKB UI 改版方案》落地设计系统与主要页面重构；修复注射部位无法选上臂的问题。v1.0.6（正式签名）可直接覆盖安装。

### 修复 · 注射部位选择

- 生物制剂（依那西普等）注射部位新增「左上臂 / 右上臂」（原仅大腿 / 腹部共 4 个部位）
- 部位选项改自动换行布局（FlowRow），窄屏下不再截断

### 修复 · WebDAV 连接测试失败（HTTPS 下 MKCOL 实际未发出）

- **根因**：Android 的 `HttpsURLConnectionImpl` 把真实实现藏在 `delegate` 字段指向的 `HttpURLConnectionImpl` 里，原反射覆写直接在包装类上找 `method` 字段，找的是无效影子字段且失败被静默吞掉——MKCOL 请求实际按 **GET** 发出，服务器自然返回 404「资源不存在」。地址与密码都对也照样报「MKCOL 失败：HTTP 404」
- 修复反射逻辑：先解引用 `delegate` 再沿类层级找 `method` 字段覆写，失败时明确报错而非静默降级
- 测试连接改为先 PROPFIND 检查服务器地址，按状态码区分原因：404 = 地址在服务器上不存在（附坚果云正确地址示例）、401 = 账号或应用密码错误、403 = 无权限、3xx = 需改用跳转后的最终地址

### 新增 · 设计系统与导航架构

- **设计 token 主题**（`ui/theme`）：间距 / 圆角 / 字阶 / 色调 / 触控尺寸统一收口，新增深色模式（`values-night`）
- **统一组件库**（`ui/components`）：SectionCard、StatusChip / AlertBanner（状态三重编码：文字 + 图标 + 颜色）、TrendChart（坐标轴 / 整数刻度 / 阈值线 / 拖动读数）、DividerList、表单与导航组件等
- **Navigation-Compose 路由**：二级页真正独立成页，页面切换不再丢失底栏导航状态
- **全局消息总线**（GlobalMessages → Snackbar）：操作提示不再逐条弹窗打断
- **领域收口**：临床阈值集中 `ClinicalThresholds`（依从 90/70、BASDAI ≥ 4 等）；枚举标签统一 `Labels`，界面不再出现英文枚举 key

### 页面重构

- **紧急卡**：反向设计——「120 急救」大按钮固定底部常驻；黑名单场景置顶横幅；事件记录迁 ModalBottomSheet；页面内可直接导出紧急卡 PDF
- **运动**：处方 hero 区（结合昨日疼痛 / 晨僵 / 体温展示判读依据）；黑榜拦截项置顶横幅；次日反馈表单迁 ModalBottomSheet
- **营养与骨健康**：体征 hero 区；体征 / 身体成分 / 饮食三组吸顶分组头；体重趋势图（TrendChart）
- **知识库**：搜索框与分类筛选吸顶；条目卡改为可点击 Surface；分类标签 StatusChip 化；详情「查看原文」改为按钮
- **症状与自评**：警报卡 StatusChip 三重编码；发作 / BASDAI 历史列表改 DividerList
- **复诊管理**：化验 / 影像 AI 导入迁 ModalBottomSheet；异常值区分偏高 / 偏低并三重编码
- **我的**：药单独立成页，药品与健康档案编辑从弹窗改为整页编辑
- **备份**：WebDAV 配置迁 ModalBottomSheet；恢复台账「查看全部」；校验结果 ✓ / ✗ 图标化
- **报表**：操作提示改全局 Snackbar；警告条 StatusChip 化

### 可及性

- 可点击元素最小 48×48dp；全部图标显式 contentDescription（装饰性图标由相邻文字承载语义）
- 状态展示三重编码（文字 + 图标 + 颜色），不单靠颜色区分
- Compose 层不再覆盖 FontWeight、不再使用魔法 dp，统一走主题排版与 Spacing token

### 验证

- 全量 86 条单元测试通过；release（正式签名）与 debug 包均构建成功

## [v1.0.6] — 2026-09-15

安全加固与健壮性（依据 v1.0.5 代码审查报告 P0/P1 项）。

### ⚠️ 升级须知（正式签名切换）

- **本版起 release 包改用正式签名**（独立 RSA-2048 密钥库，密码存 `local.properties` 不入库），此后所有版本均用该签名覆盖安装
- **手机上已装的 v1.0.5（旧 debug 签名）无法直接覆盖安装本版**：请先在旧版「我的 → 备份」完成一次全量加密备份（导出 `.ashkb` 文件并妥善保存），卸载旧版，安装 v1.0.6，再用备份文件恢复数据
- 密钥库 `ashkb-release.jks` 与密码请离线备份（密码管理器 / iCloud）——**丢失后应用将永远无法覆盖升级**

### 安全（P0）

- **WebDAV 凭据加密存储**：服务器地址 / 账号 / 密码改经 Android Keystore AES-256-GCM 加密后落盘（密钥保存在设备安全硬件中、不可导出），首次启动自动迁移旧明文配置并删除明文
- **pre-restore 快照口令去硬编码**：恢复前的退路快照改用与主备份相同的口令加密（原为 APK 内可反编译提取的固定常量）；新版生成的退路文件可直接用当时恢复所用的备份口令解开
- **release 正式签名**：生成独立密钥库并接入构建（见上方升级须知），`local.properties` 缺失时自动回退 debug 签名保证可构建

### 健壮性（P1）

- **多步写操作事务化**（`HealthRepository`）：症状保存 + 警报评估、BASDAI 覆盖 + 历史收敛 + 警报、发作开始 / 缓解、运动次日反馈、补剂打卡、体征 / 体重保存、疫苗保存 + 活疫苗警报、化验单 AI 批量导入等改为单事务原子完成，中途失败不再留下半写状态
- **备份导出读事务**（`BackupEngine.export`）：全库导出锁定同一快照，避免导出期间被并发打卡写入污染

### 代码质量（P1）

- 拆分超长 Composable（纯移动、零逻辑改动）：`CheckupScreen.kt` 946 行 → 主框架 / 列表 / 表单 / 化验影像 4 文件；`SymptomScreen.kt` 628 行 → 主框架 / 表单 / 卡片 / 弹窗 4 文件

### 验证

- 全量 86 条单元测试通过；release（正式签名）与 debug 包均构建成功

## [v1.0.5] — 2026-09-15

全局界面排版排查修复：消除各页面文字零间距、拥挤甚至堆叠的问题。

### 修复 · 表单弹窗间距

- **健康档案建档 / 编辑**：整列表单零间距 → 统一 8dp 行距（称呼、诊断、年份、B27、分期、脊柱活动度、过敏史、血型、合并症）
- **添加药品（两步）**：字段 / 标签 / 选项条整列零间距 → 统一 8dp；药品匹配建议列表 4dp 行距；「已选时刻」由逐行堆叠改为一行汇总展示

### 修复 · 页面卡片与列表间距

- **紧急卡**：⚠ 图标与标题、事件日期与场景之间的水平间距（原先 Row 内误用竖向 Spacer 导致零间距）；「应急处理卡」标题与说明包裹容器不再堆叠；「我的信息」五行信息 3dp 行距；联系人姓名与关系 2dp
- **健康 Tab 首页**：标题与副标题包裹容器；入口卡图标与文字水平间距 14dp（原为零间距）
- **运动页**：「今日处方」「黑榜」标题与说明包裹容器；次日反馈弹窗标签与选项、两处分组间距
- **复诊管理**：复诊记录卡、疫苗卡、影像卡、化验卡统一 4dp 行内间距（原先日期 / 名称 / 医院 / 结论等零间距堆叠）
- **报表页**：统计单元格数值与标签、依从率明细两行、趋势图与日期轴间距
- **我的**：药单药品名与频次说明、提醒自检项目行间距
- **症状页**：登记发作弹窗（诱因 / 处理选项分组间距）、发作历史与 BASDAI 历史行内两行间距
- **今日用药**：跳过原因弹窗、注射部位选择弹窗分组间距
- **知识库详情**：标签与内容 2dp 间距、来源卡两行间距
- **备份页**：加密说明标题与正文间距

### 验证

- 全量 86 条单元测试通过；编译零错误（无新增警告）

## [v1.0.4] — 2026-09-15

检查报告 AI 导入：化验单 / 影像报告拍照发给 AI，按模板整理后粘贴回 App 即可入库。

### 新增 · 化验数据 AI 导入（复诊管理 → 化验 Tab）

- **AI 导入流程**：点「AI 导入化验单」→ 复制内置模板 → 连同检验报告照片发给任意 AI 助手（豆包 / ChatGPT 等）→ 把 AI 回复原文粘贴回来 → 解析预览 → 保存入库
- **解析能力**：逐项提取项目名、数值 / 文本结果（阴性 / ↑20 等）、单位、参考范围（支持 `2.9-8.2`、`≤5`、`9–50` 等全半角写法）、偏高 / 偏低标记；日期支持 `2026/7/31`、`2026年7月31日` 等写法自动归一
- **化验 Tab 总览**：按检查日期分组展示全部化验指标（含复诊记录关联项与 AI 导入项），异常项红色加粗，卡片头部汇总「N 项 · M 项异常」，医院名随导入保存
- **自动判读**：导入时按参考范围自动判读 high / low / normal（AI 标记优先），异常值红色高亮

### 新增 · 影像报告 AI 导入（复诊管理 → 影像 Tab）

- **支持类型**：MRI / 磁共振、CT、X 线 / X 光 / DR / 平片，报告类型自动归一
- **字段提取**：类型、日期、医院、部位、检查所见（多行）、结论 / 印象、与前片对比；「对比」自动并入备注
- **影像 Tab**：卡片显示日期 · 类型 · 集中部位 · 医院，结论首行预览；点开查看完整所见与结论；补录的历史报告带「（补）」标记

### 数据层

- 新增 `imaging_records` 表（数据库 v6→v7 自动迁移，旧数据无损），`imaging_records` 随备份引擎动态枚举自动纳入备份 / 恢复
- AI 导入解析器（`ReportImportParser`）为纯函数、无 Android 依赖，模板说明行 / 表头复读自动跳过

### 验证

- 新增 11 条解析器单元测试（化验 / 影像 / 参考范围 / 模态归一），累计 86 条全部通过

## [v1.0.3] — 2026-09-15

昨日自评可反复修改。

### 优化 · BASDAI 自评可编辑、可多次提交

- **同日覆盖语义**：同一天多次提交 BASDAI 不再产生重复记录，后一次覆盖前一次（主键复用，与每日症状一致）
- **编辑回显**：选中「今天 / 昨天」后若当日已有自评，对话框自动回显原值，修改后点「更新」覆盖
- **状态提示**：自评卡显示「<所选日期> 已记录（总分 X.X）」；按钮按状态显示「开始 / 修改 / 补写 + 今日 / 昨日自评」
- **补写标记**：非当天提交的自评带 `backfill` 标记，历史列表日期后显示「（补）」
- **重复行清理**：修复历史遗留的同日多条记录（保存时自动收敛为一条），避免趋势图出现同日双点、虚增「14 天内 ≥2 次 ≥4.0」复诊提醒计数
- 影响范围：BASDAI 弹窗与历史列表；数据库结构无变化（`backfill` 列为既有字段，本次启用）

### 验证

- 75 条单元测试回归通过

## [v1.0.2] — 2026-09-15

症状自评支持补写昨天。

### 新增 · 症状自评按日补写

- 「症状与自评」页新增记录日期选择：今天 / 昨天（补写），影响每日症状与 BASDAI 自评的回显与保存
- 漏记场景：昨天忘了记，切到「昨天」补写即可；已记录的昨日内容也可再修改（同日保存为覆盖更新，主键复用）
- 补写数据自动进入报表统计（依从率窗口、症状均值、趋势图均按记录日期聚合）
- 发作登记（开始 / 缓解）仍固定按实际当天记录，不受日期选择影响
- 覆盖昨天的发热 ≥38.5℃ / 眼部 / 神经红旗症状时，警报规则同样生效（按记录日期去重）

### 其他

- 切换记录日期时症状表单草稿重置，避免今天的半成品误存到昨天
- 75 条单元测试回归通过

## [v1.0.1] — 2026-09-10

首个公开发布版本。新增「每周两次」用药频次与通用名键自动匹配，修复注射类药品按周提醒静默失效问题，加固备份跨版本恢复兼容；75 条单元测试全部通过。

### 新增 · 每周两次（BIW）用药频次

- 适用场景：依那西普（恩利）25mg 每周两针、周一 / 周四固定日方案等医嘱；此前只能用「自定义周期 3 天」近似，与医嘱对不上
- 录入方式：频次选「每周两次」，分别选定两个注射星期（默认周一 / 周四）；两针星期相同时表单拦截提示
- 提醒行为：每个选定星期自动出注射卡 + 提醒（默认 09:00，可自定义时刻）；打卡支持部位轮换与批次号
- 数据层：`medications` 新增 `weekly_weekday2` 列，数据库 v5→v6 自动迁移，旧数据无损
- 降级保护：仅配置第一星期时仍按单星期出针，不会静默失效

### 新增 · 通用名键自动匹配

- 内置 38 条药品目录（通用名键 / 中文通用名 / 商品名 / 别名），覆盖 TNF 抑制剂、IL-17 / IL-23 生物制剂、传统 DMARD、NSAID、JAK 抑制剂、糖皮质激素、骨健康辅助用药及类别键（`nsaid`、`biologic` 等）
- 「通用名键」字段输入任意中英文实时建议；该字段为空时按「药品名」反向推荐
- 一键回填：通用名键 + 空缺的药品名 / 商品名 / 药物类别，用药核对清单随之立即生效
- 未收录词不显示建议、不瞎猜，保留手填兜底

### 修复 · 注射药「每周一次」不生成卡片

- 原行为：「每周一次」按口服甲氨蝶呤场景设计，注射途径选中后今日卡片与提醒均静默缺失
- 现行为：按所选固定星期正常生成注射槽位与提醒
- 影响面：其他频次录入的注射药不受影响；既有的注射 + 每周配置升级后自动生效

### 加固 · 备份跨版本恢复兼容

- 问题：数据库新增列（v6）后按当前库全列校验，恢复旧版本（v5 及以前）的加密备份会被 SHA-256 误判为损坏
- 方案：恢复校验改按「备份文件自身的列集」重算摘要；备份容器 SCHEMA_VERSION 升至 6
- 效果：v1.0.0 及更早版本创建的加密备份、WebDAV 云备份均可正常恢复

### 其他

- 复诊报告 PDF 频次映射支持「每周两次」
- 档案 JSON 导出 / 导入覆盖 `weekly_weekday2`

### 质量验证

75 条 JVM 单元测试全部通过，其中新增 19 条（BIW 排程 7 条、通用名目录 12 条）。

| 测试套件 | 用例数 | 覆盖范围 |
|---|---:|---|
| ScheduleCalcTest | 26 | 计划槽位：每日 / 每周 / 每周两次 / 每两周 / 自定义周期 / 按需，注射周期与 late 容差边界 |
| ExerciseEngineTest | 23 | R27 运动分级矩阵三分期判定、黑榜拦截、反馈判读 |
| DrugKeyCatalogTest | 12 | 通用名目录检索（中英文 / 别名 / 大小写）、建议上限、精确键、回填信息（新增） |
| BackupVaultCipherTest | 9 | AES-256-GCM 加解密往返、错误口令、密文与明文头篡改、中文明文无损 |
| BackupEngineTest | 5 | 备份逐表 SHA-256 清单、行序稳定性 |

单元测试覆盖 domain 纯函数逻辑；真机三机型提醒可靠性实测按 P5 自用验证手册进行中。

### 安装与升级

1. 从 [Release v1.0.1](https://github.com/weante/ashkb/releases/tag/v1.0.1) 下载 `ashkb-1.0.1-release.apk`（11.0 MB）
2. 直接覆盖安装，本地数据（药单、打卡、体征、备份台账等）全部保留
3. 首次打开确认提醒权限；精确闹钟权限重新授权后提醒自动重排

> 升级建议：在「我的 → 备份与数据」先执行一次本地加密备份（或 WebDAV 上传），并可通过一键恢复演练验证备份可用性。

`ashkb-1.0.1-debug.apk`（16.2 MB）含完整日志，仅建议问题排查时使用。

## [v1.0.0] — 2026-08-31

1.0 定版：完成 P0~P5 全部功能开发。

- 今日：药单管理（口服 / 注射 / 按需多频次）、今日打卡（餐前餐后、注射部位轮换、批次号）、精确闹钟提醒、用药核对清单
- 健康：体征记录、体重围度、补剂档案、饮食画像与忌口、复诊管理、紧急卡
- 报表：30 天依从率、BASDAI 走势与活动度阈值、自绘趋势图、复诊报告 / 紧急卡 PDF 导出
- 运动：R27 分级矩阵（三分期判定、黑榜拦截、运动后反馈）
- 知识：内置种子医学知识库，本地检索
- 我的：AES-256-GCM 加密备份、WebDAV 云备份、档案 JSON 导出 / 导入、备份台账、一键恢复演练
- 质量：56 条单元测试；测试中发现并修复 2 个真实缺陷（late 判定失效、非法时刻致提醒全灭）；提醒对开机 / 时区变更 / 权限回授自动重排
