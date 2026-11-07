package io.github.adgjmptw2.diet.ui.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDate

/** 다시 구독할 때와 자정 이후에 화면의 날짜를 갱신한다. 기기의 시간 변경도 반영한다. */
internal fun todayDates(clock: Clock): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now(clock))
        delay(60_000L)
    }
}.distinctUntilChanged()
