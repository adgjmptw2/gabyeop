package io.github.adgjmptw2.diet.ui.supplements

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.domain.SUPPLEMENT_SLOT_ORDER
import io.github.adgjmptw2.diet.domain.Supplement
import io.github.adgjmptw2.diet.domain.SupplementCheck
import io.github.adgjmptw2.diet.domain.supplementProgress
import io.github.adgjmptw2.diet.domain.supplementSlotFlags
import io.github.adgjmptw2.diet.domain.supplementSlotLabel
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.LocalDate

/**
 * 홈·식단·하루 보기에서 함께 쓰는 영양제 체크 카드.
 * 칩을 한 번 누르면 그날 그 때의 먹음/해제가 바로 저장된다.
 */
@Composable
fun SupplementCard(
    date: LocalDate,
    modifier: Modifier = Modifier,
    vm: SupplementViewModel = hiltViewModel(),
) {
    val state by vm.supplements.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    val checks by remember(vm, date) { vm.observeDay(date) }
        .collectAsStateWithLifecycle(initialValue = emptySet())

    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("영양제", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (state.total > 0) {
                    val progress = supplementProgress(state.supplements, checks, state.splitByTime)
                    Text("${progress.done}/${progress.total}",
                        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { editing = true }, contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.heightIn(min = 32.dp)) {
                        Text("편집", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (state.total == 0) {
                FilledTonalButton(onClick = { editing = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("영양제 추가")
                }
            } else if (state.splitByTime) {
                SplitSupplementChips(state.supplements, checks) { supplement, slot ->
                    vm.toggle(date, supplement.id, slot)
                }
            } else {
                CombinedSupplementChips(state.supplements, checks) { supplement ->
                    vm.toggleAll(date, supplement.id)
                }
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (editing) {
        SupplementSheet(vm = vm, onDismiss = { editing = false })
    }
}

/** 나누기 켬: 영양제가 있는 때만 줄을 만들고, 각 줄에 그 때의 칩을 둔다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitSupplementChips(
    supplements: List<Supplement>,
    checks: Set<SupplementCheck>,
    onToggle: (Supplement, Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SUPPLEMENT_SLOT_ORDER.forEach { slot ->
            val inSlot = supplements.filter { (it.slots and slot) != 0 }
            if (inSlot.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(supplementSlotLabel(slot).orEmpty(), Modifier.width(36.dp).padding(top = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        inSlot.forEach { supplement ->
                            SupplementChip(
                                name = supplement.name,
                                selected = SupplementCheck(supplement.id, slot) in checks,
                                slotLabel = supplementSlotLabel(slot).orEmpty(),
                            ) { onToggle(supplement, slot) }
                        }
                    }
                }
            }
        }
    }
}

/** 나누기 끔: 영양제마다 칩 하나. 모든 때를 채웠을 때만 선택으로 보인다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CombinedSupplementChips(
    supplements: List<Supplement>,
    checks: Set<SupplementCheck>,
    onToggle: (Supplement) -> Unit,
) {
    var expanded by rememberSaveable(supplements.map { it.id }) { mutableStateOf(false) }
    val allChecked = { supplement: Supplement ->
        val flags = supplementSlotFlags(supplement.slots)
        flags.isNotEmpty() && flags.all { SupplementCheck(supplement.id, it) in checks }
    }
    if (expanded) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            supplements.forEach { supplement ->
                SupplementChip(supplement.name, allChecked(supplement), slotLabel = null) { onToggle(supplement) }
            }
            TextButton(onClick = { expanded = false }, contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.heightIn(min = 32.dp)) {
                Text("접기", style = MaterialTheme.typography.labelMedium)
            }
        }
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth().heightIn(max = 70.dp).wrapContentHeight(align = Alignment.Top),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxLines = 2,
            overflow = FlowRowOverflow.expandIndicator {
                TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.height(32.dp)) {
                    Text("전체 보기", style = MaterialTheme.typography.labelMedium)
                }
            },
        ) {
            supplements.forEach { supplement ->
                SupplementChip(supplement.name, allChecked(supplement), slotLabel = null) { onToggle(supplement) }
            }
        }
    }
}

@Composable
private fun SupplementChip(name: String, selected: Boolean, slotLabel: String?, onClick: () -> Unit) {
    val description = when {
        slotLabel == null && selected -> "$name 먹음, 해제"
        slotLabel == null -> "$name 먹음 체크"
        selected -> "$slotLabel $name 먹음, 해제"
        else -> "$slotLabel $name 먹음 체크"
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
        },
        leadingIcon = if (selected) {
            { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp)) }
        } else null,
        modifier = Modifier.height(32.dp).semantics { contentDescription = description },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = MaterialTheme.shapes.small,
    )
}
