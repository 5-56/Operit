# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
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

# ===== 日志优化规则 =====
# 在生产环境中移除调试日志调用
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# 移除我们自定义的LogUtils调试日志
-assumenosideeffects class com.ai.assistance.operit.util.LogUtils {
    public static void d(...);
    public static void v(...);
    public static void debugOnly(...);
    public static void measureTime(...);
}

# ===== 性能优化规则 =====
# 优化Kotlin反射
-dontwarn kotlin.reflect.**
-keep class kotlin.reflect.** { *; }

# 保持Jetpack Compose相关类
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# 保持序列化相关
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# 保持Room数据库相关
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# ===== 第三方库优化 =====
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# 保持我们的数据模型
-keep class com.ai.assistance.operit.data.model.** { *; }
-keep class com.ai.assistance.operit.data.db.** { *; }

# ===== 安全优化 =====
# 移除敏感信息
-assumenosideeffects class java.lang.System {
    public static void setProperty(java.lang.String, java.lang.String);
}

# 代码混淆加强
-repackageclasses ''
-allowaccessmodification
-printmapping mapping.txt

# ===== 资源优化 =====
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保持自定义View
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
    *** get*();
}

# 保留 Shizuku 相关类
-keep class rikka.shizuku.** { *; }

# 保留自定义的 UserService 类及 AIDL 接口
-keep class com.lyneon.cytoidinfoquerier.service.FileService { *; }
-keep class com.lyneon.cytoidinfoquerier.IFileService { *; }
-keep interface com.lyneon.cytoidinfoquerier.IFileService { *; }

# 保留 ServiceConnection 和 Binder 相关方法
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Rules to suppress R8 warnings about missing classes
# SVG Support
-dontwarn com.caverock.androidsvg.SVG
-dontwarn com.caverock.androidsvg.SVGParseException

# Java AWT classes (not available on Android)
-dontwarn java.awt.**
-dontwarn java.awt.color.**
-dontwarn java.awt.geom.**
-dontwarn java.awt.image.**

# Image processing libraries
-dontwarn javax.imageio.**
-dontwarn javax.xml.stream.**

# Saxon XML
-dontwarn net.sf.saxon.**

# Apache Batik
-dontwarn org.apache.batik.**

# OSGi Framework
-dontwarn org.osgi.framework.**

# XZ compression
-dontwarn org.tukaani.xz.**

# POI dependencies
-dontwarn org.apache.poi.xslf.draw.**
-dontwarn org.apache.poi.xslf.usermodel.**
-dontwarn org.apache.poi.util.**

# PDF Box dependencies
-dontwarn org.apache.pdfbox.**
-dontwarn org.apache.fontbox.**

# Apache commons compress
-dontwarn org.apache.commons.compress.archivers.sevenz.**

# xmlbeans
-dontwarn org.apache.xmlbeans.**

# GIF handling
-dontwarn pl.droidsonroids.gif.**

# Reactor BlockHound integration with Netty
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
-dontwarn io.netty.util.internal.Hidden$NettyBlockHoundIntegration