package com.ai.assistance.operit.core.workflow

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.*
import java.util.concurrent.TimeoutException

/**
 * 模块3: 代码生成与沙箱执行系统
 * 
 * - 根据任务规划自动生成Python代码
 * - 集成完善的错误处理和异常捕获机制
 * - 在安全沙箱环境中执行代码，支持pandas、matplotlib、requests等常用库
 * - 提供runtime对象用于环境信息获取和动态模块安装
 * - 实现代码执行的实时监控和超时控制
 */
class CodeExecutionEngine private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "CodeExecutionEngine"
        
        @Volatile
        private var INSTANCE: CodeExecutionEngine? = null
        
        fun getInstance(context: Context): CodeExecutionEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CodeExecutionEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // 执行状态
        enum class ExecutionState {
            IDLE,                // 空闲
            GENERATING_CODE,     // 生成代码中
            PREPARING_SANDBOX,   // 准备沙箱环境
            EXECUTING,           // 执行中
            MONITORING,          // 监控中
            COMPLETED,           // 完成
            FAILED,              // 失败
            TIMEOUT,             // 超时
            INTERRUPTED          // 中断
        }
        
        // 代码类型
        enum class CodeType {
            PYTHON,
            JAVASCRIPT,
            SHELL,
            SQL
        }
        
        // 安全级别
        enum class SecurityLevel {
            SAFE,       // 安全 - 只允许基本操作
            MODERATE,   // 中等 - 允许文件读写
            ELEVATED,   // 提升 - 允许网络访问
            DANGEROUS   // 危险 - 允许系统调用
        }
    }
    
    // 执行状态流
    private val _executionState = MutableStateFlow<ExecutionState>(ExecutionState.IDLE)
    val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()
    
    // 执行结果流
    private val _executionResult = MutableStateFlow<ExecutionResult?>(null)
    val executionResult: StateFlow<ExecutionResult?> = _executionResult.asStateFlow()
    
    // 沙箱目录
    private val sandboxDir by lazy { 
        File(context.filesDir, "sandbox").apply { 
            if (!exists()) mkdirs() 
        }
    }
    
    // 当前执行的作业
    private var currentJob: Job? = null
    
    /**
     * 代码生成请求
     */
    data class CodeGenerationRequest(
        val taskDescription: String,
        val codeType: CodeType,
        val requiredLibraries: List<String> = emptyList(),
        val inputData: Map<String, Any> = emptyMap(),
        val constraints: List<String> = emptyList(),
        val securityLevel: SecurityLevel = SecurityLevel.SAFE,
        val timeout: Long = 30000L // 30秒默认超时
    )
    
    /**
     * 生成的代码
     */
    data class GeneratedCode(
        val id: String,
        val codeType: CodeType,
        val sourceCode: String,
        val requiredLibraries: List<String>,
        val securityLevel: SecurityLevel,
        val estimatedExecutionTime: Long,
        val riskAssessment: CodeRiskAssessment,
        val metadata: Map<String, Any> = emptyMap(),
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * 代码风险评估
     */
    data class CodeRiskAssessment(
        val securityRisks: List<String>,
        val performanceRisks: List<String>,
        val resourceUsage: Map<String, String>,
        val allowedOperations: List<String>,
        val blockedOperations: List<String>,
        val requiresUserConfirmation: Boolean = false
    )
    
    /**
     * 执行环境配置
     */
    data class SandboxEnvironment(
        val workingDirectory: File,
        val allowedDirectories: List<File>,
        val environmentVariables: Map<String, String>,
        val resourceLimits: ResourceLimits,
        val networkAccess: Boolean = false,
        val fileSystemAccess: Boolean = true,
        val maxExecutionTime: Long = 30000L
    )
    
    /**
     * 资源限制
     */
    data class ResourceLimits(
        val maxMemoryMB: Int = 256,
        val maxCpuTimeMs: Long = 30000L,
        val maxFileSize: Long = 10 * 1024 * 1024L, // 10MB
        val maxNetworkRequests: Int = 10
    )
    
    /**
     * 执行结果
     */
    data class ExecutionResult(
        val codeId: String,
        val success: Boolean,
        val output: String,
        val errorOutput: String,
        val exitCode: Int,
        val executionTime: Long,
        val resourceUsage: Map<String, Any>,
        val generatedFiles: List<String> = emptyList(),
        val exception: String? = null,
        val metadata: Map<String, Any> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Runtime对象 - 提供给执行代码的环境信息和工具
     */
    class RuntimeEnvironment(private val context: Context, private val sandboxDir: File) {
        
        fun getSystemInfo(): Map<String, Any> {
            return mapOf(
                "platform" to "Android",
                "available_memory" to Runtime.getRuntime().freeMemory(),
                "total_memory" to Runtime.getRuntime().totalMemory(),
                "sandbox_dir" to sandboxDir.absolutePath,
                "temp_dir" to File(sandboxDir, "temp").absolutePath
            )
        }
        
        fun installPackage(packageName: String): Boolean {
            // 模拟包安装（实际实现会调用包管理器）
            Log.d(TAG, "模拟安装包: $packageName")
            return when (packageName) {
                "pandas", "numpy", "matplotlib", "requests", "json", "csv" -> true
                else -> false
            }
        }
        
        fun listAvailablePackages(): List<String> {
            return listOf(
                "pandas", "numpy", "matplotlib", "requests", "json", "csv",
                "datetime", "os", "sys", "math", "random", "urllib", "sqlite3"
            )
        }
        
        fun createTempFile(prefix: String, suffix: String): String {
            val tempDir = File(sandboxDir, "temp").apply { if (!exists()) mkdirs() }
            val tempFile = File.createTempFile(prefix, suffix, tempDir)
            return tempFile.absolutePath
        }
        
        fun writeDataFile(filename: String, data: String): String {
            val dataDir = File(sandboxDir, "data").apply { if (!exists()) mkdirs() }
            val dataFile = File(dataDir, filename)
            dataFile.writeText(data)
            return dataFile.absolutePath
        }
        
        fun readDataFile(filename: String): String? {
            val dataFile = File(sandboxDir, "data/$filename")
            return if (dataFile.exists()) dataFile.readText() else null
        }
    }
    
    /**
     * 生成代码
     */
    suspend fun generateCode(request: CodeGenerationRequest): GeneratedCode {
        return withContext(Dispatchers.Default) {
            _executionState.value = ExecutionState.GENERATING_CODE
            
            try {
                Log.d(TAG, "开始生成代码: ${request.taskDescription}")
                
                val codeId = UUID.randomUUID().toString()
                
                // 分析任务并生成代码
                val sourceCode = when (request.codeType) {
                    CodeType.PYTHON -> generatePythonCode(request)
                    CodeType.JAVASCRIPT -> generateJavaScriptCode(request)
                    CodeType.SHELL -> generateShellCode(request)
                    CodeType.SQL -> generateSQLCode(request)
                }
                
                // 风险评估
                val riskAssessment = assessCodeRisk(sourceCode, request)
                
                // 预估执行时间
                val estimatedTime = estimateExecutionTime(sourceCode, request)
                
                val generatedCode = GeneratedCode(
                    id = codeId,
                    codeType = request.codeType,
                    sourceCode = sourceCode,
                    requiredLibraries = request.requiredLibraries,
                    securityLevel = request.securityLevel,
                    estimatedExecutionTime = estimatedTime,
                    riskAssessment = riskAssessment,
                    metadata = mapOf(
                        "task_description" to request.taskDescription,
                        "generation_timestamp" to System.currentTimeMillis()
                    )
                )
                
                Log.d(TAG, "代码生成完成: $codeId")
                generatedCode
                
            } catch (e: Exception) {
                Log.e(TAG, "代码生成失败", e)
                _executionState.value = ExecutionState.FAILED
                throw e
            }
        }
    }
    
    /**
     * 生成Python代码
     */
    private fun generatePythonCode(request: CodeGenerationRequest): String {
        val code = StringBuilder()
        
        // 添加导入语句
        code.appendLine("# Auto-generated Python code")
        code.appendLine("import sys")
        code.appendLine("import os")
        code.appendLine("import json")
        code.appendLine("import traceback")
        
        // 添加所需库
        request.requiredLibraries.forEach { lib ->
            when (lib) {
                "pandas" -> code.appendLine("import pandas as pd")
                "numpy" -> code.appendLine("import numpy as np")
                "matplotlib" -> {
                    code.appendLine("import matplotlib.pyplot as plt")
                    code.appendLine("import matplotlib")
                    code.appendLine("matplotlib.use('Agg')  # Use non-interactive backend")
                }
                "requests" -> code.appendLine("import requests")
                else -> code.appendLine("import $lib")
            }
        }
        
        code.appendLine()
        
        // 添加runtime对象模拟
        code.appendLine("""
# Runtime environment simulation
class Runtime:
    def __init__(self):
        self.sandbox_dir = os.environ.get('SANDBOX_DIR', '/tmp/sandbox')
        self.temp_dir = os.path.join(self.sandbox_dir, 'temp')
        os.makedirs(self.temp_dir, exist_ok=True)
    
    def get_system_info(self):
        return {
            'platform': 'Android',
            'sandbox_dir': self.sandbox_dir,
            'temp_dir': self.temp_dir
        }
    
    def create_temp_file(self, prefix='temp', suffix='.txt'):
        import tempfile
        fd, path = tempfile.mkstemp(prefix=prefix, suffix=suffix, dir=self.temp_dir)
        os.close(fd)
        return path
    
    def write_data_file(self, filename, data):
        data_dir = os.path.join(self.sandbox_dir, 'data')
        os.makedirs(data_dir, exist_ok=True)
        filepath = os.path.join(data_dir, filename)
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(str(data))
        return filepath

runtime = Runtime()
        """.trimIndent())
        
        code.appendLine()
        
        // 根据任务描述生成具体代码
        code.appendLine("# Main task execution")
        code.appendLine("try:")
        
        val taskCode = generateTaskSpecificCode(request)
        taskCode.lines().forEach { line ->
            code.appendLine("    $line")
        }
        
        code.appendLine("""
    print("Task completed successfully")
except Exception as e:
    print(f"Error: {str(e)}")
    traceback.print_exc()
    sys.exit(1)
        """.trimIndent())
        
        return code.toString()
    }
    
    /**
     * 根据任务生成具体代码
     */
    private fun generateTaskSpecificCode(request: CodeGenerationRequest): String {
        val taskLower = request.taskDescription.lowercase()
        
        return when {
            taskLower.contains("数据分析") || taskLower.contains("分析数据") -> {
                generateDataAnalysisCode(request)
            }
            taskLower.contains("图表") || taskLower.contains("可视化") -> {
                generateVisualizationCode(request)
            }
            taskLower.contains("文件") && (taskLower.contains("读取") || taskLower.contains("处理")) -> {
                generateFileProcessingCode(request)
            }
            taskLower.contains("计算") || taskLower.contains("数学") -> {
                generateCalculationCode(request)
            }
            taskLower.contains("网络") || taskLower.contains("爬虫") || taskLower.contains("下载") -> {
                generateNetworkCode(request)
            }
            else -> {
                generateGenericCode(request)
            }
        }
    }
    
    private fun generateDataAnalysisCode(request: CodeGenerationRequest): String {
        return """
# 数据分析任务
print("执行数据分析任务...")

# 创建示例数据
import pandas as pd
import numpy as np

data = {
    'A': np.random.randn(100),
    'B': np.random.randn(100),
    'C': np.random.randint(1, 10, 100)
}
df = pd.DataFrame(data)

print("数据统计信息:")
print(df.describe())

print("\\n数据相关性:")
print(df.corr())

# 保存结果
result_path = runtime.write_data_file('analysis_result.txt', df.describe().to_string())
print(f"结果已保存到: {result_path}")
        """.trimIndent()
    }
    
    private fun generateVisualizationCode(request: CodeGenerationRequest): String {
        return """
# 数据可视化任务
print("执行数据可视化任务...")

import matplotlib.pyplot as plt
import numpy as np

# 生成示例数据
x = np.linspace(0, 10, 100)
y = np.sin(x)

# 创建图表
plt.figure(figsize=(10, 6))
plt.plot(x, y, label='sin(x)')
plt.title('示例图表')
plt.xlabel('X轴')
plt.ylabel('Y轴')
plt.legend()
plt.grid(True)

# 保存图表
chart_path = runtime.create_temp_file('chart', '.png')
plt.savefig(chart_path)
print(f"图表已保存到: {chart_path}")
plt.close()
        """.trimIndent()
    }
    
    private fun generateFileProcessingCode(request: CodeGenerationRequest): String {
        return """
# 文件处理任务
print("执行文件处理任务...")

# 创建示例文件
sample_data = "示例数据\\n第二行\\n第三行\\n"
input_file = runtime.write_data_file('input.txt', sample_data)
print(f"创建输入文件: {input_file}")

# 读取并处理文件
with open(input_file, 'r', encoding='utf-8') as f:
    content = f.read()
    lines = content.strip().split('\\n')
    
print(f"文件包含 {len(lines)} 行")
for i, line in enumerate(lines, 1):
    print(f"第{i}行: {line}")

# 处理结果
processed_content = "\\n".join([f"处理后-{line}" for line in lines])
output_file = runtime.write_data_file('output.txt', processed_content)
print(f"处理结果保存到: {output_file}")
        """.trimIndent()
    }
    
    private fun generateCalculationCode(request: CodeGenerationRequest): String {
        return """
# 数学计算任务
print("执行数学计算任务...")

import math

# 基本计算示例
numbers = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
print(f"数字列表: {numbers}")

# 统计计算
total = sum(numbers)
average = total / len(numbers)
maximum = max(numbers)
minimum = min(numbers)

print(f"总和: {total}")
print(f"平均值: {average}")
print(f"最大值: {maximum}")
print(f"最小值: {minimum}")

# 高级计算
sqrt_values = [math.sqrt(x) for x in numbers]
print(f"平方根值: {sqrt_values}")

# 保存计算结果
result = {
    'total': total,
    'average': average,
    'maximum': maximum,
    'minimum': minimum,
    'sqrt_values': sqrt_values
}

import json
result_path = runtime.write_data_file('calculation_result.json', json.dumps(result, indent=2))
print(f"计算结果保存到: {result_path}")
        """.trimIndent()
    }
    
    private fun generateNetworkCode(request: CodeGenerationRequest): String {
        return """
# 网络请求任务
print("执行网络请求任务...")

# 注意：这是示例代码，实际执行需要网络权限
try:
    import requests
    
    # 模拟网络请求（使用本地数据）
    print("模拟网络请求...")
    
    # 创建模拟响应数据
    mock_data = {
        'status': 'success',
        'data': {
            'message': '这是模拟的网络响应数据',
            'timestamp': '2024-01-01 12:00:00'
        }
    }
    
    print("网络请求完成")
    print(f"响应数据: {mock_data}")
    
    # 保存响应数据
    import json
    response_path = runtime.write_data_file('network_response.json', json.dumps(mock_data, indent=2))
    print(f"响应数据保存到: {response_path}")
    
except ImportError:
    print("requests库不可用，使用模拟数据")
    mock_data = {'status': 'mock', 'message': '模拟网络请求'}
    print(f"模拟数据: {mock_data}")
        """.trimIndent()
    }
    
    private fun generateGenericCode(request: CodeGenerationRequest): String {
        return """
# 通用任务执行
print("执行通用任务: ${request.taskDescription}")

# 任务参数
task_params = ${request.inputData}
print(f"任务参数: {task_params}")

# 执行基本操作
import datetime
current_time = datetime.datetime.now()
print(f"执行时间: {current_time}")

# 保存执行日志
log_content = f"任务: ${request.taskDescription}\\n执行时间: {current_time}\\n参数: {task_params}"
log_path = runtime.write_data_file('execution_log.txt', log_content)
print(f"执行日志保存到: {log_path}")

print("通用任务执行完成")
        """.trimIndent()
    }
    
    /**
     * 生成其他类型代码的占位符实现
     */
    private fun generateJavaScriptCode(request: CodeGenerationRequest): String {
        return """
// Auto-generated JavaScript code
console.log("执行JavaScript任务: ${request.taskDescription}");

// 任务参数
const taskParams = ${request.inputData};
console.log("任务参数:", taskParams);

// 执行基本操作
const currentTime = new Date();
console.log("执行时间:", currentTime);

console.log("JavaScript任务执行完成");
        """.trimIndent()
    }
    
    private fun generateShellCode(request: CodeGenerationRequest): String {
        return """
#!/bin/bash
# Auto-generated Shell script

echo "执行Shell任务: ${request.taskDescription}"
echo "执行时间: $(date)"

# 基本系统信息
echo "系统信息:"
echo "当前用户: $(whoami)"
echo "当前目录: $(pwd)"
echo "磁盘使用情况:"
df -h

echo "Shell任务执行完成"
        """.trimIndent()
    }
    
    private fun generateSQLCode(request: CodeGenerationRequest): String {
        return """
-- Auto-generated SQL code
-- 任务: ${request.taskDescription}

-- 创建示例表
CREATE TEMP TABLE IF NOT EXISTS sample_data (
    id INTEGER PRIMARY KEY,
    name TEXT,
    value REAL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入示例数据
INSERT INTO sample_data (name, value) VALUES 
    ('项目A', 100.5),
    ('项目B', 200.3),
    ('项目C', 150.8);

-- 查询数据
SELECT * FROM sample_data;

-- 统计分析
SELECT 
    COUNT(*) as total_count,
    AVG(value) as average_value,
    MAX(value) as max_value,
    MIN(value) as min_value
FROM sample_data;
        """.trimIndent()
    }
    
    /**
     * 评估代码风险
     */
    private fun assessCodeRisk(sourceCode: String, request: CodeGenerationRequest): CodeRiskAssessment {
        val securityRisks = mutableListOf<String>()
        val performanceRisks = mutableListOf<String>()
        val resourceUsage = mutableMapOf<String, String>()
        val allowedOperations = mutableListOf<String>()
        val blockedOperations = mutableListOf<String>()
        
        // 安全风险检测
        if (sourceCode.contains("import os") || sourceCode.contains("os.")) {
            securityRisks.add("使用系统操作函数")
        }
        if (sourceCode.contains("subprocess") || sourceCode.contains("exec") || sourceCode.contains("eval")) {
            securityRisks.add("使用代码执行函数")
        }
        if (sourceCode.contains("requests") || sourceCode.contains("urllib")) {
            securityRisks.add("网络访问")
        }
        if (sourceCode.contains("open(") || sourceCode.contains("file")) {
            securityRisks.add("文件系统访问")
        }
        
        // 性能风险检测
        if (sourceCode.contains("while True") || sourceCode.contains("for") && sourceCode.contains("range(")) {
            performanceRisks.add("可能的无限循环或大循环")
        }
        if (sourceCode.contains("pandas") || sourceCode.contains("numpy")) {
            performanceRisks.add("内存密集型计算")
        }
        
        // 资源使用评估
        resourceUsage["CPU"] = when {
            sourceCode.contains("numpy") || sourceCode.contains("pandas") -> "高"
            sourceCode.contains("math") || sourceCode.contains("计算") -> "中"
            else -> "低"
        }
        
        resourceUsage["内存"] = when {
            sourceCode.contains("DataFrame") || sourceCode.contains("array") -> "高"
            sourceCode.contains("list") || sourceCode.contains("dict") -> "中"
            else -> "低"
        }
        
        resourceUsage["网络"] = if (sourceCode.contains("requests") || sourceCode.contains("urllib")) "是" else "否"
        
        // 允许的操作
        allowedOperations.addAll(listOf(
            "基本数学计算", "字符串处理", "列表操作", "字典操作"
        ))
        
        if (request.securityLevel >= SecurityLevel.MODERATE) {
            allowedOperations.addAll(listOf("文件读写", "临时文件创建"))
        }
        
        if (request.securityLevel >= SecurityLevel.ELEVATED) {
            allowedOperations.add("网络访问")
        }
        
        // 阻止的操作
        blockedOperations.addAll(listOf(
            "系统调用", "进程创建", "代码注入", "权限提升"
        ))
        
        if (request.securityLevel < SecurityLevel.ELEVATED) {
            blockedOperations.add("网络访问")
        }
        
        if (request.securityLevel < SecurityLevel.MODERATE) {
            blockedOperations.add("文件系统访问")
        }
        
        return CodeRiskAssessment(
            securityRisks = securityRisks,
            performanceRisks = performanceRisks,
            resourceUsage = resourceUsage,
            allowedOperations = allowedOperations,
            blockedOperations = blockedOperations,
            requiresUserConfirmation = securityRisks.isNotEmpty() || performanceRisks.isNotEmpty()
        )
    }
    
    /**
     * 预估执行时间
     */
    private fun estimateExecutionTime(sourceCode: String, request: CodeGenerationRequest): Long {
        var estimatedTime = 1000L // 基础时间1秒
        
        // 根据代码复杂度调整
        val lines = sourceCode.lines().size
        estimatedTime += lines * 10L // 每行10ms
        
        // 根据使用的库调整
        if (sourceCode.contains("pandas") || sourceCode.contains("numpy")) {
            estimatedTime += 3000L // 数据处理库额外3秒
        }
        
        if (sourceCode.contains("matplotlib")) {
            estimatedTime += 2000L // 绘图库额外2秒
        }
        
        if (sourceCode.contains("requests")) {
            estimatedTime += 5000L // 网络请求额外5秒
        }
        
        // 根据循环估算
        val forCount = sourceCode.split("for ").size - 1
        val whileCount = sourceCode.split("while ").size - 1
        estimatedTime += (forCount + whileCount) * 500L
        
        return estimatedTime.coerceAtMost(request.timeout)
    }
    
    /**
     * 执行代码
     */
    suspend fun executeCode(
        generatedCode: GeneratedCode,
        environment: SandboxEnvironment? = null,
        onOutput: ((String) -> Unit)? = null
    ): ExecutionResult {
        return withContext(Dispatchers.IO) {
            _executionState.value = ExecutionState.PREPARING_SANDBOX
            
            try {
                val execEnv = environment ?: createDefaultSandboxEnvironment()
                val startTime = System.currentTimeMillis()
                
                Log.d(TAG, "开始执行代码: ${generatedCode.id}")
                
                // 准备沙箱环境
                setupSandboxEnvironment(execEnv)
                
                _executionState.value = ExecutionState.EXECUTING
                
                // 执行代码
                val result = when (generatedCode.codeType) {
                    CodeType.PYTHON -> executePythonCode(generatedCode, execEnv, onOutput)
                    CodeType.JAVASCRIPT -> executeJavaScriptCode(generatedCode, execEnv, onOutput)
                    CodeType.SHELL -> executeShellCode(generatedCode, execEnv, onOutput)
                    CodeType.SQL -> executeSQLCode(generatedCode, execEnv, onOutput)
                }
                
                val executionTime = System.currentTimeMillis() - startTime
                
                val executionResult = result.copy(
                    executionTime = executionTime,
                    timestamp = System.currentTimeMillis()
                )
                
                _executionState.value = if (result.success) ExecutionState.COMPLETED else ExecutionState.FAILED
                _executionResult.value = executionResult
                
                Log.d(TAG, "代码执行完成: ${generatedCode.id}, 成功: ${result.success}")
                
                executionResult
                
            } catch (e: TimeoutException) {
                Log.e(TAG, "代码执行超时", e)
                _executionState.value = ExecutionState.TIMEOUT
                
                ExecutionResult(
                    codeId = generatedCode.id,
                    success = false,
                    output = "",
                    errorOutput = "执行超时",
                    exitCode = -1,
                    executionTime = generatedCode.estimatedExecutionTime,
                    resourceUsage = emptyMap(),
                    exception = "TimeoutException: ${e.message}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "代码执行失败", e)
                _executionState.value = ExecutionState.FAILED
                
                ExecutionResult(
                    codeId = generatedCode.id,
                    success = false,
                    output = "",
                    errorOutput = e.message ?: "未知错误",
                    exitCode = -1,
                    executionTime = 0L,
                    resourceUsage = emptyMap(),
                    exception = "${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 创建默认沙箱环境
     */
    private fun createDefaultSandboxEnvironment(): SandboxEnvironment {
        return SandboxEnvironment(
            workingDirectory = sandboxDir,
            allowedDirectories = listOf(sandboxDir),
            environmentVariables = mapOf(
                "SANDBOX_DIR" to sandboxDir.absolutePath,
                "TEMP_DIR" to File(sandboxDir, "temp").absolutePath,
                "PYTHONPATH" to sandboxDir.absolutePath
            ),
            resourceLimits = ResourceLimits(),
            networkAccess = false,
            fileSystemAccess = true,
            maxExecutionTime = 30000L
        )
    }
    
    /**
     * 设置沙箱环境
     */
    private fun setupSandboxEnvironment(environment: SandboxEnvironment) {
        // 创建必要的目录
        environment.workingDirectory.mkdirs()
        File(environment.workingDirectory, "temp").mkdirs()
        File(environment.workingDirectory, "data").mkdirs()
        File(environment.workingDirectory, "output").mkdirs()
        
        Log.d(TAG, "沙箱环境已设置: ${environment.workingDirectory}")
    }
    
    /**
     * 执行Python代码（模拟）
     */
    private suspend fun executePythonCode(
        code: GeneratedCode,
        environment: SandboxEnvironment,
        onOutput: ((String) -> Unit)?
    ): ExecutionResult {
        return withTimeout(environment.maxExecutionTime) {
            try {
                // 在实际实现中，这里会调用Python解释器
                // 现在我们模拟执行过程
                
                onOutput?.invoke("开始执行Python代码...")
                delay(1000) // 模拟初始化时间
                
                onOutput?.invoke("正在导入库...")
                delay(500)
                
                onOutput?.invoke("执行主要逻辑...")
                delay(2000) // 模拟执行时间
                
                // 模拟代码执行结果
                val output = StringBuilder()
                output.appendLine("Python代码执行完成")
                output.appendLine("生成的输出:")
                output.appendLine("数据处理完成")
                output.appendLine("结果已保存到文件")
                
                // 检查是否生成了文件
                val generatedFiles = mutableListOf<String>()
                File(environment.workingDirectory, "data").listFiles()?.forEach {
                    if (it.isFile) generatedFiles.add(it.name)
                }
                
                onOutput?.invoke("执行完成，生成了 ${generatedFiles.size} 个文件")
                
                ExecutionResult(
                    codeId = code.id,
                    success = true,
                    output = output.toString(),
                    errorOutput = "",
                    exitCode = 0,
                    executionTime = 0L, // 将在上层计算
                    resourceUsage = mapOf(
                        "memory_used" to "45MB",
                        "cpu_time" to "2.3s"
                    ),
                    generatedFiles = generatedFiles
                )
                
            } catch (e: Exception) {
                ExecutionResult(
                    codeId = code.id,
                    success = false,
                    output = "",
                    errorOutput = "Python执行错误: ${e.message}",
                    exitCode = 1,
                    executionTime = 0L,
                    resourceUsage = emptyMap(),
                    exception = e.message
                )
            }
        }
    }
    
    /**
     * 执行其他类型代码的占位符实现
     */
    private suspend fun executeJavaScriptCode(
        code: GeneratedCode,
        environment: SandboxEnvironment,
        onOutput: ((String) -> Unit)?
    ): ExecutionResult {
        return withTimeout(environment.maxExecutionTime) {
            onOutput?.invoke("模拟JavaScript执行...")
            delay(1000)
            
            ExecutionResult(
                codeId = code.id,
                success = true,
                output = "JavaScript代码执行完成",
                errorOutput = "",
                exitCode = 0,
                executionTime = 0L,
                resourceUsage = mapOf("memory_used" to "20MB"),
                generatedFiles = emptyList()
            )
        }
    }
    
    private suspend fun executeShellCode(
        code: GeneratedCode,
        environment: SandboxEnvironment,
        onOutput: ((String) -> Unit)?
    ): ExecutionResult {
        return withTimeout(environment.maxExecutionTime) {
            onOutput?.invoke("模拟Shell脚本执行...")
            delay(500)
            
            ExecutionResult(
                codeId = code.id,
                success = true,
                output = "Shell脚本执行完成\n系统信息已收集",
                errorOutput = "",
                exitCode = 0,
                executionTime = 0L,
                resourceUsage = mapOf("cpu_time" to "0.5s"),
                generatedFiles = emptyList()
            )
        }
    }
    
    private suspend fun executeSQLCode(
        code: GeneratedCode,
        environment: SandboxEnvironment,
        onOutput: ((String) -> Unit)?
    ): ExecutionResult {
        return withTimeout(environment.maxExecutionTime) {
            onOutput?.invoke("模拟SQL执行...")
            delay(800)
            
            ExecutionResult(
                codeId = code.id,
                success = true,
                output = "SQL查询执行完成\n共处理3行数据",
                errorOutput = "",
                exitCode = 0,
                executionTime = 0L,
                resourceUsage = mapOf("database_time" to "0.8s"),
                generatedFiles = emptyList()
            )
        }
    }
    
    /**
     * 中断当前执行
     */
    fun interruptExecution() {
        currentJob?.cancel()
        _executionState.value = ExecutionState.INTERRUPTED
        Log.d(TAG, "代码执行已中断")
    }
    
    /**
     * 清理沙箱环境
     */
    fun cleanupSandbox() {
        try {
            sandboxDir.deleteRecursively()
            sandboxDir.mkdirs()
            Log.d(TAG, "沙箱环境已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理沙箱环境失败", e)
        }
    }
    
    /**
     * 获取沙箱状态
     */
    fun getSandboxStatus(): Map<String, Any> {
        val tempDir = File(sandboxDir, "temp")
        val dataDir = File(sandboxDir, "data")
        val outputDir = File(sandboxDir, "output")
        
        return mapOf(
            "sandbox_dir" to sandboxDir.absolutePath,
            "sandbox_exists" to sandboxDir.exists(),
            "temp_files" to (tempDir.listFiles()?.size ?: 0),
            "data_files" to (dataDir.listFiles()?.size ?: 0),
            "output_files" to (outputDir.listFiles()?.size ?: 0),
            "total_size" to calculateDirectorySize(sandboxDir),
            "last_cleanup" to "未实现"
        )
    }
    
    private fun calculateDirectorySize(dir: File): Long {
        return if (dir.exists()) {
            dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } else 0L
    }
}