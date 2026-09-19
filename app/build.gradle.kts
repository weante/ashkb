import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// 正式签名：凭据读自 local.properties（gitignore 排除，不入库）；
// 未配置时回退 debug 签名，保证任意环境均可构建。
val releaseKeystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStoreFile = releaseKeystoreProps.getProperty("ashkb.store.file")
val hasReleaseKeystore = !releaseStoreFile.isNullOrBlank() &&
    rootProject.file(releaseStoreFile).exists()

android {
    namespace = "com.ashkb.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ashkb.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 33
        versionName = "1.0.28"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseKeystoreProps.getProperty("ashkb.store.password")
                keyAlias = releaseKeystoreProps.getProperty("ashkb.key.alias")
                keyPassword = releaseKeystoreProps.getProperty("ashkb.key.password")
            }
        }
    }

    buildTypes {
        release {
            // v1.0.6 起：release 切换正式签名（P0 安全项）；无 keystore 环境回退 debug
            // v1.0.16（C1）：开 R8 混淆 + 资源压缩——医疗类 App 上架前必做；
            // keep 规则见 proguard-rules.pro（实体 / kotlinx-serialization 导航路由），
            // Room / Compose / Navigation 由各自 consumer rules 自带覆盖
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testOptions {
        unitTests {
            // 未 mock 的 android.* 调用返回默认值而非抛异常（P5 单测可跑纯逻辑）
            isReturnDefaultValues = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Room（27 实体逐步启用，P1 落 4 表）
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")

    // 导航（UI 改版唯一新增依赖，见方案 §8.6）：规范化返回栈 / 状态保存 / 深链接
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // JSON（种子 payload 解析，org.json 亦可用，此处统一 kotlinx）
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // P5 单元测试：org.json 桥接（Android stub 的 org.json 在 JVM 单测中不可用）
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
