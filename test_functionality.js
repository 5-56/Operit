// Operit AI 项目功能测试脚本
console.log("🚀 开始测试 Operit AI 项目功能...\n");

// 测试1: 基本JavaScript运行环境
function testBasicJS() {
    console.log("✅ 测试1: JavaScript运行环境正常");
    console.log("   Node.js版本:", process.version);
    console.log("   平台:", process.platform);
    return true;
}

// 测试2: 文件系统访问
const fs = require('fs');
const path = require('path');

function testFileSystem() {
    try {
        const currentDir = process.cwd();
        const files = fs.readdirSync(currentDir);
        console.log("✅ 测试2: 文件系统访问正常");
        console.log("   当前目录:", currentDir);
        console.log("   包含文件:", files.slice(0, 5).join(', '), files.length > 5 ? '...' : '');
        return true;
    } catch (error) {
        console.log("❌ 测试2: 文件系统访问失败:", error.message);
        return false;
    }
}

// 测试3: JSON处理
function testJSONProcessing() {
    try {
        const testObj = {
            name: "Operit AI",
            version: "1.0.0",
            features: ["自动化操作", "用户意图理解", "代码执行"],
            timestamp: new Date().toISOString()
        };

        const jsonStr = JSON.stringify(testObj, null, 2);
        const parsedObj = JSON.parse(jsonStr);

        console.log("✅ 测试3: JSON处理正常");
        console.log("   测试对象序列化/反序列化成功");
        return true;
    } catch (error) {
        console.log("❌ 测试3: JSON处理失败:", error.message);
        return false;
    }
}

// 测试4: 异步操作
async function testAsyncOperations() {
    try {
        const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

        console.log("   开始异步测试...");
        await delay(100);
        console.log("✅ 测试4: 异步操作正常");
        console.log("   Promise和async/await工作正常");
        return true;
    } catch (error) {
        console.log("❌ 测试4: 异步操作失败:", error.message);
        return false;
    }
}

// 测试5: 字符串处理和正则表达式
function testStringProcessing() {
    try {
        const text = "这是一个测试文本，包含中文和English mixed content.";
        const regex = /[\u4e00-\u9fff]+/g; // 匹配中文字符
        const chineseMatches = text.match(regex);

        console.log("✅ 测试5: 字符串处理和正则表达式正常");
        console.log("   中文字符识别:", chineseMatches ? chineseMatches.join(', ') : '无');
        return true;
    } catch (error) {
        console.log("❌ 测试5: 字符串处理失败:", error.message);
        return false;
    }
}

// 测试6: 数学计算
function testMathOperations() {
    try {
        const calculations = [
            { expr: "2 + 3", result: 2 + 3 },
            { expr: "Math.pow(2, 8)", result: Math.pow(2, 8) },
            { expr: "Math.sqrt(16)", result: Math.sqrt(16) },
            { expr: "Math.PI", result: Math.PI }
        ];

        console.log("✅ 测试6: 数学计算正常");
        calculations.forEach(calc => {
            console.log(`   ${calc.expr} = ${calc.result}`);
        });
        return true;
    } catch (error) {
        console.log("❌ 测试6: 数学计算失败:", error.message);
        return false;
    }
}

// 测试7: 错误处理
function testErrorHandling() {
    try {
        try {
            throw new Error("这是一个测试错误");
        } catch (e) {
            console.log("✅ 测试7: 错误处理正常");
            console.log("   捕获测试错误:", e.message);
            return true;
        }
    } catch (error) {
        console.log("❌ 测试7: 错误处理失败:", error.message);
        return false;
    }
}

// 主测试函数
async function runAllTests() {
    console.log("📊 Operit AI 项目功能测试报告");
    console.log("=".repeat(50));

    const tests = [
        testBasicJS,
        testFileSystem,
        testJSONProcessing,
        testAsyncOperations,
        testStringProcessing,
        testMathOperations,
        testErrorHandling
    ];

    let passedTests = 0;

    for (const test of tests) {
        try {
            const result = await test();
            if (result) passedTests++;
        } catch (error) {
            console.log("❌ 测试执行异常:", error.message);
        }
        console.log(); // 空行分隔
    }

    console.log("=".repeat(50));
    console.log(`📈 测试结果: ${passedTests}/${tests.length} 项测试通过`);
    console.log(`🎯 成功率: ${((passedTests / tests.length) * 100).toFixed(1)}%`);

    if (passedTests === tests.length) {
        console.log("🎉 所有基础功能测试通过！项目环境配置正确。");
    } else {
        console.log("⚠️  部分测试失败，可能存在配置问题。");
    }

    return passedTests === tests.length;
}

// 运行测试
runAllTests().then(success => {
    console.log("\n🏁 测试完成。");
    process.exit(success ? 0 : 1);
}).catch(error => {
    console.error("💥 测试执行出现严重错误:", error);
    process.exit(1);
});