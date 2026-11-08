package io.github.adgjmptw2.diet.data.steps

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 걸음 화면과 그래프가 함께 쓰는 읽기 전용 걸음 소스. */
interface StepsSource {
    /** 날짜별 걸음 수. 기록 없는 날은 맵에 없음 */
    fun daily(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>
    val goal: Flow<Int>
}
