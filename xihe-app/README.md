# 羲和智能助手 (Xihe Assistant)

## 项目简介

羲和智能助手是一个基于Android平台的智能AI助手应用，相比原版Operit项目，提供了更智能、更自动化的功能体验。

## 主要特性

### 🤖 智能AI对话
- 支持多种AI模型（DeepSeek、OpenAI、Claude等）
- 流式对话体验
- 智能上下文记忆
- 多轮对话支持

### 🔧 智能工具系统
- 文件管理工具
- 系统信息查看
- 网络工具
- 媒体处理工具
- 自动化脚本执行

### 🎯 智能自动化
- 任务自动化调度
- 工作流管理
- 智能触发器
- 自动化日志记录

### 🎤 语音交互
- 语音输入识别
- 语音合成输出
- 语音控制命令
- 多语言支持

### 🎨 现代化UI
- Material Design 3设计
- 深色/浅色主题
- 自适应布局
- 流畅动画效果

## 技术架构

### 核心技术栈
- **开发语言**: Kotlin
- **UI框架**: Jetpack Compose
- **架构模式**: MVVM + Repository
- **数据库**: Room + ObjectBox
- **网络**: Retrofit + OkHttp
- **依赖注入**: 手动依赖注入
- **异步处理**: Kotlin Coroutines

### 项目结构
```
xihe-app/
├── src/main/
│   ├── java/com/xihe/assistant/
│   │   ├── core/                    # 核心功能模块
│   │   │   ├── application/         # 应用主类
│   │   │   ├── ai/                 # AI相关功能
│   │   │   ├── automation/         # 自动化功能
│   │   │   ├── tools/              # 工具系统
│   │   │   └── config/             # 配置管理
│   │   ├── data/                   # 数据层
│   │   │   ├── model/              # 数据模型
│   │   │   ├── repository/         # 数据仓库
│   │   │   ├── dao/                # 数据访问对象
│   │   │   └── preferences/        # 偏好设置
│   │   ├── ui/                     # UI层
│   │   │   ├── main/               # 主界面
│   │   │   ├── features/           # 功能界面
│   │   │   ├── theme/              # 主题样式
│   │   │   └── components/         # 通用组件
│   │   ├── services/               # 服务层
│   │   └── util/                   # 工具类
│   ├── res/                        # 资源文件
│   └── AndroidManifest.xml         # 应用清单
├── build.gradle.kts                # 构建配置
└── README.md                       # 项目说明
```

## 安装和运行

### 环境要求
- Android Studio Arctic Fox 或更高版本
- Android SDK 26 或更高版本
- Kotlin 1.8.0 或更高版本
- Gradle 7.0 或更高版本

### 构建步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd xihe-app
   ```

2. **配置API密钥**
   在 `local.properties` 文件中添加：
   ```properties
   GITHUB_CLIENT_ID=your_github_client_id
   GITHUB_CLIENT_SECRET=your_github_client_secret
   ```

3. **同步项目**
   ```bash
   ./gradlew build
   ```

4. **运行应用**
   - 在Android Studio中打开项目
   - 连接Android设备或启动模拟器
   - 点击运行按钮

### 配置说明

#### API配置
应用支持多种AI服务提供商：
- **DeepSeek**: 默认推荐，性能优秀
- **OpenAI**: GPT系列模型
- **Claude**: Anthropic的AI模型
- **自定义**: 支持自定义API端点

#### 权限配置
应用需要以下权限：
- 网络访问权限
- 存储读写权限
- 麦克风权限（语音功能）
- 相机权限（拍照功能）
- 位置权限（位置相关功能）

## 功能使用

### AI对话
1. 启动应用后，首次使用需要配置API密钥
2. 在聊天界面输入消息，AI会智能回复
3. 支持语音输入和输出
4. 可以发送图片、文件等附件

### 智能自动化
1. 进入"智能自动化"页面
2. 创建自动化任务或工作流
3. 设置触发条件和执行动作
4. 启用自动化功能

### 工具箱
1. 进入"工具箱"页面
2. 选择需要的工具
3. 按照提示操作
4. 查看执行结果

## 开发指南

### 添加新功能
1. 在 `core` 包下创建功能模块
2. 在 `data` 包下定义数据模型
3. 在 `ui/features` 包下创建UI界面
4. 在 `ui/components` 包下创建可复用组件

### 自定义主题
1. 修改 `ui/theme/Color.kt` 中的颜色定义
2. 调整 `ui/theme/Type.kt` 中的字体样式
3. 更新 `res/values/colors.xml` 中的颜色资源

### 添加新工具
1. 在 `core/tools` 包下创建工具类
2. 实现 `ToolExecutor` 接口
3. 在 `AIToolHandler` 中注册工具
4. 添加权限检查和安全验证

## 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 联系方式

- 项目主页: [GitHub Repository]
- 问题反馈: [GitHub Issues]
- 邮箱: [your-email@example.com]

## 致谢

感谢以下开源项目的支持：
- [Operit](https://github.com/AAswordman/Operit) - 原版项目参考
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI框架
- [Material Design](https://material.io/) - 设计规范
- [Kotlin](https://kotlinlang.org/) - 开发语言

---

**羲和智能助手** - 让AI更智能，让生活更简单 🚀