# ASHKB 开发交接文档

> 本文档面向接手本仓库开发的 AI 会话（TraeWork Code 模式 / TraeCode）或人类工程师。
> 记录截至 **v1.0.25**（versionCode 30，2026-09-18）的全部工程知识。
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

- 全量构建约 2~3 分钟；**114 条单测**必须全过才算交付
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
│   ├── db/         # AppDatabase（Room，version=9）、DAO
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
- **新增列与备份恢复的兼容（v1.0.21）**：`BackupEngine.insertTable` 按「备份行自身的列集」按名 INSERT，故旧备份缺新列**不会**报错，但要求 Room 侧新列**可空**（NOT NULL 无默认值会插入失败）；而 `verifyAgainst` 按备份自身列集比对（R9），加列不会让旧备份 SHA 误判。**真正的坑**：恢复后新列为 NULL，会让依赖该列的查询整库失配（知识库搜索会搜不到任何条目）——必须在 `BackupEngine.restore` 双校验通过后、提交前按当前口径回填，位置与 R1 的 profile 归一化相同。
- **批量改代码脚本务必先探测行尾（v1.0.25 踩坑）**：本仓库的 `.kt` 文件是 **LF** 行尾（个别文件末尾有 1 个 CRLF），若脚本按 `\r\n` 切分，整块 import 会被当成单行 → 替换静默不生效（表现为「调用点改了但 import 没加」，编译才暴露）。正确做法：先 `-replace "\r\n","\n"` 归一化处理，写完再按原风格还原；同时注意**一个文件可能有两段 import 块（中间空行分隔）**，按块处理会给两块各插一次导致 import 重复。
- **Compose 反模式速查（v1.0.25 已修，勿回退）**：①屏幕一律用 `collectAsStateWithLifecycle()`，禁用 `collectAsState()`（后者不感知生命周期，后台仍在收 Room 流）②`Canvas` 绘制 lambda 内不得新建 `Path`/`Brush`/`PathEffect` 或调 `textMeasurer.measure`，须 `remember` 到绘制外 ③传给 `TrendChart` 的 `points` 必须 `remember(源数据)`——其入场动画以 `points` 为 key，新 List 实例会让动画反复重播 ④派生计算（`groupBy`/`partition`/`map`）注意 `remember`，且**不能写在 `LazyListScope` 内**（那不是 @Composable 作用域，需上提到 `LazyColumn` 之前）⑤ViewModel 中禁止在构造时求值 `LocalDate.now()`（跨零点冻结），须用 `MutableStateFlow` + ticker 驱动。

## 8. 当前状态与下一步

- **最新版**：v1.0.25（versionCode 30）：v1.0.21 知识库检索优化（Room v8→v9）+ v1.0.22 清除 `as MutableStateFlow` 强转 + v1.0.23 化验 Tab 分组折叠 + v1.0.24 通知/VM/PDF 文案资源化 + v1.0.25 Compose 性能审查第一批（跨零点日期、旋转丢输入、67 处 lifecycle 收集、TrendChart 绘制期零分配）
- **数据安全**：v1.0.6 起 WebDAV 凭据 Keystore 加密、备份口令化、事务化写入，均已稳定；v1.0.19 起支持登录 WebDAV 后直接拉取远程备份列表选择恢复（新机无需先生成本地备份）；v1.0.20 起备份文件名带时间戳，同天多份不互相覆盖；v1.0.21 起知识库检索走单列 `search_text` + 查询防抖（旧备份恢复后自动回填该列）
- **待办池**（用户视角，无承诺）：
  - WebDAV 非标准方法（PROPFIND / MKCOL）依赖反射改 `HttpURLConnection` 内部字段：换 Android 15 / 16 真机需回归验证（无替代方案，属设计取舍）——**需真机，本机无法完成**
  - **Compose 性能审查剩余项**（第一批已做功能性缺陷 + 低风险项，以下属结构性重构，改动面大）：MedEdit / Backup / Knowledge 的字段级 Composable 拆分、`BackupViewModel`/`ReportViewModel` 合并单一 UiState、Compose Strong Skipping Mode、`AppShell` 按需创建 ViewModel（现一次性创建 10 个）、`DividerList` 展平进父 `LazyListScope`、Sheet 内嵌套 `verticalScroll` 冲突、`TodayScreen` 的 `items` key 字符串拼接（多 PRN 项可能撞 key）、若干 `derivedStateOf` / `remember` 打磨项

  #### 规划文档需求缺口（2026-09-18 逐条核对）

> **来源**：`<旧工作区>/patient-health-plan/patient-health-plan-v3.html`（V3.0，权威）+ `patient-health-plan-v2.html`（= V2.1 final 正文）+ `archive/patient-health-plan-v2.1-final-20260829.html`，对照代码 v1.0.25。
> **方法**：全文提取 R 编号项 / M0~M10 模块清单 / 阶段 2 之后项 / 本期不做项，逐条 grep 类名·DAO·实体·字段·字符串资源核实；下表关键项已人工二次复核（标 ✅ 者为已复核）。
> ⚠️ 本节是**需求侧缺口**，与上面「审查报告类待办」性质不同：多为「规划过但未实现」，非缺陷。

