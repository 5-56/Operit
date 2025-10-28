package com.ai.assistance.operit.core.tools.javascript.debug

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Script debugger that provides step-by-step debugging capabilities
 * for JavaScript execution and automation tools.
 */
class ScriptDebugger private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ScriptDebugger"
        
        @Volatile
        private var INSTANCE: ScriptDebugger? = null
        
        fun getInstance(context: Context): ScriptDebugger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScriptDebugger(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    private val _currentSession = MutableStateFlow<DebugSession?>(null)
    val currentSession: StateFlow<DebugSession?> = _currentSession.asStateFlow()
    
    private val _debugState = MutableStateFlow(DebugState.IDLE)
    val debugState: StateFlow<DebugState> = _debugState.asStateFlow()
    
    private val pauseMutex = Mutex()
    private var pauseLatch: kotlinx.coroutines.sync.Semaphore? = null
    
    /**
     * Start a new debug session
     */
    fun startSession(scriptName: String, mode: DebugMode = DebugMode.DEBUG): DebugSession {
        val session = DebugSession(
            sessionId = UUID.randomUUID().toString(),
            scriptName = scriptName,
            startTime = System.currentTimeMillis(),
            mode = mode,
            state = DebugState.RUNNING
        )
        _currentSession.value = session
        _debugState.value = DebugState.RUNNING
        Log.d(TAG, "Started debug session: ${session.sessionId} in mode: $mode")
        return session
    }
    
    /**
     * End the current debug session
     */
    fun endSession() {
        val session = _currentSession.value
        if (session != null) {
            session.state = DebugState.COMPLETED
            Log.d(TAG, "Ended debug session: ${session.sessionId}")
        }
        _debugState.value = DebugState.IDLE
        _currentSession.value = null
        pauseLatch?.release()
        pauseLatch = null
    }
    
    /**
     * Add a breakpoint
     */
    fun addBreakpoint(line: Int, condition: String? = null): Breakpoint {
        val session = _currentSession.value ?: throw IllegalStateException("No active debug session")
        val breakpoint = Breakpoint(
            id = UUID.randomUUID().toString(),
            line = line,
            condition = condition
        )
        session.breakpoints.add(breakpoint)
        Log.d(TAG, "Added breakpoint at line $line")
        return breakpoint
    }
    
    /**
     * Remove a breakpoint
     */
    fun removeBreakpoint(breakpointId: String) {
        val session = _currentSession.value ?: return
        session.breakpoints.removeIf { it.id == breakpointId }
        Log.d(TAG, "Removed breakpoint: $breakpointId")
    }
    
    /**
     * Toggle breakpoint enable/disable
     */
    fun toggleBreakpoint(breakpointId: String) {
        val session = _currentSession.value ?: return
        val breakpoint = session.breakpoints.find { it.id == breakpointId } ?: return
        val index = session.breakpoints.indexOf(breakpoint)
        session.breakpoints[index] = breakpoint.copy(enabled = !breakpoint.enabled)
        Log.d(TAG, "Toggled breakpoint: $breakpointId to ${!breakpoint.enabled}")
    }
    
    /**
     * Clear all breakpoints
     */
    fun clearBreakpoints() {
        val session = _currentSession.value ?: return
        session.breakpoints.clear()
        Log.d(TAG, "Cleared all breakpoints")
    }
    
    /**
     * Check if should pause at current line
     */
    suspend fun checkBreakpoint(line: Int, context: DebugContext) {
        val session = _currentSession.value ?: return
        if (session.mode == DebugMode.RUN) return
        
        val shouldPause = when (session.mode) {
            DebugMode.STEP_OVER, DebugMode.STEP_INTO, DebugMode.STEP_OUT -> true
            DebugMode.DEBUG -> {
                session.breakpoints.any { it.line == line && it.enabled }
            }
            else -> false
        }
        
        if (shouldPause) {
            pause(context)
        }
    }
    
    /**
     * Pause execution and wait for resume
     */
    private suspend fun pause(context: DebugContext) {
        val session = _currentSession.value ?: return
        
        pauseMutex.withLock {
            session.state = DebugState.PAUSED
            session.currentContext = context
            _debugState.value = DebugState.PAUSED
            
            Log.d(TAG, "Paused at line ${context.currentLine}")
            
            pauseLatch = kotlinx.coroutines.sync.Semaphore(1, 1)
            pauseLatch?.acquire()
        }
    }
    
    /**
     * Resume execution
     */
    fun resume() {
        val session = _currentSession.value ?: return
        session.state = DebugState.RUNNING
        _debugState.value = DebugState.RUNNING
        Log.d(TAG, "Resumed execution")
        pauseLatch?.release()
    }
    
    /**
     * Step over (execute current line and pause at next)
     */
    fun stepOver() {
        val session = _currentSession.value ?: return
        session.state = DebugState.RUNNING
        _debugState.value = DebugState.RUNNING
        Log.d(TAG, "Step over")
        pauseLatch?.release()
    }
    
    /**
     * Step into (step into function calls)
     */
    fun stepInto() {
        val session = _currentSession.value ?: return
        session.state = DebugState.RUNNING
        _debugState.value = DebugState.RUNNING
        Log.d(TAG, "Step into")
        pauseLatch?.release()
    }
    
    /**
     * Step out (continue until current function returns)
     */
    fun stepOut() {
        val session = _currentSession.value ?: return
        session.state = DebugState.RUNNING
        _debugState.value = DebugState.RUNNING
        Log.d(TAG, "Step out")
        pauseLatch?.release()
    }
    
    /**
     * Stop execution
     */
    fun stop() {
        val session = _currentSession.value ?: return
        session.state = DebugState.COMPLETED
        _debugState.value = DebugState.COMPLETED
        Log.d(TAG, "Stopped execution")
        pauseLatch?.release()
    }
    
    /**
     * Add a log entry
     */
    fun log(level: LogLevel, message: String, context: String? = null) {
        val session = _currentSession.value ?: return
        val logEntry = DebugLog(
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message,
            context = context
        )
        session.logs.add(logEntry)
        Log.d(TAG, "[${level.name}] $message")
    }
    
    /**
     * Add an automation step
     */
    fun addAutomationStep(
        operation: String,
        description: String,
        screenshot: String? = null,
        uiState: String? = null,
        success: Boolean = true,
        error: String? = null
    ) {
        val session = _currentSession.value ?: return
        val step = AutomationStep(
            stepIndex = session.automationSteps.size,
            operation = operation,
            description = description,
            timestamp = System.currentTimeMillis(),
            screenshot = screenshot,
            uiState = uiState,
            success = success,
            error = error
        )
        session.automationSteps.add(step)
        Log.d(TAG, "Added automation step ${step.stepIndex}: $operation")
    }
    
    /**
     * Update the current debug context
     */
    fun updateContext(context: DebugContext) {
        val session = _currentSession.value ?: return
        session.currentContext = context
    }
    
    /**
     * Get all logs for the current session
     */
    fun getLogs(): List<DebugLog> {
        return _currentSession.value?.logs?.toList() ?: emptyList()
    }
    
    /**
     * Get all automation steps for the current session
     */
    fun getAutomationSteps(): List<AutomationStep> {
        return _currentSession.value?.automationSteps?.toList() ?: emptyList()
    }
    
    /**
     * Check if debugging is active
     */
    fun isDebugging(): Boolean {
        return _currentSession.value != null && _debugState.value != DebugState.IDLE
    }
    
    /**
     * Check if execution is paused
     */
    fun isPaused(): Boolean {
        return _debugState.value == DebugState.PAUSED
    }
}
