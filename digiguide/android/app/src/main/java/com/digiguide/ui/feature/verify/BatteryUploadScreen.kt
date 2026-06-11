package com.digiguide.ui.feature.verify

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digiguide.viewmodel.BatteryAnalysisViewModel
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 电池上传屏幕 - 完整实现
 * 包含文件选择器集成、进度显示、文件大小验证
 */
@Composable
fun BatteryUploadScreen(
    viewModel: BatteryAnalysisViewModel = viewModel(),
    onNavigateToResult: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()
    val analysisProgress by viewModel.analysisProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val bugreportFilePath by viewModel.bugreportFilePath.collectAsState()
    val bugreportContent by viewModel.bugreportContent.collectAsState()
    val fileSizeInfo by viewModel.fileSizeInfo.collectAsState()
    val healthResult by viewModel.healthResult.collectAsState()

    // 监听分析结果，成功后跳转
    LaunchedEffect(healthResult) {
        if (healthResult != null) {
            onNavigateToResult()
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // 从URI读取文件内容
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        // 获取文件大小
                        val fileSize = inputStream.available()
                        val fileSizeMB = fileSize / (1024 * 1024)

                        // 验证文件大小（50MB限制）
                        if (fileSize > 50 * 1024 * 1024) {
                            viewModel.setErrorMessage("文件过大（${fileSizeMB}MB），超过50MB限制")
                            inputStream.close()
                            return
                        }

                        // 读取文件内容
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val content = reader.use { it.readText() }
                        inputStream.close()

                        // 设置文件路径（使用URI字符串）和内容
                        viewModel.setBugreportFile(uri.toString())
                        viewModel.setBugreportContent(content)
                        viewModel.setFileSizeInfo("文件大小: ${fileSizeMB}MB (${formatFileSize(fileSize)}")
                    }
                } catch (e: Exception) {
                    viewModel.setErrorMessage("读取文件失败: ${e.message}")
                }
            }
        }
    }

    var textContent by remember { mutableStateOf("") }
    var showHelpDialog by remember { mutableStateOf(false) }
    var analysisStage by remember { mutableStateOf("") }

    // 监听进度更新阶段提示
    LaunchedEffect(analysisProgress) {
        analysisStage = when {
            analysisProgress < 20 -> "正在读取文件..."
            analysisProgress < 50 -> "正在解析bugreport..."
            analysisProgress < 70 -> "正在提取电池数据..."
            analysisProgress < 90 -> "正在计算健康度..."
            analysisProgress < 100 -> "正在保存报告..."
            else -> "分析完成"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电池健康分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "帮助")
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
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
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
                        text = "支持ZIP压缩包或文本文件（最大50MB）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_GET_CONTENT)
                            intent.type = "*/*"
                            intent.addCategory(Intent.CATEGORY_OPENABLE)
                            val chooserIntent = Intent.createChooser(intent, "选择bugreport文件")
                            filePickerLauncher.launch(chooserIntent)
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5A0)
                        )
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "选择文件")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择文件")
                    }

                    // 显示已选择的文件信息
                    if (bugreportFilePath != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "已选择",
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "已选择文件",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4CAF50)
                                )
                                if (fileSizeInfo != null) {
                                    Text(
                                        text = fileSizeInfo!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "文本输入",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "或直接粘贴内容",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textContent,
                        onValueChange = {
                            textContent = it
                            viewModel.setBugreportContent(it)
                        },
                        label = { Text("bugreport内容") },
                        placeholder = { Text("粘贴bugreport文本内容...\n\n提示：可以从终端复制adb bugreport输出") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        maxLines = 15,
                        enabled = !isLoading
                    )
                    if (textContent.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "内容长度: ${textContent.length} 字符",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 分析进度
            if (isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 进度条
                        LinearProgressIndicator(
                            progress = { analysisProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF00E5A0),
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // 进度百分比
                        Text(
                            text = "$analysisProgress%",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // 当前阶段
                        Text(
                            text = analysisStage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // 提示信息
                        Text(
                            text = "请勿关闭页面，分析完成后将自动跳转",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = (bugreportFilePath != null || bugreportContent != null) && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5A0),
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("分析中...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "开始分析")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始分析")
                }
            }

            // 快速提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "提示",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "快速获取bugreport",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. 手机开启开发者选项和USB调试\n" +
                               "2. 连接电脑，执行: adb bugreport\n" +
                               "3. 等待生成ZIP文件（约1-2分钟）\n" +
                               "4. 将ZIP文件上传到本页面\n" +
                               "5. 或直接复制终端输出的文本内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 帮助对话框
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("电池健康分析帮助") },
            text = {
                Column {
                    Text("什么是bugreport？", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("bugreport是Android系统的诊断报告，包含电池详细信息。")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("分析内容包括：", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• 设计容量与当前容量\n• 充电循环次数\n• 电池温度历史\n• 制造日期\n• 内阻估算")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("隐私保护：", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("所有分析在本地完成，不上传任何数据到服务器。")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }
}

/**
 * 格式化文件大小显示
 */
private fun formatFileSize(bytes: Int): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}