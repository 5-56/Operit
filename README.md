# AuraFlow Agent

> AI 驱动的智能移动自动化客户端

## 📖 项目概述

AuraFlow Agent 是一个基于 Android 的智能自动化客户端，旨在将移动设备转化为 "智能执行器" 和 "传感器"，通过与服务器端 "AI 大脑" 的实时通信，实现意图驱动的自动化任务执行。

### 🎯 核心理念

- **AI 驱动**: 完全由 AI 进行任务规划和决策
- **实时交互**: WebSocket 实现与 AI 大脑的双向通信
- **直观操作**: 按键精灵风格的用户体验
- **开发友好**: 丰富的调试工具和开发辅助功能

## ✨ 主要功能

### 🧠 AI 对话界面
- **实时交互**: 与 AI 大脑进行自然语言对话
- **思考过程可视化**: 实时显示 AI 的推理过程
- **任务状态跟踪**: 动态展示任务执行进度
- **多类型消息**: 支持文本、截图、问答等多种消息类型

### ⚙️ AI 服务配置
- **灵活配置**: 支持自定义 AI 服务和第三方 LLM
- **连接管理**: 实时监控连接状态和服务可用性
- **参数调优**: 可配置 Agent 行为参数
- **一键测试**: 内置连接测试和验证功能

### 🪟 浮动控制窗口
- **按键精灵风格**: 经典的悬浮控制面板
- **三种显示模式**: 完整/紧凑/迷你模式自由切换
- **快捷操作**: 播放/暂停/停止等核心控制
- **状态指示**: 实时显示连接和运行状态

### 🛠️ 开发工具箱
- **UI 调试工具**: 实时分析界面元素信息
- **命令执行器**: 支持 ADB Shell 和 Shizuku 命令
- **日志查看器**: 多级别日志筛选和实时监控
- **文件管理器**: 管理 Agent 生成的各类文件

## 🏗️ 技术架构

### 📦 模块设计

```
app/src/main/java/com/ai/assistance/operit/auraflow/
├── core/                      # 核心功能模块
│   ├── AuraFlowAgentManager   # Agent 管理中心
│   ├── ActionExecutor         # 操作执行引擎  
│   └── ScreenSensor          # 屏幕感知模块
├── protocol/                  # 通信协议
│   ├── AuraFlowMessage       # 消息协议定义
│   └── AuraFlowWebSocketManager # WebSocket 管理
├── ui/                       # 用户界面
│   ├── chat/                 # AI 对话界面
│   ├── config/               # 配置界面
│   ├── floating/             # 浮动窗口
│   └── toolbox/              # 工具箱
└── MainActivity              # 主入口
```

### 🔧 技术栈

- **UI 框架**: Jetpack Compose + Material Design 3
- **架构模式**: MVVM + StateFlow + ViewModel  
- **网络通信**: OkHttp WebSocket + Kotlinx Serialization
- **并发处理**: Kotlin Coroutines + Flow
- **系统集成**: Android Accessibility Service + MediaProjection

### � 核心组件

#### 1. 通信协议层
- **双向消息**: 支持屏幕更新、AI 指令、反馈等多种消息类型
- **自动重连**: 智能重连机制确保连接稳定性
- **消息队列**: 高效的消息流管理和处理

#### 2. 执行引擎
- **多操作支持**: 点击、滑动、输入、按键等 8 种操作类型
- **双模式定位**: 支持坐标和元素 ID 两种定位方式
- **实时反馈**: 操作结果即时反馈给 AI 大脑

#### 3. 感知系统
- **高效截图**: MediaProjection 实现快速屏幕捕获
- **智能压缩**: 自适应图片质量和大小优化
- **状态监控**: 实时监控屏幕变化和系统状态

## 🚀 开始使用

### 📋 系统要求

- Android 7.0 (API 24) 或更高版本
- 启用无障碍服务权限
- 启用悬浮窗显示权限
- 网络连接权限

### 🔧 开发环境

- Android Studio Arctic Fox 或更新版本
- Kotlin 1.8.0+
- Gradle 8.0+
- JDK 11+

### 📦 依赖管理

主要依赖包括：
```gradle
dependencies {
    // Jetpack Compose
    implementation "androidx.compose.ui:ui:$compose_version"
    implementation "androidx.compose.material3:material3:$material3_version"
    
    // 网络通信
    implementation "com.squareup.okhttp3:okhttp:4.12.0"
    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0"
    
    // 响应式编程
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0"
}
```

## 🎮 使用指南

### 1️⃣ 初始配置

1. **安装应用**: 安装 AuraFlow Agent APK
2. **授权权限**: 开启无障碍服务和悬浮窗权限  
3. **配置服务**: 在配置页面设置 AI 大脑服务地址
4. **测试连接**: 验证与 AI 服务的连接状态

### 2️⃣ 基本操作

1. **启动任务**: 在 AI 对话界面点击 "开始自动化任务"
2. **监控进度**: 实时查看 AI 思考过程和执行状态
3. **控制任务**: 使用浮动窗口进行播放/暂停/停止操作
4. **查看日志**: 通过工具箱监控系统运行状态

### 3️⃣ 高级功能

- **UI 调试**: 使用 UI 调试工具分析界面元素
- **命令执行**: 通过命令执行器运行系统命令
- **自定义配置**: 调整 Agent 行为参数和性能设置

## 📊 项目状态

### ✅ 已完成功能

- [x] **核心架构** (100%): 完整的消息协议和通信系统
- [x] **基础功能** (95%): 操作执行、屏幕感知、任务管理
- [x] **用户界面** (85%): AI 对话、配置页面、工具箱
- [x] **开发工具** (70%): UI 调试、命令执行、日志查看

### 🔄 进行中功能

- [ ] **浮动窗口服务**: 系统级悬浮窗实现
- [ ] **权限管理**: 完善的权限检查和引导
- [ ] **配置持久化**: 用户设置的本地存储

### � 待开发功能

- [ ] **语音交互**: 语音输入和 TTS 输出
- [ ] **手势录制**: 复杂手势的录制和回放
- [ ] **性能监控**: 系统性能和资源使用监控
- [ ] **云端同步**: 配置和任务的云端同步

## 🤝 开发贡献

### � 开发流程

1. **Fork 项目**: 创建项目分支
2. **本地开发**: 在本地环境进行开发
3. **测试验证**: 确保功能正常和代码质量  
4. **提交 PR**: 创建 Pull Request

### 📏 代码规范

- 遵循 Kotlin 官方编码规范
- 使用有意义的变量和函数命名
- 添加完整的文档注释
- 保持代码模块化和可测试性

### � 问题报告

如发现问题，请提供：
- 详细的问题描述
- 复现步骤
- 设备和系统信息
- 相关日志和截图

## 📄 开源协议

本项目基于 MIT 协议开源，详见 [LICENSE](LICENSE) 文件。

## � 致谢

感谢所有为 AuraFlow Agent 项目做出贡献的开发者和用户！

---

**AuraFlow Agent** - 让 AI 赋能移动自动化！ 🚀
