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

package com.wsy.ci.core.porting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPreviewTest {

    private val plan = CiImportFile(
        domain = ImportDomain(name = "深度学习", titles = listOf("a", "b", "c", "d", "e", "f")),
        quests = listOf(
            ImportQuest(type = "MAIN", title = "主线A", deadline = "2026-12-31"),
            ImportQuest(type = "SIDE", title = "背单词"),
        ),
        tasks = listOf(
            ImportTask(
                title = "看书",
                date = "2026-07-29",
                start = "19:00",
                end = "20:30",
                difficulty = "HARD",
                quest = "主线A",
            ),
        ),
    )

    @Test
    fun `计划预览按领域任务线任务分三段`() {
        val preview = previewPlan(plan)

        assertEquals(listOf("领域", "任务线 2 条", "任务 1 个"), preview.sections.map { it.heading })
        assertTrue(preview.summary.contains("任务线 2 条"))
        assertTrue(preview.warnings.isEmpty())
    }

    @Test
    fun `已存在的领域标为复用而不是新建`() {
        val preview = previewPlan(plan, existingDomainNames = setOf("深度学习"))

        assertTrue(preview.sections.first().lines.first().contains("复用"))
    }

    @Test
    fun `任务行带上日期时刻与难度中文`() {
        val line = previewPlan(plan).sections.last().lines.single()

        assertTrue(line, line.contains("7/29"))
        assertTrue(line, line.contains("19:00–20:30"))
        assertTrue(line, line.contains("烧脑"))
        assertTrue(line, line.contains("主线A"))
    }

    @Test
    fun `引用不到的任务线进知会项`() {
        val orphan = plan.copy(quests = emptyList())

        val preview = previewPlan(orphan)

        assertEquals(1, preview.warnings.size)
        assertTrue(preview.warnings.single().contains("主线A"))
    }

    @Test
    fun `引用库里已有的任务线不算知会项`() {
        val orphan = plan.copy(quests = emptyList())

        val preview = previewPlan(orphan, existingQuestTitles = setOf("主线A"))

        assertTrue(preview.warnings.isEmpty())
    }

    @Test
    fun `商品预览把同名的挪出上架清单`() {
        val items = listOf(
            ImportShopItem(name = "看电影", priceCi = 800, rarity = "RARE", emoji = "🎬"),
            ImportShopItem(name = "咖啡", priceCi = 400),
        )

        val preview = previewShop(items, existingNames = setOf("咖啡"))

        assertEquals(listOf("将上架 1 件"), preview.sections.map { it.heading })
        assertTrue(preview.sections.single().lines.single().contains("800 CI"))
        assertTrue(preview.warnings.single().contains("咖啡"))
    }

    @Test
    fun `商品全是同名时清单为空`() {
        val items = listOf(ImportShopItem(name = "咖啡", priceCi = 400))

        val preview = previewShop(items, existingNames = setOf("咖啡"))

        assertTrue(preview.sections.isEmpty())
        assertTrue(preview.summary.contains("没有可上架"))
    }
}
