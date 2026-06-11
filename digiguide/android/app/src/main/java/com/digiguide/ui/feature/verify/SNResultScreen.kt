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
import com.digiguide.model.SNDecodeStatus
import com.digiguide.viewmodel.SNQueryViewModel

/**
 * SN查询结果屏幕
 */
@Composable
fun SNResultScreen(
    viewModel: SNQueryViewModel = viewModel(),
    onBack: () -> Unit
) {
    val decodeResult by viewModel.decodeResult.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("查询结果") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (decodeResult == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无查询结果")
            }
            return
        }

        val result = decodeResult!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (result.status) {
                        SNDecodeStatus.SUCCESS -> Color(0xFF4CAF50)
                        SNDecodeStatus.PARTIAL -> Color(0xFFFF9800)
                        SNDecodeStatus.FAILED -> Color(0xFFF44336)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (result.status) {
                            SNDecodeStatus.SUCCESS -> Icons.Default.CheckCircle
                            SNDecodeStatus.PARTIAL -> Icons.Default.Warning
                            SNDecodeStatus.FAILED -> Icons.Default.Error
                        },
                        contentDescription = "状态",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (result.status) {
                            SNDecodeStatus.SUCCESS -> "解码成功"
                            SNDecodeStatus.PARTIAL -> "部分解码成功"
                            SNDecodeStatus.FAILED -> "解码失败"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }

            // 基本信息
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "基本信息",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ResultItem(label = "序列号", value = result.rawSn)
                    ResultItem(label = "品牌", value = result.getBrandName())
                }
            }

            // 生产日期信息
            if (result.factoryYear != null) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "生产日期",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ResultItem(label = "年份", value = "${result.factoryYear}")
                        if (result.factoryMonth != null) {
                            ResultItem(label = "月份", value = "${result.factoryMonth}")
                        }
                        if (result.factoryWeek != null) {
                            ResultItem(label = "周次", value = "第${result.factoryWeek}周")
                        }
                        if (result.halfYear != null) {
                            ResultItem(label = "半年", value = result.halfYear!!)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "生产日期估算: ${result.getProductionDateEstimate()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 保修状态
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "保修状态",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = result.getWarrantyStatus(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 错误信息
            if (result.errorMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "提示",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = result.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.clearResult() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "新查询")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新查询")
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