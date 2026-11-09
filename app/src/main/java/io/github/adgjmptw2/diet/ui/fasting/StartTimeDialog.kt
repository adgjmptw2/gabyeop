package io.github.adgjmptw2.diet.ui.fasting

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.domain.Fasting
import io.github.adgjmptw2.diet.ui.common.DayLabel
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 날짜와 시·분 고르기. 기본 범위는 진행 중 단식과 같은 최근 7일이다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartTimeDialog(
    initial: Long, now: Long, zone: ZoneId, onDismiss: () -> Unit, onSave: (Long) -> Unit,
    title: String = "단식 시작 시각",
    allowedRange: LongRange = (now - Fasting.MAX_DURATION_MS)..now,
    preview: (Long) -> String = { "단식 ${Fasting.durationText(now - it)} 지난 것으로 기록해요" },
    rangeMessage: (Long) -> String = { if (it > now) "지금보다 늦은 시각은 고를 수 없어요" else "7일 안으로 골라 주세요" },
) {
    val start = Instant.ofEpochMilli(initial).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val firstDate = Instant.ofEpochMilli(allowedRange.first).atZone(zone).toLocalDate()
    val lastDate = Instant.ofEpochMilli(allowedRange.last).atZone(zone).toLocalDate()
    var dateEpoch by rememberSaveable { mutableLongStateOf(start.toLocalDate().toEpochDay()) }
    val date = LocalDate.ofEpochDay(dateEpoch)
    val time = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
    val picked = date.atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli()
    val valid = picked in allowedRange
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { dateEpoch-- }, enabled = date > firstDate) { Text("◀ 전날") }
                Text(if (date == today) "오늘" else date.format(DayLabel),
                    style = MaterialTheme.typography.titleSmall.tabularNumbers())
                TextButton(onClick = { dateEpoch++ }, enabled = date < lastDate) { Text("다음날 ▶") }
            }
            TimeInput(time)
            Text(if (valid) preview(picked) else rangeMessage(picked),
                style = MaterialTheme.typography.bodySmall,
                color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(picked) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
