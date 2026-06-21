# ProGuard规则 for Battery Health App
# 安全策略：仅保留必要的类和方法，其余全部混淆，防止逆向工程

# === 保留 Application 入口 ===
-keep class com.batteryhealth.app.BatteryHealthApplication { *; }
-keep class com.batteryhealth.app.MainActivity { *; }
-keep class com.batteryhealth.app.ui.error.ErrorActivity { *; }

# === 保留数据模型（Room 实体 + Gson 序列化） ===
-keep class com.batteryhealth.app.data.model.** { *; }
-keep class com.batteryhealth.app.data.model.BatteryInfo { *; }
-keep class com.batteryhealth.app.data.model.PerformanceData { *; }
-keep class com.batteryhealth.app.data.model.PowerHistory { *; }
-keep class com.batteryhealth.app.data.model.DeviceConfig { *; }
-keep class com.batteryhealth.app.data.model.HealthCheckResult { *; }

# === 保留 Service（系统通过反射启动） ===
-keep class com.batteryhealth.app.service.BatteryMonitorService { *; }
-keep class com.batteryhealth.app.service.ChargingMonitorService { *; }

# === 保留内部接口和回调 ===
-keep class com.batteryhealth.app.service.BatteryMonitorService$OnBatteryDataListener { *; }
-keep class com.batteryhealth.app.service.BatteryMonitorService$NamedThreadFactory { *; }
-keep class com.batteryhealth.app.service.ChargingMonitorService$OnChargingDataListener { *; }
-keep class com.batteryhealth.app.service.ChargingMonitorService$ChargingSummary { *; }

# === 保留自定义 View（XML 反射创建） ===
-keep class com.batteryhealth.app.ui.view.CustomBottomNavigationView { *; }
-keep class com.batteryhealth.app.ui.view.CustomBottomNavigationView$NavItem { *; }
-keep class com.batteryhealth.app.ui.view.HealthRingView { *; }

# === 保留 Fragment（反射创建） ===
-keep class * extends androidx.fragment.app.Fragment { *; }

# === 保留 DAO 接口（Room 编译期生成） ===
-keep class com.batteryhealth.app.data.database.BatteryInfoDao { *; }
-keep class com.batteryhealth.app.data.database.PowerHistoryDao { *; }
-keep class com.batteryhealth.app.data.database.PerformanceDataDao { *; }
-keep class com.batteryhealth.app.data.database.AppDatabase { *; }
-keep class com.batteryhealth.app.data.database.AppDatabase_Impl { *; }

# === 保留 healthcheck checker 接口 ===
-keep class com.batteryhealth.app.utils.healthcheck.IHealthChecker { *; }
-keep class com.batteryhealth.app.utils.healthcheck.HealthCheckEngine { *; }

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

# 移除日志
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# R8 优化
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'com.batteryhealth.app.internal'