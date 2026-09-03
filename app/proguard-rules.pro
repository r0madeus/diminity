# Diminity ProGuard & R8 Optimization Rules

# Keep Android Application Components
-keep class com.melihucgun.diminity.DimOverlayService { *; }
-keep class com.melihucgun.diminity.DiminityQuickSettingsTile { *; }
-keep class com.melihucgun.diminity.MainActivity { *; }

# Keep DataStore Data Models
-keep class com.melihucgun.diminity.data.** { *; }

# Keep Jetpack Compose Composables & Annotations
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
