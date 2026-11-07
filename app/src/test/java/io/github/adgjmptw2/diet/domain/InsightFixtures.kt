package io.github.adgjmptw2.diet.domain

import java.time.LocalDate

internal fun insightWeight(date: LocalDate, kg: Double) = WeightEntry("weight-$date", date, kg)

internal fun insightMeal(date: LocalDate, kcal: Double, id: String = "meal-$date") = MealEntry(
    id = id,
    date = date,
    type = MealType.LUNCH,
    foodId = null,
    name = "식사",
    emoji = "🍚",
    amount = 100.0,
    unit = "g",
    kcal = kcal,
    carb = null,
    prot = null,
    fat = null,
    photoPath = null,
)

internal fun insightProfile(startKg: Double = 80.0, targetKg: Double = 60.0) = Profile(
    sex = Sex.FEMALE,
    birthYear = 1990,
    heightCm = 165.0,
    startWeightKg = startKg,
    targetWeightKg = targetKg,
    activity = ActivityLevel.LIGHT,
    startDate = LocalDate.of(2026, 1, 1),
)
