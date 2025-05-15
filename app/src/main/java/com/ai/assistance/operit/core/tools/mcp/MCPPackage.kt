package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.PackageToolParameter
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient
import com.ai.assistance.operit.ui.permissions.ToolCategory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/**
 * 表示MCP服务器作为工具包
 *
 * 该类将MCP服务器转换为标准ToolPackage格式，使其可以与现有的PackageManager无缝集成
 */
@Serializable
data class MCPPackage(
        val serverConfig: MCPServerConfig,
        val mcpTools: List<MCPTool> = emptyList()
) {
    companion object {
        private const val TAG = "MCPPackage"

        /**
         * 从服务器创建MCP包
         *
         * @param context 应用上下文
         * @param serverConfig 服务器配置
         * @return 创建的MCP包，如果连接失败则返回null
         */
        fun fromServer(context: Context, serverConfig: MCPServerConfig): MCPPackage? {
            // 创建桥接客户端
            val bridgeClient = MCPBridgeClient(context, serverConfig.name)
            android.util.Log.d(TAG, "正在连接到MCP服务器: ${serverConfig.name}")

            try {
                // 尝试连接
                val connected = runBlocking { bridgeClient.connect() }
                if (!connected) {
                    android.util.Log.w(TAG, "无法连接到MCP服务器: ${serverConfig.name}")
                    return null
                }

                android.util.Log.d(TAG, "成功连接到MCP服务器: ${serverConfig.name}，开始获取工具列表")

                // 获取工具列表
                val jsonTools = runBlocking { bridgeClient.getTools() }
                if (jsonTools.isEmpty()) {
                    android.util.Log.w(TAG, "MCP服务器 ${serverConfig.name} 没有提供任何工具")
                    // 不要因为没有工具就返回null
                    // 返回一个包含空工具列表的有效包
                    android.util.Log.d(TAG, "创建不包含工具的MCP包 - 服务已连接但没有工具")
                    return MCPPackage(serverConfig, emptyList())
                }

                android.util.Log.d(TAG, "成功从MCP服务器获取 ${jsonTools.size} 个工具")

                // 将JSONObject工具转换为MCPTool
                val mcpTools =
                        jsonTools.mapNotNull { jsonTool ->
                            try {
                                // 提取工具信息
                                val name = jsonTool.optString("name", "")

                                // 直接获取描述，如果没有则使用空字符串
                                val description = jsonTool.optString("description", "")

                                if (name.isEmpty()) return@mapNotNull null

                                // 提取参数信息
                                val params = mutableListOf<MCPToolParameter>()
                                // 改为从inputSchema中获取参数信息
                                val inputSchema = jsonTool.optJSONObject("inputSchema")
                                val propertiesObj = inputSchema?.optJSONObject("properties")
                                val requiredArray = inputSchema?.optJSONArray("required")

                                propertiesObj?.keys()?.forEach { paramName ->
                                    val paramObj = propertiesObj.optJSONObject(paramName)
                                    if (paramObj != null) {
                                        val paramDescription = paramObj.optString("description", "")
                                        val paramType = paramObj.optString("type", "string")
                                        val paramRequired =
                                                requiredArray?.let { required ->
                                                    (0 until required.length()).any {
                                                        required.optString(it) == paramName
                                                    }
                                                }
                                                        ?: false

                                        params.add(
                                                MCPToolParameter(
                                                        name = paramName,
                                                        description = paramDescription,
                                                        type = paramType,
                                                        required = paramRequired
                                                )
                                        )
                                    }
                                }

                                MCPTool(name, description, params)
                            } catch (e: Exception) {
                                android.util.Log.e(TAG, "解析MCP工具时出错: ${e.message}")
                                null
                            }
                        }

                // 注意：不要断开连接！让客户端保持活跃状态
                // 客户端会被缓存在MCPManager中以供后续使用
                android.util.Log.d(TAG, "成功创建MCP包，包含 ${mcpTools.size} 个工具，保持连接活跃")
                return MCPPackage(serverConfig, mcpTools)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "创建MCP包时出错: ${e.message}", e)
                // 只有在发生异常时才断开连接
                bridgeClient.disconnect()
                return null
            }
        }
    }

    /** 转换为标准工具包格式 将MCP包转换为与现有PackageManager兼容的ToolPackage格式 */
    fun toToolPackage(): ToolPackage {
        // 将MCP工具转换为标准工具包工具
        val tools =
                mcpTools.map { mcpTool ->
                    // 将MCP工具参数转换为标准工具包参数
                    val params =
                            mcpTool.parameters.map { mcpParam ->
                                PackageToolParameter(
                                        name = mcpParam.name,
                                        description = mcpParam.description,
                                        required = mcpParam.required,
                                        type = mcpParam.type
                                )
                            }

                    // 创建工具包工具 - 只使用工具名称
                    PackageTool(
                            name = mcpTool.name, // 只使用工具名
                            description = mcpTool.description,
                            parameters = params,
                            // 注意：script字段用于存储MCP服务器和工具的信息，用于识别MCP服务器
                            script = generateScriptPlaceholder(serverConfig.name, mcpTool.name)
                    )
                }

        // 创建完整的工具包，使用服务器名称作为包名，不添加任何前缀
        return ToolPackage(
                name = serverConfig.name, // 直接使用服务器名称，不添加mcp:前缀
                description = serverConfig.description,
                tools = tools,
                // 使用标准文件操作类别，可以根据需要调整
                category = ToolCategory.FILE_READ
        )
    }

    /** 生成脚本占位符 用于在script字段中存储MCP服务器和工具的信息，便于后续识别 */
    private fun generateScriptPlaceholder(serverName: String, toolName: String): String {
        return """
            /* MCPJS
            {
                "serverName": "$serverName",
                "toolName": "$toolName",
                "endpoint": "${serverConfig.endpoint}"
            }
            */
            // MCP 工具 - 不是实际的JavaScript脚本
            // 这是一个占位符，用于存储MCP服务器和工具的信息
        """.trimIndent()
    }
}
