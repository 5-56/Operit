package com.xihe.assistant.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * 语言环境工具类
 * 处理应用的多语言支持
 */
object LocaleUtils {

    /**
     * 获取当前语言代码
     */
    fun getCurrentLanguage(context: Context): String {
        return try {
            val configuration = context.resources.configuration
            val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                configuration.locale
            }
            locale.language
        } catch (e: Exception) {
            "zh" // 默认中文
        }
    }

    /**
     * 设置应用语言
     */
    fun setAppLanguage(context: Context, languageCode: String) {
        try {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)
            
            val configuration = Configuration(context.resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.setLocale(locale)
            } else {
                @Suppress("DEPRECATION")
                configuration.locale = locale
            }
            
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        } catch (e: Exception) {
            // 忽略语言设置错误
        }
    }

    /**
     * 获取支持的语言列表
     */
    fun getSupportedLanguages(): List<Pair<String, String>> {
        return listOf(
            "zh" to "中文",
            "en" to "English",
            "ja" to "日本語",
            "ko" to "한국어",
            "es" to "Español",
            "fr" to "Français",
            "de" to "Deutsch",
            "ru" to "Русский"
        )
    }
}