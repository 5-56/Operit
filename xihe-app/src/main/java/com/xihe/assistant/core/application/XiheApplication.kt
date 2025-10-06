package com.xihe.assistant.core.application

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
import com.xihe.assistant.R
import com.xihe.assistant.core.ai.AIMessageManager
import com.xihe.assistant.core.automation.SmartAutomationManager
import com.xihe.assistant.core.config.SystemPromptConfig
import com.xihe.assistant.data.db.AppDatabase
import com.xihe.assistant.data.preferences.UserPreferencesManager
import com.xihe.assistant.data.preferences.initUserPreferencesManager
import com.xihe.assistant.data.preferences.preferencesManager
import com.xihe.assistant.services.EmbeddingService
import com.xihe.assistant.util.GlobalExceptionHandler
import com.xihe.assistant.util.LocaleUtils
import com.xihe.assistant.util.SerializationSetup
import com.xihe.assistant.util.TextSegmenter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * 羲和智能助手应用主类
 * 提供更智能、更自动化的AI助手体验
 */
class XiheApplication : Application() {

    companion object {
        /** 全局JSON实例，支持自定义序列化器 */
        lateinit var json: Json
            private set

        // 全局应用实例
        lateinit var instance: XiheApplication
            private set

        // 全局ImageLoader实例，用于高效缓存图片
        lateinit var globalImageLoader: ImageLoader
            private set

        private const val TAG = "XiheApplication"
    }

    // 应用级协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 懒加载数据库实例
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化AI消息管理器
        AIMessageManager.initialize(this)

        // 初始化智能自动化管理器
        SmartAutomationManager.initialize(this)

        // 初始化嵌入服务
        EmbeddingService.initialize(this)

        // 设置全局异常处理器
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this))

        // 初始化JSON序列化器
        json = Json {
            serializersModule = SerializationSetup.module
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
            encodeDefaults = true
        }

        // 初始化用户偏好管理器
        val defaultProfileName = applicationContext.getString(R.string.default_profile)
        initUserPreferencesManager(applicationContext, defaultProfileName)

        // 在最早时机初始化并应用语言设置
        initializeAppLanguage()

        // 初始化TextSegmenter
        applicationScope.launch { TextSegmenter.initialize(applicationContext) }

        // 预加载数据库
        applicationScope.launch {
            database.problemDao().getProblemCount()
        }

        // 初始化全局图片加载器
        globalImageLoader = ImageLoader.Builder(this)
            .crossfade(true)
            .respectCacheHeaders(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(filesDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100MB磁盘缓存
                    .build()
            }
            .memoryCache {
                coil.memory.MemoryCache.Builder(this).maxSizePercent(0.2).build()
            }
            .build()

        Log.d(TAG, "羲和智能助手应用初始化完成")
    }

    /** 初始化应用语言设置 */
    private fun initializeAppLanguage() {
        try {
            val languageCode = runBlocking {
                try {
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

            val locale = Locale(languageCode)
            Locale.setDefault(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeList = LocaleListCompat.create(locale)
                AppCompatDelegate.setApplicationLocales(localeList)
                Log.d(TAG, "使用AppCompatDelegate设置语言: $languageCode")
            } else {
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
        try {
            val code = LocaleUtils.getCurrentLanguage(base)
            val locale = Locale(code)
            val config = Configuration(base.resources.configuration)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                config.setLocales(localeList)
            } else {
                config.locale = locale
                Locale.setDefault(locale)
            }

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
        Log.d(TAG, "羲和智能助手应用终止")
    }
}