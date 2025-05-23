package com.ai.assistance.operit.data.mcp.plugins

import android.util.Log
import java.io.File

/**
 * MCP 命令生成器
 *
 * 负责生成MCP插件的部署和启动命令
 */
class MCPCommandGenerator {

    companion object {
        private const val TAG = "MCPCommandGenerator"
    }

    /**
     * 生成部署命令
     *
     * @param projectStructure 项目结构
     * @param readmeContent README内容，用于查找特殊安装命令
     * @return 部署命令列表
     */
    fun generateDeployCommands(
            projectStructure: ProjectStructure,
            readmeContent: String = ""
    ): List<String> {
        val commands = mutableListOf<String>()

        when (projectStructure.type) {
            ProjectType.PYTHON -> {
                // 首先检查README中是否有特定的pip安装命令
                val pipInstallCommand = findSpecificPipInstallCommand(readmeContent)

                if (pipInstallCommand != null) {
                    // 使用README中指定的pip安装命令
                    Log.d(TAG, "使用README中指定的pip安装命令: $pipInstallCommand")
                    commands.add(pipInstallCommand)
                } else if (projectStructure.hasRequirementsTxt) {
                    commands.add("pip install -r requirements.txt")
                } else if (projectStructure.hasPyprojectToml) {
                    commands.add("pip install .")
                } else if (projectStructure.hasSetupPy) {
                    commands.add("pip install .")
                } else {
                    // 尝试从插件名称生成安装命令
                    val packageName = extractPackageNameFromDirectory(projectStructure)
                    if (packageName != null) {
                        commands.add("pip install $packageName")
                    }
                }
            }
            ProjectType.TYPESCRIPT -> {
                // TypeScript项目部署命令
                if (projectStructure.hasPackageJson) {
                    // 添加npm换源命令 - 使用国内淘宝镜像提高安装速度和成功率
                    commands.add("npm config set registry https://registry.npmmirror.com")
                    commands.add("# 如果换源失败，将继续使用默认源")

                    // 安装依赖，禁用脚本执行
                    commands.add("npm install --ignore-scripts")

                    // 检查是否有构建脚本
                    // 使用已解析的scripts信息
                    if (projectStructure.packageJsonScripts != null) {
                        val scripts = projectStructure.packageJsonScripts

                        // 首先尝试找到prepare脚本，这通常会在npm install时自动执行
                        if (scripts.has("prepare")) {
                            val prepareScript = scripts.getString("prepare")

                            // 如果prepare脚本是npm run xxx形式，则直接执行对应的脚本
                            if (prepareScript.startsWith("npm run ")) {
                                val targetScript = prepareScript.substring(8).trim()
                                if (scripts.has(targetScript)) {
                                    val actualScript = scripts.getString(targetScript)

                                    // 转换脚本内容为直接命令
                                    val command = convertNpmScriptToDirectCommand(actualScript)
                                    commands.add(command)
                                }
                            } else {
                                // 直接转换prepare脚本
                                val command = convertNpmScriptToDirectCommand(prepareScript)
                                commands.add(command)
                            }
                        }
                        // 如果没有prepare脚本或者已处理，再检查build脚本
                        else if (scripts.has("build")) {
                            val buildScript = scripts.getString("build")

                            // 转换脚本内容为直接命令
                            val command = convertNpmScriptToDirectCommand(buildScript)
                            commands.add(command)
                        }
                        // 再检查compile脚本
                        else if (scripts.has("compile")) {
                            val compileScript = scripts.getString("compile")

                            // 转换脚本内容为直接命令
                            val command = convertNpmScriptToDirectCommand(compileScript)
                            commands.add(command)
                        }
                        // 最后检查tsc脚本
                        else if (scripts.has("tsc")) {
                            val tscScript = scripts.getString("tsc")

                            // 转换脚本内容为直接命令
                            val command = convertNpmScriptToDirectCommand(tscScript)
                            commands.add(command)
                        } else {
                            // 使用默认的TypeScript编译命令
                            commands.add(
                                    "node ./node_modules/typescript/bin/tsc -p ./tsconfig.json"
                            )
                        }
                    } else if (projectStructure.hasTypeScriptDependency) {
                        commands.add("node ./node_modules/typescript/bin/tsc -p ./tsconfig.json")
                    } else if (projectStructure.hasTsConfig) {
                        commands.add("npm install typescript --save-dev || true")
                        commands.add("node ./node_modules/typescript/bin/tsc -p ./tsconfig.json")
                    }

                    // 查找编译后的JS文件
                    val mainTsFile = projectStructure.mainTsFile
                    if (mainTsFile != null) {
                        // 这里我们只添加运行命令，因为我们无法确定编译后确切的JS文件路径
                        if (projectStructure.configExample != null) {
                            // 如果有配置示例，我们将依赖它
                        } else {
                            // 如果没有配置示例，添加一条注释命令
                            commands.add("# TypeScript编译后可能需要执行: node dist/index.js 或类似命令")
                        }
                    }
                }
            }
            ProjectType.NODEJS -> {
                // Node.js项目部署命令
                if (projectStructure.hasPackageJson) {
                    // 同样为Node.js项目添加换源设置
                    commands.add("npm config set registry https://registry.npmmirror.com")
                    commands.add("# 如果换源失败，将继续使用默认源")
                    commands.add("npm install")
                }

                val mainFile = projectStructure.mainJsFile
                if (mainFile != null) {
                    commands.add("node $mainFile")
                } else {
                    commands.add("npm start")
                }
            }
            ProjectType.UNKNOWN -> {
                // 无法确定项目类型，尝试通用命令
                Log.w(TAG, "无法确定项目类型，尝试通用命令")
            }
        }

        return commands
    }

