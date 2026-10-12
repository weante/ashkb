# ASHKB 开发交接文档 · 历史归档

> 本文是 `HANDOFF.md` 2026-10-11 拆分时移出的**历史内容**，只读保留，不参与维护。
> 现行工程约定在 `HANDOFF.md`，当前状态与待办在 `HANDOFF-STATUS.md`，
> 逐版本变更明细在 `CHANGELOG.md`。

---

## 一、发版当时的更新说明块（v1.2.4 → v1.2.8）

> 原为 `HANDOFF.md` 开头的五个引用块，每发一版往上叠一块，把有效内容压到文件 20% 之后。
> **v1.2.8 更新（2026-10-09，先看这条）——UI 2.0 批次 A：整份换色（冷调瓷白 + 单一临床蓝）**：
> · **只**动 `ui/theme/Color.kt`（48 个角色 × 浅/深两套全部换成「澄序 CLARITY · ASHKB UI 2.0」设计稿的值）+ `object Glass` 六值（暖调半透明 → 冷调 92%/94% + 冷发线）+ 一处**必修的对比度回归**（`ReminderFullScreenActivity` 的「已服」按钮写死白字压在深色 `error` 上只有 2.02:1 → 改 `onError`）。**没有**动屏幕结构 / 组件 API / domain / data / ViewModel。
> · 设计稿分四批（①换色 ②换形 ③换件 ④换图）；本版 = ①，是给维护者**装机对比用的最小包**。✅ **已发布**：GitHub Release `v1.2.8`（release id `408053469`，**Latest**），资产 `ashkb-1.2.8-release.apk`（5220504 B，sha256 `ece175f8…`）——②～④ 待看完效果再定。
> · 校验脚本 `E:\ASHKB\verify-palette-contrast.ps1`（只读、可复跑）：48 × 2 个角色与设计稿 §1 的表**逐条一致**（0 缺失 / 0 不符 / 0 多余）；18 组文本对**全部 ≥ AA**，最低 **4.85:1**（浅色 `onSurfaceVariant` / `surfaceVariant`）。
> · 单测 **874 条 / 100 个文件全绿**；detekt 干净、lint 0 error。真机：`adb install -r` 覆盖安装（`versionCode 120`）**数据未丢**，今日 / 健康管理两页**浅色 + 深色截图逐个核对**（`E:\ASHKB\ui2-shots\`）。
>   ⚠️ **强提醒全屏页的「已服」按钮没有真机截图**（该页只在锁屏升级链走完时出现），依据是 WCAG 复算 2.02:1 → 7.99:1。
>
> **v1.2.7 更新（2026-10-09）——批次 14：清扫 OPEN-RISK 六项（R3 / R4 / R5 / R6 / R7 / R8）**：
> （来源：`NEXT-SESSION.md` 的 R1–R8 高危表；R1 已在 v1.2.2 修复，R2「历史幽灵行」经维护者裁决**不做**。）
> · **R3 周报「下次复诊日」恒为 null**：`ReportRepository.periodicReport` 用 `checkupRecordDao.between(f, t)` 取下次复诊，
>   而 `between` 过滤的是 **`date` 列（就诊日）**——「就诊日已过、`next_date` 在将来」的记录全被排除，那栏于是永远空着。
>   新增 `CheckupRecordDao.upcomingByNextDate(from, to)`（`next_date IS NOT NULL AND next_date != '' AND next_date BETWEEN …`，按 `next_date` 升序），
>   `periodicReport` 改调它；消费方 `ReportScreen` 本来就只在非空时显示，无需改。
> · **R4 WebDAV 轮换可能删到目录外的文件**：`WebDavClient.rotate()` 把 PROPFIND 回显的文件名**直接拼进**
>   `open("ashkb/backup/$name", "DELETE")`，原先只校验 `startsWith("ashkb-backup-") && endsWith(".ashkb")` →
>   新增 `WebDavClient.isSafeBackupName()`（`Regex("ashkb-backup-[0-9A-Za-z._-]+\.ashkb")` **全匹配**），
>   `parseBackupFileNames` 与删除循环**都**过这道闸——`%2f` 这类百分号编码的分隔符也拦得住（远端目录列表是外部输入）。
> · **R5 取消恢复仍会留下备份携带的 vault key**：`adoptIfAbsent` 从 `decryptAndSelfCheck`（verify 阶段，用户还没点确认）**移到 `restore()` 内部**。
>   ⚠️ 两条顺序硬约束：**必须在用户确认之后**（verify 只是「看一眼」）、**必须在 pre-restore 快照之前**
>   （快照用 `vaultKeyOrThrow()` 加密，换机恢复时本机此刻还没有密钥）。`vaultKeyOrThrow() = vaultKeys.getOrCreate()`，**缺密钥会自建**，
>   故不存在「没有密钥导致快照失败」。另：`BackupViewModel.errText` 不再把原始异常文本直接糊到提示条，改为
>   `vm_err_unknown`（`vm_err_unknown_detail` 附原文供排查），zh / en 两侧同序新增。
> · **R6 全局提示在无订阅者期间被静默丢弃**：`GlobalMessages` 由 `MutableSharedFlow(replay = 0)` 改为
>   `Channel<String>(capacity = 8, onBufferOverflow = DROP_OLDEST)` + `receiveAsFlow()`（`post` = `trySend`）。唯一订阅者 `AppShell` 的 `.collect` 不用改。
> · **R7 级联删除附件的顺序反了（本批唯一不可恢复的失败模式）**：旧顺序是「先逐条 `attachmentRepo.delete()`（**先删字节**，再删/标行，各自独立提交），
>   再开事务删三张表」——两步之间崩溃会留下**文件没了、行还在的悬空附件行**：用户点开就失败，且**没有任何入口能清掉**。
>   现在：事务内 `deleteRowsForCascade`（只动行，按 `remotePath != null && isSyncEnabled()` 决定墓碑还是物理删）+ 化验 / 影像 / 记录**同一个事务**，
>   **提交之后**才 `deleteLocalFiles` 删字节。崩溃只可能停在两种可接受态：① 事务之前（什么都没变）② 提交后、删字节前（**孤儿字节**，占空间但不影响任何界面）。
>   单条用户删除路径（`AttachmentRepository.delete`）**刻意保持原样**。
> · **R8 知识卡详情弹窗自己读系统时钟**：`KbDetailDialog` 加 `today: String` 形参，**四个调用方全部传入注入的日期源**——
>   `KnowledgeScreen`（`vm.date`）、`EmergencyScreen`（`vm.date`）、`SymptomScreen`（`vm.today`）、`ExerciseViewModel.today`（本版新增，此前没有公开日期）。
>   跨零点前后那个「已过期」小胶囊不会再与页面顶部日期差一天。`config/detekt/baseline.xml` 里那条 `LongMethod:KbDetailDialog.kt$…`
>   的**签名字串同步更新**（既存豁免，**未新增豁免条目**）。
> · 单测 **874 条 / 100 个文件全绿**（+9：`NextCheckupDateQueryTest` 4 条 / `GlobalMessagesTest` 1 条 /
>   `RecordDeletionCascadeTest` +2 条「墓碑」与「字节删不掉」两个中间态 / `BackupEngineTest` +2 条文件名白名单）；detekt 干净、lint 0 error。
> · ⚠️ **本批真机验证为 0 项**：构建机 `adb devices -l` 与 `adb mdns services` **均为空**（无线调试未开或设备不在线），
>   只做了静态审查 + 单测验证，**没有装机**——按 §7 教训「凡改 UI 必须逐个界面读屏」，本批改到的界面（知识卡详情弹窗）**尚未读屏**。
> **v1.2.6 更新（2026-10-09）——英文支持第三层：domain 文案层 + 散落 UI 文案 + 种子内容**：
> · **domain 文案层 105 条**（`values/strings_domain.xml` / `values-en/strings_domain.xml`，`dom_` 前缀）：
>   `Labels` / `MissedDose` / `EmergencyLockscreen` / `LifestylePrescription` / `ClinicalThresholds` /
>   `MorningWarmup` / `CheckupPrep` / `Disclaimer` / `PostureAdvice` / `LabIndicator` 十个文件。
>   ⚠️ **API 形态变了**：这些函数现在返回 `@StringRes Int` 而不是 `String`，调用方负责落地；
>   带插值/成组传递的用 `domain/Labels.kt` 里的 `data class ResText(@StringRes val res: Int, val args: List<Any> = emptyList())`。
>   ⚠️ `MissedDose.Guidance` 的字段是 **`headlineRes`**（不是 `headline`）；`MorningWarmup.Sequence` 是 `headline: ResText` + `noteRes`。
>   ⚠️ `MissedDose` 资源里**保留了字面 `**`**（加粗标记），UI 侧必须继续 `.replace("**", "")`。
>   ⚠️ **`Disclaimer` 的 `const val` 全部改成了 `val`**——AGP 8 起 `R` 字段不是编译期常量，`const val` 编译不过。
> · **散落 UI 文案 51 条**（`values/strings_ui.xml` / `values-en/strings_ui.xml`，`ui_` 前缀）。
>   ⚠️ **匹配键一律不翻译**：`ReportImport.kt` 的中文解析键（日期/医院/项目/结果/参考范围…）、
>   `SupplementTiming.kt` 的药名关键词、`DateInput.kt:23` 的正则、`LabIndicator.aliases`（「血沉」「c反应蛋白」）——
>   它们比对的是**用户导入的原文**，翻译了就再也匹配不上。
> · **种子内容 72 条**（`values/strings_seed.xml` / `values-en/strings_seed.xml`）：食谱 30 + 标签 3 + 出处说明 9 +
>   免责声明 1 + 筛查项 8 + 周期模板 21。这四条会**写进数据库**，所以另开一对文件与界面文案分开演进。
>   ⚠️ `RecipeSources.citation`（文献题录）**不翻译**——翻译后无法按题名检索。
> · **语言感知重种**：新增 `data/repo/SeedLocales.kt`（`ALL = [SIMPLIFIED_CHINESE, ENGLISH]`，用
>   `createConfigurationContext` 取词，**不改进程 Locale**）。三个仓储在种入后按语言刷新**用户没编辑过的**种子行。
>   ⚠️ **判据是内容比对，不是时间戳**——`updated_at` 会被「收藏」这类非内容操作顶掉。
>   ⚠️ `checkup_items` **没有 `isSeed` 列**，只能按「名称 + 备注与某一已知语言逐字相同」认定未编辑。
>   ⚠️ `ScreeningSeeds.pending` 必须**比对所有已知语言**，否则中文用户切英文会被重复种入一份。
> · **新守卫**：`i18n/SeedStringsParityTest`（条数/顺序/英文侧零 CJK）、`LabelsTest`「五个分期 key 指向五条不同资源」、
>   `AdherenceTest`「三档判定指向三条不同资源」、`DisclaimerTest`「四条要点必须互不相同」、
>   `RecipeSeedsTest`「30 个资源 id 互不相同」——**改资源 id 时把多条指向同一资源编译器不报错，界面只会静默退化**。
> · 单测 **874 条 / 100 个文件全绿**；detekt 干净、lint 0 error。
> · **已发布为 GitHub 正式版（Latest）**：`git push origin refs/tags/v1.2.5 refs/tags/v1.2.6` →
>   `gh api -X POST repos/weante/ashkb/releases --input rel-v126.json`（`make_latest=true`）→
>   `curl.exe --http1.1 -x http://127.0.0.1:7897` 上传 `ashkb-1.2.6-release.apk`。
>   资产 SHA-256 `b3455fe8…` 与本地 `Get-FileHash` **逐字节一致**（release id `407329934`）。
>   ⚠️ **本版真机验证时手机语言切回中文**（维护者裁决），英文验证在切回前完成。
> **v1.2.5 更新（2026-10-09）——英文支持第二层：知识库正文 + 枚举 + 备份层**：
> · **知识库正文真正翻译了**：新增 `app/src/main/assets/en/kb_seed_{itx,exc,fdg,emr,edu}.json`
>   （15 / 15 / 6 / 5 / 7 条，与中文 5 份一一对应）。`KB_SEED_VERSION` 12 → **13**。
> · **载入闸门纳入语言**：`AshkbApplication.importKbSeedIfNeeded` 原来只看版本号，切成英文后
>   种子内容变了却不重灌 → 知识库仍是中文。现在多存一个 `kb_seed_lang`，
>   `internal fun kbSeedNeedsImport(recordedVersion, recordedLanguage, currentLanguage)` 版本或语言任一变化就放行。
>   ⚠️ `SEED_FILES_EN = SEED_FILES.map { "en/$it" }` 是**派生**的，不要手抄第二份清单——两份会漂移。
>   ⚠️ 语言判定读 `context.resources.configuration.locales[0].language`，**不要用 `Locale.getDefault()`**
>   （那是进程级默认值；应用级语言改了它未必同步 → 会出现「界面英文、知识库中文」）。
>   ⚠️ **个人备注不会被换语言冲掉**：`KbSeedRefresh.plan` 里是 `seed.copy(userNote = old.userNote)`。
> · **新增守卫 `app/src/test/.../KbSeedEnglishParityTest.kt`（7 条）**：中英种子「条数 / id 顺序 /
>   固定字段逐字节 / payload 结构同形 / **受保护字段逐字节** / 除白名单外无中文 / 闸门真值表」。
>   **受保护字段 = 有代码逐字消费它的字段**，改一个字符就静默改变行为：
>   itx 的 `drug_a`/`drug_b`/`risk_level`/`positive_interaction`（`MedicationRepository` → `DrugInteractionKeys.matches`）；
>   exc 的 `list_type`/`grade`/`cervical_condition`/`grade_matrix`/`block_rule`（`domain/ExerciseEngine.kt`）；
>   fdg 的 `food`/`drug_or_class`；edu 的 `key`/`value`。**译正文可以，译这些不行。**
>   ⚠️ `kb_seed_exc.json` 的 `payload` 有**两种形态**（`exc-*` 有 dose/benefit/action，`exb-*` 有 block_rule/risk），
>   校验必须比「字段名集合」而不是硬编码某个 schema。
>   ⚠️ 扫描 CJK 时范围别写太宽：`[\u2E80-\uFFEF]` 会命中 `⚠️` 的第二个码位 **U+FE0F（变体选择符）**，报假红。
> · **`KbSeedVersionGateTest.fingerprint()` 现在遍历中英两份清单**（`SEED_FILES + SEED_FILES_EN`）——
>   否则英文种子改了不 bump 也能溜过。已登记 `13 → b4a0dfaa…`（**指纹不要自己算，从断言消息里读**）。
> · **枚举 i18n**：`data/entity/Entities.kt` 的 19 个枚举 `label`/`title`/`plain`/`note` 全部改成
>   `@StringRes val xxxRes: Int`，取值改由 `stringResource` / `context.getString` 完成。
>   `domain/EmergencyMeds.summarize` 因此多了 `freqLabel: (MedFrequency) -> String` 参数
>   （**领域层保持零 Android 依赖**）。`values/` 与 `values-en/` 各 +113 条。
>   ⚠️ `summarize` 的末位参数是 `maxLines: Int`，调用点必须写具名 `freqLabel = { ... }`，否则尾随 lambda 写错位置。
> · **备份层 i18n**：新增 `values/strings_backup.xml` + `values-en/strings_backup.xml`（各 87 条，
>   全部 `backup_` 前缀）。`VaultException` / `BackupException` 增加 `@StringRes resId` + `resArgs`，
>   中文 `message` 原样保留给单测与日志。`WebDavClient` 构造函数多了第 4 个参数 `context`（方法签名未动）。
> · **对等守卫 `EnglishStringsParityTest` 已改成「多资源分片」版**：它扫 `values/` 下**所有**含 `<string>` 的
>   XML，逐个与 `values-en/` 同名文件配对。**新增分片文件（`strings_backup.xml` 这种）会自动被守卫覆盖，
>   不需要改测试**——这也是为了让多个会话各写各的分片、并行不冲突。
> · ⚠️ **`values-en` 一存在，Robolectric 默认 locale（en-rUS）就会取到英文**：凡断言中文的测试必须加
>   `@Config(qualifiers = "zh-rCN")`（本轮给 `EmergencyMedsTest` / `EmergencyLockscreenTest` 加了）。
> · **种子解析抽成了纯函数 `AshkbApplication.parseSeedJson(json)`**（原来只在 `parseSeed` 内部，
>   测试够不到）。理由：**解析失败是完全静默的**——`importKbSeedIfNeeded` 见到 `seeds.isEmpty()`
>   直接 `return`，不写闸门、不报错、不留日志，用户只是永远看到中文知识库。现在
>   `KbSeedEnglishParityTest` 把真实文件内容喂给**生产解析器**，断言中英各 48 条全部读出、
>   id 序列一致、`searchText` 非空。⚠️ 该函数的契约是「`getString` 读不到字段即整份退化为空表」，
>   **新增字段必须用 `opt*`**，否则旧种子会整份失效。> · ⚠️ **`**` 在知识库界面是字面显示的**（渲染链从不剥它；全仓唯一的 `s.replace("**","")` 在
>   `ui/today/TodayScreen.kt` 的 MissedDose 步骤，与 KB 无关）。中英种子都有，**属既有瑕疵，暂未修**。
> · ⚠️ **仍未翻译（有理由）**：`domain/DrugInteractionKeys.kt` / `domain/DrugKeyCatalog.kt` 里的中文是
>   **匹配键**，翻译即失效；`domain/RecipeSeeds.kt` / `domain/ExercisePlanTemplates.kt` /
>   `domain/RecipeSources.kt` / `domain/ScreeningSeeds.kt` 是**种子内容**，要等语言感知加载器覆盖到它们；
>   其余约 340 行散落文案是**下一批**。
> **v1.2.4 更新（2026-10-08）——英文支持第一层：界面字符串（`values-en` 1289 条）**：
> · **新增 `app/src/main/res/values-en/strings.xml`**（1289 条，与 `values/strings.xml` 一一对应）。
>   缺条目会让 lint 的 `MissingTranslation` 直接失败——**这是有意的**，不是要你加 `lintOptions.disable`。
> · **新增 `app/src/main/res/xml/locale_config.xml`**（`zh-Hans` 在前、`en` 在后）+ `AndroidManifest.xml`
>   第 26 行 `android:localeConfig="@xml/locale_config"`。**简体中文是默认**：系统语言不在清单内时，
>   资源解析落到无语言限定的 `values/`，即中文。
> · **新增 `ui/components/DateFormat.kt` 的 `@Composable rememberDateFormatter()`**：原来 `TodayScreen.kt`
>   两处写死 `Locale.CHINESE`，英文环境下 `EEEE` 仍渲染「星期三」。改用 `LocalConfiguration.current.locales[0]`
>   （**不要用 `Locale.getDefault()`**：应用级语言改的是 Configuration/Resources，进程级默认 Locale 未必同步）。
> · **新增守卫 `app/src/test/.../i18n/EnglishStringsParityTest.kt`（6 条）**：钉住「条目名 + 顺序一致」
>   「占位符一致（允许多重集合换序）」「引号包裹一致（否则丢首尾空格）」「英文里无 CJK」。
>   唯一白名单 `QUOTE_WRAP_EXCEPTIONS = {common_notice_prefix, knowledge_high_risk_prefix}`——中文用全角
>   `【】` 自带间隔，英文半角 `]` 必须靠外层引号保住一个尾空格，否则拼成 `· [High risk]甲氨蝶呤...`。
> · **英文文件的两个字节级禁忌**：① 所有内部引号必须写 `\"`（源文件即如此）；② 需要保住首尾空格时必须
>   用**外层引号**包裹整条正文（如 `" · %1$d items to do"`），否则 Android 会裁掉空白。
> · ⚠️ **`values-night/` 只有 `themes.xml`、0 条 string**，所以**不需要 `values-en-night`**：按资源逐项回退，
>   颜色落 `values-night`，字符串落 `values-en`。
> · ⚠️ **`verifySpecSync` 不读 `README.en.md`**（只读 README.md / HANDOFF.md / HANDOFF-STATUS.md）。
> · **真机切换 App 语言（无需改系统语言）**：`adb shell cmd locale set-app-locales com.ashkb.app --locales en`
>   （Android 13+；`--locales` 后接语言标签，清空用 `--locales ""`），用
>   `adb shell cmd locale get-app-locales com.ashkb.app` 核对。

