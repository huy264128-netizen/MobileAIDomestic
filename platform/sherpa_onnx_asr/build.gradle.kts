plugins {
    alias(libs.plugins.android.library)
    // 该工程通过 Kotlin Compose 插件提供 Kotlin(Android) 能力
    alias(libs.plugins.kotlin.compose)
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

android {
    namespace = "com.projectmaidgroup.platform.sherpa_onnx_asr"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<KotlinCompile>().configureEach {
    // 不依赖 android.kotlinOptions（某些插件组合下该扩展不存在）
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