    /** 从README内容中查找特定的pip安装命令 */
    private fun findSpecificPipInstallCommand(readmeContent: String): String? {
        if (readmeContent.isBlank()) return null

        // 使用正则表达式查找pip install命令
        val pipInstallRegex =
                "(?:```[\\s\\S]*?)?pip install\\s+[\\w\\-_\\.]+(?:[\\s\\S]*?```)?".toRegex()
        val pipMatches = pipInstallRegex.findAll(readmeContent)

        for (match in pipMatches) {
            val command = match.value
            // 清理命令，去除可能的代码块标记
            val cleanCommand = command.replace("```", "").replace("`", "").trim()

            // 确保这是一个合法的pip install命令
            if (cleanCommand.startsWith("pip install") &&
                            !cleanCommand.contains("pip install .") &&
                            !cleanCommand.contains("pip install -r") &&
                            !cleanCommand.contains("pip install -e")
            ) {
                return cleanCommand
            }
        }

        // 没有找到特定的pip安装命令
        return null
    }

    /** 从目录名提取可能的包名 */
    private fun extractPackageNameFromDirectory(projectStructure: ProjectStructure): String? {
        // 获取插件目录名
        val pluginDir = File(projectStructure.mainPythonModule ?: "").parentFile?.parentFile
        val dirName = pluginDir?.name ?: return null

        // 常见的包名格式转换
        return when {
            dirName.startsWith("mcp-server-") -> dirName
            dirName.startsWith("mcp_server_") -> dirName
            dirName.contains("mcp") && dirName.contains("server") -> {
                // 尝试标准化包名格式
                "mcp-server-" +
                        dirName.replace("mcp-server-", "")
                                .replace("mcp_server_", "")
                                .replace("mcp-", "")
                                .replace("mcp_", "")
                                .replace("_server", "")
                                .replace("-server", "")
                                .lowercase()
            }
            else -> null
        }
    }

    /** 将npm脚本转换为直接命令 */
    private fun convertNpmScriptToDirectCommand(scriptContent: String): String {
        // 如果脚本包含多个命令（用&&连接），需要分别处理每个命令
        if (scriptContent.contains("&&")) {
            val commands = scriptContent.split("&&").map { it.trim() }
            return commands
                    .map { command -> processIndividualCommand(command) }
                    .joinToString(" && ")
        }

        // 单个命令的情况
        return processIndividualCommand(scriptContent)
    }

    /** 处理单个命令 */
    private fun processIndividualCommand(command: String): String {
        var result = command

        // 处理npm run xxx形式的命令
        val npmRunRegex = "npm\\s+run\\s+([\\w\\-]+)".toRegex()
        val npmRunMatch = npmRunRegex.find(result)
        if (npmRunMatch != null) {
            val scriptName = npmRunMatch.groupValues[1]
            // 这里无法直接获取脚本内容，因为我们不在这个上下文中访问packageJson
            // 而是返回一个特殊标记，需要上层调用处理
            return "##NPM_RUN_SCRIPT:$scriptName##"
        }

        // 替换tsc命令
        if (result.contains("tsc ") || result == "tsc") {
            result = result.replace("tsc", "node ./node_modules/typescript/bin/tsc")

            // 只为TypeScript编译器命令添加tsconfig参数
            if (!result.contains("-p ") && !result.contains("--project ")) {
                result += " -p ./tsconfig.json"
            }
        }

        // 替换npx tsc命令
        if (result.contains("npx tsc")) {
            result = result.replace("npx tsc", "node ./node_modules/typescript/bin/tsc")

            // 只为TypeScript编译器命令添加tsconfig参数
            if (!result.contains("-p ") && !result.contains("--project ")) {
                result += " -p ./tsconfig.json"
            }
        }

        // 替换shx命令
        if (result.contains("shx ")) {
            result = result.replace("shx ", "node ./node_modules/.bin/shx ")
        }

        // 替换其他常见的npx命令
        val npxRegex = "npx\\s+([\\w\\-]+)".toRegex()
        result =
                npxRegex.replace(result) { matchResult ->
                    val packageName = matchResult.groupValues[1]
                    "node ./node_modules/$packageName/bin/$packageName"
                }

        return result
    }
}
