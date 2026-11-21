# v1.2.19 依赖 / R8 / APK 体积评估（只读）

> 日期：2026-11-21 · 基线源码：`main` @ `8c46e97`（v1.2.19 + lint 清零 + CI 提速）
> 性质：**只读评估**，本文不改变任何构建配置。文中所有数字都是本机实测，命令见 §1。
> **2026-11-22 补记**：四阶段依赖升级后 release APK 实测 **5,572,961 B**（5,431,160 → 5,247,592 工具链 → 5,568,309 Compose BOM → 5,572,961 Room/Kotlin/KSP2）。以下数字仍是升级前基线，保留作为方法学示例；当前构建配置以 `README.md` / `app/build.gradle.kts` 为准，不要再按旧数估算。

## 0. 结论

APK **5,431,160 B（5.18 MiB）**，其中 `classes.dex` 74.6%、`resources.arsc` 19.9%、`assets`（压缩后）3.4%。
没有找到「改一行就省一大块」的空间：唯一实测有效的 R8 选项 `-repackageclasses` 只省 **0.6%**，且带反射风险。
结构性成本是 **Compose（占 R8 保留类的 50%）** 与 **中英双语字符串池（1885 个 string × 2 语言）**，两者都不是缺陷。
真正值得做的不是砍体积，而是**依赖升级排期**（§7）。

## 1. 实测口径（可复算）

```
. E:\ASHKB\build-env\env.ps1
gradle -p E:\ASHKB\patient-health-app assembleRelease --console=plain
# APK: app/build/outputs/apk/release/app-release.apk
# 构成：用 System.IO.Compression 逐条读 ZipEntry 的 Length / CompressedLength
# 资源：aapt2 dump resources app-release.apk
# 类：dexdump -f classes.dex（class_defs_size / method_ids_size / string_ids_size）
# 映射：app/build/outputs/mapping/release/mapping.txt
```

## 2. 体积构成

| 条目 | 字节 | 占 APK | 备注 |
|---|---:|---:|---|
| `classes.dex` | 4,049,092 | **74.6%** | 单 dex；未压缩存储（AGP 默认，换启动速度） |
| `resources.arsc` | 1,082,736 | **19.9%** | 未压缩存储 |
| `assets/`（30 项） | 1,770,098 → 压缩后 187,313 | 3.4% | 见 §5 |
| `lib/`（4 项） | 37,392 | 0.7% | 见 §6 |
| `kotlin/` 内建元数据 | 29,945 → 压缩后 10,468 | 0.2% | Kotlin 反射用的 builtins |
| `AndroidManifest.xml` | 10,828 → 2,718 | 0.05% | |
| `res/`（9 项） | 7,048 → 2,783 | 0.05% | 资源收缩后只剩 9 个文件 |
| `DebugProbesKt.bin` | 1,738 | 0.03% | kotlinx-coroutines 调试探针残留 |
| `kotlin-tooling-metadata.json` | 624 | 0.01% | 构建元数据 |
| `META-INF/`（58 项） | 573 | 0.01% | 签名块 |

`classes.dex + resources.arsc` 合计 **94.5%**，且两者都是**不压缩存储**——APK 从 5.43 MB 压到 5.18 MiB 的空间几乎全在这两块。

## 3. dex：4.05 MB

- `class_defs_size = 4475`、`method_ids_size = 21997`、`string_ids_size = 15660`（单 dex，方法数远低于 64K 上限，**不需要 multidex**）。
- R8 映射表登记 **5021 个类**，按来源分：

| 来源 | 类数 | 占比 |
|---|---:|---:|
| `androidx.compose.*` | 2529 | 50.4% |
| `com.ashkb.app`（自有） | 1264 | 25.2% |
| `kotlin` / `kotlinx` | 632 | 12.6% |
| `androidx.core` | 124 | 2.5% |
| `androidx.navigation` | 92 | 1.8% |
| 其余（lifecycle / activity / collection / room / emoji2 …） | 380 | 7.6% |

**Compose 是 dex 的一半**，这是 Compose 应用的正常水位（runtime + ui + foundation + material3 各自都有大量内联 lambda 与状态机）。`material-icons-extended` 虽然整包很大，但 R8 已经裁掉了未使用的图标：源码只 import 了 **55 个不同图标**（120 条 import，114 rounded + 6 outlined），dex 里没有成片的图标类。

**实测实验（已回滚，未入库）**：在 `proguard-rules.pro` 追加 `-repackageclasses ''` + `-allowaccessmodification` 后重新 `assembleRelease`：

