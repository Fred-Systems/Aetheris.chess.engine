plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fredsystems.aetheris"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fredsystems.aetheris"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "1.0.20"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-O3 -fno-exceptions -fno-rtti"
                targets += listOf("aetheris", "aetheris_engine", "aetheris_classical")
            }
        }
    }

    signingConfigs {
        create("stableRelease") {
            val keystorePath = System.getenv("AETHERIS_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("AETHERIS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AETHERIS_KEY_ALIAS")
                keyPassword = System.getenv("AETHERIS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // A stable private signing key is required for in-place updates.
            // CI sets AETHERIS_KEYSTORE_PATH when the repository secrets exist.
            signingConfig = if (System.getenv("AETHERIS_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("stableRelease")
            }
            if (project.hasProperty("crashReport")) {
                applicationIdSuffix = ".crashreport"
                versionNameSuffix = "-crashreport"
            }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.github.bhlangonijr:chesslib:1.3.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}