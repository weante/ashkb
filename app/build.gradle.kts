plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.ashkb.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ashkb.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // 1.0 定版：自用场景优先稳定（暂不裁剪），debug 签名保证可直接安装
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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

    // JSON（种子 payload 解析，org.json 亦可用，此处统一 kotlinx）
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // P5 单元测试：org.json 桥接（Android stub 的 org.json 在 JVM 单测中不可用）
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
