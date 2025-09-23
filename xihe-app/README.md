# 羲和助手 (Xihe Assistant)

<div align="center">
  <h1>羲和助手 - AI智能助手应用</h1>
  <p>📱 <b>基于Operit项目架构，打造功能完备的AI智能助手应用</b> 📱</p>
</div>

## 🌟 项目简介

**羲和助手** 是基于 Operit AI 项目架构开发的 AI 智能助手应用，它**完全独立运行**于您的 Android 设备上，拥有强大的**工具调用能力**。它不仅仅是一个聊天界面，更是一个和安卓权限和各种工具深度融合的**全能助手**。

## 🤖 核心功能特性

### AI智能对话
- **多大模型（LLM）支持**：支持 OpenAI、Qwen、Claude 等主流大模型
- **流式对话**：实时流式响应，提供流畅的对话体验
- **上下文记忆**：智能记忆对话上下文，提供连贯的交互体验
- **语音交互**：支持语音输入和语音合成输出

### 智能Agent自动化
- **一键自动理解需求**：输入需求后，agent 自动理解、规划、生成并执行脚本
- **多轮优化**：自动反馈和多轮优化，确保任务完成质量
- **任务中断与回滚**：agent 执行过程中可随时中断，历史脚本可一键回滚
- **历史脚本管理**：所有 agent 生成/优化的脚本自动保存

### 强大的工具系统
内置超过40种强大工具，使AI助手能够与您的设备深度交互：

- **文件系统工具**：读写文件、搜索文件、解压缩、文件转换等
- **HTTP工具**：网络请求、网页访问、文件上传下载等
- **系统操作工具**：管理系统设置、安装应用、控制应用运行等
- **UI自动化工具**：屏幕点击、滑动、元素查找、表单填写等
- **媒体处理工具**：视频转换、编解码、帧提取等

### 悬浮窗功能
- **随时调用**：悬浮窗模式，随时调用AI功能
- **便捷操作**：在任何应用中都可以快速使用AI助手
- **智能定位**：智能窗口定位和大小调整

### 网页开发功能
- **在线开发**：在手机上设计网页并导出为独立应用
- **应用导出**：支持导出为Android APK和Windows可执行文件
- **实时预览**：实时预览开发效果

### 插件生态系统
- **MCP协议**：支持 MCP (Model Context Protocol) 插件
- **JavaScript/TypeScript**：支持 JavaScript 和 TypeScript 插件开发
- **热插拔**：运行时动态加载和卸载插件
- **丰富插件库**：提供丰富的第三方插件

## 🛠️ 技术架构

### 前端架构
- **UI框架**: Jetpack Compose + Material 3 Design
- **架构模式**: MVVM + Repository Pattern
- **状态管理**: StateFlow + Compose State
- **导航**: Navigation Compose with custom back stack management

### 后端架构
- **数据库**: Room + ObjectBox (混合存储方案)
- **网络**: OkHttp + Retrofit
- **异步处理**: Kotlin Coroutines + Flow
- **依赖注入**: 手动依赖注入 (轻量级方案)

### 权限管理
- **Shizuku集成**: 支持系统级权限操作
- **自定义权限系统**: 分级权限管理
- **运行时权限**: 动态权限请求和管理

### 插件系统
- **MCP协议**: 基于Model Context Protocol的插件系统
- **JavaScript引擎**: 内置JavaScript执行引擎
- **TypeScript支持**: 运行时TypeScript编译
- **沙盒执行**: 安全的插件执行环境

## 📱 界面设计

### 响应式布局
- **手机布局**: 抽屉式导航 + 底部输入区域
- **平板布局**: 侧边栏 + 主内容区域，支持侧边栏收起/展开
- **自适应**: 根据屏幕尺寸自动切换布局模式

### 主要界面
1. **AI对话界面**: 流式聊天界面，支持消息气泡、附件、语音等
2. **工具箱**: 分类展示各种内置工具
3. **插件管理**: 管理已安装插件和浏览可用插件
4. **记忆库**: 向量搜索和知识管理
5. **设置中心**: 完整的设置和配置选项

