plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lqlq.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lqlq.browser"
        minSdk = 24
        targetSdk = 35
        versionCode = 64
        versionName = "0.28.0"
    }

    signingConfigs {
        create("lqlq") {
            storeFile = rootProject.file("keystore/lqlq-release.keystore")
            storePassword = "lqlq123456"
            keyAlias = "lqlq"
            keyPassword = "lqlq123456"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lqlq")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("lqlq")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
}
