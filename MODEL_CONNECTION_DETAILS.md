# Operit AI - 模型连接详细信息

## 概述

Operit AI 支持多种大语言模型(LLM)提供商，提供灵活的Agent自动化功能。本文档详细介绍了各个提供商的连接配置、API端点、支持的模型以及使用方法。

## 支持的LLM提供商

### 1. OpenAI

**提供商ID**: `openai`
**官方网站**: https://openai.com
**API文档**: https://platform.openai.com/docs

#### 连接配置
```kotlin
val config = AgentConfig(
    llmProvider = "openai",
    llmApiKey = "sk-your-api-key-here",
    llmEndpoint = "https://api.openai.com/v1/chat/completions", // 可选，使用默认端点
    llmModel = "gpt-4o-mini" // 可选，使用默认模型
)
```

#### 支持的模型
- `gpt-4o` - 最新的GPT-4 Omni模型，多模态能力
- `gpt-4o-mini` - GPT-4 Omni的轻量版本（默认）
- `gpt-4-turbo` - GPT-4 Turbo模型
- `gpt-3.5-turbo` - 经典的GPT-3.5模型

#### API Key获取方式
1. 访问 https://platform.openai.com
2. 注册账户并登录
3. 进入 API Keys 页面
4. 点击 "Create new secret key" 创建新的API密钥
5. 复制并保存密钥（仅显示一次）

#### 费用说明
- 按token使用量计费
- GPT-4o-mini: ~$0.00015/1K tokens (输入), ~$0.0006/1K tokens (输出)
- GPT-4o: ~$0.005/1K tokens (输入), ~$0.015/1K tokens (输出)

---

### 2. 通义千问 (Qwen)

**提供商ID**: `qwen` 或 `aliyun`
**官方网站**: https://dashscope.aliyun.com
**API文档**: https://help.aliyun.com/zh/dashscope

#### 连接配置
```kotlin
val config = AgentConfig(
    llmProvider = "qwen",
    llmApiKey = "sk-your-dashscope-api-key",
    llmEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
    llmModel = "qwen-turbo",
    enableThinking = true // 支持思考模式
)
```

#### 支持的模型
- `qwen-turbo` - 通义千问 Turbo版本（默认）
- `qwen-plus` - 通义千问 Plus版本
- `qwen-max` - 通义千问 Max版本，最强性能

#### 特殊功能
- **思考模式**: 支持 `enable_thinking` 参数，模型会显示思考过程
- **中文优化**: 对中文理解和生成有更好的支持

#### API Key获取方式
1. 访问 https://dashscope.console.aliyun.com
2. 使用阿里云账户登录
3. 开通DashScope服务
4. 创建API-KEY
5. 复制密钥用于配置

---

### 3. Claude (Anthropic)

**提供商ID**: `claude` 或 `anthropic`
**官方网站**: https://anthropic.com
**API文档**: https://docs.anthropic.com

#### 连接配置
```kotlin
val config = AgentConfig(
    llmProvider = "claude",
    llmApiKey = "sk-ant-your-api-key",
    llmEndpoint = "https://api.anthropic.com/v1/messages",
    llmModel = "claude-3-haiku-20240307"
)
```

#### 支持的模型
- `claude-3-5-sonnet-20241022` - 最新的Claude 3.5 Sonnet
- `claude-3-haiku-20240307` - Claude 3 Haiku，快速响应（默认）
- `claude-3-opus-20240229` - Claude 3 Opus，最强性能

#### 特色功能
- 长上下文支持（最高200K tokens）
- 强大的代码理解和生成能力
- 安全性和道德约束

#### API Key获取方式
1. 访问 https://console.anthropic.com
2. 创建账户并登录
3. 进入 API Keys 页面
4. 生成新的API密钥
5. 复制密钥进行配置

---

### 4. Gemini (Google)

**提供商ID**: `gemini` 或 `google`
**官方网站**: https://ai.google.dev
**API文档**: https://ai.google.dev/docs