**A. 安全相关（建议优先，✅ 均已复核）**

| # | 缺口 | 核实依据 |
|---|---|---|
| A1 ✅ | **紧急卡缺「当前用药」**——规划 v3 M7 要求「过敏史 / 血型 / 用药 / 诊断」，实际急救卡上看不到患者正在使用的免疫抑制剂 / 生物制剂 | `Profile.emergency_med_summary` 全库仅命中定义处，**无任何读写**；`ReportRepository.emergencyCard()` 只取 profile + contacts + emergency 类 KB 条目，**不查药单**；`ReportPdfWriter.writeEmergencyCard` 也不打印药单 |
| A2 ✅ | **备份无「恢复码」**——口令遗忘即数据永久不可恢复（规划中恢复码是砍掉设备层后仅存的两层密钥之一） | grep `恢复码` / `recoveryCode` 零命中 |
| A3 ✅ | **KDF 为 PBKDF2 而非规划写的 Argon2id**——抗暴力破解强度弱于计划（PBKDF2 本身可接受，属实现偏差非漏洞） | `VaultCipher.kt` 注释自承「后续 Argon2id 升级」 |

**B. 功能缺口（规划写了、代码里没有）**

| # | 缺口 | 规划出处 |
|---|---|---|
| B1 ✅ | **AS 专项体征**（胸廓扩张度 / 枕墙距 / 指地距 / Schober）——R26 只做了一半（情绪睡眠疲劳已做），而这恰是医生最关注的 AS 功能指标 | R26 / M6；grep `胸廓`/`枕墙`/`指地`/`Schober` 在 java 侧零命中（仅存在于 `kb_seed_*.json` 正文） |
| B2 ✅ | **知识库「个人备注层」**——v3 明文「双层结构（只读种子层 + 个人备注层）…不变」，实际只有种子层 | v3 §1 K / v2 R14；grep `kb_notes` / `个人备注` 零命中 |
| B3 | 推荐食谱库（按抗炎 / 胃肠友好 / 控热量标签，可筛选收藏、可自建） | M2 / R04；只有忌口清单，无食谱实体与界面 |
| B4 | 周报 / 月报（本机查看的结构化小结）——R11 四要素（依从统计 / 趋势图 / 周月报 / 复诊报告）缺这一项 | R11 / M9；grep `周报`/`月报` 零命中，报表仅「概览 / 趋势 / 导出」三页签 |
| B5 ✅ | 多源提醒（运动 / BASDAI 问卷 / 复诊）——M10 四源只实现用药 | `ReminderScheduler.rescheduleAll(context, meds)` 只遍历药单；全部调用点只传药单 |
| B6 ✅ | 紧急卡「锁屏紧急信息集成」+「钥匙扣二维码模板」——R19 三项只落打印版 PDF | grep `锁屏`/`二维码` 零命中；Manifest 无相关组件 |
| B7 | 4–12 周周期康复计划模板（按周递进 + 完成度追踪）——`exercise_plans` 表不存在 | M4；且 `kb_seed_exc.json` 已 app_link 引用该表（挂点悬空，见 D） |
| B8 | 免打扰时段 + 同时间段多条提醒合并推送 | M10；零实现 |
| B9 | 提醒升级链第三级「强提醒（全屏 / 横幅）」——只落「未确认 → 重复提醒」两级 | v3 M10；无 `fullScreenIntent`，`MAX_ESCALATION` 到 2 即止 |
| B10 | 复诊「结果照片归档」（化验单 / 影像报告拍照存档与检索） | M6；现由 AI 文本导入替代，无图片存储 |
| B11 | 营养素「每日上限警示」+「与用药时间错开提醒」+「与 M1 服药时间表合并视图」 | R05 / M3；`dose` 为自由文本、`take_with_food` 未被任何提醒逻辑读取 |
| B12 ✅ | 极简模式状态机（连续 3 天核心记录未完成 → 询问原因 → 身体不适/住院切极简模式） | 红线三；`Profile.ui_mode` 字段空转（仅 Entity 定义处），`minimal_since` 字段不存在，无判定 / 询问 / 切换 |
| B13 ✅ | 生活方式画像（吸烟 / 职业久坐时长 / 运动习惯 / 睡眠）→ 驱动 M4 处方个性化 | M0；`Profile.lifestyle` 字段空转，未采集未展示 |

**C. 弱化实现（有字段或半套流程，未达规划口径）**

