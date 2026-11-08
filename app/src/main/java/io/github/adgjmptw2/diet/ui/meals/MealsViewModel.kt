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
import io.github.adgjmptw2.diet.domain.CustomFoodDraft
import io.github.adgjmptw2.diet.domain.Food
import io.github.adgjmptw2.diet.domain.FoodEmoji
import io.github.adgjmptw2.diet.domain.FoodUnit
import io.github.adgjmptw2.diet.domain.Goal
import io.github.adgjmptw2.diet.domain.Macro
import io.github.adgjmptw2.diet.domain.MealEntry
import io.github.adgjmptw2.diet.domain.MealType
import io.github.adgjmptw2.diet.domain.Nutrition
import io.github.adgjmptw2.diet.domain.toCustomDraft
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
    val recent: List<Food> = emptyList(),
    val favorites: List<Food> = emptyList(),
    val customs: List<Food> = emptyList(),
    val preparing: Boolean = true,
    val error: String? = null,
)

enum class FoodListTab { RECENT, FAVORITES, CUSTOM }

data class MealEditorState(
    val date: LocalDate,
    val type: MealType,
    val session: String = UUID.randomUUID().toString(),
    val food: Food? = null,
    val original: MealEntry? = null,
    val custom: Boolean = false,
    val name: String = "",
    val servingText: String = "1",
    val kcalText: String = "",
    val unit: String = "팩",
    val amountText: String = "1",
    val favoriteIds: Set<String> = emptySet(),
    val customBrand: String = "",
    val customCarbText: String = "",
    val customProtText: String = "",
    val customFatText: String = "",
    val servingWeightText: String = "",
    val macrosExpanded: Boolean = false,
    val editingCustomId: String? = null,
    val editingCustomFood: Food? = null,
    val favorite: Boolean = false,
    val photoPath: String? = null,
    val photoBusy: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val choosingFood: Boolean get() = food == null && !custom
    val editingSavedCustom: Boolean get() = editingCustomFood != null && !custom
    val unitKind: FoodUnit get() = FoodUnit.from(unit)
    val serving: Double? get() = if (custom) parseMealAmount(servingText) else food?.serving
    /** 기록 양. 팩/개는 개수, g/ml는 그램·밀리리터다. */
    val amount: Double? get() = parseMealAmount(amountText)
    val servingWeight: Double? get() = parseOptionalMealAmount(servingWeightText)
    val previewFood: Food? get() {
        if (!custom) return food
        if (name.isBlank()) return null
        val kcal = parseMealKcal(kcalText) ?: return null
        val kind = unitKind
        if (kind.isCount) {
            val count = 1.0
            return Food(
                editingCustomId.orEmpty(), name.trim(), customBrand.trim().ifEmpty { null }, "직접 입력", name.trim(),
                kcal100 = 0.0, carb100 = null, prot100 = null, fat100 = null,
                serving = count, unit = kind.label, custom = true, favorite = favorite,
                servingKcal = kcal,
                servingCarb = parseCustomMacro(customCarbText),
                servingProt = parseCustomMacro(customProtText),
                servingFat = parseCustomMacro(customFatText),
                servingWeightG = servingWeight,
            )
        }
        val serving = serving ?: return null
        val density = kcal / serving * 100.0
        if (!density.isFinite()) return null
        return Food(
            editingCustomId.orEmpty(), name.trim(), customBrand.trim().ifEmpty { null }, "직접 입력", name.trim(),
            density, parseCustomMacro(customCarbText)?.let { it / serving * 100 },
            parseCustomMacro(customProtText)?.let { it / serving * 100 },
            parseCustomMacro(customFatText)?.let { it / serving * 100 },
            serving, kind.label, true, favorite,
        )
    }
    val nutrition: Macro? get() {
        val food = previewFood ?: return null
        val amount = amount ?: return null
        return Nutrition.forAmount(food, amount).takeIf { macro ->
            macro.kcal.isFinite() && macro.kcal <= MAX_ENTRY_KCAL &&
                listOfNotNull(macro.carb, macro.prot, macro.fat).all { it.isFinite() && it <= MAX_ENTRY_KCAL }
        }
    }
    val canSave: Boolean get() = nutrition != null && !photoBusy && !saving
    val canSaveCustom: Boolean get() = canSave && name.isNotBlank() && editMacroValid &&
        (unitKind.isCount || serving != null) &&
        (servingWeightText.isBlank() || servingWeight != null)
    val saveEnabled: Boolean get() = if (custom) canSaveCustom else canSave
    fun isFavorite(id: String): Boolean = id in favoriteIds || food?.favorite == true
    private val editMacroValid: Boolean
        get() = listOf(customCarbText, customProtText, customFatText).all { it.isBlank() || parseCustomMacro(it) != null }
}

