package com.xihe.assistant.core.tools.defaultTool

import android.content.Context
import com.xihe.assistant.core.tools.AIToolHandler

/**
 * 工具注册器
 * 负责注册所有可用的工具
 */
object ToolRegistration {

    /**
     * 注册所有工具
     */
    fun registerAllTools(toolHandler: AIToolHandler, context: Context) {
        // 注册标准工具
        StandardTools.registerAllTools(toolHandler, context)
        
        // 注册高级工具
        AdvancedTools.registerAllTools(toolHandler, context)
        
        // 注册AI工具
        AITools.registerAllTools(toolHandler, context)
        
        // 注册自动化工具
        AutomationTools.registerAllTools(toolHandler, context)
        
        // 注册语音工具
        VoiceTools.registerAllTools(toolHandler, context)
        
        // 注册媒体工具
        MediaTools.registerAllTools(toolHandler, context)
        
        // 注册网络工具
        NetworkTools.registerAllTools(toolHandler, context)
        
        // 注册系统工具
        SystemTools.registerAllTools(toolHandler, context)
    }
}