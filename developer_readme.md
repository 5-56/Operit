# Operit 研发技术文档

## 📑 项目概览

Operit 是一个基于 Android 平台的高性能 AI Agent，通过 LLM 驱动的灵活工具系统实现复杂交互智能。本文档面向内部研发团队，提供架构设计、实现细节和扩展指南。

## 🏗️ 架构设计

项目采用 Clean Architecture + MVVM 分层架构，实现高内聚低耦合的代码组织：

- **UI 层**：基于 Compose DSL 构建声明式 UI，通过单向数据流保证 UI 一致性
- **业务层**：ViewModel 作为 SSOT，管理 UI 状态和业务逻辑，避免组件间直接依赖
- **数据层**：Repository Pattern 封装数据源访问细节，DataStore 实现持久化存储
- **基础设施层**：基于 Coroutine + Flow 的异步框架，确保主线程零阻塞

### 核心组件

1. **应用基础架构**
   - `OperitApplication`: DI 容器和全局上下文管理
   - `MainActivity`: 入口点和生命周期控制器

2. **AI 引擎模块**
   - `AIService`: AI 底层通信抽象层，支持多种模型平滑切换
   - `EnhancedAIService`: 工具调用增强版服务，实现 LLM-Agent 范式

3. **工具系统框架**
   - `AIToolHandler`: 工具解析和执行引擎，使用工厂模式注册工具实现
   - 多种工具实现类（基于策略模式的插件化架构）

4. **权限管理系统**
   - `ToolPermissionManager`: 细粒度权限控制器，支持运行时权限管理
   - `PermissionRequestOverlay`: 权限弹窗 UI 组件，支持多种展示策略

5. **UI 组件体系**
   - `OperitApp`: 应用级 Composable 容器，管理全局导航
   - 功能模块化 UI 组件，基于 Slot API 实现高度可组合性

6. **系统集成模块**
   - `AdbCommandExecutor`: 基于 Shizuku 的系统命令执行器，实现 ADB 能力下放
   - `UIAccessibilityService`: A11y 服务实现，提供 UI 自动化基础能力
   - `FloatingChatService`: 全局悬浮窗服务，支持多场景 AI 交互

## 💻 技术栈详解

- **Kotlin + KTX**：利用语言特性简化代码，减少模板代码
- **Jetpack Compose**：基于 Kotlin 协程的声明式 UI 框架
- **协程 + Flow**：响应式编程范式，解决回调地狱
- **Shizuku Bridge**：用于获取特权 API 访问权限
- **A11y Framework**：实现跨应用 UI 交互自动化
- **Retrofit + OkHttp**：RESTful API 客户端，处理网络通信

## 🛠️ 工具系统架构

Operit 的核心竞争力在于其灵活的工具系统，采用插件化架构，支持动态扩展：

### 工具分类体系

1. **系统操作工具集**
   - 系统设置管理 API
   - 应用生命周期控制接口
   - 底层系统配置访问层

2. **UI 自动化工具集**
   - UI 树信息提取器
   - 用户交互模拟引擎
   - 元素定位与操作控制器

3. **文件系统工具集**
   - 文件 CRUD 操作封装
   - 目录树遍历与管理
   - 压缩/解压缩实用工具

4. **网络通信工具集**
   - Web 内容获取器
   - HTTP 请求分发器
   - 搜索引擎接口适配器

5. **基础工具集**
   - 表达式计算引擎
   - 设备信息收集器
   - 调试与演示工具

### 工具执行流水线

1. AI 生成结构化工具调用（XML 格式）
2. `AIToolHandler` 进行词法分析和语法解析
3. `ToolPermissionManager` 执行权限检查（基于 RBAC 模型）
4. 工具执行器根据策略模式分发到具体实现
5. 结果封装并返回给 AI 进行下一步决策

## 🔐 权限系统设计

应用实现了一套基于 RBAC 的细粒度权限控制系统：

### 权限级别定义

- **ALLOW**：静默执行，无需用户干预
- **CAUTION**：对敏感操作进行风险评估后询问
- **ASK**：始终要求用户确认
- **FORBID**：禁止执行，直接拒绝请求

### 权限分类矩阵

每个工具类别维护独立的权限策略：

- 系统操作权限域（高风险）
- 网络通信权限域（中风险）
- UI 自动化权限域（高风险）
- 文件读取权限域（中低风险）
- 文件写入权限域（中高风险）

### 权限控制流程

