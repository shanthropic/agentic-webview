# Preserve JS bridge methods — R8 cannot see calls from JavaScript
-keepclassmembers class dev.shantoislam.agenticwebview.AgenticWebView$JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve SDK model classes used in serialization
-keep class dev.shantoislam.agenticwebview.models.** { *; }
