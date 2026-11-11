# WebView calls these annotated bridge methods by name from bundled JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep source locations so release crashes can be retraced using mapping.txt.
-keepattributes SourceFile,LineNumberTable