### 主题系统
- **Material 3**: 基于Material Design 3规范
- **动态主题**: 支持Android 12+动态颜色
- **深色模式**: 完整的深色主题支持
- **自定义主题**: 支持用户自定义主题颜色

## 🚀 快速开始

### 系统要求
- **Android版本**: Android 8.0+ (API 26+)
- **内存**: 建议4GB以上
- **存储空间**: 200MB+
- **网络**: 需要网络连接（用于AI API调用）

### 构建步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd xihe-app
```

2. **配置环境**
- 确保已安装 Android Studio
- 设置 Android SDK (API 26+)
- 配置 Kotlin 编译环境

3. **配置依赖**
```bash
# 在项目根目录创建 local.properties
echo "sdk.dir=/path/to/android/sdk" > local.properties
```

4. **构建应用**
```bash
./gradlew assembleDebug
```

5. **安装到设备**
```bash
./gradlew installDebug
```

## 📁 项目结构

```
xihe-app/
├── src/main/
│   ├── java/com/xihe/assistant/
│   │   ├── core/                 # 核心模块
│   │   │   ├── application/      # 应用初始化
│   │   │   ├── tools/           # 工具系统
│   │   │   └── agent/           # Agent系统
│   │   ├── data/                # 数据层
│   │   │   ├── model/           # 数据模型
│   │   │   ├── preferences/     # 偏好设置
│   │   │   ├── mcp/            # MCP插件
│   │   │   └── migration/       # 数据迁移
│   │   ├── ui/                  # UI层
│   │   │   ├── main/           # 主界面
│   │   │   ├── features/       # 功能模块
│   │   │   ├── components/     # 通用组件
│   │   │   └── theme/          # 主题系统
│   │   └── util/               # 工具类
│   ├── res/                    # 资源文件
│   └── AndroidManifest.xml    # 应用清单
└── build.gradle.kts           # 构建脚本
```

## 🔧 核心模块详解

### ChatViewModel
负责聊天界面的状态管理，包括：
- 消息发送和接收
- AI响应处理
- 附件管理
- Agent状态控制

### AIToolHandler
工具系统的核心，提供：
- 工具注册和管理
- 工具执行和结果处理
- 权限检查和安全控制

### XiheApp
主应用组件，管理：
- 导航状态
- 屏幕切换
- 布局适配
- 全局状态

## 🎯 开发计划

### 已完成功能 ✅
- [x] 基础项目架构
- [x] AI聊天界面
- [x] 工具调用系统
- [x] 设置和配置功能
- [x] 响应式布局设计
- [x] 主题系统

### 进行中功能 🔄
- [ ] 悬浮窗功能
- [ ] 语音识别和合成
- [ ] 网页开发功能
- [ ] 插件包管理系统

### 计划功能 📋
- [ ] Agent自动化系统
- [ ] 记忆库和向量搜索
- [ ] MCP插件生态
- [ ] UI自动化工具
- [ ] 媒体处理功能

## 🤝 贡献指南

欢迎各种形式的贡献！

### 贡献方式
1. **Bug报告**: 提交Issue描述问题
2. **功能建议**: 提交Feature Request
3. **代码贡献**: Fork项目并提交Pull Request
4. **插件开发**: 开发MCP插件扩展功能

### 开发规范
- 遵循Kotlin编码规范
- 使用Compose进行UI开发
- 编写清晰的代码注释
- 提交前运行测试

## 📄 许可证

本项目采用**修改版GPLv3许可证**，详见 [LICENSE](LICENSE) 文件。

### 特别说明
- 基于Operit项目架构开发
- 遵循开源协议要求
- 欢迎社区贡献和改进

## 📞 联系方式

- **项目地址**: [GitHub Repository]
- **问题反馈**: [Issue Tracker]
- **邮箱**: xihe.assistant@example.com

---

<div align="center">
  <p>🚀 <b>羲和助手 - 让AI更懂你的需求</b> 🚀</p>
</div>