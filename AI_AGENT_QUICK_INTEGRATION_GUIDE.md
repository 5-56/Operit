# Operit AI Agent 快速集成指南

## 🚀 概述

本指南将帮助您将**增强的AI Agent功能**快速集成到现有的Operit AI项目中，实现完整的AI驱动自动化流程。

---

## 📋 前置条件

### 必需权限
- ✅ **无障碍服务权限** - 用于屏幕信息感知
- ✅ **悬浮窗权限** - 用于操作反馈显示
- ✅ **设备管理权限** - 用于高级操作执行
- ✅ **网络权限** - 用于与AI大脑通信

### 依赖项检查
确保项目中已包含以下依赖：
```kotlin
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"
implementation "androidx.compose.runtime:runtime-livedata:1.5.4"
```

---

## ⚡ 快速集成步骤

### 第一步: 添加核心文件
将以下新文件添加到项目中：

```
app/src/main/java/com/ai/assistance/operit/core/agent/
├── EnhancedScreenPerception.kt      # 增强屏幕感知模块
├── IntelligentActionExecutor.kt     # 智能操作执行器
├── OperitAIAgentController.kt       # AI Agent核心控制器
└── ...
```

### 第二步: 集成到现有服务

#### 2.1 修改 FloatingChatService.kt
```kotlin
// 在 FloatingChatService.kt 中添加
import com.ai.assistance.operit.core.agent.OperitAIAgentController

class FloatingChatService : Service() {
    
    // 添加AI Agent控制器
    private lateinit var aiAgent: OperitAIAgentController
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化AI Agent
        aiAgent = OperitAIAgentController.getInstance(this)
        
        // 现有初始化代码...
    }
    
    // 添加Agent控制方法
    fun executeAgentTask(userIntent: String) {
        lifecycleScope.launch {
            val intent = OperitAIAgentController.UserIntent(
                description = userIntent,
                priority = OperitAIAgentController.UserIntent.Priority.NORMAL
            )
            
            val result = aiAgent.executeUserIntent(intent)
            // 处理结果...
        }
    }
}
```

#### 2.2 增强 MainActivity.kt
```kotlin
// 在 MainActivity.kt 中添加
import com.ai.assistance.operit.core.agent.OperitAIAgentController

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 现有代码...
        
        // 初始化AI Agent系统
        initializeAIAgent()
    }
    
    private fun initializeAIAgent() {
        // 获取AI Agent实例
        val aiAgent = OperitAIAgentController.getInstance(this)
        
        // 检查必要权限
        checkAndRequestPermissions()
    }
    
    private fun checkAndRequestPermissions() {
        // 检查无障碍服务
        if (!UIAccessibilityService.isRunning()) {
            // 引导用户开启无障碍服务
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            // 请求悬浮窗权限
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
```

### 第三步: 集成到UI组件

#### 3.1 创建AI Agent状态指示器
```kotlin
// 在 Compose UI 中添加AI Agent状态显示
@Composable
fun AIAgentStatusIndicator(
    agentController: OperitAIAgentController
) {
    val agentState by agentController.agentState.collectAsState()
    val taskProgress by agentController.taskProgress.collectAsState()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🤖 AI Agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 状态显示
            val statusText = when (agentState) {
                is OperitAIAgentController.AgentState.Idle -> "待机中"
                is OperitAIAgentController.AgentState.PerceivingScreen -> "感知屏幕..."
                is OperitAIAgentController.AgentState.CommunicatingWithAI -> "AI思考中..."
                is OperitAIAgentController.AgentState.ExecutingInstructions -> "执行操作..."
                is OperitAIAgentController.AgentState.TaskCompleted -> "任务完成"
                is OperitAIAgentController.AgentState.Error -> "错误"
                else -> "未知状态"
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = if (agentController.isBusy()) 
                        MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.outline
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // 进度条（如果有任务在执行）
            taskProgress?.let { progress ->
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progress.progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(progress.progress * 100).toInt()}% - ${progress.currentStep}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
```

#### 3.2 添加快速操作按钮
```kotlin
@Composable
fun QuickAgentActions(
    agentController: OperitAIAgentController,
    onTaskExecute: (String) -> Unit
) {
    val quickTasks = listOf(
        "📱 打开设置" to "打开系统设置",
        "📞 拨打电话" to "打开拨号界面", 
        "📷 拍照" to "打开相机应用",
        "🌐 浏览网页" to "打开浏览器"
    )
    
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(quickTasks) { (icon, task) ->
            AssistChip(
                onClick = { onTaskExecute(task) },
                label = { Text(icon) },
                enabled = !agentController.isBusy()
            )
        }
    }
}
```

