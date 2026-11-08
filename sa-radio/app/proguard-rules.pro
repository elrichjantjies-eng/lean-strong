# The app has no reflection or serialization frameworks; Android components are kept via the manifest.

# The article reader's JavaScript calls Extractor.Bridge.html() by name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
