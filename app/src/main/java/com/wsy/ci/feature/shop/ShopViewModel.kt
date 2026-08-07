package com.wsy.ci.feature.shop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.core.data.PurchaseResult
import com.wsy.ci.core.db.DailyPickEntity
import com.wsy.ci.core.db.LedgerEntity
import com.wsy.ci.core.db.PurchaseEntity
import com.wsy.ci.core.db.ShopItemEntity
import com.wsy.ci.core.economy.Rarity
import com.wsy.ci.core.porting.ImportPreview
import com.wsy.ci.core.porting.ImportShopItem
import com.wsy.ci.core.porting.ShopImport
import com.wsy.ci.core.porting.ShopImportResult
import com.wsy.ci.core.porting.previewShop
import com.wsy.ci.core.shop.FulfillFilter
import com.wsy.ci.core.shop.PurchaseFilter
import com.wsy.ci.core.shop.TimeFilter
import com.wsy.ci.core.util.currentEpochDayFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as CiApp).container.shopRepository

    val items: StateFlow<List<ShopItemEntity>> = repo.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val picks: StateFlow<List<DailyPickEntity>> = currentEpochDayFlow()
        .onEach { repo.ensurePicks(it) }
        .flatMapLatest { repo.observePicks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val balance: StateFlow<Long> = repo.observeBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val ledger: StateFlow<List<LedgerEntity>> = repo.observeLedger()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchases: StateFlow<List<PurchaseEntity>> = repo.observePurchases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val message = MutableStateFlow<String?>(null)

    /** 「我的」的筛选条件，默认全不限。 */
    val purchaseFilter = MutableStateFlow(PurchaseFilter())

    fun toggleRarityFilter(rarity: Rarity) {
        val current = purchaseFilter.value
        val next = if (rarity in current.rarities) {
            current.rarities - rarity
        } else {
            current.rarities + rarity
        }
        purchaseFilter.value = current.copy(rarities = next)
    }

    fun setFulfillFilter(fulfill: FulfillFilter) {
        purchaseFilter.value = purchaseFilter.value.copy(fulfill = fulfill)
    }

    fun setTimeFilter(time: TimeFilter) {
        purchaseFilter.value = purchaseFilter.value.copy(time = time)
    }

    fun resetPurchaseFilter() {
        purchaseFilter.value = PurchaseFilter()
    }

    fun saveItem(item: ShopItemEntity) {
        viewModelScope.launch {
            if (item.id == 0L) repo.addItem(item) else repo.updateItem(item)
        }
    }

    fun removeItem(item: ShopItemEntity) {
        viewModelScope.launch { repo.removeItem(item) }
    }

    /** 「我的」里切换已实现 / 未实现。 */
    fun toggleFulfilled(purchase: PurchaseEntity) {
        viewModelScope.launch {
            repo.setPurchaseFulfilled(purchase, !purchase.fulfilled)
            message.value = if (purchase.fulfilled) {
                "「${purchase.itemName}」已改回未实现"
            } else {
                "✅ 「${purchase.itemName}」已标记为实现"
            }
        }
    }

    fun purchase(itemId: Long, pickId: Long? = null) {
        viewModelScope.launch {
            when (val result = repo.purchase(itemId, pickId)) {
                is PurchaseResult.Success ->
                    message.value = "🎉 已兑换「${result.item.name}」，花费 ${result.paid} CI"
                is PurchaseResult.NotEnough ->
                    message.value = "余额不足：需要 ${result.price} CI，当前 ${result.balance} CI"
                PurchaseResult.NotFound ->
                    message.value = "商品不存在"
                PurchaseResult.Unavailable ->
                    message.value = "今日精选已失效或已兑换"
            }
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    // ---------- AI 估价上架 ----------

    sealed interface AiPriceState {
        data object Idle : AiPriceState
        data object Loading : AiPriceState
        /** 估好价的商品草稿，交编辑对话框确认。 */
        data class Draft(val item: ShopItemEntity) : AiPriceState
    }

    val aiPrice = MutableStateFlow<AiPriceState>(AiPriceState.Idle)

    fun requestAiPrice(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            aiPrice.value = AiPriceState.Loading
            when (val r = (getApplication<Application>() as CiApp).container.llmService.priceItem(name)) {
                is com.wsy.ci.llm.LlmParsed.Ok -> {
                    val (priceCi, rarity, priced) = r.value
                    aiPrice.value = AiPriceState.Draft(
                        ShopItemEntity(
                            name = name.trim(),
                            description = priced.description,
                            emoji = priced.emoji.ifBlank { "🎁" },
                            priceCi = priceCi,
                            rarity = rarity,
                        )
                    )
                }
                is com.wsy.ci.llm.LlmParsed.Err -> {
                    message.value = r.message
                    aiPrice.value = AiPriceState.Idle
                }
            }
        }
    }

    fun dismissAiPrice() {
        aiPrice.value = AiPriceState.Idle
    }

    // ---------- 粘贴上架（一次导入一批奖励） ----------

    /** 校验通过、等用户点「确认导入」的一批商品。 */
    data class ImportPending(val items: List<ImportShopItem>, val preview: ImportPreview)

    /** 待确认的上架清单；null 表示还停在粘贴框。 */
    val importPending = MutableStateFlow<ImportPending?>(null)

    /** 导入结果文案；null 表示还没导入。以 ✅ 开头表示成功。 */
    val importResult = MutableStateFlow<String?>(null)

    /** 只校验、只出清单，货架先不动——上架要等 [confirmImport]。 */
    fun previewImport(text: String) {
        viewModelScope.launch {
            when (val parsed = ShopImport.parse(text)) {
                is ShopImportResult.Err ->
                    importResult.value = "❌ 校验未通过：\n" + parsed.errors.joinToString("\n") { "· $it" }
                is ShopImportResult.Ok -> importPending.value = ImportPending(
                    items = parsed.items,
                    preview = previewShop(parsed.items, repo.allItemNames().toSet()),
                )
            }
        }
    }

    fun confirmImport() {
        val pending = importPending.value ?: return
        viewModelScope.launch {
            importResult.value = applyImport(pending.items)
            importPending.value = null
        }
    }

    fun cancelImportPreview() {
        importPending.value = null
    }

    /** 按商品名跳过已有的（含已下架的），免得重复粘贴把货架撑成两份。 */
    private suspend fun applyImport(items: List<ImportShopItem>): String {
        val existing = repo.allItemNames().toSet()
        val (duplicated, fresh) = items.partition { it.name.trim() in existing }
        fresh.forEach { repo.addItem(ShopImport.toEntity(it)) }
        return buildString {
            append("✅ 已上架 ${fresh.size} 件")
            if (duplicated.isNotEmpty()) {
                append("\n⚠️ 跳过 ${duplicated.size} 件同名商品：")
                append(duplicated.joinToString("、") { it.name.trim() })
            }
        }
    }

    fun dismissImportResult() {
        importResult.value = null
        importPending.value = null
    }
}
