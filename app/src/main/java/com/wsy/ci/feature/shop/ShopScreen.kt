/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wsy.ci.feature.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.R
import com.wsy.ci.core.db.DailyPickEntity
import com.wsy.ci.core.db.LedgerEntity
import com.wsy.ci.core.db.LedgerType
import com.wsy.ci.core.db.PurchaseEntity
import com.wsy.ci.core.db.ShopItemEntity
import com.wsy.ci.core.designsystem.CiBalanceChip
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiFunctionIcon
import com.wsy.ci.core.designsystem.CiPanelCard
import com.wsy.ci.core.designsystem.CiPasteImportDialog
import com.wsy.ci.core.designsystem.CiQualityChip
import com.wsy.ci.core.designsystem.CiScreenHeader
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTextField
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.CiUnderlineTabs
import com.wsy.ci.core.designsystem.formatCi
import com.wsy.ci.core.designsystem.formatSignedAmount
import com.wsy.ci.core.designsystem.tabularNums
import com.wsy.ci.core.economy.DailyShop
import com.wsy.ci.core.economy.Rarity
import com.wsy.ci.core.porting.ShopImport
import com.wsy.ci.core.shop.FulfillFilter
import com.wsy.ci.core.shop.PurchaseBoard
import com.wsy.ci.core.shop.PurchaseFilter
import com.wsy.ci.core.shop.TimeFilter
import com.wsy.ci.core.util.TimeFormat

private enum class ShopTab(val label: String) {
    PICKS("今日精选"),
    SHELF("货架"),
    MINE("我的"),
    LEDGER("流水"),
}

/** 精选卡：一行四张，宽度按行等分。 */
private const val PICK_CARDS_PER_ROW = 4

/** 品质的展示雅称，纯展示层映射，不动 [Rarity] 枚举本身。 */
private val Rarity.displayLabel: String
    get() = when (this) {
        Rarity.COMMON -> "普通 · 铅字"
        Rarity.RARE -> "稀有 · 铜币"
        Rarity.EPIC -> "史诗 · 翡翠"
        Rarity.LEGENDARY -> "传说 · 朱批"
    }

/** 流水条目缺备注时的兜底文案。 */
private val LedgerType.label: String
    get() = when (this) {
        LedgerType.EARN_TASK -> "专注入账"
        LedgerType.EARN_STREAK -> "连击加成"
        LedgerType.EARN_LEVELUP -> "升级奖励"
        LedgerType.EARN_QUEST_DONE -> "复利结算"
        LedgerType.SPEND_SHOP -> "商城兑换"
        LedgerType.ADJUST -> "手动调整"
    }

