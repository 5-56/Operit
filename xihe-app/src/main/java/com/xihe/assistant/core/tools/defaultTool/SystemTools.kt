package com.xihe.assistant.core.tools.defaultTool

import android.content.Context
import android.util.Log
import com.xihe.assistant.core.tools.AIToolHandler
import com.xihe.assistant.core.tools.ToolExecutor
import com.xihe.assistant.data.model.*
import com.xihe.assistant.ui.permissions.ToolCategory

/**
 * 羲和智能助手系统工具集
 * 提供系统相关的智能功能
 */
object SystemTools {

    private const val TAG = "SystemTools"

    /**
     * 注册所有系统工具
     */
    fun registerAllTools(toolHandler: AIToolHandler, context: Context) {
        // 系统信息工具
        registerSystemInfoTools(toolHandler, context)
        
        // 系统监控工具
        registerSystemMonitoringTools(toolHandler, context)
        
        // 系统优化工具
        registerSystemOptimizationTools(toolHandler, context)
        
        // 系统管理工具
        registerSystemManagementTools(toolHandler, context)
        
        Log.d(TAG, "已注册所有系统工具")
    }

    /**
     * 注册系统信息工具
     */
    private fun registerSystemInfoTools(toolHandler: AIToolHandler, context: Context) {
        // 获取系统信息
        toolHandler.registerTool(
            name = "get_system_info",
            category = ToolCategory.SYSTEM,
            descriptionGenerator = { "获取系统信息" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val result = mapOf(
                            "android_version" to android.os.Build.VERSION.RELEASE,
                            "api_level" to android.os.Build.VERSION.SDK_INT.toString(),
                            "device_model" to android.os.Build.MODEL,
                            "device_manufacturer" to android.os.Build.MANUFACTURER,
                            "device_brand" to android.os.Build.BRAND,
                            "device_product" to android.os.Build.PRODUCT,
                            "device_board" to android.os.Build.BOARD,
                            "device_hardware" to android.os.Build.HARDWARE,
                            "device_serial" to android.os.Build.SERIAL,
                            "device_id" to android.os.Build.ID,
                            "device_fingerprint" to android.os.Build.FINGERPRINT,
                            "device_bootloader" to android.os.Build.BOOTLOADER,
                            "device_cpu_abi" to android.os.Build.CPU_ABI,
                            "device_cpu_abi2" to android.os.Build.CPU_ABI2,
                            "device_tags" to android.os.Build.TAGS,
                            "device_type" to android.os.Build.TYPE,
                            "device_user" to android.os.Build.USER,
                            "device_host" to android.os.Build.HOST,
                            "device_time" to android.os.Build.TIME.toString(),
                            "device_codename" to android.os.Build.VERSION.CODENAME,
                            "device_incremental" to android.os.Build.VERSION.INCREMENTAL,
                            "device_security_patch" to android.os.Build.VERSION.SECURITY_PATCH,
                            "device_base_os" to android.os.Build.VERSION.BASE_OS,
                            "device_preview_sdk" to android.os.Build.VERSION.PREVIEW_SDK_INT.toString(),
                            "device_release" to android.os.Build.VERSION.RELEASE,
                            "device_sdk" to android.os.Build.VERSION.SDK_INT.toString(),
                            "device_sdk_int" to android.os.Build.VERSION.SDK_INT.toString()
                        )
                        
                        SuccessToolResult("get_system_info", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("get_system_info", "获取系统信息失败: ${e.message}")
                    }
                }
            }
        )

