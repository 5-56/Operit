# 🚀 Operit AI Agent 部署指南

## 📋 概述

本指南将帮助您快速部署和启动Operit AI Agent系统。通过本指南，您将能够在本周内完成所有必要的配置并开始使用AI Agent功能。

## ✅ 前置检查

在开始之前，请确保您已经：

1. **Android开发环境就绪**
   - Android Studio 已安装
   - Android SDK 已配置
   - 设备已连接或模拟器已启动

2. **项目文件完整性**
   - 所有核心AI Agent文件已创建
   - MainActivity和FloatingChatService已集成
   - 权限配置工具已就位

3. **运行环境检查**
   ```bash
   # 验证项目完整性
   ./this_week_implementation.sh
   ```

## 📱 快速部署步骤

### 步骤1: 编译应用

```bash
# 清理并编译Debug版本
./gradlew clean assembleDebug

# 如果遇到编译错误，查看详细日志
./gradlew assembleDebug --info --stacktrace
```

### 步骤2: 安装到设备

```bash
# 安装到连接的设备
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 检查安装状态
adb shell pm list packages | grep operit
```

### 步骤3: 配置权限

1. **打开应用**
   ```bash
   adb shell am start -n com.ai.assistance.operit/.ui.main.MainActivity
   ```

2. **进入权限配置界面**
   - 应用启动后会自动检查权限状态
   - 如果权限未配置，会显示权限配置向导

3. **授予无障碍服务权限**
   - 点击"配置权限"或"去设置"
   - 在无障碍设置中找到"Operit AI"
   - 开启无障碍服务开关
   - 确认授权对话框

4. **授予悬浮窗权限**
   - 在应用权限设置中找到"显示在其他应用的上层"
   - 开启悬浮窗权限开关

### 步骤4: 验证安装

```bash
# 运行基础功能测试
./gradlew connectedAndroidTest

# 检查AI Agent状态
adb logcat -s "OperitAIAgent*" "MainActivity" "AIAgentPermissionHelper"
```

## 🧪 测试验证

### 基础功能测试

1. **权限状态检查**
   ```bash
   # 检查无障碍服务状态
   adb shell settings get secure enabled_accessibility_services
   
   # 检查悬浮窗权限状态
   adb shell appops get com.ai.assistance.operit SYSTEM_ALERT_WINDOW
   ```

2. **AI Agent初始化测试**
   - 打开应用主界面
   - 查看日志确认AI Agent成功初始化
   - 验证权限配置界面显示正常

3. **核心功能测试**
   - 测试屏幕感知功能
   - 测试简单操作执行
   - 验证状态管理正常

### 日志监控

```bash
# 启动日志监控（在单独终端中运行）
adb logcat -v time -s "OperitAIAgent*" "MainActivity" "FloatingChatService" "AIAgentPermissionHelper"

# 过滤特定功能日志
adb logcat | grep -E "(AI Agent|权限|屏幕感知|操作执行)"
```

## 🎯 功能验证清单

- [ ] **应用启动正常**
  - 应用能正常启动
  - 主界面显示完整
  - 无崩溃或严重错误

- [ ] **AI Agent初始化**
  - AI Agent控制器成功初始化
  - 所有核心组件加载完成
  - 状态管理正常工作

- [ ] **权限配置**
  - 权限配置界面功能正常
  - 能正确检测权限状态
  - 一键跳转设置功能工作

- [ ] **屏幕感知**
  - 能获取UI结构信息
  - 元素识别准确
  - 无障碍服务响应正常

- [ ] **操作执行**
  - 基础指令能正常执行
  - 操作反馈及时
  - 错误处理机制有效

## ⚠️ 常见问题排查

### 编译错误

**问题**: Gradle编译失败
```bash
# 解决方案
./gradlew clean
./gradlew assembleDebug --refresh-dependencies
```

**问题**: 依赖项冲突
```bash
# 查看依赖树
./gradlew :app:dependencies
```

### 权限问题

**问题**: 无障碍服务无法开启
- 检查设备是否支持无障碍服务
- 尝试重新安装应用
- 检查应用签名是否正确

**问题**: 悬浮窗权限被拒绝
- 检查设备MIUI/EMUI等定制系统设置
- 手动在系统设置中授予权限
- 检查勿扰模式设置

### 运行时错误

**问题**: AI Agent初始化失败
```bash
# 查看详细错误日志
adb logcat -s "OperitAIAgentController"
```

**问题**: 屏幕感知功能异常
- 确认无障碍服务正常运行
- 检查UI结构获取是否成功
- 验证权限配置正确性

## 🔧 性能优化建议

### 内存管理
- 定期清理无用的AccessibilityNodeInfo
- 及时回收Bitmap资源
- 监控内存使用情况

### 响应速度
- 优化屏幕感知频率
- 合理设置操作延迟
- 使用异步处理长时间操作

### 稳定性提升
- 增强异常处理机制
- 实现自动重试逻辑
- 添加状态恢复功能

## 📊 监控和诊断

### 性能监控
```bash
# CPU使用率监控
adb shell top -p $(adb shell pidof com.ai.assistance.operit)

# 内存使用监控
adb shell dumpsys meminfo com.ai.assistance.operit

# 网络使用监控
adb shell dumpsys netstats detail
```

### 诊断工具
```bash
# 生成性能报告
./gradlew generateDebugUnitTestCoverage

# 分析APK大小
./gradlew analyzeDebugBundle

# 检查安全性
./gradlew lintDebug
```

## 🚀 生产环境部署

### Release构建
```bash
# 构建Release版本
./gradlew assembleRelease

# 生成签名APK
./gradlew bundleRelease
```

### 发布前检查
- [ ] 移除所有调试日志
- [ ] 启用代码混淆
- [ ] 压缩资源文件
- [ ] 验证签名配置
- [ ] 测试所有核心功能

## 📞 技术支持

### 问题报告
如遇到问题，请提供以下信息：
1. 设备型号和Android版本
2. 应用版本和构建时间
3. 完整的错误日志
4. 重现步骤描述

### 调试信息收集
```bash
# 收集完整日志
adb logcat -v time > operit_debug.log

# 生成bug报告
adb bugreport operit_bug_report.zip

# 应用状态快照
adb shell dumpsys activity com.ai.assistance.operit
```

## 🎉 成功标志

当您看到以下状态时，表示AI Agent部署成功：

✅ **应用正常启动，无崩溃**  
✅ **权限配置完成，状态显示正常**  
✅ **AI Agent初始化成功，日志显示"✅ AI Agent控制器初始化完成"**  
✅ **屏幕感知功能正常，能获取UI结构**  
✅ **操作执行功能正常，测试指令执行成功**  
✅ **状态管理正常，状态切换流畅**  

## 📈 下一步计划

部署成功后，您可以：

1. **体验基础功能**
   - 尝试简单的AI指令
   - 测试屏幕操作自动化
   - 验证悬浮窗交互

2. **自定义配置**
   - 调整AI响应参数
   - 配置操作权限范围
   - 设置个性化偏好

3. **功能扩展**
   - 添加自定义AI指令
   - 集成第三方AI服务
   - 开发专业应用场景

---

🎊 **恭喜！您已成功部署Operit AI Agent系统！**

现在您拥有了一个功能完整的移动AI Agent，可以开始探索AI驱动的自动化操作能力。如有任何问题，请查看技术支持部分或查阅详细的API文档。