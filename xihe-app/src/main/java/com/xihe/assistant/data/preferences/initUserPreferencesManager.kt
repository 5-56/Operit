package com.xihe.assistant.data.preferences

import android.content.Context

/**
 * 初始化用户偏好设置管理器
 */
fun initUserPreferencesManager(context: Context, defaultProfileName: String) {
    // 初始化用户偏好设置管理器
    preferencesManager = UserPreferencesManager(context)
}