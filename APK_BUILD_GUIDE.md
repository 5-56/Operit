# Operit AI - APK构建指南和项目分析

## 项目概述

**项目名称**: Operit AI - 智能助手应用  
**当前版本**: 1.1.5 (versionCode: 6)  
**项目类型**: Android应用，基于Kotlin和Jetpack Compose  
**最小SDK**: Android 8.0 (API 26)  
**目标SDK**: Android 10 (API 29) [已调整为兼容现有环境]  

## 项目特点

Operit AI是移动端首个功能完备的AI智能助手应用，具有以下特色：

- **完全独立运行**：除外部API调用外，完全在Android设备上运行
- **工具调用能力**：深度集成Android权限和各种系统工具
- **WebDev功能**：支持Web开发，可导出为APK和EXE
- **插件系统**：支持Model Context Protocol (MCP)，首款支持MCP的移动应用
- **增强AI能力**：具备向量索引、持久化数据库、上下文记忆等功能

## 技术架构

### 核心技术栈
- **开发语言**: Kotlin
- **UI框架**: Jetpack Compose
- **构建系统**: Gradle 8.7
- **网络库**: Ktor 2.3.7
- **数据库**: Room 2.6.1
- **序列化**: Kotlinx Serialization

### 主要依赖
- Android系统核心库
- Compose UI组件
- 网络通信 (Ktor)
- 数据持久化 (Room)
- 文档处理 (Apache POI, PDFBox)
- 图像处理 (Glide, Coil)
- 加密库 (BouncyCastle)
- Root权限 (LibSU)

## APK构建步骤

### 方案一：使用Android Studio (推荐)

1. **安装Android Studio**
   ```bash
   # 下载并安装Android Studio
   # https://developer.android.com/studio
   ```

2. **安装所需SDK组件**
   - Android SDK Platform 29
   - Android SDK Build-Tools 29.0.3+
   - Android SDK Platform-Tools

3. **导入项目**
   - 打开Android Studio
   - 选择 "Open an existing project"
   - 选择项目根目录

4. **构建APK**
   ```bash
   # 在项目根目录执行
   ./gradlew assembleRelease
   ```

### 方案二：命令行构建

1. **安装Java Development Kit**
   ```bash
   sudo apt install openjdk-11-jdk
   ```

2. **下载并配置Android SDK**
   ```bash
   # 下载Android SDK Command Line Tools
   wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
   
   # 解压并设置环境
   unzip commandlinetools-linux-11076708_latest.zip
   mkdir -p ~/android-sdk/cmdline-tools/latest
   mv cmdline-tools/* ~/android-sdk/cmdline-tools/latest/
   
   # 设置环境变量
   export ANDROID_HOME=~/android-sdk
   export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
   ```

3. **安装必要的SDK组件**
   ```bash
   # 接受许可协议
   yes | sdkmanager --licenses
   
   # 安装所需组件
   sdkmanager "platform-tools" "build-tools;29.0.3" "platforms;android-29"
   ```

4. **配置项目**
   ```bash
   # 创建local.properties文件
   echo "sdk.dir=$ANDROID_HOME" > local.properties
   ```

5. **构建APK**
   ```bash
   # 清理项目
   ./gradlew clean
   
   # 构建Release版本
   ./gradlew assembleRelease
   
   # 或者构建Debug版本
   ./gradlew assembleDebug
   ```

### 方案三：Docker构建环境

1. **创建Dockerfile**
   ```dockerfile
   FROM openjdk:11-jdk-slim
   
   # 安装必要工具
   RUN apt-get update && apt-get install -y wget unzip
   
   # 下载并安装Android SDK
   ENV ANDROID_HOME /opt/android-sdk
   RUN mkdir -p ${ANDROID_HOME}/cmdline-tools
   RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
   RUN unzip commandlinetools-linux-11076708_latest.zip -d ${ANDROID_HOME}/cmdline-tools
   RUN mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest
   
   # 设置环境变量
   ENV PATH ${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools
   
   # 安装SDK组件
   RUN yes | sdkmanager --licenses
   RUN sdkmanager "platform-tools" "build-tools;29.0.3" "platforms;android-29"
   
   WORKDIR /workspace
   ```

2. **构建Docker镜像**
   ```bash
   docker build -t android-build .
   ```

3. **在容器中构建**
   ```bash
   docker run -v $(pwd):/workspace android-build ./gradlew assembleRelease
   ```

## 构建输出

成功构建后，APK文件将位于：
- **Release版本**: `app/build/outputs/apk/release/app-release.apk`
- **Debug版本**: `app/build/outputs/apk/debug/app-debug.apk`

## 签名配置

项目当前使用debug签名配置，生产环境建议：

1. **生成发布密钥**
   ```bash
   keytool -genkey -v -keystore operit-release-key.keystore -alias operit -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **配置签名**
   在`app/build.gradle.kts`中添加：
   ```kotlin
   signingConfigs {
       release {
           keyAlias = "operit"
           keyPassword = "your_key_password"
           storeFile = file("operit-release-key.keystore")
           storePassword = "your_store_password"
       }
   }
   ```

## 故障排除

### 常见问题

1. **SDK许可协议未接受**
   ```bash
   yes | sdkmanager --licenses
   ```

2. **SDK路径配置错误**
   检查`local.properties`文件中的`sdk.dir`配置

3. **内存不足**
   在`gradle.properties`中增加：
   ```properties
   org.gradle.jvmargs=-Xmx4096m -XX:MaxPermSize=512m
   ```

4. **网络问题**
   配置代理或使用阿里云镜像：
   ```properties
   # gradle.properties
   systemProp.http.proxyHost=mirrors.aliyun.com
   systemProp.https.proxyHost=mirrors.aliyun.com
   ```

### 依赖冲突解决

项目已配置资源排除规则来解决依赖冲突，如遇到新的冲突，可在`packaging`块中添加相应的排除规则。

## 性能优化

1. **启用R8代码压缩**
   ```kotlin
   buildTypes {
       release {
           isMinifyEnabled = true
           isShrinkResources = true
       }
   }
   ```

2. **配置ProGuard规则**
   在`proguard-rules.pro`中添加必要的保留规则

3. **启用构建缓存**
   ```bash
   ./gradlew assembleRelease --build-cache
   ```

## 部署建议

1. **测试建议**
   - 在多种设备上测试APK
   - 验证所有权限请求
   - 测试AI功能和工具调用

2. **发布渠道**
   - Google Play Store
   - GitHub Releases
   - 自有分发渠道

3. **版本管理**
   - 遵循语义化版本控制
   - 维护详细的更新日志

## 总结

Operit AI是一个功能强大的移动AI助手应用，具有先进的架构和丰富的功能。通过本指南，您可以成功构建和部署应用。建议使用Android Studio进行开发和构建，以获得最佳的开发体验。

如需更多技术支持，请参考：
- [项目GitHub地址](https://github.com/AAswordman/Operit)
- [用户指南](docx/USER_GUIDE.md)
- [开源共创指南](docx/CONTRIBUTING.md)