package io.github.adgjmptw2.diet.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodCleanupTest {

    @Test
    fun `referenced built in foods are never removed`() {
        val builtIn = listOf("favorite", "meal", "both", "unused")
        val favorites = setOf("favorite", "both")
        val meals = setOf("meal", "both")

        assertEquals(
            listOf("unused"),
            FoodCleanup.unreferencedBuiltIns(builtIn, favorites, meals),
        )
    }
}