1. 工具调用触发时，首先检查全局主开关状态
2. 根据工具类别查询对应权限域配置
3. CAUTION 级别下，调用危险操作检测器进行评估
4. 需要用户确认时，通过悬浮窗显示权限请求
5. 用户决策结果通过回调传递给调用方

## 🔄 高级特性实现

### 全局悬浮窗

`FloatingChatService` 实现了一个系统级的浮动交互界面：

- 基于 WindowManager 的视图注入
- 自定义拖拽与位置持久化
- 前台服务保活机制
- 状态管理与 UI 刷新策略

### Shizuku 集成

应用与 Shizuku 特权服务深度集成：

- `ShizukuInstaller`：内置安装器，支持一键部署
- `AdbCommandExecutor`：命令执行封装，支持同步和异步模式
- 基于 Binder IPC 的权限请求与状态监控

### 无障碍服务

`UIAccessibilityService` 提供了强大的 UI 自动化能力：

- 事件监听与 UI 树解析
- 元素查找与定位算法
- 动作模拟与反馈处理
- 跨应用操作协议

## 🧪 开发指南

### 添加新工具

1. 实现 `ToolExecutor` 接口，封装具体功能逻辑
2. 在 `AIToolHandler` 中注册工具，分配唯一标识符
3. 在 `ToolCategoryMapper` 中设置适当的权限类别
4. 更新 `EnhancedAIService` 中的系统提示词，使 AI 感知新工具

### 权限管理最佳实践

新增敏感功能时：

1. 进行风险评估，选择合适的权限域
2. 对高风险操作默认设置为 CAUTION 或 ASK
3. 提供清晰的操作说明，帮助用户做出决策
4. 实现降级策略，处理权限被拒绝的情况

### UI 自动化开发规范

实现 UI 自动化工具时：

1. 实现健壮的错误处理和超时机制
2. 提供详细的操作反馈和状态日志
3. 尊重系统限制和安全边界
4. 优先使用 combined_operation 工具提升用户体验

## 📚 API 文档

### AIToolHandler

工具管理核心类：

```kotlin
// 注册工具实现
fun registerTool(name: String, executor: ToolExecutor)

// 解析并执行工具调用
suspend fun extractAndExecuteTool(message: String): ToolExecutionResult

// 执行已解析的工具
suspend fun executeTool(tool: AITool): ToolResult
```

### ToolPermissionManager

权限管理核心类：

```kotlin
// 检查工具执行权限
suspend fun checkToolPermission(tool: AITool): Boolean

// 请求用户授权
private suspend fun requestPermission(tool: AITool): Boolean

// 处理权限结果回调
fun handlePermissionResult(result: PermissionRequestResult)
```

### EnhancedAIService

AI 服务核心类：

```kotlin
// 发送用户消息
fun sendMessage(message: String, callback: (String) -> Unit)

// 取消流式生成
fun cancelStreaming()

// 处理工具执行结果
suspend fun processToolResult(toolResult: ToolResult)
```

## 🔧 构建与部署

### 构建配置

项目使用 Gradle KTS 管理构建配置：

- minSdk = 26 (Android 8.0)
- targetSdk = 33 (Android 13)
- compileSdk = 34
- Compose Compiler 与 Kotlin 版本兼容性配置

### 签名配置

发布构建需配置签名密钥：

```kotlin
// 在 keystore.properties 中配置（不包含在代码库）
signingConfigs {
    create("release") {
        keyAlias = keystoreProperties["keyAlias"] as String
        keyPassword = keystoreProperties["keyPassword"] as String
        storeFile = file(keystoreProperties["storeFile"] as String)
        storePassword = keystoreProperties["storePassword"] as String
    }
}
```

### 核心依赖

关键依赖项包括：

- Jetpack Compose UI 组件库
- Shizuku API 客户端
- Kotlin 协程与 Flow
- OkHttp3 与 Retrofit2
- 各种 AndroidX 支持库

## 🐞 调试技巧

1. 启用开发者选项中的详细日志记录
2. 使用 `adb logcat -s AIToolHandler:D EnhancedAIService:D` 筛选相关日志
3. 工具失败时检查权限状态和系统限制
4. 监控 Shizuku 连接状态和 Binder 通信
5. 使用 Compose 预览功能加速 UI 开发

## 📝 未来规划

1. 实现离线 AI 模型集成，降低网络依赖
2. 添加多用户支持，实现账户隔离
3. 扩展工具集，增加更多系统集成能力
4. 优化错误处理和自动恢复机制
5. 增强 UI 定制化选项和主题支持
6. 实现上下文感知的智能推荐系统 