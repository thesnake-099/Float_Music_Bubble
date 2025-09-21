# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# IMPROVEMENT 8: ProGuard rules to prevent crashing in a release build.

# Keep an annotation used by Google Ads
#-keep public @com.google.android.gms.common.annotation.KeepForSdkExtends

# Keep RevenueCat classes that are needed for reflection
#-keep class com.revenuecat.purchases.** { *; }
#-keep public class com.android.vending.billing.IInAppBillingService

# Keep AdMob classes
#-keep class com.google.android.gms.ads.** { *; }

# Keep TapTargetView library classes
#-keep class com.getkeepsafe.taptargetview.** { *; }

# ==============================================================================
# This is the rule that has been updated for your new package name.
# It ensures that the JavaScript bridge in your FloatingService continues to work
# after obfuscation.
-keepclassmembers class com.meni.bubble_app.FloatingService$WebAppInterface {
   @android.webkit.JavascriptInterface <methods>;
}
# ==============================================================================

# Keep your Application, Activities, Services, etc.
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service