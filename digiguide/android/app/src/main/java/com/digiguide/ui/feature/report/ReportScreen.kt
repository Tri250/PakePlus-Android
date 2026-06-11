package com.digiguide.ui.feature.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digiguide.db.entity.BatteryReportEntity
import com.digiguide.viewmodel.BatteryAnalysisViewModel

/**
 * 报告历史屏幕
 */
@Composable
fun ReportScreen(
    viewModel: BatteryAnalysisViewModel = viewModel(),
    onBack: () -> Unit
) {
    val reportHistory by viewModel.reportHistory.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史报告") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearAllReports() }) {
                        Icon(Icons.Default.Delete, contentDescription = "清除全部")
                    }
                }
            )
        }
    ) { padding ->
        if (reportHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "历史",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无历史报告",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reportHistory, key = { it.id }) { report ->
                ReportItem(
                    report = report,
                    onViewDetail = { viewModel.viewReportDetail(report) },
                    onDelete = { viewModel.deleteReport(report) }
                )
            }
        }
    }
}

@Composable
fun ReportItem(
    report: BatteryReportEntity,
    onViewDetail: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 健康度等级
            Box(
                modifier = Modifier
                    .size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = report.grade,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 基本信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${report.brand ?: "未知"} ${report.model ?: ""}",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "健康度: ${report.healthPercentage.toInt()}% | 循环: ${report.cycleCount ?: "未知"}次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTime(report.reportTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 操作按钮
            IconButton(onClick = onViewDetail) {
                Icon(Icons.Default.Visibility, contentDescription = "查看详情")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return format.format(date)
}