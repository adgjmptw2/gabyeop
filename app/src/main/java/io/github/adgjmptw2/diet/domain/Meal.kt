package io.github.adgjmptw2.diet.domain

import java.time.LocalDate

enum class MealType(val label: String) {
    BREAKFAST("아침"),
    LUNCH("점심"),
    DINNER("저녁"),
    SNACK("간식"),
}

/** 음식의 1회분 단위. 팩/개는 1회분=1개인 개수 단위이고 g/ml은 무게·부피 단위다. */
enum class FoodUnit(val label: String, val isCount: Boolean) {
    PACK("팩", true),
    PIECE("개", true),
    GRAM("g", false),
    MILLILITER("ml", false);

    companion object {
        fun from(label: String): FoodUnit =
            entries.firstOrNull { it.label == label.trim() } ?: GRAM
    }
}

/**
 * g/ml 음식의 kcal100·carb100·prot100·fat100은 100g(또는 100ml) 기준이고,
 * 팩/개 음식의 servingKcal·servingCarb·servingProt·servingFat은 1회분(1개) 기준이다.
 * 팩/개는 기록 amount를 개수로 다룬다.
 */
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
    val favorite: Boolean = false,
    val servingKcal: Double? = null,
    val servingCarb: Double? = null,
    val servingProt: Double? = null,
    val servingFat: Double? = null,
    /** 팩/개 1개의 무게(g). 선택값이며 영양 계산에는 쓰지 않는다. */
    val servingWeightG: Double? = null,
) {
    val unitKind: FoodUnit get() = FoodUnit.from(unit)

    /** 기록할 때 1회분으로 잡는 양. 팩/개는 1개다. */
    val servingAmount: Double get() = if (unitKind.isCount) 1.0 else serving
}

data class Macro(val kcal: Double, val carb: Double?, val prot: Double?, val fat: Double?)

/** 내 음식 저장과 공공 음식 복사에 쓰는 1회분 기준 값. */
data class CustomFoodDraft(
    val name: String,
    val brand: String?,
    val unit: String,
    val serving: Double,
    val kcal: Double,
    val carb: Double?,
    val prot: Double?,
    val fat: Double?,
    val servingWeightG: Double? = null,
)

data class MealEntry(
    val id: String,
    val date: LocalDate,
    val type: MealType,
    val foodId: String?,
    val name: String,
    val emoji: String,
    /** unit 단위 양. 팩/개 음식이면 개수 */
    val amount: Double,
    val unit: String,
    val kcal: Double,
    val carb: Double?,
    val prot: Double?,
    val fat: Double?,
    val photoPath: String?,
)

object Nutrition {
    /** amount의 단위는 food.unit을 따른다. g/ml는 양, 팩/개는 개수다. */
    fun forAmount(food: Food, amount: Double): Macro {
        if (food.unitKind.isCount) {
            val kcal = food.servingKcal
            if (kcal == null) {
                // 단위 정보가 생기기 전의 예외 행만 1회분=100개로 본다. 새 팩/개 음식은 항상 1회분 칼로리를 갖는다.
                val legacy = amount / 100.0
                return Macro(
                    kcal = food.kcal100 * legacy,
                    carb = food.carb100?.times(legacy),
                    prot = food.prot100?.times(legacy),
                    fat = food.fat100?.times(legacy),
                )
            }
            return Macro(
                kcal = kcal * amount,
                carb = food.servingCarb?.times(amount),
                prot = food.servingProt?.times(amount),
                fat = food.servingFat?.times(amount),
            )
        }
        val ratio = amount / 100.0
        return Macro(
            kcal = food.kcal100 * ratio,
            carb = food.carb100?.times(ratio),
            prot = food.prot100?.times(ratio),
            fat = food.fat100?.times(ratio),
        )
    }

    fun forServing(food: Food): Macro = forAmount(food, food.servingAmount)

    fun total(meals: List<MealEntry>): Macro = Macro(
        kcal = meals.sumOf { it.kcal },
        carb = meals.sumOf { it.carb ?: 0.0 },
        prot = meals.sumOf { it.prot ?: 0.0 },
        fat = meals.sumOf { it.fat ?: 0.0 },
    )
}

/** 공공/저장 음식의 1회분 값을 내 음식 입력값으로 옮긴다. */
fun Food.toCustomDraft(): CustomFoodDraft {
    val perServing = Nutrition.forServing(this)
    return CustomFoodDraft(
        name = name.trim(),
        brand = brand?.takeIf { it.isNotBlank() },
        unit = unitKind.label,
        serving = servingAmount,
        kcal = perServing.kcal,
        carb = perServing.carb,
        prot = perServing.prot,
        fat = perServing.fat,
        servingWeightG = servingWeightG,
    )
}

/**
 * 1회분 입력값을 저장 가능한 Food로 만든다.
 * g/ml는 100g 기준 밀도로 환산하고, 팩/개는 1회분 값을 그대로 둔다.
 */
fun CustomFoodDraft.toFood(id: String, favorite: Boolean = false): Food {
    val trimmed = name.trim()
    val kind = FoodUnit.from(unit)
    val perServing = serving.takeIf { it.isFinite() && it > 0 }
        ?: if (kind.isCount) 1.0 else 100.0
    return Food(
        id = id,
        name = trimmed,
        brand = brand?.trim()?.takeIf { it.isNotEmpty() },
        category = "직접 입력",
        rep = trimmed,
        kcal100 = if (kind.isCount) 0.0 else kcal * 100.0 / perServing,
        carb100 = if (kind.isCount) null else carb?.times(100.0 / perServing),
        prot100 = if (kind.isCount) null else prot?.times(100.0 / perServing),
        fat100 = if (kind.isCount) null else fat?.times(100.0 / perServing),
        serving = if (kind.isCount) 1.0 else perServing,
        unit = kind.label,
        custom = true,
        favorite = favorite,
        servingKcal = kcal.takeIf { kind.isCount },
        servingCarb = carb.takeIf { kind.isCount },
        servingProt = prot.takeIf { kind.isCount },
        servingFat = fat.takeIf { kind.isCount },
        servingWeightG = servingWeightG?.takeIf { it.isFinite() && it > 0 },
    )
}
