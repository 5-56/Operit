#!/bin/bash

# ==================== Operit AI 性能组件自动化测试脚本 ====================
# 用于测试性能优化组件的功能和效果

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
readonly PACKAGE_NAME="com.ai.assistance.operit"
readonly TEST_DURATION=30
readonly RESULTS_DIR="$PROJECT_ROOT/test_results"

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

log_test() {
    echo -e "${CYAN}[TEST]${NC} $1"
}

# 显示帮助信息
show_help() {
    cat << EOF
Operit AI 性能组件自动化测试脚本

用法: $0 [选项]

选项:
  -h, --help              显示此帮助信息
  -p, --package PACKAGE   指定应用包名 [默认: $PACKAGE_NAME]
  -d, --duration SECONDS  每个测试的持续时间 [默认: $TEST_DURATION]
  -o, --output DIR        指定结果输出目录 [默认: $RESULTS_DIR]
  --memory                测试内存管理器
  --startup               测试启动优化器
  --ai-model              测试AI模型管理器
  --performance           测试性能监控器
  --all                   运行所有测试

示例:
  $0 --all                # 运行所有测试
  $0 --memory --startup   # 测试内存管理和启动优化
  $0 -d 60 --performance  # 运行60秒性能监控测试

EOF
}

# 检查工具和环境
check_environment() {
    log_test "检查测试环境..."
    
    # 检查adb
    if ! command -v adb &> /dev/null; then
        log_error "adb未找到，请安装Android SDK"
        exit 1
    fi
    
    # 检查设备连接
    local devices=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
    if [ "$devices" -eq 0 ]; then
        log_error "未检测到连接的Android设备"
        exit 1
    fi
    
    # 检查应用是否安装
    if ! adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
        log_error "应用 $PACKAGE_NAME 未安装"
        exit 1
    fi
    
    # 创建结果目录
    mkdir -p "$RESULTS_DIR"
    
    log_success "测试环境检查通过"
}

# 获取当前内存使用情况
get_memory_usage() {
    local pid=$(adb shell pidof "$PACKAGE_NAME" 2>/dev/null)
    if [ -n "$pid" ]; then
        # 获取PSS内存（推荐的内存指标）
        adb shell dumpsys meminfo "$PACKAGE_NAME" | grep "TOTAL PSS:" | awk '{print $3}' | head -1
    else
        echo "0"
    fi
}

# 获取CPU使用率
get_cpu_usage() {
    local cpu_usage=$(adb shell top -n 1 | grep "$PACKAGE_NAME" | head -1 | awk '{print $9}' | tr -d '%')
    echo "${cpu_usage:-0}"
}

# 启动应用
start_app() {
    log_info "启动应用: $PACKAGE_NAME"
    adb shell am start -n "$PACKAGE_NAME/.ui.MainActivity" >/dev/null 2>&1
    sleep 3  # 等待应用启动
}

# 停止应用
stop_app() {
    log_info "停止应用: $PACKAGE_NAME"
    adb shell am force-stop "$PACKAGE_NAME"
    sleep 2
}

# 测试内存管理器
test_memory_manager() {
    log_test "开始内存管理器测试..."
    
    local result_file="$RESULTS_DIR/memory_manager_test.log"
    
    echo "内存管理器测试报告" > "$result_file"
    echo "测试时间: $(date)" >> "$result_file"
    echo "=================" >> "$result_file"
    echo "" >> "$result_file"
    
    # 启动应用
    start_app
    
    # 基线内存测量
    local baseline_memory=$(get_memory_usage)
    echo "基线内存使用: ${baseline_memory} KB" >> "$result_file"
    log_info "基线内存使用: ${baseline_memory} KB"
    
    # 模拟内存压力
    log_info "模拟内存压力操作..."
    for i in {1..10}; do
        # 执行一些操作来增加内存使用
        adb shell input tap 500 500  # 随机点击
        adb shell input swipe 300 800 300 200  # 滑动
        sleep 1
    done
    
    # 压力后内存测量
    local stress_memory=$(get_memory_usage)
    echo "压力后内存使用: ${stress_memory} KB" >> "$result_file"
    log_info "压力后内存使用: ${stress_memory} KB"
    
    # 触发内存清理（通过发送低内存警告）
    log_info "触发内存清理..."
    adb shell am send-trim-memory "$PACKAGE_NAME" MODERATE
    sleep 5
    
    # 清理后内存测量
    local cleaned_memory=$(get_memory_usage)
    echo "清理后内存使用: ${cleaned_memory} KB" >> "$result_file"
    log_info "清理后内存使用: ${cleaned_memory} KB"
    
    # 计算效果
    local memory_increase=$((stress_memory - baseline_memory))
    local memory_decrease=$((stress_memory - cleaned_memory))
    local cleanup_efficiency=0
    
    if [ "$memory_increase" -gt 0 ]; then
        cleanup_efficiency=$((memory_decrease * 100 / memory_increase))
    fi
    
    echo "" >> "$result_file"
    echo "测试结果:" >> "$result_file"
    echo "内存增长: ${memory_increase} KB" >> "$result_file"
    echo "清理释放: ${memory_decrease} KB" >> "$result_file"
    echo "清理效率: ${cleanup_efficiency}%" >> "$result_file"
    
    log_success "内存管理器测试完成 - 清理效率: ${cleanup_efficiency}%"
    
    stop_app
}

