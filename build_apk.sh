#!/bin/bash

# ==================== Operit AI APK 优化构建脚本 ====================
# 作者: AI Assistant
# 版本: 2.0.0
# 日期: 2024-12-06
# 新增功能: 代码质量检查、性能分析、多线程构建、增量构建

set -e  # 遇到错误立即退出
set -u  # 使用未定义变量时退出
set -o pipefail  # 管道命令失败时退出

# ==================== 环境配置 ====================

# 颜色定义
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly PURPLE='\033[0;35m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m' # No Color

# 构建配置
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="${SCRIPT_DIR}"
readonly BUILD_DIR="${PROJECT_ROOT}/build"
readonly OUTPUT_DIR="${BUILD_DIR}/outputs"
readonly LOGS_DIR="${BUILD_DIR}/logs"

# 默认配置
BUILD_TYPE="release"
ENABLE_OPTIMIZATION=true
ENABLE_CODE_QUALITY_CHECK=true
ENABLE_PERFORMANCE_ANALYSIS=false
SKIP_TESTS=false
PARALLEL_BUILD=true
CLEAN_BUILD=false
VERBOSE=false

# 创建必要目录
mkdir -p "${LOGS_DIR}"

# ==================== 日志和工具函数 ====================

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" | tee -a "${LOGS_DIR}/build.log"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "${LOGS_DIR}/build.log"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "${LOGS_DIR}/build.log"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "${LOGS_DIR}/build.log"
}

log_debug() {
    if [ "$VERBOSE" = true ]; then
        echo -e "${PURPLE}[DEBUG]${NC} $1" | tee -a "${LOGS_DIR}/build.log"
    fi
}

log_step() {
    echo -e "${CYAN}[STEP]${NC} $1" | tee -a "${LOGS_DIR}/build.log"
}

# 时间计算函数
get_timestamp() {
    date +%s
}

calculate_duration() {
    local start_time=$1
    local end_time=$2
    echo "$((end_time - start_time))"
}

format_duration() {
    local duration=$1
    local minutes=$((duration / 60))
    local seconds=$((duration % 60))
    printf "%02d:%02d" "$minutes" "$seconds"
}

# 显示帮助信息
show_help() {
    cat << EOF
Operit AI APK 优化构建脚本 v2.0.0

用法: $0 [选项]

选项:
  -h, --help              显示此帮助信息
  -v, --verbose           启用详细输出
  -t, --type TYPE         构建类型 (debug|release) [默认: release]
  -c, --clean             清理构建，重新构建所有内容
  -q, --quality           启用代码质量检查
  -p, --performance       启用性能分析
  -s, --skip-tests        跳过测试
  --no-parallel           禁用并行构建
  --no-optimization       禁用构建优化

示例:
  $0                      # 标准发布构建
  $0 -t debug -v          # 详细输出的调试构建
  $0 -c -q -p            # 清理构建 + 质量检查 + 性能分析
  $0 --skip-tests         # 跳过测试的快速构建

EOF
}

# 参数解析
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -v|--verbose)
                VERBOSE=true
                ;;
            -t|--type)
                BUILD_TYPE="$2"
                shift
                ;;
            -c|--clean)
                CLEAN_BUILD=true
                ;;
            -q|--quality)
                ENABLE_CODE_QUALITY_CHECK=true
                ;;
            -p|--performance)
                ENABLE_PERFORMANCE_ANALYSIS=true
                ;;
            -s|--skip-tests)
                SKIP_TESTS=true
                ;;
            --no-parallel)
                PARALLEL_BUILD=false
                ;;
            --no-optimization)
                ENABLE_OPTIMIZATION=false
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
        shift
    done
}

# 验证构建类型
validate_build_type() {
    case $BUILD_TYPE in
        debug|release)
            ;;
        *)
            log_error "无效的构建类型: $BUILD_TYPE. 必须是 'debug' 或 'release'"
            exit 1
            ;;
    esac
}

# ==================== 主要执行逻辑 ====================

# 解析命令行参数
parse_arguments "$@"
validate_build_type

# 项目信息
readonly PROJECT_NAME="Operit AI"
readonly VERSION_NAME="1.2.0"
readonly APK_NAME="Operit-AI-v${VERSION_NAME}-${BUILD_TYPE}.apk"

# 记录构建开始时间
BUILD_START_TIME=$(get_timestamp)

