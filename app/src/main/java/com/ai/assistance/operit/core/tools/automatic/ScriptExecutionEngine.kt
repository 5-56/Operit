package com.ai.assistance.operit.core.tools.automatic

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.util.map.NodeState
import com.ai.assistance.operit.util.map.StatefulEdge
import com.ai.assistance.operit.util.map.StatefulPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Engine that executes recorded automation scripts using UIOperationExecutor.
 * Supports parameter injection, real-time progress tracking, logging and error handling.
 */
class ScriptExecutionEngine(
    private val context: Context,
    private val toolHandler: AIToolHandler
) {
    companion object {
        private const val TAG = "ScriptExecutionEngine"
        private const val OPERATION_DELAY = 500L
    }

    private fun createSingleStepPath(
        scriptId: String,
        stepIndex: Int,
        step: ScriptStep,
        parameters: Map<String, Any>
    ): StatefulPath {
        val fromStateId = "${scriptId}_step_$stepIndex"
        val toStateId = "${scriptId}_step_${stepIndex + 1}"
        val fromState = NodeState(fromStateId, parameters)
        val toState = NodeState(toStateId, parameters)
        val edge = StatefulEdge(
            from = fromStateId,
            to = toStateId,
            action = step.description.ifEmpty { "Step ${step.stepNumber}" },
            stateTransform = step.operation,
            metadata = mapOf(
                "stepNumber" to step.stepNumber,
                "continueOnError" to step.continueOnError,
                "retryCount" to step.retryCount
            )
        )
        return StatefulPath(listOf(fromState, toState), listOf(edge), 1.0)
    }

    /**
     * Execute a script with provided parameters and emit progress updates.
     * Returns a Flow that emits ScriptExecutionState updates.
     */
    suspend fun executeScript(
        script: AutomationScript,
        parameters: Map<String, Any>,
        routeConfig: UIRouteConfig? = null
    ): Flow<ScriptExecutionState> = flow {
        val startTime = System.currentTimeMillis()

        emit(ScriptExecutionState.Started(script, startTime))

        if (!script.areParametersMet(parameters)) {
            val missing = script.getMissingParameters(parameters)
            val errorMessage = "Missing required parameters: ${missing.joinToString { it.key }}"
            Log.e(TAG, "Parameter check failed for script ${script.id}: $errorMessage")
            
            emit(ScriptExecutionState.Failed(
                script = script,
                error = errorMessage,
                currentStep = 0,
                totalSteps = script.steps.size,
                logs = listOf(
                    ScriptExecutionLog(
                        stepNumber = 0,
                        stepDescription = "Parameter validation",
                        status = StepStatus.FAILED,
                        message = errorMessage,
                        error = errorMessage
                    )
                ),
                executionTimeMs = System.currentTimeMillis() - startTime
            ))
            return@flow
        }

        val logs = mutableListOf<ScriptExecutionLog>()
        val executor = UIOperationExecutor(context, toolHandler, routeConfig)

        var completedSteps = 0
        var executionSuccess = true
        var executionError: String? = null

        for ((index, step) in script.steps.withIndex()) {
            val stepNumber = step.stepNumber
            
            emit(ScriptExecutionState.StepStarted(
                script = script,
                stepNumber = stepNumber,
                stepDescription = step.description,
                currentStep = stepNumber,
                totalSteps = script.steps.size,
                logs = logs.toList()
            ))

            val startStepLog = ScriptExecutionLog(
                stepNumber = stepNumber,
                stepDescription = step.description,
                status = StepStatus.IN_PROGRESS,
                message = "Executing step $stepNumber: ${step.description}"
            )
            logs += startStepLog

            Log.d(TAG, "Executing step $stepNumber/${script.steps.size}: ${step.description}")

            var retries = 0
            var stepSuccess = false
            var stepError: String? = null

            while (retries <= step.retryCount && !stepSuccess) {
                try {
                    if (retries > 0) {
                        Log.d(TAG, "Retrying step $stepNumber (attempt ${retries + 1}/${step.retryCount + 1})")
                        delay(OPERATION_DELAY)
                    }

                    val success = withContext(Dispatchers.IO) {
                        val singleStepPath = createSingleStepPath(
                            script.id,
                            index,
                            step,
                            parameters
                        )
                        val result = executor.executePath(singleStepPath, parameters)
                        result.success
                    }

                    if (success) {
                        stepSuccess = true
                        completedSteps++

                        val completeStepLog = ScriptExecutionLog(
                            stepNumber = stepNumber,
                            stepDescription = step.description,
                            status = StepStatus.COMPLETED,
                            message = "Completed step $stepNumber: ${step.description}"
                        )
                        logs += completeStepLog

                        emit(ScriptExecutionState.StepCompleted(
                            script = script,
                            stepNumber = stepNumber,
                            stepDescription = step.description,
                            currentStep = stepNumber,
                            totalSteps = script.steps.size,
                            logs = logs.toList()
                        ))

                        Log.d(TAG, "Step $stepNumber completed successfully")
                    } else {
                        stepError = "Operation returned false"
                        if (retries < step.retryCount) {
                            retries++
                        } else {
                            break
                        }
                    }
                } catch (e: Exception) {
                    stepError = e.message ?: "Unknown error"
                    Log.e(TAG, "Error executing step $stepNumber", e)
                    if (retries < step.retryCount) {
                        retries++
                    } else {
                        break
                    }
                }
            }

            if (!stepSuccess) {
                val errorMsg = stepError ?: "Step execution failed"
                Log.e(TAG, "Step $stepNumber failed after ${retries + 1} attempts: $errorMsg")

                val failStepLog = ScriptExecutionLog(
                    stepNumber = stepNumber,
                    stepDescription = step.description,
                    status = StepStatus.FAILED,
                    message = "Failed step $stepNumber after ${retries + 1} attempts",
                    error = errorMsg
                )
                logs += failStepLog

                emit(ScriptExecutionState.StepFailed(
                    script = script,
                    stepNumber = stepNumber,
                    stepDescription = step.description,
                    error = errorMsg,
                    currentStep = stepNumber,
                    totalSteps = script.steps.size,
                    logs = logs.toList()
                ))

                if (!step.continueOnError) {
                    executionSuccess = false
                    executionError = "Script failed at step $stepNumber: $errorMsg"
                    break
                } else {
                    Log.d(TAG, "Continuing execution despite failure (continueOnError=true)")
                    logs += ScriptExecutionLog(
                        stepNumber = stepNumber,
                        stepDescription = step.description,
                        status = StepStatus.SKIPPED,
                        message = "Continuing despite failure (continueOnError=true)"
                    )
                }
            }

            delay(OPERATION_DELAY)
        }

        val executionTimeMs = System.currentTimeMillis() - startTime

        if (executionSuccess) {
            emit(ScriptExecutionState.Completed(
                script = script,
                completedSteps = completedSteps,
                totalSteps = script.steps.size,
                logs = logs.toList(),
                executionTimeMs = executionTimeMs
            ))
        } else {
            emit(ScriptExecutionState.Failed(
                script = script,
                error = executionError ?: "Unknown error",
                currentStep = completedSteps,
                totalSteps = script.steps.size,
                logs = logs.toList(),
                executionTimeMs = executionTimeMs
            ))
        }
    }
}

