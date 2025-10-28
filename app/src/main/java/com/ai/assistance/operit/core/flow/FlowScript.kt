package com.ai.assistance.operit.core.flow

import com.ai.assistance.operit.core.tools.automatic.UIOperation
import com.ai.assistance.operit.core.tools.automatic.UISelector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * DSL for representing automation flow scripts.
 * Provides a structured way to define automation flows with nodes and connections.
 */
@Serializable
data class FlowScript(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val nodes: List<FlowNode>,
    val connections: List<FlowConnection>,
    val variables: Map<String, String> = emptyMap(),
    val metadata: FlowMetadata = FlowMetadata()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
        }

        fun fromJson(jsonString: String): FlowScript {
            return json.decodeFromString(jsonString)
        }

        fun toJson(script: FlowScript): String {
            return json.encodeToString(serializer(), script)
        }
    }
}

@Serializable
data class FlowMetadata(
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val version: String = "1.0.0",
    val author: String = ""
)

/**
 * Represents a node in the flow graph.
 */
@Serializable
sealed class FlowNode {
    abstract val id: String
    abstract val name: String
    abstract val position: NodePosition
    abstract val description: String

    @Serializable
    data class Start(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "Start",
        override val position: NodePosition = NodePosition(0f, 0f),
        override val description: String = "Flow start point"
    ) : FlowNode()

    @Serializable
    data class End(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "End",
        override val position: NodePosition = NodePosition(0f, 0f),
        override val description: String = "Flow end point"
    ) : FlowNode()

    @Serializable
    data class Action(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val position: NodePosition = NodePosition(0f, 0f),
        override val description: String = "",
        val actionType: ActionType,
        val parameters: Map<String, String> = emptyMap()
    ) : FlowNode()

    @Serializable
    data class Condition(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val position: NodePosition = NodePosition(0f, 0f),
        override val description: String = "",
        val conditionType: ConditionType,
        val expression: String
    ) : FlowNode()

    @Serializable
    data class Loop(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val position: NodePosition = NodePosition(0f, 0f),
        override val description: String = "",
        val loopType: LoopType,
        val condition: String,
        val maxIterations: Int = 100
    ) : FlowNode()

    @Serializable
    data class Delay(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "Delay",
        override val position: NodePosition = NodePosition(0f, 0f),
        override val description: String = "",
        val durationMs: Long
    ) : FlowNode()

    @Serializable
    data class Variable(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val position: NodePosition = NodePosition(0f, 0f),
        override val description: String = "",
        val variableName: String,
        val operation: VariableOperation,
        val value: String
    ) : FlowNode()
}

@Serializable
data class NodePosition(
    val x: Float,
    val y: Float
)

/**
 * Represents a connection between two nodes.
 */
@Serializable
data class FlowConnection(
    val id: String = UUID.randomUUID().toString(),
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = "",
    val conditionResult: Boolean? = null // For condition nodes: true/false branches
)

@Serializable
enum class ActionType {
    CLICK,
    INPUT,
    SWIPE,
    LAUNCH_APP,
    KILL_APP,
    PRESS_KEY,
    WAIT_FOR_ELEMENT,
    SCREENSHOT,
    CUSTOM
}

@Serializable
enum class ConditionType {
    ELEMENT_EXISTS,
    ELEMENT_NOT_EXISTS,
    TEXT_EQUALS,
    TEXT_CONTAINS,
    VARIABLE_EQUALS,
    CUSTOM_EXPRESSION
}

@Serializable
enum class LoopType {
    WHILE,
    FOR,
    FOREACH,
    UNTIL
}

@Serializable
enum class VariableOperation {
    SET,
    INCREMENT,
    DECREMENT,
    APPEND,
    CLEAR
}

/**
 * Converter between FlowScript DSL and UIOperation execution model.
 */
