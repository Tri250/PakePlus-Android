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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digiguide.model.Brand
import com.digiguide.model.SNDecodeResult
import com.digiguide.viewmodel.SNQueryViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val purchaseDate by viewModel.purchaseDate.collectAsState()

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

            // 购买日期选择（可选）
            PurchaseDatePicker(
                purchaseDate = purchaseDate,
                onDateSelected = { viewModel.setPurchaseDate(it) }
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
                onClick = { viewModel.performQuery() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = snInput.isNotEmpty() && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5A0)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(Icons.Default.Search, contentDescription = "查询")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始查询")
                }
            }

            // 结果卡片（如果已有结果）
            viewModel.queryResult?.let { result ->
                SNResultCard(result = result)
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
                               "4. 可选填写购买日期以精确计算保修\n" +
                               "5. 点击查询获取生产日期信息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 品牌选择器
 */
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

/**
 * 购买日期选择器
 */
@Composable
fun PurchaseDatePicker(
    purchaseDate: String?,
    onDateSelected: (String?) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    Column {
        Text(
            text = "购买日期（可选，用于精确保修计算）",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = purchaseDate ?: "",
            onValueChange = {},
            label = { Text("购买日期") },
            placeholder = { Text("点击选择日期") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Row {
                    if (purchaseDate != null) {
                        IconButton(onClick = { onDateSelected(null) }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "选择日期")
                    }
                }
            }
        )
    }

    // 日期选择对话框
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = purchaseDate?.let {
                LocalDate.parse(it, dateFormatter).toEpochDay() * 24 * 60 * 60 * 1000
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                            onDateSelected(date.format(dateFormatter))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * SN结果卡片（简化版，用于查询页面内显示）
 */
@Composable
fun SNResultCard(result: SNDecodeResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.isSuccess()) {
                Color(0xFFE8F5E9)
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (result.isSuccess()) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = "状态",
                    tint = if (result.isSuccess()) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (result.isSuccess()) "解码成功" else "解码失败",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ResultRow("品牌", result.getBrandName())
            ResultRow("序列号", result.rawSn)

            if (result.factoryYear != null) {
                ResultRow("生产日期", result.getProductionDateEstimate())
            }

            if (result.isSuccess()) {
                ResultRow("保修状态", result.getWarrantyStatus())
            }

            if (result.errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.isSuccess()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: String) {
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
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    }
}