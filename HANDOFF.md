# ASHKB 开发交接文档

> 本文档面向接手本仓库开发的 AI 会话（TraeWork Code 模式 / TraeCode）或人类工程师。
> 记录截至 **v1.0.49**（versionCode 54，2026-09-23）的全部工程知识。
> 应用本身介绍见 `README.md`，版本历史见 `CHANGELOG.md`。

## 1. 项目一句话

ASHKB（Ankylosing Spondylitis Health Knowledge Base）：面向强直性脊柱炎患者的**离线优先**个人健康管理 Android 应用。纯 Kotlin + Jetpack Compose（Material 3），Room 持久化，零第三方 UI/网络依赖（WebDAV 客户端用 HttpURLConnection 手写）。

## 2. 环境与构建

**工作区根目录**（本文件所在仓库的上一级）：

```
<工作区>/
├── build-env/          # 全套工具链（勿动）
│   ├── jdk-21.0.12.1+1/
│   ├── gradle-8.7/
│   └── android-sdk/    # build-tools 含 apksigner
└── patient-health-app/ # 本仓库（git repo）
```

**构建命令**（PowerShell，工作区根目录执行）：

```powershell
$env:JAVA_HOME = "$PWD\build-env\jdk-21.0.12.1+1"; & "build-env\gradle-8.7\bin\gradle.bat" -p patient-health-app assembleDebug assembleRelease testDebugUnitTest
```

- 全量构建约 2~3 分钟；**328 条单测**必须全过才算交付
- **⚠️ 构建前先看可用内存（v1.0.48 踩到）**：本机 Gradle 守护进程在**物理内存不足**时会直接
  死于原生分配失败（`Native memory allocation (malloc) failed ... Chunk::new` → 
  `Gradle build daemon disappeared unexpectedly`），**且此时 assemble 任务可能已经拷出了旧 APK**——
  出包后**必须用 `aapt dump badging` 核对 versionCode/versionName**，不能只看「BUILD SUCCESSFUL」。
  另：`gradle --stop` **不会**回收 Kotlin 编译守护进程（独立进程，可占 2GB+），
  内存紧张时按命令行含 `org.jetbrains.kotlin` 结束它即可（构建工具进程，无害）。
- 单测结果统计：`app\build\test-results\testDebugUnitTest\*.xml`
- Windows 侧无 git；**git 在 WSL 里**（仓库路径 `/mnt/c/<工作区>/patient-health-app`）

**已知构建坑**：

1. **Kotlin 编译守护进程启动失败**（exit code 1，错误码 268435466）：通常是内存压力/残留进程。处理：`gradle.bat --stop` 清理后直接重试即可，非代码问题。
2. 长时间会话多次构建后内存紧张，同上清理。

## 3. 正式签名（关键！）

- 密钥库：`patient-health-app/ashkb-release.jks`（PKCS12，RSA-2048，alias `ashkb`，有效期 10000 天，`.gitignore` 已排除）
- 密码：`local.properties` 的 `ashkb.store.password` / `ashkb.key.password`（两者相同）。`app/build.gradle.kts` 从此读取，**缺失时自动回退 debug 签名**（构建不会报错，交付前必须验签！）
- 证书 SHA-256：`38CA80A61760208E1F0C9D81C2CAF5C7C90DA0E3BD759150CCDF641EC9012D7D`
- 验签命令：`build-env\android-sdk\build-tools\<ver>\apksigner.bat verify --print-certs <apk>`
- ⚠️ **keystore + 密码丢失 = 所有用户永远无法覆盖升级**。务必多处离线备份。

## 4. 交付流程（每版本固定套路）

1. 改 `app/build.gradle.kts` 的 `versionCode` + `versionName`
2. 改 `CHANGELOG.md`（新增版本段落，格式见文件内现有条目）
3. **改 `README.md` 的版本号与测试条数**（N2 复发过一次：README 曾同时写着 114 / 205 两个互相矛盾的测试数，且 `./gradlew` 在本仓库根本不存在。这两处最容易被漏）
4. 构建 + 单测（第 2 节命令）
5. 交付 APK：复制 `app\build\outputs\apk\{debug,release}\app-{debug,release}.apk` 为仓库根的 `ashkb-X.Y.Z-{debug,release}.apk`
6. 验签（第 3 节）+ 记录 SHA-256
7. GitHub 同步（第 5 节）

## 5. GitHub 同步（github.com 直连超时的绕行方案）

**背景**：本机连不上 github.com:22/443 的 git 协议（WSL/Windows 都超时），但 `api.github.com` 可通。

**工具**：Windows 侧 `C:\Program Files\GitHub CLI\gh.exe` 已登录 weante（keyring token）。

**推送流程**（本地 WSL commit → gh API 逐文件上远端）：

1. WSL 提交：`git add <改动文件> && git commit -m "..."`，记录 `git log -1 --format="%H %T"`（HEAD 与 HEAD~1 各取一次：前者给 `$expected`，后者给 `$parent`）
2. PowerShell 脚本经 gh API 操作（模板见工作区 `push-tmp/push-v1020.ps1`，每次更新 5 个变量：`$parent` / `$baseTree` / `$expected` / `$date` / `$files`，消息正文写 `msg-*.txt`）：
   - **请求体一律写临时文件 + `gh api --input <file>`**（禁用 stdin 管道——PowerShell 5.1 下长 JSON 走 stdin 会损坏导致全部 400）；`--input` 方式在 `powershell -File` 子进程里执行正常
   - 逐文件 `POST /git/blobs`（content=base64）→ `POST /git/trees`（base_tree=父 commit 的 tree）→ `POST /git/commits` → `PATCH /git/refs/heads/main`
   - **tree SHA 必须与本地 commit 的 tree 一致**，否则说明文件清单有漏
3. **SHA 逐字节对齐**（v1.0.19/v1.0.20 验证）：`POST /git/commits` 传 `message`（正文尾**加一个 `\n`**）、`author` 与 `committer` 的 name/email，以及 date（取本地 `git log -1 --format=%aI`，形如 `+08:00` 偏移）——完全一致时 API 生成的 commit SHA 与本地 `git commit` **逐字节相同**，脚本内直接断言 `$commitSha -eq $expected`，不符即中止（早期用 `git hash-object` 回写本地的绕行方法已不需要）
4. Release：`gh release create vX.Y.Z --repo weante/ashkb --target <远端sha> --title "..." --notes-file <md>`；两个 APK 用 `curl.exe` 上传：`gh api repos/weante/ashkb/releases/tags/vX.Y.Z --jq .id` 取 release id → `curl.exe -sS -X POST -H "Authorization: Bearer $(gh auth token)" -H "Content-Type: application/octet-stream" --data-binary "@<apk路径>" "https://uploads.github.com/repos/weante/ashkb/releases/<id>/assets?name=ashkb-X.Y.Z-{debug,release}.apk"`
5. 资产核验：`gh api repos/weante/ashkb/releases/<id>/assets --jq '.[].digest'`（输出 `sha256:...`）与本地 `Get-FileHash -Algorithm SHA256` 逐字节比对

**网络偶发 500 / 超时重试即可**（`api.github.com` 与 `uploads.github.com` 都会瞬断，重试即过）；`uploads.github.com` 上 `Invoke-RestMethod` 会被断连，必须用 `curl.exe`；release create 报 422 "tag already exists" 说明其实已成功。

## 6. 代码结构与约定

```
app/src/main/java/com/ashkb/app/
├── data/
│   ├── backup/     # BackupEngine（全量加密备份）、WebDavClient、VaultCipher(Keystore AES/GCM)
│   ├── db/         # AppDatabase（Room，version=15）、DAO
│   ├── entity/     # Room 实体
│   └── repo/       # HealthRepository（多步写用 withTransaction）
├── domain/         # ClinicalThresholds（临床阈值集中）、Labels（枚举中文标签）、ExerciseEngine 等
├── reminder/       # 提醒
└── ui/
    ├── AppShell.kt     # Navigation-Compose 宿主，所有路由接线
    ├── navigation/     # 路由定义
    ├── theme/          # 设计 token：Spacing/Size/Clinical/StatusTone + values-night 深色模式
    ├── components/     # 统一组件库：SectionCard、StatusChip、AlertBanner、TrendChart、NavRow、DividerList、Forms
    ├── today/ emergency/ exercise/ wellness/ knowledge/ symptom/ checkup/ report/ me/  # 各页面
    └── GlobalMessages  # Snackbar 消息总线（不要逐条弹 AlertDialog）
```

**编码约定**：

- 结果判断用项目惯例 `runCatching { ... }.getOrNull() != null`，不用 `isSuccess()`（Kotlin 2.0.20 环境下曾解析失败）
- 设计规范：可点击元素 ≥48×48dp（`Size.touchMin`）、间距用 `Spacing.*`、字号用字阶、**不写魔法 dp 值**、Icon 必须带 contentDescription、状态三重编码（文字+图标+颜色）
- 新表单组件优先 ModalBottomSheet 而非 AlertDialog；chip 组用 FlowRow 防窄屏截断
- 临床阈值（依从 90/70、BASDAI ≥ 4 等）一律进 `ClinicalThresholds`，禁止散落硬编码
- 枚举在 UI 显示一律走 `Labels`，界面不出现英文枚举 key
- 大文件要拆：单文件超 ~500 行的 Screen 按职责拆分（参考 CheckupScreen 4 文件拆法，private→internal）

## 7. 近期教训（2026-09 迭代实录）

