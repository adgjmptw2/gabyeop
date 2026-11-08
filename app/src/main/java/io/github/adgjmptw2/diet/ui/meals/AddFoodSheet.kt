package io.github.adgjmptw2.diet.ui.meals

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.domain.Food
import io.github.adgjmptw2.diet.domain.FoodEmoji
import io.github.adgjmptw2.diet.domain.FoodUnit
import io.github.adgjmptw2.diet.domain.MealType
import io.github.adgjmptw2.diet.domain.Nutrition
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.io.File
import kotlin.math.abs

private fun FoodListTab.label(): String = when (this) {
    FoodListTab.RECENT -> "최근"
    FoodListTab.FAVORITES -> "즐겨찾기"
    FoodListTab.CUSTOM -> "내 음식"
}

/** 닭가슴살 · 한끼통살 갈릭맛 · 1팩 120kcal */
private fun Food.servingLabel(): String = listOfNotNull(
    name.trim().ifEmpty { null }, brand?.trim()?.ifEmpty { null }, servingSummary(),
).joinToString(" · ")

private fun Food.servingSummary(): String {
    val calories = Nutrition.forServing(this).kcal.kcalText()
    val amount = if (unitKind.isCount) {
        "${servingAmount.mealNumber()}${unitKind.label}"
    } else {
        "${serving.mealNumber()}${unitKind.label}"
    }
    return "$amount ${calories}kcal"
}

internal fun MealEditorState.amountChipLabel(multiplier: Double): String {
    return if (unitKind.isCount) {
        if (multiplier == 0.5) "½" else multiplier.mealNumber()
    } else {
        "${multiplier.mealNumber()}인분"
    }
}

