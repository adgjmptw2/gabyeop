package io.github.adgjmptw2.diet.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.adgjmptw2.diet.domain.ActivityLevel
import io.github.adgjmptw2.diet.domain.Field
import io.github.adgjmptw2.diet.domain.Sex

@Composable
fun OnboardingScreen(isEdit: Boolean, onClose: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    LaunchedEffect(isEdit) { vm.load() }
    val input = vm.input
    val errors = vm.errors

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (isEdit) "내 정보 수정" else "시작하기",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "하루 목표 칼로리 계산에 써요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        Label("성별")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Sex.entries.forEachIndexed { i, sex ->
                SegmentedButton(
                    selected = input.sex == sex,
                    onClick = { vm.update(input.copy(sex = sex)) },
                    shape = SegmentedButtonDefaults.itemShape(i, Sex.entries.size),
                ) { Text(sex.label) }
            }
        }
        ErrorText(errors[Field.SEX])

        NumberField("출생 연도", input.birthYear, null, errors[Field.BIRTH_YEAR], decimal = false) {
            vm.update(input.copy(birthYear = it))
        }
        NumberField("키", input.heightCm, "cm", errors[Field.HEIGHT]) {
            vm.update(input.copy(heightCm = it))
        }
        NumberField("시작 체중", input.startWeightKg, "kg", errors[Field.START_WEIGHT]) {
            vm.update(input.copy(startWeightKg = it))
        }
        NumberField("목표 체중", input.targetWeightKg, "kg", errors[Field.TARGET_WEIGHT]) {
            vm.update(input.copy(targetWeightKg = it))
        }

        Label("활동량")
        Column(Modifier.selectableGroup()) {
            ActivityLevel.entries.forEach { level ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = input.activity == level,
                            onClick = { vm.update(input.copy(activity = level)) },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = input.activity == level, onClick = null)
                    Text(level.label, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        ErrorText(errors[Field.ACTIVITY])

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.submit(onClose) },
            enabled = !vm.saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (isEdit) "저장" else "시작하기") }
        if (isEdit) {
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("취소") }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    suffix: String?,
    error: String?,
    decimal: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