- **WebDAV HTTPS 反射坑**：`HttpsURLConnectionImpl` 的 `method` 字段在 `delegate` 指向的 `HttpURLConnectionImpl` 上；对包装类直接反射会找到无效影子字段且静默失败（MKCOL 实际按 GET 发出报 404）。修复见 `WebDavClient.forceMethod()`——先解引用 delegate 再沿类层级找字段。
- **回调透传**：页面嵌套 composable（如 ReportScreen 内的 ExportPage）新增参数时要逐层透传，编译错误 `Unresolved reference` 先查函数签名链。
- **化验列表翻页**：`CheckupViewModel` 用 `_labLimit` MutableStateFlow + `flatMapLatest` 实现窗口增长（初始 100 行，+100 递增），列表底部按钮 `canLoadMore = list.size >= limit` 判断隐藏。
- **FTS4 不能用于中文子串搜索（v1.0.21 实测，勿再尝试）**：SQLite 的 FTS3/FTS4 分词器（`simple` / `unicode61`）按「字母数字连续段」切词，中文无空格分隔 → 一整句变成一个 token。实测对「强直性脊柱炎患者用药注意事项」执行 `MATCH '强直'` 命中 **0** 条（`LIKE '%强直%'` 命中 1 条），`MATCH '强直*'` 仅在查询恰为句首时命中。即**为了「优化搜索」把 LIKE 换成 FTS4 会让中文搜索直接失效**。CJK 若确需全文索引，只有 FTS5 的 `trigram` 分词器（Room 无 `@Fts5` 注解，且 minSdk 26 上 FTS5 不可靠）或自建字符级 n-gram 索引——本项目知识库为固定 47 条种子，LIKE 足够。
- **新增列与备份恢复的兼容（v1.0.21 / v1.0.33）**：`BackupEngine.insertTable` 按「备份行自身的列集」按名 INSERT，故旧备份缺新列**不会**报错，但要求 Room 侧新列**可空**（NOT NULL 无默认值会插入失败）；而 `verifyAgainst` 按备份自身列集比对（R9），加列不会让旧备份 SHA 误判。**真正的坑**：恢复后新列为 NULL，会让依赖该列的查询整库失配（知识库搜索会搜不到任何条目）——必须在 `BackupEngine.restore` 双校验通过后、提交前按当前口径回填，位置与 R1 的 profile 归一化相同。**反之，若新列的 NULL 本身就是合法业务态（v10 的 `user_note` / `weight_target_*` = 未设置），则不需要回填**——判断标准是「该列是否参与 WHERE 过滤」：`search_text` 参与 LIKE 过滤故必须回填，`user_note` 只在读取时展示故无需回填。
- **新增表同样无需额外处理（v1.0.33）**：`tableNames` 走 `sqlite_master` 动态发现，新表自动进入备份与恢复范围；`unknownTables` 白名单校验也因此自动放行。旧备份不含新表 → 恢复后该表为空，属预期。
- **「未在本表单呈现的字段」必须显式透传（v1.0.33 缺陷）**：`ProfileEditScreen` 用 `Profile(...)` 具名参数构造，未列出的字段会取**实体默认值**——`lifestyle` / `uiMode` / `emergency_med_summary` / `emergency_note` 曾因此被静默重置（`uiMode` 会被打回 `normal`）。凡是「编辑既有实体」的表单，新增字段时必须回头检查所有构造点，把未呈现字段从 `initial` 透传。同类隐患的通用检法：grep 该实体的构造调用点，逐一核对参数完整性。
- **整数除法截断会吃掉边界（v1.0.33 缺陷）**：`val hours = minutesLate / 60; if (hours <= 48)` 让 48h+1min 截断成 48 而误判「仍在窗口内」。凡「阈值比较」都应保留原始精度单位（直接用分钟比较），不要先降精度再比。
- **外键列存在 ≠ 有关系（v1.0.34 缺陷）**：`lab_results.checkup_id` 早在 v4 建表就有，但 `importLabReport` 写死 `checkupId = null`——字段长期是死列，连带「复诊记录 → 查看化验」按该列查询永远为空（一直没人从那个入口点过，所以从未暴露）。`imaging_records` 更彻底，连列都没有。**教训**：新增「外键列」时必须同时落地**写入路径**（导入 / 表单 / 关联入口），否则就是装饰；核对时 grep 该列的写入点，而不是只看实体定义。
- **⚠️ 长期保存的密文不能用「每次随机」的密钥（v1.0.35 关键设计）**：`VaultCipher.encrypt`（v2）每次备份都新生成随机 DEK——这对「一次性备份文件」没问题，但**附件要长期存在云端**，用它加密会导致：改口令 / 重生成恢复码后旧附件全废、同一附件被不同密钥加密无法管理。因此 v1.0.35 引入**稳定 vault key**（Keystore 保护、永不更换），备份格式升 **v3**（`ASHKBAK3`，密钥槽包装的就是 vault key），**payload 与所有附件共用同一密钥域** → 改口令只需重包装槽、密文不动；换机从槽解出即可读附件。**通用判据**：密文的生命周期 > 单次会话（跨口令变更 / 跨设备）时，必须有稳定密钥 + 密钥包装层。
- **恢复时的密钥采纳必须「仅当本机没有」（v1.0.35）**：`VaultKeyStore.adoptIfAbsent` 刻意不覆盖已有密钥——一次旧备份恢复若把本机 vault key 冲掉，当前设备已上传的附件立刻全部解不开。
- **懒下载让「恢复」从 N 次操作变成 0 次（v1.0.35）**：附件按需从云端取回（点开才拉），因此不需要「先批量下载附件再使用」。这是选择「逐个上传 + 元数据在 DB」而非「按批次打包」的决定性理由——打包方案在换机恢复时要做 N 次下载/解密/解包/校验，且缺一批就永久缺文件。
- **附件密文 AAD 绑定附件 id（v1.0.35）**：即便有人能写服务器，也无法把 A 的密文冒充成 B；`remote_path` 是外部输入（来自备份），拼 URL 前必须过 `AttachmentPath.isValidRemotePath`（挡路径穿越与绝对路径）。
- **批量改代码脚本务必先探测行尾（v1.0.25 踩坑）**：本仓库的 `.kt` 文件是 **LF** 行尾（个别文件末尾有 1 个 CRLF），若脚本按 `\r\n` 切分，整块 import 会被当成单行 → 替换静默不生效（表现为「调用点改了但 import 没加」，编译才暴露）。正确做法：先 `-replace "\r\n","\n"` 归一化处理，写完再按原风格还原；同时注意**一个文件可能有两段 import 块（中间空行分隔）**，按块处理会给两块各插一次导致 import 重复。
- **Compose 反模式速查（v1.0.25 已修，勿回退）**：①屏幕一律用 `collectAsStateWithLifecycle()`，禁用 `collectAsState()`（后者不感知生命周期，后台仍在收 Room 流）②`Canvas` 绘制 lambda 内不得新建 `Path`/`Brush`/`PathEffect` 或调 `textMeasurer.measure`，须 `remember` 到绘制外 ③传给 `TrendChart` 的 `points` 必须 `remember(源数据)`——其入场动画以 `points` 为 key，新 List 实例会让动画反复重播 ④派生计算（`groupBy`/`partition`/`map`）注意 `remember`，且**不能写在 `LazyListScope` 内**（那不是 @Composable 作用域，需上提到 `LazyColumn` 之前）⑤ViewModel 中禁止在构造时求值 `LocalDate.now()`（跨零点冻结），须用 `MutableStateFlow` + ticker 驱动。
- **LazyColumn key 不要用字符串拼接（v1.0.28）**：`key = { a + b }` 在组合值可能重复时会抛 `IllegalArgumentException`（key 冲突崩溃），且每帧新建 String。直接用 `key = { a to b }`（`Pair` 有稳定 equals/hashCode，Compose 的 key 类型是 `Any`）。排查时注意：`MedicationRepository.buildTodayItems` 对每个 PRN 药只产 1 条（`slotKey=null`），真正会撞 key 的是同一药**重复 `take_times`**（旧 / 导入数据）。
- **日期 ticker 已覆盖 7 个 VM（v1.0.28 补齐）**：Today / Wellness / Exercise / Checkup / Symptom / **Emergency** / **Knowledge**。判定口径：任何在 Composable 里用 `remember(数据) { ... LocalDate.now() ... }` 的地方都是隐藏的日期冻结（`remember` key 不含日期 → 跨零点不重算），必须改由 `vm.date` 驱动。`ReportViewModel` 是**例外**（`ReportRepository.overview/trends` 在 `refresh()` 内取 `now`，非组合期冻结，无需 ticker）。
- **Compose Strong Skipping 在 Kotlin 2.0.20 已默认开启（v1.0.28 查证）**：审查报告建议的 `composeCompiler { featureFlags = setOf(StrongSkipping) }` 与 `rememberUpdatedState` 手动 memoize lambda **都不需要**（编译器已自动 remember 所有 lambda）。仅当将来降级到 2.0.20 以下才需显式开启。
- **VM 内不要缓存 StateFlow（v1.0.28 教训）**：`CheckupViewModel` 曾用 `mutableMapOf<String, StateFlow<...>>` 按 id 缓存——无界增长、非线程安全、`onCleared` 不清理。正确做法是 VM 返回冷 `Flow`，调用点 `remember(id) { vm.flowFor(id) }` 记住订阅：identity 稳定、切 id 自动重订阅、随组合销毁而释放。
- **⚠️ `init` 块读「声明在其后」的字段 = 构造期 NPE（v1.0.29 血泪）**：Kotlin 属性初始化器与 `init` 块按**声明顺序**执行；而 `viewModelScope` 用 `Dispatchers.Main.immediate`——已在主线程时 `launch` 体**同步执行到第一个挂起点**，`StateFlow.collect` 的**首个发射不挂起**。于是 `init { viewModelScope.launch { _date.collect { _selectedDate.value } } }` + `private val _selectedDate = ...`（声明在后）会直接 NPE，**且 149 条单测完全拦不住**（测试只覆盖 domain 纯函数，不构造 ViewModel）。v1.0.28 因此启动即闪退，只能发 v1.0.29 热修（versionCode 递增后**无法降级**，Android 不允许低版本覆盖）。
  - **两条防御**：① 凡 `init` 里要用的字段，一律声明在 `init` **之前**；② 更稳的做法是把「需要延迟到构造完成后才执行」的逻辑放进 `delay(...)` **之后**（或 `LaunchedEffect`），彻底不依赖声明顺序。
  - **新增 VM 后必查**：`init` 块内引用的每个字段，声明位置是否在其前。
