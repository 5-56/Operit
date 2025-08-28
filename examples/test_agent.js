/**
 * Agent功能测试脚本
 * 
 * 这个脚本演示如何通过JavaScript调用Agent功能
 */

function testAgentBasicFunction() {
    console.log("=== 测试Agent基本功能 ===");
    
    // 测试1：简单的文件操作任务
    console.log("测试1: 文件操作任务");
    const fileTask = toolCall("execute_agent_task", {
        request: "帮我创建一个测试文件夹，并在其中创建一个hello.txt文件，内容为'Hello Agent!'"
    });
    console.log("文件操作结果:", fileTask);
    
    // 测试2：系统信息收集
    console.log("\n测试2: 系统信息收集");
    const systemTask = toolCall("smart_script_execution", {
        task: "收集当前设备的基本信息，包括存储空间和时间",
        context: "这是一个系统监控测试"
    });
    console.log("系统信息结果:", systemTask);
    
    // 测试3：获取当前计划
    console.log("\n测试3: 获取当前执行计划");
    const currentPlan = toolCall("get_current_agent_plan", {});
    console.log("当前计划:", currentPlan);
    
    // 测试4：获取执行历史
    console.log("\n测试4: 获取执行历史");
    const history = toolCall("get_agent_history", {
        limit: 5
    });
    console.log("执行历史:", history);
    
    console.log("=== Agent功能测试完成 ===");
}

function testAgentAdvancedFunction() {
    console.log("=== 测试Agent高级功能 ===");
    
    // 测试复杂的多步骤任务
    const complexTask = toolCall("execute_agent_task", {
        request: `
        执行以下复杂任务：
        1. 创建一个名为'agent_test'的文件夹
        2. 在文件夹中创建3个文件：info.txt, data.json, config.xml
        3. 向info.txt写入当前时间和设备信息
        4. 向data.json写入一个包含测试数据的JSON对象
        5. 向config.xml写入一个简单的XML配置
        6. 最后列出文件夹中的所有文件并返回结果
        `
    });
    
    console.log("复杂任务执行结果:", complexTask);
    console.log("=== Agent高级功能测试完成 ===");
}

function testAgentErrorHandling() {
    console.log("=== 测试Agent错误处理 ===");
    
    try {
        // 故意制造一个可能失败的任务
        const errorTask = toolCall("execute_agent_task", {
            request: "尝试访问一个不存在的网络地址并处理错误"
        });
        console.log("错误处理结果:", errorTask);
    } catch (error) {
        console.log("捕获到错误:", error.message);
    }
    
    console.log("=== Agent错误处理测试完成 ===");
}

// 主测试函数
function main() {
    console.log("开始Agent功能全面测试...\n");
    
    try {
        // 基本功能测试
        testAgentBasicFunction();
        
        // 等待一下，避免请求过于频繁
        console.log("\n等待2秒后继续下一个测试...");
        
        // 高级功能测试
        testAgentAdvancedFunction();
        
        // 错误处理测试
        testAgentErrorHandling();
        
        console.log("\n所有测试完成！");
        
        // 返回测试总结
        complete({
            success: true,
            message: "Agent功能测试全部完成",
            summary: {
                basicTests: "已完成基本功能测试",
                advancedTests: "已完成高级功能测试", 
                errorTests: "已完成错误处理测试",
                timestamp: new Date().toISOString()
            }
        });
        
    } catch (error) {
        console.error("测试过程中发生错误:", error);
        complete({
            success: false,
            message: "测试失败: " + error.message,
            error: error.toString()
        });
    }
}

// 导出主函数
exports.main = main;

// 如果直接运行，执行测试
if (typeof window === 'undefined' && typeof global !== 'undefined') {
    main();
}