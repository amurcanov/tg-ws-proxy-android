# Add project specific ProGuard rules here.

# ─── JNA ───
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.swing.**
-dontwarn com.sun.jna.**
# Keep JNA interfaces and methods from being removed or obfuscated
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.Library { *; }
-keepclassmembers class * implements com.sun.jna.Library {
    <methods>;
}
# JNA callback & structure support
-keep class * implements com.sun.jna.Callback { *; }
-keep class * extends com.sun.jna.Structure { *; }

# ─── Our proxy library interface and NativeProxy object ───
-keep class com.amurcanov.tgwsproxy.NativeProxy { *; }
-keep interface com.amurcanov.tgwsproxy.ProxyLibrary { *; }
-keepclassmembers class * extends com.sun.jna.Library {
    <methods>;
}

# ─── ProxyService (foreground service, must not be obfuscated) ───
-keep class com.amurcanov.tgwsproxy.ProxyService { *; }

# ─── DataStore ───
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ─── Coroutines ───
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# ─── Compose ───
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep @androidx.compose.runtime.Composable class * { *; }

# ─── Keep native .so loaders ───
-keepclasseswithmembernames class * {
    native <methods>;
}
