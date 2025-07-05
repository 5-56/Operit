# 👨‍💻 Operit AI Agent 开发者指南

## 📚 快速开始

### 环境要求
- Android Studio Arctic Fox 或更高版本
- Kotlin 1.9.0+
- Android SDK 26-34
- Java 11+

### 项目结构
```
app/src/main/java/com/ai/assistance/operit/
├── core/agent/                    # AI Agent核心模块
│   ├── OperitAIAgentController.kt # 主控制器
│   ├── EnhancedScreenPerception.kt # 屏幕感知
│   ├── IntelligentActionExecutor.kt # 智能执行器
│   ├── AIBrainCommunicator.kt     # AI通信
│   ├── SecurityController.kt      # 安全控制
│   ├── scenarios/                 # 场景管理
│   └── optimization/              # 性能优化
├── core/plugin/                   # 插件系统
│   └── PluginManager.kt          # 插件管理器
├── ui/agent/                      # AI Agent UI
│   └── AIAgentControlPanel.kt    # 控制面板
├── services/                      # 系统服务
└── util/                         # 工具类
```

### 核心API使用

#### 1. AI Agent控制器
```kotlin
// 初始化AI Agent
val aiAgent = OperitAIAgentController.getInstance(context)

// 执行用户意图
val intent = OperitAIAgentController.UserIntent("点击登录按钮")
aiAgent.executeUserIntent(intent)

// 监听状态变化
aiAgent.currentState.collect { state ->
    when (state) {
        is AgentState.Idle -> // 空闲状态
        is AgentState.ExecutingInstructions -> // 执行中
        // 其他状态...
    }
}
```

#### 2. 屏幕感知
```kotlin
val screenPerception = EnhancedScreenPerception(context)

// 获取屏幕信息
val screenData = screenPerception.captureScreenInformation()
println("当前应用: ${screenData.contextInfo.currentApp}")
println("可交互元素: ${screenData.uiStructure.elements.size}")
```

#### 3. 智能执行器
```kotlin
val actionExecutor = IntelligentActionExecutor(context)

// 执行点击操作
val tapResult = actionExecutor.executeAction(
    IntelligentActionExecutor.ActionInstruction.Tap(500, 800)
)

// 执行文本输入
val inputResult = actionExecutor.executeAction(
    IntelligentActionExecutor.ActionInstruction.InputText("Hello World")
)
```

#### 4. AI通信
```kotlin
val communicator = AIBrainCommunicator(context)

// 配置AI服务
val config = AIBrainCommunicator.AIServiceConfig(
    baseUrl = "https://api.openai.com/v1",
    apiKey = "your-api-key",
    model = "gpt-4"
)
communicator.configureAIService(config)

// 发送用户意图
val result = communicator.sendUserIntentToAI(
    userIntent = "帮我发送一条微信消息",
    screenData = screenData
)
```

#### 5. 安全控制
```kotlin
val securityController = SecurityController(context)

// 设置安全级别
securityController.setSecurityLevel(SecurityController.SecurityLevel.HIGH)

// 验证操作安全性
val validation = securityController.validateOperation(
    operationType = SecurityController.OperationType.TAP,
    target = "button_login",
    parameters = mapOf("x" to "500", "y" to "800"),
    currentApp = "com.example.app"
)
```

### 插件开发

#### 创建插件
```kotlin
class MyPlugin(
    plugin: PluginManager.Plugin,
    context: Context,
    aiAgent: OperitAIAgentController
) : PluginManager.PluginInstance(plugin, context, aiAgent) {
    
    override suspend fun onLoad() {
        // 插件加载逻辑
    }
    
    override suspend fun onEnable() {
        // 插件启用逻辑
    }
    
    override fun getAPI(): PluginManager.PluginAPI {
        return MyPluginAPI()
    }
}

class MyPluginAPI : PluginManager.PluginAPI {
    override suspend fun executeAction(
        action: String, 
        parameters: Map<String, Any>
    ): PluginManager.PluginResult {
        return when (action) {
            "my_action" -> {
                // 执行自定义操作
                PluginManager.PluginResult(true, "操作成功")
            }
            else -> PluginManager.PluginResult(false, "不支持的操作")
        }
    }
}
```

### 性能优化

#### 使用性能优化器
```kotlin
val optimizer = PerformanceOptimizer(context)

// 设置性能等级
optimizer.setPerformanceLevel(PerformanceOptimizer.PerformanceLevel.HIGH)

// 缓存数据
val cachedData = optimizer.cacheData("key", myData)

// 异步执行任务
val result = optimizer.executeAsync {
    // 耗时操作
}
```

### 最佳实践

1. **内存管理**: 及时释放不需要的资源
2. **异常处理**: 使用try-catch处理可能的异常
3. **权限检查**: 在操作前检查必要权限
4. **日志记录**: 使用LogUtils而不是println
5. **性能监控**: 监控关键操作的执行时间

### 调试技巧

1. **启用调试日志**: 在LogUtils中设置DEBUG级别
2. **使用性能分析器**: 监控内存和CPU使用情况
3. **模拟器测试**: 在不同设备上测试兼容性
4. **权限测试**: 测试在不同权限状态下的行为

### 常见问题

**Q: AI Agent没有响应怎么办？**
A: 检查无障碍服务是否启用，检查权限配置是否正确。

**Q: 屏幕感知不准确怎么办？**
A: 确保目标应用界面已完全加载，检查是否有覆盖层。

**Q: 插件加载失败怎么办？**
A: 检查插件文件完整性，验证依赖关系和权限配置。

### 贡献指南

1. Fork项目仓库
2. 创建功能分支
3. 编写测试用例
4. 提交Pull Request
5. 等待代码审查

---
更多信息请参考项目Wiki或联系开发团队。
