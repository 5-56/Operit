package com.xihe.assistant.core.tools

import android.content.Context
import android.util.Log

class AIToolHandler private constructor(private val context: Context) {
    companion object {
        private const val TAG = "AIToolHandler"
        private var instance: AIToolHandler? = null

        fun getInstance(context: Context): AIToolHandler {
            return instance ?: synchronized(this) {
                instance ?: AIToolHandler(context.applicationContext).also { instance = it }
            }
        }
    }

    private val registeredTools = mutableMapOf<String, AITool>()

    fun registerDefaultTools() {
        Log.d(TAG, "注册默认工具")
        
        // 注册文件系统工具
        registerTool(FileSystemTool())
        
        // 注册网络工具
        registerTool(NetworkTool())
        
        // 注册系统工具
        registerTool(SystemTool())
        
        // 注册UI自动化工具
        registerTool(UIAutomationTool())
        
        // 注册媒体处理工具
        registerTool(MediaTool())
        
        Log.d(TAG, "已注册 ${registeredTools.size} 个工具")
    }

    fun registerTool(tool: AITool) {
        registeredTools[tool.name] = tool
        Log.d(TAG, "注册工具: ${tool.name}")
    }

    fun getTool(name: String): AITool? {
        return registeredTools[name]
    }

    fun getAllTools(): Map<String, AITool> {
        return registeredTools.toMap()
    }

    fun executeTool(name: String, parameters: Map<String, Any>): ToolResult {
        val tool = getTool(name)
        return if (tool != null) {
            try {
                tool.execute(parameters)
            } catch (e: Exception) {
                Log.e(TAG, "工具执行失败: $name", e)
                ToolResult.error("工具执行失败: ${e.message}")
            }
        } else {
            Log.w(TAG, "工具不存在: $name")
            ToolResult.error("工具不存在: $name")
        }
    }
}

abstract class AITool {
    abstract val name: String
    abstract val description: String
    abstract val parameters: Map<String, ParameterInfo>
    
    abstract fun execute(parameters: Map<String, Any>): ToolResult
}

data class ParameterInfo(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val defaultValue: Any? = null
)

data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun success(data: Any? = null, metadata: Map<String, Any> = emptyMap()) = 
            ToolResult(true, data, null, metadata)
        
        fun error(message: String, metadata: Map<String, Any> = emptyMap()) = 
            ToolResult(false, null, message, metadata)
    }
}

// 文件系统工具
class FileSystemTool : AITool() {
    override val name = "file_system"
    override val description = "文件系统操作工具"
    override val parameters = mapOf(
        "action" to ParameterInfo("string", "操作类型: read, write, list, delete", true),
        "path" to ParameterInfo("string", "文件路径", true),
        "content" to ParameterInfo("string", "文件内容（写入时使用）", false)
    )

    override fun execute(parameters: Map<String, Any>): ToolResult {
        val action = parameters["action"] as? String ?: return ToolResult.error("缺少action参数")
        val path = parameters["path"] as? String ?: return ToolResult.error("缺少path参数")

        return when (action) {
            "read" -> {
                try {
                    val content = java.io.File(path).readText()
                    ToolResult.success(content)
                } catch (e: Exception) {
                    ToolResult.error("读取文件失败: ${e.message}")
                }
            }
            "write" -> {
                try {
                    val content = parameters["content"] as? String ?: ""
                    java.io.File(path).writeText(content)
                    ToolResult.success("文件写入成功")
                } catch (e: Exception) {
                    ToolResult.error("写入文件失败: ${e.message}")
                }
            }
            "list" -> {
                try {
                    val files = java.io.File(path).listFiles()?.map { it.name } ?: emptyList()
                    ToolResult.success(files)
                } catch (e: Exception) {
                    ToolResult.error("列出文件失败: ${e.message}")
                }
            }
            "delete" -> {
                try {
                    val file = java.io.File(path)
                    if (file.delete()) {
                        ToolResult.success("文件删除成功")
                    } else {
                        ToolResult.error("文件删除失败")
                    }
                } catch (e: Exception) {
                    ToolResult.error("删除文件失败: ${e.message}")
                }
            }
            else -> ToolResult.error("不支持的操作: $action")
        }
    }
}

// 网络工具
class NetworkTool : AITool() {
    override val name = "network"
    override val description = "网络请求工具"
    override val parameters = mapOf(
        "method" to ParameterInfo("string", "HTTP方法: GET, POST", true),
        "url" to ParameterInfo("string", "请求URL", true),
        "headers" to ParameterInfo("object", "请求头", false),
        "body" to ParameterInfo("string", "请求体", false)
    )

    override fun execute(parameters: Map<String, Any>): ToolResult {
        // 简化的网络请求实现
        return ToolResult.success("网络请求功能待实现")
    }
}

// 系统工具
class SystemTool : AITool() {
    override val name = "system"
    override val description = "系统操作工具"
    override val parameters = mapOf(
        "command" to ParameterInfo("string", "系统命令", true)
    )

    override fun execute(parameters: Map<String, Any>): ToolResult {
        // 简化的系统命令执行
        return ToolResult.success("系统命令功能待实现")
    }
}

// UI自动化工具
class UIAutomationTool : AITool() {
    override val name = "ui_automation"
    override val description = "UI自动化工具"
    override val parameters = mapOf(
        "action" to ParameterInfo("string", "操作类型: click, swipe, input", true),
        "coordinates" to ParameterInfo("object", "坐标信息", false),
        "text" to ParameterInfo("string", "输入文本", false)
    )

    override fun execute(parameters: Map<String, Any>): ToolResult {
        // 简化的UI自动化实现
        return ToolResult.success("UI自动化功能待实现")
    }
}

// 媒体处理工具
class MediaTool : AITool() {
    override val name = "media"
    override val description = "媒体处理工具"
    override val parameters = mapOf(
        "action" to ParameterInfo("string", "操作类型: convert, extract", true),
        "inputPath" to ParameterInfo("string", "输入文件路径", true),
        "outputPath" to ParameterInfo("string", "输出文件路径", true),
        "format" to ParameterInfo("string", "目标格式", false)
    )

    override fun execute(parameters: Map<String, Any>): ToolResult {
        // 简化的媒体处理实现
        return ToolResult.success("媒体处理功能待实现")
    }
}