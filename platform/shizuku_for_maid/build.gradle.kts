import org.gradle.kotlin.dsl.dependencies

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.projectmaidgroup.platform.shizuku_for_maid"
    compileSdk = 36

    defaultConfig {
        minSdk = 31

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    val shizuku_version = "13.1.0" // 建议使用较新版本
    implementation (libs.shizuku.api)
    // 必须包含 provider，它会自动处理 Manifest 合并，让 Shizuku 识别你
    implementation (libs.shizuku.provider)
    implementation(libs.androidx.core.ktx)
    implementation(libs.shizuku.api)
    implementation(project(":platform:shizuku_service"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}