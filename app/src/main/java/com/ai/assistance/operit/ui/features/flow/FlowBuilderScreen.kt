package com.ai.assistance.operit.ui.features.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.core.flow.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowBuilderScreen(
    onBackPressed: () -> Unit = {},
    viewModel: FlowBuilderViewModel = viewModel()
) {
    val script by viewModel.currentScript
    var showNodePalette by remember { mutableStateOf(false) }
    var showTutorial by viewModel.showTutorial
    var selectedNode by remember { mutableStateOf<FlowNode?>(null) }
    var showNodeEditor by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (script == null) {
            viewModel.createNewFlow("Untitled Flow")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(script?.name ?: "Flow Builder") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Upload, "Import")
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Download, "Export")
                    }
                    IconButton(onClick = { showTutorial = true }) {
                        Icon(Icons.Default.Help, "Help")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!showNodePalette) {
                FloatingActionButton(
                    onClick = { showNodePalette = true }
                ) {
                    Icon(Icons.Default.Add, "Add Node")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main canvas
            FlowBuilderCanvas(
                script = script,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                onNodeClick = { node ->
                    selectedNode = node
                    showNodeEditor = true
                }
            )

            // Zoom controls overlay
            ZoomControls(
                scale = viewModel.scale.value,
                onZoomIn = { viewModel.updateZoom(viewModel.scale.value * 1.2f) },
                onZoomOut = { viewModel.updateZoom(viewModel.scale.value / 1.2f) },
                onResetZoom = { viewModel.updateZoom(1f) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )

            // Node palette
            if (showNodePalette) {
                NodePalette(
                    onNodeTypeSelected = { nodeType ->
                        addNodeAtCenter(nodeType, viewModel)
                        showNodePalette = false
                    },
                    onDismiss = { showNodePalette = false },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            // Tutorial overlay
            if (showTutorial) {
                TutorialOverlay(
                    onDismiss = { viewModel.dismissTutorial() }
                )
            }
        }

        // Node editor dialog
        if (showNodeEditor && selectedNode != null) {
            NodeEditorDialog(
                node = selectedNode!!,
                onDismiss = { showNodeEditor = false },
                onSave = { updatedNode ->
                    viewModel.updateNode(updatedNode)
                    showNodeEditor = false
                },
                onDelete = {
                    viewModel.removeNode(selectedNode!!.id)
                    showNodeEditor = false
                }
            )
        }

        // Export dialog
        if (showExportDialog) {
            ExportDialog(
                json = viewModel.exportToJson(),
                onDismiss = { showExportDialog = false }
            )
        }

        // Import dialog
        if (showImportDialog) {
            ImportDialog(
                onImport = { json ->
                    viewModel.importFromJson(json)
                    showImportDialog = false
                },
                onDismiss = { showImportDialog = false }
            )
        }
    }
}

@Composable
fun ZoomControls(
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onZoomIn) {
                Icon(Icons.Default.ZoomIn, "Zoom In")
            }
            Text(
                text = "${(scale * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            IconButton(onClick = onZoomOut) {
                Icon(Icons.Default.ZoomOut, "Zoom Out")
            }
            Divider()
            IconButton(onClick = onResetZoom) {
                Icon(Icons.Default.CenterFocusWeak, "Reset")
            }
        }
    }
}

@Composable
fun NodePalette(
    onNodeTypeSelected: (NodeType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(16.dp)
            .width(200.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Add Node",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            NodeTypeButton(
                icon = Icons.Default.PlayArrow,
                label = "Action",
                onClick = { onNodeTypeSelected(NodeType.ACTION) }
            )
            NodeTypeButton(
                icon = Icons.Default.QuestionMark,
                label = "Condition",
                onClick = { onNodeTypeSelected(NodeType.CONDITION) }
            )
            NodeTypeButton(
                icon = Icons.Default.Loop,
                label = "Loop",
                onClick = { onNodeTypeSelected(NodeType.LOOP) }
            )
            NodeTypeButton(
                icon = Icons.Default.Timer,
                label = "Delay",
                onClick = { onNodeTypeSelected(NodeType.DELAY) }
            )
            NodeTypeButton(
                icon = Icons.Default.Code,
                label = "Variable",
                onClick = { onNodeTypeSelected(NodeType.VARIABLE) }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun NodeTypeButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialOverlay(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Flow Builder Tutorial") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    TutorialItem(
                        icon = Icons.Default.TouchApp,
                        title = "Pan & Zoom",
                        description = "Use two fingers to pan and pinch to zoom the canvas"
                    )
                }
                item {
                    TutorialItem(
                        icon = Icons.Default.Add,
                        title = "Add Nodes",
                        description = "Tap the + button to add new nodes to your flow"
                    )
                }
                item {
                    TutorialItem(
                        icon = Icons.Default.DragIndicator,
                        title = "Move Nodes",
                        description = "Drag nodes to reposition them on the canvas"
                    )
                }
                item {
                    TutorialItem(
                        icon = Icons.Default.Link,
                        title = "Connect Nodes",
                        description = "Tap a node, then tap another to create a connection"
                    )
                }
                item {
                    TutorialItem(
                        icon = Icons.Default.Edit,
                        title = "Edit Nodes",
                        description = "Tap a node to edit its properties"
                    )
                }
                item {
                    TutorialItem(
                        icon = Icons.Default.Download,
                        title = "Export/Import",
                        description = "Save your flow as JSON or import existing flows"
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it!")
            }
        }
    )
}

@Composable
fun TutorialItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    json: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Flow") },
        text = {
            Column {
                Text("Flow exported as JSON:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = json,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var json by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Flow") },
        text = {
            Column {
                Text("Paste JSON flow definition:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = json,
                    onValueChange = { json = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    placeholder = { Text("Paste JSON here...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(json) },
                enabled = json.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class NodeType {
    ACTION, CONDITION, LOOP, DELAY, VARIABLE
}

private fun addNodeAtCenter(
    nodeType: NodeType,
    viewModel: FlowBuilderViewModel
) {
    val centerX = 400f
    val centerY = 300f

    val node = when (nodeType) {
        NodeType.ACTION -> FlowNode.Action(
            name = "New Action",
            position = NodePosition(centerX, centerY),
            actionType = ActionType.CLICK
        )
        NodeType.CONDITION -> FlowNode.Condition(
            name = "New Condition",
            position = NodePosition(centerX, centerY),
            conditionType = ConditionType.ELEMENT_EXISTS,
            expression = ""
        )
        NodeType.LOOP -> FlowNode.Loop(
            name = "New Loop",
            position = NodePosition(centerX, centerY),
            loopType = LoopType.WHILE,
            condition = ""
        )
        NodeType.DELAY -> FlowNode.Delay(
            position = NodePosition(centerX, centerY),
            durationMs = 1000
        )
        NodeType.VARIABLE -> FlowNode.Variable(
            name = "New Variable",
            position = NodePosition(centerX, centerY),
            variableName = "var",
            operation = VariableOperation.SET,
            value = ""
        )
    }

    viewModel.addNode(node)
}
