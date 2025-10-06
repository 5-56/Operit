package com.xihe.assistant.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * 全局异常处理器
 * 捕获和处理应用中的未处理异常
 */
class GlobalExceptionHandler(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    private val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // 记录异常信息
            logException(thread, throwable)
            
            // 保存崩溃报告
            saveCrashReport(thread, throwable)
            
        } catch (e: Exception) {
            Log.e("GlobalExceptionHandler", "处理异常时发生错误", e)
        } finally {
            // 调用原始处理器
            originalHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 记录异常信息
     */
    private fun logException(thread: Thread, throwable: Throwable) {
        Log.e("GlobalExceptionHandler", "未捕获的异常", throwable)
        Log.e("GlobalExceptionHandler", "线程: ${thread.name}")
        Log.e("GlobalExceptionHandler", "异常类型: ${throwable.javaClass.simpleName}")
        Log.e("GlobalExceptionHandler", "异常消息: ${throwable.message}")
    }

    /**
     * 保存崩溃报告
     */
    private fun saveCrashReport(thread: Thread, throwable: Throwable) {
        try {
            val crashDir = File(context.filesDir, "crash_reports")
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            val timestamp = dateFormat.format(Date())
            val crashFile = File(crashDir, "crash_${System.currentTimeMillis()}.txt")

            FileWriter(crashFile).use { writer ->
                writer.write("羲和智能助手崩溃报告\n")
                writer.write("时间: $timestamp\n")
                writer.write("线程: ${thread.name}\n")
                writer.write("异常类型: ${throwable.javaClass.simpleName}\n")
                writer.write("异常消息: ${throwable.message}\n")
                writer.write("堆栈跟踪:\n")
                writer.write(throwable.stackTraceToString())
                writer.write("\n\n设备信息:\n")
                writer.write("Android版本: ${android.os.Build.VERSION.RELEASE}\n")
                writer.write("设备型号: ${android.os.Build.MODEL}\n")
                writer.write("制造商: ${android.os.Build.MANUFACTURER}\n")
                writer.write("API级别: ${android.os.Build.VERSION.SDK_INT}\n")
            }

            Log.d("GlobalExceptionHandler", "崩溃报告已保存: ${crashFile.absolutePath}")
        } catch (e: IOException) {
            Log.e("GlobalExceptionHandler", "保存崩溃报告失败", e)
        }
    }
}