| # | 缺口 | 现状 vs 规划 |
|---|---|---|
| C1 | 骶髂关节影像分期 | 规划 M0 要求（诊断信息全量），`Profile` 无该字段 |
| C2 | 补剂（营养）依从统计 | v3 M9 要求「用药 / 营养 / 运动」三类，`ReportRepository.overview()` 只统计用药 + 运动 + 症状 |
| C3 | 化验「按单位分组 + 跨院数据仅供参考提示」 | R24；单位与参考范围录入已有，分组与跨院提示无 |
| C4 | 复诊前准备清单（自动打包 + **空腹等抽血准备提示**） | M6；以「复诊报告 PDF」替代了清单，无空腹等准备提示 |
| C5 | 停药 / 漏服原因枚举 | 规划「感染发热 / 准备手术 / 经济原因」「遗忘 / 外出 / 药物用完」，实际枚举为另一套（`StopReason` 6 项 / `SkipReason` 5 项） |
| C6 | 服药三态「固定 / PRN / **减量中**」+「医生批准的减量方案不触发停药警示」 | 只有固定 + PRN，无减量中态 |
| C7 | 漏服与延迟处理指引（口服按通用补服规则、注射「窗口期内尽快补注 / 超窗联系医师」分级） | 注射只有「顺延」，无补服规则与超窗分级 |
| C8 | 久坐每 30–45 分钟起身提醒、姿势 / 睡姿建议、晨僵时长驱动起床热身序列 | M4；晨僵仅作处方页「判读依据」展示，`ExerciseEngine.todayPlan()` 不吃晨僵 |
| C9 | 体重「目标区间提示」 | M2；体重 / BMI / 趋势已有，目标区间无 |
| C10 | 生物制剂续方提醒 + 结核 / 乙肝 / 丙肝筛查初始节点 | M6；靠用户自建 `CheckupItem`，无内置种子 |
| C11 | 电池白名单 / 自启动引导（仅一行文字提示，无跳转按钮）、提醒自检缺「写入测试提醒验证」 | M10 |
| C12 | 全局免责声明（显著位置 / 首启声明）+ 自动提示统一前缀「仅供参考，以主治医师医嘱为准」 | 现仅条目级与 PDF 页脚声明 |

**D. 质量项：知识库种子挂点悬空**

知识库种子的 `app_link` 已引用**代码中不存在的表 / 字段**，用户点进去会看到指不到实处的提示。已知：`exercise_plans.week_structure`、`exercise_plans(stage_mode=flare)`（→ B7）、「与 `body_measures` 胸廓扩张度趋势联动」（→ B1）、「profile 吸烟状态登记后知识库置顶」（→ B13）。修法二选一：补齐实现，或改写种子文案。

**E. 歧义项，需需求方拍板**

- **R16 批量数据导入**（忌口 / 运动计划 / 知识库内容的 CSV / JSON 模板 + 导入预检）：v2.1 定为 P1，但 **v3 需求表已不继承**（R14/R15/R16 三条均未继承，其中 R14 并入 K、R15 并入 R20）。按 v3 可判「不在本期范围」。现状只有 AI 模板粘贴解析（限化验 / 影像）与档案明文 JSON 导入，**无 CSV 文件选择、无模板、无导入预检**。

**F. 明确「取消 / 不做 / 后移」——不是缺口，勿重复盘**

M8 家属协作全部（家属端 / 共享子集 / 设备令牌 / 命令协议 / 冲突裁决 / 批量裁决界面）、R13 + M11 打卡连击与阶段目标激励、R23 豁免日（`exempt_days` 删表）、M2 三餐打卡（`diet_logs` 删除）、WebDAV 对外升级通知家属（升级链只在本机）、共享子集格式 / 命令记录协议 / 令牌吊销 / 冲突恢复走查 CR-1·3·6、iOS、紧急警报短信通道；知识库完整编辑器与内容导入导出「后移阶段 2 之后」。另有 v2 §1.7 系统边界六条（不做诊断决策 / 不做医生侧 / 不上架商店 / 无账号与自建服务器 / 不做医疗器械级监测 / 无社交商业），以及「安全内容主治医师线下过目」属流程项非代码项。

- **已关闭的旧待办**：
  - UI 改版方案未完成 PR 项——已核实唯一可确认编号的 PR4（文案资源化）在 v1.0.10 完成，方案原文已不在工作区
  - 知识库搜索 FTS4/索引（审查报告 P1）——v1.0.21 以「单列检索文本 + 防抖 + 上限」结项；**FTS4 方案经实测否决**（对中文子串零命中，详见 `domain/KbSearch.kt` 注释与 CHANGELOG v1.0.21）
  - `as MutableStateFlow` 强转（审查报告 P2）——v1.0.22 清除全部 44 处，VM 状态流统一「私有 `_xxx` 可变 + 公开只读」
  - 化验 Tab 分组折叠——v1.0.23 落地（有异常或最近一次的日期默认展开，其余收起；`rememberSaveable` + 稳定 item key 保持状态）
  - VM / 通知 / PDF 硬编码文案（v1.0.10 §遗留）——v1.0.24 三层共 124 条下沉 strings.xml（`notif_` / `vm_` / `pdf_` 前缀）。**边界**：`data/` 与 `domain/` 层的异常消息与领域标签保持硬编码——domain 层按设计纯 JVM 无 Context，且那些是数据/提示词而非界面文案
- **测试基线**：114 条单测全绿；新增功能须同步补测（`app/src/test/.../`，7 个测试文件覆盖 backup / 加密 / 通用名键 / 运动分级 / 导入解析 / 排程计算 / 知识库检索）
