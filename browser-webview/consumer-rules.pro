# Preserve the narrow JavaScript response bridge used by the versioned runtime protocol.
-keepclassmembers class dev.shantoislam.agenticwebview.webview.protocol.RuntimeProtocolBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