---

## 二、版本流水（v1.2.8 → v1.0.72）

> 原为 `HANDOFF.md` §8 开头，逐版「最新版 / 上一版」叙述；内容与 `CHANGELOG.md` 重复。
- **最新版**：**v1.2.8（versionCode 120）· UI 2.0 批次 A「整份换色」**——`ui/theme/Color.kt` 整表重写（底 `#FFF8F4` 暖米 → `#F3F6F8` 冷瓷白；品牌蓝 `#416476` 钢青 → `#0B6E9E`；深色底 `#18120E` → `#0C1218`；成功/警告/危险三组语义色换设计稿值）+ `object Glass` 六值（52% 暖白 → 92% 冷白、`borderLight` 从白高光改成冷发线 `#E2E8ED`）+ `ReminderFullScreenActivity` 一处硬编码白字改 `onError`（深色下 2.02:1 → 7.99:1）✅ **已发布为 GitHub 正式版（Latest）**：附注标签 `v1.2.8` → `e4320d8`（远端 `refs/tags/v1.2.8^{}` 已核对）→ `gh api -X POST releases --input rel-v128.json`（`make_latest=true`）→ 资产 `ashkb-1.2.8-release.apk`（5220504 B，sha256 `ece175f8…`）与本地 `Get-FileHash` **逐字节一致**（release id `408053469`，资产 id `625371828`）；真机已装机双主题核对（今日 / 健康管理）
- **上一版**：**v1.2.7（versionCode 119）· 批次 14「清扫 OPEN-RISK 六项」（R3–R8）**——周报「下次复诊日」改按 `next_date` 查（新增 `upcomingByNextDate`）/ WebDAV 轮换加文件名白名单（`isSafeBackupName`，`%2f` 也拦）/ 备份密钥采纳从 verify 移到 `restore()` 内且兜底错误文案资源化 / `GlobalMessages` 改 `Channel`（无订阅者不丢）/ 级联删除改「事务内删行 → 提交后删字节」（消灭「文件没了行还在」的悬空态）/ 知识卡弹窗改用注入日期（四个调用方）⚠️ **真机验证 0 项**（构建机无设备），静态 + 874 条单测 ✅ **已发布为 GitHub 正式版（Latest）**：`git push` 远端 main = `d8f371b` → 附注标签 `v1.2.7` → `gh api -X POST releases --input rel-v127.json`（`make_latest=true`）→ 资产 `ashkb-1.2.7-release.apk`（5220504 B，sha256 `e09e6990…`）与本地 `Get-FileHash` **逐字节一致**（release id `407916739`）
- **上一版**：**v1.2.6（versionCode 118）· 英文支持第三层**——domain 文案层 105 条（10 个文件改 `@StringRes`）+ 散落 UI 文案 51 条 + **种子内容 72 条**（食谱 / 标签 / 出处 / 筛查项 / 周期模板）+ **语言感知重种**（`SeedLocales`，按内容比对认定「用户没编辑过」）+ 5 类新守卫测试 ✅ **已发布为 GitHub 正式版（Latest）**：release id `407329934`，资产 `ashkb-1.2.6-release.apk`（5219392 B，sha256 `b3455fe8…`）与本地 `Get-FileHash` 逐字节一致
- **上一版**：**v1.2.5（versionCode 117）· 英文支持第二层**——`values-en` 再加 113 条枚举 + 87 条备份文案，知识库 48 条正文全部译成英文种子，载入闸门纳入语言（版本或语言变化即重灌，个人备注保留）✅ **已发布为 GitHub 正式版**（后由 v1.2.6 取代）：release id `407329822`，资产 `ashkb-1.2.5-release.apk`（5084704 B，sha256 `44e8c464…`，由 `93f41ed` 的 worktree 重建，与当时产物同尺寸）与本地 `Get-FileHash` 逐字节一致
- **上一版**：**v1.2.4（versionCode 116）· 英文支持（i18n）+ 提醒频次修正 + 补剂每日提醒**——维护者 m00655 一次提了 6 条，全部落地 ✅ **① BASDAI 自评间隔**：原 5 档（7/14/28/56/84 天）→ **每日 / 每周 / 每月** 三档 ✅ 读取时**归一历史存值并写回**，不留隐形旧值 ✅。**② 删「每 8 小时」**：该频次**没有任何药物规则引用**（全仓只有枚举定义 + PDF 文案）✅ 但**枚举必须保留**——删掉会让 `fromKey` 退回 DAILY，把历史「每 8 小时」静默改成「每日」⚠️ → 加 `hidden = true`，**仅当当前值就是它时才显示** ✅ 同时删掉频次标签里的括号提示（`label` 改纯名称；`plain` 字段保留给急救卡）✅。**③ 自定义周期可自定义日期**：`cycleAnchorVisible` 原**只放行注射途径** ⚠️ 于是**口服 + 自定义周期静默等同于每日** ✅ 改为口服也能填周期天数与锚点日期 ✅。**④ 新增「每月 1 次」**：`ScheduleCalc.isMonthlyDay`（按锚点日号数，月末夹到当月最后一天）✅。**⑤ 补剂每日提醒**：新增 `SupplementReminderScheduler` / `SupplementReminderReceiver` / `CHANNEL_SUPPLEMENT`（**刻意与用药通道分开**——合成一个会让用户为了关补剂而不得不连用药一起关）✅ `SupplementSheet` 增时刻输入 ✅。**⑥ 英文支持**：`values-en/strings.xml`（1289 条）+ `locale_config.xml`（**简中为默认**）+ `rememberDateFormatter()` + `README.en.md` + `EnglishStringsParityTest` 对等守卫 ✅。
- **上一版**：**v1.2.3（versionCode 115）· 自评列表行高压缩 + 生活方式拆行 + 页面级外壳统一**——本轮全部来自维护者真机截图反馈 ✅ 共 4 项：**① 自评记录间隔偏大**：`BasdaiList` 里「删除」原本在左侧 Column 内**独占一行** ✅ 每行约 430px ✅ 一屏只看 6 条 ⚠️ → 移到**右侧、与总分同列** ✅ 行高降到约 360px ✅ 一屏 8 条 ✅。**② 删除与总分对齐**：改前「删除」在右上、总分在右下 ✅ 读起来像两列 ⚠️ → **同一右对齐 Column** ✅ 且**总分在上、删除在下**（先读到分数再决定是否删 ✅）。**③ 生活方式折行末行贴左**：`KeyValueRow` 的值列虽设了 `Arrangement.End` ✅ 但**管不到 `Text` 内部换行** ✅ 折行后末行仍左对齐（截图里「偶尔」的「尔」孤零零挂左边 ⚠️）→ 给值文本加 **`textAlign = TextAlign.End`** ✅。**④ 生活方式拆行**：维护者选「拆成两行、各自右对齐」✅ —— 原先 `lifestyleLabel` 把各项拼成**一个 `·` 连接的长字符串** ✅ 于是它是一行**被折行的长值** ✅ 拆成 `lifestyleItems` 返回**逐项列表** ✅ 每项一行 ✅ 间隔因此与其它行一致 ✅。⭐ **另加一件工具**：`PageChrome.kt`（**页面级外壳**）—— **标题卡片** `ScreenTitleCard` + **胶囊页签** `TabChips`，依据是维护者自己的设计稿 `ASHKB-毛玻璃UI预览.html` 的 `.tchip`（未选中白底细描边、选中**深青渐变底 + 白字** ✅）。知识库标题、报表标题从此**装进与数据卡片同一套圆角容器** ✅ 报表页签与知识库分类标签**统一为高对比胶囊** ✅ —— 渐变末端**不新增色板项** ✅ 由主题 primary 向黑 lerp 22% 得到（设计稿 `--primary-deep` = `#2c4a5a` ✅ lerp 结果 ≈ `#324E5C` ✅）✅ 好处是**深色主题自动跟着变** ✅。**BASDAI 弹窗顶部另加「其余未答的题，按「无」记」** ✅ —— 维护者原话「大部分时间没有晨僵，但要手动先点 1 再点回 0」✅ 但**不能**把六题初值设成 0 ✅ 那会产出「打开即提交」的假 0.0 分 ✅ 正是 v1.1.3（J-6）要拦的东西 ✅ —— 差别在**谁做的声明**：默认 0 是**系统替患者答** ✅ 本按钮是**患者一次显式声明** ✅ 语义等价于逐题点「无」 ✅。⚠️ **并记录三次我自己的文件操作事故**（详见 §事故）：一次**用目测行号把整个文件截断**（`git diff` 显示删 912 行，只想删 167 ✅）✅ 一次**写文件时参数用错**导致编码/换行错乱（detekt 报 12 条 ✅）✅ 一次**在已改多轮的文件上反复做文本替换、越改越糟**（7 条 ✅）—— **三次都靠 `git checkout --` 完整恢复** ✅ **无实际损失** ✅ 但累计多花约 40 分钟 ✅。**还原**：`ScoreInput` 两端那对「点此：无 / 点此：最严重」按钮已按维护者要求**移除** ✅ 回到 v1.0.18 的纯文字可点标签 ✅（**功能保留** ✅ 那两个文字**仍可点** ✅ 是 W3 为「滑杆点当前值零回调」留的可靠兜底 ✅）。单测 **822 条全绿** ✅。
- **上一版**：**v1.0.73（versionCode 78）· 批次 1「看不见的失效」**（依据三份外部审查报告 + 维护者 2026-09-29 的 9 项裁决）：闹钟注册失败不再静默（`AlarmRegister` + `ReminderHealth` + 自检页第 5 项）／活疫苗安全警报的**默认值反转**（表单默认 `CONFIRMED → PENDING`，判定抽到 `domain/VaccineSafety`，补 7 条回归测试）／末级强提醒投递前检查 `canUseFullScreenIntent()` 并明确降级为常驻提醒／升级链认可 `skipped`／「稍后」真排 snooze（15 分钟、不上链）／`WAKE_LOCK` 兜住 goAsync 协程／**跨零点身份锚回槽位所属日**（`EXTRA_SLOT_DATE` 全链透传 + `cancelAllFuture` 扫 `today-1` + requestCode 改 SHA-256 派生）／PDF 生成下 IO／体征保存门禁／口令与恢复码页禁截屏。**519 条单测全绿**。**已发布为 GitHub 正式版（Latest）**：`git -c http.version=HTTP/1.1 push`（远端 main = `adb296b`）→ `gh api -X POST releases --input rel-v1073.json`（`make_latest=true`）→ 两资产 `curl --http1.1` 上传 → `gh api …/assets` 摘要与本地 `Get-FileHash` **逐字节一致**；v1.0.72 已加「已被 v1.0.73 取代」横幅。详见 `CHANGELOG.md` v1.0.73。
- **上一版**：**v1.0.72（versionCode 77）**——小米 / HyperOS 适配收口：依据小米官方《开发最佳实践与兼容性建议（适配常见问题）》§9/§10/§11/§12，在自检卡与「锁屏紧急信息」块补出「锁屏显示 / 后台弹出界面 / 自启动」引导，自启动跳转改用官方 action `miui.intent.action.OP_AUTO_START`（详见 §7 小米条与 `CHANGELOG.md` v1.0.72）；同版把**提醒自检整块移入二级页** `ui/me/ReminderCheckScreen.kt`（路由 `ReminderCheck`）——「我的」只留一行入口 + 状态摘要（`n/4 项已就绪`），`MeScreen.kt` 424 → 227 行。**v1.0.72 已转正为 GitHub 正式版（Latest）**：`gh release create v1.0.72 --target 7b98206 --latest`，两个资产（release / debug APK）上传后 `gh api …/assets --jq .digest` 与本地 `Get-FileHash` **逐字节一致**；v1.0.71 已按要求加「此版本已被 v1.0.72 取代」横幅（正文完整保留）。**同日 22:10 追加闭环**：在小米通道页打开「悬浮通知」+「在锁定屏幕上 → 显示通知及其内容」后，横幅 / 锁屏 / **末级强提醒全屏**全部正常（v1.0.61 强提醒链在 Xiaomi 15 Pro / Android 16 上验证通过；详见 §7 与 §9.9）。v1.0.70 在 Xiaomi 15 Pro / Android 16 上逐项真机走查后，修掉两处系统设置跳转缺陷（「允许强提醒」是**点了毫无反应的死按钮**：漏 `data=package:` + `runCatching` 静默；「申请精确闹钟」跳到全部应用列表页）与一处 chip 组溢出缺陷（骶髂分期 6 个 chip 用 `Row`，「III 中度」「IV 重度」被挤出屏幕选不到），并新增「删除已停用药品」（连带删打卡记录，确认框报条数）。**2026-09-27 真机复验：4 项修复全部正常 → `gh release edit v1.0.71 --prerelease=false --latest` 转正**；v1.0.61 ~ v1.0.70 转为历史预发布（各 Release 页均带「已被取代」横幅）。真机走查已结项：Android 16 WebDAV 反射回归 / 8 通道 / 电池白名单 / 锁屏紧急卡 / 测试提醒 / 冷启动五 Tab。以下为历史沿革：v1.0.21 知识库检索优化 + v1.0.22 清除 `as MutableStateFlow` 强转 + v1.0.23 化验 Tab 分组折叠 + v1.0.24 文案资源化 + v1.0.25 Compose 性能审查第一批 + v1.0.26 紧急卡「当前用药」+ v1.0.27 备份恢复码（v2 信封）+ v1.0.28 Compose 性能审查第二批 + v1.0.29 热修启动闪退 + v1.0.30 趋势图动画修复 + v1.0.31 药单在用药品编辑 + v1.0.32「我的」页关于卡 + v1.0.33 规划缺口第二批（Room v9→v10）+ v1.0.34 附件归档增强（Room v10→v11：化验/影像归属复诊记录）+ **v1.0.35 附件接入 WebDAV 备份（Room v11→v12 + 备份格式 v2→v3：稳定 vault key / 逐个加密上传 / 懒下载 / 同步删除 / 同步开关）** + v1.0.36 附件远端校验与补传（PROPFIND 列远端比对：远端缺失补传 / 远端多余清理；无结构变更） + **v1.0.37 用药精细化 + 化验与筛查（Room v12→v13：C5 原因枚举对齐 / C6 减量中态 + 医嘱减量豁免警示 / C2 补剂依从 / C3 化验按单位分组 + 跨院提示 / C10 生物制剂筛查与续方种子）** + **v1.0.38 营养素精细化 + 周月报（Room v13→v14：B11 补剂每日上限 + 与用药错开提醒 + 服药/补剂合并时间表；B4 报表第 4 页签「周月报」）** + **v1.0.39 推荐食谱库 + 周期康复计划（Room v14→v15：B3 `recipes` 表 + 10 条带出处种子；B7 `exercise_plans` 表 + 4/8/12 周模板 + 完成度反算）** ⚠️ **该版冷启动闪退、已废弃（勿发布）；功能随 v1.0.40 交付** + **v1.0.40 冷启动兜底 + 崩溃留档（两个新 VM 改为进页面才创建 + 启动期整体兜底 + CrashLogger；无结构变更）** ⚠️ **其「修复」不成立：只是把启动期异常吞掉，崩溃从「冷启动」挪到了「点添加模板」** + **v1.0.41 留档与兜底改进（`CrashLogger.recordNonFatal` + 崩溃摘要带 `Caused by`/栈帧 + 两条 proguard `-keep`）** ⚠️ **其「修复」不成立：归因 R8 是错的（`-keep` 与本根因无关）** + **v1.0.42 真正修复「康复计划 → 添加模板」闪退（真因：`ExercisePlanTemplates` 的正则末尾有一个未转义的 `}`——Java 的 `Pattern` 视为普通字符，Android 的 ICU 引擎抛 `PatternSyntaxException`，致类初始化失败 → 启动期被吞 → 二次触碰 `NoClassDefFoundError: K1.n`；修法：`parse` 移除正则改逐字符扫描 + `esc` 转义 `\n`/`\r`/`\t`；无结构变更）** —— **v1.0.38 / 39 / 40 / 41 均已被 v1.0.42 取代**（v1.0.39–41 为预发布且含缺陷，已在各 Release 页顶部标注；v1.0.38 功能全部含于 v1.0.42），**v1.0.42 已被 v1.0.43 取代**（功能全部含于 v1.0.43，无需再装本版） + **v1.0.43 代码审查后的 A / B 类修复 + 口令框「长按显明文」** + **v1.0.44 第三轮审查修复（N1 开机广播补 doneRefs + 删参数默认值 / N2 README 修正 / N3 种子版本化增量刷新 / S3 vault key 覆盖守卫 / S4 备份类型不符可定位报错 / S5 崩溃留档凭据脱敏；另落地正则花括号静态守卫）** + **v1.0.45 趋势页改造（方案 A）+ 7/30/90 天切换（X 轴按日期定位 / 刻度自适应 / 高度确定化 / 数据点与末点数值 / 阈值入轴槽 / 读数摘要行）** —— ⚠️ **v1.0.44 / v1.0.45 均为未验证预发布且各含缺陷（v1.0.45 的 4 处趋势图缺陷见 v1.0.46），两者均已被 v1.0.46 取代，请直接跳过** + **v1.0.46 审查修复（无结构变更：v1.0.45 的 4 处趋势图缺陷——拖动命中选错点 / 动画期间末点数值提前出现 / 阈值标签边缘溢出 / xAt 未排序防御；另 setTrendDays 过期响应竞态 + 正则守卫覆盖面告警 + 死资源清理）** ⚠️ **未真机验，已被 v1.0.47 取代** + **v1.0.47 修复打分滑杆「完全无法拖动」（无结构变更：删除 v1.0.17 引入的同级透明覆盖层——Compose 同级重叠只有 z 序最高者算命中，覆盖层独占了指针事件；点按旁听改挂祖先 Box；拖动判定改累计位移；旁听只补 M3 不响应的「点按落在当前值上」情形）** ⚠️ **未真机验，已被 v1.0.48 取代** + **v1.0.48 药单新增「用药记录」与「已停用药品」（无结构变更，仅新增只读查询：点药名看近 90 天逐条流水 + 该药依从率；底部折叠区显示停药日期/原因/备注并可点开记录；依从率公式由 ReportRepository 内联 4 遍收拢为 `domain/AdherenceCalc` 唯一实现）** + **v1.0.49 用药记录可手动修正 + 注射部位显示中文（无结构变更：记录行加铅笔可改状态/原因/注射部位/备注，归属日与计划时刻只读；注射部位键→中文映射由 TodayScreen 私有函数收拢为 `InjSite` 枚举并删掉 6 条死字符串；编辑不变量抽成 `domain/MedLogEdit`）** + **v1.0.50 修复今日页「已服」后显示一串数字（无结构变更：`nowIso()` 的 `ISO_LOCAL_DATE_TIME` 在纳秒非零时会追加小数秒，串长在 19/23/26 间浮动，而展示侧用 `takeLast(5)` 从尾部截「HH:mm」，截到的是小数秒——「已服 22:31」显示成「已服 19981」；新增 `ScheduleCalc.hhmm()` 改为解析后再格式化）** + **v1.0.51 注射类补上「计划用药时间」+ 口服字段改名（无结构变更：表单里时刻选择此前只在口服分支渲染，注射的时刻被静默写成默认 08:00 且全应用无处可见——今日卡 chip 显示的是 `PlanSlot.label`，而注射槽位 label 被写死为「注射」；现抽出共用 `PlanTimePicker`（口服多选 / 注射单选），字段改名「计划用药时间（到点提醒）」，注射槽位 label 改为计划时刻，并修正不实的「默认 09:00」说明）** + **v1.0.52「计划用药时间」改用真正的时间选择器（无结构变更：删除固定候选 chip 与「自定义 HH:mm」输入框，改用 M3 `TimePicker`（24h）；当前已设时刻**始终以文本显示**——旧实现用 chip 选中态表达当前值，存的时刻不在那 5 个候选里（如 07:30）就完全看不出来；新增 `ScheduleCalc.timeParts()` 容错解析选择器初始值；默认时刻统一为唯一常量 `DEFAULT_PLAN_TIME`，此前表单 08:00 与 `slotsFor` 兜底 09:00 不一致）** —— v1.0.52 曾为正式版（2026-09-23 用户真机确认后转正），现为历史版本；v1.0.44~v1.0.51 仍为预发布且各含「已被 v1.0.52 取代」横幅，仅作历史留存；v1.0.38~v1.0.41 那批废弃版未转正（v1.0.39 冷启动必崩，切勿标为正式版） + **v1.0.53 S1 落地：提醒链首次有自动化回归测试（无结构变更：引入 Robolectric 4.13 仅测试期依赖 + `ReminderSchedulerTest` 9 条锁「取消-重建」语义；为让测试任何时刻运行都确定，`rescheduleAll` / `cancelAllFuture` 增加带默认值的 `now` / `today` 参数——**这是本版唯一的生产代码改动**；S1b 滑杆手势测试已实测证伪、不予落地）** + **v1.0.54 趋势页方案 C：2 列小多图同屏 + 共享时间轴 + 新增 ESR / CRP 客观炎症指标（无结构变更：新 `domain/LabIndicator` 指标目录（名称归一 + 单位换算，刻意不做模糊匹配）+ `domain/LabTrends` 纯函数（单位换算/窗口/同日去重三类口径 + 未纳入条数如实计数）+ `ui/components/SmallTrendChart` 小多图单元；炎症阈值进 `ClinicalThresholds.ESR_HIGH/CRP_HIGH` 并优先用化验单自带 `refHigh`；点任一格可展开回 v1.0.45 的大图，不丢拖动读数）** —— v1.0.53 曾为正式版（2026-09-24 用户确认后转正），**已于 v1.0.58 转正后转为历史版本**；v1.0.52 亦为历史版本并带取代横幅；v1.0.44~v1.0.51 仍为预发布且各含取代横幅，仅作历史留存；v1.0.38~v1.0.41 那批废弃版未转正（v1.0.39 冷启动必崩，切勿标为正式版） + **v1.0.55 修 v1.0.54 炎症指标四问题（无结构变更：① 化验不再套 7/30/90 天窗口（季度采样，套 30 天几乎永远为空）——改 `LabResultDao.allOrdered()` + `LabTrends` 窗口参数改可选；② 日常与化验**两套独立共享时间轴**（采样频率差 1~2 个数量级，同轴必然压扁一方）；③ 指标名识别改「有界变形（剥尾限定词 / 去括号）后命中别名表」——v1.0.54 靠枚举漏了真实数据「红细胞沉降率测定」；④ 空格子文案按分组传入（收缩压 / 心率 曾误显示「暂无化验数据」）；⑤ 格子标题只用中文名，缩写移到大图标题）** —— **v1.0.55 为当前预发布（含 v1.0.44~v1.0.54 全部内容），待真机确认后转正** + **v1.0.56 修「hs-CRP 并入 CRP 导致数值画错」（无结构变更：hs-CRP 独立成第三项指标 `HSCRP`（CRP 别名表移除全部超敏写法，两者互不匹配、阈值各取自己那份 refHigh）；新增 `LabIndicator.alwaysShow`——ESR/CRP 恒占格、hs-CRP 仅在有数据时占格；同日不同数值新增 `LabTrend.sameDateConflict` 计数并在图下如实说明，不再静默取其一；`allOrdered()` 排序补 `recorded_at, id` 使选择可复现）** —— **v1.0.56 为当前预发布（含 v1.0.44~v1.0.55 全部内容），待真机确认后转正** + **v1.0.57 真修「同日多值画错」（无结构变更：去掉「同日取最近录入一条」规则——**同日多个不同数值全部画出、一个不丢**（完全相同值才合并）；`LabTrend.sameDateConflict` 改为 `conflictDates`（日期数），文案改为「有 N 个日期存在多条不同数值（均已画出）」；**移除 hs-CRP**（用户要求）——`LabIndicator.HSCRP` 与 `HSCRP_HIGH` 删除、`alwaysShow` 一并删除，CRP 别名表不含超敏写法）** + **v1.0.58 拖动读数与说明文案收尾（无结构变更：① **拖动读数不再只报一条**——`nearestIndex` 用严格小于比较、命中同日**第一个**下标，而同日多值在 `points` 里按数值升序，于是永远报到较小的 0.4（用户实测「手指拖动到 3.13 还是显示 0.4」）；新增纯函数 `sameDateIndices` 一次取出该日**全部**下标，高亮与读数都不再只报一条，气泡文案由 `selectionText` 生成、同日多值全部列出**且从大到小**；② 撤掉 v1.0.57 加在小图下的那行「有 N 个日期存在多条不同数值（均已画出）」——那些值本就已全部画出、图上一段竖线已把话说清，多挂一行字只会把该格撑高、与左侧格不齐、破坏 2 列小多图的整齐；连带删除只服务于它的 `LabTrend.conflictDates` 与字符串 `report_lab_same_date_conflict`，`hasCaveat` 只保留「有数据被排除在外」）** —— **正式版（Latest）为 v1.0.58（2026-09-25 用户真机确认后转正）**；v1.0.54 ~ v1.0.57 仍为预发布且各含取代横幅（v1.0.57 的横幅已改为「已被 v1.0.58 取代」），仅作历史留存 + **v1.0.59 B5 多源提醒（无结构变更：三源独立 Scheduler——`CheckupReminderScheduler`（复诊提前1天+当日 09:00，无升级链）/ `BasdaiReminderScheduler`（评估日 20:00 + +1/+2 天 20:00 升级，周期可配 7/14/28/56/84 天）/ `ExerciseReminderScheduler`（18:00 + 20:00/22:00 升级，今日已打卡则不排）；3 个新通知通道（`checkup_reminders` HIGH / `questionnaire_reminders` DEFAULT / `exercise_reminders` DEFAULT）；requestCode 命名空间 `chk|`/`bas|`/`exc|` 防与用药撞，通知 ID 前缀 `n_chk|`/`n_bas|`/`n_exc|`；3 个新 Receiver 验真 extra 后发通知（不在 Receiver 内排下一级，升级链在 rescheduleAll 时全部预排）；`ReminderConfigRepository` 管理 `reminder_config` prefs（非密钥 prefs，不跨备份恢复）；`BootReceiver` / `AshkbApplication` 启动期 + 4 个 ViewModel 数据变更点（saveCheckupRecord / saveBasdai / checkIn / activate-deactivate）即时 reschedule；「我的」页新增「提醒设置」卡（`ReminderSettingsSheet`：复诊/运动开关 + BASDAI 周期单选，即时写入+重排）；18 条 Robolectric 单测覆盖 7 天窗口/升级链/幂等/取消语义）** —— **正式版（Latest）为 v1.0.59**；v1.0.58 转为历史版本 + **v1.0.60 B8 免打扰时段 + 同时段多提醒合并推送（无结构变更：`domain/DndWindow.kt` 跨午夜纯函数（左闭右开 / start==end 视为全天）+ `ReminderConfigRepository` 加 `dnd_enabled`/`dnd_start`（默认 22:00）/`dnd_end`（默认 07:00）；免打扰走**静默投递**而非顺延——`NotificationHelper.postXxxReminder` 加 `silent` 参数，静默走新通道 `reminder_silent`（IMPORTANCE_LOW / 无声无振 / 通知栏仍可见），4 个 Receiver 触发时调 `NotificationHelper.isInDndNow(context)` 实时读配置（开关与时间变更**只写 prefs、无需重排闹钟**）；同时段合并推送——4 类提醒全部 `setGroup(ashkb_reminders)`，`updateGroupSummary` 在 2+ 条时发 `GROUP_ALERT_SUMMARY` 折叠通知（「您有 N 条待处理提醒」）、不足 2 条时撤掉 summary，post 与 cancel 两路都调用；`ReminderSettingsSheet` 加免打扰开关 + 起止时间（系统 `TimePickerDialog`，`SettingSwitchRow` 扩 subtitle 位）；12 条 `DndWindowTest` 单测（跨午夜/同日/边界/全天））** —— **正式版（Latest）为 v1.0.60**；v1.0.59 转为历史版本 + **v1.0.61 B9 升级链第三级「强提醒」（无结构变更：用药链末级（esc==`MAX_ESCALATION`）改用 `fullScreenIntent` 拉起新增 `ReminderFullScreenActivity`（`showWhenLocked` + `turnScreenOn` + `taskAffinity=""` 独立任务栈不进最近任务，API 27 边界守卫；界面含药名/剂量/计划时间 + 「已服用」（免开应用写库打卡）/「稍后处理」）；`ReminderScheduler.isStrongEscalation(escalation)` 纯函数判定末级；`NotificationHelper.postMedReminder` 加 `strong` 参数 + `setFullScreenIntent`，文案换 `notif_med_strong_*`；仅用药启用（BASDAI/运动非紧急不升级），DND 静默时段内不升级全屏（`strong && !silent` 才生效）；Manifest 加 `USE_FULL_SCREEN_INTENT`，Android 14+ 默认不授予 → 「提醒可靠性自检」卡加「强提醒（全屏）」行 + `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` 引导按钮，未授予优雅降级为横幅；单测 +1 条）** —— **正式版（Latest）为 v1.0.61**；v1.0.60 转为历史版本 + **v1.0.62 C11 提醒可靠性收口（无结构变更：电池白名单——自检卡用 `PowerManager.isIgnoringBatteryOptimizations` 显真实状态 + 「加入电池白名单」一键跳（官方直连弹窗优先，兜底电池优化列表页），Manifest 加 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；自启动——新增 `reminder/SystemSetupGuides.kt` 按厂商（小米/华为/荣耀/OPPO/一加/realme/vivo/iQOO/三星/魅族）组件名尽力跳转 + 兜底应用详情页，**刻意不做状态行**（系统无公开查询接口）；测试提醒——新增 `reminder/ReminderTest.kt`（10 秒后精确闹钟，`nowMs` 可注入）+ `TestReminderReceiver` + `NotificationHelper.postTestReminder`（走 `sys_notices`，不归入折叠组），端到端验「权限 + 精确闹钟 + 通道 + 投递」，DND 时静默；另修「自检卡从系统设置页返回后状态不刷新」——`ON_RESUME` 观察者触发重读；动作按钮横排 Row 改纵向满宽 Column（最多 4 个会溢出）；单测 +4 条）** —— **正式版（Latest）为 v1.0.62**；v1.0.61 转为历史版本 + **v1.0.63 C12 免责声明收口（无结构变更：首启声明门禁——新增 `ui/DisclaimerScreen.kt` 的 `FirstLaunchDisclaimer` 全屏页，`AppShell` 顶部早返回（`rememberSaveable` + `data/repo/DisclaimerStore.kt` 存 `app_prefs.disclaimer_accepted`），**确认前不创建任何 ViewModel / 不打开数据库**；四条要点：非医疗建议/不替代诊疗 · 用药与调整以主治医师医嘱为准 · 数据仅存本机不上传 · 不是医疗器械；统一前缀——唯一文案来源 `domain/Disclaimer.kt`（`PREFIX`「仅供参考，以主治医师医嘱为准。」+ 4 条声明 + `FIRST_LAUNCH_POINTS`，与 `RecipeSources.DISCLAIMER` 同理放 domain 以便单测锁措辞）+ 共享组件 `ui/components/DisclaimerNote.kt`，应用于三处自动生成提示（漏服处理 / 复诊准备 / 跨院化验）并收敛各自重复措辞（`missed_dose_disclaimer` / `checkup_prep_disclaimer` / `lab_unit_group_hint`）；**条目级（知识库 / 食谱）与 PDF 页脚刻意保持自足完整文本**——要脱离 App 被阅读；`DisclaimerTest` 6 条）** —— **正式版（Latest）为 v1.0.63**；v1.0.62 转为历史版本 + **v1.0.64 B13 生活方式画像（无结构变更，复用闲置的 `profile.lifestyle` 列：新增 `domain/Lifestyle.kt`（扁平 JSON 手写编解码——零第三方依赖 + domain 纯 JVM 拿不到 `org.json`；`hasSmokingHistory` / `pinnedKbIds()`）+ `domain/LifestylePrescription.kt`（画像→个性化提示：戒烟 / 久坐每 30–45 分钟起身 / 睡眠不足不宜过量 / 无习惯从 L1 起步 / 有习惯按进展原则加量；**刻意只出提示、不改 R27 红黑榜过滤**——过滤依据是分期与活动度，把吸烟/久坐/睡眠塞进过滤器属无依据的医学决策）；`ProfileEditScreen` 补采集（吸烟 / 运动习惯 chips + 久坐 / 睡眠数字输入），`MeScreen` 档案卡加「生活方式」一行摘要；`ExerciseUiState` 加 `lifestyleNotes`，运动页 hero 在判读依据下新增「生活方式提示」块并复用 `DisclaimerNote`；**顺带修掉 §D 最后一项悬空挂点**——`KnowledgeViewModel.uiState` 由 4 路 combine 扩为 5 路（并入 `observeProfile()`），登记吸烟后「全部」视图置顶 `edu-003`（检索 / 分类视图不置顶）；`LifestyleTest` 7 + `LifestylePrescriptionTest` 8）** —— **正式版（Latest）为 v1.0.64**；v1.0.63 转为历史版本 + **v1.0.65 B12 极简模式状态机（⚠️ **含结构变更 Room v15→v16**——本版起不再是「无结构变更」：`profile` 加 `minimal_since TEXT`（`MIGRATION_15_16`）。红线三 e2：新增 `domain/MinimalMode.kt` 纯函数状态机（`THRESHOLD_DAYS=3` / 连续缺失计数**封顶为阈值** / `entersMinimal` 只认身体不适与住院 / `isConsistent` 锁 ui_mode 与 minimal_since 的成对不变式）+ `data/repo/MinimalPromptStore.kt`（`app_prefs` 记「今日已问」）；`HealthRepository` 加 `symptomDatesBetween` 与 `setMinimalMode`（成对写入）；`TodayViewModel` 加 `app` 入参与 `minimalPrompt` 流 + `answerMinimalPrompt`/`exitMinimalMode`；今日页加极简横幅（自 YYYY-MM-DD + 退出按钮）与原因询问弹窗（**不给点外部关闭**），极简态隐藏运动快捷入口、保留告警与用药；**核心记录口径拍板为 `symptom_daily`**（不选用药打卡：无在用药品时天然为空会误判）；`ProfileEditScreen` 补透传 `minimalSince`（否则编辑档案会写成不一致状态）；备份引擎无需改（表名 `sqlite_master` 动态、列名 `PRAGMA table_info` 动态校验）；`MinimalModeTest` 8）** —— **正式版（Latest）为 v1.0.65**；v1.0.64 转为历史版本 + **v1.0.66 B6a 锁屏紧急信息（无结构变更：紧急卡页新增「锁屏显示紧急信息」开关（**默认关闭**，`app_prefs` 存 `emergency_lockscreen_enabled`）；新增 `domain/EmergencyLockscreen.kt` 纯函数文案构建（过敏 JSON 数组展平为顿号 / 免疫抑制类标注感染风险 / 用药封顶 `MAX_MED_LINES=4` 并报总数 / 家属优先于医生 / 非紧急联系人不进锁屏）+ `data/repo/EmergencyLockscreenStore.kt` + `reminder/EmergencyLockscreenPublisher.kt`（读库→构建→投递；开关关或无可显示内容则撤下通知）；`NotificationHelper` 加通道 `emergency_lockscreen`（IMPORTANCE_LOW / 锁屏公开可见 / 不响不震）与 `postLockscreenEmergencyCard`（`VISIBILITY_PUBLIC` + `setOngoing` + `setOnlyAlertOnce`）/`cancelLockscreenEmergencyCard`；`AshkbApplication` 启动期同步一次、`EmergencyScreen` 页面内数据变化即时同步（**不做后台轮询**：无后台服务与 WorkManager）；**为何用常驻通知**——Android 无面向普通应用的自定义锁屏控件（锁屏 Widget 已废弃），`VISIBILITY_PUBLIC` 通知是唯一受支持途径；`EmergencyLockscreenTest` 9）** —— **正式版（Latest）为 v1.0.66**；v1.0.65 转为历史版本 + **v1.0.67 C1 骶髂关节影像分期（⚠️ **含结构变更 Room v16→v17**：`profile` 加 `sacroiliitis_grade TEXT`（`MIGRATION_16_17`）。补规划 M0「诊断信息全量」缺的最后一项：`Labels` 加 `SACROILIITIS_KEYS`（"0".."4"）+ `sacroiliitisGrade()`（「0 正常 / I 可疑 / II 轻度 / III 中度 / IV 重度」，罗马数字在标签内、key 不进 UI）；建档表单 chips（未评估 + 0–IV）+ 「我的」档案卡 + **复诊报告 PDF** 基本信息区；**刻意不进打印版应急卡与锁屏卡**（急救场景只要即刻信息）；**`BackupRepository` 的档案 JSON 导入导出是独立字段清单**，双方向同步补该字段（否则导出再导入会丢）；`LabelsTest` 4）** —— **正式版（Latest）为 v1.0.67**；v1.0.66 转为历史版本 + **v1.0.68 C8a 久坐起身提醒（无结构变更：**提醒链第四源**。新增 `domain/SedentaryReminder.kt` 纯函数——`nextFire(now, startHour, endHour, intervalMin)` 返回**严格晚于 now 的第一个网格点**，今日网格走完则返回**次日窗口起点**（链式调度天然连续、无需每日重新武装）；`isWithinWindow` 供 Receiver 做陈旧闹钟兜底；`isValidWindow` 非法窗口视同关闭；`reminder/SedentaryReminderScheduler.kt` **链式单发**（固定 requestCode，任何时刻只有一个待触发闹钟；**不用 `setRepeating`**——API 19 起不精确且改配置取消不干净）+ `SedentaryReminderReceiver`（三件事顺序固定：验真→发通知→**无论如何都续链**）；`ReminderConfigRepository` 加 `sedentary_enabled`/`_interval_min`/`_start_hour`/`_end_hour`；提醒设置面板加开关（**默认关**）+ 间隔 chips（30/45），活动时段固定 9:00–18:00 **只读展示**（配置项已备好，将来加选择器即可）；`NotificationHelper` 加通道 `sedentary_reminders`（DEFAULT）+ `postSedentaryReminder`（**固定 ID 单条覆盖**，不堆叠）；`AshkbApplication` 启动 + `BootReceiver` 各重排一次；`SedentaryReminderTest` 9）** —— **正式版（Latest）为 v1.0.68**；v1.0.67 转为历史版本 + **v1.0.69 C8b 晨僵驱动的起床热身序列（无结构变更：把此前只当「判读依据」展示的晨僵真正接进运动处方。新增 `domain/MorningWarmup.kt` 纯函数 `build(minutes, plan)`——**动作不新造**，直接取当日处方里的 **L1 轻柔项**（R27 已判定「今日可做」且排除 pause / 拦截），封顶 `MAX_STEPS=4`；阈值分档 `MIN_REPORTABLE=15`（未记录或低于则整块不展示，避免噪音）/ `PROLONGED=30`（headline 加「（明显延长）」+ 注里加「常提示炎症活动…连续多日如此复诊时告知医生」），措辞与既有 `ExerciseEngine.interpretFeedback` 的「晨僵加重——可能提示炎症活动」**同一口径**，不引入新说法；处方无 L1 项时 `steps` 为空、由 UI 给**非处方的通用提示**（缓慢起床 / 室内慢走，以疼痛不加重为界）；`ExerciseViewModel` 加 `warmup` 流（`combine(uiState, yesterdaySymptom)`），`ExerciseScreen` 的 `PrescriptionHero` 加 `warmup` 参数渲染在 hero 内、B13 画像提示之前；`MorningWarmupTest` 9）** —— **正式版（Latest）为 v1.0.69**；v1.0.68 转为历史版本