#### 连接配置
```kotlin
val config = AgentConfig(
    llmProvider = "gemini",
    llmApiKey = "your-google-ai-api-key",
    llmEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent",
    llmModel = "gemini-pro"
)
```

#### 支持的模型
- `gemini-pro` - Gemini Pro模型（默认）
- `gemini-pro-vision` - 支持视觉输入的Gemini Pro
- `gemini-1.5-pro` - 最新的Gemini 1.5 Pro

#### 特色功能
- 免费的慷慨配额
- 优秀的代码生成能力
- 多模态支持（文本+图像）

#### API Key获取方式
1. 访问 https://makersuite.google.com/app/apikey
2. 使用Google账户登录
3. 点击 "Create API key"
4. 选择项目并创建密钥
5. 复制API密钥

---

### 5. DeepSeek

**提供商ID**: `deepseek`
**官方网站**: https://www.deepseek.com
**API文档**: https://platform.deepseek.com/api-docs

#### 连接配置
```kotlin
val config = AgentConfig(
    llmProvider = "deepseek",
    llmApiKey = "sk-your-deepseek-api-key",
    llmEndpoint = "https://api.deepseek.com/v1/chat/completions",
    llmModel = "deepseek-chat"
)
```

#### 支持的模型
- `deepseek-chat` - DeepSeek 通用对话模型（默认）
- `deepseek-coder` - DeepSeek 代码专用模型

#### 特色功能
- 高性价比
- 强大的代码理解能力
- 兼容OpenAI API格式

#### API Key获取方式
1. 访问 https://platform.deepseek.com
2. 注册并登录账户
3. 进入API管理页面
4. 创建新的API密钥
5. 复制密钥用于配置

---

### 6. 本地模型 (Ollama)

**提供商ID**: `local` 或 `ollama`
**官方网站**: https://ollama.ai
**文档**: https://github.com/ollama/ollama

#### 连接配置
```kotlin
val config = AgentConfig(
    llmProvider = "local",
    llmApiKey = "", // 本地模型不需要API Key
    llmEndpoint = "http://localhost:11434/api/generate",
    llmModel = "llama2"
)
```

#### 支持的模型
- `llama2` - Meta的Llama 2模型（默认）
- `codellama` - Code Llama代码生成模型
- `mistral` - Mistral AI模型
- `gemma` - Google的Gemma模型

#### 安装和配置
1. **安装Ollama**:
   ```bash
   # Linux
   curl -fsSL https://ollama.ai/install.sh | sh
   
   # macOS
   brew install ollama
   
   # Windows
   # 下载并安装 https://ollama.ai/download
   ```

2. **下载模型**:
   ```bash
   ollama pull llama2
   ollama pull codellama
   ```

3. **启动服务**:
   ```bash
   ollama serve
   ```

#### 优点
- 完全离线运行
- 数据隐私保护
- 无使用费用
- 可自定义模型

---

## Agent配置最佳实践

### 1. 快速配置
适合简单任务和快速测试：
```kotlin
val quickConfig = AgentConfig.createQuickConfig(
    provider = "openai",
    apiKey = "your-api-key",
    maxIterations = 2
)
```

### 2. 性能配置
适合复杂任务和高质量要求：
```kotlin
val performanceConfig = AgentConfig.createPerformanceConfig(
    provider = "claude",
    apiKey = "your-api-key"
)
```

### 3. 安全配置
适合生产环境和安全敏感场景：
```kotlin
val secureConfig = AgentConfig.createSecureConfig(
    provider = "gemini",
    apiKey = "your-api-key"
)
```

### 4. 调试配置
适合开发和调试阶段：
```kotlin
val debugConfig = AgentConfig.createDebugConfig(
    provider = "local",
    apiKey = ""
)
```

---

## 高级配置选项

### 1. 脚本生成配置
```kotlin
val config = AgentConfig(
    maxTokens = 4000,                    // 最大生成Token数
    temperature = 0.7f,                  // 创造性温度 (0.0-2.0)
    enableThinking = true,               // 启用思考模式(Qwen支持)
    scriptLanguage = "javascript"        // 脚本语言
)
```

