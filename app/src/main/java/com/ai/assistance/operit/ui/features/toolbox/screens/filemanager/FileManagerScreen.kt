package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import NewFolderDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.DisplayMode
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel

/** 文件管理器屏幕 */
@Composable
fun FileManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel = remember { FileManagerViewModel(context) }
    val toolHandler = AIToolHandler.getInstance(context)

    // 为当前目录创建LazyListState
    val listState = rememberLazyListState()

    // 当前可见的第一个项目的索引
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // 文件列表项处理函数
    val onItemClick: (FileItem) -> Unit = { file ->
        if (viewModel.isMultiSelectMode) {
            // 多选模式下，切换文件选择状态
            if (viewModel.selectedFiles.contains(file)) {
                viewModel.selectedFiles.remove(file)
            } else {
                viewModel.selectedFiles.add(file)
            }
        } else {
            // 单选模式下，如果是目录则导航到该目录，否则选中文件
            if (file.isDirectory) {
                viewModel.navigateToDirectory(file)
            } else {
                viewModel.selectedFile = file
            }
        }
    }

    val onItemLongClick: (FileItem) -> Unit = { file ->
        if (viewModel.isMultiSelectMode) {
            if (viewModel.selectedFiles.contains(file)) {
                viewModel.showBottomActionMenu = true
            } else {
                viewModel.selectedFiles.add(file)
            }
        } else {
            viewModel.contextMenuFile = file
            viewModel.showBottomActionMenu = true
        }
    }

    // 监听滚动位置变化，保存到scrollPositions
    LaunchedEffect(firstVisibleItemIndex) {
        if (viewModel.files.isNotEmpty() && viewModel.pendingScrollPosition == null) {
            viewModel.scrollPositions[viewModel.currentPath] = firstVisibleItemIndex
        }
    }

    // 加载当前目录内容
    LaunchedEffect(viewModel.currentPath) { 
        val currentPath = viewModel.currentPath
        viewModel.loadCurrentDirectory(currentPath)
    }

    // 主界面
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部工具栏
            FileManagerToolbar(
                    currentPath = viewModel.currentPath,
                    onNavigateUp = { viewModel.navigateUp() },
                    onRefresh = { viewModel.loadCurrentDirectory() },
                    onZoomIn = { canZoom: Boolean ->
                        if (canZoom && viewModel.itemSize < viewModel.maxItemSize) {
                            viewModel.itemSize += viewModel.itemSizeStep
                            true
                        } else false
                    },
                    onZoomOut = { canZoom: Boolean ->
                        if (canZoom && viewModel.itemSize > viewModel.minItemSize) {
                            viewModel.itemSize -= viewModel.itemSizeStep
                            true
                        } else false
                    },
                    onToggleMultiSelect = {
                        if (viewModel.isMultiSelectMode) {
                            viewModel.isMultiSelectMode = false
                            viewModel.selectedFiles.clear()
                        } else {
                            viewModel.isMultiSelectMode = true
                            viewModel.selectedFiles.clear()
                        }
                    },
                    onPaste = { viewModel.pasteFiles() },
                    clipboardEmpty = viewModel.clipboardFiles.isEmpty(),
                    displayMode = viewModel.displayMode,
                    onChangeDisplayMode = {
                        viewModel.displayMode =
                                when (viewModel.displayMode) {
                                    DisplayMode.SINGLE_COLUMN -> DisplayMode.TWO_COLUMNS
                                    DisplayMode.TWO_COLUMNS -> DisplayMode.THREE_COLUMNS
                                    DisplayMode.THREE_COLUMNS -> DisplayMode.SINGLE_COLUMN
                                }
                    },
                    onShowSearchDialog = {
                        viewModel.searchDialogQuery = ""
                        viewModel.showSearchDialog = true
                    },
                    isSearching = viewModel.isSearching,
                    onExitSearch = {
                        viewModel.searchQuery = ""
                        viewModel.isSearching = false
                        viewModel.searchResults.clear()
                    },
                    onNewFolder = {
                        viewModel.newFolderName = ""
                        viewModel.showNewFolderDialog = true
                    },
                    isMultiSelectMode = viewModel.isMultiSelectMode
            )

            // 标签栏
            FileManagerTabRow(
                    tabs = viewModel.tabs,
                    activeTabIndex = viewModel.activeTabIndex,
                    onSwitchTab = { viewModel.switchTab(it) },
                    onCloseTab = { viewModel.closeTab(it) },
                    onAddTab = { viewModel.addTab() }
            )

            // 主内容区域
            Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
            ) {
                FileListContent(
                        error = viewModel.error,
                        files = viewModel.files,
                        listState = listState,
                        isSearching = viewModel.isSearching,
                        searchResults = viewModel.searchResults,
                        displayMode = viewModel.displayMode,
                        itemSize = viewModel.itemSize,
                        isMultiSelectMode = viewModel.isMultiSelectMode,
                        selectedFiles = viewModel.selectedFiles,
                        selectedFile = viewModel.selectedFile,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        onShowBottomActionMenu = { viewModel.showBottomActionMenu = true }
                )
            }

            // 状态栏
            StatusBar(
                    fileCount = viewModel.files.size,
                    selectedFiles = viewModel.selectedFiles,
                    selectedFile = viewModel.selectedFile,
                    isMultiSelectMode = viewModel.isMultiSelectMode,
                    onExitMultiSelect = {
                        viewModel.isMultiSelectMode = false
                        viewModel.selectedFiles.clear()
                    }
            )
        }

        // 加载中覆盖层
        LoadingOverlay(isLoading = viewModel.isLoading)
    }

    // 对话框
    // 搜索对话框
    SearchDialog(
            showDialog = viewModel.showSearchDialog,
            searchQuery = viewModel.searchDialogQuery,
            onQueryChange = { viewModel.searchDialogQuery = it },
            isCaseSensitive = viewModel.isCaseSensitive,
            onCaseSensitiveChange = { viewModel.isCaseSensitive = it },
            useWildcard = viewModel.useWildcard,
            onWildcardChange = { viewModel.useWildcard = it },
            onSearch = {
                viewModel.searchQuery = viewModel.searchDialogQuery
                viewModel.showSearchDialog = false
                if (viewModel.searchDialogQuery.isNotBlank()) {
                    viewModel.searchFiles(viewModel.searchDialogQuery)
                }
            },
            onDismiss = { viewModel.showSearchDialog = false }
    )

    // 搜索结果对话框
    SearchResultsDialog(
            showDialog = viewModel.showSearchResultsDialog,
            searchResults = viewModel.searchResults,
            onNavigateToFileDirectory = { path -> viewModel.navigateToFileDirectory(path) },
            onDismiss = { viewModel.showSearchResultsDialog = false }
    )

    // 新建文件夹对话框
    NewFolderDialog(
            showDialog = viewModel.showNewFolderDialog,
            folderName = viewModel.newFolderName,
            onFolderNameChange = { name -> viewModel.newFolderName = name },
            onCreateFolder = {
                if (viewModel.newFolderName.isNotBlank()) {
                    viewModel.createNewFolder(viewModel.newFolderName)
                    viewModel.showNewFolderDialog = false
                }
            },
            onDismiss = { viewModel.showNewFolderDialog = false }
    )

    // 文件上下文菜单
    FileContextMenu(
            showMenu = viewModel.showBottomActionMenu,
            onDismissRequest = { viewModel.showBottomActionMenu = false },
            contextMenuFile = viewModel.contextMenuFile,
            isMultiSelectMode = viewModel.isMultiSelectMode,
            selectedFiles = viewModel.selectedFiles,
            currentPath = viewModel.currentPath,
            onFilesUpdated = { viewModel.loadCurrentDirectory() },
            toolHandler = toolHandler,
            onPaste = { viewModel.pasteFiles() },
            onCopy = { files -> viewModel.setClipboard(files, false) },
            onCut = { files -> viewModel.setClipboard(files, true) },
            onOpen = { file ->
                val fullPath = "${viewModel.currentPath}/${file.name}"
                val openTool =
                        AITool(
                                name = "open_file",
                                parameters = listOf(ToolParameter("path", fullPath))
                        )
                toolHandler.executeTool(openTool)
            },
            onShare = { file ->
                val fullPath = "${viewModel.currentPath}/${file.name}"
                val shareTool =
                        AITool(
                                name = "share_file",
                                parameters = listOf(ToolParameter("path", fullPath))
                        )
                toolHandler.executeTool(shareTool)
            }
    )
}
