# GitHub Actions 自动构建APK发布指南

## 🚀 概述

本指南将帮助您使用GitHub Actions自动构建Operit AI的APK文件，并自动发布到GitHub Release页面，实现一键发布的完整流程。

## 📋 前置条件

### ✅ 已完成的配置
- [x] GitHub Actions工作流文件已配置 (`.github/workflows/build-apk.yml`)
- [x] 项目代码已推送到GitHub仓库
- [x] 构建脚本已准备完成

### ⚙️ 需要确认的设置
- [ ] GitHub仓库的Actions权限已启用
- [ ] GitHub仓库有写入权限（用于创建Release）
- [ ] 代码已推送到main或master分支

---

## 🎯 三种触发方式

### 方式1: 版本标签触发（推荐）✨

**这是最佳的发布方式，会自动创建GitHub Release**

#### 步骤：

1. **确保代码已提交并推送**
   ```bash
   git add .
   git commit -m "准备发布 v1.2.0"
   git push origin main
   ```

2. **创建并推送版本标签**
   ```bash
   # 创建版本标签
   git tag -a v1.2.0 -m "Release v1.2.0 - 智能助手功能完善版本"
   
   # 推送标签到GitHub
   git push origin v1.2.0
   ```

3. **自动触发构建**
   - 推送标签后，GitHub Actions会自动开始构建
   - 构建成功后会自动创建GitHub Release
   - APK文件会自动上传到Release页面

### 方式2: 手动触发

#### 步骤：

1. **访问GitHub仓库的Actions页面**
   ```
   https://github.com/[您的用户名]/Operit/actions
   ```

2. **选择工作流**
   - 点击左侧的 "Build and Release APK" 工作流

3. **手动运行**
   - 点击右上角的 "Run workflow" 按钮
   - 选择要构建的分支（通常是main）
   - 可选择是否创建Release
   - 点击 "Run workflow" 开始构建

### 方式3: 代码推送自动触发

#### 自动触发条件：
- 推送到 `main` 或 `master` 分支
- 创建Pull Request到这些分支

#### 特点：
- 会构建APK但不会自动创建Release
- APK文件会作为工作流产物保存
- 适合测试和开发

---

## 📦 工作流执行过程

### 🔄 构建阶段

当工作流启动后，会依次执行以下步骤：

1. **🛒 代码检出**
   - 下载最新代码到构建环境

2. **☕ Java环境设置**
   - 安装Java 17
   - 配置Java环境变量

3. **📱 Android SDK设置**
   - 安装Android SDK
   - 配置构建工具

4. **📦 依赖缓存**
   - 缓存Gradle依赖，提高构建速度

5. **🔍 项目检查**
   - 检查项目结构
   - 验证构建文件

6. **🧹 项目清理**
   - 清理之前的构建产物

7. **🔨 构建APK**
   - 构建Debug版本（验证）
   - 构建Release版本（发布）

8. **📋 结果验证**
   - 检查APK文件生成
   - 计算文件大小

9. **📁 准备发布文件**
   - 重命名APK文件
   - 生成校验和文件
   - 创建发布说明

10. **📤 上传产物**
    - 上传为工作流产物
    - 如果是标签触发，创建GitHub Release

---

## 📊 监控构建过程

### 🔍 查看构建状态

1. **访问Actions页面**
   ```
   https://github.com/[您的用户名]/Operit/actions
   ```

2. **查看运行中的工作流**
   - 绿色 ✅ = 成功
   - 红色 ❌ = 失败
   - 黄色 🟡 = 运行中

3. **查看详细日志**
   - 点击具体的运行实例
   - 点击 "build" 作业查看详细步骤
   - 展开每个步骤查看具体日志

### ⏱️ 预计构建时间

- **首次构建**: 10-15分钟（需要下载依赖）
- **后续构建**: 5-8分钟（有缓存）
- **仅代码变更**: 3-5分钟

---

## 📱 获取构建结果

### 🎯 通过GitHub Release（标签触发）

1. **访问Releases页面**
   ```
   https://github.com/[您的用户名]/Operit/releases
   ```

2. **下载APK文件**
   - `Operit-AI-v1.2.0.apk` - 发布版APK（推荐）
   - `Operit-AI-v1.2.0-debug.apk` - 调试版APK
   - 校验和文件（.sha256, .md5）

