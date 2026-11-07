# RetroCam Proguard rules
-keep class com.koushik.retrocam.data.model.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