### 2. 执行配置
```kotlin
val config = AgentConfig(
    executionTimeout = 30000L,           // 执行超时(毫秒)
    maxRetryCount = 2,                   // 失败重试次数
    enableAutoSave = true,               // 自动保存脚本
    enableAutoUpload = false             // 自动上传Git
)
```

### 3. 安全配置
```kotlin
val config = AgentConfig(
    enableSafetyChecks = true,           // 启用安全检查
    allowSystemCommands = false,         // 允许系统命令
    allowNetworkAccess = true,           // 允许网络访问
    allowFileOperations = true           // 允许文件操作
)
```

### 4. 高级功能
```kotlin
val config = AgentConfig(
    enableMemory = true,                 // 启用记忆功能
    memorySize = 100,                    // 记忆条目数
    enableDebugMode = true,              // 调试模式
    customPromptTemplate = "..."         // 自定义提示词模板
)
```

---

## 故障排除

### 常见错误和解决方案

#### 1. API Key 无效
**错误**: `API错误: Invalid API key`
**解决方案**:
- 检查API Key是否正确复制
- 确认API Key未过期
- 验证API Key格式是否正确

#### 2. 网络连接错误
**错误**: `请求失败: Connection timeout`
**解决方案**:
- 检查网络连接
- 确认API端点URL正确
- 尝试使用代理或VPN

#### 3. 模型不存在
**错误**: `模型不存在: model not found`
**解决方案**:
- 检查模型名称拼写
- 确认所选模型在该提供商下可用
- 查看最新的模型列表

#### 4. 配额超限
**错误**: `配额超限: rate limit exceeded`
**解决方案**:
- 等待一段时间后重试
- 检查账户余额
- 升级账户计划

---

## 使用示例

### 基本使用
```kotlin
// 创建配置
val config = AgentConfig(
    llmProvider = "openai",
    llmApiKey = "your-api-key",
    maxIterations = 3
)

// 执行Agent任务
val result = AgentScriptGenerator.agentMain(
    userRequest = "帮我创建一个简单的待办事项管理功能",
    planSteps = listOf(
        "分析需求",
        "设计界面",
        "实现功能",
        "测试验证"
    ),
    config = config,
    context = applicationContext
)

// 检查结果
if (result.success) {
    println("脚本生成成功: ${result.finalScript}")
    println("执行结果: ${result.lastResult}")
    println("成功率: ${result.successRate}")
} else {
    println("执行失败: ${result.error}")
}
```

### 测试连接
```kotlin
// 测试LLM连接
val testResult = LLMServiceFactory.testLLMConnection(config)
if (testResult.isSuccess) {
    println("连接测试成功: ${testResult.getOrNull()}")
} else {
    println("连接测试失败: ${testResult.exceptionOrNull()?.message}")
}
```

---

## 费用估算

| 提供商 | 模型 | 输入价格 (1K tokens) | 输出价格 (1K tokens) | 备注 |
|--------|------|---------------------|---------------------|------|
| OpenAI | GPT-4o-mini | $0.00015 | $0.0006 | 推荐 |
| OpenAI | GPT-4o | $0.005 | $0.015 | 高质量 |
| Qwen | qwen-turbo | ¥0.0008 | ¥0.002 | 中文优化 |
| Claude | claude-3-haiku | $0.00025 | $0.00125 | 快速响应 |
| Gemini | gemini-pro | 免费额度 | 免费额度 | 性价比高 |
| DeepSeek | deepseek-chat | $0.00014 | $0.00028 | 高性价比 |
| 本地 | 任意模型 | 免费 | 免费 | 隐私保护 |

---

## 更新日志

### 2024年12月
- 新增DeepSeek支持
- 优化Qwen思考模式
- 改进错误处理机制
- 添加本地模型支持

### 支持和反馈

如果您在使用过程中遇到问题或有建议，请：
1. 查看本文档的故障排除部分
2. 提交GitHub Issue
3. 发送邮件至：aaswordsman@foxmail.com

---

## 许可证

本项目采用修改版GPLv3许可证，详见 [LICENSE](LICENSE) 文件。