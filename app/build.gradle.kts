plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kashtan.offtimer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kashtan.offtimer"
        minSdk = 30
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    // 毎回同じ鍵で署名するので、新しい版を上書きインストールできる
    signingConfigs {
        create("fixed") {
            storeFile = rootProject.file("keystore/offtimer.jks")
            storePassword = "offtimer"
            keyAlias = "offtimer"
            keyPassword = "offtimer"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
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
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
