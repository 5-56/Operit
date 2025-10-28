package com.xihe.assistant.services

import android.content.Context
import android.util.Log

/**
 * 嵌入服务
 * 处理文本嵌入和向量化
 */
object EmbeddingService {
    private const val TAG = "EmbeddingService"
    private var isInitialized = false

    /**
     * 初始化嵌入服务
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        try {
            Log.d(TAG, "嵌入服务初始化完成")
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "嵌入服务初始化失败", e)
        }
    }
}