object FlowScriptConverter {
    /**
     * Convert a FlowScript to a list of UIOperations for execution.
     */
    fun toUIOperations(script: FlowScript): List<UIOperation> {
        val operations = mutableListOf<UIOperation>()
        val nodeMap = script.nodes.associateBy { it.id }
        val connectionsBySource = script.connections.groupBy { it.fromNodeId }

        // Find start node
        val startNode = script.nodes.firstOrNull { it is FlowNode.Start }
            ?: return emptyList()

        // Traverse the flow graph and convert to operations
        traverseAndConvert(startNode, nodeMap, connectionsBySource, operations, script.variables)

        return operations
    }

    private fun traverseAndConvert(
        node: FlowNode,
        nodeMap: Map<String, FlowNode>,
        connectionsBySource: Map<String, List<FlowConnection>>,
        operations: MutableList<UIOperation>,
        variables: Map<String, String>,
        visited: MutableSet<String> = mutableSetOf()
    ) {
        if (node.id in visited) return // Prevent infinite loops
        visited.add(node.id)

        // Convert current node to operation
        when (node) {
            is FlowNode.Start -> {
                // Start node doesn't generate an operation
            }
            is FlowNode.End -> {
                // End node doesn't generate an operation
                return
            }
            is FlowNode.Action -> {
                val operation = convertActionNode(node, variables)
                operation?.let { operations.add(it) }
            }
            is FlowNode.Delay -> {
                operations.add(UIOperation.Wait(node.durationMs, node.description))
            }
            is FlowNode.Condition -> {
                // For simplicity, we'll execute the true branch
                // In a real implementation, this would need runtime evaluation
            }
            is FlowNode.Loop -> {
                // Loop handling would require more complex state management
                // For now, we'll just follow the first connection
            }
            is FlowNode.Variable -> {
                // Variable operations don't directly map to UI operations
            }
        }

        // Follow connections to next nodes
        val connections = connectionsBySource[node.id] ?: emptyList()
        for (connection in connections) {
            val nextNode = nodeMap[connection.toNodeId]
            if (nextNode != null) {
                traverseAndConvert(nextNode, nodeMap, connectionsBySource, operations, variables, visited)
            }
        }
    }

    private fun convertActionNode(node: FlowNode.Action, variables: Map<String, String>): UIOperation? {
        return when (node.actionType) {
            ActionType.CLICK -> {
                val selectorType = node.parameters["selectorType"] ?: "ByText"
                val selectorValue = node.parameters["selectorValue"] ?: return null
                val selector = createSelector(selectorType, selectorValue)
                UIOperation.Click(
                    selector = selector,
                    description = node.description.ifEmpty { node.name }
                )
            }
            ActionType.INPUT -> {
                val selectorType = node.parameters["selectorType"] ?: "ByClassName"
                val selectorValue = node.parameters["selectorValue"] ?: return null
                val textVariable = node.parameters["textVariable"] ?: "input_text"
                val selector = createSelector(selectorType, selectorValue)
                UIOperation.Input(
                    selector = selector,
                    textVariableKey = textVariable,
                    description = node.description.ifEmpty { node.name }
                )
            }
            ActionType.LAUNCH_APP -> {
                val packageName = node.parameters["packageName"] ?: return null
                UIOperation.LaunchApp(
                    packageName = packageName,
                    description = node.description.ifEmpty { node.name }
                )
            }
            ActionType.PRESS_KEY -> {
                val keyCode = node.parameters["keyCode"] ?: return null
                UIOperation.PressKey(
                    keyCode = keyCode,
                    description = node.description.ifEmpty { node.name }
                )
            }
            ActionType.WAIT_FOR_ELEMENT -> {
                val timeout = node.parameters["timeout"]?.toLongOrNull() ?: 5000L
                UIOperation.WaitForPage(
                    timeoutMs = timeout,
                    description = node.description.ifEmpty { node.name }
                )
            }
            else -> null
        }
    }

    private fun createSelector(type: String, value: String): UISelector {
        return when (type) {
            "ByText" -> UISelector.ByText(value)
            "ByResourceId" -> UISelector.ByResourceId(value)
            "ByContentDesc" -> UISelector.ByContentDesc(value)
            "ByClassName" -> UISelector.ByClassName(value)
            "ByXPath" -> UISelector.ByXPath(value)
            else -> UISelector.ByText(value)
        }
    }

