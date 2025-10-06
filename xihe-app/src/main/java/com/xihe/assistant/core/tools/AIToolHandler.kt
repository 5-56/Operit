package com.xihe.assistant.core.tools

import android.content.Context
import android.util.Log
import com.xihe.assistant.data.model.AITool
import com.xihe.assistant.data.model.ToolInvocation
import com.xihe.assistant.data.model.ToolParameter
import com.xihe.assistant.data.model.ToolResult
import com.xihe.assistant.data.model.ToolValidationResult
import com.xihe.assistant.ui.common.displays.MessageContentParser
import com.xihe.assistant.ui.permissions.ToolCategory
import com.xihe.assistant.ui.permissions.ToolPermissionSystem
import com.xihe.assistant.util.stream.splitBy
import com.xihe.assistant.util.stream.stream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 羲和智能助手工具处理器
 * 提供更智能的工具调用和执行能力
 */
class AIToolHandler private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AIToolHandler"

        @Volatile private var INSTANCE: AIToolHandler? = null

        fun getInstance(context: Context): AIToolHandler {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE ?: AIToolHandler(context.applicationContext).also { INSTANCE = it }
                }
        }
    }

    // 可用工具注册表
    private val availableTools = mutableMapOf<String, ToolExecutor>()

    // 工具权限系统
    private val toolPermissionSystem = ToolPermissionSystem.getInstance(context)

    /** 获取工具权限系统供UI使用 */
    fun getToolPermissionSystem(): ToolPermissionSystem {
        return toolPermissionSystem
    }

    /** 强制刷新权限请求状态 */
    fun refreshPermissionState(): Boolean {
        return toolPermissionSystem.refreshPermissionRequestState()
    }

    // 工具注册方法
    fun registerTool(
        name: String,
        category: ToolCategory,
        dangerCheck: ((AITool) -> Boolean)? = null,
        descriptionGenerator: ((AITool) -> String)? = null,
        executor: ToolExecutor
    ) {
        val wrappedExecutor = object : ToolExecutor {
            override fun invoke(tool: AITool): ToolResult {
                val toolWithCategory = if (tool.category == null) {
                    tool.copy(category = category)
                } else {
                    tool
                }
                return executor.invoke(toolWithCategory)
            }

            override fun invokeAndStream(tool: AITool): Flow<ToolResult> {
                val toolWithCategory = if (tool.category == null) {
                    tool.copy(category = category)
                } else {
                    tool
                }
                return executor.invokeAndStream(toolWithCategory)
            }

            override fun validateParameters(tool: AITool): ToolValidationResult {
                return executor.validateParameters(tool)
            }

            override fun getCategory(): ToolCategory {
                return category
            }
        }

        availableTools[name] = wrappedExecutor

        if (dangerCheck != null) {
            toolPermissionSystem.registerDangerousOperation(name, dangerCheck)
        }

        if (descriptionGenerator != null) {
            toolPermissionSystem.registerOperationDescription(name, descriptionGenerator)
        }
    }

    // 便捷注册方法
    fun registerTool(
        name: String,
        category: ToolCategory,
        dangerCheck: ((AITool) -> Boolean)? = null,
        descriptionGenerator: ((AITool) -> String)? = null,
        executor: (AITool) -> ToolResult
    ) {
        registerTool(
            name = name,
            category = category,
            dangerCheck = dangerCheck,
            descriptionGenerator = descriptionGenerator,
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return executor(tool)
                }
            }
        )
    }

    // 注册所有默认工具
    fun registerDefaultTools() {
        toolPermissionSystem.initializeDefaultRules()
        registerAllTools(this, context)
    }

    /** 替换响应中的工具调用为结果 */
    private fun replaceToolInvocation(
        response: String,
        invocation: ToolInvocation,
        result: String
    ): String {
        val before = response.substring(0, invocation.responseLocation.first)
        val after = response.substring(invocation.responseLocation.last + 1)
        return "$before\n**工具结果 [${invocation.tool.name}]:** \n$result\n$after"
    }

    /** 取消转义XML特殊字符 */
    private fun unescapeXml(input: String): String {
        var result = input

        if (result.startsWith("<![CDATA[") && result.endsWith("]]>")) {
            result = result.substring(9, result.length - 3)
        }

        if (result.endsWith("]]>")) {
            result = result.substring(0, result.length - 3)
        }

        if (result.startsWith("<![CDATA[")) {
            result = result.substring(9)
        }

        return result.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    /** 重置工具执行状态 */
    fun reset() {}

    /** 根据名称获取注册的工具执行器 */
    fun getToolExecutor(toolName: String): ToolExecutor? {
        return availableTools[toolName]
    }

    /** 从AI响应中提取工具调用 */
    fun extractToolInvocations(response: String): List<ToolInvocation> {
        val invocations = mutableListOf<ToolInvocation>()
        val content = response

        kotlinx.coroutines.runBlocking {
            val charStream = content.stream()
            val plugins = listOf(com.xihe.assistant.util.stream.plugins.StreamXmlPlugin())

            charStream.splitBy(plugins).collect { group ->
                val chunkContent = StringBuilder()
                group.stream.collect { chunk -> chunkContent.append(chunk) }
                val chunkString = chunkContent.toString()

                if (chunkString.isEmpty()) return@collect

                if (group.tag is com.xihe.assistant.util.stream.plugins.StreamXmlPlugin) {
                    if (chunkString.startsWith("<tool") && chunkString.contains("</tool>")) {
                        val nameMatch = MessageContentParser.namePattern.find(chunkString)
                        val toolName = nameMatch?.groupValues?.get(1) ?: return@collect

                        val parameters = mutableListOf<ToolParameter>()
                        MessageContentParser.toolParamPattern.findAll(chunkString).forEach { paramMatch ->
                            val paramName = paramMatch.groupValues[1]
                            val paramValue = paramMatch.groupValues[2]
                            parameters.add(ToolParameter(paramName, unescapeXml(paramValue)))
                        }

                        val tool = AITool(name = toolName, parameters = parameters)
                        invocations.add(ToolInvocation(tool, chunkString, chunkString.indices))
                    }
                }
            }
        }

        Log.d(TAG, "发现 ${invocations.size} 个工具调用: ${invocations.map { it.tool.name }}")
        return invocations
    }

    /** 直接执行工具 */
    fun executeTool(tool: AITool): ToolResult {
        val executor = availableTools[tool.name]

        if (executor == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "工具未找到: ${tool.name}"
            )
        }

        val validationResult = executor.validateParameters(tool)
        if (!validationResult.valid) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = validationResult.errorMessage
            )
        }

        return executor.invoke(tool)
    }
}

/** 工具执行器接口 */
interface ToolExecutor {
    fun invoke(tool: AITool): ToolResult

    fun invokeAndStream(tool: AITool): Flow<ToolResult> = flowOf(invoke(tool))

    fun validateParameters(tool: AITool): ToolValidationResult {
        return ToolValidationResult(valid = true)
    }

    fun getCategory(): ToolCategory {
        return ToolCategory.UI_AUTOMATION
    }
}