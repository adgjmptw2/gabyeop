package io.github.adgjmptw2.diet.data.steps

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

// 병합할 때 실제 StepsSource를 남기고 이 파일을 지운다. Hilt 바인딩은 걸음 워커가 제공한다.
interface StepsSource {
    /** 날짜별 걸음 수. 기록 없는 날은 맵에 없음 */
    fun daily(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>
    val goal: Flow<Int>
}
