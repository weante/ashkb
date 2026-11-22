# 依赖 / R8 / APK 体积评估（只读）

> 日期：2026-11-22 · 代码基线：`2a2934b`（四阶段依赖升级收官；之后只有文档提交，HEAD 以 `git log -1 --oneline` 现查为准）
> 性质：**只读评估**，本文不改变任何构建配置。除明确标注「历史基线实验」的数字外，其余均为当前 release 构建的本机实测。
> 对象：`app/build/outputs/apk/release/app-release.apk`，**5,572,961 B**；构建配置见 `app/build.gradle.kts` 与 `gradle/libs.versions.toml`。

## 0. 结论

- 当前 APK **5,572,961 B（5.32 MiB）**，其中 `classes.dex` **74.54%**、`resources.arsc` **19.72%**、`assets`（压缩后）**3.37%**、`lib` **0.67%**。`dex + resources.arsc` 合计 **94.26%**。
- 四阶段依赖升级已完成：androidx.test → Gradle 8.13 / AGP 8.13.2 / compileSdk + targetSdk 36 → Compose BOM 2026.06.01 → Kotlin 2.2.20 / KSP2 / Room 2.8.5；**没有发现「改一行就省一大块」的开关**。
- 历史基线实验里唯一有效的 R8 选项 `-repackageclasses ''` 只省 **0.60%**，且带反射与序列化风险；当前基线未重跑该实验，结论仍是**不做**（见 §3）。
- 结构性成本是 **Compose**（R8 映射类 2,625 / 4,940 = **53.1%**）与**中英双语字符串池**（aapt2 统计 `string` 1,886 个名字 × 2 语言），两者都是产品与框架成本，不是缺陷。
- 真正值得做的是**真机回归**（手机返修后）与**尚未纳入四阶段的依赖**，而不是砍体积。

## 1. 实测口径（可复算）

```
. E:\ASHKB\build-env\env.ps1
gradle -p E:\ASHKB\patient-health-app assembleRelease --console=plain
# APK: app/build/outputs/apk/release/app-release.apk
# 构成：Python zipfile 逐条读 ZipEntry.file_size / compress_size
# 资源：aapt2 dump resources app-release.apk
# dex 头：从 classes.dex 读取 header（string_ids / type_ids / proto_ids / field_ids / method_ids / class_defs / file_size）
# 映射类行正则：^(\S[^ ]*) -> (\S+):$
# 资源行正则：\bresource 0x[0-9a-fA-F]+ (\S+)/
```

⚠️ 计数陷阱：R8 mapping 中字段行与方法行同样含 ` -> `，不能用它直接数类；类行末尾带 `:`。aapt2 的 `resource 0x...` 行前有缩进，不能用行首锚点。`classes.dex` 的 `class_defs_size` 与 mapping 类行数不是同一口径，不可混用。

## 2. 体积构成

| 条目 | 文件数 | 未压缩 | 压缩后 | 占 APK |
|---|---:|---:|---:|---:|
| `classes.dex` | 1 | 4,153,824 | 4,153,824 | **74.54%** |
| `resources.arsc` | 1 | 1,099,088 | 1,099,088 | **19.72%** |
| `assets/` | 30 | 1,770,525 | 187,740 | 3.37% |
| `lib/` | 4 | 37,392 | 37,392 | 0.67% |
| `META-INF/` | 79 | 61,727 | 22,182 | 0.40% |
| `kotlin/` | 8 | 51,125 | 12,053 | 0.22% |
| `AndroidManifest.xml` | 1 | 11,204 | 2,807 | 0.05% |
| `res/` | 9 | 7,148 | 2,802 | 0.05% |
| `DebugProbesKt.bin` | 1 | 1,728 | 777 | 0.01% |
| `kotlin-tooling-metadata.json` | 1 | 625 | 283 | 0.01% |

ZIP 条目合计：未压缩 **7,194,386 B**、压缩后 **5,518,948 B**；与 APK 5,572,961 B 的差额是 ZIP 目录、对齐与签名块等封装开销。`classes.dex` 与 `resources.arsc` 都是未压缩存储，体积几乎全在这两块。

## 3. dex：4.15 MB

当前 `classes.dex` 头：

