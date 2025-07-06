#!/bin/bash

# ==================== Operit AI 性能分析脚本 ====================
# 用于分析应用性能，生成详细的性能报告

set -e

# 颜色定义
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly PURPLE='\033[0;35m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m' # No Color

# 配置
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
readonly REPORTS_DIR="$PROJECT_ROOT/reports"
# PACKAGE_NAME will be set in main function

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

log_step() {
    echo -e "${CYAN}[STEP]${NC} $1"
}

# 显示帮助信息
show_help() {
    cat << EOF
Operit AI 性能分析脚本

用法: $0 [选项]

选项:
  -h, --help              显示此帮助信息
  -p, --package PACKAGE   指定应用包名 [默认: com.ai.assistance.operit]
  -o, --output DIR        指定输出目录 [默认: $REPORTS_DIR]
  -d, --duration SECONDS  监控持续时间 [默认: 60]
  -m, --memory            内存分析
  -c, --cpu               CPU分析
  -g, --gpu               GPU分析
  -n, --network           网络分析
  -a, --all               全面分析
  --apk PATH              分析APK文件

示例:
  $0 -a                   # 全面性能分析
  $0 -m -c               # 内存和CPU分析
  $0 --apk app.apk       # 分析APK文件
  $0 -d 120 -a           # 2分钟全面分析

EOF
}

