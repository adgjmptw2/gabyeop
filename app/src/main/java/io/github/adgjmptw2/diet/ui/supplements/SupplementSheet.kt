package io.github.adgjmptw2.diet.ui.supplements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.data.SupplementEntity
import io.github.adgjmptw2.diet.domain.SUPPLEMENT_MAX_COUNT
import io.github.adgjmptw2.diet.domain.SUPPLEMENT_NAME_MAX
import io.github.adgjmptw2.diet.domain.SUPPLEMENT_SLOT_ORDER
import io.github.adgjmptw2.diet.domain.hasDuplicateSupplementName
import io.github.adgjmptw2.diet.domain.parseSupplementName
import io.github.adgjmptw2.diet.domain.supplementSlotLabel
import io.github.adgjmptw2.diet.domain.toggleSupplementSlot
import io.github.adgjmptw2.diet.domain.truncateToCodePoints
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SupplementSheet(vm: SupplementViewModel, onDismiss: () -> Unit) {
    val state by vm.supplements.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var adding by rememberSaveable { mutableStateOf("") }
    var addingSlots by rememberSaveable { mutableStateOf(1) }
    var splitOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    val liveNames = state.supplements.map { it.name }
    val splitByTime = splitOverride ?: state.splitByTime
    LaunchedEffect(state.splitByTime) {
        if (splitOverride == state.splitByTime) splitOverride = null
    }
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it) }
    }

    fun addCurrent() {
        val clean = parseSupplementName(adding) ?: return
        if (state.total >= SUPPLEMENT_MAX_COUNT || hasDuplicateSupplementName(clean, liveNames)) return
        val slots = if (splitByTime) addingSlots else 1
        vm.add(clean, slots) { adding = "" }
    }

    fun saveRename(id: String) {
        val clean = parseSupplementName(renameText) ?: return
        val others = liveNames.filterIndexed { index, _ -> state.supplements[index].id != id }
        if (hasDuplicateSupplementName(clean, others)) return
        vm.rename(id, clean) { renamingId = null }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("영양제 관리", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("아침·점심·저녁 나누기", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = splitByTime, onCheckedChange = { checked ->
                    splitOverride = checked
                    vm.setSplitByTime(checked)
                })
            }
            if (state.total >= SUPPLEMENT_MAX_COUNT) {
                Text("20개까지 등록할 수 있어요", Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.supplements.isEmpty()) {
                Text("등록한 영양제가 없어요", Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)) {
                    itemsIndexed(state.supplements, key = { _, supplement -> supplement.id }) { index, supplement ->
                        if (renamingId == supplement.id) {
                            val clean = parseSupplementName(renameText)
                            val others = liveNames.filterIndexed { i, _ -> state.supplements[i].id != supplement.id }
                            val duplicate = clean != null && hasDuplicateSupplementName(clean, others)
                            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(renameText, { raw ->
                                    renameText = truncateToCodePoints(raw, SUPPLEMENT_NAME_MAX)
                                }, modifier = Modifier.fillMaxWidth(), label = { Text("이름") }, singleLine = true,
                                    isError = duplicate,
                                    supportingText = { if (duplicate) Text("같은 이름이 있어요") },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { saveRename(supplement.id) }))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { renamingId = null }) { Text("취소") }
                                    TextButton(onClick = { saveRename(supplement.id) },
                                        enabled = clean != null && !duplicate) { Text("저장") }
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "이름 수정", onClick = {
                                    renamingId = supplement.id; renameText = supplement.name
                                }).heightIn(min = 52.dp).padding(start = 20.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(supplement.name, Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { vm.move(supplement.id, up = true) }, enabled = index > 0) {
                                        Icon(painterResource(R.drawable.ic_arrow_up), "위로", Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { vm.move(supplement.id, up = false) },
                                        enabled = index < state.supplements.lastIndex) {
                                        Icon(painterResource(R.drawable.ic_arrow_down), "아래로", Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = {
                                        vm.delete(supplement.id) { deleted: SupplementEntity ->
                                            scope.launch {
                                                snackbar.currentSnackbarData?.dismiss()
                                                val result = snackbar.showSnackbar("${deleted.name} 삭제했어요",
                                                    "되돌리기", withDismissAction = true)
                                                if (result == SnackbarResult.ActionPerformed) vm.undoDelete(deleted)
                                            }
                                        }
                                    }) {
                                        Icon(painterResource(R.drawable.ic_delete), "삭제", Modifier.size(20.dp))
                                    }
                                }
                                if (splitByTime) {
                                    FlowRow(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SUPPLEMENT_SLOT_ORDER.forEach { slot ->
                                            FilterChip(
                                                selected = (supplement.slots and slot) != 0,
                                                onClick = {
                                                    vm.setSlots(supplement.id,
                                                        toggleSupplementSlot(supplement.slots, slot))
                                                },
                                                label = { Text(supplementSlotLabel(slot).orEmpty()) },
                                                modifier = Modifier.height(32.dp),
                                                shape = MaterialTheme.shapes.small,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            if (splitByTime) {
                FlowRow(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUPPLEMENT_SLOT_ORDER.forEach { slot ->
                        FilterChip(
                            selected = (addingSlots and slot) != 0,
                            onClick = { addingSlots = toggleSupplementSlot(addingSlots, slot) },
                            label = { Text(supplementSlotLabel(slot).orEmpty()) },
                            modifier = Modifier.height(32.dp),
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val clean = parseSupplementName(adding)
                val duplicate = clean != null && hasDuplicateSupplementName(clean, liveNames)
                OutlinedTextField(adding, { raw -> adding = truncateToCodePoints(raw, SUPPLEMENT_NAME_MAX) },
                    modifier = Modifier.weight(1f), placeholder = { Text("예: 비타민D") }, singleLine = true,
                    isError = duplicate,
                    supportingText = { if (duplicate) Text("같은 이름이 있어요") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addCurrent() }))
                TextButton(onClick = { addCurrent() },
                    enabled = clean != null && !duplicate && state.total < SUPPLEMENT_MAX_COUNT) { Text("추가") }
            }
            SnackbarHost(snackbar, Modifier.padding(bottom = 8.dp))
        }
    }
}
