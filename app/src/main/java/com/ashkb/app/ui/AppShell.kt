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
            snackbar.showSnackbar("上次异常：\n" + CrashLogger.summary(log).take(600))
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
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { t ->
                        val selected = topTab == t
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) t.selectedIcon else t.icon,
                                    contentDescription = null,   // 装饰性：label 已承载语义
                                    modifier = Modifier.size(Size.iconMd),
                                )
                            },
                            label = { Text(t.label) },
                        )
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
    val labRecent by checkupVm.labRecent.collectAsStateWithLifecycle()
    val contacts by emergencyVm.contacts.collectAsStateWithLifecycle()

    val wellnessSub = buildList {
        add(if (vitals != null) stringResource(R.string.vitals_today_recorded) else stringResource(R.string.vitals_today_not_recorded))
        add(if (weight != null) stringResource(R.string.vitals_weight_recorded) else stringResource(R.string.vitals_weight_not_recorded))
    }.joinToString(" · ")

    val checkupSub = stringResource(R.string.health_badge_checkup_lab, checkupItems.size, labRecent.size)

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
