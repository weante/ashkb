package com.ashkb.app.ui.wellness

/**
 * v1.0.91（批次 16）：本屏「当前打开哪个弹层」的**唯一一份**状态。
 *
 * ### 为什么它必须留在根级（不能下沉到 section）
 * 七个入口分散在五个 section 里（体征 hero 的编辑 / 体重卡的录入与管理 / 补剂档案卡的开表单 /
 * 饮食画像卡的编辑 / 忌口清单卡的管理），写的是**同一个可见性**——它是跨 section 的胶水。
 * 更硬的一条：弹层本体在 `WellnessScreen` 里、**`LazyColumn` 之外**渲染
 * （`ModalBottomSheet` 是独立窗口；若登记成列表 item，一旦该 item 滚出视口就会被回收、
 * 弹层会跟着消失——与 `TodayScreen` 的四个弹层同一取舍）。
 *
 * ### 为什么收成一个 data class 而不是七个布尔
 * 拆分前根级正文是七个 `var showXxx by remember { mutableStateOf(false) }` 加上三个补剂目标，
 * 一共十个与列表无关的开关。收成一份后：
 *  · 根级只留一个 `MutableState<WellnessSheetState>`；
 *  · 各 section 通过 `sheets.value = sheets.value.copy(xxx = true)` 打开自己那一张，
 *    「这一屏有哪些弹层」在 [WellnessSheetState] 一处可数。
 * 三个补剂目标（详情 / 编辑 / 待删）**不**并进这里：它们的值是 `Supplement?`，
 * 与「弹层可见性」是两回事，混在一起会让这个类同时承担两个语义。
 * 它们同样由根级持有（写入点在列表作用域里创建的补剂行回调上，section 自己 `remember` 的东西
 * 那些回调够不着），并与弹层读**同一份引用**——写成两份 state 就会「点了没反应」
 * （v1.0.81 的缺陷正是这个形状）。
 */
internal data class WellnessSheetState(
    val vitals: Boolean = false,
    val weight: Boolean = false,
    val weightManage: Boolean = false,
    val bodyMeasure: Boolean = false,
    val supplementForm: Boolean = false,
    val dietForm: Boolean = false,
    val avoidManage: Boolean = false,
)
