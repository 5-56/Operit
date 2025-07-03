#!/bin/bash

# Operit AI - Android SDK自动安装配置脚本
# 适用于Linux环境

set -e

echo "🚀 开始安装和配置Android SDK..."

# 定义变量
ANDROID_HOME="${HOME}/android-sdk"
CMDLINE_TOOLS_VERSION="9477386"
PLATFORM_VERSION="34"
BUILD_TOOLS_VERSION="34.0.0"

# 创建Android SDK目录
echo "📁 创建Android SDK目录..."
mkdir -p ${ANDROID_HOME}
cd ${ANDROID_HOME}

# 下载Android Command Line Tools
echo "⬇️ 下载Android Command Line Tools..."
if [ ! -f "commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" ]; then
    wget "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
fi

# 解压Command Line Tools
echo "📦 解压Command Line Tools..."
if [ ! -d "cmdline-tools" ]; then
    unzip -q "commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
    mkdir -p cmdline-tools/latest
    mv cmdline-tools/bin cmdline-tools/lib cmdline-tools/NOTICE.txt cmdline-tools/source.properties cmdline-tools/latest/
fi

# 设置环境变量
echo "🔧 配置环境变量..."
export ANDROID_HOME=${ANDROID_HOME}
export PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

# 接受许可证
echo "📜 接受Android SDK许可证..."
yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses

# 安装必要的SDK组件
echo "📱 安装Android SDK组件..."
${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager "platform-tools"
${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager "platforms;android-${PLATFORM_VERSION}"
${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager "build-tools;${BUILD_TOOLS_VERSION}"

# 创建local.properties文件
echo "📋 创建local.properties文件..."
cd /workspace
echo "sdk.dir=${ANDROID_HOME}" > local.properties

# 添加环境变量到shell配置文件
echo "🔧 更新shell环境配置..."
{
    echo ""
    echo "# Android SDK配置 (由Operit AI安装脚本添加)"
    echo "export ANDROID_HOME=${ANDROID_HOME}"
    echo "export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools"
} >> ~/.bashrc

# 验证安装
echo "✅ 验证安装..."
echo "Android SDK位置: ${ANDROID_HOME}"
echo "local.properties已创建"

# 测试编译
echo "🔨 测试Android项目编译..."
cd /workspace
if ./gradlew assembleDebug; then
    echo "✅ Android项目编译成功！"
else
    echo "❌ 编译失败，请检查错误信息"
    exit 1
fi

echo ""
echo "🎉 Android SDK安装配置完成！"
echo ""
echo "📌 重要信息："
echo "  - Android SDK安装路径: ${ANDROID_HOME}"
echo "  - 已创建local.properties文件"
echo "  - 已更新~/.bashrc环境变量"
echo "  - 请运行 'source ~/.bashrc' 或重新打开终端来加载环境变量"
echo ""
echo "🚀 现在可以正常编译和运行Operit AI项目了！"