| | dex | APK |
|---|---:|---:|
| 现值 | 4,049,092 | 5,431,160 |
| `-repackageclasses ''` | 4,019,724（−29,368） | 5,398,380（−32,780，**−0.60%**） |

收益太小，而 `-repackageclasses` 会把所有保留类压进默认包，和现有的 `-keep class com.ashkb.app.data.entity.**`、kotlinx-serialization 的 `$$serializer` 查找、Room 生成代码都存在交互风险，需要真机备份/恢复往返验证。**结论：不做。**

## 4. resources.arsc：1.08 MB

`aapt2 dump resources` 统计：

| 类型 | 条目数 |
|---|---:|
| `string` | 1885 |
| `id` | 89 |
| `attr` | 39 |
| `drawable` / `xml` / `style` | 各 4 |
| `mipmap` / `color` / `integer` | 各 1 |

- 1885 个字符串名 × 2 个语言（`values` / `values-en`），共约 3770 条值——这是 `resources.arsc` 的主要重量来源，也是**产品需求**（中英双语，`EnglishStringsParityTest` 强制逐条对齐）。
- 资源收缩已经把 drawable 压到 4 个、style 压到 4 个（`shrinkResources=true` 生效），**没有可再裁的资源**。
- 若砍掉英文语言包可省约一半 arsc（≈0.5 MiB），但这是产品决策，不在工程侧建议内。

## 5. assets：未压缩 1.73 MB / 压缩后 183 KB

| 分组 | 文件数 | 未压缩 | 压缩后 |
|---|---:|---:|---:|
| Room schema JSON（v4–v21，缺 1–3） | 18 | 1,527,334 | 103,270 |
| 知识库种子 JSON（中英各 5） | 10 | 237,576 | 78,855 |
| baseline.prof | 1 | 4,569 | ~2 KB |

- schema 进 **main** assets 是 2026-10-01 维护者的明确取舍（`app/build.gradle.kts:96`）：让 `MigrationTestHelper` 能在 JVM 单测里跑「从旧版本升级」，代价是 APK 多约 1.2 MB（未压缩）。**压缩后实际只占 100 KB**，性价比其实比注释里写的更好。
- 可选：只保留最近 2 版 schema 可省约 **90 KB**，但 `MigrationPathProofTest` 的 v1→v21 / v2→v21 链会断，**不建议**。
- 种子 JSON 是离线知识库的内容来源，压缩后 79 KB，不动。

## 6. lib/：37 KB

4 个 ABI 的 `libandroidx.graphics.path.so`（Compose UI 的路径绘制）：

| ABI | 字节 |
|---|---:|
| arm64-v8a | 10,096 |
| armeabi-v7a | 7,252 |
| x86 | 9,284 |
| x86_64 | 10,760 |

用 `abiFilters += listOf("arm64-v8a")` 可省约 27 KB（0.5%），但会让 x86 模拟器与 32 位老设备装不上。**收益可忽略，不建议**。

## 7. 依赖新鲜度（releaseRuntimeClasspath 共 100 个模块）

当前版本与最新版（来自本轮之前的 `lintDebug` `GradleDependency` 报告）：

| 依赖 | 当前 | 最新 | 升级阻塞点 |
|---|---|---|---|
| AGP | 8.5.2 | 9.5.2 | 需要 Gradle 9 + JDK 21 工具链；AGP 9 对 `buildFeatures`/variant API 有破坏性改动 |
| Kotlin | 2.0.20 | 2.2.x | 与 KSP、Compose 编译器插件版本强绑定，需成套升 |
| KSP | 2.0.20-1.0.25 | 2.2.x-2.0.x | 跟随 Kotlin |
| Room | 2.6.1 | 2.8.5 | 2.7+ 要求 KSP2；`room.schemaLocation` 导出的 JSON 格式可能变（`verifySpecSync` 与 18 个历史 schema 都依赖它） |
| Compose BOM | 2024.09.03 | 2026.x | material3 1.3 → 1.5 有 API 弃用与行为变化（`FilterChip`、`ModalBottomSheet` 参数） |
| activity-compose | 1.9.2 | 1.13.0 | 需要 compileSdk 36 |
| lifecycle | 2.8.6 | 2.11.1 | 需要 compileSdk 36 |
| core-ktx | 1.13.1 | 1.19.1 | 需要 compileSdk 36 |
| navigation-compose | 2.8.4 | 2.10.2 | 类型安全路由的 serializer 生成方式有变化 |
| androidx.test ext:junit / runner | 1.1.5 / 1.5.0 | 1.3.0 / 1.7.0 | 仅测试侧，风险最低 |

