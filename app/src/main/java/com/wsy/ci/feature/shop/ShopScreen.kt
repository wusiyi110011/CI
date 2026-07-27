package com.wsy.ci.feature.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.core.db.DailyPickEntity
import com.wsy.ci.core.db.LedgerEntity
import com.wsy.ci.core.db.LedgerType
import com.wsy.ci.core.db.ShopItemEntity
import com.wsy.ci.core.designsystem.CiBalanceChip
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiPanelCard
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
import com.wsy.ci.core.util.TimeFormat

private enum class ShopTab(val label: String) {
    PICKS("今日精选"),
    SHELF("货架"),
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
        LedgerType.SPEND_SHOP -> "商城兑换"
        LedgerType.ADJUST -> "手动调整"
    }

@Composable
fun ShopScreen(viewModel: ShopViewModel = viewModel()) {
    val items by viewModel.items.collectAsState()
    val picks by viewModel.picks.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val ledger by viewModel.ledger.collectAsState()
    val message by viewModel.message.collectAsState()
    val aiPrice by viewModel.aiPrice.collectAsState()

    var tab by remember { mutableStateOf(ShopTab.PICKS) }
    var editing by remember { mutableStateOf<ShopItemEntity?>(null) }
    var showAiInput by remember { mutableStateOf(false) }
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(CiSizes.fab),
                ) {
                    Text("＋", style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(CiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CiSpacing.md),
        ) {
            CiScreenHeader(title = "商城", trailing = { CiBalanceChip(balance) })

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
                    OutlinedButton(
                        onClick = { showAiInput = true },
                        shape = CiShapes.pill,
                        modifier = Modifier.padding(bottom = CiSpacing.xs),
                    ) {
                        Text("🤖 AI 估价上架", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            when (tab) {
                ShopTab.PICKS -> DailyPicksWall(
                    picks = picks.mapNotNull { pick ->
                        items.firstOrNull { it.id == pick.itemId }?.let { pick to it }
                    },
                    onBuy = { pick, item -> viewModel.purchase(item.id, pick.id) },
                    modifier = Modifier.weight(1f),
                )
                ShopTab.SHELF -> ShelfList(
                    items = items,
                    onBuy = { viewModel.purchase(it.id) },
                    onEdit = { editing = it },
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

    if (showAiInput) {
        AiPriceInputDialog(
            loading = aiPrice is ShopViewModel.AiPriceState.Loading,
            onSubmit = viewModel::requestAiPrice,
            onDismiss = {
                showAiInput = false
                viewModel.dismissAiPrice()
            },
        )
    }
    (aiPrice as? ShopViewModel.AiPriceState.Draft)?.let { draft ->
        showAiInput = false
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
            PickCard(pick, item, onBuy, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PickCard(
    pick: DailyPickEntity,
    item: ShopItemEntity,
    onBuy: (DailyPickEntity, ShopItemEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quality = CiTheme.colors.quality(item.rarity)
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
                                DailyShop.discountedPrice(item.priceCi, pick.discountPercent)
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
                            shape = CiShapes.pill,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("兑换", style = MaterialTheme.typography.labelLarge)
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
                        Text("兑换", style = MaterialTheme.typography.labelMedium)
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