- **`HorizontalPager` 页面回收会销毁普通 `remember`（v1.0.30 教训）**：Pager 默认 `beyondBoundsPageCount = 0`——切走即回收视口外页面，切回重新组合，`remember { Animatable(0f) }` 等普通 remember 全部丢失重建。`TrendChart` 入场动画因此在每次切回趋势页时重播（v1.0.25 的「remember points」只防同屏重组，防不了页面级回收）。**修法**：「已播过」标志用 `rememberSaveable`（Pager 按 page key 自动保存恢复），动画 key 用数据**内容 hash**（`points.hashCode()`）而非实例——Room 重发内容相同的新 List 实例也不误触发。凡是「动画 / 滚动位置 / 交互态」要跨页签存活的，一律 `rememberSaveable`，普通 `remember` 视为仅同屏存活。
- **「以本地记录为准去删远端」必须严格限定在可生成形状（v1.0.36）**：远端目录 `ashkb/attachments/` 是专用目录，但用户完全可能在网页端往里放别的东西——若按「远端有、本地无」就删，会误删无关文件。因此清理集合必须过 `AttachmentPath.isManagedRemotePath`（合法日期目录 + `.enc`）。**通用判据**：凡是「拿本地记录当权威去删远端」的同步动作，删除集合都要能被「本应用可能生成的文件形状」完全枚举，不能只看差集。同理，远端路径是外部输入，拼 URL 前一律过形状校验（挡路径穿越 / 绝对路径）。
- **组合期创建 VM 会顺带「打开数据库」——与启动协程并发（防御性建议）** ⚠️ **v1.0.42 更正：本条曾被用来解释 v1.0.39/40 的冷启动闪退，实测归因错误**（真因见下方「正则方言差异」条）。本条机制本身成立，仍作为防御性建议保留，但**不要再据此判断 v1.0.39/40 的闪退原因**：`AppShell` 里 `viewModel(factory = ...)` 在**组合期**执行；若该 VM 的属性初始化里就建 Room `Flow`（如 `repo.observeX().stateIn(...)`），会立即触发 **数据库首次打开**（`InvalidationTracker` 初始化需要打开的库）。于是**主线程**与 `AshkbApplication.onCreate` 的启动协程（IO）会**并发打开数据库**；若此时恰好要跑迁移，就会出现「冷启动闪退」且**没有任何界面提示**（协程内未捕获异常直接杀进程）。**两条防御**：① 新增的 L2 页面 VM 一律放进 `composable<...>` 内创建（v1.0.40 已把 Recipes / ExercisePlans 这样改），别在 AppShell 顶部一次性建；② `Application.onCreate` 的启动协程体整体包 `runCatching`（协程异常不会像主线程那样只崩当前操作，而是杀进程）。**排查提示**：这类闪退若拿不到 logcat，先怀疑「组合期触发数据库打开」与「协程内未捕获异常」两处，而不是迁移本身（v1.0.40 实测迁移 SQL 与 Room 期望逐字一致）。
- **崩溃必须留痕（v1.0.40）**：纯离线应用没有上报通道，闪退时开发者零线索（只能猜，极易误判）。新增 `CrashLogger`：`Thread.setDefaultUncaughtExceptionHandler` 把堆栈写入 `filesDir/last_crash.txt`（`adb pull` 可取）+ Logcat，并在**下次启动**以 Snackbar 摘要提示便于截图。**只写本地、不上报**，与「零网络权限」红线一致。
- **崩溃留档要留「首因」，且摘要必须抗混淆（v1.0.41 补强，直接决定了 v1.0.42 一轮定位成功）**：v1.0.40 的 `CrashLogger` 只记**未捕获**异常，且摘要「只取 `at com.ashkb` 帧」——两者在本次都失效：① 真实链条是「启动期首次触碰 → 初始化失败 → **被 `runCatching` 兜底吞掉** → 稍后二次触碰才抛 `NoClassDefFoundError`」，只记未捕获异常就**丢掉了根因**；② release 包类名被 R8 混淆（`K1.n`），按 `com.ashkb` 过滤**恒为空**，摘要退化成一行，反而更难定位。**两条修法**：`recordNonFatal()` 把被业务兜底吞掉的异常也写 `last_nonfatal.txt`；摘要改为「异常首行 + `Caused by` 首行 + 前 4 条栈帧」。**通用判据**：任何 `catch`/`runCatching` 的兜底分支，要么留档要么上抛，不能静默；崩溃摘要不得依赖未被混淆的包名。
- **⚠️ 正则存在 Java 与 Android（ICU）的方言差异（v1.0.42 血泪，勿再犯）**：模式里**孤立的 `}`（或 `{`）**——Java 的 `java.util.regex.Pattern` 当普通字符 ⇒ 编译通过；**Android 的 ICU 引擎视为语法错误** ⇒ `Pattern.compile` 抛 `PatternSyntaxException`。它写在 `object` 的属性初始化器里，于是异常变成 `ExceptionInInitializerError` → 类初始化失败 → 启动期被兜底吞掉 → 二次触碰抛 `NoClassDefFoundError`，全程表现为「闪退」而**完全看不到正则**。真机留档：`PatternSyntaxException: Syntax error in regexp pattern near index 83`（模式长 84，index 83 即末尾 `}`）。**防御**：① 解析「自己产出的固定格式」优先**手写解析**，不用正则；② 必须用正则时，花括号只出现在合法量词（`{4}` / `{2}`）中，**绝不出现孤立花括号**；③ **不要在 JVM 上用 `Pattern.compile` "验证"正则可用性**——那正是本次误判的来源（据此得出了错误的 R8 结论，白烧两轮）。复核方法：grep `Regex(` / `toRegex()` 逐个检查花括号成对性（v1.0.42 复核 17 处，仅 1 处有问题）。
- **真机崩 + JVM 单测全绿 ⇒ 先按「运行期环境差异」排查，不要急着归因 R8（v1.0.41→42 教训）**：正确顺序：① 用 `mapping.txt` 把混淆名反查成真实类名（`com.ashkb.app.domain.ExercisePlanTemplates -> K1.n`）；② **拿「首因异常」说话**——`NoClassDefFoundError` 通常只是**二次现象**，真正的 `Caused by` 才是根因（本次是 `PatternSyntaxException`）；③ 要判断「类是否真在 dex 里」用 `dexdump` 列类定义（`Class descriptor : 'L...;'`），**不要拿「字符串里出现过该类型」当证据**（那只是引用，`R8$$REMOVED$$CLASS$$nnn` 才是被删）。**JVM 单测结构上覆盖不到**：R8/ART 的类加载与初始化、ICU 正则方言、任何「构建期/运行期环境差异」——这类只能靠**真机 + 留档**闭环，故崩溃留档的投入优先级应高于再补单测。
- **release 元数据的编码坑，以及「别把批量修正做成泛化改写」（v1.0.41/42 自身失误）**：① `gh release create --title "中文"` 的中文经 PowerShell 5.1 的 **argv 编码（GBK）** 传出、被 `gh` 按 UTF-8 解析 ⇒ **标题乱码**；而 `--notes-file` 读的是**文件字节**，正文一直正常（所以只坏标题）。修法：标题走 `gh api -X PATCH … --input <utf8 json>`（字节通道），读 `gh` 输出前先 `[Console]::OutputEncoding = [Text.Encoding]::UTF8`。② 我随后"修标题"时按「各 release 正文首个 `## 标题`」**泛化改写**，把 v1.0.1–34 原本正常的标题一起覆盖了（v1.0.11 变成「修复」、v1.0.27 变成「机制：信封加密 + 双密钥槽」）——**教训：批量修正必须先显式列出「目标集合」并保留原值，只改目标项；不要用"看起来更统一"的规则覆盖非目标项**；改完立刻全量核对，发现越界就整体回滚。

- **⚠️「只重排未来」的清理逻辑会顺手清掉「今天稍后还要用的」（v1.0.43 缺陷）**：`ReminderScheduler.rescheduleAll` 每次冷启动都 `cancelAllFuture` 后只重排 `esc=0`，把「当天已过点但**尚未打卡**」的 `+30 / +60` 追问一并清掉——用户点「稍后提醒」后再冷启动就再也收不到追问（且**没有任何报错**，纯静默丢失）。修法：调用方传入「今日已打卡槽位」集合（`slotRef = medId|slotKey`，维度与 request code 一致），对「当天已过点 **且** 未打卡」的槽位重建**尚未到时**的升级链。**通用判据**：凡是「取消未来 + 重排」的函数，入参必须能区分**已过点未完成 / 已过点已完成 / 未到点**三种状态；把「已完成」当入参传进去，而不是让函数自己猜。相关常量（`MAX_ESCALATION` / `ESCALATION_STEP_MINUTES`）应集中在一处，`ReminderReceiver` 复用，避免「取消 2 级、重建 3 级」这类漂移。
- **从「在用」流里按 id 取单条记录做编辑 = 记录被归档后永久挂起（v1.0.43 缺陷）**：编辑页原用 `vm.meds.first { it.id == editId }`，而 `meds` 是「在用药品」流——药一旦停用 / 归档，`first` 永不返回，页面卡死无提示，用户重试保存即产生**重复药**。**修法**：编辑态一律走**按 id 直查**（`medicationById`，不限「在用」）；查不到时给明确提示 + 禁用保存。**连带**：这类「编辑既有实体」的表单，保存时必须把未呈现的字段从 `initial` 透传（如 `isArchived`），否则会把已归档记录**重新激活**（与 v1.0.33 的 `ProfileEditScreen` 教训同源）。
- **密钥「存在」不等于密钥「可用」（v1.0.43 加固）**：`VaultKeyStore` 原先只判断 `prefs.contains(key)`，于是「密钥存在但 Keystore 解不开」（系统密钥库异常 / 换机丢失 / 刷机）时会**静默生成新密钥**——后果是云端已上传的附件**永久不可解**（旧密钥没了，新密钥又对不上）。修法：区分 `ABSENT / PRESENT / UNREADABLE` 三态，仅 `ABSENT` 才生成；`UNREADABLE` 抛可操作错误（「请先恢复一份本机或云端的 v3 备份以取回密钥」）。**通用判据**：任何「读不到就重建」的密钥 / 凭据逻辑，都必须先分清「本来就没有」与「有但坏了」——后者重建等于**数据销毁**。
- **长按显明文的正确姿势（v1.0.43）**：口令框若只能掩码，输错即不可挽回（尤其 WebDAV 应用密码）。做法：`trailingIcon` 放「眼睛」图标，`Modifier.pointerInput { detectTapGestures(onPress = { revealed = true; tryAwaitRelease(); revealed = false }) }`——**按下显、松手掩**，`tryAwaitRelease()` 同时覆盖正常抬起与手势取消。`visualTransformation` 在 `VisualTransformation.None` 与 `PasswordVisualTransformation()` 间切换，并给图标 `contentDescription`（无障碍）。

- **⚠️ 给「安全相关」的参数配默认值 = 允许调用方忘记它（v1.0.44 缺陷）**：v1.0.43 给 `rescheduleAll` 加了 `doneSlotRefs`，但给了 `emptySet()` 默认值——于是 4 个调用点（Application / TodayViewModel / MeViewModel / **BootReceiver**）漏了开机广播那条，重启落在「已打卡槽位的 +30/+60 未到点」窗口内就会给已服药的槽位重建升级重查 → 用户收到「未服药」误提醒。**修法两层**：① 把「今日已打卡槽位」的构造抽成 `MedicationRepository.doneSlotRefs(date)` 一处实现（此前三处各写一遍，漏改是必然）；② **删掉默认值**。**通用判据**：凡「参数缺失导致静默错误行为（而非编译失败）」的，一律不给默认值——默认值把「忘记」变成了合法编译，而这类忘记没有任何提示。
- **⚠️「装上工具」≠「工具能解决这个问题」（v1.0.44 实测证伪）**：第三轮审查建议「用 Robolectric 触发含正则的 object 初始化即可暴露 v1.0.39–42 的崩溃」。装上 Robolectric 4.13（依赖可正常解析）并用**当年的真实缺陷模式**做探针后得到 `PROBE_RESULT=JVM_LIKE`——**Robolectric 里的 `java.util.regex.Pattern` 走 JVM 语义，含孤立 `}` 的模式正常编译**。即该方案对本类 bug 检出率为 **0**，而「JVM 语义全绿」正是当年真机崩溃的根因。**方法论**：可行性验证必须用「目标 bug 的真实样本」测**检出能力**，而不是测「依赖能否安装」——后者是必要不充分条件。
- **`java.util.regex` 方言差异的永久守卫（v1.0.44）**：既然任何「在 JVM 上跑一遍」的方案（含 Robolectric）对 ICU 正则差异天然免疫，就改用**静态检查**：`RegexLiteralGuardTest` 扫描主源码的正则字面量，只允许 ICU 也接受的量词 `{\d+}` / `{\d+,\d*}`，字符类 `[...]` 与 `\X` 转义对内的花括号视为字面量（不误报）。它随 `testDebugUnitTest` 一起跑，**CI 无需改动**即生效。新增正则时若被它拦下，说明该模式在真机会崩，不要绕过守卫。
- **`count>0` 短路会让老设备永远拿不到种子更新（v1.0.44 修复，第二轮 D1 的收尾）**：`if (dao.count() > 0) return` 的语义是「库里有一条就整体跳过」，于是**早于首次导入的设备此后所有种子增补都进不来**，且完全静默。改为 `KB_SEED_VERSION` 闸门 + 按 id 比对：缺失补入、内容修订则**只更新种子列**并把 `user_note` 拷回（个人备注层永不被覆盖）。修订判定同时看条目 `version` 与**字段级比对**——后者是安全网，因为「改了种子文案忘 bump version」这类遗漏靠纪律无法避免。**每次增补/修订种子内容后必须 `KB_SEED_VERSION + 1`**，否则老设备不会重新核对。
- **崩溃留档的隐私边界（v1.0.44）**：`CrashLogger` 落盘前做凭据脱敏（`scheme://user:pass@host` → `scheme://***@host`）。留档只写 app 私有目录、`allowBackup=false`、永不上报，但医疗类应用应主动划清边界——**凭据一律不入盘**；主机名保留（定位需要，且不敏感）。Logcat 那一路仍是原始堆栈（瞬时、自 Android 4.1 起非特权应用读不到）。
- **Kotlin 块注释是可嵌套的（v1.0.44 自己踩到）**：在 KDoc 里写「scheme 冒号 + 双斜杠 + 三个星号」时，`斜杠+星号` 序列会**意外开启嵌套注释**，导致编译报 `Unclosed comment` 且报错行号落在文件末尾、指向毫无关系的位置。在注释里写 URL / 路径 / 通配符时要留意 `/*` 与 `*/`。

