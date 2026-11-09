package io.github.adgjmptw2.diet.ui.supplements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.SupplementEntity
import io.github.adgjmptw2.diet.data.SupplementRepository
import io.github.adgjmptw2.diet.domain.Supplement
import io.github.adgjmptw2.diet.domain.SupplementCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject

data class SupplementUiState(
    val supplements: List<Supplement> = emptyList(),
    val splitByTime: Boolean = true,
) {
    val total: Int get() = supplements.size
}

@HiltViewModel
class SupplementViewModel @Inject constructor(
    private val repository: SupplementRepository,
) : ViewModel() {
    private val writes = Mutex()
    val error = MutableStateFlow<String?>(null)

    val supplements: StateFlow<SupplementUiState> = combine(
        repository.observeSupplements(),
        repository.splitByTime,
    ) { rows, split ->
        SupplementUiState(rows, split)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupplementUiState())

    fun observeDay(date: LocalDate): Flow<Set<SupplementCheck>> = repository.observeDay(date)

    fun toggle(date: LocalDate, id: String, slot: Int) = action { repository.toggle(date, id, slot) }

    fun toggleAll(date: LocalDate, id: String) = action { repository.toggleAll(date, id) }

    fun setSplitByTime(split: Boolean) = action { repository.setSplitByTime(split) }

    fun add(name: String, slots: Int, onAdded: () -> Unit = {}) = action {
        repository.add(name, slots)
        onAdded()
    }

    fun setSlots(id: String, slots: Int) = action { repository.setSlots(id, slots) }

    fun rename(id: String, name: String, onRenamed: () -> Unit = {}) = action {
        repository.rename(id, name)
        onRenamed()
    }

    fun delete(id: String, onDeleted: (SupplementEntity) -> Unit = {}) = action {
        repository.delete(id)?.let(onDeleted)
    }

    fun undoDelete(row: SupplementEntity) = action { repository.undoDelete(row) }

    fun move(id: String, up: Boolean) = action { repository.move(id, up) }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            writes.withLock {
                try {
                    error.value = null
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error.value = e.message ?: "저장하지 못했어요"
                }
            }
        }
    }
}
