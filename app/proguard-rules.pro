# Diminity ProGuard & R8 Optimization Rules

# Keep Android Application Components
-keep class com.r0madeus.diminity.DimOverlayService { *; }
-keep class com.r0madeus.diminity.DiminityQuickSettingsTile { *; }
-keep class com.r0madeus.diminity.MainActivity { *; }

# Keep DataStore Data Models
-keep class com.r0madeus.diminity.data.** { *; }

# Keep Jetpack Compose Composables & Annotations
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
