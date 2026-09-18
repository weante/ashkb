# ASHKB 开发交接文档

> 本文档面向接手本仓库开发的 AI 会话（TraeWork Code 模式 / TraeCode）或人类工程师。
> 记录截至 **v1.0.20**（versionCode 25，2026-09-18）的全部工程知识。
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

- 全量构建约 2~3 分钟；**107 条单测**必须全过才算交付
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
│   ├── db/         # AppDatabase（Room，version=8）、DAO
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

## 8. 当前状态与下一步

- **最新版**：v1.0.20（versionCode 25），GitHub Release v1.0.20 已发布附两 APK（release 1.98 MB / debug 16.9 MB），发布点 commit `cd7f989`
- **数据安全**：v1.0.6 起 WebDAV 凭据 Keystore 加密、备份口令化、事务化写入，均已稳定；v1.0.19 起支持登录 WebDAV 后直接拉取远程备份列表选择恢复（新机无需先生成本地备份）；v1.0.20 起备份文件名带时间戳，同天多份不互相覆盖
- **待办池**（用户视角，无承诺）：
  - 知识库搜索现为 `title / summary / payload LIKE '%q%'` 全表扫（`data/db/Daos.kt`），数据量增长后拟迁 Room FTS4 虚表，或至少对 title 建索引并限制 LIKE 前缀（代码审查报告 P1 未完成项）
  - `as MutableStateFlow` 强转约 44 处（审查报告 P2）：可改为私有 `MutableStateFlow` + 只读 `StateFlow` 暴露
  - 化验 Tab 若历史数据继续增长可考虑分组折叠优化（现为异常置顶 + 正常项折叠 + 翻页，v1.0.7 已做）
  - WebDAV 非标准方法（PROPFIND / MKCOL）依赖反射改 `HttpURLConnection` 内部字段：换 Android 15 / 16 真机需回归验证（无替代方案，属设计取舍）
  - ViewModel 层 snackbar 文案 / NotificationHelper 通知文案 / PDF 直绘文本仍为硬编码（需注入 context，模式与 UI 层资源化不同，v1.0.10 §遗留）
- **已关闭的旧待办**：UI 改版方案未完成 PR 项——已核实唯一可确认编号的 PR4（文案资源化）在 v1.0.10 完成，方案原文已不在工作区
- **测试基线**：107 条单测全绿；新增功能须同步补测（`app/src/test/.../`，6 个测试文件覆盖 backup / 加密 / 通用名键 / 运动分级 / 导入解析 / 排程计算）
