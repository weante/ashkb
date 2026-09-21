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

# v1.0.41 加入 / v1.0.42 更正注释：这两条 keep 属**防御性**保留（禁止裁剪 / 优化 / 改名），
# 与「康复计划 → 添加模板」闪退的**真实根因无关**——真因是 ExercisePlanTemplates 里正则末尾
# 一个未转义的 `}`：Java 的 Pattern 视为普通字符，而 Android（ICU）会抛 PatternSyntaxException，
# 导致类初始化失败。v1.0.42 已把该处解析改为逐字符扫描（不再用正则）。
# 保留 keep 的理由：纯领域单例被 R8 激进优化（删 INSTANCE / 静态化成员）存在潜在风险，代价极小。
-keep class com.ashkb.app.domain.ExercisePlanTemplates { *; }
-keep class com.ashkb.app.domain.ExercisePlanTemplates$* { *; }
-keep class com.ashkb.app.domain.ExercisePlanProgress { *; }
-keep class com.ashkb.app.domain.ExercisePlanProgress$* { *; }
