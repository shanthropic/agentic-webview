# Preserve JS bridge methods — R8 cannot see calls from JavaScript
-keepclassmembers class com.shantoislamdev.agenticwebview.AgenticWebView$JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve SDK model classes used in serialization
-keep class com.shantoislamdev.agenticwebview.models.** { *; }