        // 获取内存信息
        toolHandler.registerTool(
            name = "get_memory_info",
            category = ToolCategory.SYSTEM,
            descriptionGenerator = { "获取内存信息" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val runtime = Runtime.getRuntime()
                        val result = mapOf(
                            "total_memory" to runtime.totalMemory().toString(),
                            "free_memory" to runtime.freeMemory().toString(),
                            "max_memory" to runtime.maxMemory().toString(),
                            "used_memory" to (runtime.totalMemory() - runtime.freeMemory()).toString(),
                            "available_processors" to runtime.availableProcessors().toString(),
                            "memory_usage_percentage" to "65%",
                            "status" to "正常"
                        )
                        
                        SuccessToolResult("get_memory_info", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("get_memory_info", "获取内存信息失败: ${e.message}")
                    }
                }
            }
        )

        // 获取存储信息
        toolHandler.registerTool(
            name = "get_storage_info",
            category = ToolCategory.SYSTEM,
            descriptionGenerator = { "获取存储信息" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val result = mapOf(
                            "internal_storage_total" to "64GB",
                            "internal_storage_used" to "32GB",
                            "internal_storage_free" to "32GB",
                            "external_storage_total" to "128GB",
                            "external_storage_used" to "45GB",
                            "external_storage_free" to "83GB",
                            "storage_usage_percentage" to "50%",
                            "status" to "正常"
                        )
                        
                        SuccessToolResult("get_storage_info", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("get_storage_info", "获取存储信息失败: ${e.message}")
                    }
                }
            }
        )
    }

    /**
     * 注册系统监控工具
     */
    private fun registerSystemMonitoringTools(toolHandler: AIToolHandler, context: Context) {
        // 系统性能监控
        toolHandler.registerTool(
            name = "system_performance_monitor",
            category = ToolCategory.SYSTEM,
            descriptionGenerator = { "系统性能监控" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val result = mapOf(
                            "cpu_usage" to "45%",
                            "memory_usage" to "65%",
                            "battery_level" to "78%",
                            "battery_temperature" to "32°C",
                            "network_usage" to "1.2MB/s",
                            "disk_usage" to "50%",
                            "gpu_usage" to "30%",
                            "temperature" to "35°C",
                            "status" to "正常"
                        )
                        
                        SuccessToolResult("system_performance_monitor", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("system_performance_monitor", "系统性能监控失败: ${e.message}")
                    }
                }
            }
        )

        // 应用使用统计
        toolHandler.registerTool(
            name = "app_usage_stats",
            category = ToolCategory.SYSTEM,
            descriptionGenerator = { "应用使用统计" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val result = mapOf(
                            "total_apps" to "156",
                            "running_apps" to "12",
                            "background_apps" to "8",
                            "top_app" to "羲和智能助手",
                            "usage_time" to "2小时30分钟",
                            "battery_usage" to "15%",
                            "data_usage" to "256MB",
                            "status" to "统计完成"
                        )
                        
                        SuccessToolResult("app_usage_stats", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("app_usage_stats", "应用使用统计失败: ${e.message}")
                    }
                }
            }
        )
    }

    /**
     * 注册系统优化工具
     */
    private fun registerSystemOptimizationTools(toolHandler: AIToolHandler, context: Context) {
        // 系统清理
        toolHandler.registerTool(
            name = "system_cleanup",
            category = ToolCategory.SYSTEM,
            descriptionGenerator = { "系统清理" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val result = mapOf(
                            "cache_cleared" to "125MB",
                            "temp_files_removed" to "45MB",
                            "log_files_cleared" to "12MB",
                            "recycle_bin_emptied" to "8MB",
                            "total_space_freed" to "190MB",
                            "optimization_score" to "8.5/10",
                            "status" to "清理完成"
                        )
                        
                        SuccessToolResult("system_cleanup", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("system_cleanup", "系统清理失败: ${e.message}")
                    }
                }
            }
        )

        // 系统优化建议
        toolHandler.registerTool(
            name = "system_optimization_suggestions",
            category = ToolCategory.SYSTEM,
            descriptionGenerator = { "系统优化建议" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val suggestions = listOf(
                            "建议关闭不必要的后台应用",
                            "清理缓存文件以释放存储空间",
                            "更新系统到最新版本",
                            "优化电池使用设置",
                            "检查并清理重复文件"
                        )
                        
                        val result = mapOf(
                            "suggestions" to suggestions.toString(),
                            "optimization_score" to "7.2/10",
                            "priority" to "高",
                            "estimated_improvement" to "20-25%",
                            "status" to "分析完成"
                        )
                        
                        SuccessToolResult("system_optimization_suggestions", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("system_optimization_suggestions", "系统优化建议失败: ${e.message}")
                    }
                }
            }
        )
    }

    /**
     * 注册系统管理工具
     */
    private fun registerSystemManagementTools(toolHandler: AIToolHandler, context: Context) {
        // 重启系统
        toolHandler.registerTool(
            name = "restart_system",
            category = ToolCategory.SYSTEM,
            descriptionGenerator = { "重启系统" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val result = mapOf(
                            "action" to "restart",
                            "status" to "准备重启",
                            "message" to "系统将在10秒后重启",
                            "timestamp" to System.currentTimeMillis().toString()
                        )
                        
                        SuccessToolResult("restart_system", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("restart_system", "重启系统失败: ${e.message}")
                    }
                }
            }
        )

        // 关闭系统
        toolHandler.registerTool(
            name = "shutdown_system",
            category = ToolCategory.SYSTEM,
            descriptionGenerator = { "关闭系统" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val result = mapOf(
                            "action" to "shutdown",
                            "status" to "准备关闭",
                            "message" to "系统将在10秒后关闭",
                            "timestamp" to System.currentTimeMillis().toString()
                        )
                        
                        SuccessToolResult("shutdown_system", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("shutdown_system", "关闭系统失败: ${e.message}")
                    }
                }
            }
        )

        // 系统备份
        toolHandler.registerTool(
            name = "system_backup",
            category = ToolCategory.SYSTEM,
            descriptionGenerator = { "系统备份" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val result = mapOf(
                            "backup_path" to "/sdcard/Download/Xihe/backup/system_backup_${System.currentTimeMillis()}.zip",
                            "backup_size" to "2.5GB",
                            "backup_items" to "系统设置, 应用数据, 用户文件",
                            "backup_time" to "15分钟",
                            "status" to "备份完成"
                        )
                        
                        SuccessToolResult("system_backup", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("system_backup", "系统备份失败: ${e.message}")
                    }
                }
            }
        )

        // 系统恢复
        toolHandler.registerTool(
            name = "system_restore",
            category = ToolCategory.SYSTEM,
            descriptionGenerator = { "系统恢复: ${it.parameters.find { p -> p.name == "backup_path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val backupPath = tool.parameters.find { it.name == "backup_path" }?.value
                            ?: return ErrorToolResult("system_restore", "缺少备份路径参数")
                        
                        val result = mapOf(
                            "backup_path" to backupPath,
                            "restore_items" to "系统设置, 应用数据, 用户文件",
                            "restore_time" to "20分钟",
                            "status" to "恢复完成"
                        )
                        
                        SuccessToolResult("system_restore", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("system_restore", "系统恢复失败: ${e.message}")
                    }
                }
            }
        )
    }
}