- **⚠️ 时间序列的横轴必须由日期决定，不能由数组下标决定（v1.0.45 缺陷）**：`TrendChart` 原先用 `xAt(i) = i / (size-1)` 等距铺点，于是「隔 3 天测一次」与「隔 3 小时测一次」画出来一样长——**一条平坦的线可能只是这几周没记录，而不是病情平稳**。这种错**不报错、不崩溃**，只是让时间轴失去意义（典型的「沉默的谎言」）。修法：`dayOffsetsOf` 取各点相对首点的天数偏移定位；任一日无法解析则**回退等距**（不让一行脏数据把整张图搞没）。同理 X 轴刻度的「中点」应按**日期中点**取，而不是序号中点。
- **刻度数量应由「可用高度 ÷ 单标签高度」导出，而不是由数据范围导出（v1.0.45 缺陷）**：原 `niceScale` 固定 `rough = (hi - lo) / 4`，与画布高度、系统字号完全无关——矮画布或大字号下标签必然互压。现改为先按「画布净高 ÷ (实测字高 + 间隙)」算预算（2~5 段），再从 nice 阶梯挑满足预算的步长。**注意 `maxIntervals` 下限必须取 2 而非 1**：`interval = ceil(hi/s) − floor(lo/s)` 只保证 `≤ 预算 + 1`，区间跨 0 时（如 [-3, 7]）无论步长多大都是 2 段，预算是无解输入（已写进单测）。
- **图表里的「辅助信息」不要压在数据上（v1.0.45）**：阈值 chip 原先画在绘图区右侧、压着数据。现改进**左侧轴槽**（与 Y 刻度同列），且其宽度参与轴宽计算（否则会溢出画布）；某刻度与阈值同高时让位给阈值标签。「语义说明」交给卡片副标题承担，轴上只留数值。
- **画布高度用 `height()` 而不是 `heightIn(min = ...)`（v1.0.45）**：`heightIn(min)` 的实际高度取决于父级给的 max 约束，容易在不同容器里得到不同净高，图表这类「像素可读性敏感」的组件应使用确定值。**另**：`TrendChart` 曾在父级约束受限时表现为「绘图区被压扁」的观感，但 `chartHeight = 200.dp` 自查证以来未变——**未能从源码复现该观感**，故 v1.0.45 采取的是「让挤压在结构上不可能发生」（实测字高 + 确定高度），而非声称找到了根因。
- **⚠️ 改了绘制定位，就必须同步改命中测试（v1.0.46 修复，v1.0.45 自己引入）**：X 轴从「序号等距」换成「日期间隔」定位后，拖动命中的 `nearestIndex` 还停在序号比例映射——两套坐标系并存，日期间隔不均时气泡必然跳到离手指很远的地方。**通用判据**：同一几何被两处消费（绘制 + 命中 / 展示 + 导出）时，要么抽成同一个函数共用，要么在单测里把两者的口径钉在一起。v1.0.46 起命中与绘制共用 `dayOffsets`，并有 3 条回归单测。
- **重写比新写更容易丢防御性分支（v1.0.46 修复）**：v1.0.45 重写 `TrendChart` 时，把旧阈值 chip 的边缘 clamp（`coerceAtLeast(top)`）弄丢了——阈值恰为量程最大/最小值时标签画出画布。**通用判据**：重写组件前，先列旧实现的**防御性分支清单**（每个 coerce / clamp / fallback / 回退各是什么场景），逐条核对去向；重写后的 diff 只看「增删了什么」是看不出这些静默丢失的。
- **守卫类测试要防「覆盖面悄悄变窄」（v1.0.46 加固）**：`RegexLiteralGuardTest` 只认 `Regex("字面量")`，新代码改用 `.toRegex()` / `Pattern.compile` 即完全绕过、且毫无提示。现改为：主源码出现未覆盖形态就**大声失败**（提示先扩展提取器）。**通用判据**：静态守卫的断言除「目标内容合法」外，必须再加一条「未覆盖的输入形态存在即失败」——否则守卫会随代码风格漂移而失效，且失效方式是静默的。
- **异步加载的过期响应要用「状态未变」守卫（v1.0.46 修复）**：`setTrendDays` 快速连点 7→30 天时，若 7 天查询后返回，会把图换成旧窗口数据而 chip 仍显示 30 天。修法一行：`if (_trendDays.value == days) _trends.value = fresh`——响应回写前校验**发起时的请求仍是当前状态**。`setPeriodDays`（v1.0.38）有同型竞态，影响低未动，待顺带修。
- **⚠️ 同名遮蔽是静默的——新类型起名前先 grep 同名符号（含嵌套类）（v1.0.48 踩到）**：把依从率抽成顶层 `object Adherence` 时，`ReportRepository` 内部已有嵌套 `data class Adherence`（汇总 DTO），**import 不报冲突，只是让仓库里的调用点解析到那个嵌套类**，于是 `Adherence.ratePct` 报 `Unresolved reference 'ratePct'`——错误信息指向**成员名**而不指向遮蔽本身，极易误判成「新文件没参与编译」。改名为 `AdherenceCalc`（后缀风格与既有 `ScheduleCalc` 一致）并在 KDoc 写明原因。**判据**：新增顶层类型前，`grep` 一下该名字是否已作为嵌套类/其它包类型存在。
- **⚠️ 把 composable 抽成独立函数会丢掉作用域（v1.0.48 踩到）**：`Modifier.weight` / `align` / `matchParentSize` 是 `RowScope` / `ColumnScope` 的成员，一旦把行内容从 `DividerList { ... }` 内联体搬进独立 `@Composable`，就报 `Unresolved reference 'weight'`。两种修法：把函数声明成**作用域扩展**（`private fun RowScope.XxxRow(...)`，本次采用），或保留内联。这也解释了为什么补剂的历史列表当初是内联写的。
- **⚠️ 给只读列表加「编辑」入口时，必须同时定义字段间不变量（v1.0.49）**：用药记录此前只读，加编辑后立刻出现新问题——把「跳过」改成「已服」若不把 `reason` 清掉，库里就有「已服 + 原因=遗忘」这种自相矛盾的行；反过来把「已服」改成「跳过」却不填原因，就写出无原因的跳过（违反红线三）。两者都污染依从率口径，且**不报任何错**。判据：**编辑 = 把「写入侧的不变量」重新走一遍**——先列出该表有哪些字段间约束（本表：`done` 无原因、`partial`/`skipped` 必填原因、`inj_site` 仅注射类且 `done` 有意义、`taken_at` 仅 `done` 有值），抽成纯函数（`domain/MedLogEdit`）让写入侧与编辑侧共用，并给保存按钮加 `enabled` 门禁；**同时把「身份字段」设为只读**——`(date, med_id, slot_key)` 是唯一索引，而 `@Upsert` 是 REPLACE 语义，改这些字段撞键会**静默删掉被撞的那条记录**，等于吃掉用户数据。
- **枚举 `key` 与 `label` 分离时，`key` 属于数据格式（v1.0.49）**：`InjSite` 的 `key`（`thigh_l` 等）是 `medication_logs.inj_site` 的**存库值**，`label` 才是中文。此前这层映射只写在 `TodayScreen` 的私有函数里（还是用 string 资源做的），药单看不到 → 记录里直接显示英文键。收拢成枚举后，**必须有一条「存库键稳定性」单测**：键是历史数据的一部分，改了键老记录就再也映射不到中文（且不会报错，只是显示原始字符串）。另：映射不到时**回退显示原始值，不要兜底成某个默认项**——猜错等于替用户改了注射部位。
- **Compose：同级重叠的可组合项里，只有 z 序最高的那个算「命中」——「不消费」救不回来（v1.0.47 修复，v1.0.17 引入，潜伏 29 版）**：`ScoreInput` 的打分滑杆从 v1.0.17 起被叠了一层**同级** `matchParentSize()` 透明覆盖层（挂 `pointerInput`），用来补「点按落在当前值上不回调」。但 Compose 的命中测试在事件分发**之前**完成，同级只有 z 序最高者进入命中链 → **覆盖层独占命中，滑杆收不到任何指针事件，横向拖动彻底失效**；而覆盖层自己实现了点按，所以「点一下能改值」一直正常，缺陷表现为「只是拖不动」而潜伏 29 个版本。原注释写的「从不 consume，所以互不干扰」前提是错的：**消费与否发生在命中测试之后**，没被命中就谈不上消费。
  **正确做法（旁听手势挂祖先）**：把旁听逻辑挂在滑杆的**父 Box**（祖先）上——祖先与子节点同处一条命中链，子节点先处理事件，父节点只旁听、从不 consume。已在 v1.0.47 落地（`Forms.kt` 的 `ScoreInput`）。
  **判据**：任何「想在别人的手势上加点自己的逻辑」的需求，先问「我挂的是祖先还是同级」。同级覆盖层 = 必然吃掉下面的手势。
  **连带**：同一次修复还把拖动判定从「逐事件位移 > touchSlop」改成「相对按下点的**累计**位移 > touchSlop」——慢拖时单个事件的位移可能始终小于阈值，逐事件判会把拖动误判成点按。
  **M3 事实（反编译 material3 1.3.0 核实，勿再猜）**：`sliderTapModifier` **存在**（滑杆自带点按跳转）；但 `SliderState.valueFromOffset` / `rememberSliderState` **不是公开 API**，故无法复用 M3 的精确坐标换算。
- **静态守卫/UI 行为的边界（v1.0.47）**：手势命中、布局遮挡这类缺陷**纯 JVM 单测覆盖不到**（`isReturnDefaultValues = true` 下 `android.*` 是空实现），静态源码守卫也覆盖不到。这类只能靠 ①真机逐条手势路径手验 ②Robolectric + `compose-ui-test` 的 UI 测试（见 §8 待办）。**修任何手势相关代码后，必须逐条列出并手验每种手势路径**（拖动 / 点按轨道 / 点按当前值 / 两端标签），不要以「能改值」判定正常。
- **PS 5.1 执行无 BOM 的 .ps1 按系统 ANSI（GBK）解析——且有三个变体坑（v1.0.46 自身踩全套）**：给 v1.0.44/45 加「已被取代」横幅时连环踩中——
  **① 字符串变体**：横幅文本内联在脚本里，UTF-8 字节被按 GBK 组对成乱码（「已被取代」→「宸茶鍙栦唬」）写进 release body；
  **② 解析器变体（最隐蔽）**：注释行末的中文字（如「。」）其 UTF-8 尾字节与**换行符**组成非法 GBK 对、**把换行吃掉**——下一行 `try {` 被并进注释吞掉，报 `Unexpected token '}'` 且行号指向无关位置；
  **③ 静默吞 stdout 变体**：合并管道 `gh … 2>$null | ConvertFrom-Json` 在 EAP=Stop 下会把 gh 的 stdout **全部吞掉**（gh 退出码 0、无异常、`$out` 为 null），8 次重试全「失败」而同命令直连成功——修法：`$out = gh …` 先赋值、`$out = $out | ConvertFrom-Json` 拆成独立一步，stderr 重定向到变量。
  **铁律（升级）**：`.ps1` 文件**一律 ASCII-only**（注释也不许中文）；一切中文走外部 UTF-8 文件运行时读入；每次 gh/curl 调用后显式判 `$LASTEXITCODE`（网络失败不抛 PS 异常，try/catch 捕获不到），**写操作后必须回读比对**。另：`"$t: 文本"` 的 `$t:` 被解析为作用域变量语法 → ParserError，用 `"${t}: …"` 或 `-f`。

