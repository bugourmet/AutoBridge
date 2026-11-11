plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release workflow passes -PappVersion=1.2.3 (from tag v1.2.3).
val appVersion = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "1.0.0"
val (major, minor, patch) = requireNotNull(Regex("""(\d{1,3})\.(\d{1,2})\.(\d{1,2})(-.+)?""").matchEntire(appVersion)) {
    "appVersion must look like 1.2.3, was $appVersion"
}.destructured
val appVersionCode = (major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()).coerceAtLeast(1)

val releaseKeystore: String? = System.getenv("AUTOBRIDGE_KEYSTORE")

android {
    namespace = "io.github.autobridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.autobridge"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("AUTOBRIDGE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AUTOBRIDGE_KEY_ALIAS")
                keyPassword = System.getenv("AUTOBRIDGE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.car.app)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
