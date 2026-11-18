# v1.2.18 · Compose 性能收尾：三件事的实测与取舍

> 起因：`HANDOFF-STATUS.md` §3.2「Compose 性能结构性重构 7 项」里剩 2 项 + 一处「20 处 verticalScroll 未整理」。
> 本文记录 2026-11-18 这一轮的处理结果：**做了什么、为什么这样做、以及哪一项被判定「不应做」**。
> 全部改动只碰 UI 层的收集位置与文档，**未改库结构、未改版本号以外的东西**。

---

## 一、深层 Flow 下沉（已完成）

**问题**：页根 `collectAsStateWithLifecycle` 收集的每一路 `StateFlow` 只要有一个新值，整个页面重组
（LazyColumn 的 `content` lambda 重新执行、每个 item 重新求值 key 与内容）。实测三个页面的根部收集数：

| 页面 | 改前 | 改后 | 留在根部的（及原因） |
|---|---|---|---|
| `ui/checkup/CheckupScreen.kt` | 8 | **1** | `items`（记录 tab 的 `filterItem` 解析、记录表单的项目预填都要它） |
| `ui/symptom/SymptomScreen.kt` | 6 | **2** | `selectedDate`（页面级参数）、`alerts`（决定警报区那个 `item` 是否存在） |
| `ui/exercise/ExerciseScreen.kt` | 5 | **1** | `ui`（`ExerciseUiState`，黑榜与处方列表都由它驱动） |

**做法**：把收集点搬到「最靠近使用点」的区块 composable，每个区块自己收集、自己早退。

- Checkup：`CheckupItemsTab` / `CheckupRecordsTab` / `CheckupLabsTab` / `CheckupImagingTab` / `CheckupVaccinesTab`，
  以及三个用到记录数据的宿主 `LabAttachSheetHost` / `LabsLinkPickerHost` / `ImagingLinkPickerHost`。
  「记录」tab 的回调有 6 个，收进 `private class RecordsTabActions`——不是为了好看，是为了不踩
  `detekt.yml` 的 `LongParameterList.functionThreshold = 8`。
- Symptom：`FlareSection` / `DailySymptomSection` / `BasdaiSection` / `FlareHistorySection` / `BasdaiDialogHost`。
- Exercise：`PrescriptionHeroHost`（收 `yesterdaySymptom` + `warmup`）/ `PendingFeedbackSection` / `TodayLogsSection`。

**收益的具体形态**（不是为了跑分）：打卡一次只让「今日已打卡」那一块重组，不再连带动黑榜、处方 hero、
次日反馈三块；化验「加载更多」只影响化验 tab，不再让项目/记录/疫苗三个 tab 一起走一遍。

**已知取舍**：两处条件区块（次日反馈、今日已打卡）此前是 `if (list.isNotEmpty()) { item { … } }`，
现在是 `item { SectionXxx() }`、区块内部 `if (isEmpty) return`。空列表时 LazyColumn 里会留一个 0 高
item，多出 `verticalArrangement` 的 12dp 留白——与相邻卡片之间的块间距同宽，观感上表现为「这里少了一段」。
保留 `if` 也可以，但那就必须把 list 收集回根部，等于放弃下沉；两者只能选一个，这里选了后者并留注释。

**验证**：`compileDebugKotlin` 逐屏通过；完整门禁结果见本文末。

---

## 二、`verticalScroll` 清点（已完成 · 结论是「全部正确，不应改」）

backlog 里写的是「14 文件 20 处」。**实际全仓 26 处 / 18 文件**（数字偏小，说明当时是估算）。

