package io.github.adgjmptw2.diet.domain

import java.time.LocalDate

enum class MealType(val label: String) {
    BREAKFAST("아침"),
    LUNCH("점심"),
    DINNER("저녁"),
    SNACK("간식"),
}

/** 영양값은 100g(또는 100ml) 기준. serving은 1회 제공량(unit 단위). */
data class Food(
    val id: String,
    val name: String,
    val brand: String?,
    val category: String,
    val rep: String,
    val kcal100: Double,
    val carb100: Double?,
    val prot100: Double?,
    val fat100: Double?,
    val serving: Double,
    val unit: String,
    val custom: Boolean,
)

data class Macro(val kcal: Double, val carb: Double?, val prot: Double?, val fat: Double?)

data class MealEntry(
    val id: String,
    val date: LocalDate,
    val type: MealType,
    val foodId: String?,
    val name: String,
    val emoji: String,
    /** unit 단위 양 */
    val amount: Double,
    val unit: String,
    val kcal: Double,
    val carb: Double?,
    val prot: Double?,
    val fat: Double?,
    val photoPath: String?,
)

object Nutrition {
    /** amount(g/ml)만큼 먹었을 때 */
    fun forAmount(food: Food, amount: Double): Macro {
        val r = amount / 100.0
        return Macro(
            kcal = food.kcal100 * r,
            carb = food.carb100?.times(r),
            prot = food.prot100?.times(r),
            fat = food.fat100?.times(r),
        )
    }

    fun total(meals: List<MealEntry>): Macro = Macro(
        kcal = meals.sumOf { it.kcal },
        carb = meals.sumOf { it.carb ?: 0.0 },
        prot = meals.sumOf { it.prot ?: 0.0 },
        fat = meals.sumOf { it.fat ?: 0.0 },
    )
}
