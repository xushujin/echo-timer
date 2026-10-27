import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// 版本信息：About 页面读取 BuildConfig.VERSION_NAME，CI 依据 tag 校验一致性。
// ---------------------------------------------------------------------------
val appVersionName = "1.5.1"
val appVersionCode = 11

// ---------------------------------------------------------------------------
// 签名两条通道（同一份逻辑，只改取值来源）：
//   1) 本地：根目录 keystore.properties（已被 .gitignore 忽略）
//   2) CI  ：环境变量 ECHO_STORE_FILE / ECHO_STORE_PASSWORD / ECHO_KEY_ALIAS / ECHO_KEY_PASSWORD
// 本地文件优先；都没有时 release 变体不带签名（CI 环境下直接失败，避免产出不可安装包）。
// ---------------------------------------------------------------------------
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile: File? = signingValue("storeFile", "ECHO_STORE_FILE")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.isFile }
val hasReleaseSigning = releaseStoreFile != null
val isCi = System.getenv("CI") != null
val isReleaseTaskRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

if (isCi && isReleaseTaskRequested && !hasReleaseSigning) {
    throw GradleException(
        "CI 构建 release 需要签名材料：请设置 ECHO_STORE_FILE / ECHO_STORE_PASSWORD / " +
            "ECHO_KEY_ALIAS / ECHO_KEY_PASSWORD（详见 README「签名」章节）。",
    )
}

android {
    namespace = "com.echotimer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.echotimer"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        // 关于页展示的技术栈版本，全部来自构建脚本，避免文档与代码各写一份。
        buildConfigField("String", "TECH_KOTLIN", "\"2.1.0\"")
        buildConfigField("String", "TECH_COMPOSE_BOM", "\"2024.12.01\"")
        buildConfigField("String", "TECH_AGP", "\"8.7.3\"")
        buildConfigField("String", "TECH_GRADLE", "\"8.11.1\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val store: File = requireNotNull(releaseStoreFile)
                storeFile = store
                storePassword = signingValue("storePassword", "ECHO_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "ECHO_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "ECHO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        // 依赖版本升级提示不属于代码质量问题，且会让离线/内网构建误报失败。
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // 质量要求：编译零警告，任何 Kotlin 警告都升级为错误。
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

// ---------------------------------------------------------------------------
// 产物整理：编译出的 APK 统一命名为 EchoTimer（而不是模块名 app / 小写包名）。
//   release -> build/dist/EchoTimer.apk
//   debug   -> build/dist/debug/EchoTimer-debug.apk
// 用 Sync 而不是 Copy：升级版本后旧文件会被清掉，dist 里永远只有当前产物。
// CI 直接上传 build/dist/EchoTimer.apk。
// ---------------------------------------------------------------------------
val distDir = rootProject.layout.buildDirectory.dir("dist")

// 前置任务：清理 dist 旧产物 + 校验签名配置。
// 单独成任务的原因：Sync 在源文件为空时会被判定为 NO-SOURCE 而跳过所有 action，
// 把清理和校验写在 distReleaseApk 的 doFirst 里，无签名场景下根本不会执行。
val prepareDistDir by tasks.registering {
    group = "distribution"
    description = "清理 dist 里的旧 APK，并确认本次构建配置了签名"
    doLast {
        val staleApks = distDir.get().asFile.listFiles { file -> file.isFile && file.extension == "apk" }
        staleApks?.forEach { it.delete() }
        if (!hasReleaseSigning) {
            throw GradleException(
                "distReleaseApk 需要签名材料：请配置 keystore.properties 或 ECHO_* 环境变量" +
                    "（见 README「签名」章节）。仅想检查 unsigned 包时请直接跑 assembleRelease。",
            )
        }
    }
}

val distReleaseApk by tasks.registering(Sync::class) {
    group = "distribution"
    description = "把 release APK 同步为 build/dist/EchoTimer-v<version>.apk"
    dependsOn("assembleRelease", prepareDistDir)
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
        exclude("*-unsigned.apk")
    }
    into(distDir)
    rename { "EchoTimer-v$appVersionName.apk" }
    // debug 产物放在 dist/debug 下，这里保留它，避免两种产物互相清理。
    preserve { include("debug/**") }
    // 目标目录每次都被 prepareDistDir 清过，这里不做增量判断，保证一定重新复制。
    outputs.upToDateWhen { false }
}

val distDebugApk by tasks.registering(Sync::class) {
    group = "distribution"
    description = "把 debug APK 同步为 build/dist/debug/EchoTimer-v<version>-debug.apk"
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug")) {
        include("*.apk")
    }
    into(distDir.map { it.dir("debug") })
    rename { "EchoTimer-v$appVersionName-debug.apk" }
}
