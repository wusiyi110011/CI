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

import com.wsy.ci.core.economy.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopImportTest {

    private val minimal = """
        {"version":1,"items":[{"name":"看一场电影","emoji":"🎬","priceCi":800,"rarity":"RARE"}]}
    """.trimIndent()

    @Test
    fun `最小合法输入解析出一件商品`() {
        val result = ShopImport.parse(minimal)

        val ok = result as ShopImportResult.Ok
        assertEquals(1, ok.items.size)
        assertEquals("看一场电影", ok.items.first().name)
    }

    @Test
    fun `容忍markdown围栏与前后闲话`() {
        val raw = "好的，这是你要的货架：\n```json\n$minimal\n```\n希望有帮助！"

        assertTrue(ShopImport.parse(raw) is ShopImportResult.Ok)
    }

    @Test
    fun `价格为零或负数被拦下`() {
        val result = ShopImport.parse("""{"version":1,"items":[{"name":"甲","priceCi":0}]}""")

        val err = result as ShopImportResult.Err
        assertTrue(err.errors.any { it.contains("priceCi") })
    }

    @Test
    fun `价格超过上限被拦下`() {
        val text = """{"version":1,"items":[{"name":"甲","priceCi":9999999}]}"""

        val err = ShopImport.parse(text) as ShopImportResult.Err
        assertTrue(err.errors.any { it.contains("上限") })
    }

    @Test
    fun `未知品质被拦下`() {
        val text = """{"version":1,"items":[{"name":"甲","priceCi":100,"rarity":"MYTHIC"}]}"""

        val err = ShopImport.parse(text) as ShopImportResult.Err
        assertTrue(err.errors.any { it.contains("rarity") })
    }

    @Test
    fun `同一批里重名被拦下`() {
        val text = """
            {"version":1,"items":[
              {"name":"咖啡","priceCi":400},
              {"name":"咖啡","priceCi":500}
            ]}
        """.trimIndent()

        val err = ShopImport.parse(text) as ShopImportResult.Err
        assertTrue(err.errors.any { it.contains("重复") })
    }

    @Test
    fun `空商品列表被拦下`() {
        val err = ShopImport.parse("""{"version":1,"items":[]}""") as ShopImportResult.Err

        assertTrue(err.errors.any { it.contains("至少填一件") })
    }

    @Test
    fun `版本号不符被拦下`() {
        val text = """{"version":2,"items":[{"name":"甲","priceCi":100}]}"""

        val err = ShopImport.parse(text) as ShopImportResult.Err
        assertTrue(err.errors.any { it.contains("version") })
    }

    @Test
    fun `转实体时去空格并给emoji兜底`() {
        val entity = ShopImport.toEntity(
            ImportShopItem(name = "  咖啡  ", emoji = "  ", priceCi = 400, rarity = "epic")
        )

        assertEquals("咖啡", entity.name)
        assertEquals("🎁", entity.emoji)
        assertEquals(Rarity.EPIC, entity.rarity)
    }

    @Test
    fun `自带模板本身可被解析通过`() {
        assertTrue(ShopImport.parse(ShopImport.TEMPLATE) is ShopImportResult.Ok)
    }
}
