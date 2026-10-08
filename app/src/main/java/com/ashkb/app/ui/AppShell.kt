package com.ashkb.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Emergency
import androidx.compose.material.icons.rounded.MedicalInformation
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute

import com.ashkb.app.CrashLogger
import com.ashkb.app.R
import com.ashkb.app.data.repo.DisclaimerStore
import com.ashkb.app.ui.backup.BackupScreen
import com.ashkb.app.ui.backup.BackupViewModel
import com.ashkb.app.ui.checkup.CheckupScreen
import com.ashkb.app.ui.checkup.CheckupViewModel
import com.ashkb.app.ui.components.NavRow
import com.ashkb.app.ui.emergency.EmergencyScreen
import com.ashkb.app.ui.emergency.EmergencyViewModel
import com.ashkb.app.ui.exercise.ExercisePlansScreen
import com.ashkb.app.ui.exercise.ExercisePlansViewModel
import com.ashkb.app.ui.exercise.ExerciseScreen
import com.ashkb.app.ui.exercise.ExerciseViewModel
import com.ashkb.app.ui.knowledge.KnowledgeScreen
import com.ashkb.app.ui.knowledge.KnowledgeViewModel
import com.ashkb.app.ui.me.MeScreen
import com.ashkb.app.ui.me.MeViewModel
import com.ashkb.app.ui.me.MedEditScreen
import com.ashkb.app.ui.me.MedsScreen
import com.ashkb.app.ui.me.ProfileEditScreen
import com.ashkb.app.ui.me.ReminderCheckScreen
import com.ashkb.app.ui.navigation.Backup
import com.ashkb.app.ui.navigation.Checkup
import com.ashkb.app.ui.navigation.Emergency
import com.ashkb.app.ui.navigation.Exercise
import com.ashkb.app.ui.navigation.ExercisePlans
import com.ashkb.app.ui.navigation.Health
import com.ashkb.app.ui.navigation.Knowledge
import com.ashkb.app.ui.navigation.Me
import com.ashkb.app.ui.navigation.MedEdit
import com.ashkb.app.ui.navigation.Meds
import com.ashkb.app.ui.navigation.ProfileEdit
import com.ashkb.app.ui.navigation.ReminderCheck
import com.ashkb.app.ui.navigation.Recipes
import com.ashkb.app.ui.navigation.Report
import com.ashkb.app.ui.navigation.Symptom
import com.ashkb.app.ui.navigation.TABS
import com.ashkb.app.ui.navigation.Today
import com.ashkb.app.ui.navigation.Wellness
import com.ashkb.app.ui.report.ReportScreen
import com.ashkb.app.ui.report.ReportViewModel
import com.ashkb.app.ui.symptom.SymptomScreen
import com.ashkb.app.ui.symptom.SymptomViewModel
import com.ashkb.app.ui.theme.Motion
import com.ashkb.app.ui.theme.Size
import com.ashkb.app.ui.theme.Spacing
import com.ashkb.app.ui.today.TodayScreen
import com.ashkb.app.ui.today.TodayViewModel
import com.ashkb.app.ui.wellness.RecipesScreen
import com.ashkb.app.ui.wellness.RecipesViewModel
import com.ashkb.app.ui.wellness.WellnessScreen
import com.ashkb.app.ui.wellness.WellnessViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ashkb.app.ui.theme.Glass
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
/**
 * 应用骨架。
 *
 * 修掉此前的三个结构性 bug：
 * 1. 子页靠 early `return` 渲染，`Scaffold` / `NavigationBar` 根本没被 compose（底栏消失）；
 * 2. `WellnessScreen` 无顶栏且 `onBack` 从不调用，其余 L2 页顶栏无返回箭头（进得去出不来）；
 * 3. 无 `rememberSaveable` / 无 Navigation-Compose，转屏回到初始 tab（状态全丢）。
 *
 * 现在：`Scaffold` 常驻，底栏可见性由当前路由层级决定（L1 显示、L2/L3 隐藏）。
 */
