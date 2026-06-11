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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digiguide.db.entity.BatteryReportEntity
import com.digiguide.viewmodel.BatteryAnalysisViewModel
import com.digiguide.viewmodel.ReportViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 报告历史屏幕 - 完整实现
 * 包含报告列表、详情查看、统计汇总
 */
@Composable
fun ReportScreen(
    viewModel: ReportViewModel = viewModel(),
    analysisViewModel: BatteryAnalysisViewModel = viewModel(),
    onBack: () -> Unit,
    onViewDetail: (BatteryReportEntity) -> Unit = {}
) {
    val reportHistory by viewModel.reportHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statistics by viewModel.statistics.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val selectedReport by viewModel.selectedReport.collectAsState()
    val showClearAllDialog by viewModel.showClearAllDialog.collectAsState()

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
                    IconButton(onClick = { viewModel.toggleClearAllDialog(true) }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清除全部")
                    }
                    IconButton(onClick = { viewModel.refreshReports() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 统计汇总卡片
            if (reportHistory.isNotEmpty()) {
                StatisticsCard(statistics = statistics)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 报告列表
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00E5A0))
                }
            } else if (reportHistory.isEmpty()) {
                EmptyReportCard()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(reportHistory, key = { it.id }) { report ->
                        ReportItem(
                            report = report,
                            onViewDetail = {
                                analysisViewModel.viewReportDetail(report)
                                onViewDetail(report)
                            },
                            onDelete = { viewModel.selectReportForDelete(report) }
                        )
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog && selectedReport != null) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleDeleteDialog(false) },
            title = { Text("删除报告") },
            text = { Text("确定要删除这份报告吗？删除后无法恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteReport(selectedReport!!)
                        viewModel.toggleDeleteDialog(false)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleDeleteDialog(false) }) {
                    Text("取消")
                }
            }
        )
    }

    // 清除全部确认对话框
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleClearAllDialog(false) },
            title = { Text("清除全部报告") },
            text = { Text("确定要清除所有历史报告吗？此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllReports()
                        viewModel.toggleClearAllDialog(false)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清除全部")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleClearAllDialog(false) }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 统计汇总卡片
 */
@Composable
fun StatisticsCard(statistics: ReportViewModel.ReportStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "报告统计",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItemCompact(label = "总报告", value = statistics.totalCount.toString())
                StatItemCompact(label = "平均健康度", value = "${statistics.averageHealth.toInt()}%")
                StatItemCompact(label = "平均循环", value = "${statistics.averageCycles}次")
            }
            if (statistics.gradeDistribution.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "等级分布",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    statistics.gradeDistribution.forEach { (grade, count) ->
                        GradeBadge(grade = grade, count = count)
                    }
                }
            }
        }
    }
}

@Composable
fun StatItemCompact(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun GradeBadge(grade: String, count: Int) {
    val gradeColor = when (grade) {
        "A+", "A" -> Color(0xFF4CAF50)
        "B" -> Color(0xFF8BC34A)
        "C" -> Color(0xFFFFC107)
        "D" -> Color(0xFFFF9800)
        "F" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = gradeColor.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = grade,
                style = MaterialTheme.typography.labelMedium,
                color = gradeColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = gradeColor
            )
        }
    }
}

/**
 * 空报告卡片
 */
@Composable
fun EmptyReportCard() {
    Box(
        modifier = Modifier.fillMaxSize(),
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "上传bugreport文件后，分析结果将保存在这里",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 报告列表项
 */
@Composable
fun ReportItem(
    report: BatteryReportEntity,
    onViewDetail: () -> Unit,
    onDelete: () -> Unit
) {
    val gradeColor = when (report.grade) {
        "A+", "A" -> Color(0xFF4CAF50)
        "B" -> Color(0xFF8BC34A)
        "C" -> Color(0xFFFFC107)
        "D" -> Color(0xFFFF9800)
        "F" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onViewDetail
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 健康度等级徽章
            Surface(
                color = gradeColor.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.medium
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = report.grade,
                            style = MaterialTheme.typography.titleLarge,
                            color = gradeColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "${report.healthPercentage.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = gradeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 基本信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${report.brand ?: "未知品牌"} ${report.model ?: ""}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))

                // 详细信息行
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (report.cycleCount != null) {
                        Text(
                            text = "循环${report.cycleCount}次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (report.designCapacityMah != null) {
                        Text(
                            text = "${report.designCapacityMah}mAh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTime(report.reportTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // 操作按钮
            IconButton(onClick = onViewDetail) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = "查看详情",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return format.format(date)
}