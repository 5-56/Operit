package com.ai.assistance.operit.util

import android.util.Log
import com.ai.assistance.operit.BuildConfig

/**
 * 日志工具类
 * 在生产环境下自动禁用调试日志，提高性能和安全性
 */
object LogUtils {
    
    /**
     * 是否启用调试日志
     * 生产环境下自动禁用
     */
    private const val DEBUG_ENABLED = BuildConfig.DEBUG
    
    /**
     * 输出调试日志
     */
    fun d(tag: String, message: String) {
        if (DEBUG_ENABLED) {
            Log.d(tag, message)
        }
    }
    
    /**
     * 输出信息日志
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }
    
    /**
     * 输出警告日志
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }
    
    /**
     * 输出警告日志（带异常）
     */
    fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, message, throwable)
    }
    
    /**
     * 输出错误日志
     */
    fun e(tag: String, message: String) {
        Log.e(tag, message)
    }
    
    /**
     * 输出错误日志（带异常）
     */
    fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
    
    /**
     * 输出详细日志
     */
    fun v(tag: String, message: String) {
        if (DEBUG_ENABLED) {
            Log.v(tag, message)
        }
    }
    
    /**
     * 条件日志输出 - 仅在调试模式下执行
     */
    inline fun debugOnly(action: () -> Unit) {
        if (DEBUG_ENABLED) {
            action()
        }
    }
    
    /**
     * 性能测量日志
     */
    inline fun measureTime(tag: String, operation: String, action: () -> Unit) {
        if (DEBUG_ENABLED) {
            val startTime = System.currentTimeMillis()
            action()
            val endTime = System.currentTimeMillis()
            d(tag, "$operation 耗时: ${endTime - startTime}ms")
        } else {
            action()
        }
    }
}