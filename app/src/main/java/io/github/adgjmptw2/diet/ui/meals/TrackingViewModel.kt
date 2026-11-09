package io.github.adgjmptw2.diet.ui.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.DayNoteRepository
import io.github.adgjmptw2.diet.data.FastRepository
import io.github.adgjmptw2.diet.data.ProfileRepository
import io.github.adgjmptw2.diet.data.WeightRepository
import io.github.adgjmptw2.diet.data.settings.TrackingStore
import io.github.adgjmptw2.diet.domain.Fast
import io.github.adgjmptw2.diet.domain.WaterSettings
import io.github.adgjmptw2.diet.domain.waterTargetMl
import io.github.adgjmptw2.diet.ui.common.todayDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class WaterUiState(val notes: Map<LocalDate, io.github.adgjmptw2.diet.domain.DailyNote> = emptyMap(),
    val settings: WaterSettings = WaterSettings(), val automaticTargetMl: Int = 2000, val ready: Boolean = false)
data class FastingUiState(val active: Fast? = null, val targetHours: Int = 16, val recent: List<Fast> = emptyList(),
    val history: List<Fast> = emptyList())
sealed interface TrackingEvent {
    data class WaterAdded(val date: LocalDate, val deltaMl: Int) : TrackingEvent
    sealed interface FastEvent : TrackingEvent { val fast: Fast }
    data class FastFinished(override val fast: Fast) : FastEvent
    data class FastDeleted(override val fast: Fast) : FastEvent
}

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val notes: DayNoteRepository,
    private val fasts: FastRepository,
    private val settings: TrackingStore,
    profiles: ProfileRepository,
    weights: WeightRepository,
    private val clock: Clock,
) : ViewModel() {
    private val writes = Mutex()
    val error = MutableStateFlow<String?>(null)
    private val waterChannel = Channel<TrackingEvent.WaterAdded>(Channel.BUFFERED)
    val waterEvents = waterChannel.receiveAsFlow()
    private val fastChannel = Channel<TrackingEvent.FastEvent>(Channel.BUFFERED)
    val fastingEvents = fastChannel.receiveAsFlow()
    val macroGoals = settings.macroGoals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), io.github.adgjmptw2.diet.domain.MacroGoal())
    val water = combine(notes.notes(), settings.water, profiles.profile, weights.entries, todayDates(clock)) { days, options, profile, entries, today ->
        val kg = entries.firstOrNull { it.date <= today }?.weightKg ?: profile?.startWeightKg ?: 65.0
        WaterUiState(days, options, waterTargetMl(kg), ready = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WaterUiState())
    val fasting = combine(fasts.entries, settings.fastingHours) { rows, hours ->
        val live = rows.filterNot { it.deleted }
        FastingUiState(live.filter { it.endAt == null }.maxWithOrNull(compareBy<Fast> { it.updatedAt }.thenBy { it.id }), hours,
            live.filter { it.endAt != null }.sortedByDescending { it.endAt }, live)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FastingUiState())
    fun adjustWater(date: LocalDate, delta: Int) = action { notes.adjustWater(date, delta) }
    fun addWater(date: LocalDate, ml: Int) = action {
        val added = notes.adjustWater(date, ml)
        if (added > 0) waterChannel.send(TrackingEvent.WaterAdded(date, added))
    }
    fun setWater(date: LocalDate, ml: Int) = action { notes.setWater(date, ml) }
    fun setGlassMl(ml: Int) = action { settings.setGlassMl(ml) }
    fun setMacroGoals(goal: io.github.adgjmptw2.diet.domain.MacroGoal) = action { settings.setMacroGoals(goal) }
    fun selectTarget(hours: Int) = action { settings.setFastingHours(hours); fasts.changeTarget(hours) }
    fun start(startAt: Long? = null) = action { fasts.start(settings.fastingHours.first(), startAt) }
    fun changeStartAt(startAt: Long) = action {
        if (fasting.value.active == null) return@action
        if (!io.github.adgjmptw2.diet.domain.Fasting.validStart(startAt, clock.millis())) {
            error.value = "시작 시각은 지금부터 7일 전 사이로 골라 주세요"; return@action
        }
        fasts.changeStart(startAt)
    }
    fun finish() = action { fasts.finish()?.let { fastChannel.send(TrackingEvent.FastFinished(it)) } }
    fun undoFinish(fast: Fast) = action { if (!fasts.undoFinish(fast)) error.value = "단식 기록이 바뀌어 되돌릴 수 없어요" }
    fun updatePast(id: String, startAt: Long, endAt: Long, targetHours: Int, onResult: (String?) -> Unit) =
        action(onFailure = { onResult(it) }) {
            onResult(fasts.updatePast(id, startAt, endAt, targetHours)?.message)
        }
    fun deletePast(id: String, onResult: (String?) -> Unit) = action(onFailure = { onResult(it) }) {
        val deleted = fasts.delete(id)
        if (deleted == null) onResult("단식 기록이 바뀌었어요") else {
            onResult(null)
            fastChannel.send(TrackingEvent.FastDeleted(deleted))
        }
    }
    fun restorePast(fast: Fast) = action {
        if (!fasts.restore(fast)) error.value = "단식 기록이 바뀌어 되돌릴 수 없어요"
    }
    fun now(): Long = clock.millis()
    fun zone() = clock.zone
    fun notificationDenied() { error.value = "알림 권한이 꺼져 있어요" }
    fun changeStart(dayOffset: Long, hour: Int, minute: Int) = action {
        val active = fasting.value.active ?: return@action
        val date = Instant.ofEpochMilli(active.startAt).atZone(clock.zone).toLocalDate().plusDays(dayOffset)
        val selected = date.atTime(hour, minute).atZone(clock.zone).toInstant().toEpochMilli()
        if (selected > clock.millis()) { error.value = "시작 시각은 지금 이전으로 골라 주세요"; return@action }
        if (clock.millis() - selected > io.github.adgjmptw2.diet.domain.Fasting.MAX_DURATION_MS) {
            error.value = "시작 시각은 7일 이내로 골라 주세요"
            return@action
        }
        fasts.changeStart(selected)
    }
    private fun action(onFailure: (String) -> Unit = { error.value = it }, block: suspend () -> Unit) {
        viewModelScope.launch {
            writes.withLock {
                try { error.value = null; block() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { onFailure("저장하지 못했어요") }
            }
        }
    }
}
