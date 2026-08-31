package com.ashkb.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashkb.app.ui.checkup.CheckupScreen
import com.ashkb.app.ui.checkup.CheckupViewModel
import com.ashkb.app.ui.emergency.EmergencyScreen
import com.ashkb.app.ui.emergency.EmergencyViewModel
import com.ashkb.app.ui.exercise.ExerciseScreen
import com.ashkb.app.ui.exercise.ExerciseViewModel
import com.ashkb.app.ui.knowledge.KnowledgeScreen
import com.ashkb.app.ui.knowledge.KnowledgeViewModel
import com.ashkb.app.ui.me.MeScreen
import com.ashkb.app.ui.me.MeViewModel
import com.ashkb.app.ui.symptom.SymptomScreen
import com.ashkb.app.ui.symptom.SymptomViewModel
import com.ashkb.app.ui.today.TodayScreen
import com.ashkb.app.ui.today.TodayViewModel
import com.ashkb.app.ui.wellness.WellnessScreen
import com.ashkb.app.ui.wellness.WellnessViewModel

private enum class Tab(val label: String) {
    TODAY("今日"), HEALTH("健康"), KNOWLEDGE("知识"), ME("我的")
}

/** 今日 Tab 子页 */
private enum class TodaySub { SYMPTOM, EXERCISE }

/** 健康 Tab 子页 */
private enum class HealthSub { WELLNESS, CHECKUP, EMERGENCY }

@Composable
fun AppShell() {
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var todaySub by remember { mutableStateOf<TodaySub?>(null) }
    var healthSub by remember { mutableStateOf<HealthSub?>(null) }

    val todayVm: TodayViewModel = viewModel(factory = TodayViewModel.Factory)
    val meVm: MeViewModel = viewModel(factory = MeViewModel.Factory)
    val symptomVm: SymptomViewModel = viewModel(factory = SymptomViewModel.Factory)
    val exerciseVm: ExerciseViewModel = viewModel(factory = ExerciseViewModel.Factory)
    val knowledgeVm: KnowledgeViewModel = viewModel(factory = KnowledgeViewModel.Factory)
    val wellnessVm: WellnessViewModel = viewModel(factory = WellnessViewModel.Factory)
    val checkupVm: CheckupViewModel = viewModel(factory = CheckupViewModel.Factory)
    val emergencyVm: EmergencyViewModel = viewModel(factory = EmergencyViewModel.Factory)

    // 返回键处理
    BackHandler(enabled = todaySub != null || healthSub != null) {
        when {
            todaySub != null -> todaySub = null
            healthSub != null -> healthSub = null
        }
    }

    // 今日子页
    todaySub?.let { s ->
        when (s) {
            TodaySub.SYMPTOM -> SymptomScreen(vm = symptomVm, onBack = { todaySub = null })
            TodaySub.EXERCISE -> ExerciseScreen(vm = exerciseVm, onBack = { todaySub = null })
        }
        return
    }

    // 健康子页
    healthSub?.let { s ->
        when (s) {
            HealthSub.WELLNESS -> WellnessScreen(vm = wellnessVm, onBack = { healthSub = null })
            HealthSub.CHECKUP -> CheckupScreen(vm = checkupVm, onBack = { healthSub = null })
            HealthSub.EMERGENCY -> EmergencyScreen(vm = emergencyVm, onBack = { healthSub = null })
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.TODAY -> Icons.Filled.Home
                                    Tab.HEALTH -> Icons.Filled.Favorite
                                    Tab.KNOWLEDGE -> Icons.Filled.MenuBook
                                    Tab.ME -> Icons.Filled.Person
                                }, contentDescription = t.label
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(
                    vm = todayVm,
                    onMedListNeeded = { tab = Tab.ME },
                    onOpenSymptom = { todaySub = TodaySub.SYMPTOM },
                    onOpenExercise = { todaySub = TodaySub.EXERCISE },
                )
                Tab.HEALTH -> HealthHub(
                    onOpenWellness = { healthSub = HealthSub.WELLNESS },
                    onOpenCheckup = { healthSub = HealthSub.CHECKUP },
                    onOpenEmergency = { healthSub = HealthSub.EMERGENCY },
                )
                Tab.KNOWLEDGE -> KnowledgeScreen(vm = knowledgeVm)
                Tab.ME -> MeScreen(meVm)
            }
        }
    }
}

@Composable
private fun HealthHub(
    onOpenWellness: () -> Unit,
    onOpenCheckup: () -> Unit,
    onOpenEmergency: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("健康管理", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("骨健康、营养、复诊与应急——全方位守护你的健康",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        // M2/M3 营养与骨健康
        item {
            HealthEntryCard(
                title = "营养与骨健康",
                subtitle = "体征记录 · 体重追踪 · 补剂档案 · 饮食画像 · 忌口清单",
                icon = "🥗",
                onClick = onOpenWellness,
            )
        }

        // M6 复诊管理
        item {
            HealthEntryCard(
                title = "复诊管理",
                subtitle = "复诊项目配置 · 复诊记录 · 化验结果 · 疫苗记录",
                icon = "📋",
                onClick = onOpenCheckup,
            )
        }

        // M7 紧急卡
        item {
            HealthEntryCard(
                title = "紧急卡",
                subtitle = "五应急场景 · 紧急联系人 · 个人急救信息 · 事件记录",
                icon = "🚨",
                onClick = onOpenEmergency,
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HealthEntryCard(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("→", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}
