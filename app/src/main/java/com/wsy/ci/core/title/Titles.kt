package com.wsy.ci.core.title

import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.economy.Economy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 领域头衔线：LLM 生成 6 级头衔名（titlesJson），离线时用通用兜底表。 */
object Titles {

    val DEFAULT = listOf("学徒", "入门", "熟练", "精通", "专家", "宗师")

    private val json = Json { ignoreUnknownKeys = true }

    /** 解析领域的 6 级头衔名；JSON 非法或数量不足时退回兜底表。 */
    fun titleLine(domain: DomainEntity): List<String> {
        val raw = domain.titlesJson ?: return DEFAULT
        return try {
            val parsed = json.decodeFromString<List<String>>(raw)
            if (parsed.size >= Economy.MAX_LEVEL) parsed.take(Economy.MAX_LEVEL) else DEFAULT
        } catch (_: Exception) {
            DEFAULT
        }
    }

    /** 当前头衔名。 */
    fun currentTitle(domain: DomainEntity): String {
        val level = Economy.levelForExp(domain.totalExp)
        return titleLine(domain)[level - 1]
    }

    fun encode(titles: List<String>): String = json.encodeToString(titles)
}
