package com.digiguide.ui.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 首页屏幕
 */
@Composable
fun HomeScreen(
    onNavigateToVerify: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数码指南") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
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
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
                    label = { Text("我的") },
                    selected = false,
                    onClick = onNavigateToProfile
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 快捷功能卡片
            QuickActionCard(
                title = "SN序列号查询",
                description = "查询设备生产日期和保修状态",
                icon = Icons.Default.Search,
                onClick = onNavigateToVerify
            )

            QuickActionCard(
                title = "电池健康分析",
                description = "上传bugreport分析电池状态",
                icon = Icons.Default.BatteryStd,
                onClick = onNavigateToVerify
            )

            QuickActionCard(
                title = "历史报告",
                description = "查看所有验机报告记录",
                icon = Icons.Default.History,
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(label = "SN查询", count = "0次")
                        StatItem(label = "电池分析", count = "0次")
                        StatItem(label = "报告数", count = "0个")
                    }
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
            Column {
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}