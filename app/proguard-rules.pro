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