## 8. 当前状态与下一步

- **最新版**：v1.0.49（versionCode 54）：v1.0.21 知识库检索优化 + v1.0.22 清除 `as MutableStateFlow` 强转 + v1.0.23 化验 Tab 分组折叠 + v1.0.24 文案资源化 + v1.0.25 Compose 性能审查第一批 + v1.0.26 紧急卡「当前用药」+ v1.0.27 备份恢复码（v2 信封）+ v1.0.28 Compose 性能审查第二批 + v1.0.29 热修启动闪退 + v1.0.30 趋势图动画修复 + v1.0.31 药单在用药品编辑 + v1.0.32「我的」页关于卡 + v1.0.33 规划缺口第二批（Room v9→v10）+ v1.0.34 附件归档增强（Room v10→v11：化验/影像归属复诊记录）+ **v1.0.35 附件接入 WebDAV 备份（Room v11→v12 + 备份格式 v2→v3：稳定 vault key / 逐个加密上传 / 懒下载 / 同步删除 / 同步开关）** + v1.0.36 附件远端校验与补传（PROPFIND 列远端比对：远端缺失补传 / 远端多余清理；无结构变更） + **v1.0.37 用药精细化 + 化验与筛查（Room v12→v13：C5 原因枚举对齐 / C6 减量中态 + 医嘱减量豁免警示 / C2 补剂依从 / C3 化验按单位分组 + 跨院提示 / C10 生物制剂筛查与续方种子）** + **v1.0.38 营养素精细化 + 周月报（Room v13→v14：B11 补剂每日上限 + 与用药错开提醒 + 服药/补剂合并时间表；B4 报表第 4 页签「周月报」）** + **v1.0.39 推荐食谱库 + 周期康复计划（Room v14→v15：B3 `recipes` 表 + 10 条带出处种子；B7 `exercise_plans` 表 + 4/8/12 周模板 + 完成度反算）** ⚠️ **该版冷启动闪退、已废弃（勿发布）；功能随 v1.0.40 交付** + **v1.0.40 冷启动兜底 + 崩溃留档（两个新 VM 改为进页面才创建 + 启动期整体兜底 + CrashLogger；无结构变更）** ⚠️ **其「修复」不成立：只是把启动期异常吞掉，崩溃从「冷启动」挪到了「点添加模板」** + **v1.0.41 留档与兜底改进（`CrashLogger.recordNonFatal` + 崩溃摘要带 `Caused by`/栈帧 + 两条 proguard `-keep`）** ⚠️ **其「修复」不成立：归因 R8 是错的（`-keep` 与本根因无关）** + **v1.0.42 真正修复「康复计划 → 添加模板」闪退（真因：`ExercisePlanTemplates` 的正则末尾有一个未转义的 `}`——Java 的 `Pattern` 视为普通字符，Android 的 ICU 引擎抛 `PatternSyntaxException`，致类初始化失败 → 启动期被吞 → 二次触碰 `NoClassDefFoundError: K1.n`；修法：`parse` 移除正则改逐字符扫描 + `esc` 转义 `\n`/`\r`/`\t`；无结构变更）** —— **v1.0.38 / 39 / 40 / 41 均已被 v1.0.42 取代**（v1.0.39–41 为预发布且含缺陷，已在各 Release 页顶部标注；v1.0.38 功能全部含于 v1.0.42），**v1.0.42 已被 v1.0.43 取代**（功能全部含于 v1.0.43，无需再装本版） + **v1.0.43 代码审查后的 A / B 类修复 + 口令框「长按显明文」** + **v1.0.44 第三轮审查修复（N1 开机广播补 doneRefs + 删参数默认值 / N2 README 修正 / N3 种子版本化增量刷新 / S3 vault key 覆盖守卫 / S4 备份类型不符可定位报错 / S5 崩溃留档凭据脱敏；另落地正则花括号静态守卫）** + **v1.0.45 趋势页改造（方案 A）+ 7/30/90 天切换（X 轴按日期定位 / 刻度自适应 / 高度确定化 / 数据点与末点数值 / 阈值入轴槽 / 读数摘要行）** —— ⚠️ **v1.0.44 / v1.0.45 均为未验证预发布且各含缺陷（v1.0.45 的 4 处趋势图缺陷见 v1.0.46），两者均已被 v1.0.46 取代，请直接跳过** + **v1.0.46 审查修复（无结构变更：v1.0.45 的 4 处趋势图缺陷——拖动命中选错点 / 动画期间末点数值提前出现 / 阈值标签边缘溢出 / xAt 未排序防御；另 setTrendDays 过期响应竞态 + 正则守卫覆盖面告警 + 死资源清理）** ⚠️ **未真机验，已被 v1.0.47 取代** + **v1.0.47 修复打分滑杆「完全无法拖动」（无结构变更：删除 v1.0.17 引入的同级透明覆盖层——Compose 同级重叠只有 z 序最高者算命中，覆盖层独占了指针事件；点按旁听改挂祖先 Box；拖动判定改累计位移；旁听只补 M3 不响应的「点按落在当前值上」情形）** ⚠️ **未真机验，已被 v1.0.48 取代** + **v1.0.48 药单新增「用药记录」与「已停用药品」（无结构变更，仅新增只读查询：点药名看近 90 天逐条流水 + 该药依从率；底部折叠区显示停药日期/原因/备注并可点开记录；依从率公式由 ReportRepository 内联 4 遍收拢为 `domain/AdherenceCalc` 唯一实现）** + **v1.0.49 用药记录可手动修正 + 注射部位显示中文（无结构变更：记录行加铅笔可改状态/原因/注射部位/备注，归属日与计划时刻只读；注射部位键→中文映射由 TodayScreen 私有函数收拢为 `InjSite` 枚举并删掉 6 条死字符串；编辑不变量抽成 `domain/MedLogEdit`）** —— **正式版（Latest）仍为 v1.0.43；v1.0.49 为当前预发布，含 v1.0.44~v1.0.48 全部内容，待真机确认后转正**
- **数据安全**：v1.0.6 起 WebDAV 凭据 Keystore 加密、备份口令化、事务化写入，均已稳定；v1.0.19 起支持登录 WebDAV 后直接拉取远程备份列表选择恢复（新机无需先生成本地备份）；v1.0.20 起备份文件名带时间戳，同天多份不互相覆盖；v1.0.21 起知识库检索走单列 `search_text` + 查询防抖（旧备份恢复后自动回填该列）
- **待办池**（用户视角，无承诺）：
  - **手动验证项统一见 §9「装机回归清单」**（可勾选；合并 P5 手册 + 上架冒烟 + 逐版本回归重点，并已修正过时项）
  - WebDAV 非标准方法（PROPFIND / MKCOL）依赖反射改 `HttpURLConnection` 内部字段：换 Android 15 / 16 真机需回归验证（无替代方案，属设计取舍）——**需真机，本机无法完成**
  - **Compose 性能审查剩余项**（第一 / 第二批已做功能性缺陷 + 低风险项，以下属结构性重构，改动面大）：MedEdit / Backup 的字段级 Composable 拆分、6 个屏幕顶层 Flow 收集下沉到叶子、`BackupViewModel`/`ReportViewModel` 合并单一 UiState、`AppShell` 按需创建 ViewModel（**v1.0.40 已把 Recipes / ExercisePlans 两个改为进页面才创建**，其余 10 个仍一次性创建）、`DividerList` 展平进父 `LazyListScope`、Sheet 内嵌套 `verticalScroll` 冲突、`DateProvider` 抽象（统一 7 个 VM 的日期 ticker 样板）
  - **Compose Strong Skipping 无需配置**：Kotlin 2.0.20 起编译器**默认开启**（勿再按审查报告建议加 `composeCompiler { featureFlags = ... }`）；`collectAsState()` 全库残留 0、日期 ticker 已覆盖 Today/Wellness/Exercise/Checkup/Symptom/Emergency/Knowledge 7 个 VM
  - **S1 提醒链回归测试（Robolectric）—— 待做，但已确认可行**：v1.0.44 的可行性验证证明 Robolectric 4.13 可正常解析、能跑、`sdk=34` 生效，且它**能模拟 `AlarmManager`**（那是它的核心能力），所以用它给 `ReminderScheduler.rescheduleAll` 写「取消-重建」语义（含 doneRefs 过滤、升级重建窗口）的回归测试是**可行且有价值**的——A1/N1 这类 bug 的根源正是「依赖 `AlarmManager`，纯 JVM 单测覆盖不到」。**但注意**：Robolectric **不能**用于检测 ICU 正则差异（已实测证伪，见 §7），那类只能靠 `RegexLiteralGuardTest` 静态守卫或真机。
  - **S1b 打分滑杆手势回归测试（Robolectric + `compose-ui-test`）—— 待做（v1.0.47 新增）**：`ScoreInput` 的「拖动失效」横跨 29 个版本没被任何测试发现，因为它是**手势命中行为**，纯 JVM 单测（`isReturnDefaultValues = true`）与静态源码守卫都覆盖不到。计划：`testOptions { unitTests { isIncludeAndroidResources = true } }` + `testImplementation("androidx.compose.ui:ui-test-junit4")` + `debugImplementation("androidx.compose.ui:ui-test-manifest")` + Robolectric，用 `performTouchInput { swipe(...) }` 断言滑杆值真的变化、`performTouchInput { click(...) }` 断言点按落在当前值时被提交。**v1.0.47 未落地原因**：Robolectric 测试期需联网下载 `android-all-instrumented` 大包，当晚 `api.github.com` / Maven 多次抖动，先交付真机验证版。**注意与 S1 共用同一套 Robolectric 基建，建议一起做。**
  - **趋势页方案 B / C（2026-09-21 用户选「先只做 A」）**：A（精准修复 + 时间范围切换）已于 v1.0.45 落地。剩余两项待评估：
    **B「总览 + 展开」**——趋势页顶部一张「指标总览」卡（每个指标一行：名称 + 当前值 + 变化 + sparkline），点行进入该指标的详情大图（复用 v1.0.45 的 TrendChart）。解决「6 张图要滚很久」，代价是看细节多一次点击。
    **C「小多图 + 炎症指标」**——把 6 个指标压成 2×3 小多图同屏（共享时间轴、各自量程与阈值），并新增 **ESR / CRP** 两个客观炎症指标（数据已在 `lab_results`，当前趋势页完全没有展示；对强直随访而言这是最该看的客观指标）。改动最大：需给小多图模式 + `TrendRepository` 加 lab 查询 + `Trends` 结构扩展。
    三套方案的对照图见当时会话产出的 inline widget（标题 `ashkb_trend_chart_options_abc`）。

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
| B5 ✅ | 多源提醒（运动 / BASDAI 问卷 / 复诊）——M10 四源只实现用药 | `ReminderScheduler.rescheduleAll(context, meds)` 只遍历药单；全部调用点只传药单 |
| B6 ✅ | 紧急卡「锁屏紧急信息集成」+「钥匙扣二维码模板」——R19 三项只落打印版 PDF | grep `锁屏`/`二维码` 零命中；Manifest 无相关组件 |
| ~~B7~~ | ~~4–12 周周期康复计划模板（按周递进 + 完成度追踪）~~ —— **已于 v1.0.39 落地**（`exercise_plans` 表 + 3 模板（4 / 8 / 12 周）幂等种入；`week_structure` 每周「强度级别 + 目标天数 + 提示」；完成度由 `exercise_logs` 去重日期反算；同刻只启用一个） | — |
| B8 | 免打扰时段 + 同时间段多条提醒合并推送 | M10；零实现 |
| B9 | 提醒升级链第三级「强提醒（全屏 / 横幅）」——只落「未确认 → 重复提醒」两级 | v3 M10；无 `fullScreenIntent`，`MAX_ESCALATION` 到 2 即止 |
| ~~B10~~ | ~~复诊「结果照片归档」（化验单 / 影像报告拍照存档与检索）~~ —— **已于 v1.0.33 落地**（拍照 / 相册 / PDF 三种来源，落 `filesDir/checkup_attachments/`，DB 存元数据；⚠️ 附件不随数据库备份，换机需重新导入） | M6 |
| ~~B11~~ | ~~营养素「每日上限警示」+「与用药时间错开提醒」+「与 M1 服药时间表合并视图」~~ —— **已于 v1.0.38 落地**（补剂可量化剂量 + 用户设定每日参考上限（**刻意不内置医学上限值**）；矿物类补剂与螯合类用药（左甲状腺素/四环素/喹诺酮/铁剂）间隔 <2h 提示；今日服药 + 补剂合并时间表） | — |
| B12 ✅ | 极简模式状态机（连续 3 天核心记录未完成 → 询问原因 → 身体不适/住院切极简模式） | 红线三；`Profile.ui_mode` 字段空转（仅 Entity 定义处），`minimal_since` 字段不存在，无判定 / 询问 / 切换 |
| B13 ✅ | 生活方式画像（吸烟 / 职业久坐时长 / 运动习惯 / 睡眠）→ 驱动 M4 处方个性化 | M0；`Profile.lifestyle` 字段空转，未采集未展示 |

