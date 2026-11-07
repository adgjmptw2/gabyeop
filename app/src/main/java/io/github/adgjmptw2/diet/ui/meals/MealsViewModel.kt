package io.github.adgjmptw2.diet.ui.meals

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.FoodRepository
import io.github.adgjmptw2.diet.data.MealRepository
import io.github.adgjmptw2.diet.data.ProfileRepository
import io.github.adgjmptw2.diet.data.WeightRepository
import io.github.adgjmptw2.diet.domain.Calories
import io.github.adgjmptw2.diet.domain.Food
import io.github.adgjmptw2.diet.domain.FoodEmoji
import io.github.adgjmptw2.diet.domain.Goal
import io.github.adgjmptw2.diet.domain.Macro
import io.github.adgjmptw2.diet.domain.MealEntry
import io.github.adgjmptw2.diet.domain.MealType
import io.github.adgjmptw2.diet.domain.Nutrition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class MealsUiState(
    val date: LocalDate,
    val today: LocalDate,
    val entries: List<MealEntry> = emptyList(),
    val total: Macro = Macro(0.0, 0.0, 0.0, 0.0),
    val targetKcal: Int? = null,
    val error: String? = null,
)

data class FoodSearchState(
    val query: String = "",
    val foods: List<Food> = emptyList(),
    val preparing: Boolean = true,
    val searching: Boolean = false,
    val error: String? = null,
)

data class MealEditorState(
    val date: LocalDate,
    val type: MealType,
    val session: String = UUID.randomUUID().toString(),
    val food: Food? = null,
    val original: MealEntry? = null,
    val custom: Boolean = false,
    val name: String = "",
    val servingText: String = "100",
    val kcalText: String = "",
    val unit: String = "g",
    val amountText: String = "",
    val photoPath: String? = null,
    val photoBusy: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val choosingFood: Boolean get() = food == null && !custom
    val serving: Double? get() = if (custom) parseMealAmount(servingText) else food?.serving
    val amount: Double? get() = parseMealAmount(amountText)
    val previewFood: Food? get() {
        if (!custom) return food
        val serving = serving ?: return null
        val kcal = parseMealKcal(kcalText) ?: return null
        if (name.isBlank()) return null
        val density = kcal / serving * 100.0
        if (!density.isFinite()) return null
        return Food("", name.trim(), null, "직접 입력", name.trim(), density, null, null, null, serving, unit, true)
    }
    val nutrition: Macro? get() {
        val food = previewFood ?: return null
        val amount = amount ?: return null
        return Nutrition.forAmount(food, amount).takeIf { macro ->
            macro.kcal.isFinite() && listOfNotNull(macro.carb, macro.prot, macro.fat).all { it.isFinite() }
        }
    }
    val canSave: Boolean get() = nutrition != null && !photoBusy && !saving
}

