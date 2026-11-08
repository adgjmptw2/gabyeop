package io.github.adgjmptw2.diet.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.DayNoteRepository
import io.github.adgjmptw2.diet.data.MealRepository
import io.github.adgjmptw2.diet.data.WeightRepository
import io.github.adgjmptw2.diet.domain.DailyNote
import io.github.adgjmptw2.diet.domain.DayCell
import io.github.adgjmptw2.diet.domain.MealEntry
import io.github.adgjmptw2.diet.domain.WeekSummary
import io.github.adgjmptw2.diet.domain.weekCells
import io.github.adgjmptw2.diet.domain.weeks
import io.github.adgjmptw2.diet.ui.common.todayDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class RecordDay(val date: LocalDate, val weightKg: Double?, val meals: List<MealEntry>)

data class DayDetailState(
    val date: LocalDate,
    val weightKg: Double?,
    val weightId: String?,
    val memo: String,
    val note: String,
    val meals: List<MealEntry>,
)

/** 하루 상세의 저장 진행·결과. */
data class DaySaveState(
    val date: LocalDate? = null,
    val saving: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
)

data class RecordsUiState(
    val today: LocalDate,
    val weeks: List<WeekSummary>,
    val days: Map<LocalDate, RecordDay>,
    val weekCells: List<DayCell> = emptyList(),
    val notes: Map<LocalDate, DailyNote> = emptyMap(),
    val thisWeek: WeekSummary? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordsViewModel @Inject constructor(
    weights: WeightRepository,
    meals: MealRepository,
    private val dayNotes: DayNoteRepository,
    clock: Clock,
) : ViewModel() {
    private val weightsRepository = weights
    private val mealsRepository = meals
    private val _saveState = MutableStateFlow(DaySaveState())
    val saveState: StateFlow<DaySaveState> = _saveState.asStateFlow()
    private val datedMeals = todayDates(clock).flatMapLatest { today ->
        meals.range(LocalDate.of(1900, 1, 1), today).map { today to it }
    }

    val state: StateFlow<RecordsUiState?> = combine(weights.entries, datedMeals, dayNotes.notes()) { entries, dated, notes ->
        val (today, meals) = dated
        val dailyWeights = entries.filter { it.date <= today }.distinctBy { it.date }.associateBy { it.date }
        val dailyMeals = meals.groupBy { it.date }
        val dailyNotes = notes.values.associateBy { it.date }
        val days = (dailyWeights.keys + dailyMeals.keys + dailyNotes.keys).associateWith { date ->
            RecordDay(date, dailyWeights[date]?.weightKg, dailyMeals[date].orEmpty())
        }
        val summaries = weeks(entries, meals, today, dailyNotes.keys)
        val thisWeekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        RecordsUiState(
            today = today,
            weeks = summaries,
            days = days,
            weekCells = weekCells(today, notes.values.toList()),
            notes = notes,
            thisWeek = summaries.firstOrNull { it.start == thisWeekStart },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 체중·메모를 함께 저장한다. [weightKg]가 null이고 [weightId]가 있으면 체중을 지운다. */
    fun saveDay(date: LocalDate, weightKg: Double?, weightId: String?, memo: String, note: String) {
        if (_saveState.value.saving) return
        _saveState.value = DaySaveState(date, saving = true)
        viewModelScope.launch {
            try {
                if (weightKg == null) {
                    weightId?.let { weightsRepository.delete(it) }
                } else {
                    weightsRepository.save(date, weightKg)
                }
                dayNotes.save(date, memo, note)
                _saveState.value = DaySaveState(date, success = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _saveState.value = DaySaveState(date, error = "저장하지 못했어요")
            }
        }
    }

    fun clearNote(date: LocalDate) {
        if (_saveState.value.saving) return
        _saveState.value = DaySaveState(date, saving = true)
        viewModelScope.launch {
            try {
                dayNotes.clear(date)
                _saveState.value = DaySaveState(date, success = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _saveState.value = DaySaveState(date, error = "메모를 지우지 못했어요")
            }
        }
    }

    fun dayDetail(date: LocalDate): Flow<DayDetailState> = combine(
        weightsRepository.entries,
        mealsRepository.day(date),
        dayNotes.notes(date, date),
    ) { entries, dayMeals, notes ->
        val entry = entries.firstOrNull { it.date == date }
        val dailyNote = notes[date]
        DayDetailState(date, entry?.weightKg, entry?.id, dailyNote?.memo.orEmpty(), dailyNote?.note.orEmpty(), dayMeals)
    }
}
