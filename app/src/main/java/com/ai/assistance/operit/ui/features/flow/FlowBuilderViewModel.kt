package com.ai.assistance.operit.ui.features.flow

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.flow.*
import kotlinx.coroutines.launch

class FlowBuilderViewModel : ViewModel() {
    // Current flow script
    var currentScript = mutableStateOf<FlowScript?>(null)
        private set

    // Node positions for visual rendering
    val nodePositions = mutableStateMapOf<String, androidx.compose.ui.geometry.Offset>()

    // Selected nodes
    val selectedNodeIds = mutableStateListOf<String>()

    // Connection being created
    var connectionSource = mutableStateOf<String?>(null)
        private set

    // Zoom and pan state
    var scale = mutableStateOf(1f)
        private set
    var offset = mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
        private set

    // Dragging state
    var draggingNodeId = mutableStateOf<String?>(null)
        private set

    // Tooltip state
    var showTutorial = mutableStateOf(true)
        private set

    fun createNewFlow(name: String) {
        val startNode = FlowNode.Start(
            position = NodePosition(200f, 100f)
        )
        val endNode = FlowNode.End(
            position = NodePosition(200f, 500f)
        )
        
        currentScript.value = FlowScript(
            name = name,
            nodes = listOf(startNode, endNode),
            connections = emptyList()
        )

        syncNodePositions()
    }

    fun loadFlow(script: FlowScript) {
        currentScript.value = script
        syncNodePositions()
    }

    fun addNode(node: FlowNode) {
        val script = currentScript.value ?: return
        currentScript.value = script.copy(
            nodes = script.nodes + node
        )
        nodePositions[node.id] = androidx.compose.ui.geometry.Offset(node.position.x, node.position.y)
    }

    fun removeNode(nodeId: String) {
        val script = currentScript.value ?: return
        // Remove node
        val updatedNodes = script.nodes.filter { it.id != nodeId }
        // Remove connections to/from this node
        val updatedConnections = script.connections.filter {
            it.fromNodeId != nodeId && it.toNodeId != nodeId
        }
        currentScript.value = script.copy(
            nodes = updatedNodes,
            connections = updatedConnections
        )
        nodePositions.remove(nodeId)
        selectedNodeIds.remove(nodeId)
    }

    fun updateNodePosition(nodeId: String, position: androidx.compose.ui.geometry.Offset) {
        val script = currentScript.value ?: return
        val updatedNodes = script.nodes.map { node ->
            if (node.id == nodeId) {
                when (node) {
                    is FlowNode.Start -> node.copy(position = NodePosition(position.x, position.y))
                    is FlowNode.End -> node.copy(position = NodePosition(position.x, position.y))
                    is FlowNode.Action -> node.copy(position = NodePosition(position.x, position.y))
                    is FlowNode.Condition -> node.copy(position = NodePosition(position.x, position.y))
                    is FlowNode.Loop -> node.copy(position = NodePosition(position.x, position.y))
                    is FlowNode.Delay -> node.copy(position = NodePosition(position.x, position.y))
                    is FlowNode.Variable -> node.copy(position = NodePosition(position.x, position.y))
                }
            } else {
                node
            }
        }
        currentScript.value = script.copy(nodes = updatedNodes)
        nodePositions[nodeId] = position
    }

    fun startConnection(nodeId: String) {
        connectionSource.value = nodeId
    }

    fun completeConnection(targetNodeId: String, label: String = "", conditionResult: Boolean? = null) {
        val sourceId = connectionSource.value ?: return
        val script = currentScript.value ?: return

        if (sourceId != targetNodeId) {
            val connection = FlowConnection(
                fromNodeId = sourceId,
                toNodeId = targetNodeId,
                label = label,
                conditionResult = conditionResult
            )
            currentScript.value = script.copy(
                connections = script.connections + connection
            )
        }
        connectionSource.value = null
    }

    fun cancelConnection() {
        connectionSource.value = null
    }

    fun removeConnection(connectionId: String) {
        val script = currentScript.value ?: return
        currentScript.value = script.copy(
            connections = script.connections.filter { it.id != connectionId }
        )
    }

    fun selectNode(nodeId: String, multiSelect: Boolean = false) {
        if (multiSelect) {
            if (nodeId in selectedNodeIds) {
                selectedNodeIds.remove(nodeId)
            } else {
                selectedNodeIds.add(nodeId)
            }
        } else {
            selectedNodeIds.clear()
            selectedNodeIds.add(nodeId)
        }
    }

    fun clearSelection() {
        selectedNodeIds.clear()
    }

    fun updateZoom(newScale: Float) {
        scale.value = newScale.coerceIn(0.2f, 3f)
    }

    fun updateOffset(newOffset: androidx.compose.ui.geometry.Offset) {
        offset.value = newOffset
    }

    fun startDragging(nodeId: String) {
        draggingNodeId.value = nodeId
    }

    fun stopDragging() {
        draggingNodeId.value = null
    }

    fun dismissTutorial() {
        showTutorial.value = false
    }

    fun exportToJson(): String {
        val script = currentScript.value ?: return ""
        return FlowScript.toJson(script)
    }

    fun importFromJson(json: String) {
        try {
            val script = FlowScript.fromJson(json)
            loadFlow(script)
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun convertToUIOperations() = viewModelScope.launch {
        val script = currentScript.value ?: return@launch
        FlowScriptConverter.toUIOperations(script)
    }

    private fun syncNodePositions() {
        val script = currentScript.value ?: return
        nodePositions.clear()
        script.nodes.forEach { node ->
            nodePositions[node.id] = androidx.compose.ui.geometry.Offset(node.position.x, node.position.y)
        }
    }

    fun updateNode(updatedNode: FlowNode) {
        val script = currentScript.value ?: return
        val updatedNodes = script.nodes.map { node ->
            if (node.id == updatedNode.id) updatedNode else node
        }
        currentScript.value = script.copy(nodes = updatedNodes)
    }
}
