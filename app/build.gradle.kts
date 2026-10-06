import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // 批次 2（测试安全网）：静态分析（配置见 config/detekt/detekt.yml，历史问题进基线）
    alias(libs.plugins.detekt)
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
        minSdk = 31
        targetSdk = 34
        versionCode = 114
        versionName = "1.2.2"
        // 批次 2（测试安全网）：instrumented 测试（Room schema 漂移校验）需要 runner
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            // v1.0.6 起：release 切换正式签名（P0 安全项）
            // v1.0.16（C1）：开 R8 混淆 + 资源压缩——医疗类 App 上架前必做；
            // keep 规则见 proguard-rules.pro（实体 / kotlinx-serialization 导航路由），
            // Room / Compose / Navigation 由各自 consumer rules 自带覆盖
            isMinifyEnabled = true
            isShrinkResources = true
            // v1.0.75（S-13）：**不再静默回退 debug 签名**（见下方 taskGraph 校验）
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testOptions {
        unitTests {
            // 未 mock 的 android.* 调用返回默认值而非抛异常（P5 单测可跑纯逻辑）
            isReturnDefaultValues = true
            // S1（v1.0.53）：Robolectric 需要真实资源（androidx.test:monitor 等已随其传递依赖引入）
            isIncludeAndroidResources = true
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
    sourceSets {
        // 批次 2：把导出的 Room schema 挂成 androidTest 的 assets——schema 漂移校验要读它
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        // 批次 5：schema 同时挂到 **main** assets。
        // 理由：MigrationTestHelper 从 assets 读 schema，只有挂 main 才能在 JVM/Robolectric 单测里跑
        // 「从旧版本升级」的实证（androidTest assets 进不了 CI）。代价是 APK 多约 1.2 MB（4–19 共 16 个 JSON）
        // ——维护者 2026-10-01 明确选择用体积换 CI 覆盖。
        getByName("main").assets.srcDir("$projectDir/schemas")
    }
}

/**
 * 批次 9：Compose 编译器**诊断报告**（只读产物，不改变任何编译结果）。
 *
 * 为什么保留：第四轮 Compose 性能审查称「Kotlin 2.0.20 未开启 Strong Skipping，需显式加
 * `featureFlags`」。批次 9 用这两个目录里的 `*-composables.txt` / `*-module.json` 做实证判定
 * （本次结论：**2.0.20 已默认开启**，报告见批次 9 回报）——保留它＝该结论可复算，
 * 而不是只能靠转述。产物落在 `app/build/` 下（不入库）；若嫌构建变慢，注释掉本块即可。
 */
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose-reports")
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
}

/**
 * 批次 2（测试安全网）：**导出 Room schema 并入库**（`app/schemas/`）。
 *
 * 为什么必须有：`AppDatabase` 的 version 已到 17，而此前**一个 schema 文件都没有**——
 * 「迁移是否真的把库结构改对了」在仓库里无从比对，只能等用户升级时崩。
 * 导出后：① 每次改实体都会在 git 里留下 diff（评审能看见结构变更）；
 * ② 后续新增的迁移可被 `MigrationTestHelper` 验证；③ 本批新增的 schema 漂移测试拿它当基准。
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * 批次 2（测试安全网）：detekt 静态分析。
 *
 * 策略：继承官方默认规则集 + 项目化让步（config/detekt/detekt.yml），
 * **历史问题一律进基线**（config/detekt/baseline.xml），基线之外的新问题让构建失败。
 * 这样 CI 立刻有守门员，又不需要为「零告警」做一次大重构。
 *
 * 重新生成基线：`gradle :app:detektBaseline`（生成后请人工看 diff，别把新问题一起塞进去）
 */
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline.xml")
    parallel = true
    source.setFrom(
        "src/main/java",
        "src/test/java",
        "src/androidTest/java",
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

/**
 * v1.0.75（S-13）：**产出正式包时，缺少正式签名必须失败，而不是安静地用 debug 签名。**
 *
 * 旧行为：`local.properties` 里没有 keystore 时，`assembleRelease` 会静默产出一个
 * debug 签名的「正式包」。风险是实打实的——它**无法覆盖安装**已发布的正式版，
 * 用户只能卸载重装（丢数据），而交付者若不逐字节验签根本发现不了。
 *
 * 刻意放在 taskGraph 回调里而不是配置期：配置期抛异常会把 debug 构建与单元测试一起挡掉。
 * CI 无密钥，故显式设置 `ASHKB_ALLOW_DEBUG_SIGNING=1` 放行（并打印醒目告警）。
 */
gradle.taskGraph.whenReady {
    val buildingReleaseApk = allTasks.any {
        (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package")) &&
            it.name.contains("Release")
    }
    if (buildingReleaseApk && !hasReleaseKeystore) {
        if (System.getenv("ASHKB_ALLOW_DEBUG_SIGNING") == "1") {
            logger.warn(
                "⚠️ ASHKB_ALLOW_DEBUG_SIGNING=1：本次产出的 release 包使用 **debug 签名**，" +
                    "仅限 CI / 本地冒烟，**不得对外发布**。"
            )
        } else {
            throw GradleException(
                "缺少正式签名配置：local.properties 需要 ashkb.store.file 指向存在的 keystore。" +
                    "若确实要产出 debug 签名的测试包，请设置环境变量 ASHKB_ALLOW_DEBUG_SIGNING=1。"
            )
        }
    }
}

dependencies {
    // Room（27 实体逐步启用，P1 落 4 表）
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.ktx)

    // 导航（UI 改版唯一新增依赖，见方案 §8.6）：规范化返回栈 / 状态保存 / 深链接
    implementation(libs.navigation.compose)

    // JSON（种子 payload 解析，org.json 亦可用，此处统一 kotlinx）
    implementation(libs.kotlinx.serialization.json)

    // P5 单元测试：org.json 桥接（Android stub 的 org.json 在 JVM 单测中不可用）
    testImplementation(libs.junit)
    testImplementation(libs.json)

    // S1（v1.0.53）：提醒链回归——`ReminderScheduler` 依赖 `AlarmManager`，纯 JVM 单测
    // （isReturnDefaultValues=true）完全覆盖不到，A1/N1 那类「只在真机暴露」的 bug 正源于此。
    // Robolectric 能模拟 AlarmManager（其核心能力），故用它断言「取消-重建」语义。
    // ⚠️ 不可用于检测 ICU 正则差异（已实测证伪，见 HANDOFF §7）。
    // 4.13 + instrumented android-all(API 34) 已在本机 Gradle / Maven 缓存中，可离线跑。
    testImplementation(libs.robolectric)
    // 批次 5：迁移实证用（MigrationTestHelper）；随 schema 进 main assets 后在 JVM 单测里可用
    testImplementation(libs.room.testing)

    // 批次 2（测试安全网）：instrumented 测试。
    // 目的：把「迁移是否真的把库结构改对」变成机器可判——schema 漂移校验要在真机上开一次库。
    // 版本选择：androidx.test.* 用本机缓存里已有的版本（离线可解析）；room-testing 与 Room 同版本。
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)

    // S1b（手势回归）**已实测证伪、不予落地**（v1.0.53）：本环境能注入触摸事件
    // （最小 pointerInput 盒子能收到 down），但**驱动不了 M3 Slider 的拖动**——
    // 连「裸 M3 Slider」（无 ScoreInput 包装、无祖先旁听）都拖不动，故那 3 条手势断言
    // 是**环境假红**而非应用缺陷。结论：滑杆手势仍只能真机验证，不要在此环境写手势测试。
    // 证据与探针写法见 HANDOFF §7。
}