- `string_ids_size = 16293`
- `type_ids_size = 5184`
- `proto_ids_size = 6082`
- `field_ids_size = 16258`
- `method_ids_size = 21431`
- `class_defs_size = 4270`
- `file_size = 4153824`

单 dex，方法数远低于 64K 上限，**不需要 multidex**。

R8 mapping 的类行共 **4,940** 条，按来源分：

| 来源 | 类数 | 占比 |
|---|---:|---:|
| `androidx.compose.*` | 2,625 | 53.1% |
| `com.ashkb.app`（自有） | 981 | 19.9% |
| `kotlin` / `kotlinx` | 602 | 12.2% |
| `androidx.core` | 139 | 2.8% |
| `androidx.navigation` | 101 | 2.0% |
| 其余（lifecycle / activity / collection / room / emoji2 …） | 492 | 10.0% |

mapping 记录的是被 R8 处理过的类，包含未进入最终 dex 的输入类，所以 4,940 与 dex 头的 `class_defs_size = 4270` 不冲突；前者用于看来源构成，后者用于看最终体积。

**图标**：源码当前实际引用 **52 个不同图标**；`material-icons-extended` 的未用部分由 R8 裁掉，dex 里没有成片图标类。

**历史基线实验（升级前、已回滚、未在现基线重跑）**：在旧基线的 `proguard-rules.pro` 追加 `-repackageclasses ''` + `-allowaccessmodification` 后，APK 只缩小 **0.60%**。收益太小，且会把保留类压进默认包，与 `-keep class com.ashkb.app.data.entity.**`、kotlinx-serialization 的 `$$serializer` 查找、Room 生成代码存在交互风险，需要真机备份 / 恢复往返验证。**结论：不做。**

## 4. resources.arsc：1.10 MB

`aapt2 dump resources` 统计 **2,095** 个资源条目：

| 类型 | 条目数 |
|---|---:|
| `string` | 1,886 |
| `id` | 123 |
| `attr` | 70 |
| `style` | 5 |
| `drawable` | 4 |
| `xml` | 4 |
| `mipmap` | 1 |
| `color` | 1 |
| `integer` | 1 |

- 1,886 个字符串名 × 2 个语言（`values` / `values-en`），是 `resources.arsc` 的主要重量来源，也是产品需求（`EnglishStringsParityTest` 强制逐条对齐）。
- 资源收缩已经把 drawable 压到 4 个、style 压到 5 个，**没有可再裁的资源**。
- 砍英文语言包可省约一半 arsc（≈0.5 MiB），但这是产品决策，不在工程侧建议内。

## 5. assets：未压缩 1.73 MB / 压缩后 183 KB

30 个文件，按目录分：

| 分组 | 文件数 | 未压缩 | 压缩后 |
|---|---:|---:|---:|
| Room schema JSON（v4–v21，缺 1–3） | 18 | 1,527,334 | 103,270 |
| 英文种子 JSON（`assets/en/`） | 5 | 129,385 | 38,805 |
| 中文知识库种子 JSON（`assets/kb_seed_*.json`） | 5 | 108,191 | 40,050 |
| `dexopt`（baseline profile） | 2 | 5,615 | 5,615 |

中文种子分项：`edu` 14,443→6,117 / `emr` 10,729→4,635 / `exc` 35,970→11,608 / `fdg` 11,620→4,637 / `itx` 35,429→13,053。`dexopt` 内 `baseline.prof` 5,005 B、`baseline.profm` 610 B，均未压缩。

- schema 进 **main** assets 是维护者的明确取舍：让 `MigrationTestHelper` 能在 JVM 单测里跑「从旧版本升级」。未压缩 1.53 MB 看着大，**压缩后实际只占 101 KB**，性价比高于注释里的旧估算。
- 只保留最近 2 版 schema 可省约 90 KB，但 `MigrationPathProofTest` 的 v1→v21 / v2→v21 链会断，**不建议**。
- 种子 JSON 是离线知识库的内容来源，压缩后合计 79 KB，不动。

## 6. lib/：37 KB

4 个 ABI 的 `libandroidx.graphics.path.so`（Compose UI 路径绘制）：

| ABI | 字节 |
|---|---:|
| arm64-v8a | 10,096 |
| armeabi-v7a | 7,252 |
| x86 | 9,284 |
| x86_64 | 10,760 |

