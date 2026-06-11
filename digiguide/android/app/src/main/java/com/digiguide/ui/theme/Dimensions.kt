package com.digiguide.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 尺寸定义 - 支持多分辨率适配
 * 使用dp单位，自动适配不同屏幕密度
 */
object Dimensions {

    // ========== 间距尺寸 ==========

    // 基础间距
    val spacingExtraSmall = 2.dp
    val spacingSmall = 4.dp
    val spacingMedium = 8.dp
    val spacingLarge = 12.dp
    val spacingExtraLarge = 16.dp
    val spacingDoubleExtraLarge = 24.dp
    val spacingTripleExtraLarge = 32.dp

    // 卡片内边距
    val cardPaddingSmall = 8.dp
    val cardPaddingMedium = 12.dp
    val cardPaddingLarge = 16.dp
    val cardPaddingExtraLarge = 20.dp

    // 页面边距
    val pagePaddingSmall = 12.dp
    val pagePaddingMedium = 16.dp
    val pagePaddingLarge = 20.dp

    // ========== 组件尺寸 ==========

    // 图标尺寸
    val iconSizeSmall = 16.dp
    val iconSizeMedium = 24.dp
    val iconSizeLarge = 32.dp
    val iconSizeExtraLarge = 48.dp
    val iconSizeHuge = 64.dp

    // 按钮尺寸
    val buttonHeightSmall = 36.dp
    val buttonHeightMedium = 44.dp
    val buttonHeightLarge = 52.dp
    val buttonHeightExtraLarge = 60.dp

    // 卡片尺寸
    val cardElevation = 2.dp
    val cardCornerRadiusSmall = 4.dp
    val cardCornerRadiusMedium = 8.dp
    val cardCornerRadiusLarge = 12.dp
    val cardCornerRadiusExtraLarge = 16.dp

    // 头像/徽章尺寸
    val avatarSizeSmall = 32.dp
    val avatarSizeMedium = 48.dp
    val avatarSizeLarge = 64.dp
    val badgeSize = 24.dp

    // ========== 列表尺寸 ==========

    // 列表项高度
    val listItemHeightSmall = 48.dp
    val listItemHeightMedium = 64.dp
    val listItemHeightLarge = 80.dp

    // 列表间距
    val listItemSpacing = 8.dp
    val listContentPadding = 16.dp

    // ========== 输入框尺寸 ==========

    // 输入框高度
    val inputFieldHeightSmall = 40.dp
    val inputFieldHeightMedium = 48.dp
    val inputFieldHeightLarge = 56.dp

    // 输入框圆角
    val inputFieldCornerRadius = 8.dp

    // ========== 进度条尺寸 ==========

    // 进度条高度
    val progressBarHeightSmall = 4.dp
    val progressBarHeightMedium = 8.dp
    val progressBarHeightLarge = 12.dp

    // ========== 分隔线尺寸 ==========

    val dividerThickness = 1.dp
    val dividerVerticalSpacing = 8.dp

    // ========== 特殊组件尺寸 ==========

    // 健康度等级徽章
    val healthGradeBadgeSize = 56.dp

    // 循环进度条
    val cycleProgressHeight = 10.dp

    // 结果项间距
    val resultItemVerticalPadding = 4.dp
}

/**
 * 预定义的Modifier扩展
 * 方便在代码中直接使用
 */
object ModifierExtensions {
    // 这些可以在实际使用时通过Modifier.padding(Dimensions.xxx)等方式使用
}