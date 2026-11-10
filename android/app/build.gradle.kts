plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.xxt.board"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xxt.board"
        minSdk = 26
        targetSdk = 36
        versionCode = 48
        versionName = "0.51.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // GitHub Release 分发的 APK 与日常调试包同签名（debug keystore），
            // 用户可以覆盖安装；发布包只是关闭日志/开启压缩
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

ksp {
    // 导出 schema，方便写 Migration 与审查
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    androidTestImplementation(libs.kyant.backdrop)
    androidTestCompileOnly(files("${android.sdkDirectory}/platforms/android-${android.compileSdk}/optional/android.test.runner.jar",
        "${android.sdkDirectory}/platforms/android-${android.compileSdk}/optional/android.test.base.jar"))
    implementation(libs.kyant.backdrop)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)


    testImplementation("junit:junit:4.13.2")
}