### 第四步: 集成AI思考过程显示

#### 4.1 AI思考过程组件
```kotlin
@Composable
fun AIThinkingDisplay(
    agentController: OperitAIAgentController
) {
    val aiThinking by agentController.aiThinking.collectAsState()
    
    aiThinking?.let { thinking ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "AI思考过程",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "步骤: ${thinking.step}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = "推理: ${thinking.reasoning}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "置信度: ",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    LinearProgressIndicator(
                        progress = thinking.confidence,
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                    )
                    
                    Text(
                        text = " ${(thinking.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
```

---

## 🔧 高级配置

### 配置AI大脑通信
```kotlin
// 在 AIBrainCommunicator.kt 中实现实际的AI通信
class AIBrainCommunicator {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    suspend fun sendRequest(requestData: JSONObject): AIResponse {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://your-ai-brain-endpoint.com/api/agent")
                .post(requestData.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            // 解析AI响应
            parseAIResponse(responseBody)
        }
    }
    
    private fun parseAIResponse(responseJson: String): AIResponse {
        val json = JSONObject(responseJson)
        
        // 解析指令列表
        val instructions = mutableListOf<AIInstruction>()
        val instructionsArray = json.getJSONArray("instructions")
        
        for (i in 0 until instructionsArray.length()) {
            val instructionJson = instructionsArray.getJSONObject(i)
            instructions.add(
                AIInstruction(
                    type = instructionJson.getString("type"),
                    parameters = parseParameters(instructionJson.getJSONObject("parameters")),
                    description = instructionJson.optString("description")
                )
            )
        }
        
        // 解析思考过程
        val thinkingJson = json.getJSONObject("thinking")
        val thinkingProcess = AIThinkingProcess(
            step = thinkingJson.getString("step"),
            reasoning = thinkingJson.getString("reasoning"),
            confidence = thinkingJson.getDouble("confidence").toFloat(),
            nextAction = thinkingJson.optString("next_action")
        )
        
        return AIResponse(
            taskCompleted = json.getBoolean("task_completed"),
            instructions = instructions,
            thinkingProcess = thinkingProcess,
            confidence = json.getDouble("confidence").toFloat()
        )
    }
}
```

### 安全控制配置
```kotlin
// 增强 SecurityController.kt
class SecurityController(private val context: Context) {
    
    // 危险操作关键词库
    private val dangerousKeywords = setOf(
        "delete", "remove", "uninstall", "reset", "format", "clear",
        "删除", "移除", "卸载", "重置", "格式化", "清空"
    )
    
    // 敏感应用包名
    private val sensitiveApps = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.packageinstaller"
    )
    
    fun checkInstructions(instructions: List<AIInstruction>): SecurityCheckResult {
        instructions.forEach { instruction ->
            // 检查危险操作
            if (isDangerousOperation(instruction)) {
                return SecurityCheckResult(
                    approved = false,
                    reason = "检测到危险操作: ${instruction.type}"
                )
            }
            
            // 检查敏感应用操作
            if (isSensitiveAppOperation(instruction)) {
                return SecurityCheckResult(
                    approved = false,
                    reason = "尝试操作敏感应用"
                )
            }
        }
        
        return SecurityCheckResult(approved = true)
    }
    
    private fun isDangerousOperation(instruction: AIInstruction): Boolean {
        return instruction.parameters.values.any { value ->
            dangerousKeywords.any { keyword ->
                value.contains(keyword, ignoreCase = true)
            }
        }
    }
    
    private fun isSensitiveAppOperation(instruction: AIInstruction): Boolean {
        val packageName = instruction.parameters["package_name"]
        return packageName != null && sensitiveApps.contains(packageName)
    }
}
```

---

## 📱 实际使用示例