---

## 三、规划文档需求缺口台账（2026-09-18 逐条核对）

> 原为 `HANDOFF.md` §8 的 A / B / C / D 四张表。`HANDOFF-STATUS.md` §1 已记「规划缺口池全部结项」
> （A 类 3 项、B 类 13 项、C 类 12 项、D 类种子挂点全部落地或已取消），故本表转为历史存档。
  #### 规划文档需求缺口（2026-09-18 逐条核对）

> **来源**：`<旧工作区>/patient-health-plan/patient-health-plan-v3.html`（V3.0，权威）+ `patient-health-plan-v2.html`（= V2.1 final 正文）+ `archive/patient-health-plan-v2.1-final-20260829.html`，对照代码 v1.0.25。
> **方法**：全文提取 R 编号项 / M0~M10 模块清单 / 阶段 2 之后项 / 本期不做项，逐条 grep 类名·DAO·实体·字段·字符串资源核实；下表关键项已人工二次复核（标 ✅ 者为已复核）。
> ⚠️ 本节是**需求侧缺口**，与上面「审查报告类待办」性质不同：多为「规划过但未实现」，非缺陷。

**A. 安全相关（建议优先，✅ 均已复核）**

| # | 缺口 | 核实依据 |
|---|---|---|
| ~~A1~~ | ~~紧急卡缺「当前用药」~~ —— **已于 v1.0.26 落地**（紧急卡页面 + 打印版 PDF 自动汇总在用药单，免疫抑制类置顶标注；见「已关闭的旧待办」） | — |
| ~~A2~~ | ~~备份无「恢复码」~~ —— **已于 v1.0.27 落地**（v2 信封格式双密钥槽，口令/恢复码任一可解；见「已关闭的旧待办」） | — |
| ~~A3~~ | ~~KDF 为 PBKDF2 而非规划写的 Argon2id~~ —— **评估结论文档化（v1.0.27）**：不引入 Argon2id（Android 无内置；BC 有 provider 冲突史 / argon2 native +1MB APK）；v2 每槽自带 `kdf`/`iter` 参数，将来可在新槽内无破坏切换，旧文件按头内参数解。架构铺路完成，实现延后 | — |

