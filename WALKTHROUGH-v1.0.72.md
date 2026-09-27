# v1.0.72 真机走查 · 小米通知链路归档（2026-09-27 晚）

> 承接 [WALKTHROUGH-v1.0.70.md](WALKTHROUGH-v1.0.70.md)（同日首轮走查）。本轮目标：把「小米上通知不弹横幅 / 不上锁屏 / 强提醒全屏能否生效」一次查到底。
> 配套证据文件（工作区 `release-tooling/research/`，不随仓库发布）：
> `xiaomi-adaptation-FAQ.txt`（小米官方文档正文抽取）、`pre-important-snapshot.txt`（21:26 改动前快照）、`pretest-2210-snapshot.txt`（22:10 前快照，含待触发闹钟与通道参数）、`installed-base-1072.apk`（从手机拉回的 base.apk）。

## 0. 环境与制品

| 项 | 值 |
|---|---|
| 设备 | Xiaomi 15 Pro（2410DPN6CC）· Android 16 (API 36) · HyperOS OS3.0.308.0.WOBCNXM |
| 被测制品 | **v1.0.72 发布资产本体**：手机上 `pm path` → `base.apk` 拉回后 sha256 = `ce192b1873b54f83bc3958b5a83a935d532631785dfd82a6a75f9c8a44907ed9`（2,548,326 B）**与 GitHub Release 资产逐字节一致** |
| 安装方式 | 用户**从 GitHub Release 页面自行安装**（就地覆盖，数据保留；`lastUpdateTime=2026-09-27 21:49:19`） |
| 连接方式 | 无线调试（adb over Wi-Fi）。**注意：手机锁屏/息屏后该链路会掉线**，端口亦会变化；`adb mdns services` 可重新发现端点 |

## 1. 官方依据（小米《开发最佳实践与兼容性建议（适配常见问题）》）

抓自 `dev.mi.com/docs/appsmarket/technical_docs/adaptation_FAQ/`，与本应用直接相关的四条：

| 条目 | 原文要点 | 影响的功能 |
|---|---|---|
| **§9 为什么不能在锁屏显示 Activity** | MIUI 引入**锁屏显示窗口权限控制**，**默认不能在锁屏上显示 Activity**（`FLAG_DISMISS_KEYGUARD`），需用户主动授予 | 末级强提醒全屏（v1.0.61）、锁屏紧急信息（v1.0.66） |
| **§10 如何获取某项权限是否开启** | **暂时没有这个查询接口**；可引导用户跳转应用权限管理页：`miui.intent.action.APP_PERM_EDITOR` + `CATEGORY_DEFAULT` + `extra_pkgname` | 决定「只给指引 + 跳转按钮、**不做状态行**」 |
| **§11 为什么我的 Alarm 不太精确** | Google 与 MIUI 均启用**对齐唤醒**，会把一小段时间内的 Alarm 对齐执行 | 解释走查中「末级提醒晚几分钟」，非缺陷 |
| **§12 我的应用为什么不能自启动** | MIUI 上自启动由用户控制、**默认不开放**（含开机自启动与接收系统广播）；引导 `miui.intent.action.OP_AUTO_START` | `BootReceiver` 重排闹钟 |

**MIUI 私有 appops 编号**（经开源实现交叉核对）：`MIUIOP(10020)` = 锁屏显示内容、`MIUIOP(10021)` = 后台弹出界面、`MIUIOP(10014)` = 精确闹钟，**默认均 `ignore`**。

## 2. 定论：不弹横幅 + 不上锁屏的真正开关

**结论：两者都出在小米「每通道」设置页，且都默认不给第三方应用开。**

> 设置 → 应用设置 → ASHKB → 通知管理 → 锁屏紧急信息
> ① 打开 **「悬浮通知」** ⇒ **横幅恢复**
> ② 「**在锁定屏幕上**」→「**显示通知及其内容**」 ⇒ **锁屏恢复**
>
> 用户 2026-09-27 依次打开，两项均恢复正常。

**为什么 App 侧改不动**：`NotificationHelper` 自 v1.0.66 起就设了 `lockscreenVisibility = PUBLIC`，但 `dumpsys notification` 里该通道**改动前后始终是 `mLockscreenVisibility=-1000`（NO_OVERRIDE）**——MIUI 把每通道这两项存在**它自己的存储**里，既不读也不回写 AOSP 通道字段（通道 `mImportance=4` 亦为 MIUI 自行提升，`mOriginalImp=2`）。
⇒ **判据**：通道参数与通知 `vis=PUBLIC` 全部正确却既不弹横幅又不上锁屏 ⇒ 别再查通道参数。

