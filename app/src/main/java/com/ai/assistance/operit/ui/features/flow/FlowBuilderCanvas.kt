package com.ai.assistance.operit.ui.features.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.flow.FlowConnection
import com.ai.assistance.operit.core.flow.FlowNode
import com.ai.assistance.operit.core.flow.FlowScript
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalTextApi::class)
@Composable
fun FlowBuilderCanvas(
    script: FlowScript?,
    viewModel: FlowBuilderViewModel,
    modifier: Modifier = Modifier,
    onNodeClick: (FlowNode) -> Unit = {},
    onNodeLongPress: (FlowNode) -> Unit = {},
    onConnectionClick: (FlowConnection) -> Unit = {},
    onCanvasClick: (Offset) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val colorScheme = MaterialTheme.colorScheme

    val scale by viewModel.scale
    val offset by viewModel.offset
    val draggingNodeId by viewModel.draggingNodeId
    val connectionSource by viewModel.connectionSource

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(scale, offset) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(0.2f, 3f)
                        val newOffset = (offset - centroid).scaleBy(newScale / oldScale) + centroid + pan
                        viewModel.updateZoom(newScale)
                        viewModel.updateOffset(newOffset)
                    }
                }
                .pointerInput(draggingNodeId) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val node = findNodeAtOffset(startOffset, script, viewModel, scale, offset)
                            node?.let { viewModel.startDragging(it.id) }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            draggingNodeId?.let { nodeId ->
                                val currentPos = viewModel.nodePositions[nodeId] ?: return@let
                                val newPos = currentPos + dragAmount.divideBy(scale)
                                viewModel.updateNodePosition(nodeId, newPos)
                            }
                        },
                        onDragEnd = {
                            viewModel.stopDragging()
                        },
                        onDragCancel = {
                            viewModel.stopDragging()
                        }
                    )
                }
                .pointerInput(script, connectionSource) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val node = findNodeAtOffset(tapOffset, script, viewModel, scale, offset)
                            if (node != null) {
                                onNodeClick(node)
                            } else {
                                val connection = findConnectionAtOffset(tapOffset, script, viewModel, scale, offset)
                                if (connection != null) {
                                    onConnectionClick(connection)
                                } else {
                                    onCanvasClick(tapOffset)
                                }
                            }
                        },
                        onLongPress = { pressOffset ->
                            val node = findNodeAtOffset(pressOffset, script, viewModel, scale, offset)
                            if (node != null) {
                                onNodeLongPress(node)
                            }
                        }
                    )
                }
        ) {
            drawGrid(canvasWidth, canvasHeight, scale, offset, colorScheme.outline.copy(alpha = 0.08f))

            script?.connections?.forEach { connection ->
                val fromPos = viewModel.nodePositions[connection.fromNodeId]
                val toPos = viewModel.nodePositions[connection.toNodeId]
                if (fromPos != null && toPos != null) {
                    val start = fromPos.scaleBy(scale) + offset
                    val end = toPos.scaleBy(scale) + offset
                    drawConnection(
                        start = start,
                        end = end,
                        label = connection.label,
                        color = colorScheme.primary,
                        textMeasurer = textMeasurer,
                        colorScheme = colorScheme
                    )
                }
            }

            script?.nodes?.forEach { node ->
                val position = viewModel.nodePositions[node.id]
                if (position != null) {
                    val viewPos = position.scaleBy(scale) + offset
                    val isSelected = node.id in viewModel.selectedNodeIds
                    val isConnectionSource = connectionSource == node.id
                    drawNode(
                        node = node,
                        position = viewPos,
                        scale = scale,
                        textMeasurer = textMeasurer,
                        colorScheme = colorScheme,
                        isSelected = isSelected,
                        isConnectionSource = isConnectionSource
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGrid(
    width: Float,
    height: Float,
    scale: Float,
    offset: Offset,
    color: Color
) {
    val gridSize = (120f * scale).coerceAtLeast(40f)
    val startX = (-offset.x) % gridSize
    val startY = (-offset.y) % gridSize

    var x = startX
    while (x < width) {
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f
        )
        x += gridSize
    }

    var y = startY
    while (y < height) {
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )
        y += gridSize
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawConnection(
    start: Offset,
    end: Offset,
    label: String,
    color: Color,
    textMeasurer: TextMeasurer,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    val angle = atan2(end.y - start.y, end.x - start.x)
    val arrowSize = 16f
    val arrowAngle = Math.PI / 6

    val arrowPoint1 = Offset(
        end.x - arrowSize * cos(angle - arrowAngle).toFloat(),
        end.y - arrowSize * sin(angle - arrowAngle).toFloat()
    )
    val arrowPoint2 = Offset(
        end.x - arrowSize * cos(angle + arrowAngle).toFloat(),
        end.y - arrowSize * sin(angle + arrowAngle).toFloat()
    )

    val arrowPath = Path().apply {
        moveTo(end.x, end.y)
        lineTo(arrowPoint1.x, arrowPoint1.y)
        lineTo(arrowPoint2.x, arrowPoint2.y)
        close()
    }
    drawPath(arrowPath, color)

    if (label.isNotEmpty()) {
        val center = (start + end) / 2f
        val textLayoutResult = textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(
                fontSize = 11.sp,
                color = colorScheme.onSurface,
                background = colorScheme.surface
            )
        )
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                x = center.x - textLayoutResult.size.width / 2,
                y = center.y - textLayoutResult.size.height / 2
            )
        )
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNode(
    node: FlowNode,
    position: Offset,
    scale: Float,
    textMeasurer: TextMeasurer,
    colorScheme: androidx.compose.material3.ColorScheme,
    isSelected: Boolean,
    isConnectionSource: Boolean
) {
    val nodeSize = getNodeSize(node)
    val scaledWidth = nodeSize.width * scale
    val scaledHeight = nodeSize.height * scale

    val (backgroundColor, borderColor) = getNodeColors(node, colorScheme)

    drawRoundRect(
        color = backgroundColor,
        topLeft = Offset(position.x - scaledWidth / 2, position.y - scaledHeight / 2),
        size = Size(scaledWidth, scaledHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f * scale)
    )

    val border = when {
        isConnectionSource -> colorScheme.secondary
        isSelected -> colorScheme.primary
        else -> borderColor
    }

    drawRoundRect(
        color = border,
        topLeft = Offset(position.x - scaledWidth / 2, position.y - scaledHeight / 2),
        size = Size(scaledWidth, scaledHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f * scale),
        style = Stroke(width = if (isSelected || isConnectionSource) 4f else 2f)
    )

    val displayText = getNodeDisplayText(node)
    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(displayText),
        style = TextStyle(
            fontSize = (12 * scale).sp,
            color = colorScheme.onSurface
        )
    )

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(
            x = position.x - textLayoutResult.size.width / 2,
            y = position.y - textLayoutResult.size.height / 2
        )
    )
}

private fun getNodeSize(node: FlowNode): Size {
    return when (node) {
        is FlowNode.Start -> Size(110f, 60f)
        is FlowNode.End -> Size(110f, 60f)
        is FlowNode.Action -> Size(140f, 72f)
        is FlowNode.Condition -> Size(160f, 80f)
        is FlowNode.Loop -> Size(160f, 80f)
        is FlowNode.Delay -> Size(120f, 60f)
        is FlowNode.Variable -> Size(140f, 70f)
    }
}

private fun getNodeColors(
    node: FlowNode,
    colorScheme: androidx.compose.material3.ColorScheme
): Pair<Color, Color> {
    return when (node) {
        is FlowNode.Start -> colorScheme.primaryContainer.copy(alpha = 0.85f) to colorScheme.primary
        is FlowNode.End -> colorScheme.errorContainer.copy(alpha = 0.85f) to colorScheme.error
        is FlowNode.Action -> colorScheme.tertiaryContainer.copy(alpha = 0.85f) to colorScheme.tertiary
        is FlowNode.Condition -> colorScheme.secondaryContainer.copy(alpha = 0.85f) to colorScheme.secondary
        is FlowNode.Loop -> colorScheme.secondaryContainer.copy(alpha = 0.85f) to colorScheme.secondary
        is FlowNode.Delay -> colorScheme.surfaceVariant.copy(alpha = 0.85f) to colorScheme.outline
        is FlowNode.Variable -> colorScheme.tertiaryContainer.copy(alpha = 0.85f) to colorScheme.tertiary
    }
}

private fun getNodeDisplayText(node: FlowNode): String {
    return when (node) {
        is FlowNode.Start -> "Start"
        is FlowNode.End -> "End"
        is FlowNode.Action -> node.name
        is FlowNode.Condition -> node.name
        is FlowNode.Loop -> node.name
        is FlowNode.Delay -> "Delay ${node.durationMs}ms"
        is FlowNode.Variable -> node.variableName
    }
}

private fun findNodeAtOffset(
    offset: Offset,
    script: FlowScript?,
    viewModel: FlowBuilderViewModel,
    scale: Float,
    viewportOffset: Offset
): FlowNode? {
    return script?.nodes?.findLast { node ->
        val pos = viewModel.nodePositions[node.id] ?: return@findLast false
        val viewPos = pos.scaleBy(scale) + viewportOffset
        val size = getNodeSize(node)
        Rect(
            left = viewPos.x - (size.width * scale) / 2,
            top = viewPos.y - (size.height * scale) / 2,
            right = viewPos.x + (size.width * scale) / 2,
            bottom = viewPos.y + (size.height * scale) / 2
        ).contains(offset)
    }
}

private fun findConnectionAtOffset(
    offset: Offset,
    script: FlowScript?,
    viewModel: FlowBuilderViewModel,
    scale: Float,
    viewportOffset: Offset
): FlowConnection? {
    return script?.connections?.find { connection ->
        val fromPos = viewModel.nodePositions[connection.fromNodeId]
        val toPos = viewModel.nodePositions[connection.toNodeId]
        if (fromPos != null && toPos != null) {
            val start = fromPos.scaleBy(scale) + viewportOffset
            val end = toPos.scaleBy(scale) + viewportOffset
            distanceToSegment(offset, start, end) < 24f
        } else false
    }
}

private fun distanceToSegment(p: Offset, start: Offset, end: Offset): Float {
    val l2 = (start - end).getDistanceSquared()
    if (l2 == 0f) return (p - start).getDistance()
    val t = ((p.x - start.x) * (end.x - start.x) + (p.y - start.y) * (end.y - start.y)) / l2
    val tClamped = t.coerceIn(0f, 1f)
    val projection = start + (end - start) * tClamped
    return (p - projection).getDistance()
}

private fun Offset.scaleBy(scale: Float): Offset = Offset(x * scale, y * scale)
private fun Offset.divideBy(scale: Float): Offset = Offset(x / scale, y / scale)
