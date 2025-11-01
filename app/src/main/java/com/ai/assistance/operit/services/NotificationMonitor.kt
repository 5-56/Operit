package com.ai.assistance.operit.services

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.automatic.AutomationTools
import com.ai.assistance.operit.data.dao.TriggerRuleDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AutomationAction
import com.ai.assistance.operit.data.model.ToolAction
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.TriggerActionType
import com.ai.assistance.operit.data.model.TriggerCondition
import com.ai.assistance.operit.data.model.TriggerRule
import com.ai.assistance.operit.data.model.TriggerType
import com.ai.assistance.operit.data.model.MatchMode
import com.ai.assistance.operit.data.preferences.TriggerPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class NotificationMonitor : NotificationListenerService() {
    
    private val TAG = "NotificationMonitor"
    private lateinit var triggerRuleDao: TriggerRuleDao
    private lateinit var triggerPreferences: TriggerPreferences
    private lateinit var automationTools: AutomationTools
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationMonitor service created")
        
        val database = AppDatabase.getDatabase(this)
        triggerRuleDao = database.triggerRuleDao()
        triggerPreferences = TriggerPreferences.getInstance(this)
        
        val toolHandler = AIToolHandler.getInstance(this)
        automationTools = AutomationTools(this, toolHandler)
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        if (sbn == null) return
        
        serviceScope.launch {
            try {
                // Check if notification triggers are enabled
                if (!triggerPreferences.isNotificationTriggersEnabled()) {
                    Log.d(TAG, "Notification triggers are disabled, ignoring notification")
                    return@launch
                }
                
                // Get enabled notification rules
                val rules = triggerRuleDao.getEnabledRulesByType(TriggerType.NOTIFICATION).first()
                
                if (rules.isEmpty()) {
                    return@launch
                }
                
                val notification = sbn.notification ?: return@launch
                val packageName = sbn.packageName
                
                // Extract notification content
                val extras = notification.extras
                val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                
                Log.d(TAG, "Notification received from $packageName: title='$title', text='$text'")
                
                // Check each rule
                for (rule in rules) {
                    if (rule.triggerCondition !is TriggerCondition.NotificationCondition) {
                        continue
                    }
                    
                    val condition = rule.triggerCondition
                    
                    // Check if package name matches
                    if (condition.packageName != packageName) {
                        continue
                    }
                    
                    // Check title pattern if specified
                    if (condition.titlePattern != null) {
                        if (!matchesPattern(title, condition.titlePattern, condition.matchMode)) {
                            continue
                        }
                    }
                    
                    // Check text pattern if specified
                    if (condition.textPattern != null) {
                        if (!matchesPattern(text, condition.textPattern, condition.matchMode)) {
                            continue
                        }
                    }
                    
                    Log.i(TAG, "Trigger rule '${rule.name}' matched for notification from $packageName")
                    
                    // Execute the associated action
                    executeAction(rule)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification", e)
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Currently we don't handle notification removal
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationMonitor connected")
        
        serviceScope.launch {
            triggerPreferences.setNotificationAccessGranted(true)
        }
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "NotificationMonitor disconnected")
        
        serviceScope.launch {
            triggerPreferences.setNotificationAccessGranted(false)
        }
        
        // Request rebind
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(android.content.ComponentName(this, NotificationMonitor::class.java))
        }
    }
    
    private fun matchesPattern(text: String, pattern: String, mode: MatchMode): Boolean {
        return when (mode) {
            MatchMode.EXACT -> text == pattern
            MatchMode.CONTAINS -> text.contains(pattern, ignoreCase = true)
            MatchMode.REGEX -> {
                try {
                    pattern.toRegex().containsMatchIn(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid regex pattern: $pattern", e)
                    false
                }
            }
        }
    }
    
    private suspend fun executeAction(rule: TriggerRule) {
        try {
            Log.d(TAG, "Executing action for rule '${rule.name}': ${rule.actionType}")
            
            when (rule.actionType) {
                TriggerActionType.RUN_AUTOMATION -> {
                    val action = json.decodeFromString<AutomationAction>(rule.actionData)
                    executeAutomation(action)
                }
                TriggerActionType.EXECUTE_TOOL -> {
                    val action = json.decodeFromString<ToolAction>(rule.actionData)
                    executeTool(action)
                }
                TriggerActionType.CUSTOM_ACTION -> {
                    // Can be extended for custom actions
                    Log.d(TAG, "Custom action not yet implemented")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action for rule '${rule.name}'", e)
        }
    }
    
    private suspend fun executeAutomation(action: AutomationAction) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Executing automation: ${action.functionName}")
                
                // Get plan parameters
                val getPlanTool = AITool(
                    name = "get_plan_parameters",
                    parameters = buildList {
                        add(ToolParameter("function_name", action.functionName))
                        if (action.packageName != null) {
                            add(ToolParameter("package_name", action.packageName))
                        }
                    }
                )
                
                val planResult = automationTools.getPlanParameters(getPlanTool)
                if (!planResult.success) {
                    Log.e(TAG, "Failed to get plan parameters: ${planResult.error}")
                    return@withContext
                }
                
                // Execute plan with provided parameters
                val parametersJson = if (action.parameters.isEmpty()) {
                    "{}"
                } else {
                    json.encodeToString(action.parameters as Map<String, String>)
                }
                
                val executeTool = AITool(
                    name = "execute_plan",
                    parameters = listOf(
                        ToolParameter("parameters", parametersJson)
                    )
                )
                
                val executeResult = automationTools.executePlan(executeTool)
                if (executeResult.success) {
                    Log.i(TAG, "Automation executed successfully: ${action.functionName}")
                } else {
                    Log.e(TAG, "Failed to execute automation: ${executeResult.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing automation", e)
            }
        }
    }
    
    private suspend fun executeTool(action: ToolAction) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Executing tool: ${action.toolName}")
                
                val tool = AITool(
                    name = action.toolName,
                    parameters = action.parameters.map { (name, value) ->
                        ToolParameter(name, value)
                    }
                )
                
                val toolHandler = AIToolHandler.getInstance(this@NotificationMonitor)
                val result = toolHandler.executeTool(tool)
                
                if (result.success) {
                    Log.i(TAG, "Tool executed successfully: ${action.toolName}")
                } else {
                    Log.e(TAG, "Failed to execute tool: ${result.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing tool", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "NotificationMonitor service destroyed")
    }
}
