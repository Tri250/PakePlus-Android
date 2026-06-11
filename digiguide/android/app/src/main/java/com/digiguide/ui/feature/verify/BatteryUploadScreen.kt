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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digiguide.viewmodel.BatteryAnalysisViewModel

/**
 * 电池上传屏幕
 */
@Composable
fun BatteryUploadScreen(
    viewModel: BatteryAnalysisViewModel = viewModel(),
    onNavigateToResult: () -> Unit,
    onBack: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val analysisProgress by viewModel.analysisProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val bugreportFilePath by viewModel.bugreportFilePath.collectAsState()
    val bugreportContent by viewModel.bugreportContent.collectAsState()
    val healthResult by viewModel.healthResult.collectAsState()

    // 监听分析结果，成功后跳转
    LaunchedEffect(healthResult) {
        if (healthResult != null) {
            onNavigateToResult()
        }
    }

    var showFilePicker by remember { mutableStateOf(false) }
    var textContent by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电池健康分析") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 文件上传区域
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = "上传文件",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "上传bugreport文件",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "支持ZIP压缩包或文本文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showFilePicker = true },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "选择文件")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择文件")
                    }

                    if (bugreportFilePath != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "已选择: ${bugreportFilePath!!.substringAfterLast("/")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 文本输入区域
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "或直接粘贴内容",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textContent,
                        onValueChange = {
                            textContent = it
                            viewModel.setBugreportContent(it)
                        },
                        label = { Text("bugreport内容") },
                        placeholder = { Text("粘贴bugreport文本内容...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        maxLines = 10
                    )
                }
            }

            // 分析进度
            if (isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { analysisProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "正在分析... $analysisProgress%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 错误提示
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "错误",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 开始分析按钮
            Button(
                onClick = { viewModel.startAnalysis() },
                modifier = Modifier.fillMaxWidth(),
                enabled = (bugreportFilePath != null || bugreportContent != null) && !isLoading
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "开始分析")
                Spacer(modifier = Modifier.width(8.dp))
                Text("开始分析")
            }

            // 使用说明
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "如何获取bugreport",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. 打开手机开发者选项\n" +
                               "2. 启用USB调试\n" +
                               "3. 连接电脑执行: adb bugreport\n" +
                               "4. 上传生成的ZIP文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}