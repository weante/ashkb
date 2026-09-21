# ASHKB 开发交接文档

> 本文档面向接手本仓库开发的 AI 会话（TraeWork Code 模式 / TraeCode）或人类工程师。
> 记录截至 **v1.0.35**（versionCode 40，2026-09-21）的全部工程知识。
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

- 全量构建约 2~3 分钟；**264 条单测**必须全过才算交付
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
3. 构建 + 单测（第 2 节命令）
4. 交付 APK：复制 `app\build\outputs\apk\{debug,release}\app-{debug,release}.apk` 为仓库根的 `ashkb-X.Y.Z-{debug,release}.apk`
5. 验签（第 3 节）+ 记录 SHA-256
6. GitHub 同步（第 5 节）

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
- **⚠️ 组合期创建 VM 会顺带「打开数据库」——与启动协程并发即可能闪退（v1.0.40 血泪）**：`AppShell` 里 `viewModel(factory = ...)` 在**组合期**执行；若该 VM 的属性初始化里就建 Room `Flow`（如 `repo.observeX().stateIn(...)`），会立即触发 **数据库首次打开**（`InvalidationTracker` 初始化需要打开的库）。于是**主线程**与 `AshkbApplication.onCreate` 的启动协程（IO）会**并发打开数据库**；若此时恰好要跑迁移，就会出现「冷启动闪退」且**没有任何界面提示**（协程内未捕获异常直接杀进程）。**两条防御**：① 新增的 L2 页面 VM 一律放进 `composable<...>` 内创建（v1.0.40 已把 Recipes / ExercisePlans 这样改），别在 AppShell 顶部一次性建；② `Application.onCreate` 的启动协程体整体包 `runCatching`（协程异常不会像主线程那样只崩当前操作，而是杀进程）。**排查提示**：这类闪退若拿不到 logcat，先怀疑「组合期触发数据库打开」与「协程内未捕获异常」两处，而不是迁移本身（v1.0.40 实测迁移 SQL 与 Room 期望逐字一致）。
- **崩溃必须留痕（v1.0.40）**：纯离线应用没有上报通道，闪退时开发者零线索（只能猜，极易误判）。新增 `CrashLogger`：`Thread.setDefaultUncaughtExceptionHandler` 把堆栈写入 `filesDir/last_crash.txt`（`adb pull` 可取）+ Logcat，并在**下次启动**以 Snackbar 摘要提示便于截图。**只写本地、不上报**，与「零网络权限」红线一致。

## 8. 当前状态与下一步

