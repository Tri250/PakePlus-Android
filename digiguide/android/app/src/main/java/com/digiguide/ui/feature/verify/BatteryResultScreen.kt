package com.digiguide.ui.feature.verify

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digiguide.model.BatteryHealthResult
import com.digiguide.model.BatteryRawData
import com.digiguide.viewmodel.BatteryAnalysisViewModel

/**
 * 电池分析结果屏幕
 */
@Composable
fun BatteryResultScreen(
    viewModel: BatteryAnalysisViewModel = viewModel(),
    onBack: () -> Unit
) {
    val healthResult by viewModel.healthResult.collectAsState()
    val rawData by viewModel.rawData.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分析结果") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (healthResult == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无分析结果")
            }
            return
        }

        val result = healthResult!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 健康度等级卡片
            HealthGradeCard(result)

            // 循环次数详情卡片
            CycleCountDetailCard(
                cycleCount = result.cycleCount,
                cycleGrade = result.cycleGrade,
                cyclePercentUsed = result.cyclePercentUsed,
                estimatedRemainingCycles = result.estimatedRemainingCycles
            )

            // 原始数据卡片
            if (rawData != null) {
                RawDataCard(rawData!!)
            }

            // 详细分析卡片
            DetailedAnalysisCard(result)

            // 使用建议卡片
            SuggestionsCard(result)

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.clearAnalysis() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "新分析")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新分析")
                }

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Home, contentDescription = "返回首页")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("返回首页")
                }
            }
        }
    }
}

@Composable
fun HealthGradeCard(result: BatteryHealthResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(android.graphics.Color.parseColor(result.getGradeColor()))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = result.grade,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${result.healthPercentage.toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = result.getGradeDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            
            // 置信度显示
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "置信度",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "置信度: ${result.confidence.name} (${result.getAvailableFactorsCount()}个因子)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * 循环次数详情卡片
 */
@Composable
fun CycleCountDetailCard(
    cycleCount: Int?,
    cycleGrade: String,
    cyclePercentUsed: Float,
    estimatedRemainingCycles: Int?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "电池循环次数",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (cycleCount != null) {
                // 主数字：当前循环次数
                Text(
                    text = "$cycleCount 次",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 循环进度条
                LinearProgressIndicator(
                    progress = { (cyclePercentUsed / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = when {
                        cyclePercentUsed <= 40f -> Color(0xFF4CAF50)
                        cyclePercentUsed <= 80f -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                // 循环状态标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("循环状态", style = MaterialTheme.typography.bodyMedium)
                    CycleGradeChip(cycleGrade)
                }

                // 循环寿命消耗
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("寿命消耗", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${String.format("%.1f", cyclePercentUsed)}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 预估剩余循环
                if (estimatedRemainingCycles != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("预估剩余循环", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "约 $estimatedRemainingCycles 次",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                estimatedRemainingCycles > 200 -> Color(0xFF4CAF50)
                                estimatedRemainingCycles > 50 -> Color(0xFFFFC107)
                                else -> Color(0xFFF44336)
                            }
                        )
                    }

                    // 预估剩余使用时间
                    val remainingMonths = estimatedRemainingCycles / 30  // 假设每天约1次循环
                    Text(
                        "按每日一充估算，还可使用约 $remainingMonths 个月",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 循环次数说明
                Text(
                    "锂电池典型额定寿命为 500 次循环，达到该值后容量通常降至原始的 80% 左右。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 循环次数不可用
                InfoBanner(
                    icon = Icons.Default.Warning,
                    text = "未能从日志中提取到循环次数，请确保在设置-电池中采集完整数据后再生成bugreport。",
                    severity = BannerSeverity.Warning
                )
            }
        }
    }
}

/**
 * 循环等级标签
 */
@Composable
fun CycleGradeChip(cycleGrade: String) {
    val (color, text) = when (cycleGrade) {
        "极佳" -> Pair(Color(0xFF4CAF50), "极佳")
        "良好" -> Pair(Color(0xFF8BC34A), "良好")
        "一般" -> Pair(Color(0xFFFFC107), "一般")
        "警告" -> Pair(Color(0xFFFF9800), "警告")
        "危险" -> Pair(Color(0xFFF44336), "危险")
        else -> Pair(Color(0xFF9E9E9E), cycleGrade)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

/**
 * 信息提示条
 */
@Composable
fun InfoBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    severity: BannerSeverity
) {
    val backgroundColor = when (severity) {
        BannerSeverity.Info -> MaterialTheme.colorScheme.secondaryContainer
        BannerSeverity.Warning -> Color(0xFFFFF3E0)
        BannerSeverity.Error -> MaterialTheme.colorScheme.errorContainer
    }

    val iconColor = when (severity) {
        BannerSeverity.Info -> MaterialTheme.colorScheme.secondary
        BannerSeverity.Warning -> Color(0xFFFF9800)
        BannerSeverity.Error -> MaterialTheme.colorScheme.error
    }

    val textColor = when (severity) {
        BannerSeverity.Info -> MaterialTheme.colorScheme.onSecondaryContainer
        BannerSeverity.Warning -> Color(0xFFE65100)
        BannerSeverity.Error -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}

enum class BannerSeverity {
    Info, Warning, Error
}

@Composable
fun RawDataCard(data: BatteryRawData) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "设备信息",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (data.brand != null) {
                ResultItem(label = "品牌", value = data.brand!!)
            }
            if (data.model != null) {
                ResultItem(label = "型号", value = data.model!!)
            }
            if (data.designCapacityMah != null) {
                ResultItem(label = "设计容量", value = "${data.designCapacityMah} mAh")
            }
            if (data.currentCapacityMah != null) {
                ResultItem(label = "当前容量", value = "${data.currentCapacityMah} mAh")
            }
            if (data.cycleCount != null) {
                ResultItem(label = "循环次数", value = "${data.cycleCount} 次")
            }
            if (data.temperatureCelsius != null) {
                ResultItem(label = "温度", value = "${data.temperatureCelsius}°C")
            }
        }
    }
}

@Composable
fun DetailedAnalysisCard(result: BatteryHealthResult) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "详细分析",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (result.capacityRetention != null) {
                ResultItem(
                    label = "容量保持率",
                    value = "${(result.capacityRetention!! * 100).toInt()}%"
                )
            }
            if (result.cycleDecay != null) {
                ResultItem(
                    label = "循环衰减因子",
                    value = "${(result.cycleDecay!! * 100).toInt()}%"
                )
            }
            if (result.resistanceGrowth != null) {
                ResultItem(
                    label = "内阻增长因子",
                    value = "${(result.resistanceGrowth!! * 100).toInt()}%"
                )
            }
            if (result.thermalAging != null) {
                ResultItem(
                    label = "温度老化因子",
                    value = "${(result.thermalAging!! * 100).toInt()}%"
                )
            }
            if (result.chargingDamage != null) {
                ResultItem(
                    label = "充电损伤因子",
                    value = "${(result.chargingDamage!! * 100).toInt()}%"
                )
            }
            if (result.estimatedResistanceMohm != null) {
                ResultItem(
                    label = "估算内阻",
                    value = "${result.estimatedResistanceMohm!!.toInt()} mΩ"
                )
            }
            if (result.remainingLifespanMonths != null) {
                ResultItem(
                    label = "预估剩余寿命",
                    value = "${result.remainingLifespanMonths!!} 个月"
                )
            }
        }
    }
}

@Composable
fun SuggestionsCard(result: BatteryHealthResult) {
    if (result.suggestions.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "使用建议",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            for (suggestion in result.suggestions) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = "建议",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ResultItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}