@Composable
fun ShopScreen(viewModel: ShopViewModel = viewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val picks by viewModel.picks.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val purchaseFilter by viewModel.purchaseFilter.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val aiPrice by viewModel.aiPrice.collectAsStateWithLifecycle()
    val importPending by viewModel.importPending.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(ShopTab.PICKS) }
    var editing by remember { mutableStateOf<ShopItemEntity?>(null) }
    var showAiInput by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            if (tab == ShopTab.SHELF) {
                FloatingActionButton(
                    onClick = {
                        editing = ShopItemEntity(name = "", priceCi = 100, rarity = Rarity.COMMON)
                    },
                    shape = CiShapes.fab,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(CiSizes.fab),
                ) {
                    CiFunctionIcon(
                        resourceId = R.drawable.ic_ci_add,
                        contentDescription = "新建商品",
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(CiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CiSpacing.md),
        ) {
            CiScreenHeader(
                title = "商城",
                subtitle = "把长期投入兑换成真实休息与奖励",
                trailing = { CiBalanceChip(balance) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CiUnderlineTabs(
                    options = ShopTab.entries,
                    selected = tab,
                    label = { it.label },
                    onSelect = { tab = it },
                    modifier = Modifier.weight(1f),
                )
                if (tab == ShopTab.SHELF) {
                    Row(
                        modifier = Modifier.padding(bottom = CiSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                    ) {
                        OutlinedButton(onClick = { showAiInput = true }, shape = CiShapes.pill) {
                            CiFunctionIcon(
                                resourceId = R.drawable.ic_ci_ai_schedule,
                                contentDescription = null,
                                modifier = Modifier.size(CiSizes.compactIcon),
                            )
                            Text(
                                "AI 估价上架",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = CiSpacing.xs),
                            )
                        }
                        OutlinedButton(onClick = { showImport = true }, shape = CiShapes.pill) {
                            CiFunctionIcon(
                                resourceId = R.drawable.ic_ci_import,
                                contentDescription = null,
                                modifier = Modifier.size(CiSizes.compactIcon),
                            )
                            Text(
                                "粘贴批量上架",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = CiSpacing.xs),
                            )
                        }
                    }
                }
            }

            when (tab) {
                ShopTab.PICKS -> DailyPicksWall(
                    picks = picks.mapNotNull { pick ->
                        items.firstOrNull { it.id == pick.itemId }?.let { pick to it }
                    },
                    balance = balance,
                    onBuy = { pick, item -> viewModel.purchase(item.id, pick.id) },
                    modifier = Modifier.weight(1f),
                )
                ShopTab.SHELF -> ShelfList(
                    items = items,
                    balance = balance,
                    onBuy = { viewModel.purchase(it.id) },
                    onEdit = { editing = it },
                    modifier = Modifier.weight(1f),
                )
                ShopTab.MINE -> PurchaseList(
                    purchases = purchases,
                    filter = purchaseFilter,
                    onToggleRarity = viewModel::toggleRarityFilter,
                    onSetFulfill = viewModel::setFulfillFilter,
                    onSetTime = viewModel::setTimeFilter,
                    onReset = viewModel::resetPurchaseFilter,
                    onToggleFulfilled = viewModel::toggleFulfilled,
                    modifier = Modifier.weight(1f),
                )
                ShopTab.LEDGER -> LedgerList(ledger, modifier = Modifier.weight(1f))
            }
        }
    }

    editing?.let { item ->
        ShopItemEditorDialog(
            initial = item,
            onSave = viewModel::saveItem,
            onDelete = if (item.id != 0L) viewModel::removeItem else null,
            onDismiss = { editing = null },
        )
    }

    if (showImport) {
        CiPasteImportDialog(
            title = "粘贴批量上架",
            hint = "把「复制模板」的内容发给任何 AI（或自己写），列好想要的奖励后粘贴到下面。" +
                "校验通过后会先列出要上架的商品，确认了才真上架；同名商品会自动跳过。",
            template = ShopImport.TEMPLATE,
            pasteLabel = "粘贴商品 JSON",
            preview = importPending?.preview,
            result = importResult,
            onPreview = viewModel::previewImport,
            onConfirm = viewModel::confirmImport,
            onCancelPreview = viewModel::cancelImportPreview,
            onDismissResult = viewModel::dismissImportResult,
            onDismiss = {
                viewModel.cancelImportPreview()
                showImport = false
            },
        )
    }

    if (showAiInput && aiPrice !is ShopViewModel.AiPriceState.Draft) {
        AiPriceInputDialog(
            loading = aiPrice is ShopViewModel.AiPriceState.Loading,
            onSubmit = viewModel::requestAiPrice,
            onDismiss = {
                showAiInput = false
                viewModel.dismissAiPrice()
            },
        )
    }
    LaunchedEffect(aiPrice) {
        if (aiPrice is ShopViewModel.AiPriceState.Draft) showAiInput = false
    }
    (aiPrice as? ShopViewModel.AiPriceState.Draft)?.let { draft ->
        ShopItemEditorDialog(
            initial = draft.item,
            onSave = viewModel::saveItem,
            onDelete = null,
            onDismiss = { viewModel.dismissAiPrice() },
        )
    }
}