### 示例1: 在聊天界面中使用
```kotlin
class ChatFragment : Fragment() {
    
    private lateinit var aiAgent: OperitAIAgentController
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        aiAgent = OperitAIAgentController.getInstance(requireContext())
        
        // 设置消息处理
        chatInput.setOnMessageSendListener { message ->
            handleUserMessage(message)
        }
    }
    
    private fun handleUserMessage(message: String) {
        when {
            message.startsWith("/agent ") -> {
                val task = message.removePrefix("/agent ")
                executeAgentTask(task)
            }
            shouldTriggerAgent(message) -> {
                executeAgentTask(message)
            }
            else -> {
                // 普通聊天处理
                handleRegularChat(message)
            }
        }
    }
    
    private fun executeAgentTask(task: String) {
        lifecycleScope.launch {
            try {
                showAgentStartMessage(task)
                
                val intent = OperitAIAgentController.UserIntent(
                    description = task,
                    priority = OperitAIAgentController.UserIntent.Priority.NORMAL
                )
                
                val result = aiAgent.executeUserIntent(intent)
                showTaskResult(result)
                
            } catch (e: Exception) {
                showErrorMessage(e.message ?: "任务执行失败")
            }
        }
    }
    
    private fun shouldTriggerAgent(message: String): Boolean {
        val triggerKeywords = listOf("帮我", "自动", "打开", "设置")
        return triggerKeywords.any { message.contains(it, ignoreCase = true) }
    }
}
```

### 示例2: 在工具箱中集成
```kotlin
@Composable
fun ToolboxScreen() {
    val context = LocalContext.current
    val aiAgent = remember { OperitAIAgentController.getInstance(context) }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Agent 工具卡片
        item {
            ToolCard(
                title = "AI Agent",
                description = "智能自动化助手",
                icon = Icons.Default.SmartToy,
                onClick = {
                    // 启动AI Agent界面
                    launchAIAgentInterface(context)
                }
            )
        }
        
        // 其他工具...
    }
}

private fun launchAIAgentInterface(context: Context) {
    val intent = Intent(context, AIAgentActivity::class.java)
    context.startActivity(intent)
}
```

---

## 🧪 测试和验证

### 单元测试示例
```kotlin
@Test
fun testAIAgentBasicFlow() = runTest {
    val context = mockk<Context>()
    val aiAgent = OperitAIAgentController.getInstance(context)
    
    val intent = OperitAIAgentController.UserIntent(
        description = "测试任务",
        priority = OperitAIAgentController.UserIntent.Priority.NORMAL
    )
    
    // 模拟任务执行
    val result = aiAgent.executeUserIntent(intent)
    
    // 验证结果
    assertTrue(result.success)
    assertNotNull(result.executedSteps)
}
```

### 集成测试清单
- [ ] ✅ 无障碍服务正常工作
- [ ] ✅ 屏幕感知数据获取正确
- [ ] ✅ AI指令执行成功
- [ ] ✅ 操作反馈显示正常
- [ ] ✅ 权限检查和请求流程
- [ ] ✅ 错误处理和恢复机制

---

## 🚀 部署和优化

### 性能优化建议
1. **内存管理**: 及时释放不用的资源
2. **网络优化**: 使用请求缓存和压缩
3. **UI优化**: 使用lazy loading和虚拟化
4. **电池优化**: 合理使用后台任务

### 生产环境配置
```kotlin
// 在 Application 类中初始化
class OperitApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化AI Agent系统
        initializeAIAgentSystem()
    }
    
    private fun initializeAIAgentSystem() {
        // 配置日志级别
        if (BuildConfig.DEBUG) {
            LogUtils.setLogLevel(LogUtils.VERBOSE)
        } else {
            LogUtils.setLogLevel(LogUtils.ERROR)
        }
        
        // 初始化核心组件
        val aiAgent = OperitAIAgentController.getInstance(this)
        
        // 预加载必要资源
        preloadResources()
    }
}
```

---

## 📚 常见问题解答

### Q: 如何处理权限被拒绝的情况？
A: 实现优雅的降级策略，提供手动操作引导。

### Q: AI Agent执行任务时如何防止误操作？
A: 通过SecurityController进行多层安全检查，并提供用户确认机制。

### Q: 如何优化屏幕感知的性能？
A: 使用合适的采样频率，压缩截图数据，并实现智能缓存。

### Q: 如何扩展AI Agent的能力？
A: 通过AIToolHandler注册新的工具，或扩展现有工具的功能。

---

## 🎯 下一步

完成集成后，您可以：

1. **自定义AI大脑** - 连接您的AI服务
2. **扩展工具集** - 添加更多自动化工具
3. **优化界面** - 完善用户交互体验
4. **监控分析** - 添加使用统计和性能监控

通过本指南，您已经成功将强大的AI Agent功能集成到Operit AI项目中，为用户提供了前所未有的智能自动化体验！

---

**🚀 恭喜！您的Operit AI现在已具备完整的AI Agent能力！**