# ASHKB 开发交接文档

> 本文档面向接手本仓库开发的 AI 会话（TraeWork Code 模式 / TraeCode）或人类工程师。
> 记录截至 **v1.0.8**（远端 `35fe1fd`，2026-09-17）的全部工程知识。
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

- 全量构建约 2~3 分钟；**86 条单测**必须全过才算交付
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

1. WSL 提交：`git add -A && git commit -m "..."`，记录 `git log -1 --format="%H %T"`
2. PowerShell 脚本经 gh API 操作（**必须在当前 shell 内 dot-source 执行，禁止 `powershell -File` 子进程**——子进程里 `gh api --input -` 的 stdin 会损坏导致全部 400）：
   - `GET /repos/weante/ashkb/git/ref/heads/main` 取远端 HEAD，校验是预期的父提交
   - 逐文件 `POST /git/blobs`（content=base64）→ `POST /git/trees`（base_tree=远端 HEAD 的 tree）→ `POST /git/commits` → `PATCH /git/refs/heads/main`
   - **tree SHA 必须与本地 commit 的 tree 一致**，否则说明文件清单有漏
3. 本地对齐：GitHub API 生成的 commit 对象与本地 `git commit` SHA 不同。在 WSL 用 `printf`（消息**不带结尾换行**，时区 **+0800**，与 API 传入的 date 偏移一致）构造同款对象，`git hash-object -t commit -w --stdin` 写入后 `git update-ref refs/heads/main` 和 `refs/remotes/origin/main` 对齐
4. Release：`gh.exe release create vX.Y.Z --target <远端sha> --title "..." --notes-file <md>` + `gh.exe release upload vX.Y.Z <两个APK> -R weante/ashkb --clobber`

**网络偶发 500/超时重试即可**；release create 报 422 "tag already exists" 说明其实已成功。

## 6. 代码结构与约定

```
app/src/main/java/com/ashkb/app/
├── data/
│   ├── backup/     # BackupEngine（全量加密备份）、WebDavClient、VaultCipher(Keystore AES/GCM)
│   ├── db/         # AppDatabase（Room，version=7）、DAO
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

- **最新版**：v1.0.8（versionCode 13），远端 `35fe1fd`，GitHub Release v1.0.8 已发布附两 APK
- **数据安全**：v1.0.6 起 WebDAV 凭据 Keystore 加密、备份口令化、事务化写入，均已稳定
- **待办池**（用户视角，无承诺）：
  - UI 改版方案（附件《ASHKB-UI改版方案.md》）中未完成的 PR 项
  - 化验 Tab 若历史数据继续增长可考虑分组折叠优化
- **测试基线**：86 条单测全绿；新增功能须同步补测（`app/src/test/.../`，6 个测试文件覆盖 backup/domain/导入解析/排程计算）
