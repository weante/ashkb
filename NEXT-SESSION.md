# 下次接手从这里开始（写于 v1.1.0 发布后）

**当前状态**：`v1.1.0`（versionCode **102**）已发布为 GitHub Latest、已装维护者真机；单测 **736 条全绿**；CI 连续 `success`；CHANGELOG / HANDOFF / HANDOFF-STATUS / README **四份已同步**；工作区无未提交改动。

---

## 一、第一优先级：收维护者的真机问题（攒够一批再改）

维护者从 2026-10-02 起用几天攒问题。**回来时不要再一版一版追**——v1.0.90 → v1.0.96 连出 7 个版本，其中 5 个是观感微调，每次都要他重新装机重看，效率低。

**做法**：把所有问题写成一个批次任务书（`release-tooling/batchN-brief.md`）→ 派**一个**子代理 → 复核 → 构建 → 发布 **1.1.1**。

**问清三点**（这几轮证明这三点最省时间）：哪个界面/哪个元素（**截图最好**）· 期望 vs 实际 · 属于哪类（排版 / 数字 / 找不到记录 / 闪退）。

---

## 二、三处"我没能验证"的地方（维护者若碰到，优先看）

| # | 未验项 | 为什么没验 | 怎么验 |
|---|---|---|---|
| 1 | **`MedEditScreen` 保存路径** | 拆了 9 个 section（v1.0.92），我的自动读屏导航连续失败 | 我的 → 药品 → 添加/编辑 → 填完**点保存**，看是否真存下、字段有没有丢 |
| 2 | **Wellness 下半屏**（饮食画像 / 忌口 / 时间线） | 自动滚动误触拨号盘两次，我不再用盲手势 | 健康 → 营养与骨健康 → 往下滚（**补剂区维护者已确认正常** ✅） |
| 3 | **`KeyValueRow` 新对齐**（v1.0.95/96） | 同上 | 健康档案 / 身体指标 / 饮食画像应**靠右**；周月报长值**从固定位置折行**；急救卡剂量说明**不再 6 行** |

---

## 三、A 组剩余（性能拆分）

| 屏幕 | 现状 |
|---|---|
| `ReportScreen`（**803 行**） | 报告页三个 tab 的 Flow 仍在根级 |
| `EmergencyScreen`（**822 行**） | 同上；**事件卡的同形挤压风险已在 v1.0.92 修掉** |

**照已完成的四个先例做**（`MedsScreen` 标杆 / `BackupScreen` 19→3 / `TodayScreen` 8→3 / `WellnessScreen` 10→0 / `MedEditScreen` 根级读点 24→0）。

---

## 四、小尾巴（可一次性收掉）

1. **补剂「部分完成」写入路径**：数据层已就位（`SupplementLogStatus` 覆盖 partial、依从率已按三态算），只差 UI（卡片只给「打卡 / 跳过」，与药品一致）
2. **3 条孤儿字符串**（batch 3b 报告：`report_adherence_breakdown` 等），当时为避免动范围外内容未删
3. **`HealthRepository` 一句注释**仍写"随库 v19 落地"（现已落地）
4. **`DateProvider` 广播注册**无自动化测试（需真机跨零点或改时钟）
5. **级联删除的附件分支**未真机验（行 + 磁盘文件；**19 条 Robolectric 测试**覆盖）
6. **v1.0.85 的体感收益未验**（附件同步时输密码不再被打断）——设备上**没有附件**，那 3 条 Flow 不发射

---

## 五、必须记住的硬约束（踩过的坑）

- ⚠️ **一次只跑一个 Gradle 构建**（本机多次因并发构建内存爆掉崩溃；子代理也曾因"第一条命令失败但仍启动了 Gradle"造成并发）
- **`strings.xml` 是混合换行（CRLF+LF）**：必须字节安全改写（UTF8 无 BOM 读入 → 逐条唯一匹配断言 → Replace → 同编码写回），**禁止整文件重排**
- **改版本号必须同时同步四份文档**，否则 `verifySpecSync` 会 FAILED（它**正确拦住过**一次）
- **子代理不得改**：版本号 / 四份文档 / lint+detekt 基线 / `ExerciseEngine.kt` / `DateInput.kt` / `ClinicalThresholds.kt` / `AdherenceCalc.kt`
- **`lintDebug` 0 error 是红线**；中途报出的 detekt 新问题**一律改代码、不进基线**
- **detekt 基线按「文件 + 完整签名」记账**（连 `private` 都写死）→ 被登记过的函数不能换文件、不能改形参表
- **发布流程**：`gh api -X POST repos/weante/ashkb/releases --input rel-*.json`（中文走 JSON 文件）→ `curl.exe --http1.1` 上传资产 → **核对摘要**（本地 `Get-FileHash` vs 远端 `digest`）→ 给上一版挂取代横幅（**PATCH 用 release id**）
- **GitHub 必须 HTTP/1.1**：`git -c http.version=HTTP/1.1 push origin main`
- **崩溃恢复**：`Could not read workspace metadata from ...transforms-4\...\metadata.bin` = Gradle 转换缓存被写坏 → **停守护进程 + 整个删掉 `transforms-4`** 再重建（只删单个条目无效）
- **真机读屏**：`uiautomator dump` + `adb pull`；点击要打**最近的可点击祖先**（文本节点常不可点）；⚠️ **别在含电话号码的页面上盲滑动**（已误触拨号盘两次）

---

## 六、已裁决"不做"（避免重复提）

复诊项目**物理删除**（保持"只停用"）· 化验行内胶囊 28dp 触达区（不动）· Strong Skipping 配置（2.0.20 已默认开启）· skippable 计数 CI 门（脆弱门比没有门更糟）· 迁移起点 **1/2/3 永久不可覆盖**（伪造会让测试虚假通过）· `gradlew`（官方分发 URL 本机不可达）
