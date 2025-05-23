package com.ai.assistance.operit.ui.features.packages.dialogs

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.packTool.PackageManager

@Composable
fun PackageDetailsDialog(
        packageName: String,
        packageDescription: String,
        packageManager: PackageManager,
        onRunScript: (PackageTool) -> Unit,
        onDismiss: () -> Unit
) {
    // Load the package details
    val toolPackage =
            remember(packageName) {
                try {
                    val packages = packageManager.getPackageTools(packageName)
                    packages
                } catch (e: Exception) {
                    Log.e("PackageDetailsDialog", "Failed to load package details", e)
                    null
                }
            }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Package Details") },
            text = {
                Column {
                    Text(
                            text = packageName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = packageDescription, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                            text = "Package Tools",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (toolPackage?.tools == null || toolPackage.tools.isEmpty()) {
                        Text(
                                text = "No tools found in this package",
                                style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                                modifier = Modifier.height(250.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items = toolPackage.tools, key = { tool -> tool.name }) { tool ->
                                Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = tool.name,
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                        text = tool.description,
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                            }

                                            // Add Run Script button
                                            IconButton(onClick = { onRunScript(tool) }) {
                                                Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Run Script",
                                                        tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        if (tool.parameters.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                    text = "Script Parameters:" + ":",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                            )
                                            for (param in tool.parameters) {
                                                val requiredText =
                                                        if (param.required) "(required)"
                                                        else "(optional)"
                                                Text(
                                                        text =
                                                                "• ${param.name} $requiredText: ${param.description}",
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } }
    )
}