@Composable
private fun FavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline),
            contentDescription = if (favorite) "즐겨찾기 해제" else "즐겨찾기",
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddFoodSheet(draft: MealEditorState, query: String, search: FoodSearchState, vm: MealsViewModel) {
    val busy = draft.saving || draft.photoBusy
    val currentBusy = rememberUpdatedState(busy)
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !currentBusy.value },
    )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::attachPhoto)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), vm::cameraResult)
    var tab by rememberSaveable { mutableStateOf<FoodListTab?>(null) }
    // 목록이 준비된 뒤 처음 한 번만 정한다. 별을 눌러 즐겨찾기가 생겨도 화면이 튀지 않게.
    var defaultTab by rememberSaveable { mutableStateOf<FoodListTab?>(null) }
    LaunchedEffect(search.preparing, search.favorites.isNotEmpty(), draft.choosingFood) {
        if (draft.choosingFood && !search.preparing && defaultTab == null) {
            defaultTab = if (search.favorites.isNotEmpty()) FoodListTab.FAVORITES else FoodListTab.RECENT
        }
    }

    ModalBottomSheet(
        onDismissRequest = vm::dismissEditor, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (draft.original != null) "기록 수정" else "음식 추가", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::dismissEditor, enabled = !busy) { Text("닫기") }
            }
            if (draft.choosingFood) {
                FoodSearch(
                    query, search, vm, tab ?: defaultTab ?: FoodListTab.RECENT,
                    onTabChange = { tab = it }, onCopyAsCustom = vm::copyAsCustom,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (draft.original == null) {
                        item { TextButton(onClick = vm::backToSearch, enabled = !busy) { Text("음식 검색") } }
                    }
                    if (draft.custom) {
                        item { CustomFields(draft, vm) }
                    } else {
                        item {
                            val food = draft.previewFood
                            if (food != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(draft.original?.emoji ?: FoodEmoji.of(food.name, food.rep, food.category), fontSize = 32.sp, fontFamily = FontFamily.Default)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(food.name, style = MaterialTheme.typography.titleMedium)
                                        food.brand?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(food.servingSummary(), style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (food.id.isNotBlank()) {
                                        val favorite = draft.isFavorite(food.id)
                                        FavoriteButton(favorite) { vm.toggleFavorite(food.id, favorite) }
                                    }
                                }
                            }
                        }
                    }
                    if (draft.editingSavedCustom) {
                        item {
                            TextButton(onClick = { draft.editingCustomFood?.let(vm::editCustom) }) {
                                Text("내 음식 수정")
                            }
                        }
                    }
                    item { AmountFields(draft, vm) }
                    item {
                        val macro = draft.nutrition
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${macro?.kcal?.kcalText() ?: "—"} kcal", style = MaterialTheme.typography.headlineMedium.tabularNumbers())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MacroLabel("탄수화물", macro?.carb, Modifier.weight(1f))
                                MacroLabel("단백질", macro?.prot, Modifier.weight(1f))
                                MacroLabel("지방", macro?.fat, Modifier.weight(1f))
                            }
                        }
                    }
                    item { MealTypePicker(draft, vm) }
                    item {
                        PhotoFields(
                            draft, onRemove = vm::removePhoto,
                            onCamera = { vm.takePhoto { camera.launch(it) } },
                            onGallery = {
                                try {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } catch (_: Exception) {
                                    vm.photoUnavailable()
                                }
                            },
                        )
                    }
                    draft.error?.let { error ->
                        item { Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                if (draft.custom && !draft.customLibraryOnly) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = draft.saveToCustomFoods, onCheckedChange = vm::saveToCustomFoods, enabled = !busy)
                        Text("내 음식에도 저장", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (draft.original != null) {
                        TextButton(
                            onClick = vm::delete, enabled = !busy,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("삭제") }
                    }
                    val onSave: () -> Unit = if (draft.custom && draft.customLibraryOnly) vm::saveCustomFoodOnly else vm::save
                    Button(onClick = onSave, enabled = draft.saveEnabled, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                        Text(
                            when {
                                draft.saving -> "저장 중"
                                draft.custom && draft.customLibraryOnly -> "내 음식 저장"
                                draft.original != null -> "저장"
                                else -> "${draft.type.label}에 추가"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodSearch(
    query: String,
    state: FoodSearchState,
    vm: MealsViewModel,
    tab: FoodListTab,
    onTabChange: (FoodListTab) -> Unit,
    onCopyAsCustom: (Food) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query, onValueChange = vm::search, label = { Text("음식 검색") }, singleLine = true,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        val showingTabs = query.isBlank()
        val selectedTab = tab
        if (showingTabs) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FoodListTab.entries.forEach { entry ->
                    FilterChip(
                        selected = selectedTab == entry,
                        onClick = { onTabChange(entry) },
                        label = { Text(entry.label()) },
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            when {
                state.preparing -> item { SearchStatus("음식 목록 준비 중", loading = true) }
                state.error != null -> item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = vm::prepareFoods) { Text("다시 시도") }
                    }
                }
                state.query != query.trim() -> item { SearchStatus("검색 중", loading = true) }
                else -> {
                    val foods = when {
                        !showingTabs -> state.foods
                        selectedTab == FoodListTab.RECENT -> state.recent
                        selectedTab == FoodListTab.FAVORITES -> state.favorites
                        else -> state.customs
                    }
                    if (foods.isEmpty()) {
                        item {
                            SearchStatus(
                                when {
                                    !showingTabs -> "검색 결과 없음"
                                    selectedTab == FoodListTab.RECENT -> "최근 음식 없음"
                                    selectedTab == FoodListTab.FAVORITES -> "즐겨찾기 없음"
                                    else -> "내 음식 없음"
                                }
                            )
                        }
                    }
                    items(foods, key = { it.id }) { food ->
                        FoodResult(
                            food = food,
                            onClick = { vm.choose(food) },
                            onLongClick = if (!food.custom && food.id.isNotBlank()) ({ onCopyAsCustom(food) }) else null,
                            onFavorite = if (food.id.isNotBlank()) {
                                { vm.toggleFavorite(food.id, food.favorite) }
                            } else null,
                            onEdit = if (food.custom) ({ vm.editCustom(food) }) else null,
                        )
                    }
                    if (showingTabs && selectedTab == FoodListTab.CUSTOM) {
                        item {
                            TextButton(
                                onClick = vm::newCustom,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            ) { Text("+ 내 음식 만들기") }
                        }
                    }
                }
            }
        }
        if (!showingTabs || selectedTab != FoodListTab.CUSTOM) {
            OutlinedButton(
                onClick = vm::custom, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                shape = MaterialTheme.shapes.small,
            ) { Text("직접 입력") }
        }
    }
}

@Composable
private fun SearchStatus(text: String, loading: Boolean = false) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FoodResult(food: Food, onClick: () -> Unit, onLongClick: (() -> Unit)?, onFavorite: (() -> Unit)?, onEdit: (() -> Unit)?) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().combinedClickable(
            onClickLabel = "음식 선택",
            onLongClickLabel = onLongClick?.let { "내 음식으로 복사" },
            onLongClick = onLongClick?.let { { menuOpen = true } },
            onClick = onClick,
        ).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(FoodEmoji.of(food.name, food.rep, food.category), fontSize = 28.sp, fontFamily = FontFamily.Default)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(food.servingLabel(), style = MaterialTheme.typography.bodyMedium.tabularNumbers())
        }
        onEdit?.let {
            IconButton(onClick = it) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = "내 음식 수정",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        onFavorite?.let { toggle ->
            FavoriteButton(food.favorite, toggle)
        }
        if (onLongClick != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("내 음식으로 복사") },
                    onClick = {
                        menuOpen = false
                        onLongClick()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountFields(draft: MealEditorState, vm: MealsViewModel) {
    val enabled = !draft.saving && !draft.photoBusy
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("먹은 양", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val multipliers = if (draft.unitKind.isCount) {
                listOf(0.5, 1.0, 2.0)
            } else {
                listOf(0.5, 1.0, 1.5, 2.0)
            }
            multipliers.forEach { multiplier ->
                val base = draft.previewFood?.servingAmount ?: draft.serving
                val expected = base?.times(multiplier)
                FilterChip(
                    selected = expected != null && draft.amount?.let { abs(it - expected) < 0.001 } == true,
                    onClick = { vm.portion(multiplier) }, enabled = enabled && expected != null,
                    label = { Text(draft.amountChipLabel(multiplier), style = MaterialTheme.typography.bodySmall.tabularNumbers()) },
                )
            }
        }
        OutlinedTextField(
            value = draft.amountText, onValueChange = vm::amount, label = { Text("양") }, suffix = { Text(draft.unitKind.label) },
            singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = draft.amountText.isNotBlank() && draft.amount == null,
            textStyle = MaterialTheme.typography.titleLarge.tabularNumbers(),
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomFields(draft: MealEditorState, vm: MealsViewModel) {
    val enabled = !draft.saving && !draft.photoBusy
    val countUnit = draft.unitKind.isCount
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (!draft.customLibraryOnly) "직접 입력" else if (draft.editingCustomId == null) "내 음식 만들기" else "내 음식 수정",
            style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.name, onValueChange = vm::customName, label = { Text("이름") }, singleLine = true,
                enabled = enabled, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f),
                leadingIcon = { Text(FoodEmoji.of(draft.name, draft.name, ""), fontSize = 24.sp, fontFamily = FontFamily.Default) },
                isError = draft.name.isBlank(),
                supportingText = { if (draft.name.isBlank()) Text("이름을 입력해 주세요") },
            )
            FavoriteButton(draft.favorite, vm::customFavorite)
        }
        OutlinedTextField(
            value = draft.customBrand, onValueChange = vm::customBrand, label = { Text("브랜드·맛(선택)") },
            singleLine = true, enabled = enabled, shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("1회분", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!countUnit) {
                OutlinedTextField(
                    value = draft.servingText, onValueChange = vm::customServing, label = { Text("1회분 양") },
                    singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = draft.servingText.isNotBlank() && draft.serving == null,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f),
                )
            } else {
                Text("1${draft.unitKind.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            FoodUnit.entries.forEach { unit ->
                FilterChip(
                    selected = draft.unitKind == unit, onClick = { vm.customUnit(unit.label) },
                    enabled = enabled, label = { Text(unit.label) },
                )
            }
        }
        if (countUnit) {
            OutlinedTextField(
                value = draft.servingWeightText, onValueChange = vm::customServingWeight,
                label = { Text("1${draft.unitKind.label} 무게(선택, g)") },
                singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = draft.servingWeightText.isNotBlank() && draft.servingWeight == null,
                shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = draft.kcalText, onValueChange = vm::customKcal,
            label = { Text(if (countUnit) "1회분 칼로리" else "1회분 칼로리 (${draft.servingText.ifBlank { "0" }}${draft.unitKind.label} 기준)") },
            suffix = { Text("kcal") },
            singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = draft.kcalText.isNotBlank() && parseMealKcal(draft.kcalText) == null,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
        )
        if (!draft.macrosExpanded) {
            TextButton(onClick = { vm.customServingsExpanded(true) }, enabled = enabled) {
                Text("탄·단·지 입력")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("탄·단·지(선택)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.customServingsExpanded(false) }, enabled = enabled) { Text("접기") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("탄수화물", draft.customCarbText, vm::customCarb),
                    Triple("단백질", draft.customProtText, vm::customProt),
                    Triple("지방", draft.customFatText, vm::customFat),
                ).forEach { (label, value, onValueChange) ->
                    OutlinedTextField(
                        value = value, onValueChange = onValueChange, label = { Text(label) }, suffix = { Text("g") },
                        singleLine = true, enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = value.isNotBlank() && parseCustomMacro(value) == null,
                        shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (draft.editingCustomFood != null) {
            TextButton(
                onClick = { draft.editingCustomFood?.let(vm::deleteCustom) },
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("내 음식 삭제") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealTypePicker(draft: MealEditorState, vm: MealsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("끼니", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { type ->
                FilterChip(
                    selected = draft.type == type, onClick = { vm.mealType(type) },
                    enabled = !draft.saving && !draft.photoBusy, label = { Text(type.label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoFields(draft: MealEditorState, onGallery: () -> Unit, onCamera: () -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("사진 추가", style = MaterialTheme.typography.titleSmall)
        if (draft.photoBusy) SearchStatus("사진 준비 중", loading = true)
        draft.photoPath?.let { path ->
            AsyncImage(
                model = File(path), contentDescription = "식단 사진", contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.ic_meal),
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.small),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val enabled = !draft.saving && !draft.photoBusy
            OutlinedButton(onClick = onGallery, enabled = enabled, shape = MaterialTheme.shapes.small) { Text("앨범") }
            OutlinedButton(onClick = onCamera, enabled = enabled, shape = MaterialTheme.shapes.small) { Text("카메라") }
            if (draft.photoPath != null) TextButton(onClick = onRemove, enabled = enabled) { Text("사진 삭제") }
        }
    }
}
