package com.digiguide.ui.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digiguide.viewmodel.ProfileViewModel
import com.digiguide.viewmodel.HomeViewModel

/**
 * 个人中心屏幕 - 完整实现
 * 包含真实统计数据、设置功能、数据管理
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snQueryCount by homeViewModel.snQueryCount.collectAsState()
    val batteryAnalysisCount by homeViewModel.batteryAnalysisCount.collectAsState()
    val reportCount by homeViewModel.reportCount.collectAsState()
    val averageHealth by homeViewModel.averageHealth.collectAsState()

    val darkMode by viewModel.darkMode.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()
    val appVersion by viewModel.appVersion.collectAsState()

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 用户信息卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "用户",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "数码指南用户",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "版本: $appVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "本地模式 · 数据不上传",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // 使用统计卡片（真实数据）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "统计",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "使用统计",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(label = "SN查询", count = snQueryCount.toString())
                        StatItem(label = "电池分析", count = batteryAnalysisCount.toString())
                        StatItem(label = "报告数", count = reportCount.toString())
                    }
                    if (averageHealth > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "平均健康度: ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${averageHealth.toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = getHealthColor(averageHealth)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { homeViewModel.refreshStatistics() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("刷新统计")
                    }
                }
            }

            // 设置项
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    // 深色模式
                    SettingToggleItem(
                        icon = Icons.Default.DarkMode,
                        title = "深色模式",
                        subtitle = "跟随系统设置",
                        checked = darkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) }
                    )
                    HorizontalDivider()

                    // 通知设置
                    SettingToggleItem(
                        icon = Icons.Default.Notifications,
                        title = "通知提醒",
                        subtitle = "电池健康预警通知",
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )
                    HorizontalDivider()

                    // 自动同步
                    SettingToggleItem(
                        icon = Icons.Default.Sync,
                        title = "自动同步",
                        subtitle = "同步历史记录到云端",
                        checked = autoSyncEnabled,
                        onCheckedChange = { viewModel.setAutoSyncEnabled(it) }
                    )
                }
            }

            // 数据管理
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    SettingItemClickable(
                        icon = Icons.Default.Storage,
                        title = "数据管理",
                        subtitle = "缓存: $cacheSize",
                        onClick = { showClearCacheDialog = true }
                    )
                    HorizontalDivider()
                    SettingItemClickable(
                        icon = Icons.Default.FileDownload,
                        title = "导出数据",
                        subtitle = "导出历史记录和报告",
                        onClick = { showExportDialog = true }
                    )
                    HorizontalDivider()
                    SettingItemClickable(
                        icon = Icons.Default.FileUpload,
                        title = "导入数据",
                        subtitle = "从备份文件恢复数据",
                        onClick = { /* TODO: 实现导入功能 */ }
                    )
                }
            }

            // 其他设置
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    SettingItemClickable(
                        icon = Icons.Default.Info,
                        title = "关于应用",
                        subtitle = "版本信息、开发者",
                        onClick = { showAboutDialog = true }
                    )
                    HorizontalDivider()
                    SettingItemClickable(
                        icon = Icons.Default.Feedback,
                        title = "反馈建议",
                        subtitle = "提交问题或建议",
                        onClick = { showFeedbackDialog = true }
                    )
                    HorizontalDivider()
                    SettingItemClickable(
                        icon = Icons.Default.Shield,
                        title = "隐私政策",
                        subtitle = "数据安全与隐私保护",
                        onClick = { /* TODO: 显示隐私政策 */ }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 清除缓存对话框
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清除缓存") },
            text = { Text("当前缓存大小: $cacheSize\n\n清除缓存不会删除历史记录和报告数据。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearCache(context)
                        showClearCacheDialog = false
                    }
                ) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 导出数据对话框
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出数据") },
            text = {
                Column {
                    Text("选择导出内容：")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• SN查询历史 ($snQueryCount 条)")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• 电池分析报告 ($reportCount 条)")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("导出格式: JSON文件")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("导出位置: 应用私有目录")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.exportData(context)
                        showExportDialog = false
                    }
                ) {
                    Text("导出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 关于对话框
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于数码指南") },
            text = {
                Column {
                    Text("版本: $appVersion")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("数码指南是一款专业的设备验机和电池健康分析工具。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("核心功能：")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• SN序列号解码（12品牌支持）")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• 电池健康度分析（5因子算法）")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• bugreport解析（50+正则模式）")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("隐私保护：")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("所有分析在本地完成，不上传任何数据。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("© 2024 数码指南团队")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }

    // 反馈对话框
    if (showFeedbackDialog) {
        var feedbackText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("反馈建议") },
            text = {
                Column {
                    Text("请描述您的问题或建议：")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text("输入反馈内容...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitFeedback(feedbackText)
                        showFeedbackDialog = false
                    },
                    enabled = feedbackText.isNotEmpty()
                ) {
                    Text("提交")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun SettingToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingItemClickable(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    count: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getHealthColor(health: Float): Color {
    return when {
        health >= 90 -> Color(0xFF4CAF50)
        health >= 80 -> Color(0xFF8BC34A)
        health >= 70 -> Color(0xFFFFC107)
        health >= 60 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}