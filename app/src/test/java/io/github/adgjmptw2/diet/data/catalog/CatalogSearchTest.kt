package io.github.adgjmptw2.diet.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSearchTest {

    private fun food(
        id: String,
        name: String,
        brand: String = "",
        kind: Int = 0,
    ) = CatalogFood(
        id = id,
        name = name,
        brand = brand,
        category = "",
        rep = name,
        kcal100 = 100.0,
        carb100 = null,
        prot100 = null,
        fat100 = null,
        serving = 100.0,
        unit = "g",
        pack = null,
        kind = kind,
    )

    @Test
    fun `query is lowercased and split on whitespace`() {
        assertEquals(listOf("the더건강한", "닭가슴살"), CatalogSearch.tokens("  THE더건강한   닭가슴살 "))
    }

    @Test
    fun `every token must appear in the search key`() {
        val key = CatalogSearch.searchKey("The더건강한 닭가슴살 통살", "The더건강한")

        assertTrue(CatalogSearch.matchesAll(key, CatalogSearch.tokens("닭가슴살 통살")))
        assertTrue(CatalogSearch.matchesAll(key, CatalogSearch.tokens("통살 THE더건강한")))
        assertFalse(CatalogSearch.matchesAll(key, CatalogSearch.tokens("닭가슴살 소시지")))
        assertFalse(CatalogSearch.matchesAll(key, CatalogSearch.tokens("  ")))
    }

    @Test
    fun `search key joins name and brand without spaces`() {
        assertEquals("the더건강한닭가슴살통살the더건강한",
            CatalogSearch.searchKey("The더건강한 닭가슴살 통살", " The더건강한 "))
    }

    @Test
    fun `ranking prefers exact name then prefix then shorter name then food kind`() {
        val exact = food("4", "닭가슴살", kind = 2)
        val prefix = food("3", "닭가슴살 통살", kind = 2)
        val shortFood = food("2", "닭가슴살 스테이크", kind = 0)
        val longProcessed = food("1", "닭가슴살 통살 구이 세트", kind = 2)
        val ingredient = food("5", "닭가슴살 스테이크", kind = 1)

        val ranked = CatalogSearch.ranking("닭가슴살", listOf(longProcessed, shortFood, prefix, exact, ingredient))

        assertEquals(listOf("4", "3", "2", "5", "1"), ranked.map { it.id })
    }

    @Test
    fun `prefix match wins over a shorter unrelated name`() {
        val shorter = food("1", "닭가슴")
        val prefix = food("2", "닭가슴살 통살")

        assertEquals(listOf("2", "1"), CatalogSearch.ranking("닭가슴살", listOf(shorter, prefix)).map { it.id })
    }

    @Test
    fun `name comparison ignores case and repeated whitespace`() {
        val food = food("1", "  THE더건강한   닭가슴살 ")

        assertEquals("the더건강한 닭가슴살", CatalogSearch.normalizedName(food.name))
        assertEquals(0, CatalogSearch.comparator(" the더건강한 닭가슴살 ").compare(food, food.copy()))
    }

    @Test
    fun `tie keeps a stable order by id`() {
        val a = food("b", "닭가슴살")
        val b = food("a", "닭가슴살")

        assertEquals(listOf("a", "b"), CatalogSearch.ranking("닭가슴살", listOf(a, b)).map { it.id })
    }
}