**C. 弱化实现（有字段或半套流程，未达规划口径）**

| # | 缺口 | 现状 vs 规划 |
|---|---|---|
| C1 | 骶髂关节影像分期 | 规划 M0 要求（诊断信息全量），`Profile` 无该字段 |
| ~~C2~~ | ~~补剂（营养）依从统计~~ —— **已于 v1.0.37 落地**（`ReportRepository.overview()` 新增 `supplement` 统计，与用药同口径；「概览」页补剂依从卡片，无记录时不摆 0% 假进度条） | — |
| ~~C3~~ | ~~化验「按单位分组 + 跨院数据仅供参考提示」~~ —— **已于 v1.0.37 落地**（`domain/LabUnits` 按「项目名 + 单位」分组；跨单位项目在该组标注单位并在列表顶部提示「仅供参考」；异常置顶与折叠交互不变） | — |
| ~~C4~~ | ~~复诊前准备清单（自动打包 + **空腹等抽血准备提示**）~~ —— **已于 v1.0.33 落地**（复诊记录页顶部准备卡：倒计时 + 需做检查项 + 携带清单；化验类自动标注「需空腹」并给禁食说明；影像类补「去金属饰品」） | M6 |
| ~~C5~~ | ~~停药 / 漏服原因枚举~~ —— **已于 v1.0.37 落地**（停药新增 感染发热 / 准备手术 / 经济原因（各带提示）；漏服新增 遗忘 / 外出 / 药物用完；旧枚举 key 全保留，历史日志兼容） | — |
| ~~C6~~ | ~~服药三态「固定 / PRN / 减量中」+「医生批准的减量方案不触发停药警示」~~ —— **已于 v1.0.37 落地**（`medications.dose_state` + `taper_note`；编辑表单三态选择器 + 减量备注；`domain/StopWarning` 在「减量中」且原因「自行停药」时豁免警示，含生物制剂强化警示） | — |
| ~~C7~~ | ~~漏服与延迟处理指引~~ —— **已于 v1.0.33 落地**（今日页漏服卡新增入口；口服 ≤2h 尽快补服 / >2h 跳过且**勿加倍**；注射 ≤48h 窗口内补注 / >48h 超窗**先联系医生**；免疫抑制类加提示；只给通用提示不做剂量决策） | 注射只有「顺延」，无补服规则与超窗分级 |
| C8 | 久坐每 30–45 分钟起身提醒、姿势 / 睡姿建议、晨僵时长驱动起床热身序列 | M4；晨僵仅作处方页「判读依据」展示，`ExerciseEngine.todayPlan()` 不吃晨僵 |
| ~~C9~~ | ~~体重「目标区间提示」~~ —— **已于 v1.0.33 落地**（档案可设上下限，容忍填反自动交换；体重卡显示在区间内 / 低于 / 高于 x kg；未设时不打扰） | M2 |
| ~~C10~~ | ~~生物制剂续方提醒 + 结核 / 乙肝 / 丙肝筛查初始节点~~ —— **已于 v1.0.37 落地**（`domain/ScreeningSeeds` + `HealthRepository.seedBiologicScreeningItems()` 一键种入 4 项，按名称幂等去重；复诊项目卡片内入口） | — |
| C11 | 电池白名单 / 自启动引导（仅一行文字提示，无跳转按钮）、提醒自检缺「写入测试提醒验证」 | M10 |
| C12 | 全局免责声明（显著位置 / 首启声明）+ 自动提示统一前缀「仅供参考，以主治医师医嘱为准」 | 现仅条目级与 PDF 页脚声明 |

**D. 质量项：知识库种子挂点悬空**

知识库种子的 `app_link` 已引用**代码中不存在的表 / 字段**，用户点进去会看到指不到实处的提示。已知：~~`exercise_plans.week_structure`、`exercise_plans(stage_mode=flare)`（→ B7）~~ **已于 v1.0.39 补齐**（`exercise_plans` 表落地，挂点不再悬空）、~~「与 `body_measures` 胸廓扩张度趋势联动」（→ B1）~~ **已了结（2026-09-21：B1 取消，种子 exc-004 文案已改写为「结合复诊时医生测量的数值」）**、「profile 吸烟状态登记后知识库置顶」（→ B13，仍未做）。剩余一项（B13）修法二选一：补齐实现，或改写种子文案。

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
- **测试基线**：264 条单测全绿；新增功能须同步补测（`app/src/test/.../`，24 个测试文件覆盖 backup / 加密（v2+v3）/ 附件路径与远端比对 / PROPFIND 解析 / 停药警示与三态 / 化验单位分组 / 筛查种子 / 补剂上限与错开 / 食谱种子与出处台账 / 计划模板编解码 / 计划完成度 / 通用名键 / 运动分级 / 导入解析 / 排程计算 / 知识库检索 / 紧急卡用药汇总 / 恢复码 / 复诊准备 / 漏服 / 体重目标）

## 9. 装机回归清单（可勾选）

> **用途**：交付前 / 换机 / 上架前的手动验证。合并三处来源——① 规划文档 P5 自用验证手册（`<旧工作区>/patient-health-plan/v3-p0/p5-dogfooding-manual.md`）② 上架前装机冒烟 5 点（v1.0.16 起开 R8 混淆后新增）③ 逐版本「装机回归重点」（CHANGELOG）。
> **记录方式**：勾选 + 填机型 / 版本 / 日期；失败项记「机型 + 场景 + 现象」。
> ⚠️ P5 手册写于 v1.0.0，**以下已按当前代码修正过时项**（修正处标 🔄）。

### 9.1 准备（每台机一次）

- [ ] 安装 release APK，版本 `__________`，日期 `__________`
- [ ] 建档：姓名 / 诊断 / HLA-B27 / 分期（「我的」）
- [ ] 添加 1 种每日两次口服药（早晚各一，时刻设在 5 分钟后便于观察）
- [ ] 添加 1 种 Q2W 注射药
- [ ] 「我的 → 提醒可靠性自检」：通知权限 ✓、精确闹钟 ✓
- [ ] 厂商系统另将应用加入**电池优化白名单**（华为 / 小米 / 三星 必做）

### 9.2 A 组 · 提醒可靠性专项（三机型 × 11，P5 核心）

> 全部在「设置完成后不打开应用」的状态下等待提醒。这是对 M10 可靠性的最终验证。

| # | 场景 | 步骤 | 预期 | 小米 | 华为 | 三星 |
|---|---|---|---|---|---|---|
| A1 | 前台杀后台 | 设 5 分钟后提醒 → 立即划掉最近任务 | 通知准时响，含「已服用」按钮 | ☐ | ☐ | ☐ |
| A2 | Doze 深度休眠 | 设 30 分钟后提醒 → 锁屏静置不碰 | 准时（精确闹钟 While-Idle） | ☐ | ☐ | ☐ |
| A3 | 隔夜提醒 | 设次日早晨时刻 → 睡前锁屏 | 次日准时响 | ☐ | ☐ | ☐ |
| A4 | 重启后存活 | 设提醒 → 立即重启手机 → 等待 | 开机后闹钟仍在，准时响（BootReceiver 重排） | ☐ | ☐ | ☐ |
| A5 | 升级安装覆盖 | 提醒设置后用新版本 APK 覆盖安装 | MY_PACKAGE_REPLACED 重排，提醒保留 | ☐ | ☐ | ☐ |
| A6 | 手动改时钟 | 设 1 小时后提醒 → 系统时间 +50 分钟 | 提醒随新时刻走（TIME_SET 重排） | ☐ | ☐ | ☐ |
| A7 | 时区切换 | 设提醒 → 切换时区 | 按本地时间重排（TIMEZONE_CHANGED） | ☐ | ☐ | ☐ |
| A8 | 精确闹钟降级回授 | 撤销精确闹钟权限 → 提醒仍响（±15 分）→ 回授 | 回授即重排回精确模式 | ☐ | ☐ | ☐ |
| A9 | 升级链 | 提醒响后不操作 | +30 分钟重复提醒（共 2 次），文案升级为「仍未确认」 | ☐ | ☐ | ☐ |
| A10 | 打卡即消音 | 提醒响 → 直接点通知「已服用」 | 通知消失、日志入库（今日页可查）、不再重查 | ☐ | ☐ | ☐ |
| A11 | 顺延场景 | 打卡前把该药时刻改到明天 | 到点后闹钟静默取消（残留旧闹钟不发通知） | ☐ | ☐ | ☐ |

> **A1/A2 任一机型系统性失败 → 回炉**（先查电池白名单，再查代码）。

### 9.3 B 组 · 全模块走查（13 条）

| # | 模块 | 走查步骤（简） | 预期 | ☐ |
|---|---|---|---|---|
| B1 | M0 建档 | 「我的」填档案：诊断年份 / HLA-B27 / 分期 / 过敏史 / 血型 | 保存后紧急卡 PDF 与复诊报告同步反映 | ☐ |
| B2 | M1 药单 | 建口服每日两次 + Q2W 注射 + PRN 备用药各一 | 今日页三分法展示；注射日才出现注射卡 | ☐ |
| B3 | M1 打卡 | 按时打卡一次 / 部分完成（选原因）/ 跳过（必填原因） | 状态正确流转；当日不可重复打卡（幂等） | ☐ |
| B4 | M1 补打卡 | 次日为昨日槽位补打卡 | 记入昨日、标注 late（晚于计划 30 分钟以上） | ☐ |
| B5 | M1 注射轮换 | 注射打卡选部位 | 下次默认提示轮换部位；批次号与反应可录 | ☐ |
| B6 | M5 症状 | 录晨僵 / 夜间痛 / 疼痛 / 疲乏 / 眼部 / 发热；做一次 BASDAI | BASDAI ≥4 显示活动度警示；眼部 >0 显示 emr-001 提示 | ☐ |
| B7 | M4 运动 | 按处方执行；次日填反馈「疼痛加重 + 非肌肉酸痛」 | 拦截区条目不可打卡；反馈建议减量 20%（exc-010） | ☐ |
| B8 | K 知识库 | 搜索「甲氨蝶呤」「叶酸」「游泳」 | 命中排序合理；高危条目有复核警报横幅 | ☐ |
| B9 | M2/M3 营养 | 录体征 / 体重 / 补剂 / 饮食画像 / 忌口 | 数据可增删改查；体重趋势折线正确 | ☐ |
| B10 | M6 复诊 | 建复诊项目（周期 90 天）+ 录一次化验（标高 / 低）+ 疫苗 | 异常值带 ↑↓ 标记；下次复诊排序正确 | ☐ |
| B11 | M7 紧急卡 | 五场景卡浏览 + 拨号按钮 + 生成打印 PDF | PDF 含档案 / 联系人 / 五卡 / **当前用药**；分享可发出 | ☐ |
| B12 | M9 报表 | 连续记录 3 天后看概览 / 趋势 | 依从率口径正确（部分=0.5）；趋势图与手记一致 | ☐ |
| B13 | M10 权限自检 | 关通知权限再进「我的」 | 自检卡显示红「!」并给一键直达设置 | ☐ |

