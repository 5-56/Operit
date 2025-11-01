package com.ai.assistance.operit.ui.features.scriptlibrary.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.scriptlibrary.viewmodel.ImportDialogState

@Composable
fun ImportScriptDialog(
    state: ImportDialogState,
    onDismiss: () -> Unit,
    onImportFromFile: () -> Unit,
    onImportFromUrl: (String) -> Unit,
    onImportBundle: () -> Unit
) {
    when (state) {
        ImportDialogState.Hidden -> Unit
        ImportDialogState.SelectSource -> ImportScriptSelectionDialog(
            onDismiss = onDismiss,
            onImportFromFile = onImportFromFile,
            onImportFromUrl = onImportFromUrl,
            onImportBundle = onImportBundle
        )
        ImportDialogState.Importing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing Script") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Please wait while the script is imported...")
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
        is ImportDialogState.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Import Successful") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        )
        is ImportDialogState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Import Failed") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ImportScriptSelectionDialog(
    onDismiss: () -> Unit,
    onImportFromFile: () -> Unit,
    onImportFromUrl: (String) -> Unit,
    onImportBundle: () -> Unit
) {
    val urlState = remember { mutableStateOf("") }
    val urlError = remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Code, contentDescription = null) },
        title = { Text("Import Script", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Choose how you'd like to import scripts into your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onImportFromFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import from File")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onImportBundle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Bundle")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Import from URL",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = urlState.value,
                    onValueChange = {
                        urlState.value = it
                        urlError.value = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/script.operit-script") },
                    singleLine = true,
                    isError = urlError.value != null,
                    supportingText = {
                        urlError.value?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val url = urlState.value.trim()
                        if (url.isEmpty()) {
                            urlError.value = "Please enter a script URL"
                        } else {
                            onImportFromUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = urlState.value.isNotBlank()
                ) {
                    Text("Import from URL")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
