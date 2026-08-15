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

package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.voice.skill.SkillTestFixtures.STANDARD_CANDIDATES
import com.wsy.ci.core.voice.skill.SkillTestFixtures.ctx
import com.wsy.ci.core.voice.skill.targetIdOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** QueryShop / PurchaseItem 的规则匹配：商城查询与购买的商品目标消歧。 */
class ShopSkillsTest {

    // ---------- QueryShopSkill ----------

    @Test
    fun `商城有什么命中商品查询`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = QueryShopSkill.matchRule("商城有什么", ctx)

        // Assert
        assertEquals(emptyMap<String, Any?>(), args)
    }

    @Test
    fun `打开商城不命中QueryShop留给Navigate`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = QueryShopSkill.matchRule("打开商城", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `非商城查询不命中QueryShop`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = QueryShopSkill.matchRule("明天有什么安排", ctx)

        // Assert
        assertNull(result)
    }

    // ---------- PurchaseItemSkill ----------

    @Test
    fun `说买XX命中purchase_item并解析出商品id`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val args = PurchaseItemSkill.matchRule("买奶茶", ctx)

        // Assert
        assertEquals(30L, args?.targetIdOrNull())
    }

    @Test
    fun `买非商品候选不命中PurchaseItem`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)

        // Act
        val result = PurchaseItemSkill.matchRule("买机器学习", ctx)

        // Assert
        assertNull(result)
    }

    @Test
    fun `购买预览带危险态警示`() {
        // Arrange
        val ctx = ctx(STANDARD_CANDIDATES)
        val args = PurchaseItemSkill.matchRule("买奶茶", ctx)!!

        // Act
        val preview = PurchaseItemSkill.preview(args, ctx)

        // Assert
        assertEquals(true, preview.dangerous)
    }
}