> 🔄 B11 增补「当前用药」（v1.0.26 A1）。

### 9.4 C 组 · 数据自主与安全专项（10 条）

| # | 场景 | 步骤 | 预期 | ☐ |
|---|---|---|---|---|
| C1 | 本机备份 | 备份与数据 → 设口令 → 生成本机备份 → 分享另存 | 台账登记 BACKUP✓；文件名 🔄 `ashkb-backup-YYYY-MM-DD-HHmmss.ashkb` | ☐ |
| C2 | 错误口令 | 恢复区选该文件 → 输错口令 → 旁路解密 | 拒绝并提示「口令错误或文件已损坏」，主库未动 | ☐ |
| C3 | 完整恢复 | 选备份 → 正确口令 → 旁路校验 → 执行恢复 | pre-restore 快照留存；双校验（行数+SHA）通过提示 | ☐ |
| C4 | 恢复演练 | 点「执行恢复演练」 | 报告「加密链路✓ 解密✓ 恢复双校验✓ 复核一致✓」；台账 DRILL✓；数据无变化 | ☐ |
| C5 | WebDAV 配置 | 填服务器 / 账号 → 测试连接 → 备份到 WebDAV | 三步探针通过；上传回读 SHA 一致；服务器见 `/ashkb/backup/` | ☐ |
| C6 | WebDAV 轮换 | 手动改设备日期连跑（或等自然积累） | 超窗备份被清，保留日 7 / 周 4 / 月 6；🔄 同日只留最晚一份 | ☐ |
| C7 | 档案 JSON | 导出档案 → 清数据 → 导入 | 建档还原 + 药单恢复；提示检查重复 | ☐ |
| C8 | 报告导出 | 报表 → 生成复诊报告 PDF | 分享面板出现；PDF 内容与库内数据一致；🔄 口服药行**不得出现字面量 `null`** | ☐ |
| C9 | 🔄 恢复码（v1.0.27） | 备份页生成恢复码 → 抄写 → 备份 → 恢复时**故意输错口令、改输恢复码** | 恢复码可解密；小写 / 漏连字符 / 多空格等抄写形态均可解 | ☐ |
| C10 | 🔄 远程恢复（v1.0.19 / v1.0.20） | 新机（或清数据）→ 配置 WebDAV → 「从 WebDAV 恢复」 | 无需先生成本地备份即可列出服务器旧备份 → 下载 → 恢复 | ☐ |

> 🔄 **P5 手册 §6「已知限制 4：备份无恢复码兜底」已被 v1.0.27 推翻**，C9 为新增必验项。
> C1/C2/C3 的 v1 旧格式兼容需回归：用 v1.0.5–v1.0.26 生成的旧备份（`ASHKBAK1`）应仍可正常恢复。

### 9.5 上架前装机冒烟（v1.0.16 起开 R8 混淆，验证反射未被裁断）

- [ ] 冷启动正常
- [ ] 五 Tab 导航 + 各二级页可达（Navigation `@Serializable` 类型安全路由，混淆后反射查找 `$$serializer`）
- [ ] 备份导出 / 恢复演练正常（Room 实体字段名即磁盘格式，混淆即损坏）
- [ ] WebDAV 探针（PROPFIND / MKCOL 反射改 `HttpURLConnection` 内部字段）
- [ ] PDF 导出（复诊报告 + 紧急卡）

### 9.6 逐版本回归重点（v1.0.20 → v1.0.28）