/** 서버가 받는 한 줄 최대치와 같게 맞춘다 */
private const val MAX_ENTRY_KCAL = 20_000.0

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
            val current = weightEntries.firstOrNull { e -> !e.date.isAfter(now) }?.weightKg ?: it.startWeightKg
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
            query.isEmpty() -> combine(
                foods.recentFoods(),
                foods.favorites(),
                foods.customFoods(),
            ) { recent, favorites, customs ->
                FoodSearchState(
                    query = query, foods = recent, recent = recent, favorites = favorites,
                    customs = customs, preparing = false,
                )
            }
            else -> combine(foods.favoriteIds(), flow { emit(foods.search(query)) }) { ids, results ->
                FoodSearchState(
                    query = query,
                    foods = results
                        .map { it.copy(favorite = it.id in ids) }
                        .sortedWith(compareByDescending<Food> { it.custom }.thenByDescending { it.favorite }),
                    preparing = false,
                )
            }
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(FoodSearchState(query = query, preparing = false, error = "음식을 불러오지 못했어요"))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodSearchState())

    init {
        prepareFoods()
        viewModelScope.launch {
            var previous = selectedDate.value
            today.collect { now ->
                if (now != previous) {
                    if (selectedDate.value == previous) selectedDate.value = now
                    previous = now
                }
            }
        }
    }

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
        val kind = FoodUnit.from(entry.unit)
        val amount = entry.amount.takeIf { it.isFinite() && it > 0 } ?: 1.0
        // 기록 kcal은 "먹은 양 전체"다. 팩/개는 1개당, g/ml는 100g당으로 되돌려 둔다.
        val basis = if (kind.isCount) 1.0 else amount
        val food = Food(
            entry.foodId.orEmpty(), entry.name, null, "", entry.name,
            if (kind.isCount) 0.0 else entry.kcal / basis * 100.0,
            if (kind.isCount) null else entry.carb?.let { it / basis * 100 },
            if (kind.isCount) null else entry.prot?.let { it / basis * 100 },
            if (kind.isCount) null else entry.fat?.let { it / basis * 100 },
            basis, entry.unit, false,
            servingKcal = if (kind.isCount) entry.kcal / amount else null,
            servingCarb = if (kind.isCount) entry.carb?.div(amount) else null,
            servingProt = if (kind.isCount) entry.prot?.div(amount) else null,
            servingFat = if (kind.isCount) entry.fat?.div(amount) else null,
        )
        val draft = MealEditorState(
            date = entry.date, type = entry.type, food = food, original = entry,
            amountText = entry.amount.amountText(), unit = entry.unit, photoPath = entry.photoPath,
            servingText = basis.amountText(),
        )
        _editor.value = draft
        val id = entry.foodId ?: return
        viewModelScope.launch {
            try {
                val stored = foods.byId(id)?.takeIf { it.unit == entry.unit } ?: return@launch
                updateSession(draft.session) {
                    it.copy(
                        food = stored,
                        servingText = stored.servingAmount.amountText(),
                        favorite = stored.favorite,
                        favoriteIds = if (stored.favorite) it.favoriteIds + id else it.favoriteIds - id,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 동기화로 들어온 기록은 식품 원본이 없어도 수정할 수 있다.
            }
        }
    }

    fun search(query: String) { _query.value = query.take(100) }

    fun choose(food: Food) = updateEditor { draft ->
        draft.copy(
            food = food, amountText = food.servingAmount.amountText(), unit = food.unitKind.label,
            servingText = food.servingAmount.amountText(),
            favorite = food.favorite,
            favoriteIds = if (food.favorite) draft.favoriteIds + food.id else draft.favoriteIds - food.id,
            editingCustomFood = food.takeIf { food.custom },
            editingCustomId = if (food.custom) food.id else null,
            error = null,
        )
    }

    fun custom() = updateEditor {
        it.copy(custom = true, name = _query.value.trim(), amountText = "1", servingText = "1",
            unit = FoodUnit.PACK.label, kcalText = "", customBrand = "",
            customCarbText = "", customProtText = "", customFatText = "", servingWeightText = "",
            macrosExpanded = false, editingCustomId = null, editingCustomFood = null,
            favorite = false, favoriteIds = emptySet(), error = null)
    }

    fun backToSearch() = updateEditor {
        it.copy(food = null, custom = false, name = "", kcalText = "", amountText = "",
            unit = FoodUnit.GRAM.label, servingText = "100", servingWeightText = "", macrosExpanded = false,
            customBrand = "", customCarbText = "", customProtText = "", customFatText = "",
            editingCustomId = null, editingCustomFood = null, favorite = false, error = null)
    }

    fun amount(text: String) = updateEditor { it.copy(amountText = mealNumberInput(text), error = null) }

    /** 칩은 1회분 배수를 고른다. 팩/개는 1개를 기준으로 ½·1·2개다. */
    fun portion(multiplier: Double) = updateEditor {
        val base = it.previewFood?.servingAmount ?: it.serving ?: return@updateEditor it
        it.copy(amountText = (base * multiplier).amountText(), error = null)
    }

    fun mealType(type: MealType) = updateEditor { it.copy(type = type, error = null) }
    fun customName(name: String) = updateEditor { it.copy(name = name.take(100), error = null) }
    fun customKcal(text: String) = updateEditor { it.copy(kcalText = mealNumberInput(text), error = null) }
    fun customBrand(value: String) = updateEditor { it.copy(customBrand = value.take(40), error = null) }
    fun customCarb(text: String) = updateEditor { it.copy(customCarbText = mealNumberInput(text), error = null) }
    fun customProt(text: String) = updateEditor { it.copy(customProtText = mealNumberInput(text), error = null) }
    fun customFat(text: String) = updateEditor { it.copy(customFatText = mealNumberInput(text), error = null) }

    fun customServing(text: String) = updateEditor { draft ->
        val value = mealNumberInput(text)
        if (draft.unitKind.isCount) draft.copy(servingText = value, error = null)
        else draft.copy(servingText = value, amountText = if (draft.amountText == draft.servingText) value else draft.amountText, error = null)
    }

    fun customServingsExpanded(expanded: Boolean) = updateEditor { it.copy(macrosExpanded = expanded) }

    fun customServingWeight(text: String) = updateEditor { it.copy(servingWeightText = mealNumberInput(text), error = null) }

    /** 단위를 바꿔도 영양값은 "1회분" 기준 그대로 두고, 1회분 양만 새 단위 기본값으로 맞춘다. */
    fun customUnit(unit: String) = updateEditor { draft ->
        val next = FoodUnit.from(unit)
        if (next == draft.unitKind) return@updateEditor draft
        val newServing = if (next.isCount) "1" else "100"
        draft.copy(
            unit = next.label,
            servingText = newServing,
            amountText = if (draft.unitKind.isCount != next.isCount || draft.amountText == draft.servingText) {
                newServing
            } else {
                draft.amountText
            },
            servingWeightText = if (next.isCount) draft.servingWeightText else "",
            error = null,
        )
    }

    fun customFavorite() = updateEditor { it.copy(favorite = !it.favorite, error = null) }

    fun toggleFavorite(id: String, currentlyFavorite: Boolean? = null) {
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                val draft = _editor.value ?: return@launch
                val desired = currentlyFavorite?.not() ?: (id !in draft.favoriteIds)
                foods.setFavorite(id, desired)
                updateSession(draft.session) {
                    it.copy(
                        favorite = if (it.food?.id == id) desired else it.favorite,
                        favoriteIds = if (desired) it.favoriteIds + id else it.favoriteIds - id,
                        food = it.food?.takeIf { saved -> saved.id == id }?.copy(favorite = desired) ?: it.food,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateEditor { it.copy(error = "즐겨찾기를 바꾸지 못했어요") }
            }
        }
    }

    fun newCustom() {
        _query.value = ""
        updateEditor {
            it.copy(custom = true, food = null, name = "", servingText = "1", kcalText = "",
                unit = FoodUnit.PACK.label, amountText = "1", customBrand = "",
                customCarbText = "", customProtText = "", customFatText = "", servingWeightText = "",
                macrosExpanded = false, editingCustomId = null, editingCustomFood = null, favorite = false, error = null)
        }
    }

    /** 공공 음식 → 내 음식 만들기. 이름·1회분·영양·단위를 그대로 채우고 원본은 바꾸지 않는다. */
    fun copyAsCustom(food: Food) {
        val draft = food.toCustomDraft()
        applyCustomDraft(draft, editingId = null, editingFood = null, favorite = false)
    }

    fun editCustom(food: Food) {
        if (!food.custom) return
        val draft = food.toCustomDraft()
        applyCustomDraft(draft, editingId = food.id, editingFood = food, favorite = food.favorite)
    }

    private fun applyCustomDraft(draft: CustomFoodDraft, editingId: String?, editingFood: Food?, favorite: Boolean) {
        updateEditor {
            it.copy(
                custom = true, food = null, name = draft.name, customBrand = draft.brand.orEmpty(),
                unit = draft.unit, servingText = draft.serving.amountText(), kcalText = draft.kcal.amountText(),
                amountText = draft.serving.amountText(),
                customCarbText = draft.carb?.amountText().orEmpty(),
                customProtText = draft.prot?.amountText().orEmpty(),
                customFatText = draft.fat?.amountText().orEmpty(),
                servingWeightText = draft.servingWeightG?.amountText().orEmpty(),
                macrosExpanded = listOfNotNull(draft.carb, draft.prot, draft.fat).isNotEmpty(),
                editingCustomId = editingId, editingCustomFood = editingFood,
                favorite = favorite, error = null,
            )
        }
    }

    fun deleteCustom(food: Food) {
        if (_editor.value?.saving == true) return
        viewModelScope.launch {
            try {
                foods.deleteCustom(food.id)
                updateEditor {
                    if (it.editingCustomId == food.id) {
                        it.copy(custom = false, name = "", kcalText = "", customBrand = "",
                            customCarbText = "", customProtText = "", customFatText = "", servingWeightText = "",
                            editingCustomId = null, editingCustomFood = null, food = null, error = null)
                    } else it
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateEditor { it.copy(error = "내 음식을 삭제하지 못했어요") }
            }
        }
    }

    fun saveCustomFoodOnly() {
        val draft = _editor.value?.takeIf { it.custom } ?: return
        val preview = draft.previewFood ?: return
        if (!draft.canSaveCustom) return
        updateSession(draft.session) { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val perServing = Nutrition.forServing(preview)
                val customDraft = CustomFoodDraft(
                    name = preview.name, brand = preview.brand, unit = preview.unit,
                    serving = preview.servingAmount,
                    kcal = perServing.kcal,
                    carb = perServing.carb,
                    prot = perServing.prot,
                    fat = perServing.fat,
                    servingWeightG = preview.servingWeightG,
                )
                val saved = draft.editingCustomId?.let { id ->
                    foods.updateCustom(id, customDraft, preview.favorite)
                        ?: throw IllegalStateException("내 음식을 찾지 못했어요")
                } ?: foods.createCustom(customDraft, preview.favorite)
                updateSession(draft.session) {
                    it.copy(
                        food = saved.copy(favorite = preview.favorite), custom = false,
                        editingCustomFood = saved.copy(favorite = preview.favorite), editingCustomId = saved.id,
                        favorite = preview.favorite, servingText = saved.servingAmount.amountText(),
                        amountText = saved.servingAmount.amountText(), unit = saved.unit,
                        saving = false, error = null,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateSession(draft.session) { it.copy(saving = false, error = "내 음식을 저장하지 못했어요") }
            }
        }
    }

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
        val draft = _editor.value?.takeIf { it.saveEnabled && !it.custom } ?: return
        val preview = draft.previewFood ?: return
        val macro = draft.nutrition ?: return
        val amount = draft.amount ?: return
        updateSession(draft.session) { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val food = preview
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
