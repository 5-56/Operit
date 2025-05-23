package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.mcp.MCPConfigPreferences
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalSessionManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * MCP 插件部署工具类
 *
 * 负责协调项目分析、命令生成和配置生成等组件完成插件部署
 */
class MCPDeployer(private val context: Context) {

    // 创建一个协程作用域，用于处理异步操作
    private val deployerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "MCPDeployer"
    }

    // 部署状态
    sealed class DeploymentStatus {
        object NotStarted : DeploymentStatus()
        data class InProgress(val message: String) : DeploymentStatus()
        data class Success(val message: String) : DeploymentStatus()
        data class Error(val message: String) : DeploymentStatus()
    }

    /**
     * 部署MCP插件（使用自动生成的命令）
     *
     * @param pluginId 插件ID
     * @param pluginPath 插件安装路径
     * @param environmentVariables 环境变量键值对
     * @param statusCallback 部署状态回调
     */
    suspend fun deployPlugin(
            pluginId: String,
            pluginPath: String,
            environmentVariables: Map<String, String> = emptyMap(),
            statusCallback: (DeploymentStatus) -> Unit
    ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    statusCallback(DeploymentStatus.InProgress("开始部署插件: $pluginId"))
                    Log.d(TAG, "开始部署插件: $pluginId, 路径: $pluginPath")

                    // 验证插件路径
                    val pluginDir = File(pluginPath)
                    if (!pluginDir.exists() || !pluginDir.isDirectory) {
                        Log.e(TAG, "插件目录不存在: $pluginPath")
                        statusCallback(DeploymentStatus.Error("插件目录不存在: $pluginPath"))
                        return@withContext false
                    }

                    // 创建项目分析器
                    val projectAnalyzer = MCPProjectAnalyzer()

                    // 查找README文件
                    val readmeFile = projectAnalyzer.findReadmeFile(pluginDir)
                    val readmeContent = readmeFile?.readText() ?: ""

                    // 分析项目结构
                    statusCallback(DeploymentStatus.InProgress("分析项目结构..."))
                    val projectStructure =
                            projectAnalyzer.analyzeProjectStructure(pluginDir, readmeContent)
                    Log.d(TAG, "项目类型: ${projectStructure.type}")

                    // 创建命令生成器
                    val commandGenerator = MCPCommandGenerator()

                    // 生成部署命令
                    val deployCommands =
                            commandGenerator.generateDeployCommands(projectStructure, readmeContent)
                    if (deployCommands.isEmpty()) {
                        Log.e(TAG, "无法生成部署命令: $pluginId")
                        statusCallback(DeploymentStatus.Error("无法确定如何部署此插件，请查看README手动部署"))
                        return@withContext false
                    }

                    Log.d(TAG, "生成部署命令: $deployCommands")

                    // 创建配置生成器
                    val configGenerator = MCPConfigGenerator()

                    // 生成MCP配置，包含环境变量
                    val mcpConfig = configGenerator.generateMcpConfig(pluginId, projectStructure, environmentVariables)
                    Log.d(TAG, "生成MCP配置: $mcpConfig")

                    // 保存MCP配置
                    statusCallback(DeploymentStatus.InProgress("保存MCP配置..."))
                    val mcpLocalServer = MCPLocalServer.getInstance(context)
                    val configSaveResult = mcpLocalServer.savePluginConfig(pluginId, mcpConfig)

                    if (!configSaveResult) {
                        Log.e(TAG, "保存MCP配置失败: $pluginId")
                        statusCallback(DeploymentStatus.Error("保存MCP配置失败"))
                        return@withContext false
                    }

                    // 执行部署命令
                    return@withContext executeDeployCommands(
                            pluginId, 
                            pluginPath, 
                            deployCommands, 
                            statusCallback,
                            configGenerator.extractServerNameFromConfig(mcpConfig)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "部署插件时出错", e)
                    statusCallback(DeploymentStatus.Error("部署出错: ${e.message}"))
                    return@withContext false
                }
            }
            
    /**
     * 部署MCP插件（使用自定义命令）
     *
     * @param pluginId 插件ID
     * @param pluginPath 插件安装路径
     * @param customCommands 自定义部署命令列表
     * @param environmentVariables 环境变量键值对
     * @param statusCallback 部署状态回调
     */
    suspend fun deployPluginWithCommands(
            pluginId: String,
            pluginPath: String,
            customCommands: List<String>,
            environmentVariables: Map<String, String> = emptyMap(),
            statusCallback: (DeploymentStatus) -> Unit
    ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    statusCallback(DeploymentStatus.InProgress("开始部署插件: $pluginId"))
                    Log.d(TAG, "开始部署插件(自定义命令): $pluginId, 路径: $pluginPath")

                    // 验证插件路径
                    val pluginDir = File(pluginPath)
                    if (!pluginDir.exists() || !pluginDir.isDirectory) {
                        Log.e(TAG, "插件目录不存在: $pluginPath")
                        statusCallback(DeploymentStatus.Error("插件目录不存在: $pluginPath"))
                        return@withContext false
                    }

                    // 创建项目分析器（仅用于分析项目类型和生成配置）
                    val projectAnalyzer = MCPProjectAnalyzer()
                    val readmeFile = projectAnalyzer.findReadmeFile(pluginDir)
                    val readmeContent = readmeFile?.readText() ?: ""
                    
                    // 分析项目结构以便生成配置
                    statusCallback(DeploymentStatus.InProgress("分析项目结构..."))
                    val projectStructure = projectAnalyzer.analyzeProjectStructure(pluginDir, readmeContent)
                    
                    // 创建配置生成器
                    val configGenerator = MCPConfigGenerator()
                    
                    // 生成MCP配置，包含环境变量
                    val mcpConfig = configGenerator.generateMcpConfig(pluginId, projectStructure, environmentVariables)
                    
                    // 保存MCP配置
                    statusCallback(DeploymentStatus.InProgress("保存MCP配置..."))
                    val mcpLocalServer = MCPLocalServer.getInstance(context)
                    val configSaveResult = mcpLocalServer.savePluginConfig(pluginId, mcpConfig)

                    if (!configSaveResult) {
                        Log.e(TAG, "保存MCP配置失败: $pluginId")
                        statusCallback(DeploymentStatus.Error("保存MCP配置失败"))
                        return@withContext false
                    }
                    
                    Log.d(TAG, "使用自定义命令: $customCommands")
                    
                    // 执行部署命令
                    return@withContext executeDeployCommands(
                            pluginId, 
                            pluginPath, 
                            customCommands, 
                            statusCallback,
                            configGenerator.extractServerNameFromConfig(mcpConfig)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "使用自定义命令部署插件时出错", e)
                    statusCallback(DeploymentStatus.Error("部署出错: ${e.message}"))
                    return@withContext false
                }
            }
        
    /**
     * 执行部署命令（提取的公共方法）
     */
    private suspend fun executeDeployCommands(
            pluginId: String,
            pluginPath: String,
            deployCommands: List<String>,
            statusCallback: (DeploymentStatus) -> Unit,
            serverName: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 获取终端会话
            val session = TerminalSessionManager.createSession("MCP-Deploy-$pluginId")

            // 执行命令
            val successFlag = AtomicBoolean(true)

            // 定义Termux数据目录路径
            val termuxPluginDir =
                    "/data/data/com.termux/files/home/mcp_plugins/${pluginId.split("/").last()}"

            // 首先创建Termux目标目录
            statusCallback(DeploymentStatus.InProgress("创建Termux目录: $termuxPluginDir"))

            TerminalSessionManager.executeSessionCommand(
                    context = context,
                    session = session,
                    command = "mkdir -p $termuxPluginDir",
                    onOutput = { output -> Log.d(TAG, "创建目录输出: $output") },
                    onInteractivePrompt = { prompt, executionId ->
                        Log.w(TAG, "创建目录出现交互提示: $prompt ($executionId)")
                        null
                    },
                    onComplete = { exitCode, success ->
                        if (!success) {
                            Log.e(TAG, "创建Termux目录失败，退出码: $exitCode")
                            successFlag.set(false)
                        }
                    }
            )

            if (!successFlag.get()) {
                statusCallback(DeploymentStatus.Error("创建Termux目录失败"))
                return@withContext false
            }

            // 复制插件文件到Termux目录
            statusCallback(DeploymentStatus.InProgress("复制插件文件到Termux目录..."))

            TerminalSessionManager.executeSessionCommand(
                    context = context,
                    session = session,
                    command = "cp -r $pluginPath/* $termuxPluginDir/",
                    onOutput = { output -> Log.d(TAG, "复制文件输出: $output") },
                    onInteractivePrompt = { prompt, executionId ->
                        Log.w(TAG, "复制文件出现交互提示: $prompt ($executionId)")
                        null
                    },
                    onComplete = { exitCode, success ->
                        if (!success) {
                            Log.e(TAG, "复制文件到Termux目录失败，退出码: $exitCode")
                            successFlag.set(false)
                        }
                    }
            )

            if (!successFlag.get()) {
                statusCallback(DeploymentStatus.Error("复制文件到Termux目录失败"))
                return@withContext false
            }

            // 切换到Termux插件目录
            statusCallback(DeploymentStatus.InProgress("切换到Termux插件目录"))

            TerminalSessionManager.executeSessionCommand(
                    context = context,
                    session = session,
                    command = "cd $termuxPluginDir",
                    onOutput = { output -> Log.d(TAG, "目录切换输出: $output") },
                    onInteractivePrompt = { prompt, executionId ->
                        Log.w(TAG, "目录切换出现交互提示: $prompt ($executionId)")
                        null
                    },
                    onComplete = { exitCode, success ->
                        if (!success) {
                            Log.e(TAG, "切换目录失败，退出码: $exitCode")
                            successFlag.set(false)
                        }
                    }
            )

            if (!successFlag.get()) {
                statusCallback(DeploymentStatus.Error("切换到Termux插件目录失败"))
                return@withContext false
            }

            // 安装依赖并配置环境
            for ((index, command) in deployCommands.withIndex()) {
                // 跳过启动命令，只执行依赖安装命令
                if (command.contains("python -m") ||
                                (command.contains("node ") &&
                                        !command.contains(
                                                "node ./node_modules/typescript"
                                        )) ||
                                command.contains("npm start") ||
                                command.startsWith("#")
                ) {
                    continue
                }

                val cleanCommand = command.trim()
                if (cleanCommand.isBlank()) continue

                // 判断是否是非关键命令（如npm配置命令）
                val isNonCriticalCommand =
                        cleanCommand.contains("npm config set") ||
                                cleanCommand.contains("|| true") ||
                                cleanCommand.contains("npm install -g") ||
                                cleanCommand.startsWith("npm config")

                statusCallback(
                        DeploymentStatus.InProgress(
                                "执行命令 (${index + 1}/${deployCommands.size}): $cleanCommand"
                        )
                )
                Log.d(TAG, "执行命令 (${index + 1}/${deployCommands.size}): $cleanCommand")

                val commandSuccess = AtomicBoolean(true)

                TerminalSessionManager.executeSessionCommand(
                        context = context,
                        session = session,
                        command = cleanCommand,
                        onOutput = { output ->
                            val trimmedOutput = output.trim()
                            if (trimmedOutput.isNotEmpty()) {
                                Log.d(TAG, "命令输出: $trimmedOutput")
                                statusCallback(
                                        DeploymentStatus.InProgress("输出: $trimmedOutput")
                                )
                            }
                        },
                        onInteractivePrompt = { prompt, executionId ->
                            Log.d(TAG, "收到交互式提示: $prompt (执行ID: $executionId)")
                            statusCallback(DeploymentStatus.InProgress("需要交互: $prompt"))
                            null
                        },
                        onComplete = { exitCode, success ->
                            Log.d(TAG, "命令'$cleanCommand'执行完成: 退出码=$exitCode, 成功=$success")
                            commandSuccess.set(success)
                        }
                )

                // 如果命令失败
                if (!commandSuccess.get()) {
                    if (isNonCriticalCommand) {
                        // 对于非关键命令，即使失败也继续
                        Log.w(TAG, "非关键命令执行失败，但将继续部署: $cleanCommand")
                        statusCallback(
                                DeploymentStatus.InProgress(
                                        "非关键命令执行失败，继续后续步骤: $cleanCommand"
                                )
                        )
                    } else {
                        // 关键命令失败，中止部署
                        Log.e(TAG, "命令执行失败: $cleanCommand")
                        statusCallback(DeploymentStatus.Error("命令执行失败: $cleanCommand"))
                        return@withContext false
                    }
                }
            }

            // 构建部署成功消息
            val successMessage = StringBuilder()
            successMessage.append("插件部署成功: $pluginId\n")
            successMessage.append("Termux部署路径: $termuxPluginDir\n")

            // 如果有MCP配置，添加服务器名称
            val finalServerName = serverName ?: pluginId.split("/").last().lowercase()
            successMessage.append("服务器名称: $finalServerName\n")

            // 保存当前时间作为部署时间
            val currentTime = System.currentTimeMillis()
            successMessage.append("部署时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(currentTime))}")

            // 保存部署成功状态到配置中
            try {
                val mcpConfigPreferences = MCPConfigPreferences(context)
                mcpConfigPreferences.saveDeploySuccess(pluginId, true)
                Log.d(TAG, "已保存部署成功状态: $pluginId")
            } catch (e: Exception) {
                Log.e(TAG, "保存部署成功状态失败: ${e.message}")
            }

            statusCallback(DeploymentStatus.Success(successMessage.toString()))
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "执行部署命令时出错", e)
            statusCallback(DeploymentStatus.Error("部署出错: ${e.message}"))
            return@withContext false
        }
    }
}
