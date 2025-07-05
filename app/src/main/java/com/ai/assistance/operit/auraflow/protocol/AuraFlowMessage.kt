package com.ai.assistance.operit.auraflow.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * AuraFlow Agent 与 AI 大脑通信的消息类型枚举
 */
enum class MessageType {
    // Agent 发送给 AI 大脑的消息
    SCREEN_UPDATE,      // 屏幕信息更新（UI结构 + 截图）
    AGENT_STATUS,       // Agent 状态更新（电量、网络等）
    ACTION_RESULT,      // 操作执行结果反馈
    
    // AI 大脑发送给 Agent 的消息
    AI_COMMAND,         // AI 指令（点击、滑动、输入等）
    AI_FEEDBACK,        // AI 反馈（思考过程、状态更新）
    AI_QUESTION,        // AI 提问（需要用户确认或输入）
    
    // 双向消息
    HEARTBEAT,          // 心跳保活
    ERROR,              // 错误消息
    CONNECT,            // 连接建立
    DISCONNECT          // 连接断开
}

/**
 * AuraFlow 通信协议的基础消息结构
 */
@Serializable
data class AuraFlowMessage(
    val messageId: String,              // 消息唯一ID
    val type: MessageType,              // 消息类型
    val timestamp: Long,                // 时间戳
    val data: JsonObject? = null,       // 消息数据内容
    val replyTo: String? = null         // 回复的消息ID（可选）
)

/**
 * 屏幕更新消息数据
 */
@Serializable
data class ScreenUpdateData(
    val uiHierarchy: String,            // UI层次结构XML
    val screenshot: String? = null,     // Base64编码的屏幕截图（可选）
    val screenResolution: ScreenResolution,  // 屏幕分辨率信息
    val activeApp: String? = null,      // 当前活跃应用包名
    val screenOrientation: Int = 0      // 屏幕方向
)

/**
 * 屏幕分辨率信息
 */
@Serializable
data class ScreenResolution(
    val width: Int,
    val height: Int,
    val density: Float
)

/**
 * AI指令数据
 */
@Serializable
data class AICommandData(
    val action: ActionType,             // 操作类型
    val parameters: JsonObject          // 操作参数
)

/**
 * 操作类型枚举
 */
enum class ActionType {
    CLICK,              // 点击操作
    LONG_PRESS,         // 长按操作
    SWIPE,              // 滑动操作
    TYPE_TEXT,          // 文本输入
    PRESS_KEY,          // 按键操作
    SCROLL,             // 滚动操作
    GESTURE,            // 复杂手势
    WAIT,               // 等待操作
    TAKE_SCREENSHOT     // 截图操作
}

/**
 * 点击操作参数
 */
@Serializable
data class ClickParameters(
    val x: Int,
    val y: Int,
    val elementId: String? = null       // 可选的UI元素ID
)

/**
 * 长按操作参数
 */
@Serializable
data class LongPressParameters(
    val x: Int,
    val y: Int,
    val duration: Long = 1000L,         // 长按持续时间（毫秒）
    val elementId: String? = null
)

/**
 * 滑动操作参数
 */
@Serializable
data class SwipeParameters(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val duration: Long = 500L           // 滑动持续时间（毫秒）
)

/**
 * 文本输入参数
 */
@Serializable
data class TypeTextParameters(
    val text: String,
    val clearFirst: Boolean = false,    // 是否先清空现有文本
    val elementId: String? = null       // 目标输入框ID
)

/**
 * 按键操作参数
 */
@Serializable
data class PressKeyParameters(
    val keyCode: Int,                   // Android KeyEvent 键码
    val metaState: Int = 0              // Meta状态（如Ctrl、Alt等）
)

/**
 * 滚动操作参数
 */
@Serializable
data class ScrollParameters(
    val direction: ScrollDirection,
    val amount: Int = 1,                // 滚动量
    val elementId: String? = null       // 可滚动元素ID
)

/**
 * 滚动方向枚举
 */
enum class ScrollDirection {
    UP, DOWN, LEFT, RIGHT
}

/**
 * 操作执行结果数据
 */
@Serializable
data class ActionResultData(
    val commandId: String,              // 对应的指令ID
    val success: Boolean,               // 执行是否成功
    val errorMessage: String? = null,   // 错误信息（失败时）
    val executionTime: Long,            // 执行耗时（毫秒）
    val resultData: JsonObject? = null  // 额外的结果数据
)

/**
 * AI反馈数据
 */
@Serializable
data class AIFeedbackData(
    val feedbackType: FeedbackType,
    val content: String,                // 反馈内容
    val context: JsonObject? = null     // 额外上下文信息
)

/**
 * AI反馈类型
 */
enum class FeedbackType {
    THINKING,           // AI思考过程
    STATUS_UPDATE,      // 状态更新
    TASK_PROGRESS,      // 任务进度
    COMPLETION,         // 任务完成
    WARNING,            // 警告信息
    INFO                // 一般信息
}

/**
 * AI提问数据
 */
@Serializable
data class AIQuestionData(
    val question: String,               // 问题内容
    val questionType: QuestionType,     // 问题类型
    val options: List<String>? = null,  // 选项（选择题）
    val defaultValue: String? = null,   // 默认值
    val timeout: Long? = null           // 超时时间（毫秒）
)

/**
 * 问题类型枚举
 */
enum class QuestionType {
    YES_NO,             // 是/否问题
    MULTIPLE_CHOICE,    // 多选题
    TEXT_INPUT,         // 文本输入
    CONFIRMATION        // 确认操作
}

/**
 * Agent状态数据
 */
@Serializable
data class AgentStatusData(
    val connectionStatus: ConnectionStatus,
    val batteryLevel: Int,              // 电池电量百分比
    val isCharging: Boolean,            // 是否在充电
    val networkType: String,            // 网络类型
    val availableMemory: Long,          // 可用内存（字节）
    val cpuUsage: Float,                // CPU使用率百分比
    val permissions: PermissionStatus   // 权限状态
)

/**
 * 连接状态枚举
 */
enum class ConnectionStatus {
    CONNECTED,          // 已连接
    CONNECTING,         // 连接中
    DISCONNECTED,       // 已断开
    RECONNECTING,       // 重连中
    ERROR               // 连接错误
}

/**
 * 权限状态
 */
@Serializable
data class PermissionStatus(
    val accessibilityService: Boolean,  // 无障碍服务
    val overlayPermission: Boolean,     // 悬浮窗权限
    val screenshotPermission: Boolean,  // 截图权限
    val shizukuStatus: Boolean          // Shizuku状态
)

/**
 * 心跳消息数据
 */
@Serializable
data class HeartbeatData(
    val agentVersion: String,           // Agent版本
    val uptime: Long                    // 运行时间（毫秒）
)

/**
 * 错误消息数据
 */
@Serializable
data class ErrorData(
    val errorCode: String,              // 错误代码
    val errorMessage: String,           // 错误信息
    val stackTrace: String? = null      // 堆栈跟踪（调试用）
)