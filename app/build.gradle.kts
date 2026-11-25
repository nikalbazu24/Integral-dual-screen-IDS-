plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val reportUrl = System.getenv("IDS_REPORT_URL") ?: ""

android {
    namespace = "com.nikalbazu.ids"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nikalbazu.ids"
        minSdk = 31
        targetSdk = 33
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        buildConfigField("String", "REPORT_URL", "\"$reportUrl\"")
        buildConfigField("String", "REPO", "\"nikalbazu24/Integral-dual-screen-IDS-\"")
    }

    // Firma: si existe la llave en los secretos del repositorio se usa esa; si no, la llave de depuración guardada en caché.
    val ksFile = System.getenv("IDS_KEYSTORE_FILE")
    signingConfigs {
        if (ksFile != null) {
            create("release") {
                storeFile = file(ksFile)
                storePassword = System.getenv("IDS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("IDS_KEY_ALIAS")
                keyPassword = System.getenv("IDS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksFile != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
