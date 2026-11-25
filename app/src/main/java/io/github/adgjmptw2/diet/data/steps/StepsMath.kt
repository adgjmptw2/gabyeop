package io.github.adgjmptw2.diet.data.steps

import java.time.LocalDate
import kotlin.math.roundToInt

/** 걸음 화면의 계산. Android 없이 JVM에서 검증한다. */
object StepsMath {
    /** 키(cm) × 0.415를 m 보폭으로 쓴다. 키가 없거나 범위를 벗어나면 0.7m. */
    fun strideMeters(heightCm: Double?): Double =
        heightCm?.takeIf { it in 100.0..250.0 }?.times(0.415)?.div(100.0) ?: 0.7

    fun distanceKm(steps: Int, heightCm: Double?): Double =
        steps.coerceAtLeast(0) * strideMeters(heightCm) / 1000.0

    /** 체중 × 거리(km) × 0.5. 체중이 없으면 60kg로 어림한다. */
    fun kcal(steps: Int, heightCm: Double?, weightKg: Double?): Int {
        val weight = weightKg?.takeIf { it in 30.0..300.0 } ?: 60.0
        return (weight * distanceKm(steps, heightCm) * 0.5).roundToInt()
    }

    fun average(days: List<Int?>): Int =
        days.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0

    fun achieved(days: List<Int?>, goal: Int): Int =
        days.count { (it ?: 0) >= goal && (it ?: 0) > 0 }

    /** 화면에 그릴 날짜별 목록. 값이 없는 날은 null로 둔다. */
    fun dayList(today: LocalDate, dayCount: Long, values: Map<LocalDate, Int>): List<Pair<LocalDate, Int?>> =
        (0 until dayCount.toInt()).map { offset ->
            val date = today.minusDays(dayCount - 1 - offset)
            date to values[date]
        }

    /** Health Connect 기록 한 건: 어느 앱이, 어느 날, 몇 걸음 */
    data class Entry(val origin: String, val date: LocalDate, val steps: Long)

    /**
     * 합계가 비었을 때 쓰는 대체 계산. 날마다 앱별로 더한 뒤 가장 많은 앱 하나만 쓴다.
     * 앱끼리 더하면 폰·워치·삼성 헬스가 같은 걸음을 두세 번 센다.
     */
    fun busiestOrigin(entries: List<Entry>): Map<LocalDate, Int> =
        entries.filter { it.steps > 0 }
            .groupBy { it.date }
            .mapValues { (_, day) ->
                day.groupBy { it.origin }.values.maxOf { rows -> rows.sumOf { it.steps } }
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
}
