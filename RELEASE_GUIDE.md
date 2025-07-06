# Operit AI APK 构建与发布指南

## 📋 概述

本指南提供了多种构建Operit AI APK的方法，以及完整的发布流程。无论您是开发者还是想要构建自己版本的用户，都可以找到适合的方案。

## 🔧 构建方案

### 方案1: GitHub Actions 自动构建（推荐）

**优点**: 无需本地环境，自动化程度高，支持一键发布
**适用场景**: 正式发布、持续集成

#### 使用步骤:

1. **推送代码到GitHub仓库**
   ```bash
   git add .
   git commit -m "准备发布版本"
   git push origin main
   ```

2. **触发构建**
   - 自动触发：推送到main分支会自动触发构建
   - 手动触发：在GitHub仓库的Actions页面手动运行工作流
   - 标签触发：推送版本标签会触发构建并创建Release

3. **创建版本标签（可选）**
   ```bash
   git tag -a v1.2.0 -m "Release v1.2.0"
   git push origin v1.2.0
   ```

4. **下载构建产物**
   - 在Actions页面下载构建产物
   - 如果推送了标签，会自动创建GitHub Release

#### 工作流特性:
- ✅ 自动设置Java和Android SDK环境
- ✅ 支持Gradle缓存，提高构建速度
- ✅ 同时构建Debug和Release版本
- ✅ 生成校验和文件
- ✅ 自动创建GitHub Release
- ✅ 包含完整的发布说明

---

### 方案2: Docker 构建（推荐）

**优点**: 环境一致性好，无需安装Android SDK
**适用场景**: 本地开发、测试构建

#### 使用步骤:

1. **确保Docker已安装并运行**
   ```bash
   docker --version
   docker info
   ```

2. **运行Docker构建脚本**
   ```bash
   chmod +x build_with_docker.sh
   ./build_with_docker.sh
   ```

3. **获取构建结果**
   - APK文件位于 `release/Operit-AI-v1.2.0.apk`
   - 包含校验和文件

#### Docker构建特性:
- ✅ 自动下载和配置Android SDK
- ✅ 隔离的构建环境
- ✅ 无需本地Android开发环境
- ✅ 支持多平台（Linux、macOS、Windows）

---

### 方案3: 本地构建

**优点**: 完全控制构建过程，适合开发调试
**适用场景**: 开发环境、定制构建

#### 前置要求:
- Java 11+ (推荐Java 17)
- Android SDK (API 34)
- Android Build Tools (34.0.0)

#### 使用步骤:

1. **设置环境变量**
   ```bash
   export ANDROID_HOME=/path/to/android-sdk
   export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
   ```

2. **运行构建脚本**
   ```bash
   chmod +x build_apk.sh
   ./build_apk.sh
   ```

3. **或者直接使用Gradle命令**
   ```bash
   ./gradlew clean
   ./gradlew assembleRelease
   ```

#### 注意事项:
- 需要从Google Drive下载额外依赖
- 依赖下载地址：https://drive.google.com/drive/folders/1g-Q_i7cf6Ua4KX9ZM6V282EEZvTVVfF7
- 将下载的文件放入项目中有.keep文件的目录

---

## 📦 项目依赖处理

### 外部依赖下载
由于项目使用了一些外部库，需要从Google Drive下载：

1. **访问下载链接**
   ```
   https://drive.google.com/drive/folders/1g-Q_i7cf6Ua4KX9ZM6V282EEZvTVVfF7
   ```

2. **查找.keep文件所在目录**
   ```bash
   find . -name ".keep" -type f
   ```

3. **将下载的文件放入对应目录**
   - 通常是`app/libs/`、`app/src/main/jniLibs/`等目录
   - 确保文件名和路径与项目配置一致

### 依赖文件说明
- **JNI库**: 用于语音识别和AI推理
- **模型文件**: 本地AI模型权重文件
- **资源文件**: 图标、音频等资源

---

## 🚀 发布流程

### 1. 版本准备

#### 更新版本信息
编辑 `app/build.gradle.kts`:
```kotlin
android {
    defaultConfig {
        versionCode = 17  // 递增版本号
        versionName = "1.2.1"  // 更新版本名称
    }
}
```

