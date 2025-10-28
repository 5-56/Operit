package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.recorder.ActionRecorder
import com.ai.assistance.operit.core.recorder.PrivacyFilterConfig
import com.ai.assistance.operit.core.recorder.RecordingEvent
import com.ai.assistance.operit.core.recorder.Script
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Delegate for managing action recording in the chat/floating window UI
 */
class RecorderDelegate(
    private val context: Context,
    private val viewModelScope: CoroutineScope
) {
    companion object {
        private const val TAG = "RecorderDelegate"
    }

    private val recorder = ActionRecorder.getInstance(context)

    // UI state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentScript = MutableStateFlow<Script?>(null)
    val currentScript: StateFlow<Script?> = _currentScript.asStateFlow()

    private val _recordingStepCount = MutableStateFlow(0)
    val recordingStepCount: StateFlow<Int> = _recordingStepCount.asStateFlow()

    private val _showAnnotationDialog = MutableStateFlow(false)
    val showAnnotationDialog: StateFlow<Boolean> = _showAnnotationDialog.asStateFlow()

    private val _showPrivacyFilterDialog = MutableStateFlow(false)
    val showPrivacyFilterDialog: StateFlow<Boolean> = _showPrivacyFilterDialog.asStateFlow()

    private val _privacyFilterConfig = MutableStateFlow(PrivacyFilterConfig())
    val privacyFilterConfig: StateFlow<PrivacyFilterConfig> = _privacyFilterConfig.asStateFlow()

    private val _recordingError = MutableStateFlow<String?>(null)
    val recordingError: StateFlow<String?> = _recordingError.asStateFlow()

    init {
        // Collect recording state changes
        viewModelScope.launch {
            recorder.isRecording.collect { isRecording ->
                _isRecording.value = isRecording
                if (!isRecording) {
                    _recordingStepCount.value = 0
                }
            }
        }

        // Collect privacy filter config changes
        viewModelScope.launch {
            recorder.privacyFilterConfig.collect { config ->
                _privacyFilterConfig.value = config
            }
        }

        // Collect recording events
        viewModelScope.launch {
            recorder.recordingEvents.collect { event ->
                when (event) {
                    is RecordingEvent.RecordingStarted -> {
                        Log.d(TAG, "Recording started: ${event.sessionId}")
                        _recordingError.value = null
                    }
                    is RecordingEvent.RecordingStopped -> {
                        Log.d(TAG, "Recording stopped: ${event.script.steps.size} steps")
                        _currentScript.value = event.script
                    }
                    is RecordingEvent.StepRecorded -> {
                        _recordingStepCount.value++
                    }
                    is RecordingEvent.Error -> {
                        Log.e(TAG, "Recording error: ${event.message}")
                        _recordingError.value = event.message
                    }
                }
            }
        }
    }

    /**
     * Start recording actions
     */
    fun startRecording() {
        viewModelScope.launch {
            val result = recorder.startRecording(
                sessionName = "Recording ${System.currentTimeMillis()}",
                privacyConfig = _privacyFilterConfig.value
            )
            if (!result.success) {
                _recordingError.value = result.message
                Log.e(TAG, "Failed to start recording: ${result.message}")
            }
        }
    }

    /**
     * Stop recording and get the script
     */
    fun stopRecording() {
        viewModelScope.launch {
            val (result, script) = recorder.stopRecording()
            if (!result.success) {
                _recordingError.value = result.message
                Log.e(TAG, "Failed to stop recording: ${result.message}")
            } else if (script != null) {
                _currentScript.value = script
            }
        }
    }

    /**
     * Toggle recording state
     */
    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    /**
     * Show annotation dialog
     */
    fun showAnnotationDialog() {
        _showAnnotationDialog.value = true
    }

    /**
     * Hide annotation dialog
     */
    fun hideAnnotationDialog() {
        _showAnnotationDialog.value = false
    }

    /**
     * Add an annotation to the recording
     */
    fun addAnnotation(annotation: String) {
        recorder.addAnnotation(annotation)
        hideAnnotationDialog()
    }

    /**
     * Show privacy filter configuration dialog
     */
    fun showPrivacyFilterDialog() {
        _showPrivacyFilterDialog.value = true
    }

    /**
     * Hide privacy filter configuration dialog
     */
    fun hidePrivacyFilterDialog() {
        _showPrivacyFilterDialog.value = false
    }

    /**
     * Update privacy filter configuration
     */
    fun updatePrivacyFilter(config: PrivacyFilterConfig) {
        _privacyFilterConfig.value = config
        recorder.updatePrivacyFilter(config)
        hidePrivacyFilterDialog()
    }

    /**
     * Discard current script
     */
    fun discardScript() {
        _currentScript.value = null
        _recordingStepCount.value = 0
    }

    /**
     * Clear recording error
     */
    fun clearError() {
        _recordingError.value = null
    }
}