**B. 功能缺口（规划写了、代码里没有）**

| # | 缺口 | 规划出处 |
|---|---|---|
| ~~B1~~ ❌ | ~~**AS 专项体征**（胸廓扩张度 / 枕墙距 / 指地距 / Schober）~~ —— **已取消（2026-09-21 用户拍板）**：四项均须医生诊室测量（Schober 需先定位标记、胸廓扩张度的测量平面与手法有讲究），自测误差大且易造成误导（虚惊或虚假安心）。R26 定为「部分实现」：情绪 / 睡眠 / 疲劳已做，体征四项**不做**。连带：种子 exc-004 文案已改写（不再引用 `body_measures` 联动；⚠️ 种子仅首启导入，已有设备上该条详情仍显示旧文案，属可接受残留） | R26 / M6 |
| ~~B2~~ ✅ | ~~知识库「个人备注层」~~ —— **已于 v1.0.33 落地**（`kb_entries.user_note` 列 + 详情弹窗写/改/清除 + 列表「有备注」标记；只更新该列，种子内容不受影响；四处弹窗均可编辑） | v3 §1 K / v2 R14 |
| ~~B3~~ | ~~推荐食谱库（按抗炎 / 胃肠友好 / 控热量标签，可筛选收藏、可自建）~~ —— **已于 v1.0.39 落地**（`recipes` 表 + 10 条种子食谱（**带出处编号 S1–S9，点开详情才展开完整题录**）+ 标签筛选 / 收藏置顶 / 自建；第 6 条按用户要求弱化为「减少精制淀粉」而非「低淀粉疗法」） | — |
| ~~B4~~ | ~~周报 / 月报（本机查看的结构化小结）~~ —— **已于 v1.0.38 落地**（`ReportRepository.periodicReport(days)` + 报表第 4 页签「周月报」，近 7 / 30 天切换；口径与概览一致，无数据显示「暂无」） | — |
| B5 ✅ | 多源提醒（运动 / BASDAI 问卷 / 复诊）——**已于 v1.0.59 落地**（三源独立 Scheduler + 3 通道 + ReminderSettingsSheet + 18 单测；无结构变更） | `ReminderScheduler.rescheduleAll(context, meds)` 只遍历药单；全部调用点只传药单 |
| B6 🔶 | 紧急卡「锁屏紧急信息集成」**✅ 已于 v1.0.66 落地**（`EmergencyScreen` 开关（默认关闭）+ `emergency_lockscreen` 常驻通道（IMPORTANCE_LOW / 锁屏公开可见 / 不响不震）+ `NotificationHelper.postLockscreenEmergencyCard`（`VISIBILITY_PUBLIC` + `setOngoing`）+ `domain/EmergencyLockscreen.kt` 纯函数文案（过敏 JSON 展平 / 免疫抑制标注 / 用药封顶 4 条报总数 / 家属优先 / 非紧急联系人不进）+ `reminder/EmergencyLockscreenPublisher.kt`（启动期 + 页面内数据变化即时同步；开关关或无内容则撤下）+ `EmergencyLockscreenTest` 9）；**「钥匙扣二维码模板」仍未做**——需自写 QR 编码器（零第三方依赖），留作独立增量 | R19 三项只落打印版 PDF；`grep 锁屏` 此前零命中 |
| ~~B7~~ | ~~4–12 周周期康复计划模板（按周递进 + 完成度追踪）~~ —— **已于 v1.0.39 落地**（`exercise_plans` 表 + 3 模板（4 / 8 / 12 周）幂等种入；`week_structure` 每周「强度级别 + 目标天数 + 提示」；完成度由 `exercise_logs` 去重日期反算；同刻只启用一个） | — |
| B8 ✅ | 免打扰时段 + 同时间段多条提醒合并推送——**已于 v1.0.60 落地**（`domain/DndWindow.kt` 跨午夜纯函数 + 静默通道 `reminder_silent` + 通知组 `ashkb_reminders` summary + 设置面板开关/起止时间；Receiver 实时读配置，无需重排闹钟；12 单测） | M10；零实现 |
| B9 ✅ | 提醒升级链第三级「强提醒（全屏 / 横幅）」——**已于 v1.0.61 落地**（用药链末级改用 `fullScreenIntent` + `ReminderFullScreenActivity`（showWhenLocked/turnScreenOn）；Android 14+ 需 `USE_FULL_SCREEN_INTENT`，自检卡给授权引导，未授予降级为横幅；仅用药启用，DND 时不升级全屏） | v3 M10；无 `fullScreenIntent`，`MAX_ESCALATION` 到 2 即止 |
| ~~B10~~ | ~~复诊「结果照片归档」（化验单 / 影像报告拍照存档与检索）~~ —— **已于 v1.0.33 落地**（拍照 / 相册 / PDF 三种来源，落 `filesDir/checkup_attachments/`，DB 存元数据；⚠️ 附件不随数据库备份，换机需重新导入） | M6 |
| ~~B11~~ | ~~营养素「每日上限警示」+「与用药时间错开提醒」+「与 M1 服药时间表合并视图」~~ —— **已于 v1.0.38 落地**（补剂可量化剂量 + 用户设定每日参考上限（**刻意不内置医学上限值**）；矿物类补剂与螯合类用药（左甲状腺素/四环素/喹诺酮/铁剂）间隔 <2h 提示；今日服药 + 补剂合并时间表） | — |
| ~~B12~~ | ~~极简模式状态机（连续 3 天核心记录未完成 → 询问原因 → 身体不适/住院切极简模式）~~ —— **已于 v1.0.65 落地**（红线三 e2。**Room v15→v16**：`profile` 加 `minimal_since`；`domain/MinimalMode.kt` 纯函数状态机——**核心记录口径拍板为 `symptom_daily`**（唯一「每日必填且与用药清单无关」的自评入口；刻意不选用药打卡——无药时天然为空会误判）；连续缺失计数**封顶为阈值**；只有「身体不适/住院」进极简、`other` 不改界面（否则掩盖真实数据缺口）；`ui_mode` 与 `minimal_since` 成对维护（`isConsistent` 断言 + `ProfileEditScreen` 补透传）；今日页横幅 + 退出按钮，极简态隐藏运动快捷入口、保留告警与用药；`MinimalPromptStore` 同日只问一次；备份引擎无需改（表/列白名单均动态发现）；`MinimalModeTest` 8） | 红线三；`Profile.ui_mode` 字段空转（仅 Entity 定义处），`minimal_since` 字段不存在，无判定 / 询问 / 切换 |
| ~~B13~~ | ~~生活方式画像（吸烟 / 职业久坐时长 / 运动习惯 / 睡眠）→ 驱动 M4 处方个性化~~ —— **已于 v1.0.64 落地**（`profile.lifestyle` 此前只透传未采集 → 建档表单补采集 + 「我的」显示摘要；`domain/Lifestyle.kt`（扁平 JSON 手写编解码）+ `domain/LifestylePrescription.kt`（画像→个性化提示，**只出提示不改红黑榜过滤**——过滤依据是分期与活动度）；运动页新增「生活方式提示」块并复用 `DisclaimerNote`；登记吸烟后知识库置顶 `edu-003`，**顺带修掉 §D 的悬空挂点**；`LifestyleTest` 7 + `LifestylePrescriptionTest` 8） | M0；`Profile.lifestyle` 字段空转，未采集未展示 |

