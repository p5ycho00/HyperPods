plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

extra["androidMinSdkVersion"] = 31
extra["androidTargetSdkVersion"] = 36
// compileSdk 37 (with minorApiLevel) is what AGP 9 requires here: miuix 0.9.4, Compose 1.12 and
// libxposed api 102 all declare an AAR metadata minCompileSdk of 37.
extra["androidCompileSdkVersion"] = 37
extra["androidCompileSdkVersionMinor"] = 0
extra["androidBuildToolsVersion"] = "37.0.0"
extra["androidSourceCompatibility"] = JavaVersion.VERSION_21
extra["androidTargetCompatibility"] = JavaVersion.VERSION_21

// ---------------------------------------------------------------------------
// Version stamping
//
// The version lives in version.properties and is bumped here, so the same versionCode can never
// ship twice by accident. `build` is also the trailing component shown in 关于.
// ---------------------------------------------------------------------------
val versionFile = file("version.properties")
val versionProperties = java.util.Properties().apply {
    if (versionFile.exists()) versionFile.inputStream().use { load(it) }
}
val versionMajor = versionProperties.getProperty("major", "1").toIntOrNull() ?: 1
val versionMinor = versionProperties.getProperty("minor", "0").toIntOrNull() ?: 0
val versionBuild = (versionProperties.getProperty("build", "0").toIntOrNull() ?: 0) + 1
versionProperties.setProperty("major", versionMajor.toString())
versionProperties.setProperty("minor", versionMinor.toString())
versionProperties.setProperty("build", versionBuild.toString())
versionFile.outputStream().use {
    versionProperties.store(it, "Auto-incremented on every build; do not edit by hand.")
}

logger.lifecycle("HyperPods version $versionMajor.$versionMinor.$versionBuild (versionCode $versionBuild)")

extra["hyperPodsVersionCode"] = versionBuild
extra["hyperPodsVersionName"] = "$versionMajor.$versionMinor.$versionBuild"
