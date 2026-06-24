# ProGuard规则 for Battery Health App

# 保留 Application 和入口 Activity（避免 AndroidManifest 反射失败）
-keep class com.batteryhealth.app.BatteryHealthApplication { *; }
-keep class com.batteryhealth.app.MainActivity { *; }

# 保留 Hilt 生成的组件（Dagger/Hilt 依赖注入）
-keep class com.batteryhealth.app.di.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# 保留 Room 实体和 DAO（由 Room 注解处理器生成，需保留）
-keep @androidx.room.Entity class com.batteryhealth.app.data.model.** { *; }
-keep @androidx.room.Dao interface com.batteryhealth.app.data.database.** { *; }
-keep class com.batteryhealth.app.data.database.AppDatabase { *; }
-keep class com.batteryhealth.app.data.database.AppDatabase_Impl { *; }

# 保留 Retrofit 接口（运行时反射调用）
-keep interface com.batteryhealth.app.data.api.** { *; }

# 保留 Gson 序列化使用的数据模型（避免字段名混淆后 JSON 解析失败）
-keep class com.batteryhealth.app.data.model.** { *; }

# OkHttp - 忽略缺失的平台类
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }

# Room数据库
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * {
    @androidx.room.PrimaryKey <fields>;
    @androidx.room.ColumnInfo <fields>;
    @androidx.room.Embedded <fields>;
    <init>();
}
-keepclassmembers class * {
    @androidx.room.Dao <methods>;
    @androidx.room.Query <methods>;
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Lottie
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# 保留序列化类
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留Parcelable类
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 仅保留应用自身 R 类（避免全局 ** 阻止 R8 资源缩减）
-keepclassmembers class com.batteryhealth.app.R$* {
    public static <fields>;
}

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# PermissionX
-keep class com.permissionx.** { *; }
-dontwarn com.permissionx.**

# EncryptedSharedPreferences / security-crypto
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Material Components 与 AndroidX UI 组件：避免 release 混淆后布局解析失败
-keep class com.google.android.material.card.MaterialCardView { *; }
-keep class androidx.core.widget.NestedScrollView { *; }
-keep class androidx.viewpager2.widget.ViewPager2 { *; }
-keep class androidx.recyclerview.widget.RecyclerView { *; }
-keep class androidx.coordinatorlayout.widget.CoordinatorLayout { *; }

# Fragment 与自定义 View
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class com.batteryhealth.app.ui.view.CustomBottomNavigationView { *; }

# 保留所有 public View 构造函数，防止 XML 反射创建失败
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keep public class * extends android.view.ViewGroup {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 移除日志（保留 Log.e 以便在 release 中仍能捕获崩溃关键信息）
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
}

# 优化
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify