import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing key lives OUTSIDE the repo (never commit it).
// Path can be overridden with the UVWIDGET_KEY_PROPS environment variable.
val keyPropsFile = file(
    System.getenv("UVWIDGET_KEY_PROPS") ?: "C:/Users/shaib/Documents/UvWidget-signing/key.properties"
)
val keyProps = Properties().apply { if (keyPropsFile.exists()) keyPropsFile.inputStream().use { load(it) } }

android {
    namespace = "com.shai.uvwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shai.uvwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.7"
        // Where the app looks for new versions (GitHub Releases).
        buildConfigField("String", "GITHUB_REPO", "\"shaiberkov/uv-index-widget\"")
    }

    signingConfigs {
        if (keyPropsFile.exists()) {
            create("release") {
                storeFile = file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
