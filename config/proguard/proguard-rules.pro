# ==================== Operit AI 高级代码混淆和优化规则 ====================
# 专为AI智能助手应用定制的ProGuard/R8配置

# ==================== 基础优化配置 ====================

# 启用优化
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# 保持源文件名（便于调试，release版本可以移除）
-keepattributes SourceFile,LineNumberTable

# 保持注解
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature

# ==================== Android框架保护 ====================

# 保持Activity、Service、BroadcastReceiver和ContentProvider
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# 保持Fragment
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Fragment

# 保持View构造函数
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保持自定义View
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
    *** get*();
}

# ==================== Jetpack Compose优化 ====================

# 保持Compose相关类
-keep class androidx.compose.** { *; }
-keep class androidx.activity.compose.** { *; }
-keep class androidx.navigation.compose.** { *; }
-keep class androidx.lifecycle.viewmodel.compose.** { *; }

# 保持Composable函数
-keep @androidx.compose.runtime.Composable class * { *; }
-keep class **.*ComposableSingletons* { *; }

# 保持State和MutableState
-keep class androidx.compose.runtime.State { *; }
-keep class androidx.compose.runtime.MutableState { *; }

# ==================== Kotlin和协程优化 ====================

# 保持Kotlin元数据
-keep class kotlin.Metadata { *; }

# 保持协程相关
-keepclassmembernames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepclassmembernames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 保持Kotlin数据类
-keep @kotlin.Metadata class * { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }

# 保持序列化相关
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==================== AI和ML模型优化 ====================

# 保持TensorFlow Lite相关
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }

# 保持ML Kit相关
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.tasks.** { *; }

# 保持AI模型相关类
-keep class com.ai.assistance.operit.core.AIModelManager { *; }
-keep class com.ai.assistance.operit.core.AIModelManager$* { *; }

# 保持语音识别相关
-keep class android.speech.** { *; }
-keep class com.google.android.gms.speech.** { *; }

# ==================== 网络和API优化 ====================

# 保持Retrofit和OkHttp
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.squareup.okhttp3.** { *; }

# 保持JSON序列化
-keep class com.google.gson.** { *; }
-keep class kotlinx.serialization.** { *; }

# 保持API响应类
-keep class com.ai.assistance.operit.api.** { *; }
-keep class com.ai.assistance.operit.data.model.** { *; }

# ==================== 数据库优化 ====================

# 保持Room数据库相关
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }

# 保持Entity和DAO
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# 保持数据库实体
-keep class com.ai.assistance.operit.data.db.** { *; }

# ==================== 性能优化组件保护 ====================

# 保持我们的性能优化组件
-keep class com.ai.assistance.operit.core.MemoryManager { *; }
-keep class com.ai.assistance.operit.core.StartupOptimizer { *; }
-keep class com.ai.assistance.operit.core.PerformanceMonitor { *; }
-keep class com.ai.assistance.operit.core.PerformanceUtils { *; }

# 保持性能监控相关的枚举和数据类
-keep enum com.ai.assistance.operit.core.PerformanceUtils$* { *; }
-keep class com.ai.assistance.operit.core.PerformanceUtils$* { *; }

# ==================== 第三方库优化 ====================

# Shizuku
-keep class moe.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }

# Coil图片加载
-keep class coil.** { *; }

# LeakCanary（仅debug版本）
-dontwarn com.squareup.leakcanary.**

# ==================== 安全和反射优化 ====================

# 移除日志调用（release版本）
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# 移除断言
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNull(java.lang.Object, java.lang.String);
}

# ==================== 字符串和资源优化 ====================

# 优化字符串拼接
-optimizations !code/simplification/string

# 保持资源ID
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保持BuildConfig
-keep class com.ai.assistance.operit.BuildConfig { *; }

# ==================== WebView和JavaScript优化 ====================

# 保持WebView相关
-keep class android.webkit.** { *; }
-keep class * implements android.webkit.WebViewClient { *; }
-keep class * implements android.webkit.WebChromeClient { *; }

# 保持JavaScript接口
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ==================== 本地库和JNI优化 ====================

# 保持native方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保持JNI相关
-keep class com.ai.assistance.operit.dragonbones.** { *; }

# ==================== 其他优化配置 ====================

# 不混淆枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保持Parcelable实现
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保持Serializable实现
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==================== 调试信息保留（可选） ====================

# 保留行号信息（便于调试崩溃）
-keepattributes SourceFile,LineNumberTable

# 保留变量名（便于调试，release版本可移除）
-keepattributes LocalVariableTable,LocalVariableTypeTable

# ==================== 性能优化提示 ====================

# 输出映射文件
-printmapping mapping.txt

# 输出种子文件
-printseeds seeds.txt

# 输出未使用的代码
-printusage usage.txt

# 优化详情
-verbose

# ==================== 错误抑制 ====================

# 忽略警告
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**