| 类别 | 处数 | 文件:行 | 为什么不改成 LazyColumn |
|---|---|---|---|
| 对话框正文（`AlertDialog.text`） | 7 | `DisclaimerScreen.kt:47`（首启免责声明页，整页法律长文）、`SymptomDialogs.kt:68`、`SymptomDialogs.kt:284`、`KbDetailDialog.kt:71`、`CheckupLabImaging.kt:324`（化验明细十几行）、`CheckupLabImaging.kt:691`、`EmergencyScreen.kt:777`（联系人编辑）、`ExerciseScreen.kt:449`（打卡对话框） | 对话框高度由 `AlertDialog` 决定，内容是**有界**的表单/明细；塞 LazyColumn 进去要自己算最大高度，且与对话框的 `ime` 处理打架 |
| sheet 正文 | 10 | `KbEditSheet.kt:71`、`BackupSheets.kt:61`、`BackupSheets.kt:117`、`AttachmentSheet.kt:338`、`CheckupLabImaging.kt:992`（`SheetColumn` 骨架）、`CheckupLabImaging.kt:744`、`CheckupLabImaging.kt:936`、`EmergencyEventSheet.kt:84`、`ExerciseScreen.kt:499`、`ReminderSettingsSheet.kt:86`、`RecipesScreen.kt:482`、`WellnessScreen.kt:1477`（`SheetColumn` 骨架） | `ModalBottomSheet` 的滑动关闭手势与 LazyColumn 的滚动会互相抢；且这些 sheet 里是「几个字段 + 一两个小列表」，不是长列表 |
| 整页单列 | 5 | `BackupScreen.kt:211`（多张 SectionCard 纵排）、`MedEditScreen.kt:379`、`ProfileEditScreen.kt:142`、`ReminderCheckScreen.kt:75` | 页面本身就是一列卡片，条目数固定（5–8 个 SectionCard），LazyColumn 的懒加载没有对象可省，反而要处理 key 与嵌套滚动 |
| 限高内滚块 | 3 | `DailyValuesList.kt:79`（`heightIn(max = 220dp)`，v1.2.15 V-3 刻意如此）、`CheckupLabImaging.kt:837`（AI 导入模板预览 `heightIn(max = 160.dp)`）、`CheckupLabImaging.kt:744` | 设计意图就是「块内自己滚、不撑长外层」 |
| 无数据 List 的兜底 | 1 | `CheckupLabImaging.kt:936`（导入预览） | 同上，预览区限高 |

**结论**：26 处没有一处是「该用 LazyColumn 却用了 verticalScroll」。真正的长列表（知识库 48 条、
化验 50 条、复诊记录、运动库、报告明细）早已是 LazyColumn。因此本项**不改代码**，只把清单钉死——
新增 `app/src/test/java/com/ashkb/app/ui/VerticalScrollInventoryTest.kt`：
该文件记录「文件 → 处数」的清单并断言与源码一致。以后谁再往页面级容器上加 `verticalScroll`，
或把某处悄悄换成 LazyColumn，测试会红，必须在清单里留下理由。这是本仓既有做法
（`KbTierLabelGuardTest`、`BackupBatch2GuardTest` 都是这个套路）。

---

## 三、把多路 `MutableStateFlow` 合并成一个 `UiState`（**判定：不应做**）

backlog 写的是「`BackupViewModel` 20 个 `MutableStateFlow`、`ReportViewModel` 7 个合并成单一 `UiState`」。
实测与实际结构对不上，而且**合并会让重组范围变大**：

1. **数量对不上**：`ui/backup/BackupViewModel.kt` 现有 **30 个** `StateFlow`（其中 24 个是
   `_x` + 只读对外的成对写法），`ui/report/ReportViewModel.kt` 现有 **8 个**。
2. **消费点已经是最优形态**：`ui/report/ReportScreen.kt:145` 是 `HorizontalPager`，四个 tab
   各自在自己的 composable 里收集——
   `OverviewTab:166` 收 `overview`、`TrendsTab:197` 收 `trends`/`trendDays`/`loadFailed`、
   `PeriodicTab:206` 收 `periodic`/`periodDays`、`ExportTab:219` 收 `busy`，根部只有 `busy`/`message`
   （Snackbar 需要）。**合并成单一 `UiState` 后，任何一次加载完成都会让根部重组、四个 tab 全部重走**，
   比现在按需重组更差。
3. **备份路径上的代价更明显**：`ui/backup/*.kt` 共 34 处消费，`stage`/`attachSyncStage` 这类字段在
   备份/附件同步时**每秒都在变**；合并后每次变都会让备份页 + 5 个 sheet 一起重组，而这条路径刚在
   v1.2.15 / v1.2.17 做过真机验收（恢复互斥、演练闸），此刻做纯结构改写属于**只增风险、无收益**。

**已做的替代动作**：`BackupViewModel` 的这一批字段本来就只有备份页与它的 sheet 消费，真正的收益点是
「哪些 UI 需要在哪个粒度重组」——那是上面第一项已经解决的问题（下沉）。因此本项建议从 §3.2 里
**划掉**，理由记在此处；如果以后要动，必须与备份恢复的真机回归一起排期，不能单独做。

---

## 门禁与验证

见 `CHANGELOG.md` 的 `[v1.2.18]` 小节（单测条数 / lint / detekt / verifySpecSync 的实际输出）。
