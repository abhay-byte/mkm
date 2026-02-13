// F-Droid reproducible builds: disable baseline profiles using Groovy script
apply(from = "fix-baseline-profiles.gradle")

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace = "com.ivarna.mkm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ivarna.mkm"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndkVersion = "29.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Disable dependency metadata block for F-Droid
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/repos/mkm-release.jks")
            storePassword = "mkm2026release"
            keyAlias = "mkm-key"
            keyPassword = "mkm2026release"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Disable baseline profiles for F-Droid reproducible builds
            packaging {
                resources.excludes.add("META-INF/**")
                resources.excludes.add("**.prof")
                resources.excludes.add("assets/dexopt/baseline.prof")
            }
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

    androidResources {
        // Disable PNG crunching for reproducible builds
        @Suppress("UnstableApiUsage")
        ignoreAssetsPattern = "!.svn:!.git:.*:!CVS:!thumbs.db:!picasa.ini:!*.scc:*~"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha12")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Shizuku API & Provider - Disabled for v1.0, will be added in v1.1
    // The Shizuku 13.x API requires significant refactoring
    // implementation("dev.rikka.shizuku:api:13.1.5")
    // implementation("dev.rikka.shizuku:provider:13.1.5")
    
    // libsu for robust Root access
    implementation("com.github.topjohnwu.libsu:core:6.0.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
