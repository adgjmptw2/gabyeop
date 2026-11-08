package io.github.adgjmptw2.diet.ui

import io.github.adgjmptw2.diet.domain.GraphMetric

enum class RecordSection(val label: String) { CALENDAR("달력"), GRAPHS("그래프"), PROGRESS("성과") }

/** 홈과 알림의 이동 요청은 MainScaffold에서 같은 경로로 처리한다. */
sealed interface Destination {
    data object Home : Destination
    data object Meals : Destination
    data object AddMeal : Destination
    data object Fasting : Destination
    data object Steps : Destination
    data class Records(val section: RecordSection = RecordSection.CALENDAR,
        val metric: GraphMetric = GraphMetric.WATER, val openToday: Boolean = false) : Destination
}
