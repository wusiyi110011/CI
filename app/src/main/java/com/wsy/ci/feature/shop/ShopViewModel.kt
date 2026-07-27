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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShopViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as CiApp).container.shopRepository

    val items: StateFlow<List<ShopItemEntity>> = repo.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val picks: StateFlow<List<DailyPickEntity>> = repo.observeTodayPicks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val balance: StateFlow<Long> = repo.observeBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val ledger: StateFlow<List<LedgerEntity>> = repo.observeLedger()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchases: StateFlow<List<PurchaseEntity>> = repo.observePurchases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { repo.ensureTodayPicks() }
    }

    fun saveItem(item: ShopItemEntity) {
        viewModelScope.launch {
            if (item.id == 0L) repo.addItem(item) else repo.updateItem(item)
        }
    }

    fun removeItem(item: ShopItemEntity) {
        viewModelScope.launch { repo.removeItem(item) }
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
            }
        }
    }

    fun dismissMessage() {
        message.value = null
    }
}
