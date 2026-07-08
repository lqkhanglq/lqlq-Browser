plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val dynamicLootEndpoint = providers.gradleProperty("LQLQ_DYNAMIC_LOOT_ENDPOINT")
    .orElse("")
    .get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val releaseStoreFile = providers.gradleProperty("LQLQ_RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("LQLQ_RELEASE_STORE_FILE"))
val releaseStorePassword = providers.gradleProperty("LQLQ_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("LQLQ_RELEASE_STORE_PASSWORD"))
val releaseKeyAlias = providers.gradleProperty("LQLQ_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("LQLQ_RELEASE_KEY_ALIAS"))
val releaseKeyPassword = providers.gradleProperty("LQLQ_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("LQLQ_RELEASE_KEY_PASSWORD"))

val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.orNull.isNullOrBlank() }

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
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