**C. 弱化实现（有字段或半套流程，未达规划口径）**

| # | 缺口 | 现状 vs 规划 |
|---|---|---|
| ~~C1~~ | ~~骶髂关节影像分期~~ —— **已于 v1.0.67 落地**（⚠️ **Room v16→v17**：`profile` 加 `sacroiliitis_grade`（改良纽约标准 mNY X 线 0–IV）；`Labels` 加 `SACROILIITIS_KEYS` + `sacroiliitisGrade()`（含罗马数字，key 不进 UI）；建档表单 chips（未评估 + 0/I/II/III/IV）+ 「我的」档案卡展示 + **复诊报告 PDF** 基本信息区；**刻意不进打印版应急卡与锁屏卡**（急救只要即刻信息，分期属慢性期资料）；`档案 JSON 导入导出`（`BackupRepository` 独立字段清单）双方向同步补 `sacroiliitis_grade`；`LabelsTest` 4） | 规划 M0 要求（诊断信息全量），`Profile` 无该字段 |
| ~~C2~~ | ~~补剂（营养）依从统计~~ —— **已于 v1.0.37 落地**（`ReportRepository.overview()` 新增 `supplement` 统计，与用药同口径；「概览」页补剂依从卡片，无记录时不摆 0% 假进度条） | — |
| ~~C3~~ | ~~化验「按单位分组 + 跨院数据仅供参考提示」~~ —— **已于 v1.0.37 落地**（`domain/LabUnits` 按「项目名 + 单位」分组；跨单位项目在该组标注单位并在列表顶部提示「仅供参考」；异常置顶与折叠交互不变） | — |
| ~~C4~~ | ~~复诊前准备清单（自动打包 + **空腹等抽血准备提示**）~~ —— **已于 v1.0.33 落地**（复诊记录页顶部准备卡：倒计时 + 需做检查项 + 携带清单；化验类自动标注「需空腹」并给禁食说明；影像类补「去金属饰品」） | M6 |
| ~~C5~~ | ~~停药 / 漏服原因枚举~~ —— **已于 v1.0.37 落地**（停药新增 感染发热 / 准备手术 / 经济原因（各带提示）；漏服新增 遗忘 / 外出 / 药物用完；旧枚举 key 全保留，历史日志兼容） | — |
| ~~C6~~ | ~~服药三态「固定 / PRN / 减量中」+「医生批准的减量方案不触发停药警示」~~ —— **已于 v1.0.37 落地**（`medications.dose_state` + `taper_note`；编辑表单三态选择器 + 减量备注；`domain/StopWarning` 在「减量中」且原因「自行停药」时豁免警示，含生物制剂强化警示） | — |
| ~~C7~~ | ~~漏服与延迟处理指引~~ —— **已于 v1.0.33 落地**（今日页漏服卡新增入口；口服 ≤2h 尽快补服 / >2h 跳过且**勿加倍**；注射 ≤48h 窗口内补注 / >48h 超窗**先联系医生**；免疫抑制类加提示；只给通用提示不做剂量决策） | 注射只有「顺延」，无补服规则与超窗分级 |
| C8 🔶 | **「久坐每 30–45 分钟起身提醒」✅ 已于 v1.0.68 落地**（`domain/SedentaryReminder.kt` 纯函数 `nextFire`（窗口内固定网格，走完则次日窗口起点）/ `isWithinWindow`（陈旧闹钟兜底）/ `isValidWindow`；`reminder/SedentaryReminderScheduler.kt` **链式单发**（任何时刻只有一个待触发闹钟，不用 `setRepeating`）+ `SedentaryReminderReceiver`（不在窗口内静默丢弃但**仍续链**，否则链断永久失效）；`ReminderConfigRepository` 加开关 / 间隔 / 起止小时；提醒设置面板加开关（默认关）+ 间隔 chips，活动时段固定 9:00–18:00 只读；通道 `sedentary_reminders`（DEFAULT，DND 时走静默）；通知固定 ID 单条覆盖；`AshkbApplication` + `BootReceiver` 各重排一次）。**「晨僵时长驱动起床热身序列」✅ 已于 v1.0.69 落地**（新增 `domain/MorningWarmup.kt` 纯函数 `build(minutes, plan)`——**动作不新造**，直接取当日处方里的 L1 轻柔项（R27 已判定可做且排除 pause/拦截），封顶 `MAX_STEPS=4`；阈值分档 `MIN_REPORTABLE=15`（低于不提示）/ `PROLONGED=30`（加「明显延长」+ 常提示炎症活动 + 连续多日需复诊告知医生），与既有 `ExerciseEngine.interpretFeedback` 同一口径；处方无 L1 项时 steps 为空、UI 给非处方通用提示；`ExerciseViewModel` 加 `warmup` 流（`combine(uiState, yesterdaySymptom)`），`ExerciseScreen` 的 `PrescriptionHero` 加 `warmup` 参数渲染；`MorningWarmupTest` 9）。**「姿势 / 睡姿建议」✅ 已于 v1.0.70 落地**（新增知识库条目 `edu-005`（`payload.key = posture_sleep_advice`，来源 NASS axSpA 体位教育），**建议以带出处的知识条目落地，非硬编码文案**；`domain/PostureAdvice.kt` 只做展示位拆分（日常姿势 2 条 / 睡姿卧具 3 条，`shouldShow(plan)` 处方非空即展示），运动页处方 hero 新增「姿势 / 睡姿建议」块 + 来源行 + `DisclaimerNote`；`Lifestyle.pinnedKbIds()` 睡眠时长登记后置顶 `edu-005`；`PostureAdviceTest` 9）。**C8 全部子项完成** | M4；晨僵仅作处方页「判读依据」展示，`ExerciseEngine.todayPlan()` 不吃晨僵 |
| ~~C9~~ | ~~体重「目标区间提示」~~ —— **已于 v1.0.33 落地**（档案可设上下限，容忍填反自动交换；体重卡显示在区间内 / 低于 / 高于 x kg；未设时不打扰） | M2 |
| ~~C10~~ | ~~生物制剂续方提醒 + 结核 / 乙肝 / 丙肝筛查初始节点~~ —— **已于 v1.0.37 落地**（`domain/ScreeningSeeds` + `HealthRepository.seedBiologicScreeningItems()` 一键种入 4 项，按名称幂等去重；复诊项目卡片内入口） | — |
| ~~C11~~ | ~~电池白名单 / 自启动引导（仅一行文字提示，无跳转按钮）、提醒自检缺「写入测试提醒验证」~~ —— **已于 v1.0.62 落地**（电池白名单用 `PowerManager.isIgnoringBatteryOptimizations` 显示真实状态 + 一键加入；自启动按厂商组件名尽力跳转 + 兜底应用详情（**刻意不做状态行**：系统无公开查询接口）；「发送测试提醒」真排 10 秒后精确闹钟 → `TestReminderReceiver` 发通知，验整条链路；另修「从系统设置返回后自检状态不刷新」） | M10 |
| ~~C12~~ | ~~全局免责声明（显著位置 / 首启声明）+ 自动提示统一前缀「仅供参考，以主治医师医嘱为准」~~ —— **已于 v1.0.63 落地**（首启全屏门禁 `FirstLaunchDisclaimer`，确认前不创建任何 VM / 不开库，状态存 `app_prefs`；统一前缀唯一来源 `domain/Disclaimer.kt` + 共享组件 `DisclaimerNote`，应用于漏服 / 复诊准备 / 跨院化验三处自动提示并收敛各自重复措辞；条目级与 PDF 页脚保持自足文本；`DisclaimerTest` 6 条锁措辞） | 现仅条目级与 PDF 页脚声明 |