用 `abiFilters` 只留 arm64 可省约 27 KB（0.5%），但会让 x86 模拟器与 32 位老设备装不上。**收益可忽略，不建议**。

## 7. 依赖现状（四阶段升级后）

| 依赖 | 当前固定版本 |
|---|---|
| Gradle | 8.13（wrapper 与 CI 同步） |
| AGP | 8.13.2 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 31 |
| Kotlin（含 compose / serialization 插件） | 2.2.20 |
| KSP | 2.2.20-2.0.4 |
| Room | 2.8.5 |
| Compose BOM | 2026.06.01（UI / Foundation / Runtime / Animation 1.11.4、Material3 1.4.0） |
| androidx.test ext:junit / runner | 1.3.0 / 1.7.0 |
| detekt | 1.23.7（实测兼容 Kotlin 2.2.20） |

四阶段**未动**的版本：`activity-compose 1.9.2`、`lifecycle 2.8.6`、`core-ktx 1.13.1`、`navigation-compose 2.8.4`、`kotlinx-serialization 1.7.3`、`json 20240303`。它们不在本轮门禁验证范围内，升级需另排期并跑全套门禁 + 真机回归，不能按「低风险」直接改。

Compose BOM 止于 2026.06.01：2026.08.00 / 09.00 / 11.01 的 AAR 要求 `compileSdk 37` 与 AGP 9.1.0，跨到那一档是另一次工具链升级，风险量级不同。AGP 9.x 也未在本轮采用。

依赖走用户级 `~/.gradle/init.gradle` 的镜像；升版本前需确认镜像已同步目标版本，否则会卡在解析阶段而不是编译阶段。

## 8. R8 规则审查（`app/proguard-rules.pro`）

| 规则 | 目的 | 判断 |
|---|---|---|
| `-keep class com.ashkb.app.data.entity.** { *; }` | 备份按 `PRAGMA table_info` 列名读写，payload 按字段名拼 JSON | **仍必要**（30 个 `@Entity`）。可收窄成 `-keepclassmembers` + `-keepnames`，估计省 <100 KB，但要真机跑备份 / 恢复 / 知识库导入再改，收益不值风险 |
| `-dontwarn org.bouncycastle.**` | 历史遗留 | 升级前基线查得 classpath 0 命中；升级后未重新核验。若清理，先复跑依赖解析确认，再按纯配置卫生处理；不影响体积 |
| kotlinx-serialization 的 `$$serializer` / `Companion` / `serializer(...)` 三条 | 类型安全导航路由（`@Serializable`） | 必要；删了会在运行时导航崩溃 |
| `-keep class com.ashkb.app.domain.ExercisePlanTemplates*` / `ExercisePlanProgress*` | 领域单例防御性保留 | 代价小，保留 |
| `-keepattributes *Annotation*, InnerClasses, Signature` | kotlinx.serialization 反射查找 | 必要 |

其余（Room / Compose / Navigation）由各自 consumer rules 覆盖，`assembleRelease` 通过、无 R8 警告。

## 9. 结论与建议（按性价比排序）

1. **不做** `-repackageclasses`：历史基线实测仅 −0.60%，当前基线未重跑，反射 / 序列化风险不值。
2. **不做** 剪 schema、ABI 过滤、替换图标库、去英文：省 30~100 KB 级别，代价是测试覆盖或产品能力。
3. **未纳入四阶段的依赖**只在有明确需求时单独排期；每次只升一组，跑全套门禁 + 真机回归。
4. 若目标是**启动更快**而不是包更小，下一步应做 **Baseline Profile**：当前 `baseline.prof` 只有 5,005 B，需真机采集，另立项。
5. 若继续压缩 `resources.arsc`，应先做产品决策（是否保留英文），不是工程侧可自行改的事。

## 10. 未验证与边界

- 数字来自本机 Windows release 构建；CI 同源构建可能在签名块与打包顺序上差几十字节，不代表内容差异。
- 未测安装后占用、启动耗时、内存；本文只谈 APK 体积与依赖版本。
- 手机送修中，**未做真机回归**。targetSdk 36 的返回手势 / edge-to-edge、Compose 1.11 的滚动与弹层、Android 16 通知权限，仍需实机确认。
- 本文为只读评估：`app/proguard-rules.pro`、`app/build.gradle.kts`、`gradle/libs.versions.toml` 均未改动。
