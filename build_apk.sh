#!/bin/bash

# Operit AI APK构建脚本
# 作者: AI Assistant
# 版本: 1.0.0
# 日期: 2024-12-06

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 项目信息
PROJECT_NAME="Operit AI"
VERSION_NAME="1.2.0"
APK_NAME="Operit-AI-v${VERSION_NAME}.apk"

log_info "🚀 开始构建 $PROJECT_NAME APK"
log_info "📋 版本: $VERSION_NAME"
log_info "⏰ 时间: $(date)"

# 1. 环境检查
log_info "🔍 检查构建环境..."

# 检查是否在项目根目录
if [ ! -f "gradlew" ]; then
    log_error "gradlew 文件不存在，请确保在项目根目录执行此脚本"
    exit 1
fi

if [ ! -f "build.gradle.kts" ]; then
    log_error "build.gradle.kts 文件不存在，请确保在项目根目录执行此脚本"
    exit 1
fi

# 检查Java版本
if ! command -v java &> /dev/null; then
    log_error "Java 未安装或未配置到 PATH 中"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 11 ]; then
    log_error "Java 版本过低，需要 Java 11 或更高版本"
    exit 1
fi

log_success "✅ Java 环境检查通过 (版本: $(java -version 2>&1 | head -n1))"

# 检查Android SDK
if [ -z "$ANDROID_HOME" ]; then
    log_warning "⚠️ ANDROID_HOME 环境变量未设置，尝试自动检测..."
    # 尝试常见的Android SDK路径
    POSSIBLE_PATHS=(
        "$HOME/Android/Sdk"
        "$HOME/android-sdk"
        "$HOME/Library/Android/sdk"
        "/usr/local/android-sdk"
    )
    
    for path in "${POSSIBLE_PATHS[@]}"; do
        if [ -d "$path" ]; then
            export ANDROID_HOME="$path"
            log_success "✅ 检测到 Android SDK: $ANDROID_HOME"
            break
        fi
    done
    
    if [ -z "$ANDROID_HOME" ]; then
        log_error "❌ 未找到 Android SDK，请设置 ANDROID_HOME 环境变量"
        exit 1
    fi
fi

# 2. 权限检查
log_info "🔐 检查脚本权限..."
if [ ! -x "./gradlew" ]; then
    log_info "📝 添加 gradlew 执行权限..."
    chmod +x ./gradlew
fi

# 3. 依赖检查
log_info "📦 检查项目依赖..."

# 检查Google Drive依赖是否下载
DRIVE_DEPS_URL="https://drive.google.com/drive/folders/1g-Q_i7cf6Ua4KX9ZM6V282EEZvTVVfF7"
KEEP_FILES=$(find . -name ".keep" -type f | wc -l)

if [ "$KEEP_FILES" -gt 0 ]; then
    log_warning "⚠️ 检测到 .keep 文件，可能需要下载额外依赖"
    log_info "🔗 依赖下载地址: $DRIVE_DEPS_URL"
    log_info "📁 请将下载的文件放入有 .keep 文件的目录中"
    
    # 检查是否有空的 .keep 目录
    EMPTY_KEEP_DIRS=$(find . -name ".keep" -type f -exec dirname {} \; | while read dir; do
        if [ $(find "$dir" -type f ! -name ".keep" | wc -l) -eq 0 ]; then
            echo "$dir"
        fi
    done | wc -l)
    
    if [ "$EMPTY_KEEP_DIRS" -gt 0 ]; then
        log_warning "⚠️ 发现空的依赖目录，可能影响构建"
        read -p "是否继续构建？(y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "🛑 用户取消构建"
            exit 1
        fi
    fi
fi

# 4. 清理项目
log_info "🧹 清理项目..."
./gradlew clean --quiet

if [ $? -eq 0 ]; then
    log_success "✅ 项目清理完成"
else
    log_error "❌ 项目清理失败"
    exit 1
fi

# 5. 构建项目
log_info "🔨 开始构建项目..."
log_info "📝 构建日志将保存到 build.log"

