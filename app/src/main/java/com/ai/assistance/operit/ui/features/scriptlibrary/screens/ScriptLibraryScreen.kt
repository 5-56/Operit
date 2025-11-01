package com.ai.assistance.operit.ui.features.scriptlibrary.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.model.Script
import com.ai.assistance.operit.services.ScriptLibraryService
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.scriptlibrary.viewmodel.*
import com.ai.assistance.operit.ui.features.scriptlibrary.dialogs.ImportScriptDialog
import com.ai.assistance.operit.ui.features.scriptlibrary.dialogs.SafetyWarningDialog
import com.ai.assistance.operit.ui.features.scriptlibrary.dialogs.ScriptDetailsDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptLibraryScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val service = remember { ScriptLibraryService.getInstance(context) }
    val viewModel: ScriptLibraryViewModel = viewModel(
        factory = ScriptLibraryViewModel.Factory(context, service)
    )

    val uiState by viewModel.uiState.collectAsState()
    val filteredScripts by viewModel.filteredScripts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showFavoritesOnly by viewModel.showFavoritesOnly.collectAsState()
    val importDialogState by viewModel.importDialogState.collectAsState()
    val safetyWarningDialog by viewModel.safetyWarningDialog.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var selectedScript by remember { mutableStateOf<Script?>(null) }
    var showScriptDetails by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // File picker for importing scripts
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importFromFile(it) }
    }

    // File picker for bundle import
    val bundlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importBundle(it) }
    }

    // File picker for backup restore
    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.restoreBackup(it, replaceExisting = false) }
    }

    CustomScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Script Library") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavoritesOnly() }) {
                        Icon(
                            if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Toggle Favorites"
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import Script") },
                            onClick = {
                                showMenu = false
                                viewModel.showImportDialog()
                            },
                            leadingIcon = { Icon(Icons.Default.Add, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Create Backup") },
                            onClick = {
                                showMenu = false
                                viewModel.createBackup()
                            },
                            leadingIcon = { Icon(Icons.Default.Backup, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Restore Backup") },
                            onClick = {
                                showMenu = false
                                backupPicker.launch("*/*")
                            },
                            leadingIcon = { Icon(Icons.Default.Restore, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            onClick = {
                                showMenu = false
                                viewModel.refresh()
                            },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search scripts...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true
            )

            // Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Category filter
                FilterChipDropdown(
                    label = selectedCategory ?: "All Categories",
                    items = listOf(null) + categories,
                    selectedItem = selectedCategory,
                    onItemSelected = { viewModel.setSelectedCategory(it) },
                    itemLabel = { it ?: "All Categories" }
                )

                // Tag filter
                FilterChipDropdown(
                    label = selectedTag ?: "All Tags",
                    items = listOf(null) + tags.map { it.name },
                    selectedItem = selectedTag,
                    onItemSelected = { viewModel.setSelectedTag(it) },
                    itemLabel = { it ?: "All Tags" }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Script List
            when (uiState) {
                is ScriptLibraryUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ScriptLibraryUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (uiState as ScriptLibraryUiState.Error).message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    if (filteredScripts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No scripts found",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Import or create a new script to get started",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredScripts, key = { it.id }) { script ->
                                ScriptCard(
                                    script = script,
                                    onClick = {
                                        selectedScript = script
                                        showScriptDetails = true
                                    },
                                    onFavoriteClick = { viewModel.toggleFavorite(script.id) },
                                    onShareClick = { viewModel.shareScript(script.id) },
                                    onExportClick = { viewModel.exportScript(script.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (importDialogState != ImportDialogState.Hidden) {
        ImportScriptDialog(
            state = importDialogState,
            onDismiss = { viewModel.hideImportDialog() },
            onImportFromFile = { filePicker.launch("*/*") },
            onImportFromUrl = { url -> viewModel.importFromUrl(url) },
            onImportBundle = { bundlePicker.launch("*/*") }
        )
    }

    safetyWarningDialog?.let { state ->
        SafetyWarningDialog(
            script = state.script,
            validation = state.validation,
            onConfirm = { viewModel.confirmSaveScript(state.script) },
            onDismiss = { viewModel.dismissSafetyWarning() }
        )
    }

    if (showScriptDetails && selectedScript != null) {
        ScriptDetailsDialog(
            script = selectedScript!!,
            onDismiss = { showScriptDetails = false },
            onDelete = {
                viewModel.deleteScript(selectedScript!!.id)
                showScriptDetails = false
            },
            onExecute = {
                viewModel.recordScriptUsage(selectedScript!!.id)
                // Execute script logic would go here
            },
            onAddTag = { tag -> viewModel.addTagToScript(selectedScript!!, tag) },
            onRemoveTag = { tag -> viewModel.removeTagFromScript(selectedScript!!, tag) }
        )
    }

    // Handle UI state changes
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ScriptLibraryUiState.ExportSuccess -> {
                snackbarHostState.showSnackbar(state.message)
            }
            is ScriptLibraryUiState.BackupSuccess -> {
                snackbarHostState.showSnackbar(state.message)
            }
            is ScriptLibraryUiState.RestoreSuccess -> {
                snackbarHostState.showSnackbar(state.message)
            }
            else -> {}
        }
    }
}

@Composable
fun ScriptCard(
    script: Script,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = script.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (script.description.isNotEmpty()) {
                        Text(
                            text = script.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        if (script.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (script.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tags and metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Chip(
                        label = script.category,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                    Chip(
                        label = script.language,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    )
                    if (!script.isTrusted) {
                        Chip(
                            label = "⚠️ Untrusted",
                            color = MaterialTheme.colorScheme.errorContainer
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Default.Share, "Share", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onExportClick) {
                        Icon(Icons.Default.Download, "Export", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun Chip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)),
        color = color
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun <T> FilterChipDropdown(
    label: String,
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T?) -> Unit,
    itemLabel: (T?) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = selectedItem != null,
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
