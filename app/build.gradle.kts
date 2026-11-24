import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- Release signing ----
// On GitHub Actions the key comes from repository secrets (env vars).
// Locally it comes from a key.properties file OUTSIDE the repo (never commit it).
val keyPropsFile = file(
    System.getenv("UVWIDGET_KEY_PROPS") ?: "C:/Users/shaib/Documents/UvWidget-signing/key.properties"
)
val keyProps = Properties().apply { if (keyPropsFile.exists()) keyPropsFile.inputStream().use { load(it) } }
fun signing(env: String, prop: String): String? = System.getenv(env) ?: keyProps.getProperty(prop)
val keystorePath = signing("UVWIDGET_KEYSTORE_FILE", "storeFile")

// ---- Version ----
// CI passes -PappVersionName=1.8 -PappVersionCode=123; local builds use the defaults.
val appVersionName = (findProperty("appVersionName") as String?) ?: "1.7"
val appVersionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 8

android {
    namespace = "com.shai.uvwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shai.uvwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        // Where the app looks for new versions (GitHub Releases).
        buildConfigField("String", "GITHUB_REPO", "\"shaiberkov/uv-index-widget\"")
    }

    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = signing("UVWIDGET_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signing("UVWIDGET_KEY_ALIAS", "keyAlias")
                keyPassword = signing("UVWIDGET_KEY_PASSWORD", "keyPassword")
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
