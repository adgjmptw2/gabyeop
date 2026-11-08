package io.github.adgjmptw2.diet.domain

import kotlin.math.roundToInt

data class MacroGoal(val carbGrams: Int? = null, val proteinGrams: Int? = null, val fatGrams: Int? = null) {
    val energyKcal: Int get() = (carbGrams ?: 0) * 4 + (proteinGrams ?: 0) * 4 + (fatGrams ?: 0) * 9
    val isEmpty: Boolean get() = carbGrams == null && proteinGrams == null && fatGrams == null

    /** 비어 있는 항목은 현재 하루 목표 칼로리로 계산한다. 직접 정한 0도 유지한다. */
    fun resolved(kcal: Int): MacroGoal {
        val recommended = macroGoal(kcal)
        return MacroGoal(carbGrams ?: recommended.carbGrams,
            proteinGrams ?: recommended.proteinGrams, fatGrams ?: recommended.fatGrams)
    }
}

enum class MacroPreset(val label: String, val carb: Int, val protein: Int, val fat: Int) {
    BALANCED("균형 50:20:30", 50, 20, 30),
    PROTEIN("고단백 40:30:30", 40, 30, 30),
    LOW_CARB("저탄수 25:35:40", 25, 35, 40),
}

fun macroGoal(kcal: Int, preset: MacroPreset = MacroPreset.BALANCED): MacroGoal {
    require(kcal >= 0)
    return MacroGoal(
        (kcal.toDouble() * preset.carb / 100 / 4).roundToInt().coerceIn(0, 1000),
        (kcal.toDouble() * preset.protein / 100 / 4).roundToInt().coerceIn(0, 1000),
        (kcal.toDouble() * preset.fat / 100 / 9).roundToInt().coerceIn(0, 1000),
    )
}
