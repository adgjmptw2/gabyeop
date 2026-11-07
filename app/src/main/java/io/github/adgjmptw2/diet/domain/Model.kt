package io.github.adgjmptw2.diet.domain

import java.time.LocalDate

enum class Sex(val label: String) {
    MALE("남성"),
    FEMALE("여성"),
}

enum class ActivityLevel(val factor: Double, val label: String) {
    SEDENTARY(1.2, "거의 안 움직임"),
    LIGHT(1.375, "가벼운 운동 주 1~3회"),
    MODERATE(1.55, "운동 주 3~5회"),
    ACTIVE(1.725, "운동 주 6~7회"),
}

data class Profile(
    val sex: Sex,
    val birthYear: Int,
    val heightCm: Double,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val activity: ActivityLevel,
    val startDate: LocalDate,
)

data class WeightEntry(
    val id: String,
    val date: LocalDate,
    val weightKg: Double,
)
