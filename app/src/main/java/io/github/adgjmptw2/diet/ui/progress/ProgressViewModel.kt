package io.github.adgjmptw2.diet.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.MealRepository
import io.github.adgjmptw2.diet.data.ProfileRepository
import io.github.adgjmptw2.diet.data.WeightRepository
import io.github.adgjmptw2.diet.domain.BadgeState
import io.github.adgjmptw2.diet.domain.Profile
import io.github.adgjmptw2.diet.domain.WeightAverage
import io.github.adgjmptw2.diet.domain.WeightEntry
import io.github.adgjmptw2.diet.domain.badges
import io.github.adgjmptw2.diet.domain.expectedGoalDate
import io.github.adgjmptw2.diet.domain.sevenDayAverages
import io.github.adgjmptw2.diet.domain.monthWeightChange
import io.github.adgjmptw2.diet.ui.common.todayDates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class ProgressPeriod(val label: String, val days: Long?) {
    MONTH("1개월", 30L),
    THREE_MONTHS("3개월", 90L),
    ALL("전체", null),
}

data class ProgressUiState(
    val today: LocalDate,
    val profile: Profile,
    val period: ProgressPeriod,
    val from: LocalDate,
    val entries: List<WeightEntry>,
    val averages: List<WeightAverage>,
    val lostKg: Double?,
    val monthChangeKg: Double?,
    val goalDate: LocalDate?,
    /** 최근 4주 기록이 4개 이상인데 줄고 있지 않을 때 */
    val goalDateUnavailable: Boolean = false,
    val goalReached: Boolean,
    val badges: List<BadgeState>,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    profiles: ProfileRepository,
    weights: WeightRepository,
    meals: MealRepository,
    clock: Clock,
) : ViewModel() {
    private val period = MutableStateFlow(ProgressPeriod.MONTH)

    val state: StateFlow<ProgressUiState?> = combine(
        profiles.profile.filterNotNull(), weights.entries, meals.loggedDays, todayDates(clock), period,
    ) { profile, weights, mealDays, today, selected ->
        val entries = weights.filter { it.date <= today }.distinctBy { it.date }.sortedBy { it.date }
        val from = selected.days?.let { today.minusDays(it - 1) } ?: entries.firstOrNull()?.date ?: today.minusDays(29)
        val currentKg = entries.lastOrNull()?.weightKg
        ProgressUiState(
            today = today,
            profile = profile,
            period = selected,
            from = from,
            entries = entries.filter { it.date >= from },
            // 선택 기간 직전 6일도 평균 계산에 포함한 뒤 화면 범위로 자른다.
            averages = sevenDayAverages(entries, today).filter { it.date >= from },
            lostKg = currentKg?.let { profile.startWeightKg - it },
            monthChangeKg = monthWeightChange(entries, YearMonth.from(today)),
            goalDate = expectedGoalDate(entries, profile.targetWeightKg, today),
            goalDateUnavailable = entries.filter { it.date >= today.minusDays(27) && it.date <= today }
                .distinctBy { it.date }.size >= 4,
            goalReached = currentKg != null && currentKg <= profile.targetWeightKg,
            badges = badges(profile, entries, mealDays, today),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPeriod(selected: ProgressPeriod) {
        period.value = selected
    }
}