@Composable
fun AppShell() {
    // v1.0.63 C12：首启免责声明门禁——未确认前不创建任何 ViewModel（也就不会打开数据库）。
    val shellContext = LocalContext.current
    var disclaimerAccepted by rememberSaveable {
        mutableStateOf(DisclaimerStore.isAccepted(shellContext))
    }
    if (!disclaimerAccepted) {
        FirstLaunchDisclaimer(
            onAccept = {
                DisclaimerStore.accept(shellContext)
                disclaimerAccepted = true
            },
        )
        return
    }

    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    val backStackEntry by nav.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val tabs = TABS()
    val topTab = tabs.firstOrNull { t ->
        destination?.hierarchy?.any { it.hasRoute(t.route::class) } == true
    }
    val showBottomBar = topTab != null

    // ViewModel 一次性消息 → Snackbar（替代"每个操作都要点一次知道了"的阻塞弹窗）
    LaunchedEffect(Unit) {
        GlobalMessages.events.collect { snackbar.showSnackbar(it) }
    }

    // v1.0.40：上次启动若有未捕获异常，本次启动弹一条摘要（便于截图反馈）；读后即清，不重复打扰。
    // 完整堆栈留在 filesDir/last_crash.txt（adb pull 可取）。
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        CrashLogger.takeLast(appContext)?.let { log ->
            // v1.2.6（i18n）：LaunchedEffect 内不是 Composable 作用域，故用 appContext.getString
            snackbar.showSnackbar(
                appContext.getString(R.string.ui_appshell_last_crash, CrashLogger.summary(log).take(600)),
            )
        }
    }

    // v1.0.86（批次 11）：**只保留"构造即有代价且必须跨页共用"的 VM**。
    //
    // 下沉判据（三条全中才动，保守优先）：
    //   ① 该 VM 只被**一个**路由使用（否则下沉会变成多实例，导航/作用域语义就变了）；
    //   ② 构造期有真实代价（起协程 / 查库 / 解 Keystore），放在这里等于每次冷启动白付；
    //   ③ 下沉后创建时机仍在该路由的 NavBackStackEntry 作用域内（viewModel() 在 composable<X>
    //      里取到的 owner 就是这条目的 entry），返回栈语义不变。
    //
    // 已下沉（6 个）：Today / Symptom / Exercise / Knowledge / Report / Backup——都只属于一个路由，
    // 且构造期分别有「起协程查库」「起 ticker 协程」「解 Keystore 读凭据」等真实代价。
    // 保留在这里（4 个）：
    //   · MeViewModel——被 Me / Meds / MedEdit **三个**路由共用，下沉会分裂成三份实例，
    //     药单页与编辑页读到的 profile / meds 流各自独立（违反判据 ①）；
    //   · Wellness / Checkup / EmergencyViewModel——除各自二级页外，还被 L1 的 HealthHub
    //     （composable<Health>）用来渲染实时摘要副标题，同样多路由共用（判据 ①）。
    // 保留这 4 个的代价：批次 11 之后**构造期已无任何协程 / 查库**（ticker 收归 DateProvider，
    // 各流都是 WhileSubscribed 懒启动），只剩 MeViewModel 零成本、其余三个各持一份轻量对象。
    val meVm: MeViewModel = viewModel(factory = MeViewModel.Factory)
    val wellnessVm: WellnessViewModel = viewModel(factory = WellnessViewModel.Factory)
    val checkupVm: CheckupViewModel = viewModel(factory = CheckupViewModel.Factory)
    val emergencyVm: EmergencyViewModel = viewModel(factory = EmergencyViewModel.Factory)
    // v1.0.40：Recipes / ExercisePlans 两个 VM 改为**进页面才创建**（见下方 L2 注册）。
    // 这两个 VM 的属性初始化会即时创建 Room Flow（进而触发数据库打开），放在这里会让冷启动
    // 多背两份构建成本与失败面；移入路由后，冷启动与它们彻底解耦。

    Scaffold(
        // v1.1.6：**容器透明**。`Scaffold` 默认用 `colorScheme.background` 铺满整个区域
        // （含 bottomBar 槽位下方那段系统手势区）。置零 contentWindowInsets 只去掉了
        // "额外留白"，但**槽位本身仍被 background 铺满**——维护者截图里那条横带（实测
        // y≈3087..3200，高 113px ≈ 41dp，正是手势区高度）就是它。
        // 透明之后，页面背景由各页面自己提供，dock 下方露出的就是页面本色。
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                // v1.1.6：导航栏改用「半透明底 + 顶部 1dp 高光」的玻璃表层。
                // 为什么不是真 backdrop-blur：Compose 没有 backdrop-filter（Modifier.blur 模糊的是
                // 自己），真模糊背后要 RenderEffect = API 31+，而本应用 minSdk 26 且维护者要求
                // 顾及其他机型 —— 见 ui/theme/Color.kt 的 Glass 注释。
                // 为什么导航栏合适：它是唯一**完全静止**的全宽表层（内容从它下面滚过、它自己不动），
                // 半透明能让人看见"内容还在继续"，而它一帧都不用重算。
                // 浅色 / 深色判定只算一次：两处表层色与高光色必须同源，各算一次可能不一致
                val isLightSurface = MaterialTheme.colorScheme.background.luminance() > 0.5f
                // v1.1.6：**iOS 式悬浮 dock**——不再全宽贴底，而是留出外边距的圆角矩形。
                // 维护者原话：「需要向 iOS 一样圆角矩形，而不是像现在一样直接贴合在最底层无边框」。
                // 三件套缺一不可：① 外侧留白（左右 + 底部）② 大圆角 ③ 柔和投影（浮起来）。
                val barShape = RoundedCornerShape(Size.dockCorner)
                Box(
                    Modifier
                        .fillMaxWidth()
                        // 手势区避让由这里承担（NavigationBar 的 windowInsets 已置零）：
                        // 底部 10dp 比顶部 8dp 略多，让 dock 与屏幕下缘有呼吸感。
                        // 底部 = 系统手势区高度 + 10dp 呼吸感（Scaffold 的 inset 已置零，
                        // 这里成为唯一的底部避让点——置零而不补，dock 会压在手势条上）。
                        .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm)
                        .padding(bottom = 10.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // ⚠️ v1.1.6 定案：**不能用 `Modifier.shadow`**。
                            //
                            // 像素采样证明（维护者第五次反馈"横带还在"）：`elevation = 10dp`
                            // 会在 dock 下方投出一条 y=3110..3140 的**渐变灰带**（采样 203→234、
                            // 整宽均匀）。前四次我分别怀疑过 NavigationBar 的 windowInsets、
                            // NavigationBar 自带高度、Scaffold 的 contentWindowInsets、Scaffold 的
                            // containerColor —— 全错。定位靠的是**采样像素的渐变形**（底色带颜色
                            // 均匀，投影才渐变），不是读代码推理。
                            //
                            // 附带事实：y=3140..3200 那段是**系统手势条**（dumpsys window 实测
                            // `navigationBars frame=[0,3140][1440,3200]`），应用改不了它；
                            // 能消的只有我们自己投影的这 30px。
                            //
                            // 替代：画一圈极淡轮廓代替真实投影——分层观感保留，下方不再有灰带。
                            .drawBehind {
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = 0.04f),
                                    topLeft = Offset(0f, 1.dp.toPx()),
                                    // ⚠️ 这里的 Size 是 Compose 几何类型，与项目 theme.Size
                                    // （度量对象）同名，故用全限定名避免歧义。
                                    size = androidx.compose.ui.geometry.Size(size.width, size.height),
                                    cornerRadius = CornerRadius(
                                        com.ashkb.app.ui.theme.Size.dockCorner.toPx(),
                                        com.ashkb.app.ui.theme.Size.dockCorner.toPx(),
                                    ),
                                )
                            }
                            .clip(barShape)
                            .background(if (isLightSurface) Glass.surfaceLight else Glass.surfaceDark)
                            .border(
                                width = 1.dp,
                                color = if (isLightSurface) Glass.borderLight else Glass.borderDark,
                                shape = barShape,
                            ),
                    ) {
                        // v1.1.6：**不再用 Material3 的 `NavigationBar`**，改用手写 Row。
                        //
                        // 为什么换掉（维护者截图："有个奇怪的阴影"）：`NavigationBar` **自带高度**
                        // （80dp 容器 + 可被 windowInsets 撑高），它内部还有自己的占位与底色。
                        // 外边再套圆角玻璃底时，两者高度对不齐 → 出现一条"上边缘硬、下边缘带阴影"
                        // 的横带。关掉 windowInsets 只解决了它撑高的问题，**没解决容器自带留白**。
                        // 手写 Row 后：高度由我们的内边距决定，圆角/边框/投影三者共用同一条边界，
                        // 不再有任何"对不齐"的可能。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(barShape)
                                .padding(vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tabs.forEach { t ->
                                val selected = topTab == t
                                // 单 tab：图标 + 文字竖排；选中态是**内缩的胶囊**（iOS 那种），
                                // 而不是 NavigationBarItem 默认的整格高亮 —— 整格高亮会一路顶到
                                // dock 边缘，把圆角吃出一个方角。
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(Size.dockItemCorner))
                                        .background(
                                            if (selected) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                Color.Transparent
                                            },
                                        )
                                        .clickable(enabled = !selected) {
                                            nav.navigate(t.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        .padding(vertical = Spacing.xs)
                                        .heightIn(min = Size.touchMin),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = if (selected) t.selectedIcon else t.icon,
                                        contentDescription = null,   // 装饰性：label 已承载语义
                                        modifier = Modifier.size(Size.iconMd),
                                    )
                                    Spacer(Modifier.height(Spacing.xxs))
                                    Text(
                                        t.label,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Today,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(Motion.NormalMs)) },
            exitTransition = { fadeOut(tween(Motion.NormalMs)) },
        ) {
            // ---- L1 ----
            composable<Today> {
                // v1.0.86（批次 11）：本 VM 构造时即起协程算一次「昨天未记录」（查库），
                // 而它只被今日页使用——故下沉到路由内，冷启动不再白付。
                TodayScreen(
                    vm = viewModel(factory = TodayViewModel.Factory),
                    onMedListNeeded = { nav.navigate(MedEdit()) },   // 修：直达表单，不再只切 tab
                    onOpenSymptom = { nav.navigate(Symptom) },
                    onOpenExercise = { nav.navigate(Exercise) },
                )
            }
            composable<Health> {
                HealthHub(
                    wellnessVm = wellnessVm,
                    checkupVm = checkupVm,
                    emergencyVm = emergencyVm,
                    onOpenWellness = { nav.navigate(Wellness) },
                    onOpenCheckup = { nav.navigate(Checkup) },
                    onOpenEmergency = { nav.navigate(Emergency) },
                )
            }
            composable<Report> {
                ReportScreen(
                    // v1.0.86（批次 11）：报表 VM 只被本路由使用——下沉后冷启动不再为它查库
                    // （取数时机同时从 init 改为首次进入本页，见 ReportViewModel.loadOnce）。
                    vm = viewModel(factory = ReportViewModel.Factory),
                    onOpenBackup = { nav.navigate(Backup) },
                )
            }
            composable<Knowledge> {
                KnowledgeScreen(vm = viewModel(factory = KnowledgeViewModel.Factory))
            }
            composable<Me> {
                MeScreen(
                    vm = meVm,
                    onOpenMeds = { nav.navigate(Meds) },
                    onOpenBackup = { nav.navigate(Backup) },
                    onEditProfile = { nav.navigate(ProfileEdit) },
                    onOpenReminderCheck = { nav.navigate(ReminderCheck) },
                )
            }

            // ---- L2 ----
            composable<Symptom> {
                SymptomScreen(vm = viewModel(factory = SymptomViewModel.Factory), onBack = { nav.popBackStack() })
            }
            composable<Exercise> {
                ExerciseScreen(
                    vm = viewModel(factory = ExerciseViewModel.Factory),
                    onOpenPlans = { nav.navigate(ExercisePlans) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<Wellness> {
                WellnessScreen(
                    vm = wellnessVm,
                    onOpenRecipes = { nav.navigate(Recipes) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<Checkup> { CheckupScreen(vm = checkupVm, onBack = { nav.popBackStack() }) }
            composable<Emergency> { EmergencyScreen(vm = emergencyVm, onBack = { nav.popBackStack() }) }
            // v1.0.72：提醒可靠性自检（从「我的」入口进入的二级页）
            composable<ReminderCheck> { ReminderCheckScreen(onBack = { nav.popBackStack() }) }
            composable<Backup> {
                BackupScreen(
                    // v1.0.86（批次 11）：备份页 VM 只被本路由使用；其构造期要解 Keystore 读
                    // WebDAV 凭据（webdavConfig），下沉后冷启动不再白付这一次解密。
                    vm = viewModel(factory = BackupViewModel.Factory),
                    onBack = { nav.popBackStack() },
                )
            }
            composable<Meds> {
                MedsScreen(
                    vm = meVm,
                    onAdd = { nav.navigate(MedEdit()) },
                    onEdit = { nav.navigate(MedEdit(it.id)) },
                    onBack = { nav.popBackStack() },
                )
            }

            composable<Recipes> {
                RecipesScreen(
                    vm = viewModel(factory = RecipesViewModel.Factory),
                    onBack = { nav.popBackStack() },
                )
            }
            composable<ExercisePlans> {
                ExercisePlansScreen(
                    vm = viewModel(factory = ExercisePlansViewModel.Factory),
                    onBack = { nav.popBackStack() },
                )
            }

            // ---- L3 ----
            composable<MedEdit> { entry ->
                MedEditScreen(
                    vm = meVm,
                    editId = entry.toRoute<MedEdit>().id,
                    onSaved = { nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<ProfileEdit> {
                val profile by meVm.profile.collectAsStateWithLifecycle()
                ProfileEditScreen(
                    initial = profile,
                    onSave = {
                        meVm.saveProfile(it)
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

/** 健康 hub：三张入口卡改为 NavRow，副标题放实时摘要（不点进去也知道状态）。 */
@Composable
private fun HealthHub(
    wellnessVm: WellnessViewModel,
    checkupVm: CheckupViewModel,
    emergencyVm: EmergencyViewModel,
    onOpenWellness: () -> Unit,
    onOpenCheckup: () -> Unit,
    onOpenEmergency: () -> Unit,
) {
    val vitals by wellnessVm.vitalsToday.collectAsStateWithLifecycle()
    val weight by wellnessVm.weightToday.collectAsStateWithLifecycle()
    val checkupItems by checkupVm.checkupItems.collectAsStateWithLifecycle()
    // v1.0.87（批次 13）：摘要报的是**总数**，不是化验列表分页窗口的长度——
    // 旧口径（labRecent.size）在库里化验 ≥100 行时恒为 100，且删掉几行也不变（维护者真机反馈）
    val labTotal by checkupVm.labTotal.collectAsStateWithLifecycle()
    val contacts by emergencyVm.contacts.collectAsStateWithLifecycle()

    val wellnessSub = buildList {
        add(if (vitals != null) stringResource(R.string.vitals_today_recorded) else stringResource(R.string.vitals_today_not_recorded))
        add(if (weight != null) stringResource(R.string.vitals_weight_recorded) else stringResource(R.string.vitals_weight_not_recorded))
    }.joinToString(" · ")

    val checkupSub = stringResource(R.string.health_badge_checkup_lab, checkupItems.size, labTotal)

    val emergencySub = if (contacts.isEmpty()) {
        stringResource(R.string.emergency_no_contacts)
    } else {
        stringResource(R.string.health_badge_contacts_ready, contacts.size)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column(Modifier.padding(top = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.me_health_manage), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.me_health_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // M2/M3 营养与骨健康
        item {
            NavRow(
                icon = Icons.Rounded.Restaurant,
                title = stringResource(R.string.nutrition_bone_health_title),
                subtitle = wellnessSub,
                onClick = onOpenWellness,
            )
        }

        // M6 复诊管理
        item {
            NavRow(
                icon = Icons.Rounded.MedicalInformation,
                title = stringResource(R.string.checkup_manage_title),
                subtitle = checkupSub,
                onClick = onOpenCheckup,
            )
        }

        // M7 紧急卡
        item {
            NavRow(
                icon = Icons.Rounded.Emergency,
                title = stringResource(R.string.emergency_card_title),
                subtitle = emergencySub,
                onClick = onOpenEmergency,
            )
        }

        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}
