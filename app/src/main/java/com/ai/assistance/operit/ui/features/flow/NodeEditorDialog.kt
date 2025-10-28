package com.ai.assistance.operit.ui.features.flow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.flow.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditorDialog(
    node: FlowNode,
    onDismiss: () -> Unit,
    onSave: (FlowNode) -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${getNodeTypeName(node)}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (node) {
                    is FlowNode.Start -> {
                        Text("Start node - no configuration needed")
                    }
                    is FlowNode.End -> {
                        Text("End node - no configuration needed")
                    }
                    is FlowNode.Action -> {
                        ActionNodeEditor(node, onSave)
                    }
                    is FlowNode.Condition -> {
                        ConditionNodeEditor(node, onSave)
                    }
                    is FlowNode.Loop -> {
                        LoopNodeEditor(node, onSave)
                    }
                    is FlowNode.Delay -> {
                        DelayNodeEditor(node, onSave)
                    }
                    is FlowNode.Variable -> {
                        VariableNodeEditor(node, onSave)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    )
}

@Composable
fun ActionNodeEditor(
    node: FlowNode.Action,
    onSave: (FlowNode) -> Unit
) {
    var name by remember { mutableStateOf(node.name) }
    var description by remember { mutableStateOf(node.description) }
    var actionType by remember { mutableStateOf(node.actionType) }
    var parameters by remember { mutableStateOf(node.parameters) }

    LaunchedEffect(name, description, actionType, parameters) {
        onSave(node.copy(
            name = name,
            description = description,
            actionType = actionType,
            parameters = parameters
        ))
    }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Description") },
        modifier = Modifier.fillMaxWidth()
    )

    Text("Action Type", style = MaterialTheme.typography.labelMedium)
    ActionType.values().forEach { type ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(type.name)
            RadioButton(
                selected = actionType == type,
                onClick = { actionType = type }
            )
        }
    }

    Text("Parameters", style = MaterialTheme.typography.labelMedium)
    when (actionType) {
        ActionType.CLICK, ActionType.INPUT -> {
            var selectorType by remember { mutableStateOf(parameters["selectorType"] ?: "ByText") }
            var selectorValue by remember { mutableStateOf(parameters["selectorValue"] ?: "") }

            OutlinedTextField(
                value = selectorType,
                onValueChange = {
                    selectorType = it
                    parameters = parameters + ("selectorType" to it)
                },
                label = { Text("Selector Type") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = selectorValue,
                onValueChange = {
                    selectorValue = it
                    parameters = parameters + ("selectorValue" to it)
                },
                label = { Text("Selector Value") },
                modifier = Modifier.fillMaxWidth()
            )

            if (actionType == ActionType.INPUT) {
                var textVariable by remember { mutableStateOf(parameters["textVariable"] ?: "input_text") }
                OutlinedTextField(
                    value = textVariable,
                    onValueChange = {
                        textVariable = it
                        parameters = parameters + ("textVariable" to it)
                    },
                    label = { Text("Text Variable") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        ActionType.LAUNCH_APP -> {
            var packageName by remember { mutableStateOf(parameters["packageName"] ?: "") }
            OutlinedTextField(
                value = packageName,
                onValueChange = {
                    packageName = it
                    parameters = parameters + ("packageName" to it)
                },
                label = { Text("Package Name") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        ActionType.PRESS_KEY -> {
            var keyCode by remember { mutableStateOf(parameters["keyCode"] ?: "") }
            OutlinedTextField(
                value = keyCode,
                onValueChange = {
                    keyCode = it
                    parameters = parameters + ("keyCode" to it)
                },
                label = { Text("Key Code") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        else -> {}
    }
}

@Composable
fun ConditionNodeEditor(
    node: FlowNode.Condition,
    onSave: (FlowNode) -> Unit
) {
    var name by remember { mutableStateOf(node.name) }
    var description by remember { mutableStateOf(node.description) }
    var conditionType by remember { mutableStateOf(node.conditionType) }
    var expression by remember { mutableStateOf(node.expression) }

    LaunchedEffect(name, description, conditionType, expression) {
        onSave(node.copy(
            name = name,
            description = description,
            conditionType = conditionType,
            expression = expression
        ))
    }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Description") },
        modifier = Modifier.fillMaxWidth()
    )

    Text("Condition Type", style = MaterialTheme.typography.labelMedium)
    ConditionType.values().forEach { type ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(type.name)
            RadioButton(
                selected = conditionType == type,
                onClick = { conditionType = type }
            )
        }
    }

    OutlinedTextField(
        value = expression,
        onValueChange = { expression = it },
        label = { Text("Expression") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
}

@Composable
fun LoopNodeEditor(
    node: FlowNode.Loop,
    onSave: (FlowNode) -> Unit
) {
    var name by remember { mutableStateOf(node.name) }
    var description by remember { mutableStateOf(node.description) }
    var loopType by remember { mutableStateOf(node.loopType) }
    var condition by remember { mutableStateOf(node.condition) }
    var maxIterations by remember { mutableStateOf(node.maxIterations.toString()) }

    LaunchedEffect(name, description, loopType, condition, maxIterations) {
        onSave(node.copy(
            name = name,
            description = description,
            loopType = loopType,
            condition = condition,
            maxIterations = maxIterations.toIntOrNull() ?: 100
        ))
    }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Description") },
        modifier = Modifier.fillMaxWidth()
    )

    Text("Loop Type", style = MaterialTheme.typography.labelMedium)
    LoopType.values().forEach { type ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(type.name)
            RadioButton(
                selected = loopType == type,
                onClick = { loopType = type }
            )
        }
    }

    OutlinedTextField(
        value = condition,
        onValueChange = { condition = it },
        label = { Text("Condition") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )

    OutlinedTextField(
        value = maxIterations,
        onValueChange = { maxIterations = it },
        label = { Text("Max Iterations") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun DelayNodeEditor(
    node: FlowNode.Delay,
    onSave: (FlowNode) -> Unit
) {
    var durationMs by remember { mutableStateOf(node.durationMs.toString()) }
    var description by remember { mutableStateOf(node.description) }

    LaunchedEffect(durationMs, description) {
        onSave(node.copy(
            durationMs = durationMs.toLongOrNull() ?: 1000,
            description = description
        ))
    }

    OutlinedTextField(
        value = durationMs,
        onValueChange = { durationMs = it },
        label = { Text("Duration (ms)") },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Description") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun VariableNodeEditor(
    node: FlowNode.Variable,
    onSave: (FlowNode) -> Unit
) {
    var name by remember { mutableStateOf(node.name) }
    var description by remember { mutableStateOf(node.description) }
    var variableName by remember { mutableStateOf(node.variableName) }
    var operation by remember { mutableStateOf(node.operation) }
    var value by remember { mutableStateOf(node.value) }

    LaunchedEffect(name, description, variableName, operation, value) {
        onSave(node.copy(
            name = name,
            description = description,
            variableName = variableName,
            operation = operation,
            value = value
        ))
    }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Description") },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = variableName,
        onValueChange = { variableName = it },
        label = { Text("Variable Name") },
        modifier = Modifier.fillMaxWidth()
    )

    Text("Operation", style = MaterialTheme.typography.labelMedium)
    VariableOperation.values().forEach { op ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(op.name)
            RadioButton(
                selected = operation == op,
                onClick = { operation = op }
            )
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text("Value") },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun getNodeTypeName(node: FlowNode): String {
    return when (node) {
        is FlowNode.Start -> "Start Node"
        is FlowNode.End -> "End Node"
        is FlowNode.Action -> "Action Node"
        is FlowNode.Condition -> "Condition Node"
        is FlowNode.Loop -> "Loop Node"
        is FlowNode.Delay -> "Delay Node"
        is FlowNode.Variable -> "Variable Node"
    }
}
