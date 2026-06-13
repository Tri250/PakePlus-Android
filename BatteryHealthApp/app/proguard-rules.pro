# Battery Health App ProGuard Rules
# 电池健康度分析工具 ProGuard 配置

# ==================== 基础配置 ====================
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# ==================== 优化选项 ====================
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable
-allowaccessmodification
-repackageclasses ''
-adaptclassstrings

# ==================== 保留关键类 ====================
# 保留Application和Activity
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# 保留WebView相关
-keep class android.webkit.** { *; }
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, ...);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, ...);
}

# ==================== 保留JavaScript接口 ====================
# 保留所有JavaScript接口方法
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ==================== 保留资源 ====================
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ==================== 保留native方法 ====================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ==================== 保留自定义View ====================
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ==================== 保留Parcelable ====================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ==================== 保留Serializable ====================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==================== 保留枚举 ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== 移除日志 ====================
# 移除调试日志（Release版本）
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ==================== 保留异常处理 ====================
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

# ==================== 混淆配置 ====================
# 不混淆特定包
-keep class com.batteryhealth.app.** { *; }

# ==================== 第三方库保留 ====================
# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Material Design
-keep class com.google.android.material.** { *; }

# ==================== 警告抑制 ====================
-dontwarn android.webkit.**
-dontwarn androidx.**
-dontwarn com.google.android.material.**