# 清理日志文件
> "${LOGS_DIR}/build.log"

log_step "🚀 开始构建 $PROJECT_NAME APK"
log_info "📋 版本: $VERSION_NAME"
log_info "🔧 构建类型: $BUILD_TYPE"
log_info "⏰ 开始时间: $(date)"
log_info "📁 项目目录: $PROJECT_ROOT"

log_debug "构建配置:"
log_debug "  - 构建类型: $BUILD_TYPE"
log_debug "  - 启用优化: $ENABLE_OPTIMIZATION"
log_debug "  - 代码质量检查: $ENABLE_CODE_QUALITY_CHECK"
log_debug "  - 性能分析: $ENABLE_PERFORMANCE_ANALYSIS"
log_debug "  - 跳过测试: $SKIP_TESTS"
log_debug "  - 并行构建: $PARALLEL_BUILD"
log_debug "  - 清理构建: $CLEAN_BUILD"

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
if [ "$CLEAN_BUILD" = true ]; then
    log_step "🧹 清理项目..."
    local clean_start=$(get_timestamp)
    
    if [ "$VERBOSE" = true ]; then
        ./gradlew clean --stacktrace
    else
        ./gradlew clean --quiet
    fi
    
    if [ $? -eq 0 ]; then
        local clean_end=$(get_timestamp)
        local clean_duration=$(calculate_duration "$clean_start" "$clean_end")
        log_success "✅ 项目清理完成 (耗时: $(format_duration "$clean_duration"))"
    else
        log_error "❌ 项目清理失败"
        exit 1
    fi
else
    log_info "⏭️ 跳过清理，使用增量构建"
fi

# 4.1. 代码质量检查
if [ "$ENABLE_CODE_QUALITY_CHECK" = true ]; then
    log_step "🔍 运行代码质量检查..."
    local quality_start=$(get_timestamp)
    
    log_info "🔍 运行 Detekt 静态代码分析..."
    if [ "$VERBOSE" = true ]; then
        ./gradlew detekt
    else
        ./gradlew detekt --quiet
    fi
    
    if [ $? -ne 0 ]; then
        log_warning "⚠️ Detekt 检查发现问题，请查看报告"
    fi
    
    log_info "🔍 运行 Ktlint 代码格式检查..."
    if [ "$VERBOSE" = true ]; then
        ./gradlew ktlintCheck
    else
        ./gradlew ktlintCheck --quiet
    fi
    
    if [ $? -ne 0 ]; then
        log_warning "⚠️ Ktlint 检查发现格式问题，建议运行 ./gradlew ktlintFormat 修复"
    fi
    
    local quality_end=$(get_timestamp)
    local quality_duration=$(calculate_duration "$quality_start" "$quality_end")
    log_success "✅ 代码质量检查完成 (耗时: $(format_duration "$quality_duration"))"
fi

# 4.2. 运行测试
if [ "$SKIP_TESTS" = false ]; then
    log_step "🧪 运行测试..."
    local test_start=$(get_timestamp)
    
    if [ "$VERBOSE" = true ]; then
        ./gradlew test --stacktrace
    else
        ./gradlew test --quiet
    fi
    
    if [ $? -eq 0 ]; then
        local test_end=$(get_timestamp)
        local test_duration=$(calculate_duration "$test_start" "$test_end")
        log_success "✅ 测试通过 (耗时: $(format_duration "$test_duration"))"
        
        # 生成测试覆盖率报告
        log_info "📊 生成测试覆盖率报告..."
        ./gradlew jacocoTestReport --quiet
    else
        log_error "❌ 测试失败"
        exit 1
    fi
else
    log_info "⏭️ 跳过测试"
fi

# 5. 构建项目
log_step "🔨 开始构建项目..."
local build_start=$(get_timestamp)

# 准备Gradle参数
GRADLE_OPTS=""
if [ "$PARALLEL_BUILD" = true ]; then
    GRADLE_OPTS="$GRADLE_OPTS --parallel"
fi

if [ "$ENABLE_OPTIMIZATION" = true ]; then
    GRADLE_OPTS="$GRADLE_OPTS --build-cache"
fi

if [ "$VERBOSE" = true ]; then
    GRADLE_OPTS="$GRADLE_OPTS --stacktrace --info"
else
    GRADLE_OPTS="$GRADLE_OPTS --quiet"
fi

log_debug "Gradle选项: $GRADLE_OPTS"