    /**
     * Convert UIOperations to a FlowScript.
     */
    fun fromUIOperations(operations: List<UIOperation>, name: String = "Generated Flow"): FlowScript {
        val nodes = mutableListOf<FlowNode>()
        val connections = mutableListOf<FlowConnection>()

        // Add start node
        val startNode = FlowNode.Start(position = NodePosition(100f, 100f))
        nodes.add(startNode)

        var previousNodeId = startNode.id
        var yOffset = 100f

        operations.forEach { operation ->
            yOffset += 150f
            val node = convertOperationToNode(operation, NodePosition(100f, yOffset))
            nodes.add(node)

            // Connect to previous node
            connections.add(FlowConnection(
                fromNodeId = previousNodeId,
                toNodeId = node.id
            ))
            previousNodeId = node.id
        }

        // Add end node
        yOffset += 150f
        val endNode = FlowNode.End(position = NodePosition(100f, yOffset))
        nodes.add(endNode)
        connections.add(FlowConnection(
            fromNodeId = previousNodeId,
            toNodeId = endNode.id
        ))

        return FlowScript(
            name = name,
            nodes = nodes,
            connections = connections
        )
    }

    private fun convertOperationToNode(operation: UIOperation, position: NodePosition): FlowNode {
        return when (operation) {
            is UIOperation.Click -> FlowNode.Action(
                name = "Click",
                position = position,
                description = operation.description,
                actionType = ActionType.CLICK,
                parameters = mapOf(
                    "selectorType" to getSelectorType(operation.selector),
                    "selectorValue" to getSelectorValue(operation.selector)
                )
            )
            is UIOperation.Input -> FlowNode.Action(
                name = "Input",
                position = position,
                description = operation.description,
                actionType = ActionType.INPUT,
                parameters = mapOf(
                    "selectorType" to getSelectorType(operation.selector),
                    "selectorValue" to getSelectorValue(operation.selector),
                    "textVariable" to operation.textVariableKey
                )
            )
            is UIOperation.Wait -> FlowNode.Delay(
                name = "Wait",
                position = position,
                description = operation.description,
                durationMs = operation.durationMs
            )
            is UIOperation.LaunchApp -> FlowNode.Action(
                name = "Launch App",
                position = position,
                description = operation.description,
                actionType = ActionType.LAUNCH_APP,
                parameters = mapOf("packageName" to operation.packageName)
            )
            is UIOperation.PressKey -> FlowNode.Action(
                name = "Press Key",
                position = position,
                description = operation.description,
                actionType = ActionType.PRESS_KEY,
                parameters = mapOf("keyCode" to operation.keyCode)
            )
            else -> FlowNode.Action(
                name = "Custom Action",
                position = position,
                description = operation.description,
                actionType = ActionType.CUSTOM
            )
        }
    }

    private fun getSelectorType(selector: UISelector): String {
        return when (selector) {
            is UISelector.ByText -> "ByText"
            is UISelector.ByResourceId -> "ByResourceId"
            is UISelector.ByContentDesc -> "ByContentDesc"
            is UISelector.ByClassName -> "ByClassName"
            is UISelector.ByXPath -> "ByXPath"
            is UISelector.ByBounds -> "ByBounds"
            is UISelector.Compound -> "Compound"
        }
    }

    private fun getSelectorValue(selector: UISelector): String {
        return when (selector) {
            is UISelector.ByText -> selector.text
            is UISelector.ByResourceId -> selector.id
            is UISelector.ByContentDesc -> selector.desc
            is UISelector.ByClassName -> selector.name
            is UISelector.ByXPath -> selector.xpath
            is UISelector.ByBounds -> selector.bounds
            is UISelector.Compound -> selector.selectors.joinToString(",") { getSelectorValue(it) }
        }
    }
}
