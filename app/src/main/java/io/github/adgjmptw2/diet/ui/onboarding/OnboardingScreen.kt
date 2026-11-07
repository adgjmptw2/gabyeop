package io.github.adgjmptw2.diet.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.adgjmptw2.diet.domain.ActivityLevel
import io.github.adgjmptw2.diet.domain.Field
import io.github.adgjmptw2.diet.domain.Sex
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers

@Composable
fun OnboardingScreen(isEdit: Boolean, onClose: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    LaunchedEffect(isEdit) { vm.load() }
    val input = vm.input
    val errors = vm.errors

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (isEdit) "내 정보 수정" else "시작하기", style = MaterialTheme.typography.headlineLarge)
            Text(
                "하루 목표 칼로리 계산에 써요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("성별")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Sex.entries.forEachIndexed { i, sex ->
                            SegmentedButton(
                                selected = input.sex == sex,
                                onClick = { vm.update(input.copy(sex = sex)) },
                                shape = SegmentedButtonDefaults.itemShape(i, Sex.entries.size),
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) { Text(sex.label) }
                        }
                    }
                    ErrorText(errors[Field.SEX])
                }
                NumberField("출생 연도", input.birthYear, null, errors[Field.BIRTH_YEAR], decimal = false) {
                    vm.update(input.copy(birthYear = it))
                }
                NumberField("키", input.heightCm, "cm", errors[Field.HEIGHT]) {
                    vm.update(input.copy(heightCm = it))
                }
            }
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NumberField("시작 체중", input.startWeightKg, "kg", errors[Field.START_WEIGHT]) {
                    vm.update(input.copy(startWeightKg = it))
                }
                NumberField("목표 체중", input.targetWeightKg, "kg", errors[Field.TARGET_WEIGHT]) {
                    vm.update(input.copy(targetWeightKg = it))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("활동량")
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    ActivityLevel.entries.forEachIndexed { index, level ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = input.activity == level,
                                    onClick = { vm.update(input.copy(activity = level)) },
                                    role = Role.RadioButton,
                                )
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = input.activity == level, onClick = null)
                            Text(
                                level.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).padding(start = 16.dp),
                            )
                        }
                        if (index < ActivityLevel.entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
            ErrorText(errors[Field.ACTIVITY])
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.submit(onClose) },
                enabled = !vm.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(if (isEdit) "저장" else "시작하기") }
            if (isEdit) {
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("취소")
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
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
        textStyle = MaterialTheme.typography.bodyLarge.tabularNumbers(),
        shape = MaterialTheme.shapes.small,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
