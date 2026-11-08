package io.github.adgjmptw2.diet.domain

import kotlin.math.floor

data class MacroRatios(
    val carbGrams: Double, val proteinGrams: Double, val fatGrams: Double,
    val carbPercent: Int, val proteinPercent: Int, val fatPercent: Int,
    val missingCount: Int,
) {
    val hasEnergy: Boolean get() = carbGrams * 4 + proteinGrams * 4 + fatGrams * 9 > 0
}

/** Omit incomplete rows; largest remainders keep rounded percentages at 100. */
fun macroRatios(meals: List<MealEntry>): MacroRatios {
    val complete = meals.filter { meal ->
        listOf(meal.carb, meal.prot, meal.fat).all { it != null && it.isFinite() && it >= 0 }
    }
    val carb = complete.sumOf { it.carb!! }
    val prot = complete.sumOf { it.prot!! }
    val fat = complete.sumOf { it.fat!! }
    val energy = listOf(carb * 4, prot * 4, fat * 9)
    val total = energy.sum()
    val exact = energy.map { if (total > 0) it / total * 100 else 0.0 }
    val percent = exact.map { floor(it).toInt() }.toMutableList()
    if (total > 0) {
        val order = exact.indices.sortedByDescending { exact[it] - percent[it] }
        repeat(100 - percent.sum()) { percent[order[it % order.size]]++ }
    }
    return MacroRatios(carb, prot, fat, percent[0], percent[1], percent[2], meals.size - complete.size)
}