当前 `compileSdk = 34` / `targetSdk = 34` / `minSdk = 31`。**动任何 androidx 新版都会先要求 compileSdk 36**，所以升级不是「改版本号」，而是「compileSdk + AGP + Kotlin + Compose 编译器 + KSP + Room 一起动」。

建议分四步、每步单独一个 PR 并跑全套门禁 + 真机冒烟：

1. **测试侧先行**（低风险）：`androidx.test` 两个包升级；不需要动 compileSdk。
2. **工具链**：Gradle 8.7 → 8.13 → JDK 21 → AGP 8.13（先不碰 AGP 9）→ compileSdk 36 / targetSdk 36，处理 `POST_NOTIFICATIONS`、`fullScreenIntent` 等 API 36 行为变化。
3. **Compose**：BOM 升到当前稳定版，逐个处理 material3 API 弃用；`FilterChips`（`LazyRow`）与 `ModalBottomSheet` 是重点回归对象。
4. **Room + Kotlin/KSP**：Room 2.8 + KSP2 + Kotlin 2.2 成套；重跑 `MigrationPathProofTest`、`RestoreRollbackTest`、`AttachmentCascadeDeleteTest`，并确认 `app/schemas` 导出格式未变。

**风险提示**：本机依赖解析走用户级 `~/.gradle/init.gradle` 的国内镜像（`gradle/libs.versions.toml:13`），升版本前需确认镜像已同步目标版本，否则会卡在解析阶段而不是编译阶段。

## 8. R8 规则审查（`app/proguard-rules.pro`）

| 规则 | 目的 | 现状判断 |
|---|---|---|
| `-keep class com.ashkb.app.data.entity.** { *; }` | 保留实体字段名：备份导出按 `PRAGMA table_info` 的列名读写，`org.json` 也按字段名拼 payload | **仍必要**。共 30 个 `@Entity`。可收窄成 `-keepclassmembers ... { <fields>; }` + `-keepnames`（估计省 < 100 KB），但要真机跑一遍备份/恢复/知识库导入再改，收益不值这个风险 |
| `-dontwarn org.bouncycastle.**` | 历史遗留（某依赖曾引用 bcprov） | **死规则**：`releaseRuntimeClasspath` 里 bouncycastle 出现 **0 次**。可删，纯配置卫生，不影响体积 |
| kotlinx-serialization 的 `$$serializer` / `Companion` / `serializer(...)` 三条 | 类型安全导航路由（`Routes.kt` 全部 `@Serializable`） | 仍必要；删了会在运行时导航崩溃 |
| `-keep class com.ashkb.app.domain.ExercisePlanTemplates*` / `ExercisePlanProgress*` | 纯领域单例，防 R8 激进优化 | 注释已说明是防御性保留（真因是正则/ICU 差异，与此无关），代价小，保留 |
| `-keepattributes *Annotation*, InnerClasses, Signature` | kotlinx.serialization 反射查找 | 必要 |

其余未在规则里的东西（Room / Compose / Navigation）由各自 consumer rules 覆盖，实测 `assembleRelease` 通过、无 R8 警告。

## 9. 结论与建议（按性价比排序）

1. **零风险、5 分钟**：删掉 `-dontwarn org.bouncycastle.**`（死规则）；如需再抠 2 KB，可在 `packaging` 里排除 `DebugProbesKt.bin` 与 `kotlin-tooling-metadata.json`——**收益可忽略，只做前者即可**。
2. **不做**：`-repackageclasses`（实测仅 −0.6%，反射风险）。
3. **不做**：剪 schema、ABI 过滤、替换图标库、去英文——省 30~100 KB 级别，代价是测试覆盖或产品能力。
4. **值得做的是依赖升级**（安全补丁与 API 36 合规），按 §7 的四步走，与体积无关。
5. 若目标是「启动更快」而不是「包更小」，下一步应做 **Baseline Profile**（当前 `assets/dexopt/baseline.prof` 只有 4.5 KB，是库自带的最小集合），需要真机采 profile，另立项。

## 10. 未验证与边界

- 数字来自本机 Windows release 构建（AGP 8.5.2 / JDK 17 / 无签名差异影响体积）；CI 同源构建可能差几十字节。
- 未测安装后占用、启动耗时、内存；本文只谈 APK 体积与依赖版本。
- `-repackageclasses` 的实验构建已回滚，`app/proguard-rules.pro` 与 `git status` 均为干净状态。
