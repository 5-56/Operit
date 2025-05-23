package com.ai.assistance.operit.ui.features.packages.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.packages.components.EmptyState
import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.ai.assistance.operit.ui.features.packages.dialogs.PackageDetailsDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.ScriptExecutionDialog
import com.ai.assistance.operit.ui.features.packages.lists.AvailablePackagesList
import com.ai.assistance.operit.ui.features.packages.lists.ImportedPackagesList
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageManagerScreen() {
    val context = LocalContext.current
    val packageManager = remember {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    val scope = rememberCoroutineScope()
    val mcpRepository = remember { MCPRepository(context) }

    // State for available and imported packages
    val availablePackages = remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    val importedPackages = remember { mutableStateOf<List<String>>(emptyList()) }

    // State for selected package and showing details
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    // State for script execution
    var showScriptExecution by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<PackageTool?>(null) }
    var scriptExecutionResult by remember { mutableStateOf<ToolResult?>(null) }

    // State for snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Tab selection state
    var selectedTab by remember { mutableStateOf(PackageTab.AVAILABLE) }

    // File picker launcher for importing external packages
    val packageFilePicker =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri
                ->
                uri?.let {
                    scope.launch {
                        try {
                            // Convert URI to file path - this is a simplified approach
                            val cursor = context.contentResolver.query(uri, null, null, null, null)
                            cursor?.use {
                                val nameIndex = it.getColumnIndex("_display_name")
                                if (it.moveToFirst() && nameIndex >= 0) {
                                    val fileName = it.getString(nameIndex)
                                    if (!fileName.endsWith(".hjson")) {
                                        snackbarHostState.showSnackbar(message = "只支持.hjson文件")
                                        return@launch
                                    }
                                }
                            }

                            // Copy the file to a temporary location
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val tempFile = File(context.cacheDir, "temp_package.hjson")

                            inputStream?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }

                            // Import the package from the temporary file
                            val result =
                                    packageManager.importPackageFromExternalStorage(
                                            tempFile.absolutePath
                                    )

                            // Refresh the lists
                            availablePackages.value = packageManager.getAvailablePackages()
                            importedPackages.value = packageManager.getImportedPackages()

                            snackbarHostState.showSnackbar(message = "外部包导入成功")

                            // Clean up the temporary file
                            tempFile.delete()
                        } catch (e: Exception) {
                            Log.e("PackageManagerScreen", "Failed to import external package", e)
                            snackbarHostState.showSnackbar(message = "外部包导入失败: ${e.message}")
                        }
                    }
                }
            }

    // Load packages
    LaunchedEffect(Unit) {
        try {
            availablePackages.value = packageManager.getAvailablePackages()
            importedPackages.value = packageManager.getImportedPackages()
        } catch (e: Exception) {
            Log.e("PackageManagerScreen", "Failed to load packages", e)
        }
    }

    Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (selectedTab != PackageTab.MCP_MARKETPLACE &&
                                selectedTab != PackageTab.MCP_CONFIG
                ) {
                    FloatingActionButton(
                            onClick = { packageFilePicker.launch("*/*") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = "导入外部包",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
    ) { paddingValues ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 优化标签栏布局 - 使用可滚动标签并减小文本大小
            ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 0.dp,
                    divider = {
                        Divider(
                                thickness = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    },
                    indicator = { tabPositions ->
                        if (selectedTab.ordinal < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                    modifier =
                                            Modifier.tabIndicatorOffset(
                                                    tabPositions[selectedTab.ordinal]
                                            ),
                                    height = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
            ) {
                // 可用包标签
                Tab(
                        selected = selectedTab == PackageTab.AVAILABLE,
                        onClick = { selectedTab = PackageTab.AVAILABLE },
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                        text = {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Extension,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                        "可用包",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                )

                // 已导入包标签
                Tab(
                        selected = selectedTab == PackageTab.IMPORTED,
                        onClick = { selectedTab = PackageTab.IMPORTED },
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                        text = {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                        "已导入",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                )

                // MCP插件市场标签
                Tab(
                        selected = selectedTab == PackageTab.MCP_MARKETPLACE,
                        onClick = { selectedTab = PackageTab.MCP_MARKETPLACE },
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                        text = {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                        "插件市场",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                )

                // MCP配置标签
                Tab(
                        selected = selectedTab == PackageTab.MCP_CONFIG,
                        onClick = { selectedTab = PackageTab.MCP_CONFIG },
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                        text = {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                        "MCP配置",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 内容区域
            when (selectedTab) {
                PackageTab.AVAILABLE -> {
                    // 可用包列表
                    if (availablePackages.value.isEmpty()) {
                        EmptyState(message = "没有可用的包")
                    } else {
                        AvailablePackagesList(
                                packages = availablePackages.value,
                                onPackageClick = { packageName ->
                                    selectedPackage = packageName
                                    showDetails = true
                                },
                                onImportClick = { packageName ->
                                    scope.launch {
                                        try {
                                            val result = packageManager.importPackage(packageName)
                                            importedPackages.value =
                                                    packageManager.getImportedPackages()
                                            snackbarHostState.showSnackbar(message = "包导入成功")
                                        } catch (e: Exception) {
                                            Log.e(
                                                    "PackageManagerScreen",
                                                    "Failed to import package",
                                                    e
                                            )
                                            snackbarHostState.showSnackbar(message = "包导入错误")
                                        }
                                    }
                                }
                        )
                    }
                }
                PackageTab.IMPORTED -> {
                    // 已导入包列表
                    if (importedPackages.value.isEmpty()) {
                        EmptyState(message = "尚未导入任何包")
                    } else {
                        ImportedPackagesList(
                                packages = importedPackages.value,
                                availablePackages = availablePackages.value,
                                onPackageClick = { packageName ->
                                    selectedPackage = packageName
                                    showDetails = true
                                },
                                onRemoveClick = { packageName ->
                                    scope.launch {
                                        try {
                                            packageManager.removePackage(packageName)
                                            importedPackages.value =
                                                    packageManager.getImportedPackages()
                                            snackbarHostState.showSnackbar(message = "包已移除")
                                        } catch (e: Exception) {
                                            Log.e(
                                                    "PackageManagerScreen",
                                                    "Failed to remove package",
                                                    e
                                            )
                                        }
                                    }
                                }
                        )
                    }
                }
                PackageTab.MCP_MARKETPLACE -> {
                    // MCP插件市场界面
                    MCPScreen(mcpRepository = mcpRepository)
                }
                PackageTab.MCP_CONFIG -> {
                    // MCP配置界面
                    MCPConfigScreen()
                }
            }

            // Package Details Dialog
            if (showDetails && selectedPackage != null) {
                PackageDetailsDialog(
                        packageName = selectedPackage!!,
                        packageDescription = availablePackages.value[selectedPackage]?.description
                                        ?: "",
                        packageManager = packageManager,
                        onRunScript = { tool ->
                            selectedTool = tool
                            showScriptExecution = true
                        },
                        onDismiss = { showDetails = false }
                )
            }

            // Script Execution Dialog
            if (showScriptExecution && selectedTool != null && selectedPackage != null) {
                ScriptExecutionDialog(
                        packageName = selectedPackage!!,
                        tool = selectedTool!!,
                        packageManager = packageManager,
                        initialResult = scriptExecutionResult,
                        onExecuted = { result -> scriptExecutionResult = result },
                        onDismiss = {
                            showScriptExecution = false
                            scriptExecutionResult = null
                        }
                )
            }
        }
    }
}
