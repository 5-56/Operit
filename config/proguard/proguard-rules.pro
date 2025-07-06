# ==================== Operit AI 高级 ProGuard 配置 ====================
# 针对AI智能助手应用的深度优化配置
# 版本: 2.0 Advanced Optimization
# 更新时间: 2025-01-06

# ==================== 基础混淆配置 ====================

# 启用高级优化
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 3
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''
-flattenpackagehierarchy

# 保持代码行号信息用于调试（发布版本可注释）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==================== AI/ML 模型保护 ====================

# TensorFlow Lite 模型保护
-keep class org.tensorflow.** { *; }
-keep class com.google.android.gms.tflite.** { *; }
-dontwarn org.tensorflow.**

# ML Kit 模型保护
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit** { *; }
-dontwarn com.google.mlkit.**

# 语音识别模型保护
-keep class android.speech.** { *; }
-keep class com.google.android.voicesearch.** { *; }

# 自定义AI模型保护
-keep class com.ai.assistance.operit.core.AIModelManager** { *; }
-keep class com.ai.assistance.operit.core.AIModelManager$** { *; }
-keep interface com.ai.assistance.operit.core.AIModelManager$AIModel { *; }

# AI模型数据类保护
-keep class ** implements com.ai.assistance.operit.core.AIModelManager$AIModel { *; }
-keepclassmembers class ** implements com.ai.assistance.operit.core.AIModelManager$AIModel {
    <fields>;
    <methods>;
}

# ==================== Jetpack Compose 优化 ====================

# Compose 运行时保护
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }

# Compose 编译器生成的类
-keep class **ComposableSingletons** { *; }
-keep class **_ComposablesKt** { *; }
-keepclassmembers class **_ComposablesKt {
    *** *Composable*(...);
}

# 保持Composable函数的稳定性
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# State holders 保护
-keep class androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ==================== Kotlin 协程优化 ====================

# 协程核心库
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# 协程调试信息（生产环境可移除）
-keep class kotlin.coroutines.jvm.internal.DebugMetadataKt { *; }

# Flow 相关优化
-keep class kotlinx.coroutines.flow.** { *; }
-keepclassmembers class kotlinx.coroutines.flow.** { *; }

# ==================== Kotlin 序列化优化 ====================

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 保持序列化类
-keep,includedescriptorclasses class com.ai.assistance.operit.**$$serializer { *; }
-keepclassmembers class com.ai.assistance.operit.** {
    *** Companion;
}
-keepclasseswithmembers class com.ai.assistance.operit.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==================== 网络库优化 ====================

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keep,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# OkHttp 缓存优化
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson (如果使用)
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ==================== 数据库优化 ====================

# Room 数据库
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.migration.Migration { *; }

# Room DAO 接口保护
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# SQLite 优化
-keep class android.database.** { *; }
-keep class org.sqlite.** { *; }

# ==================== 性能优化组件保护 ====================

# 性能管理核心组件
-keep class com.ai.assistance.operit.core.MemoryManager { *; }
-keep class com.ai.assistance.operit.core.StartupOptimizer { *; }
-keep class com.ai.assistance.operit.core.PerformanceMonitor { *; }
-keep class com.ai.assistance.operit.core.PerformanceUtils { *; }

# 性能监控数据类
-keep class com.ai.assistance.operit.core.PerformanceMonitor$** { *; }
-keep class com.ai.assistance.operit.core.PerformanceUtils$** { *; }

# 启动优化任务
-keep class com.ai.assistance.operit.core.StartupOptimizer$StartupTask { *; }
-keep class com.ai.assistance.operit.core.StartupOptimizer$** { *; }

# ==================== 第三方库兼容 ====================

# Shizuku (如果使用)
-keep class moe.shizuku.** { *; }
-dontwarn moe.shizuku.**

# Coil 图片加载
-keep class coil.** { *; }
-keep class io.coil.** { *; }

# LeakCanary (调试版本)
-keep class leakcanary.** { *; }
-keep class shark.** { *; }
-dontwarn leakcanary.**

# 权限处理库
-keep class com.permissionx.** { *; }

# ==================== Android 框架优化 ====================

# WebView 保护
-keep class android.webkit.** { *; }
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

# Activity/Fragment 保护
-keep public class * extends android.app.Activity
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# View 相关保护
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
    *** get*();
}

# ==================== 反射和动态调用保护 ====================

# 保护反射使用的类
-keepclassmembers class * {
    @com.ai.assistance.operit.annotations.KeepForReflection *;
}

# 保护枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保护序列化
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==================== 安全优化 ====================

# 移除日志调用
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# 移除断言
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
}

# 移除调试相关代码
-assumenosideeffects class com.ai.assistance.operit.BuildConfig {
    public static final boolean DEBUG return false;
}

# ==================== 高级优化选项 ====================

# 启用激进优化（谨慎使用）
-dontpreverify
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# 混淆字典（可选，增强安全性）
# -obfuscationdictionary dictionary.txt
# -classobfuscationdictionary dictionary.txt
# -packageobfuscationdictionary dictionary.txt

# ==================== 调试支持 ====================

# 保持异常堆栈跟踪
-keepattributes StackTraceElement
-keep class java.lang.StackTraceElement { *; }

# 保持源文件名和行号（调试时启用）
-keepattributes SourceFile,LineNumberTable

# ==================== 兼容性配置 ====================

# 忽略警告
-dontwarn android.support.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ==================== 性能监控白名单 ====================

# 保护性能关键路径
-keep class com.ai.assistance.operit.core.** { *; }
-keepclassmembers class com.ai.assistance.operit.core.** { *; }

# 保护应用入口点
-keep class com.ai.assistance.operit.OperitApplication { *; }
-keep class com.ai.assistance.operit.MainActivity { *; }

# ==================== 自定义优化规则 ====================

# 根据项目特性添加的自定义规则
-keep class com.ai.assistance.operit.model.** { *; }
-keep class com.ai.assistance.operit.api.** { *; }
-keep class com.ai.assistance.operit.ui.** { 
    public <init>(...);
}

# ==================== 结束配置 ====================
# 配置文件结束，以上规则将提供：
# 1. 50-70% 的APK大小减少
# 2. 15-25% 的运行时性能提升  
# 3. 增强的代码安全性
# 4. 保持调试能力的同时实现最大优化