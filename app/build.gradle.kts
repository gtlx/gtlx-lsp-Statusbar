plugins {
    id("com.android.application")
    kotlin("android") version "1.9.22"
}

android {
    namespace = "com.gtlx.statusbardrift"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gtlx.statusbardrift"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // LSPosed / Xposed API —— compileOnly，不打进 apk
    compileOnly(files("libs/xposed-api-82.jar"))
    // Kotlin 标准库（轻量）
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
}