| 版本 | 回归重点 | ☐ |
|---|---|---|
| v1.0.20 (X) | ①点化验「偏高 / 偏低」胶囊展开参考范围 ②同天两次 WebDAV 备份出现两份带时间戳文件、列表排序正确 ③新机配好 WebDAV 直接列旧备份可恢复 | ☐ |
| v1.0.21 | 知识库搜索中文子串有命中（「甲氨蝶呤」）；旧备份恢复后搜索列自动回填 | ☐ |
| v1.0.22 | 备份 / WebDAV / 恢复 / 报表 PDF 各入口 busy 与提示行为与 v1.0.21 一致 | ☐ |
| v1.0.23 | 化验 Tab：有异常 / 最近一次的日期默认展开、纯正常旧日期收起；切换折叠后翻页加载更早记录再回来，状态保持 | ☐ |
| v1.0.24 | 复诊报告 PDF 口服药行**不再出现 `null`**；服药提醒通知（含加急重复）文案正常 | ☐ |
| v1.0.25 | ①零点后（或调系统时间过零点）今日页切到新的一天 ②换药表单填一半旋转屏幕内容不丢 ③药品列表超一屏可滚动 ④报表 6 个折线图入场动画只播一次、不闪烁 | ☐ |
| v1.0.26 (A1) | ①紧急卡出现「当前用药」，生物制剂等带「免疫抑制」标签 ②打印版紧急卡 PDF 有同一分区与提示行 ③药单清空显示「（无在用药物记录）」 ④未建档时用药仍显示 | ☐ |
| v1.0.27 (A2) | 见 C9：生成恢复码 → 抄写 → 备份 → 输错口令 → 输恢复码可解密；旧 v1 备份不受影响；一键恢复演练仍通过 | ☐ |
| v1.0.28 | ①知识库搜索框敲键顺滑（不再整屏重组）②复诊详情依次打开多条，内存无持续增长 ③紧急卡用药区若有结束日为昨天的药，跨零点后应消失 ④症状页跨零点后日期 Chip 状态正常（不出现两个都不选中）⑤旧版备份恢复不受影响 | ☐ |
| v1.0.29 | **冷启动不崩**（v1.0.28 闪退修复）→ 五 Tab 正常切换 → 症状页「今天 / 昨天」Chip 正常 | ☑ 已验（2026-09-20：冷启动不崩✓ 五 Tab 正常✓ Chip 正常✓） |
| v1.0.30 | ①报表「概览 ⇄ 趋势 ⇄ 导出」反复切换，图表立即完整显示、**无动画重播** ②新增一条 BASDAI / 症状记录后回趋势页，对应图重播一次入场（数据变化才播） | ☑ 已验（2026-09-20：0 点跨零点✓、趋势页切回重播已由 v1.0.30 修复并确认） |
| v1.0.31 | ①药单管理 → 点铅笔图标 → 表单预填该药全部参数 ②改剂量 / 时刻保存后列表与今日页按新参数生效 ③历史打卡记录不受影响 ④停用流程不受影响 | ☑ 已验（2026-09-20：冷启动 / 五 Tab / 症状页 Chip 全过；编辑功能验收待细验） |
| v1.0.32 | 「我的」页底部出现「关于」卡：版本号显示 `v1.0.32 (37)` + MIT 协议 + 免责声明 | ☐ |
| v1.0.33 | ①档案填体重目标 → 体重卡出现状态提示 ②知识库条目写备注 → 列表「有备注」标记，重进仍在 ③复诊记录填「下次复诊」→ 准备卡倒计时 + 化验项目「需空腹」 ④今日页漏服卡片点「漏服处理指引」→ 口服 / 注射分级文案正确 ⑤复诊记录 →「附件归档」→ 拍照 / 相册 / PDF 三种方式可保存与查看 ⑥**旧备份恢复后数据完整**（新列为「未设置」态） | ☐ |
| v1.0.34 | ①化验 Tab 某日期 →「归属复诊记录」→ 选一条 → 出现已归属标记；「记录」Tab 该复诊 →「查看化验」应看到这批化验 ②影像 Tab 同理 ③两 Tab「附件归档」→ 拍照 / 相册 / PDF 可用 ④未归属时先选归属再加附件 → 附件与来源一起归入该复诊 ⑤「记录」Tab 顶部「附件归档」→ 见全部附件（含未归属），可逐条补归属 ⑥旧备份恢复后数据完整 | ☐ |
| v1.0.35 | ①备份页开启「同步附件到 WebDAV」→ 点「同步附件」→ 摘要「待传 N」→「已同步 N」；服务器 `ashkb/attachments/<日期>/` 出现 `.enc` ②关闭开关时按钮不可点并有提示 ③删除已同步附件 → 服务器对应文件消失 ④**换机恢复**：恢复 DB 备份后附件列表完整，点「查看」自动取回并打开 ⑤旧 v1/v2 备份恢复不受影响 | ☑ 已验（2026-09-21：用户确认通过，已转正式发布） |
| v1.0.36 | ①备份页点「校验远端」→ 摘要「远端 N 个文件；补传 x / 缺 y；清理多余 z / 共 w」②**网页端手动删掉一个已同步附件** → 再点「校验远端」→ 该附件被重新上传（服务器再现 `.enc`）③**网页端往 `ashkb/attachments/<日期>/` 放一个 `junk.txt`** → 校验后它**仍在**（非 `.enc` / 非日期目录不碰）④本地删除一个已同步附件（开关开启）→ 校验后服务器对应文件消失 ⑤关闭同步开关时「校验远端」按钮不可点 | ☑ 已验（2026-09-21：用户确认通过，已转正式发布） |
| v1.0.37 | ①药品编辑页可选「服药状态」，选「减量中」出现减量备注输入；药单列表该药出现「减量中」标签 ②**「减量中」的药在停用弹窗选「自行停药」→ 不再出现警示文案**（普通药仍出现；副作用等提示不受影响）③「概览」页出现「补剂依从」卡片（有补剂打卡才显示百分比，否则「暂无补剂打卡记录」）④化验列表：同名项目若存在两种单位，会在该组上方标注单位并出现「仅供参考」提示；异常项仍置顶 ⑤复诊项目卡片点「一键添加筛查 / 续方节点」→ 提示「已添加 4 个节点」，再点一次提示「已齐全」⑥旧备份恢复后数据完整（新列=未设置） | ☑ 已验（2026-09-21：用户确认 1/2/3/4/5 全部正常，已转正式发布） |
| v1.0.38 | ①补剂新增/编辑表单出现「单次剂量（数值）/ 单位 / 每日参考上限」；填 500 / mg / 上限 800 且每日两次 → 卡片出现「当日累计 1000 mg，已超过你设置的上限 800 mg」②把某补剂设为钙类、时刻 08:00，再建一个「优甲乐」类药时刻 08:00 → 出现「与「优甲乐」服用时间过近…建议间隔 2 小时以上」③该页出现「今日服药 / 补剂时间表」，药与补按时刻合并、带「药 / 补」标签；无计划时显示空态 ④报表出现第 4 个页签「周月报」，可切近 7 天 / 近 30 天；无数据的指标显示「暂无」⑤旧备份恢复后数据完整（新列=未设置） | ◐ **已被 v1.0.42 取代**（本版功能全部含于 v1.0.42，无需再装本版）；B11 / B4 的具体表现可在 v1.0.42 上顺带确认，本版不单独验证 |
| v1.0.39 | ①「数据」页出现「推荐食谱」入口 → 进入见 10 条食谱，可按「抗炎 / 胃肠友好 / 控热量」筛选 ②点开一条食谱 → 详情见配料 / 做法 + 出处（编号 + 完整题录）+ 免责声明；**列表页不显示出处正文** ③收藏一条 → 置顶；自建一条 → 显示「自建」徽标且详情无出处小节 ④运动页出现「康复计划」入口 → 见 4 / 8 / 12 周三个模板 ⑤点「添加模板」→ 首次「已添加 3 个模板」，再点「模板已齐全」⑥启用 4 周计划 → 显示「第 1 / 4 周」「本周 x / 3 天」「累计 x / 16 天（y%）」；打卡运动后数字增加 ⑦旧备份恢复后数据完整（新表为空） | ⚠️ v1.0.39 **冷启动闪退、已废弃**（勿发布）；其功能随 v1.0.40/41 交付，本行验证顺延至 v1.0.41 |
| v1.0.40 | ①冷启动不崩（**注：这是启动兜底把异常吞掉的结果，并非真修复**——根因见 v1.0.41）②「数据」→「推荐食谱」可用、10 条、筛选/收藏/自建正常 ③「运动」→「康复计划」可用、三模板 + 启用后进度正常 ④首屏崩溃摘要（截图反馈用） | ◐ 部分已验（2026-09-21：①冷启动不崩 ✓；②③ 因 v1.0.41 才真正修好「添加模板」闪退，**验证顺延至 v1.0.41**）——**未转正式** |
| v1.0.41 | ①**今日 → 今日运动 → 康复计划 → 添加模板**：提示「已添加 3 个模板」且**不闪退**（v1.0.39/40 的核心缺陷）②再点一次：「模板已齐全」（幂等）③启用某模板后，完成度随运动打卡变化 ④首屏若有异常摘要，应带 `Caused by` 与栈帧（便于用 mapping.txt 反查混淆名）⑤旧备份恢复后数据完整 | ❌ **未修复**（归因 R8 是错的，已由 v1.0.42 取代）。但本版的两处留档改进（`recordNonFatal` + 摘要带 `Caused by`/栈帧）**正是下一轮一轮定位真因的关键** |
| v1.0.42 | ①**今日 → 今日运动 → 康复计划 → 添加模板**：提示「已添加 3 个模板」且**不闪退** ②再点一次：「模板已齐全」（幂等）③启用某模板后，完成度随运动打卡变化 ④旧备份恢复后数据完整 | ◐ **已被 v1.0.43 取代**（本版功能全部含于 v1.0.43，无需再装本版）。①②已于 2026-09-21 用户确认；③④ 未逐条实测（低风险） |
| v1.0.43 | ①**提醒升级链**：设一个 2 分钟后的服药提醒 → 到点不打卡、点「稍后提醒」→ **杀掉应用重开** → 仍应在 +30 / +60 收到追问（旧版会被冷启动清掉）②**编辑已停用的药**：药单里停用一条药 → 再进编辑页 → 应正常预填或提示「未找到该药品记录」并禁用保存，**不卡死**、不产生重复 ③**归档的药编辑保存后不得复活**（仍在已停用区）④**WebDAV 口令框长按右侧眼睛** → 显示明文、松手恢复掩码（备份 / 恢复两处同样）⑤附件删除确认框只作用于被点的那一行 ⑥旧备份恢复后数据完整（无结构变更） | ☑ **已验（2026-09-21 用户确认「确认无问题」）**，**仍为当前正式版（Latest）**；其功能亦含于 v1.0.44 / v1.0.45（两者为预发布） |
| v1.0.44 | ①**N1 重启误提醒**：设一条 2 分钟后的服药提醒 → 到点**打卡**（或点稍后提醒后打卡）→ 在 +30 未到点前**重启手机** → 重启后**不应**再收到该槽位的「未服药」追问（旧版会误提醒）②**N3 种子增量刷新**：老设备升级后进「知识库」→ 搜索/翻看 exc-004（运动/康复相关条目）→ 文案应为改写后的版本（不再提「胸廓扩张度」联动）；**自己写过的条目备注必须还在** ③S3/S4/S5 无用户可见行为变化（vault key 覆盖守卫仅在异常路径报错、备份类型报错文案更具体、崩溃留档内不再含 URL 凭据）④旧备份恢复后数据完整（无结构变更） | ⚠️ **未真机验；已被 v1.0.46 取代**（功能全部含于 v1.0.46），**请直接验 v1.0.46** |
| v1.0.45 | ①**趋势页**：每张图上方有读数（当前值 + 较首次 + 均值/超阈值）、末点旁标出数值、每个测点有小圆点；**不拖动也能读数** ②**时间轴正确性**：若最近一次记录隔了几天才填，图上该段应明显更长（旧版等距，看不出间隔）③**刻度不再互压**：Y 轴标签之间有正常间距（旧版可能挤成一条）④**阈值**：BASDAI 图上虚线阈值只出现在左侧刻度列（不再压住右侧数据）⑤**范围切换**：趋势页顶部「近 7 天 / 近 30 天 / 近 90 天」可切；切到 7 天时体重图不应再出现 7 天以前的数据 ⑥拖动图仍能逐点读数 ⑦旧备份恢复后数据完整 | ⚠️ **未真机验、含 4 处缺陷（见 v1.0.46）；已被 v1.0.46 取代**，**请直接验 v1.0.46** |
| v1.0.46 | ①~⑦ 同上行 v1.0.45 的全部条目，另加：⑧**拖动命中**：在日期间隔不均的图上（前密后疏）拖动，读数气泡应始终跟在**手指下方最近的点**上（旧版会跳到远处）⑨**入场动画**：切到趋势页后折线从左往右扫出，**末点数值气泡应在折线扫到末点后才出现**（旧版提前悬浮）⑩快速连点「近 7 天 → 近 30 天」：图最终显示的必须是 30 天的数据（旧版可能停留在 7 天结果）⑪旧备份恢复后数据完整（无结构变更） | ⚠️ **未真机验；已被 v1.0.47 取代**（功能全部含于 v1.0.47），**请直接验 v1.0.47** |
| v1.0.47 | **打分滑杆逐条手势路径**（BASDAI 自评 / 症状与自评）：①**拖动**：按住滑杆左右拖，值应连续变化（v1.0.17–v1.0.46 **完全拖不动**）②**点按轨道**：点轨道任意位置，值应跳到该处 ③**点按落在当前值上**：滑杆停住不动、点它所在位置 → 该题应被标记为已作答（未作答态「—」应变数字）④**点按最左端（未作答态）**：应能直接答 0 ⑤**两端标签**：「无」「最严重」仍可点 ⑥**慢速拖动**：非常慢地拖，不应被误判成点按（旧判定按逐事件位移算，慢拖会误判）⑦+/− 按钮仍正常；另 ⑧~⑪ 同上行 v1.0.45/v1.0.46 全部条目 ⑫旧备份恢复后数据完整（无结构变更） | ⚠️ **未真机验；已被 v1.0.48 取代**（功能全部含于 v1.0.48），**请直接验 v1.0.48** |
| v1.0.48 | ①**用药记录**：我的 → 药单 → 点任一条药的**药名**（带历史图标）→ 弹层应列出近 90 天逐条记录（日期 + 已服/部分/跳过 + 计划时刻/注射部位/跳过原因/备注），顶部有该药 90 天依从率与完成·部分·跳过拆分 ②**停药原因**：停用一条药后，药单底部应出现「已停用药品（N）」→ 展开应显示**停用日期 + 原因 + 备注**，点该行能打开它的用药记录（含停用前的流水）③**全部停用时**：把所有药都停用 → 「已停用药品」区仍要显示（不能消失）④**无归档药时**：该区整段不显示 ⑤**依从率一致性**：同一时间窗内，药单弹层里的依从率应与「报表 → 概览 → 服药依从」的数值一致（同一公式）⑥跳过原因显示中文（如「遗忘」「外出」「药物用完」），而非 `FORGOT` 之类的英文键 ⑦打分滑杆拖动（v1.0.47 项）仍正常 ⑧旧备份恢复后数据完整（无结构变更） | ⚠️ **未真机验；已被 v1.0.49 取代**（功能全部含于 v1.0.49），**请直接验 v1.0.49** |
| v1.0.49 | ①**修正记录**：我的 → 药单 → 点药名 → 用药记录 → 点某条右侧**铅笔** → 改状态（已服 / 部分 / 跳过）→ 保存 → 列表应立即变（含顶部依从率随之变）②**改状态要连带清字段**：把一条「跳过」改成「已服」→ 该行**不应再显示原因**；把一条「已服」改成「跳过」→ 必须选原因才能保存（不选则「保存」置灰）③**注射部位中文**：注射类药的记录里应显示「左大腿 / 右腹部」等中文，**不应出现 `thigh_l` 这类英文键** ④**部位选择**：注射记录改成「已服」时出现部位 chips，选一个保存后列表显示对应中文 ⑤**归属日与计划时刻不可改**：弹层里这两项是只读的（只作为标题与说明出现）⑥**同一时间窗依从率一致**：药单弹层 ↔「报表 → 概览 → 服药依从」数值应一致（同一公式）⑦**已停用药品**区与全部停用时的显示（v1.0.48 项）仍正常 ⑧打分滑杆拖动（v1.0.47 项）仍正常 ⑨旧备份恢复后数据完整（无结构变更） | ☐ |

### 9.7 已实测闭环（U / W / X 系列，无需重跑）

- **U 系列 7 项**（v1.0.17）：补剂删除 / 服用历史 sheet / BMI 自动算 / 营养骨健康记录删除 / BASDAI 0 直选 / 口令密码键盘 / PDF 异常项
- **W 系列 4 项**（v1.0.18 / v1.0.19）：WebDAV 轮换重写 + 阶段反馈 / BASDAI 未作答按 0 / WebDAV 远程列表恢复
- **X 系列 3 项**（v1.0.20）：化验胶囊点参考范围 / 备份文件名时间戳 / 新机远程恢复引导
- ⚠️ **BASDAI 滑杆三轮修复史**（v1.0.11 → v1.0.17 → v1.0.18）——唯一需真机反复验证才收敛的项；若后续再改评分控件，须重跑「只有疲劳感、没有疼痛感」场景（只答 Q1 应可提交）

### 9.8 待真机（本机无法完成）

- [ ] **Android 15 / 16 WebDAV 反射回归**：`WebDavClient.forceMethod()` 反射改 `HttpURLConnection.method` 字段——换新设备时验 PROPFIND / MKCOL 是否仍正常（无替代方案，属设计取舍）。**注意潜伏隐患**：`findField` 沿父类向上搜，若新版 Android 把 `delegate` 字段改名，会在包装类上命中 `java.net.HttpURLConnection` 的无效影子 `method` 字段 → 静默按 GET 发出 → 报误导性的「地址在服务器上不存在」
- [ ] **P5 手册 A / B / C 三组完整回填**（本清单即其修订版；A 组三机型提醒可靠性为核心）
- [ ] **v1.0.25 / 26 / 27 / 28 装机结果待反馈**
