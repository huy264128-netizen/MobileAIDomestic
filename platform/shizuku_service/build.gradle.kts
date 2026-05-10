plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.projectmaidgroup.platform.shizuku_service"
    compileSdk = 36

    defaultConfig {
        minSdk = 31

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Shizuku UserService 的 ComponentName 包名必须是「已安装主应用」的 applicationId，不能写库模块 namespace。
        buildConfigField(
            "String",
            "SHIZUKU_HOST_APPLICATION_ID",
            "\"com.projectmaidgroup.mobileaidomestic\"",
        )
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
    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    api(libs.shizuku.api)
    api(libs.shizuku.provider)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