### 📦 通过工作流产物（其他触发方式）

1. **访问具体的工作流运行页面**
2. **滚动到底部找到 "Artifacts" 部分**
3. **下载 "operit-ai-apk" 压缩包**
4. **解压获取APK文件**

---

## 🔧 常见问题排除

### ❌ 构建失败的常见原因

#### 1. 依赖问题
**症状**: "Could not resolve dependency"
**解决方案**:
```bash
# 检查gradle.properties配置
# 确保网络连接正常
# 等待并重试构建
```

#### 2. 权限问题
**症状**: "Permission denied"
**解决方案**:
- 确保仓库有写入权限
- 检查GITHUB_TOKEN权限
- 确认Actions权限已启用

#### 3. 磁盘空间不足
**症状**: "No space left on device"
**解决方案**:
- GitHub Actions会自动处理
- 重试构建通常能解决

#### 4. 超时问题
**症状**: "Job was cancelled"
**解决方案**:
- 检查网络连接
- 重试构建
- 检查是否有无限循环的代码

### 🔍 调试技巧

#### 查看详细错误信息
```bash
# 在工作流中添加调试步骤
- name: Debug info
  run: |
    echo "Java version: $(java -version)"
    echo "Android Home: $ANDROID_HOME"
    echo "Available space: $(df -h)"
    ./gradlew --version
```

#### 本地验证构建
```bash
# 在本地测试构建命令
./gradlew clean
./gradlew assembleDebug
./gradlew assembleRelease
```

---

## ⚡ 构建优化建议

### 🚀 提高构建速度

#### 1. 优化gradle.properties
```properties
# 添加到gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:+UseG1GC
org.gradle.parallel=true
org.gradle.daemon=true
org.gradle.configureondemand=true
```

#### 2. 使用构建缓存
```yaml
# 工作流中已包含缓存配置
- name: Cache Gradle
  uses: actions/cache@v3
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
```

#### 3. 并行构建
```bash
# 同时构建多个变体
./gradlew assembleDebug assembleRelease --parallel
```

---

## 📋 发布检查清单

### 🔍 发布前验证

- [ ] 代码已提交并推送到GitHub
- [ ] 版本号已更新（app/build.gradle.kts）
- [ ] 发布说明已准备
- [ ] 测试通过
- [ ] 没有编译错误或警告

### 🚀 执行发布

- [ ] 创建版本标签
- [ ] 推送标签到GitHub
- [ ] 等待GitHub Actions构建完成
- [ ] 验证Release页面的APK文件
- [ ] 测试下载和安装

### ✅ 发布后确认

- [ ] APK文件可以正常下载
- [ ] 安装测试成功
- [ ] 核心功能正常工作
- [ ] 发布公告已发布

---

## 📞 获取帮助

### 🔗 有用链接

- **GitHub Actions文档**: https://docs.github.com/en/actions
- **Android构建指南**: https://developer.android.com/studio/build
- **Gradle用户指南**: https://gradle.org/guides/

### 🐛 问题报告

如果遇到问题，请提供以下信息：
- 错误日志截图
- 工作流运行链接
- 本地构建结果
- 系统环境信息

---

## 🎉 成功示例

### 📱 成功构建的标志

1. **GitHub Actions显示绿色✅**
2. **Release页面出现新版本**
3. **APK文件可以下载**
4. **文件大小合理（50-100MB）**
5. **校验和文件齐全**

### 📈 发布成功后的下一步

1. **分享下载链接**
2. **更新项目文档**
3. **发布版本公告**
4. **收集用户反馈**
5. **规划下一版本**

---

## 🚀 立即开始

### 快速启动命令

```bash
# 一键发布命令序列
git add .
git commit -m "Release v1.2.0"
git push origin main
git tag -a v1.2.0 -m "Release v1.2.0"
git push origin v1.2.0

# 然后访问：https://github.com/[您的用户名]/Operit/actions
```

### 🎯 预期结果

执行上述命令后，您将在10-15分钟内获得：
- ✅ 自动构建的APK文件
- ✅ GitHub Release页面
- ✅ 用户可下载的安装包
- ✅ 完整的发布说明

**祝您发布成功！** 🎉

---

*更新时间: 2024年12月6日*
*适用版本: Operit AI v1.2.0+*