# 构建指定类型的APK
if [ "$BUILD_TYPE" = "debug" ]; then
    log_info "🔍 构建调试版本..."
    BUILD_TASK="assembleDebug"
else
    log_info "🚀 构建发布版本..."
    BUILD_TASK="assembleRelease"
fi

# 执行构建
log_info "� 构建日志将保存到 ${LOGS_DIR}/gradle.log"

if [ "$VERBOSE" = true ]; then
    ./gradlew $BUILD_TASK $GRADLE_OPTS | tee "${LOGS_DIR}/gradle.log"
    BUILD_RESULT=$?
else
    ./gradlew $BUILD_TASK $GRADLE_OPTS > "${LOGS_DIR}/gradle.log" 2>&1
    BUILD_RESULT=$?
fi

if [ $BUILD_RESULT -eq 0 ]; then
    local build_end=$(get_timestamp)
    local build_duration=$(calculate_duration "$build_start" "$build_end")
    log_success "✅ $BUILD_TYPE 版本构建成功 (耗时: $(format_duration "$build_duration"))"
else
    log_error "❌ $BUILD_TYPE 版本构建失败，请检查 ${LOGS_DIR}/gradle.log"
    tail -20 "${LOGS_DIR}/gradle.log"
    exit 1
fi

# 性能分析
if [ "$ENABLE_PERFORMANCE_ANALYSIS" = true ]; then
    log_step "📊 生成性能分析报告..."
    ./gradlew generatePerformanceReport --quiet || log_warning "⚠️ 性能报告生成失败"
fi

# 6. 检查构建结果
log_step "🔍 检查构建结果..."

TARGET_APK="app/build/outputs/apk/${BUILD_TYPE}/app-${BUILD_TYPE}.apk"

if [ -f "$TARGET_APK" ]; then
    log_success "✅ $BUILD_TYPE 版本 APK 构建成功"
    APK_SIZE=$(ls -lh "$TARGET_APK" | awk '{print $5}')
    APK_SIZE_BYTES=$(ls -l "$TARGET_APK" | awk '{print $5}')
    log_info "📦 APK 大小: $APK_SIZE ($APK_SIZE_BYTES bytes)"
    
    # 分析APK内容
    log_debug "分析APK内容..."
    if command -v unzip &> /dev/null; then
        unzip -l "$TARGET_APK" | head -10 | log_debug || true
    fi
else
    log_error "❌ 未找到 $BUILD_TYPE 版本 APK: $TARGET_APK"
    log_error "可用的APK文件:"
    find app/build/outputs/apk -name "*.apk" 2>/dev/null || log_error "没有找到任何APK文件"
    exit 1
fi

# 7. 创建发布目录
log_info "📁 创建发布目录..."
mkdir -p release

# 复制APK到发布目录
cp "$TARGET_APK" "release/$APK_NAME"
log_success "✅ $BUILD_TYPE 版本 APK 已复制到发布目录"

# 如果是发布构建，也尝试复制调试版本（如果存在）
if [ "$BUILD_TYPE" = "release" ]; then
    DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$DEBUG_APK" ]; then
        cp "$DEBUG_APK" "release/Operit-AI-v${VERSION_NAME}-debug.apk"
        log_success "✅ 调试版 APK 也已复制到发布目录"
    fi
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
BUILD_END_TIME=$(get_timestamp)
TOTAL_DURATION=$(calculate_duration "$BUILD_START_TIME" "$BUILD_END_TIME")

log_step "🎉 构建完成！"
log_info "📋 构建摘要:"
log_info "   • 项目名称: $PROJECT_NAME"
log_info "   • 版本: $VERSION_NAME"
log_info "   • 构建类型: $BUILD_TYPE"
log_info "   • 输出APK: release/$APK_NAME"
log_info "   • APK大小: $APK_SIZE"
log_info "   • 总耗时: $(format_duration "$TOTAL_DURATION")"
log_info "   • 完成时间: $(date)"

# 构建性能统计
log_info "📊 性能统计:"
log_info "   • 并行构建: $PARALLEL_BUILD"
log_info "   • 增量构建: $([ "$CLEAN_BUILD" = false ] && echo "是" || echo "否")"
log_info "   • 代码检查: $ENABLE_CODE_QUALITY_CHECK"
log_info "   • 性能分析: $ENABLE_PERFORMANCE_ANALYSIS"

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