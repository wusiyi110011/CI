package com.wsy.ci.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * 4dp 基准网格上的间距刻度。括号内为设计文档里的 token 名。
 */
object CiSpacing {
    /** space050 */
    val xxs = 4.dp

    /** space100 */
    val xs = 8.dp

    /** space150 */
    val sm = 12.dp

    /** space200 */
    val md = 16.dp

    /** space300 —— 内容区统一内边距 */
    val lg = 24.dp

    /** space400 */
    val xl = 32.dp

    /** space600 */
    val xxl = 48.dp

    /** space800 */
    val xxxl = 64.dp
}

/** 固定尺寸常量：来自逐屏布局规格，避免散落的魔法数。 */
object CiSizes {
    /** 账页分隔线与控件描边。 */
    val border = 1.dp

    /** 左侧 NavigationRail 宽度。 */
    val navRailWidth = 104.dp

    /** NavigationRail 单项的点击区域边长。 */
    val navRailItem = 72.dp

    /** NavigationRail 图标边长。 */
    val navRailIcon = 28.dp
    val navRailAiIcon = 48.dp

    /** 手绘功能图标尺寸：源图留白较多，显示框需略大于普通字形。 */
    val actionIcon = 28.dp
    val compactIcon = 22.dp
    /** 28dp 图标置于 48dp 触控框时的内边距。 */
    val iconTouchPadding = 10.dp
    val featureIcon = 48.dp
    val balanceIcon = 42.dp

    /** 时间线左侧时刻尺宽度。 */
    val timeRulerWidth = 48.dp

    /** 进行中计时卡高度。 */
    val timerCardHeight = 176.dp

    /** 头部行 / Tab 行高度。 */
    val headerRowHeight = 48.dp

    /** 输入框、选择器行高。 */
    val fieldHeight = 48.dp

    /** 分段控件高度。 */
    val segmentedHeight = 40.dp

    /** 分段控件内缩与选中指示线。 */
    val segmentedInset = 4.dp
    val selectionIndicator = 2.dp

    /** 常规下拉选择器宽度。 */
    val dropdownWidth = 240.dp

    /** 平板设置页左侧分类栏宽度。 */
    val settingsMenuWidth = 176.dp

    /** FAB 边长。 */
    val fab = 56.dp

    /** 进度条 / 经验条高度。 */
    val progressBar = 8.dp

    /** 精选卡顶部品质色条高度。 */
    val qualityStripe = 4.dp

    /** 时间线当前时刻线粗细。 */
    val currentTimeLine = 2.dp

    /** 任务块左侧强调条宽度。 */
    val blockAccent = 3.dp

    /** 表单类对话框宽度。 */
    val dialogFormWidth = 560.dp

    /** 庆祝类对话框宽度。 */
    val dialogCelebrateWidth = 480.dp

    /** 含长列表的详情对话框宽度（任务线详情）。 */
    val dialogWideWidth = 720.dp

    /** 详情对话框内可滚动区域的最大高度，避免长列表撑破屏幕。 */
    val dialogScrollMaxHeight = 420.dp

    /** 列表行高：货架。 */
    val shelfRowHeight = 64.dp

    /** 列表行高：流水 / 模型路由。 */
    val ledgerRowHeight = 56.dp
}

/** Elevation 刻度，见设计规格「Elevation」一节。 */
object CiElevation {
    /** 扁平列表行 / NavigationRail。 */
    val flat = 0.dp

    /** 卡片静止态。 */
    val card = 1.dp

    /** 卡片 hover / 拖拽中。 */
    val cardRaised = 3.dp

    /** 对话框 / FAB 静止态。 */
    val dialog = 6.dp

    /** FAB 按下态。 */
    val fabPressed = 8.dp

    /** 升级 / 入账庆祝浮层。 */
    val celebrate = 12.dp
}
