package com.digiguide.ui.feature.verify

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
        }
    }
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

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "置信度: ${result.confidence.name} (${result.getAvailableFactorsCount()}个因子)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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