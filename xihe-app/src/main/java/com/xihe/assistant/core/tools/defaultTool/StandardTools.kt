package com.xihe.assistant.core.tools.defaultTool

import android.content.Context
import android.util.Log
import com.xihe.assistant.core.tools.AIToolHandler
import com.xihe.assistant.core.tools.ToolExecutor
import com.xihe.assistant.data.model.*
import com.xihe.assistant.ui.permissions.ToolCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 羲和智能助手标准工具集
 * 提供基础的文件、系统、网络等操作功能
 */
object StandardTools {

    private const val TAG = "StandardTools"

    /**
     * 注册所有标准工具
     */
    fun registerAllTools(toolHandler: AIToolHandler, context: Context) {
        // 文件系统工具
        registerFileSystemTools(toolHandler, context)
        
        // 系统信息工具
        registerSystemInfoTools(toolHandler, context)
        
        // 网络工具
        registerNetworkTools(toolHandler, context)
        
        // 媒体工具
        registerMediaTools(toolHandler, context)
        
        // 自动化工具
        registerAutomationTools(toolHandler, context)
        
        // AI工具
        registerAITools(toolHandler, context)
        
        Log.d(TAG, "已注册所有标准工具")
    }

    /**
     * 注册文件系统工具
     */
    private fun registerFileSystemTools(toolHandler: AIToolHandler, context: Context) {
        // 读取文件
        toolHandler.registerTool(
            name = "read_file",
            category = ToolCategory.FILE_SYSTEM,
            descriptionGenerator = { "读取文件内容: ${it.parameters.find { p -> p.name == "path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val path = tool.parameters.find { it.name == "path" }?.value
                            ?: return ErrorToolResult("read_file", "缺少文件路径参数")
                        
                        val file = File(path)
                        if (!file.exists()) {
                            return ErrorToolResult("read_file", "文件不存在: $path")
                        }
                        
                        val content = file.readText()
                        SuccessToolResult("read_file", StringResultData(content))
                    } catch (e: Exception) {
                        ErrorToolResult("read_file", "读取文件失败: ${e.message}")
                    }
                }
            }
        )

        // 写入文件
        toolHandler.registerTool(
            name = "write_file",
            category = ToolCategory.FILE_SYSTEM,
            descriptionGenerator = { "写入文件: ${it.parameters.find { p -> p.name == "path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val path = tool.parameters.find { it.name == "path" }?.value
                            ?: return ErrorToolResult("write_file", "缺少文件路径参数")
                        val content = tool.parameters.find { it.name == "content" }?.value
                            ?: return ErrorToolResult("write_file", "缺少文件内容参数")
                        
                        val file = File(path)
                        file.parentFile?.mkdirs()
                        file.writeText(content)
                        
                        SuccessToolResult("write_file", StringResultData("文件写入成功: $path"))
                    } catch (e: Exception) {
                        ErrorToolResult("write_file", "写入文件失败: ${e.message}")
                    }
                }
            }
        )

        // 列出目录内容
        toolHandler.registerTool(
            name = "list_directory",
            category = ToolCategory.FILE_SYSTEM,
            descriptionGenerator = { "列出目录内容: ${it.parameters.find { p -> p.name == "path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val path = tool.parameters.find { it.name == "path" }?.value
                            ?: return ErrorToolResult("list_directory", "缺少目录路径参数")
                        
                        val dir = File(path)
                        if (!dir.exists() || !dir.isDirectory) {
                            return ErrorToolResult("list_directory", "目录不存在或不是目录: $path")
                        }
                        
                        val files = dir.listFiles()?.map { file ->
                            mapOf(
                                "name" to file.name,
                                "type" to if (file.isDirectory) "directory" else "file",
                                "size" to file.length().toString(),
                                "modified" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(file.lastModified())
                            )
                        } ?: emptyList()
                        
                        SuccessToolResult("list_directory", JsonResultData(mapOf("files" to files.toString())))
                    } catch (e: Exception) {
                        ErrorToolResult("list_directory", "列出目录失败: ${e.message}")
                    }
                }
            }
        )

        // 创建目录
        toolHandler.registerTool(
            name = "create_directory",
            category = ToolCategory.FILE_SYSTEM,
            descriptionGenerator = { "创建目录: ${it.parameters.find { p -> p.name == "path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val path = tool.parameters.find { it.name == "path" }?.value
                            ?: return ErrorToolResult("create_directory", "缺少目录路径参数")
                        
                        val dir = File(path)
                        val success = dir.mkdirs()
                        
                        if (success) {
                            SuccessToolResult("create_directory", StringResultData("目录创建成功: $path"))
                        } else {
                            ErrorToolResult("create_directory", "目录创建失败: $path")
                        }
                    } catch (e: Exception) {
                        ErrorToolResult("create_directory", "创建目录失败: ${e.message}")
                    }
                }
            }
        )

        // 删除文件或目录
        toolHandler.registerTool(
            name = "delete_file",
            category = ToolCategory.FILE_SYSTEM,
            descriptionGenerator = { "删除文件: ${it.parameters.find { p -> p.name == "path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val path = tool.parameters.find { it.name == "path" }?.value
                            ?: return ErrorToolResult("delete_file", "缺少文件路径参数")
                        
                        val file = File(path)
                        if (!file.exists()) {
                            return ErrorToolResult("delete_file", "文件不存在: $path")
                        }
                        
                        val success = if (file.isDirectory) {
                            file.deleteRecursively()
                        } else {
                            file.delete()
                        }
                        
                        if (success) {
                            SuccessToolResult("delete_file", StringResultData("删除成功: $path"))
                        } else {
                            ErrorToolResult("delete_file", "删除失败: $path")
                        }
                    } catch (e: Exception) {
                        ErrorToolResult("delete_file", "删除文件失败: ${e.message}")
                    }
                }
            }
        )
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
                        val systemInfo = mapOf(
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
                        
                        SuccessToolResult("get_system_info", JsonResultData(systemInfo))
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
                        val memoryInfo = mapOf(
                            "total_memory" to runtime.totalMemory().toString(),
                            "free_memory" to runtime.freeMemory().toString(),
                            "max_memory" to runtime.maxMemory().toString(),
                            "used_memory" to (runtime.totalMemory() - runtime.freeMemory()).toString(),
                            "available_processors" to runtime.availableProcessors().toString()
                        )
                        
                        SuccessToolResult("get_memory_info", JsonResultData(memoryInfo))
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
                        val storageInfo = mapOf(
                            "internal_storage_path" to context.filesDir.absolutePath,
                            "external_storage_path" to context.getExternalFilesDir(null)?.absolutePath ?: "不可用",
                            "cache_path" to context.cacheDir.absolutePath,
                            "data_path" to context.dataDir.absolutePath,
                            "code_cache_path" to context.codeCacheDir.absolutePath,
                            "no_backup_path" to context.noBackupFilesDir.absolutePath
                        )
                        
                        SuccessToolResult("get_storage_info", JsonResultData(storageInfo))
                    } catch (e: Exception) {
                        ErrorToolResult("get_storage_info", "获取存储信息失败: ${e.message}")
                    }
                }
            }
        )
    }

    /**
     * 注册网络工具
     */
    private fun registerNetworkTools(toolHandler: AIToolHandler, context: Context) {
        // 检查网络连接
        toolHandler.registerTool(
            name = "check_network",
            category = ToolCategory.NETWORK,
            descriptionGenerator = { "检查网络连接状态" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        val networkInfo = connectivityManager.activeNetworkInfo
                        val isConnected = networkInfo?.isConnected ?: false
                        
                        val networkInfoMap = mapOf(
                            "is_connected" to isConnected.toString(),
                            "network_type" to (networkInfo?.typeName ?: "未知"),
                            "network_subtype" to (networkInfo?.subtypeName ?: "未知"),
                            "is_roaming" to (networkInfo?.isRoaming ?: false).toString()
                        )
                        
                        SuccessToolResult("check_network", JsonResultData(networkInfoMap))
                    } catch (e: Exception) {
                        ErrorToolResult("check_network", "检查网络状态失败: ${e.message}")
                    }
                }
            }
        )

        // 发送HTTP请求
        toolHandler.registerTool(
            name = "http_request",
            category = ToolCategory.NETWORK,
            descriptionGenerator = { "发送HTTP请求: ${it.parameters.find { p -> p.name == "url" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val url = tool.parameters.find { it.name == "url" }?.value
                            ?: return ErrorToolResult("http_request", "缺少URL参数")
                        val method = tool.parameters.find { it.name == "method" }?.value ?: "GET"
                        val headers = tool.parameters.find { it.name == "headers" }?.value
                        val body = tool.parameters.find { it.name == "body" }?.value
                        
                        // 这里应该使用实际的HTTP客户端，如OkHttp
                        // 为了演示，我们返回一个模拟响应
                        val response = mapOf(
                            "url" to url,
                            "method" to method,
                            "status_code" to "200",
                            "headers" to (headers ?: "{}"),
                            "body" to (body ?: ""),
                            "response" to "模拟HTTP响应"
                        )
                        
                        SuccessToolResult("http_request", JsonResultData(response))
                    } catch (e: Exception) {
                        ErrorToolResult("http_request", "HTTP请求失败: ${e.message}")
                    }
                }
            }
        )
    }

    /**
     * 注册媒体工具
     */
    private fun registerMediaTools(toolHandler: AIToolHandler, context: Context) {
        // 获取图片信息
        toolHandler.registerTool(
            name = "get_image_info",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "获取图片信息: ${it.parameters.find { p -> p.name == "path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val path = tool.parameters.find { it.name == "path" }?.value
                            ?: return ErrorToolResult("get_image_info", "缺少图片路径参数")
                        
                        val file = File(path)
                        if (!file.exists()) {
                            return ErrorToolResult("get_image_info", "图片文件不存在: $path")
                        }
                        
                        val imageInfo = mapOf(
                            "path" to path,
                            "name" to file.name,
                            "size" to file.length().toString(),
                            "modified" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(file.lastModified()),
                            "extension" to file.extension,
                            "mime_type" to "image/${file.extension}"
                        )
                        
                        SuccessToolResult("get_image_info", JsonResultData(imageInfo))
                    } catch (e: Exception) {
                        ErrorToolResult("get_image_info", "获取图片信息失败: ${e.message}")
                    }
                }
            }
        )

        // 获取音频信息
        toolHandler.registerTool(
            name = "get_audio_info",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "获取音频信息: ${it.parameters.find { p -> p.name == "path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val path = tool.parameters.find { it.name == "path" }?.value
                            ?: return ErrorToolResult("get_audio_info", "缺少音频路径参数")
                        
                        val file = File(path)
                        if (!file.exists()) {
                            return ErrorToolResult("get_audio_info", "音频文件不存在: $path")
                        }
                        
                        val audioInfo = mapOf(
                            "path" to path,
                            "name" to file.name,
                            "size" to file.length().toString(),
                            "modified" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(file.lastModified()),
                            "extension" to file.extension,
                            "mime_type" to "audio/${file.extension}"
                        )
                        
                        SuccessToolResult("get_audio_info", JsonResultData(audioInfo))
                    } catch (e: Exception) {
                        ErrorToolResult("get_audio_info", "获取音频信息失败: ${e.message}")
                    }
                }
            }
        )
    }

    /**
     * 注册自动化工具
     */
    private fun registerAutomationTools(toolHandler: AIToolHandler, context: Context) {
        // 创建自动化任务
        toolHandler.registerTool(
            name = "create_automation_task",
            category = ToolCategory.AUTOMATION,
            descriptionGenerator = { "创建自动化任务: ${it.parameters.find { p -> p.name == "name" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val name = tool.parameters.find { it.name == "name" }?.value
                            ?: return ErrorToolResult("create_automation_task", "缺少任务名称参数")
                        val description = tool.parameters.find { it.name == "description" }?.value ?: ""
                        val actions = tool.parameters.find { it.name == "actions" }?.value?.split(",") ?: emptyList()
                        
                        // 这里应该调用实际的自动化管理器
                        val taskInfo = mapOf(
                            "name" to name,
                            "description" to description,
                            "actions" to actions.toString(),
                            "status" to "已创建"
                        )
                        
                        SuccessToolResult("create_automation_task", JsonResultData(taskInfo))
                    } catch (e: Exception) {
                        ErrorToolResult("create_automation_task", "创建自动化任务失败: ${e.message}")
                    }
                }
            }
        )

        // 执行自动化任务
        toolHandler.registerTool(
            name = "execute_automation_task",
            category = ToolCategory.AUTOMATION,
            descriptionGenerator = { "执行自动化任务: ${it.parameters.find { p -> p.name == "task_id" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val taskId = tool.parameters.find { it.name == "task_id" }?.value
                            ?: return ErrorToolResult("execute_automation_task", "缺少任务ID参数")
                        
                        // 这里应该调用实际的自动化管理器
                        val result = mapOf(
                            "task_id" to taskId,
                            "status" to "执行中",
                            "start_time" to System.currentTimeMillis().toString()
                        )
                        
                        SuccessToolResult("execute_automation_task", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("execute_automation_task", "执行自动化任务失败: ${e.message}")
                    }
                }
            }
        )
    }

    /**
     * 注册AI工具
     */
    private fun registerAITools(toolHandler: AIToolHandler, context: Context) {
        // 文本分析
        toolHandler.registerTool(
            name = "analyze_text",
            category = ToolCategory.AI,
            descriptionGenerator = { "分析文本: ${it.parameters.find { p -> p.name == "text" }?.value?.take(50)}..." },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val text = tool.parameters.find { it.name == "text" }?.value
                            ?: return ErrorToolResult("analyze_text", "缺少文本参数")
                        
                        val analysis = mapOf(
                            "text" to text,
                            "length" to text.length.toString(),
                            "word_count" to text.split("\\s+".toRegex()).size.toString(),
                            "char_count" to text.length.toString(),
                            "line_count" to text.split("\n").size.toString(),
                            "language" to "中文", // 这里应该使用实际的语言检测
                            "sentiment" to "中性" // 这里应该使用实际的情感分析
                        )
                        
                        SuccessToolResult("analyze_text", JsonResultData(analysis))
                    } catch (e: Exception) {
                        ErrorToolResult("analyze_text", "文本分析失败: ${e.message}")
                    }
                }
            }
        )

        // 生成摘要
        toolHandler.registerTool(
            name = "generate_summary",
            category = ToolCategory.AI,
            descriptionGenerator = { "生成摘要: ${it.parameters.find { p -> p.name == "text" }?.value?.take(50)}..." },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val text = tool.parameters.find { it.name == "text" }?.value
                            ?: return ErrorToolResult("generate_summary", "缺少文本参数")
                        val maxLength = tool.parameters.find { it.name == "max_length" }?.value?.toIntOrNull() ?: 100
                        
                        // 简单的摘要生成（实际应该使用AI模型）
                        val summary = if (text.length <= maxLength) {
                            text
                        } else {
                            text.take(maxLength) + "..."
                        }
                        
                        val summaryInfo = mapOf(
                            "original_length" to text.length.toString(),
                            "summary_length" to summary.length.toString(),
                            "summary" to summary
                        )
                        
                        SuccessToolResult("generate_summary", JsonResultData(summaryInfo))
                    } catch (e: Exception) {
                        ErrorToolResult("generate_summary", "生成摘要失败: ${e.message}")
                    }
                }
            }
        )
    }
}