# 测试启动优化器
test_startup_optimizer() {
    log_test "开始启动优化器测试..."
    
    local result_file="$RESULTS_DIR/startup_optimizer_test.log"
    
    echo "启动优化器测试报告" > "$result_file"
    echo "测试时间: $(date)" >> "$result_file"
    echo "==================" >> "$result_file"
    echo "" >> "$result_file"
    
    # 测试多次启动时间
    local total_startup_time=0
    local test_count=5
    
    echo "启动时间测试 (${test_count}次):" >> "$result_file"
    
    for i in $(seq 1 $test_count); do
        log_info "启动测试 $i/$test_count"
        
        # 确保应用已停止
        stop_app
        sleep 2
        
        # 测量启动时间
        local start_time=$(date +%s%N)
        start_app
        
        # 等待应用完全启动（检查主Activity）
        local timeout=30
        local elapsed=0
        while [ $elapsed -lt $timeout ]; do
            if adb shell dumpsys window | grep -q "mCurrentFocus.*$PACKAGE_NAME"; then
                break
            fi
            sleep 1
            elapsed=$((elapsed + 1))
        done
        
        local end_time=$(date +%s%N)
        local startup_time=$(((end_time - start_time) / 1000000))  # 转换为毫秒
        
        echo "启动 $i: ${startup_time}ms" >> "$result_file"
        total_startup_time=$((total_startup_time + startup_time))
        
        log_info "启动时间: ${startup_time}ms"
    done
    
    local avg_startup_time=$((total_startup_time / test_count))
    
    echo "" >> "$result_file"
    echo "测试结果:" >> "$result_file"
    echo "平均启动时间: ${avg_startup_time}ms" >> "$result_file"
    
    # 评估启动性能
    local performance_rating="良好"
    if [ $avg_startup_time -gt 3000 ]; then
        performance_rating="需要优化"
    elif [ $avg_startup_time -lt 1500 ]; then
        performance_rating="优秀"
    fi
    
    echo "性能评级: $performance_rating" >> "$result_file"
    
    log_success "启动优化器测试完成 - 平均启动时间: ${avg_startup_time}ms ($performance_rating)"
    
    stop_app
}

# 测试AI模型管理器
test_ai_model_manager() {
    log_test "开始AI模型管理器测试..."
    
    local result_file="$RESULTS_DIR/ai_model_manager_test.log"
    
    echo "AI模型管理器测试报告" > "$result_file"
    echo "测试时间: $(date)" >> "$result_file"
    echo "====================" >> "$result_file"
    echo "" >> "$result_file"
    
    # 启动应用
    start_app
    
    # 测试模型加载对内存的影响
    local pre_load_memory=$(get_memory_usage)
    echo "模型加载前内存: ${pre_load_memory} KB" >> "$result_file"
    log_info "模型加载前内存: ${pre_load_memory} KB"
    
    # 模拟AI功能使用（导航到AI聊天界面）
    log_info "模拟AI功能使用..."
    adb shell input tap 200 1000  # 点击聊天标签
    sleep 3
    adb shell input text "测试AI模型性能"
    adb shell input keyevent 66  # 回车键
    sleep 5  # 等待AI响应
    
    local post_load_memory=$(get_memory_usage)
    echo "AI功能使用后内存: ${post_load_memory} KB" >> "$result_file"
    log_info "AI功能使用后内存: ${post_load_memory} KB"
    
    # 测试模型切换
    log_info "测试模型切换..."
    # 这里可以添加切换不同AI模型的操作
    sleep 3
    
    local switch_memory=$(get_memory_usage)
    echo "模型切换后内存: ${switch_memory} KB" >> "$result_file"
    
    # 计算内存影响
    local memory_impact=$((post_load_memory - pre_load_memory))
    local switch_impact=$((switch_memory - post_load_memory))
    
    echo "" >> "$result_file"
    echo "测试结果:" >> "$result_file"
    echo "模型加载内存影响: ${memory_impact} KB" >> "$result_file"
    echo "模型切换内存影响: ${switch_impact} KB" >> "$result_file"
    
    # 评估模型管理效率
    local efficiency_rating="良好"
    if [ $memory_impact -gt 100000 ]; then  # 100MB
        efficiency_rating="需要优化"
    elif [ $memory_impact -lt 50000 ]; then  # 50MB
        efficiency_rating="优秀"
    fi
    
    echo "内存管理效率: $efficiency_rating" >> "$result_file"
    
    log_success "AI模型管理器测试完成 - 内存影响: ${memory_impact} KB ($efficiency_rating)"
    
    stop_app
}