@Composable
private fun AiPriceInputDialog(
    loading: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CiShapes.dialog,
        title = { Text("AI 估价上架") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                CiTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "奖励名（如：看一场电影 / iPhone 17）",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loading) {
                    Text(
                        text = "估价中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name) },
                enabled = !loading && name.isNotBlank(),
                shape = CiShapes.pill,
            ) { Text("估价") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 今日精选：一行四张卡，顶部品质色条 + 折扣角标 + 原价删除线。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyPicksWall(
    picks: List<Pair<DailyPickEntity, ShopItemEntity>>,
    balance: Long,
    onBuy: (DailyPickEntity, ShopItemEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (picks.isEmpty()) {
        EmptyHint(
            text = "今日精选为空——先去货架上架几件商品，明天 0 点自动刷新" +
                "（上架后切回本页也会立即补抽）",
            modifier = modifier,
        )
        return
    }
    FlowRow(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.md),
        maxItemsInEachRow = PICK_CARDS_PER_ROW,
    ) {
        picks.forEach { (pick, item) ->
            PickCard(pick, item, balance, onBuy, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PickCard(
    pick: DailyPickEntity,
    item: ShopItemEntity,
    balance: Long,
    onBuy: (DailyPickEntity, ShopItemEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quality = CiTheme.colors.quality(item.rarity)
    val paidPrice = DailyShop.discountedPrice(item.priceCi, pick.discountPercent)
    Card(
        modifier = modifier,
        shape = CiShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            Column {
                // 顶部 4dp 品质色条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CiSizes.qualityStripe)
                        .background(quality.accent)
                )
                Column(
                    modifier = Modifier.padding(CiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                ) {
                    CiQualityChip(rarity = item.rarity, label = item.rarity.displayLabel)
                    Text(
                        text = "${item.emoji} ${item.name}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                    ) {
                        Text(
                            text = formatCi(item.priceCi),
                            style = MaterialTheme.typography.bodySmall.tabularNums(),
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatCi(
                                paidPrice
                            ),
                            style = MaterialTheme.typography.titleLarge.tabularNums(),
                            color = quality.accent,
                        )
                    }
                    if (pick.purchased) {
                        CiChip(
                            text = "今日已购",
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            verticalPadding = 10.dp,
                        )
                    } else {
                        Button(
                            onClick = { onBuy(pick, item) },
                            enabled = balance >= paidPrice,
                            shape = CiShapes.pill,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (balance >= paidPrice) "兑换" else "余额不足",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
            CiChip(
                text = "-${pick.discountPercent}%",
                container = MaterialTheme.colorScheme.secondary,
                content = MaterialTheme.colorScheme.onSecondary,
                style = MaterialTheme.typography.labelSmall,
                horizontalPadding = 10.dp,
                verticalPadding = 3.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            )
        }
    }
}

/** 货架：64dp 列表行。 */
@Composable
private fun ShelfList(
    items: List<ShopItemEntity>,
    balance: Long,
    onBuy: (ShopItemEntity) -> Unit,
    onEdit: (ShopItemEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyHint(
            text = "货架空空如也，点右下角上架第一件奖励吧（比如：🎬 看一场电影）",
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier
            .clip(CiShapes.field)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CiShapes.field)
    ) {
        items(items, key = { it.id }) { item ->
            val quality = CiTheme.colors.quality(item.rarity)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CiSizes.shelfRowHeight)
                    .padding(horizontal = CiSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.sm),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(item.emoji, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CiQualityChip(rarity = item.rarity, label = item.rarity.label)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.md),
                ) {
                    Text(
                        text = formatCi(item.priceCi),
                        style = MaterialTheme.typography.labelLarge.tabularNums(),
                        color = quality.accent,
                    )
                    Text(
                        text = "编辑",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onEdit(item) },
                    )
                    Button(
                        onClick = { onBuy(item) },
                        enabled = balance >= item.priceCi,
                        shape = CiShapes.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        contentPadding = PaddingValues(
                            horizontal = CiSpacing.md,
                            vertical = CiSpacing.xs,
                        ),
                    ) {
                        Text(
                            if (balance >= item.priceCi) "兑换" else "余额不足",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** 流水：56dp 列表行，收入走 ciIncome、支出走 ciExpense。 */
@Composable
private fun LedgerList(entries: List<LedgerEntity>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) {
        EmptyHint(text = "还没有流水，完成一次专注就有第一笔入账", modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier
            .clip(CiShapes.field)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CiShapes.field)
    ) {
        items(entries, key = { it.id }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CiSizes.ledgerRowHeight)
                    .padding(horizontal = CiSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = entry.note.ifBlank { entry.type.label },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${TimeFormat.shortDate(TimeFormat.millisToEpochDay(entry.at))} " +
                            TimeFormat.clock(entry.at),
                        style = MaterialTheme.typography.labelSmall.tabularNums(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatSignedAmount(entry.amount),
                    style = MaterialTheme.typography.labelLarge.tabularNums(),
                    color = if (entry.amount >= 0) {
                        CiTheme.colors.income
                    } else {
                        CiTheme.colors.expense
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * 「我的」：已兑换的奖励清单。花掉 CI 只是买下了权利，真去兑现了才手动标「已实现」，
 * 所以未实现的排在前面当待办看，已实现的沉到下面当战利品看。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurchaseList(
    purchases: List<PurchaseEntity>,
    filter: PurchaseFilter,
    onToggleRarity: (Rarity) -> Unit,
    onSetFulfill: (FulfillFilter) -> Unit,
    onSetTime: (TimeFilter) -> Unit,
    onReset: () -> Unit,
    onToggleFulfilled: (PurchaseEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (purchases.isEmpty()) {
        EmptyHint(text = "还没兑换过奖励，攒够 CI 去货架上挑一个吧", modifier = modifier)
        return
    }
    val shown = PurchaseBoard.apply(purchases, filter, System.currentTimeMillis())
    val doneCount = purchases.count { it.fulfilled }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
        PurchaseFilterBar(
            filter = filter,
            onToggleRarity = onToggleRarity,
            onSetFulfill = onSetFulfill,
            onSetTime = onSetTime,
            onReset = onReset,
        )
        Text(
            text = "共 ${purchases.size} 件 · 已实现 $doneCount 件 · " +
                "待实现 ${purchases.size - doneCount} 件" +
                if (shown.size != purchases.size) " · 当前筛选出 ${shown.size} 件" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (shown.isEmpty()) {
            EmptyHint(
                text = "没有符合当前筛选条件的记录，点「重置」看全部",
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .clip(CiShapes.field)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CiShapes.field)
        ) {
            items(shown, key = { it.id }) { purchase ->
                PurchaseRow(purchase = purchase, onToggle = { onToggleFulfilled(purchase) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** 筛选条：品质多选 + 状态单选 + 时间单选，默认全不限，右侧一键重置。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurchaseFilterBar(
    filter: PurchaseFilter,
    onToggleRarity: (Rarity) -> Unit,
    onSetFulfill: (FulfillFilter) -> Unit,
    onSetTime: (TimeFilter) -> Unit,
    onReset: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
    ) {
        FilterCaption("品质")
        Rarity.entries.forEach { rarity ->
            val selected = rarity in filter.rarities
            val quality = CiTheme.colors.quality(rarity)
            FilterChipItem(
                text = rarity.label,
                selected = selected,
                container = if (selected) quality.container else null,
                content = if (selected) quality.accent else null,
                onClick = { onToggleRarity(rarity) },
            )
        }
        FilterCaption("状态")
        FulfillFilter.entries.forEach { option ->
            FilterChipItem(
                text = option.label,
                selected = filter.fulfill == option,
                onClick = { onSetFulfill(option) },
            )
        }
        FilterCaption("时间")
        TimeFilter.entries.forEach { option ->
            FilterChipItem(
                text = option.label,
                selected = filter.time == option,
                onClick = { onSetTime(option) },
            )
        }
        if (!filter.isDefault) {
            TextButton(onClick = onReset) {
                Text("重置", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun FilterCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = CiSpacing.xs),
    )
}

@Composable
private fun FilterChipItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    container: androidx.compose.ui.graphics.Color? = null,
    content: androidx.compose.ui.graphics.Color? = null,
) {
    CiChip(
        text = text,
        container = container ?: if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        content = content ?: if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.labelMedium,
        borderColor = if (selected) MaterialTheme.colorScheme.onSurface else null,
        modifier = Modifier
            .heightIn(min = CiSizes.fieldHeight)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
    )
}

@Composable
private fun PurchaseRow(purchase: PurchaseEntity, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CiSizes.shelfRowHeight)
            // 已实现的压一层阴影底色，整行看起来是「沉下去」的，和待兑现的一眼分开
            .then(
                if (purchase.fulfilled) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = CiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.sm),
    ) {
        CiQualityChip(purchase.rarity, purchase.rarity.label)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = purchase.itemName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (purchase.fulfilled) TextDecoration.LineThrough else null,
            )
            Text(
                text = "${TimeFormat.shortDate(TimeFormat.millisToEpochDay(purchase.at))} " +
                    "${TimeFormat.clock(purchase.at)} · 花费 ${formatCi(purchase.pricePaid)}",
                style = MaterialTheme.typography.labelSmall.tabularNums(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CiChip(
            text = if (purchase.fulfilled) "已实现" else "未实现",
            container = if (purchase.fulfilled) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            content = if (purchase.fulfilled) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelMedium,
            leadingIcon = if (purchase.fulfilled) R.drawable.ic_ci_complete else null,
            iconContentDescription = if (purchase.fulfilled) "已实现" else null,
        )
        if (purchase.fulfilled) {
            TextButton(onClick = onToggle) {
                Text("撤销", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Button(
                onClick = onToggle,
                shape = CiShapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                contentPadding = PaddingValues(horizontal = CiSpacing.md, vertical = CiSpacing.xs),
            ) {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_complete,
                    contentDescription = null,
                    modifier = Modifier.size(CiSizes.compactIcon),
                )
                Text(
                    "已实现",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = CiSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    CiPanelCard(modifier = modifier.fillMaxWidth(), contentPadding = CiSpacing.lg) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
