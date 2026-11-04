package com.ashkb.app.ui.backup

/**
 * v1.2.15（外部 UI 审查 P1-5）：这一次待确认的恢复是从哪个入口发起的。
 *
 * 存在的唯一理由是**待确认块与恢复结果要显示在发起它的那张卡里**。缺陷原状：待确认块固定
 * 渲染在恢复卡内部（[BackupRestoreSection] 的 `RestoreSection`），而快照撤销入口在恢复卡
 * **下方**（[UndoSnapshotBlock]）——用户点完「用该快照撤销」，下一步却出现在屏幕上方的
 * 另一张卡里，附近毫无变化，看起来像没反应，于是重复点。
 *
 * 为什么不把待确认块挪到某个"公共位置"：挪到撤销块下方会坑普通恢复（同样的问题反方向
 * 存在），挪到页面底部又离发起处更远。真正要表达的是「谁发起、谁显示」，那就得知道发起者。
 *
 * 同一时刻只可能有一个入口在推进：两个入口都经 [BackupViewModel] 的 `verifyBytes`，
 * 它一次性覆盖 `pendingRestore` / `pendingFileName` / `restoreOrigin` 三者，所以两处永远
 * 只有一个渲染出待确认块。
 */
enum class RestoreOrigin {
    /** 用户在恢复卡里选了本机文件或 WebDAV 远程备份。 */
    PICKED_FILE,

    /** 用户在恢复卡下方的快照块里点了「用该快照撤销」。 */
    SNAPSHOT_UNDO,
}
