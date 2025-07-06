package com.ai.assistance.operit.core.application

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil.ImageLoader
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.mcp.MCPImageCache
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.initAndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.initUserPreferencesManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.SerializationSetup
import com.ai.assistance.operit.util.TextSegmenter
// 新增性能优化组件导入
import com.ai.assistance.operit.core.MemoryManager
import com.ai.assistance.operit.core.StartupOptimizer
import com.ai.assistance.operit.core.AIModelManager
import com.ai.assistance.operit.core.PerformanceMonitor
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/** Application class for Operit */
class OperitApplication : Application() {

    companion object {
        /** Global JSON instance with custom serializers */
        lateinit var json: Json
            private set

        // 全局应用实例
        lateinit var instance: OperitApplication
            private set

        // 全局ImageLoader实例，用于高效缓存图片
        lateinit var globalImageLoader: ImageLoader
            private set

        // 性能优化组件实例
        lateinit var memoryManager: MemoryManager
            private set
            
        lateinit var startupOptimizer: StartupOptimizer
            private set
            
        lateinit var aiModelManager: AIModelManager
            private set
            
        lateinit var performanceMonitor: PerformanceMonitor
            private set

        private const val TAG = "OperitApplication"
    }

    // 应用级协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 懒加载数据库实例
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ==================== 性能优化组件初始化 ====================
        Log.i(TAG, "开始初始化性能优化组件...")
        
        // 1. 启动优化器（最高优先级，尽早初始化）
        startupOptimizer = StartupOptimizer.getInstance()
        startupOptimizer.initialize(this)
        
        // 2. 内存管理器
        memoryManager = MemoryManager.getInstance(this)
        
        // 3. AI模型管理器
        aiModelManager = AIModelManager.getInstance(this)
        
        // 4. 性能监控器
        performanceMonitor = PerformanceMonitor.getInstance(this)
        performanceMonitor.startMonitoring()
        
        // 记录应用启动事件
        performanceMonitor.recordEvent("app_launches")
        
        Log.i(TAG, "性能优化组件初始化完成")

        // Initialize the JSON serializer with our custom module
        json = Json {
            serializersModule = SerializationSetup.module
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
            encodeDefaults = true
        }

        // 初始化用户偏好管理器
        initUserPreferencesManager(applicationContext)

        // 初始化Android权限偏好管理器
        initAndroidPermissionPreferences(applicationContext)

        // 在最早时机初始化并应用语言设置
        initializeAppLanguage()

        // 初始化AndroidShellExecutor上下文
        AndroidShellExecutor.setContext(applicationContext)

        // 初始化图片缓存
        MCPImageCache.initialize(applicationContext)

        // 初始化TextSegmenter
        applicationScope.launch { TextSegmenter.initialize(applicationContext) }

        // 预加载数据库
        applicationScope.launch {
            // 简单访问数据库以触发初始化
            database.problemDao().getProblemCount()
        }

        // 初始化全局图片加载器，设置强大的缓存策略
        globalImageLoader =
                ImageLoader.Builder(this)
                        .crossfade(true)
                        .respectCacheHeaders(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCache {
                            DiskCache.Builder()
                                    .directory(filesDir.resolve("image_cache"))
                                    .maxSizeBytes(50 * 1024 * 1024) // 50MB磁盘缓存上限，比百分比更精确
                                    .build()
                        }
                        .memoryCache {
                            // 设置内存缓存最大大小为应用可用内存的15%
                            coil.memory.MemoryCache.Builder(this).maxSizePercent(0.15).build()
                        }
                        .build()
        
        // ==================== 延迟初始化任务 ====================
        scheduleDelayedInitialization()
        
        Log.i(TAG, "OperitApplication 初始化完成")
    }
    
    /**
     * 调度延迟初始化任务
     */
    private fun scheduleDelayedInitialization() {
        // 添加启动任务
        startupOptimizer.addTasks(
            StartupOptimizer.STAGE_LAZY to StartupOptimizer.SimpleStartupTask(
                name = "preload_ai_models",
                dependencies = emptyList()
            ) { application ->
                Log.d(TAG, "开始预加载AI模型...")
                aiModelManager.preloadCoreModels()
                Log.d(TAG, "AI模型预加载完成")
            },
            
            StartupOptimizer.STAGE_BACKGROUND to StartupOptimizer.SimpleStartupTask(
                name = "cleanup_expired_cache",
                dependencies = emptyList()
            ) { application ->
                Log.d(TAG, "清理过期缓存...")
                memoryManager.clearCache()
                aiModelManager.cleanupExpiredModels()
                Log.d(TAG, "缓存清理完成")
            }
        )
        
        // 每30分钟执行一次内存清理
        startupOptimizer.executeDelayed(30 * 60 * 1000L) {
            Log.d(TAG, "执行定时内存清理...")
            memoryManager.clearCache()
            System.gc()
        }
    }

