package io.github.adgjmptw2.diet.ui.steps

import io.github.adgjmptw2.diet.data.steps.StepsMath
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.ProfileRepository
import io.github.adgjmptw2.diet.data.WeightRepository
import io.github.adgjmptw2.diet.data.steps.StepsAvailability
import io.github.adgjmptw2.diet.data.steps.StepsRepository
import io.github.adgjmptw2.diet.domain.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class StepsRange(val label: String, val days: Long) {
    WEEK("1주", 7),
    MONTH("1달", 30),
}

data class StepsDay(val date: LocalDate, val steps: Int?)

data class StepsUiState(
    val today: LocalDate = LocalDate.now(),
    val todaySteps: Int = 0,
    val goal: Int = 8000,
    val days: List<StepsDay> = emptyList(),
    val range: StepsRange = StepsRange.WEEK,
    val source: StepsAvailability = StepsAvailability.NONE,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val busy: Boolean = false,
    val goalEditing: Boolean = false,
    /** Health Connect와 센서를 모두 거부한 상태 */
    val denied: Boolean = false,
    val error: String? = null,
) {
    val average: Int get() = StepsMath.average(days.map { it.steps })
    val achieved: Int get() = StepsMath.achieved(days.map { it.steps }, goal)
    val distanceKm: Double get() = StepsMath.distanceKm(todaySteps, heightCm)
    val kcal: Int get() = StepsMath.kcal(todaySteps, heightCm, weightKg)
    val hasPermission: Boolean get() = source != StepsAvailability.NONE
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StepsViewModel @Inject constructor(
    private val steps: StepsRepository,
    profiles: ProfileRepository,
    weights: WeightRepository,
    private val clock: Clock,
) : ViewModel() {
    private val range = MutableStateFlow(StepsRange.WEEK)
    private val editing = MutableStateFlow(false)
    private val working = MutableStateFlow(false)
    private val source = MutableStateFlow(StepsAvailability.NONE)
    private val denied = MutableStateFlow(false)

    val state: StateFlow<StepsUiState> = combine(
        range,
        profiles.profile.onStart { emit(null) },
        weights.entries.onStart { emit(emptyList()) },
        steps.goal.onStart { emit(8000) },
        source,
    ) { selected, profile, entries, goal, availability ->
        Snapshot(selected, profile, entries.firstOrNull()?.weightKg, goal, availability)
    }.combine(combine(working, editing, denied) { a, b, c -> Flags(a, b, c) }) { snapshot, flags ->
        snapshot to flags
    }.flatMapLatest { (snapshot, flags) ->
        val today = LocalDate.now(clock)
        val from = today.minusDays(snapshot.range.days - 1)
        steps.daily(from, today).map { values -> build(snapshot, today, values, flags) }
    }.catch {
        emit(StepsUiState(error = "걸음 수를 불러오지 못했어요"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StepsUiState(today = LocalDate.now(clock)))

    init {
        viewModelScope.launch {
            steps.source.collect { source.value = it }
        }
        viewModelScope.launch {
            steps.denied.collect { denied.value = it }
        }
        refresh()
    }

    fun setRange(value: StepsRange) {
        range.value = value
    }

    fun refresh() {
        viewModelScope.launch {
            working.value = true
            try {
                steps.restoreSource()
                steps.sample()
            } finally {
                working.value = false
            }
        }
    }

    fun beginGoalEdit() {
        editing.value = true
    }

    fun cancelGoalEdit() {
        editing.value = false
    }

    fun setGoal(value: Int) {
        if (value !in 1000..50000) return
        viewModelScope.launch {
            steps.setGoal(value)
            editing.value = false
        }
    }

    fun connectHealthConnect() {
        viewModelScope.launch {
            working.value = true
            try {
                steps.useHealthConnect()
            } finally {
                working.value = false
            }
        }
    }

    fun connectSensor() {
        viewModelScope.launch {
            working.value = true
            try {
                steps.useSensor()
            } finally {
                working.value = false
            }
        }
    }

    fun canUseHealthConnect(): Boolean = steps.canUseHealthConnect()

    fun canUseSensor(): Boolean = steps.canUseSensor()

    fun hasSensorPermission(): Boolean = steps.hasSensorPermission()

    fun sourceLabel(): String = steps.sourceLabel()

    /** Health Connect를 못 쓰는 환경에서 센서 권한까지 거부했을 때만 둘 다 실패한 것으로 본다. */
    fun markDenied() {
        denied.value = true
        viewModelScope.launch { steps.markDeniedIfBlocked() }
    }

    /** 권한을 다시 요청하면 이전 안내는 지운다. */
    fun clearDenied() {
        denied.value = false
        viewModelScope.launch { steps.clearDenied() }
    }

    private fun build(
        snapshot: Snapshot,
        today: LocalDate,
        values: Map<LocalDate, Int>,
        flags: Flags,
    ): StepsUiState {
        val days = StepsMath.dayList(today, snapshot.range.days, values).map { (date, steps) ->
            StepsDay(date, steps)
        }
        return StepsUiState(
            today = today,
            todaySteps = values[today] ?: 0,
            goal = snapshot.goal,
            days = days,
            range = snapshot.range,
            source = snapshot.source,
            heightCm = snapshot.heightCm,
            weightKg = snapshot.weightKg,
            busy = flags.working,
            goalEditing = flags.editing,
            denied = flags.denied,
            error = null,
        )
    }

    private data class Flags(val working: Boolean, val editing: Boolean, val denied: Boolean)

    private data class Snapshot(
        val range: StepsRange,
        val profile: Profile?,
        val weightKg: Double?,
        val goal: Int,
        val source: StepsAvailability,
    ) {
        val heightCm: Double? get() = profile?.heightCm
    }
}
