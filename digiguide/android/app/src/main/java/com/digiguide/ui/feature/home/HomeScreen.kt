package com.digiguide.ui.feature.home

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digiguide.viewmodel.HomeViewModel

/**
 * 首页屏幕
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToVerify: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val snQueryCount by viewModel.snQueryCount.collectAsState()
    val batteryAnalysisCount by viewModel.batteryAnalysisCount.collectAsState()
    val reportCount by viewModel.reportCount.collectAsState()
    val averageHealth by viewModel.averageHealth.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数码指南") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "个人中心")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                    label = { Text("首页") },
                    selected = true,
                    onClick = {}
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Verified, contentDescription = "验机") },
                    label = { Text("验机") },
                    selected = false,
                    onClick = onNavigateToVerify
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Report, contentDescription = "报告") },
                    label = { Text("报告") },
                    selected = false,
                    onClick = onNavigateToReport
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Explore, contentDescription = "发现") },
                    label = { Text("发现") },
                    selected = false,
                    onClick = onNavigateToDiscover
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 欢迎卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "欢迎使用数码指南",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "专业的设备验机和电池健康度分析工具",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 快捷功能卡片
            QuickActionCard(
                title = "SN序列号查询",
                description = "查询设备生产日期和保修状态",
                icon = Icons.Default.Search,
                count = snQueryCount,
                onClick = onNavigateToVerify
            )

            QuickActionCard(
                title = "电池健康分析",
                description = "上传bugreport分析电池状态",
                icon = Icons.Default.BatteryStd,
                count = batteryAnalysisCount,
                onClick = onNavigateToVerify
            )

            QuickActionCard(
                title = "历史报告",
                description = "查看所有验机报告记录",
                icon = Icons.Default.History,
                count = reportCount,
                onClick = onNavigateToReport
            )

            // 统计信息卡片
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "使用统计",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(label = "SN查询", count = "$snQueryCount 次")
                        StatItem(label = "电池分析", count = "$batteryAnalysisCount 次")
                        StatItem(label = "报告数", count = "$reportCount 个")
                    }

                    if (averageHealth > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "平均健康度",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${averageHealth.toInt()}%",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = when {
                                        averageHealth >= 90 -> MaterialTheme.colorScheme.primary
                                        averageHealth >= 80 -> Color(0xFFFFC107)
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 最近活动卡片
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "最近活动",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (snQueryCount == 0 && batteryAnalysisCount == 0) {
                        Text(
                            text = "暂无活动记录，开始使用吧！",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        ActivityItem(
                            icon = Icons.Default.Search,
                            title = "SN查询",
                            count = snQueryCount
                        )
                        ActivityItem(
                            icon = Icons.Default.BatteryStd,
                            title = "电池分析",
                            count = batteryAnalysisCount
                        )
                    }
                }
            }

            // 提示卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "提示",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "所有数据均在本地处理，不会上传到服务器",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    count: Int = 0,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (count > 0) {
                Badge { Text("$count") }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.ArrowForward,
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

@Composable
fun ActivityItem(
    icon: ImageVector,
    title: String,
    count: Int
) {
    if (count > 0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "$title: $count 次",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}