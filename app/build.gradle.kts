plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "net.hansen.ringthrough"
    compileSdk = 35
    defaultConfig {
        applicationId = "net.hansen.ringthrough"
        minSdk = 29
        // 34, not 35: from 35, setInterruptionFilter changes only an app-owned DND mode, not the phone DND.
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
