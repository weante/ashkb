# 保留种子层 JSON payload 字段名（org.json 解析种子 + 备份列名与实体字段一致性）
-keep class com.ashkb.app.data.entity.** { *; }
-dontwarn org.bouncycastle.**

# v1.0.16（C1）R8 混淆：kotlinx.serialization 官方推荐规则——
# Navigation-Compose 类型安全路由（Routes.kt 全部 @Serializable）经 serializer() 合成方法
# 反射查找，混淆或裁剪会在运行时导航崩溃
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.ashkb.app.**$$serializer { *; }
-keepclassmembers class com.ashkb.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.ashkb.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# v1.0.41（B7 闪退修复）：R8 对 Kotlin object 单例（含嵌套 data class）的激进优化在 ART 上
# 会产出无法完成初始化的类 —— 真机表现为 NoClassDefFoundError: K1.n
# （mapping 反查 = com.ashkb.app.domain.ExercisePlanTemplates）。
# usage.txt 显示该类 INSTANCE 字段被删、成员被静态化，且 ExercisePlanProgress（唯一以
# ExercisePlanTemplates.WeekSpec 作签名类型的类）被整类删除并内联。
# 故对这两个纯领域类及其嵌套类做完整保留（禁裁剪 / 优化 / 改名）。
-keep class com.ashkb.app.domain.ExercisePlanTemplates { *; }
-keep class com.ashkb.app.domain.ExercisePlanTemplates$* { *; }
-keep class com.ashkb.app.domain.ExercisePlanProgress { *; }
-keep class com.ashkb.app.domain.ExercisePlanProgress$* { *; }
