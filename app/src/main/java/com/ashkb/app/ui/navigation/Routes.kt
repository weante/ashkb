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
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

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

val TABS: List<TopTab> = listOf(
    TopTab(Today, "今日", Icons.Outlined.CheckCircle, Icons.Rounded.CheckCircle),
    TopTab(Health, "健康", Icons.Outlined.MonitorHeart, Icons.Rounded.MonitorHeart),
    TopTab(Report, "报表", Icons.Outlined.Insights, Icons.Rounded.Insights),
    TopTab(Knowledge, "知识", Icons.Outlined.MenuBook, Icons.Rounded.MenuBook),
    TopTab(Me, "我的", Icons.Outlined.Person, Icons.Rounded.Person),
)