# 首先尝试构建调试版本以验证项目完整性
log_info "🔍 构建调试版本进行验证..."
./gradlew assembleDebug --stacktrace > build.log 2>&1

if [ $? -eq 0 ]; then
    log_success "✅ 调试版本构建成功"
else
    log_error "❌ 调试版本构建失败，请检查 build.log"
    tail -20 build.log
    exit 1
fi

# 构建发布版本
log_info "🚀 构建发布版本..."
./gradlew assembleRelease --stacktrace >> build.log 2>&1

if [ $? -eq 0 ]; then
    log_success "✅ 发布版本构建成功"
else
    log_error "❌ 发布版本构建失败，请检查 build.log"
    tail -20 build.log
    exit 1
fi

# 6. 检查构建结果
log_info "🔍 检查构建结果..."

RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"

if [ -f "$RELEASE_APK" ]; then
    log_success "✅ 发布版 APK 构建成功"
    APK_SIZE=$(ls -lh "$RELEASE_APK" | awk '{print $5}')
    log_info "📦 APK 大小: $APK_SIZE"
else
    log_error "❌ 未找到发布版 APK: $RELEASE_APK"
    exit 1
fi

# 7. 创建发布目录
log_info "📁 创建发布目录..."
mkdir -p release

# 复制APK到发布目录
cp "$RELEASE_APK" "release/$APK_NAME"

if [ -f "$DEBUG_APK" ]; then
    cp "$DEBUG_APK" "release/Operit-AI-v${VERSION_NAME}-debug.apk"
    log_success "✅ 调试版 APK 已复制到发布目录"
fi

# 8. 生成APK信息
log_info "📋 生成APK信息..."

cat > "release/README.md" << EOF
# Operit AI v${VERSION_NAME} 发布包

## 📦 包含文件
- **${APK_NAME}** - 发布版 APK (推荐安装)
- **Operit-AI-v${VERSION_NAME}-debug.apk** - 调试版 APK

## 📱 系统要求
- Android 8.0 (API 26) 或更高版本
- RAM: 推荐 4GB 或更多
- 存储空间: 至少 500MB 可用空间
- 架构: 支持 ARM64、ARM32

## 🚀 安装说明
1. 下载 ${APK_NAME}
2. 在设备上启用"未知来源"应用安装
3. 运行APK文件进行安装
4. 按照应用内指引完成初始设置

## 🔧 权限需求
- 存储权限: 读写文件
- 网络权限: API调用和数据同步
- 麦克风权限: 语音识别功能
- 系统权限: 高级功能需要 Shizuku 或 Root

## 📞 支持
- 项目主页: https://github.com/AAswordman/Operit
- 问题报告: https://github.com/AAswordman/Operit/issues
- 邮箱: aaswordsman@foxmail.com

---
*构建时间: $(date)*
*构建版本: ${VERSION_NAME}*
EOF

# 9. 生成校验和
log_info "🔐 生成文件校验和..."
cd release
for file in *.apk; do
    if [ -f "$file" ]; then
        sha256sum "$file" > "${file}.sha256"
        md5sum "$file" > "${file}.md5"
    fi
done
cd ..

# 10. 完成构建
log_success "🎉 构建完成！"
log_info "📋 构建摘要:"
log_info "   • 项目名称: $PROJECT_NAME"
log_info "   • 版本: $VERSION_NAME"
log_info "   • 发布APK: release/$APK_NAME"
log_info "   • APK大小: $APK_SIZE"
log_info "   • 构建时间: $(date)"

# 11. 可选：自动打开发布目录
if command -v xdg-open &> /dev/null; then
    log_info "📂 打开发布目录..."
    xdg-open release/
elif command -v open &> /dev/null; then
    log_info "📂 打开发布目录..."
    open release/
fi

# 12. 提供下一步建议
log_info "💡 下一步建议:"
log_info "   1. 测试 APK 在目标设备上的安装和运行"
log_info "   2. 检查核心功能是否正常工作"
log_info "   3. 创建 GitHub Release 并上传 APK"
log_info "   4. 更新项目文档和版本说明"

echo ""
log_success "✨ Operit AI APK 构建成功完成！"
echo ""