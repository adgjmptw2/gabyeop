package io.github.adgjmptw2.diet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionTest {

    private fun packFood(
        kcal: Double = 120.0,
        carb: Double? = 3.0,
        prot: Double? = 22.0,
        fat: Double? = 2.5,
    ) = Food(
        id = "u-pack",
        name = "닭가슴살",
        brand = "한끼통살 갈릭맛",
        category = "직접 입력",
        rep = "닭가슴살",
        kcal100 = 0.0,
        carb100 = null,
        prot100 = null,
        fat100 = null,
        serving = 1.0,
        unit = "팩",
        custom = true,
        servingKcal = kcal,
        servingCarb = carb,
        servingProt = prot,
        servingFat = fat,
        servingWeightG = 100.0,
    )

    private fun gramFood() = Food(
        id = "g-food",
        name = "밥",
        brand = null,
        category = "밥류",
        rep = "밥",
        kcal100 = 150.0,
        carb100 = 30.0,
        prot100 = 3.0,
        fat100 = 1.0,
        serving = 200.0,
        unit = "g",
        custom = false,
    )

    @Test
    fun `반 팩은 1회분 영양값의 절반이다`() {
        val macro = Nutrition.forAmount(packFood(), 0.5)

        assertEquals(60.0, macro.kcal, 1e-9)
        assertEquals(1.5, macro.carb!!, 1e-9)
        assertEquals(11.0, macro.prot!!, 1e-9)
        assertEquals(1.25, macro.fat!!, 1e-9)
    }

    @Test
    fun `두 팩은 1회분 영양값의 두 배다`() {
        val macro = Nutrition.forAmount(packFood(), 2.0)

        assertEquals(240.0, macro.kcal, 1e-9)
        assertEquals(6.0, macro.carb!!, 1e-9)
        assertEquals(44.0, macro.prot!!, 1e-9)
        assertEquals(5.0, macro.fat!!, 1e-9)
    }

    @Test
    fun `개 단위도 팩과 같이 개수로 계산한다`() {
        val food = packFood().copy(unit = "개")
        val half = Nutrition.forAmount(food, 0.5)
        val two = Nutrition.forAmount(food, 2.0)

        assertEquals(60.0, half.kcal, 1e-9)
        assertEquals(1.5, half.carb!!, 1e-9)
        assertEquals(11.0, half.prot!!, 1e-9)
        assertEquals(1.25, half.fat!!, 1e-9)
        assertEquals(240.0, two.kcal, 1e-9)
        assertEquals(6.0, two.carb!!, 1e-9)
        assertEquals(44.0, two.prot!!, 1e-9)
        assertEquals(5.0, two.fat!!, 1e-9)
    }

    @Test
    fun `g 단위는 한 팩처럼 개수로 해석하지 않는다`() {
        val food = packFood().copy(
            unit = "g", kcal100 = 120.0, carb100 = 3.0, prot100 = 22.0, fat100 = 2.5,
            servingKcal = null, servingCarb = null, servingProt = null, servingFat = null,
        )

        assertEquals(60.0, Nutrition.forAmount(food, 50.0).kcal, 1e-9)
    }

    @Test
    fun `g 음식은 100g 기준 계산이 그대로다`() {
        val macro = Nutrition.forAmount(gramFood(), 200.0)

        assertEquals(300.0, macro.kcal, 1e-9)
        assertEquals(60.0, macro.carb!!, 1e-9)
        assertEquals(6.0, macro.prot!!, 1e-9)
        assertEquals(2.0, macro.fat!!, 1e-9)
        assertEquals(300.0, Nutrition.forServing(gramFood()).kcal, 1e-9)
    }

    @Test
    fun `g 음식의 복사 초안은 1회분 값과 g 단위를 그대로 옮긴다`() {
        val draft = gramFood().toCustomDraft()

        assertEquals("밥", draft.name)
        assertEquals("g", draft.unit)
        assertEquals(200.0, draft.serving, 1e-9)
        assertEquals(300.0, draft.kcal, 1e-9)
        assertEquals(60.0, draft.carb!!, 1e-9)
        assertEquals(6.0, draft.prot!!, 1e-9)
        assertEquals(2.0, draft.fat!!, 1e-9)
    }

    @Test
    fun `복사한 g 음식을 저장하면 원래 100g 영양값과 1회분 양이 같다`() {
        val saved = gramFood().toCustomDraft().toFood(id = "u-copy", favorite = true)

        assertEquals("밥", saved.name)
        assertEquals("g", saved.unit)
        assertEquals(200.0, saved.serving, 1e-9)
        assertEquals(150.0, saved.kcal100, 1e-9)
        assertEquals(30.0, saved.carb100!!, 1e-9)
        assertEquals(3.0, saved.prot100!!, 1e-9)
        assertEquals(1.0, saved.fat100!!, 1e-9)
        assertEquals(true, saved.favorite)
        assertNull(saved.servingKcal)
    }

    @Test
    fun `복사한 팩 음식은 개수 단위와 1회분 영양값을 보존한다`() {
        val saved = packFood().toCustomDraft().toFood(id = "u-copy")

        assertEquals("닭가슴살", saved.name)
        assertEquals("한끼통살 갈릭맛", saved.brand)
        assertEquals("팩", saved.unit)
        assertEquals(1.0, saved.serving, 1e-9)
        assertEquals(1.0, saved.servingAmount, 1e-9)
        assertEquals(120.0, saved.servingKcal!!, 1e-9)
        assertEquals(3.0, saved.servingCarb!!, 1e-9)
        assertEquals(22.0, saved.servingProt!!, 1e-9)
        assertEquals(2.5, saved.servingFat!!, 1e-9)
        assertEquals(100.0, saved.servingWeightG!!, 1e-9)
        assertNull(saved.carb100)
    }
}