- **最新版**：v1.0.41（versionCode 46）：v1.0.21 知识库检索优化 + v1.0.22 清除 `as MutableStateFlow` 强转 + v1.0.23 化验 Tab 分组折叠 + v1.0.24 文案资源化 + v1.0.25 Compose 性能审查第一批 + v1.0.26 紧急卡「当前用药」+ v1.0.27 备份恢复码（v2 信封）+ v1.0.28 Compose 性能审查第二批 + v1.0.29 热修启动闪退 + v1.0.30 趋势图动画修复 + v1.0.31 药单在用药品编辑 + v1.0.32「我的」页关于卡 + v1.0.33 规划缺口第二批（Room v9→v10）+ v1.0.34 附件归档增强（Room v10→v11：化验/影像归属复诊记录）+ **v1.0.35 附件接入 WebDAV 备份（Room v11→v12 + 备份格式 v2→v3：稳定 vault key / 逐个加密上传 / 懒下载 / 同步删除 / 同步开关）** + v1.0.36 附件远端校验与补传（PROPFIND 列远端比对：远端缺失补传 / 远端多余清理；无结构变更） + **v1.0.37 用药精细化 + 化验与筛查（Room v12→v13：C5 原因枚举对齐 / C6 减量中态 + 医嘱减量豁免警示 / C2 补剂依从 / C3 化验按单位分组 + 跨院提示 / C10 生物制剂筛查与续方种子）** + **v1.0.38 营养素精细化 + 周月报（Room v13→v14：B11 补剂每日上限 + 与用药错开提醒 + 服药/补剂合并时间表；B4 报表第 4 页签「周月报」）** + **v1.0.39 推荐食谱库 + 周期康复计划（Room v14→v15：B3 `recipes` 表 + 10 条带出处种子；B7 `exercise_plans` 表 + 4/8/12 周模板 + 完成度反算）** ⚠️ **该版冷启动闪退、已废弃（勿发布）；功能随 v1.0.40 交付** + **v1.0.40 冷启动兜底 + 崩溃留档（两个新 VM 改为进页面才创建 + 启动期整体兜底 + CrashLogger；无结构变更）** ⚠️ **其「修复」不成立：只是把启动期异常吞掉，崩溃从「冷启动」挪到了「点添加模板」** + **v1.0.41 修复「康复计划 → 添加模板」闪退（根因：R8 对 Kotlin `object` 单例的激进优化使 `ExercisePlanTemplates` 在 ART 上无法完成初始化——真机 `NoClassDefFoundError: K1.n` 经 mapping 反查；修法：`proguard-rules.pro` 完整保留该组领域类 + 兜底异常也留档 + 崩溃摘要带 Caused by/栈帧；无结构变更）**
- **数据安全**：v1.0.6 起 WebDAV 凭据 Keystore 加密、备份口令化、事务化写入，均已稳定；v1.0.19 起支持登录 WebDAV 后直接拉取远程备份列表选择恢复（新机无需先生成本地备份）；v1.0.20 起备份文件名带时间戳，同天多份不互相覆盖；v1.0.21 起知识库检索走单列 `search_text` + 查询防抖（旧备份恢复后自动回填该列）
- **待办池**（用户视角，无承诺）：
  - **手动验证项统一见 §9「装机回归清单」**（可勾选；合并 P5 手册 + 上架冒烟 + 逐版本回归重点，并已修正过时项）
  - WebDAV 非标准方法（PROPFIND / MKCOL）依赖反射改 `HttpURLConnection` 内部字段：换 Android 15 / 16 真机需回归验证（无替代方案，属设计取舍）——**需真机，本机无法完成**
  - **Compose 性能审查剩余项**（第一 / 第二批已做功能性缺陷 + 低风险项，以下属结构性重构，改动面大）：MedEdit / Backup 的字段级 Composable 拆分、6 个屏幕顶层 Flow 收集下沉到叶子、`BackupViewModel`/`ReportViewModel` 合并单一 UiState、`AppShell` 按需创建 ViewModel（**v1.0.40 已把 Recipes / ExercisePlans 两个改为进页面才创建**，其余 10 个仍一次性创建）、`DividerList` 展平进父 `LazyListScope`、Sheet 内嵌套 `verticalScroll` 冲突、`DateProvider` 抽象（统一 7 个 VM 的日期 ticker 样板）
  - **Compose Strong Skipping 无需配置**：Kotlin 2.0.20 起编译器**默认开启**（勿再按审查报告建议加 `composeCompiler { featureFlags = ... }`）；`collectAsState()` 全库残留 0、日期 ticker 已覆盖 Today/Wellness/Exercise/Checkup/Symptom/Emergency/Knowledge 7 个 VM

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
| B1 ✅ | **AS 专项体征**（胸廓扩张度 / 枕墙距 / 指地距 / Schober）——R26 只做了一半（情绪睡眠疲劳已做），而这恰是医生最关注的 AS 功能指标 | R26 / M6；grep `胸廓`/`枕墙`/`指地`/`Schober` 在 java 侧零命中（仅存在于 `kb_seed_*.json` 正文） |
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