**背景机制（非决定性闸门）**：MIUI 的「通知过滤」分级 FBO——`settings get secure KEY_FBO_DATA` 里 `level1/2/3` 各 9 个包并带轮换时间戳，不在名单内的包按「不重要」处理（本应用即属此类），SystemUI 对应日志 `"No heads up: unimportant notification:"`，并折叠进 `tag=UNIMPORTANT` 聚合。但**通道页开关可直接覆盖它**。

## 3. 结算：末级强提醒全屏在小米上闭环

2026-09-27 **22:10** 实测（v1.0.72 装机后，通道开关已打开）：

| 证据 | 内容 |
|---|---|
| 闹钟被消耗 | 22:10 的 esc=2 闹钟从待触发列表消失（`dumpsys alarm`） |
| 通知记录挂 FSI | `NotificationRecord(pkg=com.ashkb.app … channel=med_reminders importance=4 flags=AUTO_CANCEL\|HIGH_PRIORITY category=reminder actions=1)` + **`fullscreenIntent=PendingIntent{… com.ashkb.app startActivity (allowlist: …/NotificationManagerService)}`** |
| 用户可见 | **22:10 亮屏全屏**：「该服药了 / 测试-全屏 1000mg / 计划时间 21:10」+「已服用」「稍后处理」 |

⇒ **MIUI 的通知过滤不会压掉 `fullScreenIntent`**；v1.0.61 强提醒链（0 → +30 → +60 末级）在小米上完整跑通。
⇒ **附带结论**：用户在系统里打开的通道开关**扛住了版本升级**（v1.0.71 → v1.0.72 覆盖安装后仍生效）。

## 4. 小米用户装机必做（已写入 `HANDOFF.md` §9.1）

1. 电池优化白名单
2. **「设置 → 应用设置 → ASHKB → 通知管理 → 〈通道〉」：打开「悬浮通知」+ 把「在锁定屏幕上」设为「显示通知及其内容」**
3. 「权限管理 → 其他权限」：打开 **锁屏显示**、**后台弹出界面**，并把 **自启动** 打开
4. 若需强提醒全屏：自检页三项（通知 / 精确闹钟 / 强提醒）全绿后，**锁屏等待**（解锁状态只会出横幅，属 AOSP 既定语义）

## 5. 复现命令（下次直接用）

```powershell
# 设备与制品
adb devices                                        # 锁屏后掉线属正常；adb mdns services 重新发现端点
adb shell pm path com.ashkb.app                    # → pull 回 base.apk，比对发布资产 sha256

# MIUI 侧现状
adb shell cmd appops get com.ashkb.app             # MIUIOP(10020)/(10021) 是否 allow
adb shell cmd appops get com.ashkb.app USE_FULL_SCREEN_INTENT   # 看 Uid mode，不是 granted=
adb shell settings get secure KEY_FBO_DATA         # 通知过滤分级名单
adb shell "dumpsys notification --noredact | grep -E 'pkg=com.ashkb.app|tag=UNIMPORTANT'"   # 通道参数 / 是否被折叠
adb shell "dumpsys notification --noredact | grep -B12 'fullscreenIntent=PendingIntent'"    # 末级通知是否挂 FSI
adb shell "dumpsys alarm | grep -A3 'com.ashkb.app/.reminder.ReminderReceiver'"             # 待触发链
```

## 6. 本轮仍未走完（下次真机继续）

久坐窗口与陈旧闹钟兜底（v1.0.68）、免打扰静默投递 + 同时段折叠（v1.0.60）、极简模式（v1.0.65，需连续 3 天无症状记录）、运动页「晨僵三档 + 姿势/睡姿块」视觉确认（v1.0.69/70）、骶髂分期在「我的」档案卡与复诊报告 PDF 的显示 + 档案 JSON 往返（v1.0.67）。

## 7. 本轮待办（非真机项）

- 把「**末级强提醒全屏同样依赖那两个通道开关**」补进 App 文案（自检页小米区块 + 锁屏紧急信息块）——纯文案改动，随下一个版本发布
- 删除测试用药「测试-全屏」（每日 21:10 计划，不删会每晚 22:10 触发全屏强提醒）