**D. 质量项：知识库种子挂点悬空**

知识库种子的 `app_link` 已引用**代码中不存在的表 / 字段**，用户点进去会看到指不到实处的提示。已知：~~`exercise_plans.week_structure`、`exercise_plans(stage_mode=flare)`（→ B7）~~ **已于 v1.0.39 补齐**（`exercise_plans` 表落地，挂点不再悬空）、~~「与 `body_measures` 胸廓扩张度趋势联动」（→ B1）~~ **已了结（2026-09-21：B1 取消，种子 exc-004 文案已改写为「结合复诊时医生测量的数值」）**、「profile 吸烟状态登记后知识库置顶」（→ B13，仍未做）。剩余一项（B13）修法二选一：补齐实现，或改写种子文案。

> ✅ **已全部了结（v1.0.64）**，本节可作历史留存：唯一剩余的 `edu-003`（吸烟条目）挂点选了「补齐实现」——v1.0.64 起 `profile.lifestyle` 真正采集，登记吸烟后知识库「全部」视图将 `edu-003` 置顶（见 §8 B13）。**知识库种子挂点已无悬空项。**

**E. 歧义项，需需求方拍板**

- **R16 批量数据导入**（忌口 / 运动计划 / 知识库内容的 CSV / JSON 模板 + 导入预检）：v2.1 定为 P1，但 **v3 需求表已不继承**（R14/R15/R16 三条均未继承，其中 R14 并入 K、R15 并入 R20）。按 v3 可判「不在本期范围」。现状只有 AI 模板粘贴解析（限化验 / 影像）与档案明文 JSON 导入，**无 CSV 文件选择、无模板、无导入预检**。

