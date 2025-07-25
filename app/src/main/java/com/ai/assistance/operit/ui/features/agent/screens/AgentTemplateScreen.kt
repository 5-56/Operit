package com.ai.assistance.operit.ui.features.agent.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.agent.AgentTemplate
import com.ai.assistance.operit.core.agent.AgentTemplateManager
import com.ai.assistance.operit.core.agent.TemplateDifficulty

/**
 * Agent模板选择界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentTemplateScreen(
    onTemplateSelected: (AgentTemplate, Map<String, String>) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val templateManager = remember { AgentTemplateManager.getInstance(context) }
    
    var selectedCategory by remember { mutableStateOf("全部") }
    var searchQuery by remember { mutableStateOf("") }
    var showTemplateDialog by remember { mutableStateOf<AgentTemplate?>(null) }
    
    val categories = remember {
        listOf("全部") + templateManager.getAllCategories()
    }
    
    val filteredTemplates = remember(selectedCategory, searchQuery) {
        val baseTemplates = if (selectedCategory == "全部") {
            templateManager.getAllTemplates()
        } else {
            templateManager.getTemplatesByCategory(selectedCategory)
        }
        
        if (searchQuery.isBlank()) {
            baseTemplates
        } else {
            templateManager.searchTemplates(searchQuery)
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部工具栏
        TopAppBar(
            title = { Text("Agent任务模板") },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { /* TODO: 添加自定义模板 */ }) {
                    Icon(Icons.Default.Add, contentDescription = "添加模板")
                }
            }
        )
        
        // 搜索框
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("搜索模板...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                }
            )
        }
        
        // 类别筛选
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                FilterChip(
                    onClick = { selectedCategory = category },
                    label = { Text(category) },
                    selected = selectedCategory == category
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 模板列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 推荐模板
            if (selectedCategory == "全部" && searchQuery.isBlank()) {
                item {
                    Text(
                        text = "推荐模板",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                val recommendedTemplates = templateManager.getRecommendedTemplates()
                items(recommendedTemplates) { template ->
                    TemplateCard(
                        template = template,
                        isRecommended = true,
                        onClick = { showTemplateDialog = template }
                    )
                }
                
                item {
                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = "所有模板",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            
            // 所有模板
            items(filteredTemplates) { template ->
                TemplateCard(
                    template = template,
                    onClick = { showTemplateDialog = template }
                )
            }
            
            // 空状态
            if (filteredTemplates.isEmpty()) {
                item {
                    EmptyStateCard(
                        message = if (searchQuery.isNotBlank()) {
                            "未找到匹配的模板"
                        } else {
                            "该类别下暂无模板"
                        }
                    )
                }
            }
        }
    }
    
    // 模板详情对话框
    showTemplateDialog?.let { template ->
        TemplateDetailDialog(
            template = template,
            onDismiss = { showTemplateDialog = null },
            onUseTemplate = { params ->
                onTemplateSelected(template, params)
                showTemplateDialog = null
            }
        )
    }
}

/**
 * 模板卡片组件
 */
@Composable
private fun TemplateCard(
    template: AgentTemplate,
    isRecommended: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (isRecommended) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = { },
                                label = { Text("推荐", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                DifficultyChip(difficulty = template.difficulty)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 标签和信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    template.tags.take(3).forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { 
                                Text(
                                    text = tag, 
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    
                    if (template.tags.size > 3) {
                        Text(
                            text = "+${template.tags.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = template.estimatedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 难度等级芯片
 */
@Composable
private fun DifficultyChip(
    difficulty: TemplateDifficulty,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (difficulty) {
        TemplateDifficulty.EASY -> "简单" to Color.Green
        TemplateDifficulty.MEDIUM -> "中等" to Color.Orange
        TemplateDifficulty.HARD -> "困难" to Color.Red
        TemplateDifficulty.EXPERT -> "专家" to Color.Magenta
    }
    
    AssistChip(
        onClick = { },
        label = { 
            Text(
                text = text, 
                style = MaterialTheme.typography.labelSmall
            ) 
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.1f),
            labelColor = color
        ),
        modifier = modifier
    )
}

/**
 * 空状态卡片
 */
@Composable
private fun EmptyStateCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 模板详情对话框
 */
@Composable
private fun TemplateDetailDialog(
    template: AgentTemplate,
    onDismiss: () -> Unit,
    onUseTemplate: (Map<String, String>) -> Unit
) {
    val parametersInPrompt = remember {
        // 从模板提示词中提取参数 {parameter}
        val regex = "\\{([^}]+)\\}".toRegex()
        regex.findAll(template.prompt).map { it.groupValues[1] }.toList()
    }
    
    val parameterValues = remember {
        mutableStateMapOf<String, String>().apply {
            parametersInPrompt.forEach { param ->
                this[param] = ""
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = template.name)
        },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 基本信息
                    InfoRow("类别", template.category)
                    InfoRow("难度", when(template.difficulty) {
                        TemplateDifficulty.EASY -> "简单"
                        TemplateDifficulty.MEDIUM -> "中等"
                        TemplateDifficulty.HARD -> "困难"
                        TemplateDifficulty.EXPERT -> "专家"
                    })
                    InfoRow("预计时间", template.estimatedTime)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 执行步骤
                    if (template.expectedSteps.isNotEmpty()) {
                        Text(
                            text = "执行步骤:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        template.expectedSteps.forEachIndexed { index, step ->
                            Text(
                                text = "${index + 1}. $step",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // 参数输入
                    if (parametersInPrompt.isNotEmpty()) {
                        Text(
                            text = "参数设置:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                items(parametersInPrompt) { param ->
                    OutlinedTextField(
                        value = parameterValues[param] ?: "",
                        onValueChange = { parameterValues[param] = it },
                        label = { Text(param) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onUseTemplate(parameterValues.toMap())
                },
                enabled = parametersInPrompt.all { parameterValues[it]?.isNotBlank() == true }
            ) {
                Text("使用模板")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 信息行组件
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}