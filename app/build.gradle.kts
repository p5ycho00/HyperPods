@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val androidCompileSdkVersion = rootProject.extra["androidCompileSdkVersion"] as Int
val androidCompileSdkVersionMinor = rootProject.extra["androidCompileSdkVersionMinor"] as Int
val androidBuildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String
val androidMinSdkVersion = rootProject.extra["androidMinSdkVersion"] as Int
val androidTargetSdkVersion = rootProject.extra["androidTargetSdkVersion"] as Int
val androidSourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
val androidTargetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
val hyperPodsVersionCode = rootProject.extra["hyperPodsVersionCode"] as Int
val hyperPodsVersionName = rootProject.extra["hyperPodsVersionName"] as String

android {
    namespace = "com.hyperpods"
    compileSdk {
        version = release(androidCompileSdkVersion) {
            minorApiLevel = androidCompileSdkVersionMinor
        }
    }
    buildToolsVersion = androidBuildToolsVersion

    defaultConfig {
        applicationId = "com.hyperpods"
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = hyperPodsVersionCode
        versionName = hyperPodsVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    compileOnly(libs.libxposed.api)

    implementation(libs.libxposed.service)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigationevent.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.squircle)
}