private data class ImportState(val preparing: Boolean = true, val error: String? = null)
private data class MealDay(val date: LocalDate, val entries: List<MealEntry> = emptyList(), val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MealsViewModel @Inject constructor(
    private val foods: FoodRepository,
    private val meals: MealRepository,
    profiles: ProfileRepository,
    weights: WeightRepository,
    private val photos: MealPhotoStore,
    private val clock: Clock,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now(clock))
    private val importing = MutableStateFlow(ImportState())
    private var importJob: Job? = null
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _editor = MutableStateFlow<MealEditorState?>(null)
    val editor = _editor.asStateFlow()
    private var camera: CameraCapture? = null
    private var cameraSession: String? = null

    private val today = flow {
        while (true) {
            emit(LocalDate.now(clock))
            delay(30_000)
        }
    }.distinctUntilChanged()

    val state: StateFlow<MealsUiState> = combine(
        selectedDate.flatMapLatest { date ->
            meals.day(date).map { MealDay(date, it) }.catch { e ->
                if (e is CancellationException) throw e
                emit(MealDay(date, error = "식단을 불러오지 못했어요"))
            }
        },
        profiles.profile,
        weights.entries,
        today,
    ) { day, profile, weightEntries, now ->
        val target = profile?.let {
            val current = weightEntries.firstOrNull()?.weightKg ?: it.startWeightKg
            val progress = Goal.progress(it.startWeightKg, it.targetWeightKg, current)
            Calories.target(it, current, now, progress.reached).target
        }
        MealsUiState(day.date, now, day.entries, Nutrition.total(day.entries), target, day.error)
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(MealsUiState(selectedDate.value, LocalDate.now(clock), error = "식단을 불러오지 못했어요"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealsUiState(selectedDate.value, LocalDate.now(clock)))

    val search: StateFlow<FoodSearchState> = combine(
        _query.debounce(250).distinctUntilChanged(), importing,
    ) { query, status -> query.trim() to status }.flatMapLatest { (query, status) ->
        when {
            status.preparing -> flowOf(FoodSearchState(query = query))
            status.error != null -> flowOf(FoodSearchState(query = query, preparing = false, error = status.error))
            query.isEmpty() -> meals.recentFoods().map { FoodSearchState(foods = it, preparing = false) }
            else -> flow {
                emit(FoodSearchState(query = query, preparing = false, searching = true))
                emit(FoodSearchState(query = query, foods = foods.search(query), preparing = false))
            }
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(FoodSearchState(query = query, preparing = false, error = "음식을 불러오지 못했어요"))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodSearchState())

    init { prepareFoods() }

    fun prepareFoods() {
        if (importJob?.isActive == true) return
        importing.value = ImportState()
        importJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { foods.ensureImported() }
                importing.value = ImportState(preparing = false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("DietFoods", "food import failed", e)
                importing.value = ImportState(preparing = false, error = "음식 목록을 준비하지 못했어요")
            }
        }
    }

    fun selectDate(date: LocalDate) {
        if (date <= LocalDate.now(clock)) selectedDate.value = date
    }

    fun moveDate(days: Long) = selectDate(selectedDate.value.plusDays(days))

    fun add(type: MealType) {
        if (_editor.value != null) return
        _query.value = ""
        _editor.value = MealEditorState(selectedDate.value, type)
    }

    fun edit(entry: MealEntry) {
        if (_editor.value != null) return
        val basis = entry.amount.takeIf { it.isFinite() && it > 0 } ?: 100.0
        // 식품 DB가 갱신돼도 기존 기록의 영양값을 보존한다.
        val food = Food(
            entry.foodId.orEmpty(), entry.name, null, "", entry.name,
            entry.kcal / basis * 100, entry.carb?.let { it / basis * 100 },
            entry.prot?.let { it / basis * 100 }, entry.fat?.let { it / basis * 100 },
            basis, entry.unit, false,
        )
        val draft = MealEditorState(
            date = entry.date, type = entry.type, food = food, original = entry,
            amountText = entry.amount.amountText(), unit = entry.unit, photoPath = entry.photoPath,
        )
        _editor.value = draft
        val id = entry.foodId ?: return
        viewModelScope.launch {
            try {
                val serving = foods.byId(id)?.takeIf { it.unit == entry.unit }?.serving ?: return@launch
                updateSession(draft.session) { it.copy(food = it.food?.copy(serving = serving)) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 동기화로 들어온 기록은 식품 원본이 없어도 수정할 수 있다.
            }
        }
    }

    fun search(query: String) { _query.value = query.take(100) }

    fun choose(food: Food) = updateEditor {
        it.copy(food = food, amountText = food.serving.amountText(), unit = food.unit, error = null)
    }

    fun custom() = updateEditor {
        it.copy(custom = true, name = _query.value.trim(), amountText = "100", error = null)
    }

    fun backToSearch() = updateEditor {
        it.copy(food = null, custom = false, name = "", kcalText = "", amountText = "", error = null)
    }

    fun amount(text: String) = updateEditor { it.copy(amountText = mealNumberInput(text), error = null) }
    fun portion(multiplier: Double) = updateEditor {
        it.serving?.let { serving -> it.copy(amountText = (serving * multiplier).amountText(), error = null) } ?: it
    }
    fun mealType(type: MealType) = updateEditor { it.copy(type = type, error = null) }
    fun customName(name: String) = updateEditor { it.copy(name = name.take(100), error = null) }
    fun customKcal(text: String) = updateEditor { it.copy(kcalText = mealNumberInput(text), error = null) }
    fun customServing(text: String) = updateEditor {
        val value = mealNumberInput(text)
        it.copy(servingText = value, amountText = if (it.amountText == it.servingText) value else it.amountText, error = null)
    }
    fun customUnit(unit: String) = updateEditor { it.copy(unit = if (unit == "ml") "ml" else "g") }
    fun photoUnavailable() = updateEditor { it.copy(error = "사진 선택기를 열지 못했어요") }

    fun attachPhoto(uri: Uri) {
        val draft = _editor.value?.takeUnless { it.saving || it.photoBusy } ?: return
        updateSession(draft.session) { it.copy(photoBusy = true, error = null) }
        viewModelScope.launch { importPhoto(draft, uri) }
    }

    fun takePhoto(launch: (Uri) -> Unit) {
        val draft = _editor.value?.takeUnless { it.saving || it.photoBusy } ?: return
        updateSession(draft.session) { it.copy(photoBusy = true, error = null) }
        viewModelScope.launch {
            try {
                camera?.let { photos.discardCapture(it) }
                val capture = photos.cameraCapture()
                camera = capture
                cameraSession = draft.session
                launch(capture.uri)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                camera?.let { photos.discardCapture(it) }
                camera = null
                cameraSession = null
                updateSession(draft.session) { it.copy(error = "카메라를 열지 못했어요") }
            } finally {
                updateSession(draft.session) { it.copy(photoBusy = false) }
            }
        }
    }

    fun cameraResult(success: Boolean) {
        val capture = camera ?: return
        val session = cameraSession
        camera = null
        cameraSession = null
        val draft = _editor.value?.takeIf { it.session == session }
        if (success && draft != null) updateSession(draft.session) { it.copy(photoBusy = true, error = null) }
        viewModelScope.launch {
            try {
                if (success && draft != null) importPhoto(draft, capture.uri)
            } finally {
                withContext(NonCancellable) { photos.discardCapture(capture) }
            }
        }
    }

    private suspend fun importPhoto(draft: MealEditorState, uri: Uri) {
        try {
            // 파일 생성 후 취소돼 경로를 잃지 않도록, 반환과 정리는 함께 끝낸다.
            withContext(NonCancellable) {
                val path = photos.store(uri)
                if (_editor.value?.session == draft.session) {
                    updateSession(draft.session) { it.copy(photoPath = path) }
                    if (draft.photoPath != draft.original?.photoPath) photos.deletePhoto(draft.photoPath)
                } else photos.deletePhoto(path)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            updateSession(draft.session) { it.copy(error = "사진을 불러오지 못했어요") }
        } finally {
            updateSession(draft.session) { it.copy(photoBusy = false) }
        }
    }

    fun removePhoto() {
        val draft = _editor.value?.takeUnless { it.saving || it.photoBusy } ?: return
        updateSession(draft.session) { it.copy(photoPath = null, error = null) }
        if (draft.photoPath != draft.original?.photoPath) viewModelScope.launch {
            runCatching { photos.deletePhoto(draft.photoPath) }
        }
    }

    fun dismissEditor() {
        val draft = _editor.value?.takeUnless { it.saving || it.photoBusy } ?: return
        _editor.value = null
        _query.value = ""
        cleanDraftPhoto(draft)
    }

    fun save() {
        val draft = _editor.value?.takeIf { it.canSave } ?: return
        val preview = draft.previewFood ?: return
        val macro = draft.nutrition ?: return
        val amount = draft.amount ?: return
        updateSession(draft.session) { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val food = if (draft.custom) foods.createCustom(preview.name, preview.kcal100, preview.serving, preview.unit) else preview
                meals.save(MealEntry(
                    id = draft.original?.id.orEmpty(), date = draft.date, type = draft.type,
                    foodId = if (draft.original != null) draft.original.foodId else food.id.takeIf { it.isNotEmpty() },
                    name = food.name, emoji = draft.original?.emoji ?: FoodEmoji.of(food.name, food.rep, food.category),
                    amount = amount, unit = food.unit, kcal = macro.kcal,
                    carb = macro.carb, prot = macro.prot, fat = macro.fat, photoPath = draft.photoPath,
                ))
                _editor.value = null
                _query.value = ""
                // 저장 성공 전까지 원래 사진을 보존해 취소·실패 시 기록이 깨지지 않게 한다.
                if (draft.original?.photoPath != draft.photoPath) {
                    runCatching { photos.deletePhoto(draft.original?.photoPath) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateSession(draft.session) { it.copy(saving = false, error = "저장하지 못했어요") }
            }
        }
    }

    fun delete() {
        val draft = _editor.value?.takeUnless { it.saving || it.photoBusy } ?: return
        val original = draft.original ?: return
        updateSession(draft.session) { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                meals.delete(original.id)
                _editor.value = null
                cleanDraftPhoto(draft) // 저장된 사진은 동기화 복구를 위해 보존한다.
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateSession(draft.session) { it.copy(saving = false, error = "삭제하지 못했어요") }
            }
        }
    }

    private fun cleanDraftPhoto(draft: MealEditorState) {
        if (draft.photoPath != draft.original?.photoPath) viewModelScope.launch {
            runCatching { photos.deletePhoto(draft.photoPath) }
        }
    }

    private fun updateEditor(transform: (MealEditorState) -> MealEditorState) {
        _editor.update { draft -> draft?.let { if (it.saving || it.photoBusy) it else transform(it) } }
    }

    private fun updateSession(session: String, transform: (MealEditorState) -> MealEditorState) {
        _editor.update { draft -> draft?.let { if (it.session == session) transform(it) else it } }
    }
}
