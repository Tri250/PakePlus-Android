package com.digiguide.ui.feature.discover

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

/**
 * 发现页面屏幕 - 电池知识库
 */
@Composable
fun DiscoverScreen(
    onBack: () -> Unit
) {
    var selectedTopic by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发现") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (selectedTopic != null) {
            // 显示详细内容
            TopicDetailScreen(
                topic = selectedTopic!!,
                onBack = { selectedTopic = null }
            )
        } else {
            // 显示主题列表
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 电池基础知识
                Text(
                    text = "电池基础知识",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                KnowledgeCard(
                    title = "锂电池工作原理",
                    description = "了解锂电池的充放电机制和化学反应",
                    icon = Icons.Default.Science,
                    onClick = { selectedTopic = "锂电池工作原理" }
                )

                KnowledgeCard(
                    title = "电池老化机制",
                    description = "容量衰减、内阻增长、循环寿命",
                    icon = Icons.Default.TrendingDown,
                    onClick = { selectedTopic = "电池老化机制" }
                )

                KnowledgeCard(
                    title = "温度对电池的影响",
                    description = "高温老化、低温性能下降",
                    icon = Icons.Default.Thermostat,
                    onClick = { selectedTopic = "温度对电池的影响" }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 使用技巧
                Text(
                    text = "使用技巧",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                KnowledgeCard(
                    title = "延长电池寿命的方法",
                    description = "避免深度放电、控制充电速度",
                    icon = Icons.Default.BatteryChargingFull,
                    onClick = { selectedTopic = "延长电池寿命的方法" }
                )

                KnowledgeCard(
                    title = "最佳充电习惯",
                    description = "20%-80%电量区间、避免过充",
                    icon = Icons.Default.Power,
                    onClick = { selectedTopic = "最佳充电习惯" }
                )

                KnowledgeCard(
                    title = "快充对电池的影响",
                    description = "快充技术原理和注意事项",
                    icon = Icons.Default.Bolt,
                    onClick = { selectedTopic = "快充对电池的影响" }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 品牌SN规则
                Text(
                    text = "品牌SN规则",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                KnowledgeCard(
                    title = "Apple SN编码规则",
                    description = "12位序列号解码方法",
                    icon = Icons.Default.PhoneIphone,
                    onClick = { selectedTopic = "Apple SN编码规则" }
                )

                KnowledgeCard(
                    title = "Samsung SN编码规则",
                    description = "倒数第7位年份、第6位月份",
                    icon = Icons.Default.PhoneAndroid,
                    onClick = { selectedTopic = "Samsung SN编码规则" }
                )

                KnowledgeCard(
                    title = "华为/荣耀SN编码规则",
                    description = "第6-7位年份、第8-9位周次",
                    icon = Icons.Default.Devices,
                    onClick = { selectedTopic = "华为/荣耀SN编码规则" }
                )

                KnowledgeCard(
                    title = "其他品牌SN规则",
                    description = "小米、OPPO、vivo、联想等",
                    icon = Icons.Default.MoreHoriz,
                    onClick = { selectedTopic = "其他品牌SN规则" }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 常见问题
                Text(
                    text = "常见问题",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                KnowledgeCard(
                    title = "如何获取bugreport",
                    description = "Android设备日志导出方法",
                    icon = Icons.Default.Description,
                    onClick = { selectedTopic = "如何获取bugreport" }
                )

                KnowledgeCard(
                    title = "健康度等级含义",
                    description = "A+到F各等级的判定标准",
                    icon = Icons.Default.Grade,
                    onClick = { selectedTopic = "健康度等级含义" }
                )

                KnowledgeCard(
                    title = "何时需要更换电池",
                    description = "电池更换的判断标准",
                    icon = Icons.Default.Build,
                    onClick = { selectedTopic = "何时需要更换电池" }
                )
            }
        }
    }
}

@Composable
fun KnowledgeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                icon,
                contentDescription = title,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "查看详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TopicDetailScreen(
    topic: String,
    onBack: () -> Unit
) {
    val content = getTopicContent(topic)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 标题
        Text(
            text = topic,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 内容
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 返回按钮
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            Spacer(modifier = Modifier.width(8.dp))
            Text("返回列表")
        }
    }
}

fun getTopicContent(topic: String): String {
    return when (topic) {
        "锂电池工作原理" -> """
锂电池（Li-ion）的工作原理基于锂离子在正负极之间的移动。

【充电过程】
锂离子从正极（通常是钴酸锂、磷酸铁锂等）脱嵌，经过电解质和隔膜，嵌入负极（石墨）。同时，电子从外电路流向负极，保持电荷平衡。

【放电过程】
锂离子从负极脱嵌，回到正极，电子从外电路流向正极，产生电流供设备使用。

【关键参数】
- 额定电压：3.7V（单体）
- 充电限制电压：4.2V
- 放电终止电压：2.75V-3.0V
- 能量密度：150-260Wh/kg

【安全机制】
现代锂电池内置保护电路（PCM），防止过充、过放、过流和短路。
""".trimIndent()

        "电池老化机制" -> """
电池老化主要表现为容量衰减和内阻增长，原因包括：

【容量衰减】
1. SEI膜增厚：负极表面固体电解质界面（SEI）膜随循环增厚，消耗活性锂
2. 正极材料退化：钴酸锂等正极材料结构崩塌
3. 电解质分解：电解质在高温下分解，降低离子传导效率

【内阻增长】
1. 接触电阻增加：电极与集流体接触恶化
2. 电化学反应阻抗：SEI膜增厚阻碍离子传输
3. 电解质粘度增加：老化电解质流动性下降

【循环寿命】
- 手机电池：500次循环后容量降至80%
- 笔记本电池：800-1000次循环
- 磷酸铁锂电池：2000+次循环

【影响因素】
- 温度：每升高10°C，老化速率翻倍
- 充电速度：快充加速老化
- 放电深度：深度放电损伤更大
""".trimIndent()

        "温度对电池的影响" -> """
温度是影响电池性能和寿命的关键因素。

【高温影响（>35°C）】
1. 加速化学反应：老化速率随温度指数增长（阿伦尼乌斯方程）
2. 电解质分解：高温加速电解质挥发和分解
3. SEI膜不稳定：高温下SEI膜可能破裂重组，消耗更多锂
4. 热失控风险：极端高温可能导致电池起火

【低温影响（<10°C）】
1. 容量暂时下降：低温下离子迁移速率降低，可用容量减少
2. 充电困难：低温充电可能导致锂金属析出（析锂），造成永久损伤
3. 内阻增加：低温下电解质粘度增加，内阻显著上升

【最佳温度范围】
- 使用温度：15°C - 35°C
- 存储温度：20°C - 25°C
- 充电温度：10°C - 30°C

【建议】
- 避免高温环境使用和充电
- 冬季充电前预热设备
- 长期存储保持50%电量，存放于阴凉处
""".trimIndent()

        "延长电池寿命的方法" -> """
以下方法可有效延长电池使用寿命：

【电量管理】
1. 保持电量在20%-80%区间
   - 避免深度放电（<20%）
   - 避免过充（>95%）
2. 部分充电优于完全充电
   - 多次短时间充电比一次完整充电更健康

【充电习惯】
1. 使用原装充电器
   - 第三方充电器可能电压不稳定
2. 避免快充过频
   - 快充产生更多热量，加速老化
3. 充电时避免使用设备
   - 充电+使用=双重发热

【温度控制】
1. 避免高温环境充电
   - 车内、阳光下充电风险大
2. 充电时取下保护壳
   - 保护壳阻碍散热
3. 发热时暂停充电
   - 设备过热应等待冷却

【长期存储】
1. 保持50%电量
   - 满电或空电存储都会损伤电池
2. 定期检查充电
   - 每3个月检查并补充电量
3. 存放于阴凉干燥处
""".trimIndent()

        "最佳充电习惯" -> """
科学的充电习惯可显著延长电池寿命。

【充电时机】
1. 电量降至20%时开始充电
2. 电量达到80%时停止充电
3. 避免等到电量耗尽再充电

【充电方式】
1. 优先使用慢充
   - 5W-10W充电对电池最友好
2. 快充仅在紧急时使用
   - 快充发热量大，加速老化
3. 无线充电注意散热
   - 无线充电效率低，发热更多

【充电环境】
1. 温度15°C-25°C最佳
2. 避免充电时使用高负载应用
3. 充电时取下厚重的保护壳

【夜间充电建议】
1. 使用智能充电功能（部分手机支持）
   - 先充到80%，起床前再充满
2. 或使用定时插座
   - 避免整夜过充

【避免的行为】
- 边玩边充（尤其是游戏）
- 高温环境充电
- 使用劣质充电器
- 长期保持100%电量
""".trimIndent()

        "快充对电池的影响" -> """
快充技术带来便利，但也对电池有影响。

【快充原理】
快充通过提高充电功率（电压×电流）来缩短充电时间：
- 高压快充：提高电压（如9V/12V）
- 高电流快充：提高电流（如5A）
- 闪充技术：同时提高电压和电流

【对电池的影响】
1. 发热增加
   - P=I²R，电流越大发热越多
   - 高温加速电池老化

2. 内阻压力
   - 大电流通过内阻产生更多热量
   - 长期快充可能增加内阻

3. 极化效应
   - 快充时电化学反应跟不上
   - 可能导致电压虚高

【快充建议】
1. 日常使用慢充
2. 紧急时使用快充
3. 快充时避免使用设备
4. 快充后让设备冷却
5. 选择品牌原装快充

【快充技术对比】
- 18W快充：相对温和，影响较小
- 30W-65W快充：中等发热，适度使用
- 120W+快充：高发热，谨慎使用
""".trimIndent()

        "Apple SN编码规则" -> """
Apple设备序列号（SN）为12位字母数字组合。

【编码位置】
- 第4位：半年代码（年份+上半年/下半年）
- 第5位：周次代码（第1-52周）

【年份解码表】
| 代码 | 年份 | 半年 |
|------|------|------|
| C/D | 2010 | 上/下 |
| F/G | 2011 | 上/下 |
| H/J | 2012 | 上/下 |
| K/L | 2013 | 上/下 |
| M/N | 2014 | 上/下 |
| P/Q | 2015 | 上/下 |
| R/S | 2016 | 上/下 |
| T/V | 2017 | 上/下 |
| W/X | 2018 | 上/下 |
| Y/Z | 2019 | 上/下 |
| 0/1 | 2020 | 上/下 |
| 2/3 | 2021 | 上/下 |
| 4/5 | 2022 | 上/下 |
| 6/7 | 2023 | 上/下 |
| 8/9 | 2024 | 上/下 |
| A/B | 2025 | 上/下 |

【周次解码】
1-9 = 第1-9周
C = 第10周，D = 第11周...（跳过I/O）
每半年循环一次

【示例】
C3LXK2XXXXX
- C = 2010年上半年
- K = 第19周
- 生产日期：2010年5月

【官方查询】
https://checkcoverage.apple.com
""".trimIndent()

        "Samsung SN编码规则" -> """
Samsung设备序列号编码规则。

【编码位置】
- 倒数第7位：年份代码
- 倒数第6位：月份代码

【年份解码】
| 代码 | 年份 |
|------|------|
| R | 2023 |
| S | 2024 |
| T | 2025 |
| U | 2026 |
| V | 2027 |
| W | 2028 |

【月份解码】
| 代码 | 月份 |
|------|------|
| 1-9 | 1-9月 |
| A | 10月 |
| B | 11月 |
| C | 12月 |

【示例】
R5CR70H1N4（11位）
- 倒数第7位：R = 2023年
- 倒数第6位：0（无效，需查看实际数据）

【注意】
Samsung SN格式可能变化，部分型号编码规则不同。

【官方查询】
https://www.samsung.com/us/support/service
""".trimIndent()

        "华为/荣耀SN编码规则" -> """
华为和荣耀设备序列号编码规则相似。

【编码位置】
- 第6-7位：年份后两位
- 第8-9位：生产周次

【解码方法】
年份 = 2000 + 第6-7位数字
周次 = 第8-9位数字（1-52）
月份 ≈ 周次 / 4

【示例】
XXXXXX2315XXXXX
- 第6-7位：23 = 2023年
- 第8-9位：15 = 第15周
- 估算月份：15/4 ≈ 4月

【官方查询】
华为：https://consumer.huawei.com/cn/support/warranty-query
荣耀：https://www.hihonor.com/cn/support/warranty-query

【注意】
部分型号SN格式可能不同，建议通过官方渠道验证。
""".trimIndent()

        "其他品牌SN规则" -> """
其他品牌SN编码规则概览。

【小米】
- IMEI格式：15位纯数字，无法本地解码
- 自定义格式：部分型号第3-4位含年份
- 官方查询：https://www.mi.com/support/warranty

【OPPO】
- 格式多变，第4-5位可能含年份月份
- 官方查询：https://support.oppo.com/cn/warranty

【vivo】
- 第5-6位：年份后两位
- 第7-8位：周次或月份
- 官方查询：https://www.vivo.com.cn/service/warranty

【联想ThinkPad】
- 前4位：机型代码
- 第5位：年份代码（A=2010...R=2025）
- 官方查询：https://support.lenovo.com/us/en/warrantylookup

【HP】
- 第3-4位：年份和地区编码
- 官方查询：https://support.hp.com/check-warranty

【ASUS】
- 第2位：年份代码
- 第3位：月份代码
- 官方查询：https://www.asus.com/support/warranty

【Dell】
- 服务标签：5-7位字母数字
- 无法本地解码，必须官方查询
- 官方查询：https://www.dell.com/support
""".trimIndent()

        "如何获取bugreport" -> """
bugreport是Android系统诊断日志，包含电池详细信息。

【获取方法】
1. 开启开发者选项
   - 设置 → 关于手机 → 连续点击"版本号"7次

2. 启用USB调试
   - 设置 → 开发者选项 → USB调试

3. 连接电脑
   - 使用USB数据线连接电脑

4. 执行命令
   ```bash
   adb bugreport
   ```
   或指定输出路径：
   ```bash
   adb bugreport > bugreport.txt
   ```

5. 等待生成
   - bugreport生成需要几分钟
   - 输出为ZIP压缩包（包含多个文件）

【ZIP文件结构】
- bugreport-XXX.txt：主日志文件
- battery-history.txt：电池历史
- system_trace.txt：系统追踪

【关键信息位置】
- ro.product.brand：品牌
- ro.product.model：型号
- DesignCapacity：设计容量
- Min learned battery capacity：当前容量
- battery cycle count：循环次数

【注意事项】
- 生成的bugreport可能很大（几十MB）
- 包含敏感信息，请妥善保管
- 建议在电池信息完整时生成
""".trimIndent()

        "健康度等级含义" -> """
电池健康度等级判定标准。

【等级定义】
| 等级 | 健康度 | 状态描述 |
|------|--------|----------|
| A+ | ≥95% | 极佳，几乎无老化 |
| A | 90-95% | 良好，轻微老化 |
| B | 80-90% | 一般，中度老化 |
| C | 70-80% | 较差，明显老化 |
| D | 60-70% | 很差，严重老化 |
| F | <60% | 极差，建议更换 |

【判定因子】
1. 容量保持率（35%权重）
   - 当前容量 / 设计容量

2. 循环衰减（30%权重）
   - 基于循环次数的衰减模型

3. 内阻增长（15%权重）
   - 内阻相对于基准值的变化

4. 温度老化（10%权重）
   - 基于使用温度的老化估算

5. 充电损伤（10%权重）
   - 基于充电行为的损伤估算

【置信度】
- HIGH：4+因子可用
- MEDIUM：2-3因子可用
- LOW：1因子可用
- NONE：无可用因子

【建议】
- A+/A：继续保持良好习惯
- B：注意优化使用方式
- C/D：考虑更换电池
- F：尽快更换电池
""".trimIndent()

        "何时需要更换电池" -> """
电池更换的判断标准和建议。

【更换信号】
1. 健康度低于60%
   - 容量衰减严重，续航明显下降

2. 循环次数超过500次
   - 已达到手机电池设计寿命

3. 充电异常
   - 充电速度明显变慢
   - 充电时发热严重
   - 电量跳变（如从30%突然到10%）

4. 使用异常
   - 续航时间大幅缩短
   - 意外关机（电量显示还有20%却关机）
   - 电池鼓包（立即停止使用）

【更换时机】
- 建议健康度降至70%以下时考虑更换
- 建议循环次数超过400次时关注状态

【更换渠道】
1. 官方售后
   - 原装电池，质量保证
   - 价格较高，服务规范

2. 授权维修点
   - 品牌授权，相对可靠
   - 价格适中

3. 自行更换（有风险）
   - 需要技术和工具
   - 可能影响防水性能

【更换后建议】
- 前3次充电充满至100%
- 避立即使用快充
- 观察新电池表现

【注意事项】
- 鼓包电池立即停止使用
- 不要自行拆解鼓包电池
- 选择正规渠道更换
""".trimIndent()

        else -> "暂无详细内容"
    }
}