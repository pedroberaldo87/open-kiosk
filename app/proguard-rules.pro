# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# Keep Room entities
-keep class com.openkiosk.data.local.entity.** { *; }

# Keep JavaScript interface
-keepclassmembers class com.openkiosk.presentation.component.KioskJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
