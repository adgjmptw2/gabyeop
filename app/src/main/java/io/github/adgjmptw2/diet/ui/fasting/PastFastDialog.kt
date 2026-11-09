package io.github.adgjmptw2.diet.ui.fasting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.domain.Fast
import io.github.adgjmptw2.diet.domain.Fasting
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val pastTimeLabel = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN)
private enum class PastTimeField { START, END }

@Composable
internal fun PastFastDialog(
    fast: Fast, others: List<Fast>, now: Long, zone: ZoneId,
    onDismiss: () -> Unit,
    onSave: (Long, Long, Int, (String?) -> Unit) -> Unit,
    onDelete: ((String?) -> Unit) -> Unit,
) {
    var startAt by rememberSaveable(fast.id) { mutableLongStateOf(fast.startAt) }
    var endAt by rememberSaveable(fast.id) { mutableLongStateOf(requireNotNull(fast.endAt)) }
    var hours by rememberSaveable(fast.id) { mutableIntStateOf(fast.targetHours) }
    var editingTime by rememberSaveable(fast.id) { mutableStateOf<PastTimeField?>(null) }
    var editingGoal by rememberSaveable(fast.id) { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    // 시각을 안 바꿨으면 예전 기록끼리 겹쳐 있어도 목표만 고치거나 삭제할 수 있게 한다.
    val timesChanged = startAt != fast.startAt || endAt != fast.endAt
    val validation = Fasting.pastError(fast.id, startAt, endAt, hours, if (timesChanged) others else emptyList(), now)?.message
    val duration = Fasting.elapsedMillis(startAt, now, endAt)
    val preview = "길이 " + Fasting.durationText(duration) + if (duration >= hours * Fasting.HOUR_MS) " · 목표 달성" else ""
    val result: (String?) -> Unit = { message ->
        working = false
        saveError = message
        if (message == null) onDismiss()
    }

    Dialog(onDismissRequest = { if (!working) onDismiss() }) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("단식 기록", style = MaterialTheme.typography.headlineSmall)
                PastTimeRow("시작", startAt, zone, !working) { editingTime = PastTimeField.START }
                PastTimeRow("끝", endAt, zone, !working) { editingTime = PastTimeField.END }
                Text(preview, style = MaterialTheme.typography.bodyMedium.tabularNumbers())
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(enabled = !working, role = Role.Button, onClickLabel = "목표 시간 수정") { editingGoal = true },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("목표 ${hours}시간", style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                (validation ?: saveError)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !working, onClick = { working = true; onDelete(result) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("삭제") }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = !working, onClick = onDismiss) { Text("취소") }
                    TextButton(enabled = !working && validation == null,
                        onClick = { working = true; onSave(startAt, endAt, hours, result) }) { Text("저장") }
                }
            }
        }
    }

    editingTime?.let { field ->
        StartTimeDialog(initial = if (field == PastTimeField.START) startAt else endAt, now = now, zone = zone,
            onDismiss = { editingTime = null },
            onSave = {
                if (field == PastTimeField.START) startAt = it else endAt = it
                saveError = null
                editingTime = null
            },
            title = if (field == PastTimeField.START) "단식 시작 시각" else "단식 끝 시각",
            allowedRange = Long.MIN_VALUE..now,
            preview = { picked ->
                val elapsed = if (field == PastTimeField.START) endAt - picked else picked - startAt
                "단식 ${Fasting.durationText(elapsed)}"
            },
            rangeMessage = { "지금보다 늦은 시각은 고를 수 없어요" })
    }
    if (editingGoal) FastingGoalDialog(hours, { editingGoal = false }, {
        hours = it
        saveError = null
        editingGoal = false
    })
}

@Composable
private fun PastTimeRow(label: String, at: Long, zone: ZoneId, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "$label 시각 수정", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Instant.ofEpochMilli(at).atZone(zone).format(pastTimeLabel), Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers())
        Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
