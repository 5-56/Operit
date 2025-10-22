/*
METADATA
{
    "name": "agent",
    "description": "多轮对话智能体，自动理解用户需求，规划并调用现有工具，自动执行、收集结果、分析反馈、自动优化（重试、参数微调、换用不同工具），支持多轮对话。",
    "category": "AGENT"
}
*/

// 引入现有工具
const codeRunner = require('./code_runner');
const superAdmin = require('./super_admin');
const reader = require('./reader');
const dailyLife = require('./daily_life');

// 工具注册表，便于动态切换
const TOOL_REGISTRY = {
    code_runner: codeRunner,
    super_admin: superAdmin,
    reader: reader,
    daily_life: dailyLife
};

// 工具能力描述（可扩展）
const TOOL_CAPABILITIES = [
    { name: 'code_runner', desc: '多语言代码执行', types: ['js', 'python', 'ruby', 'go', 'rust'] },
    { name: 'super_admin', desc: '终端命令、Shell操作', types: ['shell', 'terminal'] },
    { name: 'reader', desc: '代码搜索、提取', types: ['search', 'extract'] },
    { name: 'daily_life', desc: '日常生活、设备操作', types: ['date', 'reminder', 'alarm', 'device', 'weather'] }
];

// 多轮对话上下文
let conversationHistory = [];

// 智能体主流程
async function main(params) {
    const user_input = params && params.user_input ? params.user_input : '';
    if (!user_input) {
        complete('请提供用户需求（user_input）');
        return;
    }
    
    conversationHistory.push({ role: 'user', content: user_input });
    let tryCount = 0;
    const maxTries = 5;
    let lastResult = null;
    let lastTool = null;
    let lastParams = null;
    let lastError = null;
    let planLog = [];

    // 1. 需求理解与工具规划
    let plan = await planFromInput(user_input);
    planLog.push({ step: 'plan', plan });

    while (tryCount < maxTries) {
        tryCount++;
        // 2. 工具选择与参数生成
        const { toolName, toolFunc, toolParams, toolDesc } = await selectToolAndParams(plan, lastResult, lastError, tryCount);
        lastTool = toolName;
        lastParams = toolParams;
        planLog.push({ step: `try_${tryCount}`, tool: toolName, params: toolParams });

        // 3. 执行工具
        let result = await safeCall(toolFunc, toolParams);
        lastResult = result;
        planLog.push({ step: `result_${tryCount}`, result });

        // 4. 结果评价
        if (isSuccess(result)) {
            conversationHistory.push({ role: 'agent', content: `成功：${JSON.stringify(result)}` });
            complete({
                success: true,
                message: '任务完成',
                planLog,
                result,
                conversationHistory
            });
            return;
        } else {
            // 5. 自动优化：参数微调/换工具/重试
            lastError = result && result.error ? result.error : '未知错误';
            planLog.push({ step: `fail_${tryCount}`, error: lastError });
            plan = await optimizePlan(plan, lastResult, lastError, tryCount);
            conversationHistory.push({ role: 'agent', content: `失败：${lastError}` });
        }
    }
    complete({
        success: false,
        message: '多次尝试后仍未完成任务',
        planLog,
        lastResult,
        conversationHistory
    });
}

// 需求理解与初步规划
async function planFromInput(user_input) {
    // 简单关键词映射，可扩展为LLM调用
    const lower = user_input.toLowerCase();
    if (lower.includes('python') || lower.includes('代码') || lower.includes('运行')) {
        return { tool: 'code_runner', func: 'run_python', params: { script: extractCode(user_input, 'python') } };
    }
    if (lower.includes('shell') || lower.includes('终端') || lower.includes('命令')) {
        return { tool: 'super_admin', func: 'terminal', params: { command: extractCommand(user_input) } };
    }
    if (lower.includes('查找函数') || lower.includes('提取函数')) {
        return { tool: 'reader', func: 'extract_functions', params: { folder_path: extractPath(user_input) } };
    }
    if (lower.includes('提醒') || lower.includes('闹钟')) {
        return { tool: 'daily_life', func: 'set_reminder', params: { title: user_input } };
    }
    // 默认用code_runner执行js
    return { tool: 'code_runner', func: 'run_javascript_es5', params: { script: extractCode(user_input, 'js') } };
}

// 工具选择与参数生成（支持自动优化/换工具/参数微调）
async function selectToolAndParams(plan, lastResult, lastError, tryCount) {
    let toolName = plan.tool;
    let funcName = plan.func;
    let toolParams = plan.params;
    // 自动优化策略
    if (tryCount > 1) {
        // 1. 参数微调
        toolParams = tweakParams(toolParams, tryCount);
        // 2. 换用不同工具
        if (tryCount === 3 && toolName === 'code_runner') {
            toolName = 'super_admin';
            funcName = 'terminal';
            toolParams = { command: toolParams.script || 'echo hello' };
        }
        if (tryCount === 4 && toolName === 'super_admin') {
            toolName = 'reader';
            funcName = 'search_code_in_folder';
            toolParams = { folder_path: '.', pattern: 'function' };
        }
    }
    const tool = TOOL_REGISTRY[toolName];
    const toolFunc = tool && tool[funcName] ? tool[funcName] : async () => ({ error: '工具未找到' });
    return { toolName, toolFunc, toolParams, toolDesc: TOOL_CAPABILITIES.find(t => t.name === toolName)?.desc };
}

// 结果评价
function isSuccess(result) {
    if (!result) return false;
    if (typeof result === 'object' && result.success === false) return false;
    if (typeof result === 'object' && result.error) return false;
    return true;
}

// 自动优化：参数微调/换工具/重试
async function optimizePlan(plan, lastResult, lastError, tryCount) {
    // 可扩展为LLM自动优化
    // 这里只做简单参数微调和工具切换
    return plan;
}

// 工具安全调用
async function safeCall(func, params) {
    try {
        return await func(params);
    } catch (e) {
        return { error: e.message };
    }
}

// 参数微调示例
function tweakParams(params, tryCount) {
    if (!params) return params;
    // 简单示例：增加重试标记
    params._retry = tryCount;
    return params;
}

// 从用户输入中提取代码片段（可扩展为LLM）
function extractCode(input, lang) {
    // 简单正则提取
    const match = input.match(/```[a-zA-Z]*\n([\s\S]*?)```/);
    if (match) return match[1];
    // fallback
    return input;
}

// 从用户输入中提取命令
function extractCommand(input) {
    // 简单正则提取
    const match = input.match(/\b(?:运行|执行|shell|命令)[:：]?\s*([\w\W]+)/);
    if (match) return match[1];
    return input;
}

// 从用户输入中提取路径
function extractPath(input) {
    const match = input.match(/([\/\w\.-]+)$/);
    if (match) return match[1];
    return '.';
}

// 导出主函数
exports.main = main;