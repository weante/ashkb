package com.ashkb.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.Serializable

import com.ashkb.app.R

// ---- L1（底栏可见，无返回箭头）----
@Serializable data object Today
@Serializable data object Health
@Serializable data object Report
@Serializable data object Knowledge
@Serializable data object Me

// ---- L2（底栏隐藏，TopAppBar + 返回）----
@Serializable data object Symptom
@Serializable data object Exercise
@Serializable data object Wellness
@Serializable data object Checkup
@Serializable data object Emergency
@Serializable data object Backup
@Serializable data object Meds
// v1.0.39：B3 推荐食谱库 / B7 周期康复计划
@Serializable data object Recipes
@Serializable data object ExercisePlans

// ---- L3（表单全屏）----
@Serializable data class MedEdit(val id: String? = null)   // null = 新增
@Serializable data object ProfileEdit

/** 底栏 Tab 定义。选中 / 未选中必须用成对图标（M3 标准选中反馈）。 */
class TopTab(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

@Composable
fun TABS(): List<TopTab> = listOf(
    TopTab(Today, stringResource(R.string.today_tab), Icons.Outlined.CheckCircle, Icons.Rounded.CheckCircle),
    TopTab(Health, stringResource(R.string.me_health_section), Icons.Outlined.MonitorHeart, Icons.Rounded.MonitorHeart),
    TopTab(Report, stringResource(R.string.report_tab), Icons.Outlined.Insights, Icons.Rounded.Insights),
    TopTab(Knowledge, stringResource(R.string.knowledge_tab), Icons.Outlined.MenuBook, Icons.Rounded.MenuBook),
    TopTab(Me, stringResource(R.string.nav_me), Icons.Outlined.Person, Icons.Rounded.Person),
)
