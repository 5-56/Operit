package com.ai.assistance.operit.core.agent

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Agent任务模板
 */
data class AgentTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val prompt: String,
    val expectedSteps: List<String> = emptyList(),
    val requiredTools: List<String> = emptyList(),
    val difficulty: TemplateDifficulty = TemplateDifficulty.MEDIUM,
    val estimatedTime: String = "未知",
    val tags: List<String> = emptyList()
)

/**
 * 模板难度等级
 */
enum class TemplateDifficulty {
    EASY,      // 简单任务
    MEDIUM,    // 中等任务  
    HARD,      // 复杂任务
    EXPERT     // 专家级任务
}

/**
 * Agent模板管理器 - 管理预设任务模板和脚本模板
 */
class AgentTemplateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AgentTemplateManager"
        
        @Volatile
        private var INSTANCE: AgentTemplateManager? = null
        
        fun getInstance(context: Context): AgentTemplateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentTemplateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val templates = mutableListOf<AgentTemplate>()
    
    init {
        loadDefaultTemplates()
        loadCustomTemplates()
    }
    
    /**
     * 加载默认模板
     */
    private fun loadDefaultTemplates() {
        templates.clear()
        
        // 文件管理类模板
        templates.addAll(createFileManagementTemplates())
        
        // 系统监控类模板
        templates.addAll(createSystemMonitoringTemplates())
        
        // 数据处理类模板
        templates.addAll(createDataProcessingTemplates())
        
        // 应用管理类模板
        templates.addAll(createAppManagementTemplates())
        
        // 开发辅助类模板
        templates.addAll(createDevelopmentTemplates())
        
        Log.d(TAG, "已加载 ${templates.size} 个默认模板")
    }
    
    /**
     * 创建文件管理类模板
     */
    private fun createFileManagementTemplates(): List<AgentTemplate> {
        return listOf(
            AgentTemplate(
                id = "file_organizer",
                name = "文件整理助手",
                description = "自动整理指定目录中的文件，按类型分类到不同文件夹",
                category = "文件管理",
                prompt = "帮我整理 {directory} 目录中的文件，将相同类型的文件放到对应的文件夹中",
                expectedSteps = listOf(
                    "扫描目录中的所有文件",
                    "按文件扩展名分类",
                    "创建对应的分类文件夹",
                    "移动文件到对应文件夹",
                    "生成整理报告"
                ),
                requiredTools = listOf("list_files", "create_directory", "move_file"),
                difficulty = TemplateDifficulty.EASY,
                estimatedTime = "1-3分钟",
                tags = listOf("文件", "整理", "分类")
            ),
            
            AgentTemplate(
                id = "duplicate_finder",
                name = "重复文件查找",
                description = "查找并处理指定目录中的重复文件",
                category = "文件管理",
                prompt = "帮我查找 {directory} 目录中的重复文件，并提供处理建议",
                expectedSteps = listOf(
                    "扫描目录中的所有文件",
                    "计算文件哈希值",
                    "识别重复文件",
                    "生成重复文件报告",
                    "提供处理建议"
                ),
                requiredTools = listOf("list_files", "read_file", "file_info"),
                difficulty = TemplateDifficulty.MEDIUM,
                estimatedTime = "2-5分钟",
                tags = listOf("文件", "重复", "清理")
            ),
            
            AgentTemplate(
                id = "batch_rename",
                name = "批量文件重命名",
                description = "按照指定规则批量重命名文件",
                category = "文件管理",
                prompt = "帮我将 {directory} 目录中的文件按照 {pattern} 规则进行批量重命名",
                expectedSteps = listOf(
                    "扫描目标目录",
                    "分析重命名规则",
                    "预览重命名结果",
                    "执行批量重命名",
                    "生成操作日志"
                ),
                requiredTools = listOf("list_files", "rename_file"),
                difficulty = TemplateDifficulty.MEDIUM,
                estimatedTime = "1-2分钟",
                tags = listOf("文件", "重命名", "批量")
            )
        )
    }
    
    /**
     * 创建系统监控类模板
     */
    private fun createSystemMonitoringTemplates(): List<AgentTemplate> {
        return listOf(
            AgentTemplate(
                id = "system_health_check",
                name = "系统健康检查",
                description = "全面检查系统状态，包括存储、内存、应用等",
                category = "系统监控",
                prompt = "帮我进行系统健康检查，包括存储空间、内存使用、运行应用等信息",
                expectedSteps = listOf(
                    "检查存储空间使用情况",
                    "监控内存使用状态",
                    "列出正在运行的应用",
                    "检查系统温度和电池",
                    "生成健康报告"
                ),
                requiredTools = listOf("get_storage_info", "get_memory_info", "list_running_apps"),
                difficulty = TemplateDifficulty.EASY,
                estimatedTime = "30秒-1分钟",
                tags = listOf("系统", "监控", "健康检查")
            ),
            
            AgentTemplate(
                id = "performance_monitor",
                name = "性能监控报告",
                description = "监控系统性能指标并生成详细报告",
                category = "系统监控",
                prompt = "帮我监控系统性能指标，生成详细的性能分析报告",
                expectedSteps = listOf(
                    "收集CPU使用率",
                    "监控内存占用",
                    "检查网络状态",
                    "分析应用性能",
                    "生成性能报告"
                ),
                requiredTools = listOf("get_system_info", "monitor_performance"),
                difficulty = TemplateDifficulty.MEDIUM,
                estimatedTime = "1-2分钟",
                tags = listOf("性能", "监控", "分析")
            ),
            
            AgentTemplate(
                id = "cleanup_assistant",
                name = "系统清理助手",
                description = "清理系统垃圾文件和缓存，释放存储空间",
                category = "系统监控",
                prompt = "帮我清理系统中的垃圾文件和缓存，释放存储空间",
                expectedSteps = listOf(
                    "扫描临时文件",
                    "识别缓存文件",
                    "查找日志文件",
                    "清理垃圾文件",
                    "生成清理报告"
                ),
                requiredTools = listOf("list_files", "delete_file", "get_storage_info"),
                difficulty = TemplateDifficulty.MEDIUM,
                estimatedTime = "2-5分钟",
                tags = listOf("清理", "缓存", "存储")
            )
        )
    }
    
    /**
     * 创建数据处理类模板
     */
    private fun createDataProcessingTemplates(): List<AgentTemplate> {
        return listOf(
            AgentTemplate(
                id = "web_data_fetcher",
                name = "网络数据获取",
                description = "从指定网址获取数据并保存到文件",
                category = "数据处理",
                prompt = "帮我从 {url} 获取数据，并保存到 {filename} 文件中",
                expectedSteps = listOf(
                    "发送HTTP请求",
                    "解析响应数据",
                    "格式化数据内容",
                    "保存到指定文件",
                    "验证保存结果"
                ),
                requiredTools = listOf("http_get", "write_file"),
                difficulty = TemplateDifficulty.EASY,
                estimatedTime = "30秒-1分钟",
                tags = listOf("网络", "数据", "保存")
            ),
            
            AgentTemplate(
                id = "json_processor",
                name = "JSON数据处理",
                description = "处理和转换JSON格式的数据文件",
                category = "数据处理",
                prompt = "帮我处理 {input_file} 中的JSON数据，{operation}，并保存到 {output_file}",
                expectedSteps = listOf(
                    "读取JSON文件",
                    "解析JSON数据",
                    "执行数据处理",
                    "格式化输出数据",
                    "保存处理结果"
                ),
                requiredTools = listOf("read_file", "write_file"),
                difficulty = TemplateDifficulty.MEDIUM,
                estimatedTime = "1-2分钟",
                tags = listOf("JSON", "数据处理", "转换")
            ),
            
            AgentTemplate(
                id = "csv_analyzer",
                name = "CSV数据分析",
                description = "分析CSV文件中的数据并生成统计报告",
                category = "数据处理",
                prompt = "帮我分析 {csv_file} 中的数据，生成统计报告",
                expectedSteps = listOf(
                    "读取CSV文件",
                    "解析数据结构",
                    "计算统计指标",
                    "生成可视化图表",
                    "输出分析报告"
                ),
                requiredTools = listOf("read_file", "write_file"),
                difficulty = TemplateDifficulty.HARD,
                estimatedTime = "3-5分钟",
                tags = listOf("CSV", "数据分析", "统计")
            )
        )
    }
    
    /**
     * 创建应用管理类模板
     */
    private fun createAppManagementTemplates(): List<AgentTemplate> {
        return listOf(
            AgentTemplate(
                id = "app_installer",
                name = "应用批量安装",
                description = "批量安装指定目录中的APK文件",
                category = "应用管理",
                prompt = "帮我批量安装 {directory} 目录中的所有APK文件",
                expectedSteps = listOf(
                    "扫描APK文件",
                    "验证文件完整性",
                    "逐个安装应用",
                    "记录安装结果",
                    "生成安装报告"
                ),
                requiredTools = listOf("list_files", "install_apk"),
                difficulty = TemplateDifficulty.MEDIUM,
                estimatedTime = "5-10分钟",
                tags = listOf("应用", "安装", "批量")
            ),
            
            AgentTemplate(
                id = "app_backup",
                name = "应用数据备份",
                description = "备份指定应用的数据和设置",
                category = "应用管理",
                prompt = "帮我备份 {app_name} 应用的数据和设置",
                expectedSteps = listOf(
                    "识别应用包名",
                    "获取应用数据路径",
                    "创建备份目录",
                    "复制应用数据",
                    "生成备份清单"
                ),
                requiredTools = listOf("get_app_info", "copy_file", "create_directory"),
                difficulty = TemplateDifficulty.HARD,
                estimatedTime = "2-5分钟",
                tags = listOf("应用", "备份", "数据")
            )
        )
    }
    
    /**
     * 创建开发辅助类模板
     */
    private fun createDevelopmentTemplates(): List<AgentTemplate> {
        return listOf(
            AgentTemplate(
                id = "log_analyzer",
                name = "日志分析工具",
                description = "分析应用日志文件，提取关键信息",
                category = "开发辅助",
                prompt = "帮我分析 {log_file} 日志文件，提取错误和警告信息",
                expectedSteps = listOf(
                    "读取日志文件",
                    "解析日志格式",
                    "提取错误信息",
                    "统计问题类型",
                    "生成分析报告"
                ),
                requiredTools = listOf("read_file", "write_file"),
                difficulty = TemplateDifficulty.MEDIUM,
                estimatedTime = "1-3分钟",
                tags = listOf("日志", "分析", "调试")
            ),
            
            AgentTemplate(
                id = "code_formatter",
                name = "代码格式化工具",
                description = "格式化指定目录中的代码文件",
                category = "开发辅助",
                prompt = "帮我格式化 {directory} 目录中的 {language} 代码文件",
                expectedSteps = listOf(
                    "扫描代码文件",
                    "分析代码语言",
                    "应用格式化规则",
                    "保存格式化结果",
                    "生成格式化报告"
                ),
                requiredTools = listOf("list_files", "read_file", "write_file"),
                difficulty = TemplateDifficulty.HARD,
                estimatedTime = "2-5分钟",
                tags = listOf("代码", "格式化", "开发")
            )
        )
    }
    
    /**
     * 获取所有模板
     */
    fun getAllTemplates(): List<AgentTemplate> {
        return templates.toList()
    }
    
    /**
     * 按类别获取模板
     */
    fun getTemplatesByCategory(category: String): List<AgentTemplate> {
        return templates.filter { it.category == category }
    }
    
    /**
     * 获取所有类别
     */
    fun getAllCategories(): List<String> {
        return templates.map { it.category }.distinct().sorted()
    }
    
    /**
     * 按难度获取模板
     */
    fun getTemplatesByDifficulty(difficulty: TemplateDifficulty): List<AgentTemplate> {
        return templates.filter { it.difficulty == difficulty }
    }
    
    /**
     * 按标签搜索模板
     */
    fun searchTemplatesByTag(tag: String): List<AgentTemplate> {
        return templates.filter { template ->
            template.tags.any { it.contains(tag, ignoreCase = true) }
        }
    }
    
    /**
     * 根据ID获取模板
     */
    fun getTemplateById(id: String): AgentTemplate? {
        return templates.find { it.id == id }
    }
    
    /**
     * 搜索模板
     */
    fun searchTemplates(query: String): List<AgentTemplate> {
        val lowerQuery = query.lowercase()
        return templates.filter { template ->
            template.name.lowercase().contains(lowerQuery) ||
            template.description.lowercase().contains(lowerQuery) ||
            template.tags.any { it.lowercase().contains(lowerQuery) }
        }
    }
    
    /**
     * 获取推荐模板（基于使用频率和用户偏好）
     */
    fun getRecommendedTemplates(limit: Int = 5): List<AgentTemplate> {
        // 简单的推荐逻辑：优先推荐简单和中等难度的常用模板
        return templates
            .filter { it.difficulty in listOf(TemplateDifficulty.EASY, TemplateDifficulty.MEDIUM) }
            .sortedBy { it.difficulty }
            .take(limit)
    }
    
    /**
     * 应用模板参数
     */
    fun applyTemplate(template: AgentTemplate, parameters: Map<String, String>): String {
        var prompt = template.prompt
        parameters.forEach { (key, value) ->
            prompt = prompt.replace("{$key}", value)
        }
        return prompt
    }
    
    /**
     * 添加自定义模板
     */
    fun addCustomTemplate(template: AgentTemplate): Boolean {
        return try {
            if (templates.none { it.id == template.id }) {
                templates.add(template)
                saveCustomTemplates()
                Log.d(TAG, "添加自定义模板: ${template.name}")
                true
            } else {
                Log.w(TAG, "模板ID已存在: ${template.id}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "添加自定义模板失败", e)
            false
        }
    }
    
    /**
     * 删除自定义模板
     */
    fun removeCustomTemplate(templateId: String): Boolean {
        return try {
            val removed = templates.removeAll { it.id == templateId }
            if (removed) {
                saveCustomTemplates()
                Log.d(TAG, "删除自定义模板: $templateId")
            }
            removed
        } catch (e: Exception) {
            Log.e(TAG, "删除自定义模板失败", e)
            false
        }
    }
    
    /**
     * 保存自定义模板到文件
     */
    private fun saveCustomTemplates() {
        try {
            val customTemplates = templates.filter { !isDefaultTemplate(it.id) }
            val jsonArray = JSONArray()
            
            customTemplates.forEach { template ->
                val jsonObject = JSONObject().apply {
                    put("id", template.id)
                    put("name", template.name)
                    put("description", template.description)
                    put("category", template.category)
                    put("prompt", template.prompt)
                    put("expectedSteps", JSONArray(template.expectedSteps))
                    put("requiredTools", JSONArray(template.requiredTools))
                    put("difficulty", template.difficulty.name)
                    put("estimatedTime", template.estimatedTime)
                    put("tags", JSONArray(template.tags))
                }
                jsonArray.put(jsonObject)
            }
            
            val file = File(context.filesDir, "custom_agent_templates.json")
            file.writeText(jsonArray.toString())
            
        } catch (e: Exception) {
            Log.e(TAG, "保存自定义模板失败", e)
        }
    }
    
    /**
     * 加载自定义模板
     */
    private fun loadCustomTemplates() {
        try {
            val file = File(context.filesDir, "custom_agent_templates.json")
            if (file.exists()) {
                val jsonContent = file.readText()
                val jsonArray = JSONArray(jsonContent)
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val template = AgentTemplate(
                        id = jsonObject.getString("id"),
                        name = jsonObject.getString("name"),
                        description = jsonObject.getString("description"),
                        category = jsonObject.getString("category"),
                        prompt = jsonObject.getString("prompt"),
                        expectedSteps = jsonArrayToStringList(jsonObject.getJSONArray("expectedSteps")),
                        requiredTools = jsonArrayToStringList(jsonObject.getJSONArray("requiredTools")),
                        difficulty = TemplateDifficulty.valueOf(jsonObject.getString("difficulty")),
                        estimatedTime = jsonObject.getString("estimatedTime"),
                        tags = jsonArrayToStringList(jsonObject.getJSONArray("tags"))
                    )
                    templates.add(template)
                }
                
                Log.d(TAG, "加载了 ${jsonArray.length()} 个自定义模板")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载自定义模板失败", e)
        }
    }
    
    /**
     * 将JSONArray转换为String列表
     */
    private fun jsonArrayToStringList(jsonArray: JSONArray): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }
    
    /**
     * 检查是否为默认模板
     */
    private fun isDefaultTemplate(templateId: String): Boolean {
        val defaultIds = setOf(
            "file_organizer", "duplicate_finder", "batch_rename",
            "system_health_check", "performance_monitor", "cleanup_assistant",
            "web_data_fetcher", "json_processor", "csv_analyzer",
            "app_installer", "app_backup",
            "log_analyzer", "code_formatter"
        )
        return templateId in defaultIds
    }
}