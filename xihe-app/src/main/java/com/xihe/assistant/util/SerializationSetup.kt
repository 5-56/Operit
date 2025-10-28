package com.xihe.assistant.util

import kotlinx.serialization.modules.SerializersModule

/**
 * 序列化设置
 * 配置应用的序列化模块
 */
object SerializationSetup {
    val module = SerializersModule {
        // 这里可以添加自定义序列化器
        // 目前使用默认配置
    }
}