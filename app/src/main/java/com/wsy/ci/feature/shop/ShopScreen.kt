package com.wsy.ci.feature.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.core.db.LedgerEntity
import com.wsy.ci.core.db.ShopItemEntity
import com.wsy.ci.core.economy.DailyShop
import com.wsy.ci.core.economy.Rarity
import com.wsy.ci.core.util.TimeFormat

private val Rarity.emoji: String
    get() = when (this) {
        Rarity.COMMON -> "⚪"
        Rarity.RARE -> "🔵"
        Rarity.EPIC -> "🟣"
        Rarity.LEGENDARY -> "🟠"
    }

@Composable
fun ShopScreen(viewModel: ShopViewModel = viewModel()) {
    val items by viewModel.items.collectAsState()
    val picks by viewModel.picks.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val ledger by viewModel.ledger.collectAsState()
    val message by viewModel.message.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<ShopItemEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    text = { Text("上架商品") },
                    icon = { Text("＋") },
                    onClick = { editing = ShopItemEntity(name = "", priceCi = 100, rarity = Rarity.COMMON) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("商城", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text("💰 $balance CI") })
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("今日精选") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("货架") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("流水") })
            }
            when (tab) {
                0 -> DailyPicksGrid(
                    picks = picks.mapNotNull { pick ->
                        items.firstOrNull { it.id == pick.itemId }?.let { pick to it }
                    },
                    onBuy = { pick, item -> viewModel.purchase(item.id, pick.id) },
                )
                1 -> ShelfGrid(
                    items = items,
                    onBuy = { viewModel.purchase(it.id) },
                    onEdit = { editing = it },
                )
                2 -> LedgerList(ledger)
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
}

@Composable
private fun DailyPicksGrid(
    picks: List<Pair<com.wsy.ci.core.db.DailyPickEntity, ShopItemEntity>>,
    onBuy: (com.wsy.ci.core.db.DailyPickEntity, ShopItemEntity) -> Unit,
) {
    if (picks.isEmpty()) {
        Text(
            "今日精选为空——先去货架上架几件商品，明天 0 点自动刷新（上架后切回本页也会立即补抽）",
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 16.dp),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        items(picks, key = { it.first.id }) { (pick, item) ->
            Card {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${item.emoji} ${item.name}", style = MaterialTheme.typography.titleMedium)
                    Text("${item.rarity.emoji} ${item.rarity.label}", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${item.priceCi}",
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(
                            "${DailyShop.discountedPrice(item.priceCi, pick.discountPercent)} CI",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text("-${pick.discountPercent}%", color = MaterialTheme.colorScheme.error)
                    }
                    if (pick.purchased) {
                        Text("今日已购", color = MaterialTheme.colorScheme.outline)
                    } else {
                        Button(onClick = { onBuy(pick, item) }, modifier = Modifier.fillMaxWidth()) {
                            Text("兑换")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfGrid(
    items: List<ShopItemEntity>,
    onBuy: (ShopItemEntity) -> Unit,
    onEdit: (ShopItemEntity) -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            "货架空空如也，点右下角上架第一件奖励吧（比如：🎬 看一场电影）",
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 16.dp),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Card {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${item.emoji} ${item.name}", style = MaterialTheme.typography.titleMedium)
                    if (item.description.isNotBlank()) {
                        Text(item.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${item.rarity.emoji} ${item.rarity.label}", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${item.priceCi} CI",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onEdit(item) }) { Text("编辑") }
                        Button(onClick = { onBuy(item) }) { Text("兑换") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerList(entries: List<LedgerEntity>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.note.ifBlank { entry.type.name }, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${TimeFormat.shortDate(TimeFormat.millisToEpochDay(entry.at))} ${TimeFormat.clock(entry.at)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Text(
                    text = (if (entry.amount > 0) "+" else "") + "${entry.amount} CI",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (entry.amount > 0) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
        if (entries.isEmpty()) {
            item { Text("还没有流水，完成一次专注就有第一笔入账", color = MaterialTheme.colorScheme.outline) }
        }
    }
}