# 检查工具可用性
check_tools() {
    log_step "检查分析工具..."
    
    local missing_tools=()
    
    # 检查必需工具
    if ! command -v adb &> /dev/null; then
        missing_tools+=("adb")
    fi
    
    if ! command -v aapt &> /dev/null && ! command -v aapt2 &> /dev/null; then
        missing_tools+=("aapt/aapt2")
    fi
    
    if [ ${#missing_tools[@]} -gt 0 ]; then
        log_error "缺少必需工具: ${missing_tools[*]}"
        log_info "请安装Android SDK并配置PATH环境变量"
        exit 1
    fi
    
    log_success "分析工具检查通过"
}

# 检查设备连接
check_device() {
    log_step "检查设备连接..."
    
    local devices=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
    
    if [ "$devices" -eq 0 ]; then
        log_error "未检测到连接的Android设备"
        log_info "请确保设备已连接并启用USB调试"
        exit 1
    elif [ "$devices" -gt 1 ]; then
        log_warning "检测到多个设备，将使用第一个设备"
    fi
    
    local device_info=$(adb shell getprop ro.product.model)
    log_success "设备连接正常: $device_info"
}

# 检查应用是否安装
check_app_installed() {
    log_step "检查应用安装状态..."
    
    if ! adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
        log_error "应用 $PACKAGE_NAME 未安装"
        log_info "请先安装应用或使用 --apk 选项分析APK文件"
        exit 1
    fi
    
    log_success "应用已安装"
}

# 创建报告目录
create_report_dir() {
    local timestamp=$(date +"%Y%m%d_%H%M%S")
    REPORT_DIR="$REPORTS_DIR/performance_$timestamp"
    
    mkdir -p "$REPORT_DIR"
    log_info "报告将保存到: $REPORT_DIR"
}

# 内存分析
analyze_memory() {
    log_step "开始内存分析..."
    
    local memory_file="$REPORT_DIR/memory_analysis.txt"
    
    cat > "$memory_file" << EOF
Operit AI 内存分析报告
生成时间: $(date)
设备: $(adb shell getprop ro.product.model)
Android版本: $(adb shell getprop ro.build.version.release)

==================================================

EOF
    
    # 获取应用PID
    local pid=$(adb shell pidof "$PACKAGE_NAME")
    if [ -z "$pid" ]; then
        log_warning "应用未运行，启动应用进行分析"
        adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1
        sleep 3
        pid=$(adb shell pidof "$PACKAGE_NAME")
    fi
    
    if [ -n "$pid" ]; then
        # 内存使用情况
        echo "1. 内存使用概览" >> "$memory_file"
        echo "-------------------" >> "$memory_file"
        adb shell dumpsys meminfo "$PACKAGE_NAME" >> "$memory_file"
        echo "" >> "$memory_file"
        
        # GC信息
        echo "2. GC统计信息" >> "$memory_file"
        echo "-------------------" >> "$memory_file"
        adb shell dumpsys gfxinfo "$PACKAGE_NAME" >> "$memory_file"
        echo "" >> "$memory_file"
        
        # 内存映射
        echo "3. 内存映射" >> "$memory_file"
        echo "-------------------" >> "$memory_file"
        adb shell cat "/proc/$pid/smaps" >> "$memory_file" 2>/dev/null || echo "无法获取内存映射信息" >> "$memory_file"
        
        log_success "内存分析完成: $memory_file"
    else
        log_error "无法获取应用进程ID"
    fi
}

# CPU分析
analyze_cpu() {
    log_step "开始CPU分析..."
    
    local cpu_file="$REPORT_DIR/cpu_analysis.txt"
    local duration=${DURATION:-60}
    
    cat > "$cpu_file" << EOF
Operit AI CPU分析报告
生成时间: $(date)
监控时长: ${duration}秒

==================================================

EOF
    
    # CPU使用率监控
    echo "1. CPU使用率监控" >> "$cpu_file"
    echo "-------------------" >> "$cpu_file"
    
    log_info "监控CPU使用率 ${duration}秒..."
    for i in $(seq 1 "$duration"); do
        local cpu_usage=$(adb shell top -n 1 | grep "$PACKAGE_NAME" | head -1 | awk '{print $9}')
        if [ -n "$cpu_usage" ]; then
            echo "$(date '+%H:%M:%S') CPU: ${cpu_usage}%" >> "$cpu_file"
        fi
        sleep 1
    done
    
    echo "" >> "$cpu_file"
    
    # 线程信息
    echo "2. 线程信息" >> "$cpu_file"
    echo "-------------------" >> "$cpu_file"
    local pid=$(adb shell pidof "$PACKAGE_NAME")
    if [ -n "$pid" ]; then
        adb shell cat "/proc/$pid/status" >> "$cpu_file" 2>/dev/null || echo "无法获取线程信息" >> "$cpu_file"
    fi
    
    log_success "CPU分析完成: $cpu_file"
}

# GPU分析
analyze_gpu() {
    log_step "开始GPU分析..."
    
    local gpu_file="$REPORT_DIR/gpu_analysis.txt"
    
    cat > "$gpu_file" << EOF
Operit AI GPU分析报告
生成时间: $(date)

==================================================

EOF
    
    # GPU渲染信息
    echo "1. GPU渲染信息" >> "$gpu_file"
    echo "-------------------" >> "$gpu_file"
    adb shell dumpsys SurfaceFlinger >> "$gpu_file"
    echo "" >> "$gpu_file"
    
    # OpenGL信息
    echo "2. OpenGL信息" >> "$gpu_file"
    echo "-------------------" >> "$gpu_file"
    adb shell dumpsys gfxinfo "$PACKAGE_NAME" framestats >> "$gpu_file"
    
    log_success "GPU分析完成: $gpu_file"
}

# 网络分析
analyze_network() {
    log_step "开始网络分析..."
    
    local network_file="$REPORT_DIR/network_analysis.txt"
    local duration=${DURATION:-60}
    
    cat > "$network_file" << EOF
Operit AI 网络分析报告
生成时间: $(date)
监控时长: ${duration}秒

==================================================

EOF
    
    # 获取应用UID
    local uid=$(adb shell dumpsys package "$PACKAGE_NAME" | grep "userId=" | head -1 | sed 's/.*userId=\([0-9]*\).*/\1/')
    
    if [ -n "$uid" ]; then
        echo "应用UID: $uid" >> "$network_file"
        echo "" >> "$network_file"
        
        # 网络统计信息
        echo "1. 网络流量统计" >> "$network_file"
        echo "-------------------" >> "$network_file"
        
        local start_rx=$(adb shell cat "/proc/net/xt_qtaguid/stats" | grep " $uid " | awk '{sum+=$6} END {print sum}')
        local start_tx=$(adb shell cat "/proc/net/xt_qtaguid/stats" | grep " $uid " | awk '{sum+=$8} END {print sum}')
        
        log_info "监控网络流量 ${duration}秒..."
        sleep "$duration"
        
        local end_rx=$(adb shell cat "/proc/net/xt_qtaguid/stats" | grep " $uid " | awk '{sum+=$6} END {print sum}')
        local end_tx=$(adb shell cat "/proc/net/xt_qtaguid/stats" | grep " $uid " | awk '{sum+=$8} END {print sum}')
        
        local rx_bytes=$((end_rx - start_rx))
        local tx_bytes=$((end_tx - start_tx))
        
        echo "接收字节数: $rx_bytes" >> "$network_file"
        echo "发送字节数: $tx_bytes" >> "$network_file"
        echo "总流量: $((rx_bytes + tx_bytes))" >> "$network_file"
        
    else
        echo "无法获取应用UID" >> "$network_file"
    fi
    
    log_success "网络分析完成: $network_file"
}

# APK分析
analyze_apk() {
    local apk_path="$1"
    log_step "开始APK分析: $apk_path"
    
    if [ ! -f "$apk_path" ]; then
        log_error "APK文件不存在: $apk_path"
        exit 1
    fi
    
    local apk_file="$REPORT_DIR/apk_analysis.txt"
    
    cat > "$apk_file" << EOF
Operit AI APK分析报告
APK文件: $apk_path
生成时间: $(date)

==================================================

EOF
    
    # APK基本信息
    echo "1. APK基本信息" >> "$apk_file"
    echo "-------------------" >> "$apk_file"
    
    if command -v aapt2 &> /dev/null; then
        aapt2 dump badging "$apk_path" >> "$apk_file" 2>/dev/null || aapt dump badging "$apk_path" >> "$apk_file"
    else
        aapt dump badging "$apk_path" >> "$apk_file"
    fi
    
    echo "" >> "$apk_file"
    
    # APK大小信息
    echo "2. APK大小分析" >> "$apk_file"
    echo "-------------------" >> "$apk_file"
    
    local apk_size=$(ls -lh "$apk_path" | awk '{print $5}')
    echo "APK总大小: $apk_size" >> "$apk_file"
    
    # 解压分析
    local temp_dir=$(mktemp -d)
    unzip -q "$apk_path" -d "$temp_dir"
    
    echo "内容分析:" >> "$apk_file"
    du -sh "$temp_dir"/* 2>/dev/null | sort -hr >> "$apk_file"
    
    # 清理临时目录
    rm -rf "$temp_dir"
    
    echo "" >> "$apk_file"
    
    # 权限分析
    echo "3. 权限分析" >> "$apk_file"
    echo "-------------------" >> "$apk_file"
    if command -v aapt2 &> /dev/null; then
        aapt2 dump permissions "$apk_path" >> "$apk_file" 2>/dev/null || aapt dump permissions "$apk_path" >> "$apk_file"
    else
        aapt dump permissions "$apk_path" >> "$apk_file"
    fi
    
    log_success "APK分析完成: $apk_file"
}

# 生成HTML报告
generate_html_report() {
    log_step "生成HTML报告..."
    
    local html_file="$REPORT_DIR/performance_report.html"
    
    cat > "$html_file" << EOF
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Operit AI 性能分析报告</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        h1 { color: #2c3e50; text-align: center; margin-bottom: 30px; }
        h2 { color: #34495e; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
        .summary { background-color: #ecf0f1; padding: 15px; border-radius: 5px; margin-bottom: 20px; }
        .metric { display: inline-block; margin: 10px; padding: 10px; background-color: #3498db; color: white; border-radius: 5px; min-width: 120px; text-align: center; }
        .chart-container { margin: 20px 0; padding: 20px; border: 1px solid #ddd; border-radius: 5px; }
        pre { background-color: #f8f9fa; padding: 15px; border-radius: 5px; overflow-x: auto; }
        .footer { text-align: center; margin-top: 30px; color: #7f8c8d; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 Operit AI 性能分析报告</h1>
        
        <div class="summary">
            <h2>📊 分析概览</h2>
            <div class="metric">生成时间<br>$(date '+%Y-%m-%d %H:%M')</div>
            <div class="metric">设备型号<br>$(adb shell getprop ro.product.model 2>/dev/null || echo "未知")</div>
            <div class="metric">Android版本<br>$(adb shell getprop ro.build.version.release 2>/dev/null || echo "未知")</div>
            <div class="metric">应用包名<br>$PACKAGE_NAME</div>
        </div>
        
        <h2>📈 分析结果</h2>
        <div class="chart-container">
            <p>详细的分析数据请查看以下文件：</p>
            <ul>
EOF

    # 添加生成的报告文件链接
    for file in "$REPORT_DIR"/*.txt; do
        if [ -f "$file" ]; then
            local filename=$(basename "$file")
            echo "                <li><a href=\"$filename\">$filename</a></li>" >> "$html_file"
        fi
    done
    
    cat >> "$html_file" << EOF
            </ul>
        </div>
        
        <div class="footer">
            <p>📱 Generated by Operit AI Performance Analyzer</p>
            <p>🔧 For more information, visit the project repository</p>
        </div>
    </div>
</body>
</html>
EOF
    
    log_success "HTML报告生成完成: $html_file"
}

# 主函数
main() {
    # 默认配置
    local PACKAGE_NAME_DEFAULT="com.ai.assistance.operit"
    PACKAGE_NAME="${PACKAGE_NAME:-$PACKAGE_NAME_DEFAULT}"
    DURATION=60
    DO_MEMORY=false
    DO_CPU=false
    DO_GPU=false
    DO_NETWORK=false
    DO_ALL=false
    APK_PATH=""
    
    # 参数解析
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -p|--package)
                PACKAGE_NAME="$2"
                shift 2
                ;;
            -o|--output)
                REPORTS_DIR="$2"
                shift 2
                ;;
            -d|--duration)
                DURATION="$2"
                shift 2
                ;;
            -m|--memory)
                DO_MEMORY=true
                shift
                ;;
            -c|--cpu)
                DO_CPU=true
                shift
                ;;
            -g|--gpu)
                DO_GPU=true
                shift
                ;;
            -n|--network)
                DO_NETWORK=true
                shift
                ;;
            -a|--all)
                DO_ALL=true
                shift
                ;;
            --apk)
                APK_PATH="$2"
                shift 2
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    log_step "🚀 开始Operit AI性能分析"
    
    # 检查工具
    check_tools
    
    # 创建报告目录
    create_report_dir
    
    # APK分析模式
    if [ -n "$APK_PATH" ]; then
        analyze_apk "$APK_PATH"
        generate_html_report
        log_success "✅ APK分析完成，报告保存在: $REPORT_DIR"
        exit 0
    fi
    
    # 设备分析模式
    check_device
    check_app_installed
    
    # 执行分析
    if [ "$DO_ALL" = true ]; then
        analyze_memory
        analyze_cpu
        analyze_gpu
        analyze_network
    else
        [ "$DO_MEMORY" = true ] && analyze_memory
        [ "$DO_CPU" = true ] && analyze_cpu
        [ "$DO_GPU" = true ] && analyze_gpu
        [ "$DO_NETWORK" = true ] && analyze_network
    fi
    
    # 如果没有指定任何分析类型，默认执行内存和CPU分析
    if [ "$DO_ALL" = false ] && [ "$DO_MEMORY" = false ] && [ "$DO_CPU" = false ] && [ "$DO_GPU" = false ] && [ "$DO_NETWORK" = false ]; then
        log_info "未指定分析类型，执行默认分析（内存+CPU）"
        analyze_memory
        analyze_cpu
    fi
    
    # 生成HTML报告
    generate_html_report
    
    log_success "✅ 性能分析完成！"
    log_info "📊 报告保存在: $REPORT_DIR"
    log_info "🌐 打开 performance_report.html 查看详细报告"
}

# 执行主函数
main "$@"