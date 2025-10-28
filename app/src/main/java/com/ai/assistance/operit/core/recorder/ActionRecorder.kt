package com.ai.assistance.operit.core.recorder

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.system.action.ActionListener
import com.ai.assistance.operit.core.tools.system.action.ActionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Records user actions and tool invocations into a script
 */
class ActionRecorder(
    private val context: Context,
    private val actionManager: ActionManager
) {
    companion object {
        private const val TAG = "ActionRecorder"
        
        @Volatile
        private var INSTANCE: ActionRecorder? = null
        
        fun getInstance(context: Context): ActionRecorder {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActionRecorder(
                    context.applicationContext,
                    ActionManager.getInstance(context)
                ).also { INSTANCE = it }
            }
        }
    }

    private val recorderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Current recording session
    private var currentSession: RecordingSession? = null
    
    // Recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    // Privacy filter configuration
    private val _privacyFilterConfig = MutableStateFlow(PrivacyFilterConfig())
    val privacyFilterConfig: StateFlow<PrivacyFilterConfig> = _privacyFilterConfig.asStateFlow()
    
    // Recording events (for UI updates)
    private val _recordingEvents = MutableSharedFlow<RecordingEvent>(replay = 0)
    val recordingEvents: SharedFlow<RecordingEvent> = _recordingEvents.asSharedFlow()
    
    // Callback IDs for cleanup
    private val actionCallbackId = "recorder_action_callback"
    
    // Last action timestamp for automatic delay calculation
    private var lastActionTimestamp: Long = 0

    /**
     * Start recording actions
     */
    suspend fun startRecording(
        sessionName: String = "Recording ${System.currentTimeMillis()}",
        privacyConfig: PrivacyFilterConfig = PrivacyFilterConfig(),
        startActionManager: Boolean = true
    ): RecordingResult {
        if (_isRecording.value) {
            return RecordingResult.failure("Already recording")
        }
        
        try {
            // Create new session
            val session = RecordingSession(
                id = UUID.randomUUID().toString(),
                startTime = System.currentTimeMillis(),
                privacyFilterConfig = privacyConfig
            )
            currentSession = session
            _privacyFilterConfig.value = privacyConfig
            lastActionTimestamp = System.currentTimeMillis()
            
            // Register action listener callback
            actionManager.registerEventCallback(actionCallbackId) { event ->
                recorderScope.launch {
                    handleActionEvent(event)
                }
            }
            
            // Start listening if requested and not already
            if (startActionManager && !actionManager.isListening.value) {
                val result = actionManager.startListeningWithHighestPermission { event ->
                    // Event will be handled by the registered callback
                }
                
                if (!result.success) {
                    currentSession = null
                    return RecordingResult.failure("Failed to start action listening: ${result.message}")
                }
            }
            
            _isRecording.value = true
            
            // Emit recording started event
            _recordingEvents.emit(RecordingEvent.RecordingStarted(session.id, sessionName))
            
            Log.d(TAG, "Recording started: ${session.id}")
            return RecordingResult.success("Recording started", session.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            currentSession = null
            return RecordingResult.failure("Failed to start recording: ${e.message}")
        }
    }

    /**
     * Stop recording and return the generated script
     */
    suspend fun stopRecording(): Pair<RecordingResult, Script?> {
        if (!_isRecording.value || currentSession == null) {
            return Pair(RecordingResult.failure("Not currently recording"), null)
        }
        
        try {
            val session = currentSession!!
            session.endTime = System.currentTimeMillis()
            
            // Unregister callbacks
            actionManager.unregisterEventCallback(actionCallbackId)
            
            // Create script from session
            val script = Script(
                id = session.id,
                name = "Recording ${session.startTime}",
                description = "Recorded from ${session.startTime} to ${session.endTime}",
                steps = session.steps.toList(),
                createdAt = session.startTime
            )
            
            _isRecording.value = false
            currentSession = null
            
            // Emit recording stopped event
            _recordingEvents.emit(RecordingEvent.RecordingStopped(script))
            
            Log.d(TAG, "Recording stopped: ${script.id}, ${script.steps.size} steps")
            return Pair(RecordingResult.success("Recording stopped", script.id), script)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            _isRecording.value = false
            currentSession = null
            return Pair(RecordingResult.failure("Failed to stop recording: ${e.message}"), null)
        }
    }

    /**
     * Add an annotation to the current step or as a separate step
     */
    fun addAnnotation(annotation: String) {
        val session = currentSession ?: return
        
        recorderScope.launch {
            val step = ScriptStep.AnnotationStep(
                timestamp = System.currentTimeMillis(),
                description = "User annotation",
                annotation = annotation
            )
            
            session.steps.add(step)
            _recordingEvents.emit(RecordingEvent.StepRecorded(step))
            
            Log.d(TAG, "Annotation added: $annotation")
        }
    }

    /**
     * Record a tool invocation (to be called manually from tool execution code)
     */
    fun recordToolInvocation(toolName: String, parameters: Map<String, String>, category: String? = null) {
        if (!_isRecording.value) return
        
        recorderScope.launch {
            handleToolInvocationData(toolName, parameters, category)
        }
    }

    /**
     * Update the privacy filter configuration
     */
    fun updatePrivacyFilter(config: PrivacyFilterConfig) {
        _privacyFilterConfig.value = config
        currentSession?.let {
            it.privacyFilterConfig = config
        }
        Log.d(TAG, "Privacy filter updated: $config")
    }

    /**
     * Handle action events from ActionManager
     */
    private suspend fun handleActionEvent(event: ActionListener.ActionEvent) {
        val session = currentSession ?: return
        
        // Apply privacy filter
        if (shouldFilterAction(event)) {
            Log.d(TAG, "Action filtered by privacy settings")
            return
        }
        
        // Calculate delay from last action
        val currentTime = System.currentTimeMillis()
        val delayMs = currentTime - lastActionTimestamp
        
        // Add delay step if significant (> 100ms)
        if (delayMs > 100 && session.steps.isNotEmpty()) {
            val delayStep = ScriptStep.DelayStep(
                timestamp = lastActionTimestamp,
                description = "Wait ${delayMs}ms",
                delayMs = delayMs
            )
            session.steps.add(delayStep)
        }
        
        // Create gesture step
        val gestureStep = ScriptStep.UIGestureStep(
            timestamp = event.timestamp,
            description = buildActionDescription(event),
            actionType = event.actionType,
            coordinates = event.coordinates?.let { Coordinates(it.first, it.second) },
            elementInfo = event.elementInfo?.let { 
                ElementInfo(
                    resourceId = it.resourceId,
                    className = it.className,
                    text = filterSensitiveText(it.text),
                    contentDescription = it.contentDescription,
                    bounds = it.bounds,
                    packageName = it.packageName
                )
            },
            inputText = filterSensitiveText(event.inputText),
            additionalData = event.additionalData.mapValues { it.value.toString() }
        )
        
        session.steps.add(gestureStep)
        lastActionTimestamp = currentTime
        
        // Emit step recorded event
        _recordingEvents.emit(RecordingEvent.StepRecorded(gestureStep))
        
        Log.d(TAG, "Action recorded: ${event.actionType}")
    }

    /**
     * Handle tool invocations (internal helper)
     */
    private suspend fun handleToolInvocationData(toolName: String, parameters: Map<String, String>, category: String? = null) {
        val session = currentSession ?: return
        
        // Apply privacy filter
        if (shouldFilterTool(toolName, parameters)) {
            Log.d(TAG, "Tool invocation filtered by privacy settings")
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val delayMs = currentTime - lastActionTimestamp
        
        // Add delay step if significant
        if (delayMs > 100 && session.steps.isNotEmpty()) {
            val delayStep = ScriptStep.DelayStep(
                timestamp = lastActionTimestamp,
                description = "Wait ${delayMs}ms",
                delayMs = delayMs
            )
            session.steps.add(delayStep)
        }
        
        // Create tool invocation step
        val toolStep = ScriptStep.ToolInvocationStep(
            timestamp = currentTime,
            description = buildToolDescription(toolName, parameters),
            toolName = toolName,
            parameters = parameters,
            category = category
        )
        
        session.steps.add(toolStep)
        lastActionTimestamp = currentTime
        
        // Emit step recorded event
        _recordingEvents.emit(RecordingEvent.StepRecorded(toolStep))
        
        Log.d(TAG, "Tool invocation recorded: $toolName")
    }

    /**
     * Check if an action should be filtered based on privacy settings
     */
    private fun shouldFilterAction(event: ActionListener.ActionEvent): Boolean {
        val config = currentSession?.privacyFilterConfig ?: return false
        
        if (!config.enabled) return false
        
        // Filter password fields
        if (config.filterPasswords && event.elementInfo?.className?.contains("password", ignoreCase = true) == true) {
            return true
        }
        
        // Filter sensitive resource IDs
        if (event.elementInfo?.resourceId != null && config.sensitiveResourceIds.contains(event.elementInfo.resourceId)) {
            return true
        }
        
        // Filter sensitive packages
        if (event.elementInfo?.packageName != null && config.sensitivePackages.contains(event.elementInfo.packageName)) {
            return true
        }
        
        // Apply custom regex filters
        if (config.filterSensitiveInputs && event.inputText != null) {
            for (pattern in config.customFilters) {
                try {
                    if (Regex(pattern).containsMatchIn(event.inputText)) {
                        return true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid regex pattern: $pattern", e)
                }
            }
        }
        
        return false
    }

    /**
     * Check if a tool invocation should be filtered
     */
    private fun shouldFilterTool(toolName: String, parameters: Map<String, String>): Boolean {
        val config = currentSession?.privacyFilterConfig ?: return false
        
        if (!config.enabled) return false
        
        // Filter tools with password-related parameters
        if (config.filterPasswords) {
            val hasPasswordParam = parameters.any { (key, value) ->
                key.contains("password", ignoreCase = true) ||
                key.contains("secret", ignoreCase = true) ||
                key.contains("token", ignoreCase = true) ||
                value.contains("password", ignoreCase = true) ||
                value.contains("secret", ignoreCase = true) ||
                value.contains("token", ignoreCase = true)
            }
            if (hasPasswordParam) return true
        }
        
        return false
    }

    /**
     * Filter sensitive text based on privacy settings
     */
    private fun filterSensitiveText(text: String?): String? {
        if (text == null) return null
        
        val config = currentSession?.privacyFilterConfig ?: return text
        
        if (!config.enabled || !config.filterSensitiveInputs) return text
        
        // Apply custom regex filters
        var filteredText = text
        for (pattern in config.customFilters) {
            try {
                filteredText = filteredText.replace(Regex(pattern), "***")
            } catch (e: Exception) {
                Log.w(TAG, "Invalid regex pattern: $pattern", e)
            }
        }
        
        return filteredText
    }

    /**
     * Build a human-readable description for an action
     */
    private fun buildActionDescription(event: ActionListener.ActionEvent): String {
        val element = event.elementInfo
        return when (event.actionType) {
            ActionListener.ActionType.CLICK -> {
                if (element?.text != null) {
                    "Click on '${element.text}'"
                } else if (element?.contentDescription != null) {
                    "Click on '${element.contentDescription}'"
                } else {
                    "Click at (${event.coordinates?.first}, ${event.coordinates?.second})"
                }
            }
            ActionListener.ActionType.LONG_CLICK -> {
                "Long click on ${element?.text ?: "element"}"
            }
            ActionListener.ActionType.SWIPE -> {
                "Swipe from (${event.coordinates?.first}, ${event.coordinates?.second})"
            }
            ActionListener.ActionType.TEXT_INPUT -> {
                "Input text: ${event.inputText}"
            }
            ActionListener.ActionType.SCROLL -> {
                "Scroll in ${element?.className ?: "view"}"
            }
            else -> {
                "${event.actionType} action"
            }
        }
    }

    /**
     * Build a human-readable description for a tool invocation
     */
    private fun buildToolDescription(toolName: String, parameters: Map<String, String>): String {
        val params = parameters.entries.joinToString(", ") { "${it.key}=${it.value}" }
        return if (params.isNotEmpty()) {
            "Execute $toolName($params)"
        } else {
            "Execute $toolName"
        }
    }

    /**
     * Get current session information
     */
    fun getCurrentSession(): RecordingSession? = currentSession
}

/**
 * Recording events for UI updates
 */
sealed class RecordingEvent {
    data class RecordingStarted(val sessionId: String, val name: String) : RecordingEvent()
    data class RecordingStopped(val script: Script) : RecordingEvent()
    data class StepRecorded(val step: ScriptStep) : RecordingEvent()
    data class Error(val message: String) : RecordingEvent()
}

/**
 * Recording result
 */
data class RecordingResult(
    val success: Boolean,
    val message: String,
    val sessionId: String? = null
) {
    companion object {
        fun success(message: String, sessionId: String? = null) = 
            RecordingResult(true, message, sessionId)
        fun failure(message: String) = 
            RecordingResult(false, message)
    }
}
