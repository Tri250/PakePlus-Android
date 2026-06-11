package com.digiguide.ui.feature.verify

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digiguide.model.Brand
import com.digiguide.viewmodel.SNQueryViewModel

/**
 * SN查询屏幕
 */
@Composable
fun SNQueryScreen(
    viewModel: SNQueryViewModel = viewModel(),
    onNavigateToResult: () -> Unit,
    onBack: () -> Unit
) {
    val snInput by viewModel.snInput.collectAsState()
    val selectedBrand by viewModel.selectedBrand.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val formatHint by viewModel.formatHint.collectAsState()
    val decodeResult by viewModel.decodeResult.collectAsState()

    // 监听解码结果，成功后跳转
    LaunchedEffect(decodeResult) {
        if (decodeResult != null && decodeResult!!.isSuccess()) {
            onNavigateToResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SN序列号查询") },
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
            // SN输入框
            OutlinedTextField(
                value = snInput,
                onValueChange = { viewModel.updateSNInput(it) },
                label = { Text("序列号") },
                placeholder = { Text("请输入设备序列号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Text
                ),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (snInput.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSNInput("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                }
            )

            // 格式提示
            if (formatHint != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "提示",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatHint!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // 品牌选择
            Text(
                text = "选择品牌（可选）",
                style = MaterialTheme.typography.titleSmall
            )

            BrandSelector(
                selectedBrand = selectedBrand,
                onBrandSelected = { viewModel.selectBrand(it) }
            )

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

            // 查询按钮
            Button(
                onClick = { viewModel.decode() },
                modifier = Modifier.fillMaxWidth(),
                enabled = snInput.isNotEmpty() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在查询...")
                } else {
                    Icon(Icons.Default.Search, contentDescription = "查询")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始查询")
                }
            }

            // 使用说明
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "使用说明",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. 输入设备序列号（SN）\n" +
                               "2. 系统会自动识别品牌\n" +
                               "3. 如识别错误，可手动选择品牌\n" +
                               "4. 点击查询获取生产日期信息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BrandSelector(
    selectedBrand: Brand?,
    onBrandSelected: (Brand) -> Unit
) {
    val brands = listOf(
        Brand.APPLE, Brand.SAMSUNG, Brand.HUAWEI, Brand.HONOR,
        Brand.XIAOMI, Brand.OPPO, Brand.VIVO,
        Brand.LENOVO, Brand.HP, Brand.ASUS, Brand.DELL
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        brands.chunked(4).forEach { rowBrands ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowBrands.forEach { brand ->
                    FilterChip(
                        selected = selectedBrand == brand,
                        onClick = { onBrandSelected(brand) },
                        label = { Text(brand.toChinese()) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}