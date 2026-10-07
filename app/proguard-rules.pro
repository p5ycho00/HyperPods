# The LSPosed entry class is loaded by name from assets/xposed_init, so keep it.
-keep class com.hyperpods.hook.HyperPodsEntry { *; }
-keep class com.hyperpods.hook.** { *; }

# libxposed service bridge
-keep class io.github.libxposed.service.** { *; }

# Kotlin serialization generated serializers for the navigation routes
-keepclassmembers class com.hyperpods.ui.nav.** {
    *** Companion;
}
-keepclasseswithmembers class com.hyperpods.ui.nav.** {
    kotlinx.serialization.KSerializer serializer(...);
}