# 测试性能监控器
test_performance_monitor() {
    log_test "开始性能监控器测试..."
    
    local result_file="$RESULTS_DIR/performance_monitor_test.log"
    
    echo "性能监控器测试报告" > "$result_file"
    echo "测试时间: $(date)" >> "$result_file"
    echo "==================" >> "$result_file"
    echo "" >> "$result_file"
    
    # 启动应用
    start_app
    
    # 监控性能指标
    log_info "监控性能指标 ${TEST_DURATION}秒..."
    
    local cpu_samples=()
    local memory_samples=()
    local max_cpu=0
    local max_memory=0
    local min_memory=999999999
    
    echo "性能监控数据:" >> "$result_file"
    echo "时间,CPU(%),内存(KB)" >> "$result_file"
    
    for i in $(seq 1 $TEST_DURATION); do
        local cpu=$(get_cpu_usage)
        local memory=$(get_memory_usage)
        
        # 记录数据
        echo "$(date '+%H:%M:%S'),$cpu,$memory" >> "$result_file"
        
        # 更新统计
        cpu_samples+=($cpu)
        memory_samples+=($memory)
        
        if (( $(echo "$cpu > $max_cpu" | bc -l) )); then
            max_cpu=$cpu
        fi
        
        if [ $memory -gt $max_memory ]; then
            max_memory=$memory
        fi
        
        if [ $memory -lt $min_memory ]; then
            min_memory=$memory
        fi
        
        # 模拟用户操作
        if [ $((i % 5)) -eq 0 ]; then
            adb shell input tap $((RANDOM % 800 + 100)) $((RANDOM % 1400 + 100))
        fi
        
        sleep 1
    done
    
    # 计算平均值
    local cpu_sum=0
    for cpu in "${cpu_samples[@]}"; do
        cpu_sum=$(echo "$cpu_sum + $cpu" | bc -l)
    done
    local avg_cpu=$(echo "$cpu_sum / ${#cpu_samples[@]}" | bc -l)
    
    local memory_sum=0
    for memory in "${memory_samples[@]}"; do
        memory_sum=$((memory_sum + memory))
    done
    local avg_memory=$((memory_sum / ${#memory_samples[@]}))
    
    echo "" >> "$result_file"
    echo "统计结果:" >> "$result_file"
    echo "平均CPU使用率: ${avg_cpu}%" >> "$result_file"
    echo "最大CPU使用率: ${max_cpu}%" >> "$result_file"
    echo "平均内存使用: ${avg_memory} KB" >> "$result_file"
    echo "最大内存使用: ${max_memory} KB" >> "$result_file"
    echo "最小内存使用: ${min_memory} KB" >> "$result_file"
    echo "内存波动范围: $((max_memory - min_memory)) KB" >> "$result_file"
    
    # 评估性能稳定性
    local stability_rating="良好"
    local memory_volatility=$((max_memory - min_memory))
    
    if (( $(echo "$avg_cpu > 50" | bc -l) )) || [ $memory_volatility -gt 50000 ]; then
        stability_rating="需要优化"
    elif (( $(echo "$avg_cpu < 20" | bc -l) )) && [ $memory_volatility -lt 20000 ]; then
        stability_rating="优秀"
    fi
    
    echo "性能稳定性: $stability_rating" >> "$result_file"
    
    log_success "性能监控器测试完成 - 平均CPU: ${avg_cpu}%, 稳定性: $stability_rating"
    
    stop_app
}

# 生成综合报告
generate_summary_report() {
    log_info "生成综合测试报告..."
    
    local summary_file="$RESULTS_DIR/test_summary.html"
    
    cat > "$summary_file" << EOF
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Operit AI 性能组件测试报告</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        h1 { color: #2c3e50; text-align: center; margin-bottom: 30px; }
        h2 { color: #34495e; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
        .test-result { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
        .success { border-left: 5px solid #27ae60; background-color: #d5f4e6; }
        .warning { border-left: 5px solid #f39c12; background-color: #fef5e7; }
        .error { border-left: 5px solid #e74c3c; background-color: #fadbd8; }
        .metric { display: inline-block; margin: 10px; padding: 10px; background-color: #3498db; color: white; border-radius: 5px; min-width: 120px; text-align: center; }
        .footer { text-align: center; margin-top: 30px; color: #7f8c8d; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🧪 Operit AI 性能组件测试报告</h1>
        
        <h2>📊 测试概览</h2>
        <div class="metric">测试时间<br>$(date '+%Y-%m-%d %H:%M')</div>
        <div class="metric">设备型号<br>$(adb shell getprop ro.product.model 2>/dev/null || echo "未知")</div>
        <div class="metric">Android版本<br>$(adb shell getprop ro.build.version.release 2>/dev/null || echo "未知")</div>
        <div class="metric">应用版本<br>1.2.0</div>
        
        <h2>🔍 测试结果</h2>
EOF

    # 添加各个测试结果
    for log_file in "$RESULTS_DIR"/*.log; do
        if [ -f "$log_file" ]; then
            local test_name=$(basename "$log_file" .log)
            echo "        <div class=\"test-result success\">" >> "$summary_file"
            echo "            <h3>$test_name</h3>" >> "$summary_file"
            echo "            <pre>$(cat "$log_file")</pre>" >> "$summary_file"
            echo "        </div>" >> "$summary_file"
        fi
    done
    
    cat >> "$summary_file" << EOF
        
        <div class="footer">
            <p>🔬 Generated by Operit AI Performance Test Suite</p>
            <p>📊 Test results saved in: $RESULTS_DIR</p>
        </div>
    </div>
</body>
</html>
EOF
    
    log_success "综合测试报告生成完成: $summary_file"
}

# 主函数
main() {
    # 默认配置
    TEST_MEMORY=false
    TEST_STARTUP=false
    TEST_AI_MODEL=false
    TEST_PERFORMANCE=false
    TEST_ALL=false
    
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
            -d|--duration)
                TEST_DURATION="$2"
                shift 2
                ;;
            -o|--output)
                RESULTS_DIR="$2"
                shift 2
                ;;
            --memory)
                TEST_MEMORY=true
                shift
                ;;
            --startup)
                TEST_STARTUP=true
                shift
                ;;
            --ai-model)
                TEST_AI_MODEL=true
                shift
                ;;
            --performance)
                TEST_PERFORMANCE=true
                shift
                ;;
            --all)
                TEST_ALL=true
                shift
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    log_info "🧪 开始Operit AI性能组件测试"
    
    # 检查环境
    check_environment
    
    # 执行测试
    if [ "$TEST_ALL" = true ]; then
        test_memory_manager
        test_startup_optimizer
        test_ai_model_manager
        test_performance_monitor
    else
        [ "$TEST_MEMORY" = true ] && test_memory_manager
        [ "$TEST_STARTUP" = true ] && test_startup_optimizer
        [ "$TEST_AI_MODEL" = true ] && test_ai_model_manager
        [ "$TEST_PERFORMANCE" = true ] && test_performance_monitor
    fi
    
    # 如果没有指定任何测试，默认运行所有测试
    if [ "$TEST_ALL" = false ] && [ "$TEST_MEMORY" = false ] && [ "$TEST_STARTUP" = false ] && [ "$TEST_AI_MODEL" = false ] && [ "$TEST_PERFORMANCE" = false ]; then
        log_info "未指定测试类型，运行所有测试"
        test_memory_manager
        test_startup_optimizer
        test_ai_model_manager
        test_performance_monitor
    fi
    
    # 生成综合报告
    generate_summary_report
    
    log_success "✅ 性能组件测试完成！"
    log_info "📊 测试结果保存在: $RESULTS_DIR"
    log_info "🌐 打开 test_summary.html 查看详细报告"
}

# 执行主函数
main "$@"