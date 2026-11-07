package io.github.adgjmptw2.diet.ui.meals

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
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
import io.github.adgjmptw2.diet.domain.MealType
import io.github.adgjmptw2.diet.domain.Nutrition
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.io.File
import kotlin.math.abs

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
                FoodSearch(query, search, vm, Modifier.weight(1f))
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
                            val food = draft.food
                            if (food != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(draft.original?.emoji ?: FoodEmoji.of(food.name, food.rep, food.category), fontSize = 32.sp, fontFamily = FontFamily.Default)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(food.name, style = MaterialTheme.typography.titleMedium)
                                        food.brand?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text("1회 ${food.serving.mealNumber()}${food.unit}", style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    item { AmountFields(draft, vm) }
                    item {
                        val macro = draft.nutrition
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${macro?.kcal?.mealNumber() ?: "—"} kcal", style = MaterialTheme.typography.headlineMedium.tabularNumbers())
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
                    Button(onClick = vm::save, enabled = draft.canSave, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                        Text(if (draft.saving) "저장 중" else "저장")
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodSearch(query: String, state: FoodSearchState, vm: MealsViewModel, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query, onValueChange = vm::search, label = { Text("음식 검색") }, singleLine = true,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
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
                state.searching || state.query != query.trim() -> item { SearchStatus("검색 중", loading = true) }
                else -> {
                    if (query.isBlank()) item { SearchStatus("최근 먹은 음식") }
                    if (state.foods.isEmpty()) {
                        item { SearchStatus(if (query.isBlank()) "최근 음식 없음" else "검색 결과 없음") }
                    }
                    items(state.foods, key = { it.id }) { food -> FoodResult(food) { vm.choose(food) } }
                }
            }
        }
        OutlinedButton(
            onClick = vm::custom, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            shape = MaterialTheme.shapes.small,
        ) { Text("직접 입력") }
    }
}

@Composable
private fun SearchStatus(text: String, loading: Boolean = false) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FoodResult(food: Food, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(FoodEmoji.of(food.name, food.rep, food.category), fontSize = 28.sp, fontFamily = FontFamily.Default)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(food.name, style = MaterialTheme.typography.bodyMedium)
            food.brand?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "1회 ${food.serving.mealNumber()}${food.unit} · ${Nutrition.forAmount(food, food.serving).kcal.mealNumber()} kcal",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            listOf(0.5, 1.0, 1.5, 2.0).forEach { multiplier ->
                val expected = draft.serving?.times(multiplier)
                FilterChip(
                    selected = expected != null && draft.amount?.let { abs(it - expected) < 0.001 } == true,
                    onClick = { vm.portion(multiplier) }, enabled = enabled && expected != null,
                    label = { Text("${multiplier.mealNumber()}인분", style = MaterialTheme.typography.bodySmall.tabularNumbers()) },
                )
            }
        }
        OutlinedTextField(
            value = draft.amountText, onValueChange = vm::amount, label = { Text("양") }, suffix = { Text(draft.unit) },
            singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = draft.amountText.isNotBlank() && draft.amount == null,
            textStyle = MaterialTheme.typography.titleLarge.tabularNumbers(),
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CustomFields(draft: MealEditorState, vm: MealsViewModel) {
    val enabled = !draft.saving && !draft.photoBusy
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("직접 입력", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = draft.name, onValueChange = vm::customName, label = { Text("이름") }, singleLine = true,
            enabled = enabled, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Text(FoodEmoji.of(draft.name, draft.name, ""), fontSize = 24.sp, fontFamily = FontFamily.Default) },
        )
        Text("1회분 기준", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.servingText, onValueChange = vm::customServing, label = { Text("1회분 양") },
                singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = draft.servingText.isNotBlank() && draft.serving == null,
                shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f),
            )
            listOf("g", "ml").forEach { unit ->
                FilterChip(selected = draft.unit == unit, onClick = { vm.customUnit(unit) }, enabled = enabled, label = { Text(unit) })
            }
        }
        OutlinedTextField(
            value = draft.kcalText, onValueChange = vm::customKcal, label = { Text("1회분 칼로리") }, suffix = { Text("kcal") },
            singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = draft.kcalText.isNotBlank() && parseMealKcal(draft.kcalText) == null,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
        )
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
