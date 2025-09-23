package com.xihe.assistant.util

import android.content.Context
import android.content.res.Configuration
import java.util.*

object LocaleUtils {
    fun getCurrentLanguage(context: Context): String {
        val config = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            config.locale.language
        }
    }
    
    fun setLanguage(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}