**F. 明确「取消 / 不做 / 后移」——不是缺口，勿重复盘**

M8 家属协作全部（家属端 / 共享子集 / 设备令牌 / 命令协议 / 冲突裁决 / 批量裁决界面）、R13 + M11 打卡连击与阶段目标激励、R23 豁免日（`exempt_days` 删表）、M2 三餐打卡（`diet_logs` 删除）、WebDAV 对外升级通知家属（升级链只在本机）、共享子集格式 / 命令记录协议 / 令牌吊销 / 冲突恢复走查 CR-1·3·6、iOS、紧急警报短信通道；知识库完整编辑器与内容导入导出「后移阶段 2 之后」。另有 v2 §1.7 系统边界六条（不做诊断决策 / 不做医生侧 / 不上架商店 / 无账号与自建服务器 / 不做医疗器械级监测 / 无社交商业），以及「安全内容主治医师线下过目」属流程项非代码项。

- **已关闭的旧待办**：
  - **规划缺口第五批（v1.0.39）**：B3 推荐食谱库（10 条带出处种子 + 标签筛选 / 收藏 / 自建） / B7 4–12 周周期康复计划模板（完成度反算）——两项落地，Room v14→v15
  - **规划缺口第四批（v1.0.38）**：B11 营养素每日上限 + 与用药错开提醒 + 服药/补剂合并时间表 / B4 报表「周月报」页签——两项落地，Room v13→v14
  - **规划缺口第三批（v1.0.37）**：C5 停药/漏服原因枚举对齐 / C6 服药三态「减量中」+ 医嘱减量豁免警示 / C2 补剂依从统计 / C3 化验按单位分组 + 跨院提示 / C10 生物制剂筛查与续方节点种子——五项全部落地，Room v12→v13
  - **规划缺口第二批（v1.0.33）**：C4 复诊前准备清单 / B2 知识库个人备注层 / C7 漏服处理指引 / C9 体重目标区间 / B10 复诊附件归档（拍照 / 相册 / PDF）——五项全部落地，Room v9→v10
  - UI 改版方案未完成 PR 项——已核实唯一可确认编号的 PR4（文案资源化）在 v1.0.10 完成，方案原文已不在工作区
  - 知识库搜索 FTS4/索引（审查报告 P1）——v1.0.21 以「单列检索文本 + 防抖 + 上限」结项；**FTS4 方案经实测否决**（对中文子串零命中，详见 `domain/KbSearch.kt` 注释与 CHANGELOG v1.0.21）
  - `as MutableStateFlow` 强转（审查报告 P2）——v1.0.22 清除全部 44 处，VM 状态流统一「私有 `_xxx` 可变 + 公开只读」
  - 化验 Tab 分组折叠——v1.0.23 落地（有异常或最近一次的日期默认展开，其余收起；`rememberSaveable` + 稳定 item key 保持状态）
  - VM / 通知 / PDF 硬编码文案（v1.0.10 §遗留）——v1.0.24 三层共 124 条下沉 strings.xml（`notif_` / `vm_` / `pdf_` 前缀）。**边界**：`data/` 与 `domain/` 层的异常消息与领域标签保持硬编码——domain 层按设计纯 JVM 无 Context，且那些是数据/提示词而非界面文案
  - **规划缺口 A1：紧急卡缺「当前用药」**——v1.0.26 落地。放弃闲置的 `Profile.emergency_med_summary`（无读写），改为纯函数 `domain/EmergencyMeds.kt` 从在用药单自动汇总：未归档 + 结束日期口径筛选 → 免疫抑制类（BIOLOGIC / JAK / CSDMARD / GLUCOCORTICOID）置顶标注 → 12 条封顶；紧急卡页面（`EmergencyScreen`，刻意放在 `profile?.let` 之外，未建档也显示）与打印版 PDF（`EmergencyCard.meds` 字段）两处同源。单测 +16 条
   - **规划缺口 A2：备份恢复码**——v1.0.27 落地。备份文件格式升级 **v2 信封（magic `ASHKBAK2`）**：随机 256-bit DEK 加密内容，DEK 再被口令/恢复码分别包装进两个密钥槽（类 LUKS keyslot），任一可解；槽 id 进 AAD 防槽交换。恢复码 160-bit Base32（32 字符 8 组，`domain/RecoveryCode.kt` 纯函数），Keystore 加密落盘（`vault_config` prefs）。**解密归一化兜底**：先按原样逐槽尝试，输入形似恢复码再按归一化形态重试（任意抄写形态可解；口令第一轮命中不受影响）。`ASHKBAK1` 旧格式永久兼容读取（`encryptLegacy` 仅测试用）；未设恢复码也用 v2 单槽（格式不分裂）。pre-restore 快照同带恢复码槽。**A3 决策**：不引入 Argon2id（Android 无内置 / BC 冲突史 / native +1MB），v2 槽自带 KDF 参数可将来无破坏升级