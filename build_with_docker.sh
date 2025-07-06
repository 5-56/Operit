#!/bin/bash

# Operit AI Docker构建脚本
# 使用Docker容器构建APK，无需本地Android SDK

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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
DOCKER_IMAGE="openjdk:17-jdk-slim"

log_info "🐳 使用 Docker 构建 $PROJECT_NAME APK"
log_info "📋 版本: $VERSION_NAME"
log_info "🔧 Docker 镜像: $DOCKER_IMAGE"

# 检查Docker是否可用
if ! command -v docker &> /dev/null; then
    log_error "❌ Docker 未安装或不可用"
    log_info "💡 请先安装 Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

# 检查Docker服务
if ! docker info &> /dev/null; then
    log_error "❌ Docker 服务未启动"
    log_info "💡 请启动 Docker 服务"
    exit 1
fi

log_success "✅ Docker 环境检查通过"

# 创建Dockerfile
log_info "📝 创建 Dockerfile..."
cat > Dockerfile << 'EOF'
FROM openjdk:17-jdk-slim

# 安装必要的包
RUN apt-get update && \
    apt-get install -y wget unzip curl && \
    rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 设置环境变量
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin

# 下载和安装Android SDK
RUN mkdir -p $ANDROID_HOME && \
    cd $ANDROID_HOME && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-8512546_latest.zip && \
    unzip -q commandlinetools-linux-8512546_latest.zip && \
    rm commandlinetools-linux-8512546_latest.zip && \
    mkdir -p cmdline-tools/latest && \
    mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true

# 接受SDK许可并安装必要的包
RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 复制项目文件
COPY . .

# 给gradlew执行权限
RUN chmod +x gradlew

# 构建APK
CMD ["./gradlew", "assembleRelease"]
EOF

# 构建Docker镜像
log_info "🔨 构建 Docker 镜像..."
docker build -t operit-ai-builder .

if [ $? -eq 0 ]; then
    log_success "✅ Docker 镜像构建成功"
else
    log_error "❌ Docker 镜像构建失败"
    exit 1
fi

# 运行容器构建APK
log_info "🚀 在 Docker 容器中构建 APK..."
docker run --rm -v $(pwd)/app/build:/app/app/build operit-ai-builder

if [ $? -eq 0 ]; then
    log_success "✅ APK 构建成功"
else
    log_error "❌ APK 构建失败"
    exit 1
fi

# 检查构建结果
RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
if [ -f "$RELEASE_APK" ]; then
    log_success "✅ 发布版 APK 构建成功"
    APK_SIZE=$(ls -lh "$RELEASE_APK" | awk '{print $5}')
    log_info "📦 APK 大小: $APK_SIZE"
    
    # 创建发布目录
    mkdir -p release
    cp "$RELEASE_APK" "release/Operit-AI-v${VERSION_NAME}.apk"
    
    # 生成校验和
    cd release
    sha256sum "Operit-AI-v${VERSION_NAME}.apk" > "Operit-AI-v${VERSION_NAME}.apk.sha256"
    md5sum "Operit-AI-v${VERSION_NAME}.apk" > "Operit-AI-v${VERSION_NAME}.apk.md5"
    cd ..
    
    log_success "🎉 APK 已复制到 release/ 目录"
else
    log_error "❌ 未找到发布版 APK"
    exit 1
fi

# 清理Docker镜像
log_info "🧹 清理 Docker 资源..."
docker rmi operit-ai-builder
rm -f Dockerfile

log_success "✨ Docker 构建完成！"
log_info "📦 发布文件位于: release/Operit-AI-v${VERSION_NAME}.apk"