    /** 初始化应用语言设置 */
    private fun initializeAppLanguage() {
        try {
            // 同步获取已保存的语言设置
            val languageCode = runBlocking {
                try {
                    // 使用更安全的方式检查preferencesManager
                    val manager = runCatching { preferencesManager }.getOrNull()
                    if (manager != null) {
                        manager.appLanguage.first()
                    } else {
                        UserPreferencesManager.DEFAULT_LANGUAGE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "获取语言设置失败", e)
                    UserPreferencesManager.DEFAULT_LANGUAGE
                }
            }

            Log.d(TAG, "获取语言设置: $languageCode")

            // 立即应用语言设置
            val locale = Locale(languageCode)
            // 设置默认语言
            Locale.setDefault(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 使用AppCompatDelegate API
                val localeList = LocaleListCompat.create(locale)
                AppCompatDelegate.setApplicationLocales(localeList)
                Log.d(TAG, "使用AppCompatDelegate设置语言: $languageCode")
            } else {
                // 较旧版本Android - 此处使用的部分更新将在attachBaseContext中完成更完整更新
                val config = Configuration()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val localeList = LocaleList(locale)
                    LocaleList.setDefault(localeList)
                    config.setLocales(localeList)
                } else {
                    config.locale = locale
                }

                resources.updateConfiguration(config, resources.displayMetrics)
                Log.d(TAG, "使用Configuration设置语言: $languageCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化语言设置失败", e)
        }
    }

    override fun attachBaseContext(base: Context) {
        // 在基础上下文附加前应用语言设置
        try {
            val code = LocaleUtils.getCurrentLanguage(base)
            val locale = Locale(code)
            val config = Configuration(base.resources.configuration)

            // 设置语言配置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                config.setLocales(localeList)
            } else {
                config.locale = locale
                Locale.setDefault(locale)
            }

            // 使用createConfigurationContext创建新的上下文
            val context = base.createConfigurationContext(config)
            super.attachBaseContext(context)
            Log.d(TAG, "成功应用基础上下文语言: $code")
        } catch (e: Exception) {
            Log.e(TAG, "应用基础上下文语言失败", e)
            super.attachBaseContext(base)
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        Log.i(TAG, "应用终止，开始清理资源...")
        
        // ==================== 性能优化组件清理 ====================
        try {
            // 停止性能监控
            if (::performanceMonitor.isInitialized) {
                performanceMonitor.stopMonitoring()
                Log.d(TAG, "性能监控已停止")
            }
            
            // 清理内存管理器
            if (::memoryManager.isInitialized) {
                memoryManager.stopMemoryMonitoring()
                Log.d(TAG, "内存管理器已清理")
            }
            
            // 清理AI模型管理器
            if (::aiModelManager.isInitialized) {
                aiModelManager.cleanup()
                Log.d(TAG, "AI模型管理器已清理")
            }
            
            // 清理启动优化器
            if (::startupOptimizer.isInitialized) {
                startupOptimizer.cleanup()
                Log.d(TAG, "启动优化器已清理")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "清理性能优化组件失败", e)
        }
        
        // 在应用终止时关闭LocalWebServer服务器
        try {
            val webServer = LocalWebServer.getInstance(applicationContext)
            if (webServer.isRunning()) {
                webServer.stop()
                Log.d(TAG, "应用终止，已关闭本地Web服务器")
            }
        } catch (e: Exception) {
            Log.e(TAG, "关闭本地Web服务器失败: ${e.message}", e)
        }
        
        Log.i(TAG, "应用资源清理完成")
    }
    
    /**
     * 处理内存不足情况
     */
    override fun onLowMemory() {
        super.onLowMemory()
        
        Log.w(TAG, "系统内存不足，执行紧急清理...")
        
        try {
            // 触发内存管理器的紧急清理
            if (::memoryManager.isInitialized) {
                memoryManager.clearCache()
                memoryManager.forceGarbageCollection()
            }
            
            // 卸载非关键AI模型
            if (::aiModelManager.isInitialized) {
                aiModelManager.cleanupExpiredModels()
            }
            
            // 记录低内存事件
            if (::performanceMonitor.isInitialized) {
                performanceMonitor.recordEvent("memory_warnings")
            }
            
            Log.i(TAG, "低内存清理完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "低内存清理失败", e)
        }
    }
    
    /**
     * 处理系统配置变化时的内存优化
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        Log.d(TAG, "系统要求释放内存，级别: $level")
        
        try {
            when (level) {
                TRIM_MEMORY_UI_HIDDEN -> {
                    // UI隐藏时的轻度清理
                    if (::memoryManager.isInitialized) {
                        memoryManager.clearCache()
                    }
                }
                
                TRIM_MEMORY_BACKGROUND,
                TRIM_MEMORY_MODERATE -> {
                    // 后台运行时的中等清理
                    if (::memoryManager.isInitialized) {
                        memoryManager.clearCache()
                    }
                    if (::aiModelManager.isInitialized) {
                        aiModelManager.cleanupExpiredModels()
                    }
                }
                
                TRIM_MEMORY_COMPLETE,
                TRIM_MEMORY_CRITICAL -> {
                    // 关键内存不足时的完全清理
                    if (::memoryManager.isInitialized) {
                        memoryManager.clearCache()
                        memoryManager.forceGarbageCollection()
                    }
                    if (::aiModelManager.isInitialized) {
                        aiModelManager.cleanup()
                    }
                }
            }
            
            // 记录内存清理事件
            if (::performanceMonitor.isInitialized) {
                performanceMonitor.recordEvent("memory_trim_level_$level")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "内存清理失败", e)
        }
    }
}