知识库种子的 `app_link` 已引用**代码中不存在的表 / 字段**，用户点进去会看到指不到实处的提示。已知：~~`exercise_plans.week_structure`、`exercise_plans(stage_mode=flare)`（→ B7）~~ **已于 v1.0.39 补齐**（`exercise_plans` 表落地，挂点不再悬空）、「与 `body_measures` 胸廓扩张度趋势联动」（→ B1，仍未做）、「profile 吸烟状态登记后知识库置顶」（→ B13，仍未做）。剩余两项修法二选一：补齐实现，或改写种子文案。

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
| v1.0.38 | ①补剂新增/编辑表单出现「单次剂量（数值）/ 单位 / 每日参考上限」；填 500 / mg / 上限 800 且每日两次 → 卡片出现「当日累计 1000 mg，已超过你设置的上限 800 mg」②把某补剂设为钙类、时刻 08:00，再建一个「优甲乐」类药时刻 08:00 → 出现「与「优甲乐」服用时间过近…建议间隔 2 小时以上」③该页出现「今日服药 / 补剂时间表」，药与补按时刻合并、带「药 / 补」标签；无计划时显示空态 ④报表出现第 4 个页签「周月报」，可切近 7 天 / 近 30 天；无数据的指标显示「暂无」⑤旧备份恢复后数据完整（新列=未设置） | ☐ |
| v1.0.39 | ①「数据」页出现「推荐食谱」入口 → 进入见 10 条食谱，可按「抗炎 / 胃肠友好 / 控热量」筛选 ②点开一条食谱 → 详情见配料 / 做法 + 出处（编号 + 完整题录）+ 免责声明；**列表页不显示出处正文** ③收藏一条 → 置顶；自建一条 → 显示「自建」徽标且详情无出处小节 ④运动页出现「康复计划」入口 → 见 4 / 8 / 12 周三个模板 ⑤点「添加模板」→ 首次「已添加 3 个模板」，再点「模板已齐全」⑥启用 4 周计划 → 显示「第 1 / 4 周」「本周 x / 3 天」「累计 x / 16 天（y%）」；打卡运动后数字增加 ⑦旧备份恢复后数据完整（新表为空） | ⚠️ v1.0.39 **冷启动闪退、已废弃**（勿发布）；其功能随 v1.0.40/41 交付，本行验证顺延至 v1.0.41 |
| v1.0.40 | ①冷启动不崩（**注：这是启动兜底把异常吞掉的结果，并非真修复**——根因见 v1.0.41）②「数据」→「推荐食谱」可用、10 条、筛选/收藏/自建正常 ③「运动」→「康复计划」可用、三模板 + 启用后进度正常 ④首屏崩溃摘要（截图反馈用） | ◐ 部分已验（2026-09-21：①冷启动不崩 ✓；②③ 因 v1.0.41 才真正修好「添加模板」闪退，**验证顺延至 v1.0.41**）——**未转正式** |
| v1.0.41 | ①**今日 → 今日运动 → 康复计划 → 添加模板**：提示「已添加 3 个模板」且**不闪退**（v1.0.39/40 的核心缺陷）②再点一次：「模板已齐全」（幂等）③启用某模板后，完成度随运动打卡变化 ④首屏若有异常摘要，应带 `Caused by` 与栈帧（便于用 mapping.txt 反查混淆名）⑤旧备份恢复后数据完整 | ☐ 待验（2026-09-21 已发预发布；根因与修复见 CHANGELOG v1.0.41） |

### 9.7 已实测闭环（U / W / X 系列，无需重跑）

- **U 系列 7 项**（v1.0.17）：补剂删除 / 服用历史 sheet / BMI 自动算 / 营养骨健康记录删除 / BASDAI 0 直选 / 口令密码键盘 / PDF 异常项
- **W 系列 4 项**（v1.0.18 / v1.0.19）：WebDAV 轮换重写 + 阶段反馈 / BASDAI 未作答按 0 / WebDAV 远程列表恢复
- **X 系列 3 项**（v1.0.20）：化验胶囊点参考范围 / 备份文件名时间戳 / 新机远程恢复引导
- ⚠️ **BASDAI 滑杆三轮修复史**（v1.0.11 → v1.0.17 → v1.0.18）——唯一需真机反复验证才收敛的项；若后续再改评分控件，须重跑「只有疲劳感、没有疼痛感」场景（只答 Q1 应可提交）

### 9.8 待真机（本机无法完成）

- [ ] **Android 15 / 16 WebDAV 反射回归**：`WebDavClient.forceMethod()` 反射改 `HttpURLConnection.method` 字段——换新设备时验 PROPFIND / MKCOL 是否仍正常（无替代方案，属设计取舍）。**注意潜伏隐患**：`findField` 沿父类向上搜，若新版 Android 把 `delegate` 字段改名，会在包装类上命中 `java.net.HttpURLConnection` 的无效影子 `method` 字段 → 静默按 GET 发出 → 报误导性的「地址在服务器上不存在」
- [ ] **P5 手册 A / B / C 三组完整回填**（本清单即其修订版；A 组三机型提醒可靠性为核心）
- [ ] **v1.0.25 / 26 / 27 / 28 装机结果待反馈**
