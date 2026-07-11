plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val dynamicLootEndpoint = providers.gradleProperty("LQLQ_DYNAMIC_LOOT_ENDPOINT")
    .orElse("")
    .get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val bundledLqlqKeystore = rootProject.file("keystore/lqlq-release.keystore")

android {
    namespace = "com.lqlq.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lqlq.browser"
        minSdk = 24
        targetSdk = 35
        versionCode = 79
        versionName = "0.32.12"
        buildConfigField("String", "DYNAMIC_LOOT_ENDPOINT", "\"$dynamicLootEndpoint\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("lqlq") {
            if (bundledLqlqKeystore.exists()) {
                storeFile = bundledLqlqKeystore
                storePassword = "lqlq123456"
                keyAlias = "lqlq"
                keyPassword = "lqlq123456"
            } else {
                initWith(getByName("debug"))
            }
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas/automation")
    arg("room.incremental", "true")
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
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.13")
}