/**
 * Represents the current state of script execution
 */
sealed class ScriptExecutionState {
    data class Started(
        val script: AutomationScript,
        val startTime: Long
    ) : ScriptExecutionState()

    data class StepStarted(
        val script: AutomationScript,
        val stepNumber: Int,
        val stepDescription: String,
        val currentStep: Int,
        val totalSteps: Int,
        val logs: List<ScriptExecutionLog>
    ) : ScriptExecutionState()

    data class StepCompleted(
        val script: AutomationScript,
        val stepNumber: Int,
        val stepDescription: String,
        val currentStep: Int,
        val totalSteps: Int,
        val logs: List<ScriptExecutionLog>
    ) : ScriptExecutionState()

    data class StepFailed(
        val script: AutomationScript,
        val stepNumber: Int,
        val stepDescription: String,
        val error: String,
        val currentStep: Int,
        val totalSteps: Int,
        val logs: List<ScriptExecutionLog>
    ) : ScriptExecutionState()

    data class Completed(
        val script: AutomationScript,
        val completedSteps: Int,
        val totalSteps: Int,
        val logs: List<ScriptExecutionLog>,
        val executionTimeMs: Long
    ) : ScriptExecutionState()

    data class Failed(
        val script: AutomationScript,
        val error: String,
        val currentStep: Int,
        val totalSteps: Int,
        val logs: List<ScriptExecutionLog>,
        val executionTimeMs: Long
    ) : ScriptExecutionState()
}