#### 准备发布说明
创建 `RELEASE_NOTES.md`:
```markdown
# Operit AI v1.2.1 发布说明

## 🆕 新功能
- 功能1描述
- 功能2描述

## 🔧 改进
- 改进1描述
- 改进2描述

## 🐛 Bug修复
- 修复1描述
- 修复2描述
```

### 2. 构建APK

选择以下方案之一:
- **推荐**: 使用GitHub Actions自动构建
- **备选**: 使用Docker本地构建
- **开发**: 使用本地环境构建

### 3. 测试验证

#### 基本测试
- [ ] APK安装测试
- [ ] 应用启动测试
- [ ] 核心功能测试
- [ ] 权限申请测试

#### 兼容性测试
- [ ] 不同Android版本测试
- [ ] 不同设备型号测试
- [ ] 不同屏幕尺寸测试

### 4. 发布到GitHub

#### 自动发布（推荐）
1. 推送版本标签
2. GitHub Actions自动创建Release
3. 检查Release内容

#### 手动发布
1. 前往GitHub仓库的Releases页面
2. 点击"Create a new release"
3. 上传APK文件和校验和文件
4. 填写发布说明

### 5. 发布后处理

#### 更新文档
- [ ] 更新README.md
- [ ] 更新用户指南
- [ ] 更新API文档

#### 社区通知
- [ ] 发布公告
- [ ] 更新项目网站
- [ ] 社交媒体宣传

---

## 🔧 故障排除

### 常见问题

#### 1. 构建失败：找不到Android SDK
**解决方案**:
- 确保设置了ANDROID_HOME环境变量
- 使用Docker构建方案
- 使用GitHub Actions自动构建

#### 2. 依赖错误：缺少外部库
**解决方案**:
- 从Google Drive下载所需依赖
- 确保文件放在正确的目录
- 检查.keep文件所在目录

#### 3. 权限错误：gradlew没有执行权限
**解决方案**:
```bash
chmod +x gradlew
```

#### 4. 内存不足：Gradle构建失败
**解决方案**:
在`gradle.properties`中添加:
```properties
org.gradle.jvmargs=-Xmx4g
```

#### 5. 网络问题：无法下载依赖
**解决方案**:
- 使用代理或VPN
- 使用本地Maven仓库
- 预先下载依赖

### 构建日志分析

#### 查看详细错误
```bash
./gradlew assembleRelease --stacktrace --info
```

#### 清理构建缓存
```bash
./gradlew clean
rm -rf ~/.gradle/caches/
```

---

## 📊 性能优化

### 构建优化

#### Gradle配置优化
```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:+UseG1GC
org.gradle.parallel=true
org.gradle.daemon=true
org.gradle.configureondemand=true
```

#### 依赖版本管理
使用BOM（Bill of Materials）管理依赖版本:
```kotlin
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
```

### APK优化

#### 启用代码压缩
```kotlin
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

#### 资源优化
```kotlin
android {
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
```

---

## 🎯 最佳实践

### 版本管理
- 使用语义化版本号（例如：1.2.0）
- 为每个版本创建Git标签
- 维护详细的变更日志

### 构建管理
- 使用自动化构建流程
- 保持构建环境的一致性
- 定期更新依赖版本

### 发布管理
- 充分测试后再发布
- 提供详细的发布说明
- 保持发布频率的稳定

### 质量保证
- 添加单元测试和集成测试
- 使用代码检查工具
- 监控应用性能和崩溃率

---

## 📞 支持与反馈

### 获取帮助
- **项目主页**: https://github.com/AAswordman/Operit
- **问题报告**: https://github.com/AAswordman/Operit/issues
- **讨论区**: https://github.com/AAswordman/Operit/discussions
- **邮箱**: aaswordsman@foxmail.com

### 贡献指南
- 阅读贡献指南：`CONTRIBUTING.md`
- 提交Issue前搜索已存在的问题
- 提供详细的问题描述和重现步骤
- 遵循代码风格和提交规范

---

## 📄 许可证

本项目采用修改版GPLv3许可证，详情请查看 [LICENSE](LICENSE) 文件。

---

*最后更新时间: 2024年12月6日*
*文档版本: 1.0.0*