package io.github.adgjmptw2.diet.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.BuildConfig
import io.github.adgjmptw2.diet.data.auth.AccountType
import io.github.adgjmptw2.diet.ui.theme.AppTheme
import io.github.adgjmptw2.diet.ui.theme.LocalAppTheme
import io.github.adgjmptw2.diet.ui.theme.ThemePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.util.Locale
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.meals.liters

private enum class SettingsPage { MAIN, THEME, PRIVACY, LICENSES, FONT }
private enum class AccountDialog { LINK, LOGOUT, DELETE, CALORIE, WATER }

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onEditProfile: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    var dialog by rememberSaveable { mutableStateOf<AccountDialog?>(null) }
    var keepLocal by rememberSaveable { mutableStateOf(true) }
    var browserError by rememberSaveable { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setReminderEnabled(true) else vm.notificationDenied()
    }

    BackHandler(enabled = !state.busy) {
        if (page == SettingsPage.FONT) page = SettingsPage.LICENSES
        else if (page != SettingsPage.MAIN) page = SettingsPage.MAIN
        else onClose()
    }
    // Keep account removal and export actions alive until their result has been handled.
    BackHandler(enabled = state.busy) { }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                SettingsEvent.Close -> onClose()
                is SettingsEvent.Share -> try {
                    context.startActivity(Intent.createChooser(event.intent, "기록 내보내기"))
                } catch (_: Exception) { vm.shareFailed() }
            }
        }
    }

    val title = when (page) {
        SettingsPage.MAIN -> "설정"
        SettingsPage.THEME -> "테마"
        SettingsPage.PRIVACY -> "개인정보처리방침"
        SettingsPage.LICENSES -> "오픈소스·데이터 출처"
        SettingsPage.FONT -> "Pretendard 글꼴 라이선스"
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(enabled = !state.busy, onClick = {
                if (page == SettingsPage.FONT) page = SettingsPage.LICENSES
                else if (page != SettingsPage.MAIN) page = SettingsPage.MAIN
                else onClose()
            }) { Text("닫기") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        browserError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        when (page) {
            SettingsPage.MAIN -> {
                SettingsGroup("계정") {
                    val google = state.account.account == AccountType.GOOGLE
                    SettingsRow(if (google) "Google 계정" else "게스트",
                        if (google) state.account.email.orEmpty() else "기록은 이 기기에만 있고 어디로도 보내지 않아요")
                    if (state.account.needsReauthentication) {
                        Text("다시 로그인이 필요해요", color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp))
                    }
                    if (vm.googleAvailable && (!google || state.account.needsReauthentication)) {
                        SettingsRow(if (google) "Google로 다시 로그인" else "Google 계정 연결",
                            "연결하면 이 기기의 기록을 서버에 저장해요", !state.busy) { dialog = AccountDialog.LINK }
                    }
                    if (google) {
                        SettingsRow("로그아웃", enabled = !state.busy) { keepLocal = true; dialog = AccountDialog.LOGOUT }
                        SettingsRow("계정 삭제", enabled = !state.busy, destructive = true) { dialog = AccountDialog.DELETE }
                    }
                }
                SettingsGroup("내 기록") {
                    SettingsRow("내 정보 수정", enabled = !state.busy, onClick = onEditProfile)
                    SettingsRow("하루 목표 칼로리",
                        state.records.calorieTarget?.let { "${it.comma()} kcal" }
                            ?: "권장 ${state.records.recommendedKcal.comma()} kcal",
                        !state.busy && state.records.recommendedKcal > 0) { dialog = AccountDialog.CALORIE }
                    SettingsRow("물", "한 잔 ${state.records.water.glassMl} ml · " +
                        (state.records.water.targetMl?.let { "목표 ${liters(it)} L" }
                            ?: "자동 ${liters(state.records.automaticWaterMl)} L"), !state.busy) { dialog = AccountDialog.WATER }
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("기록 알림", style = MaterialTheme.typography.bodyLarge)
                            Text("오늘 체중을 기록하지 않았을 때 알려요", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.reminder.enabled, enabled = !state.busy, onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else vm.setReminderEnabled(enabled)
                        })
                    }
                    SettingsRow("알림 시간", String.format(Locale.KOREA, "%02d:%02d", state.reminder.hour, state.reminder.minute),
                        enabled = !state.busy) {
                        TimePickerDialog(context, { _, hour, minute -> vm.setReminderTime(hour, minute) },
                            state.reminder.hour, state.reminder.minute, DateFormat.is24HourFormat(context)).show()
                    }
                    SettingsRow("데이터 내보내기", "체중·식단·메모·단식 CSV", !state.busy, onClick = vm::export)
                }
                SettingsGroup("화면") {
                    SettingsRow("테마", LocalAppTheme.current.label, enabled = !state.busy) { page = SettingsPage.THEME }
                }
                SettingsGroup("앱 정보") {
                    SettingsRow("개인정보처리방침", enabled = !state.busy) {
                        browserError = null
                        if (SettingsViewModel.privacyUrl.isBlank()) page = SettingsPage.PRIVACY
                        else try { context.startActivity(Intent(Intent.ACTION_VIEW, SettingsViewModel.privacyUrl.toUri())) }
                        catch (_: Exception) { browserError = "브라우저를 열지 못했어요" }
                    }
                    SettingsRow("오픈소스·데이터 출처", enabled = !state.busy) { page = SettingsPage.LICENSES }
                    SettingsRow("앱 버전", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                }
            }
            SettingsPage.THEME -> ThemePicker(
                selected = LocalAppTheme.current,
                onSelect = onThemeSelected,
                enabled = !state.busy,
            )
            SettingsPage.PRIVACY -> Text(LOCAL_PRIVACY, style = MaterialTheme.typography.bodyLarge)
            SettingsPage.LICENSES -> {
                Text(LIBRARY_LICENSES, style = MaterialTheme.typography.bodyMedium)
                SettingsGroup("글꼴") {
                    SettingsRow("Pretendard", "SIL Open Font License 1.1") { page = SettingsPage.FONT }
                }
                SettingsGroup("데이터 출처") {
                    Text("식품의약품안전처 전국통합식품영양성분정보(음식)표준데이터(공공데이터포털)",
                        modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            SettingsPage.FONT -> {
                val license by produceState("불러오는 중", context) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { context.assets.open("licenses/font-OFL.txt").bufferedReader().use { it.readText() } }
                            .getOrDefault("라이선스를 불러오지 못했어요")
                    }
                }
                Text(license, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    when (dialog) {
        AccountDialog.CALORIE -> CalorieTargetDialog(state.records, onDismiss = { dialog = null },
            onSave = { vm.setCalorieTarget(it); dialog = null })
        AccountDialog.WATER -> WaterSettingsDialog(state.records, onDismiss = { dialog = null },
            onSave = { vm.setWater(it); dialog = null })
        AccountDialog.LINK -> AlertDialog(onDismissRequest = { dialog = null },
            title = { Text("Google 계정 연결") },
            text = { Text("연결하면 이 기기의 체중과 식단 기록을 서버에 저장해요. 사진은 이 기기에 남아요") },
            confirmButton = { TextButton(onClick = { dialog = null; vm.signIn(context) }) { Text("연결") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("취소") } })
        AccountDialog.LOGOUT -> AlertDialog(onDismissRequest = { dialog = null },
            title = { Text("로그아웃") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("이 기기의 기록을 남길지 선택할 수 있어요. 서버 기록은 유지돼요")
                    Row(Modifier.fillMaxWidth().clickable { keepLocal = !keepLocal }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepLocal, onCheckedChange = { keepLocal = it })
                        Text("이 기기에 기록 남기기")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null; vm.signOut(keepLocal) }) { Text("로그아웃") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("취소") } })
        AccountDialog.DELETE -> AlertDialog(onDismissRequest = { dialog = null },
            title = { Text("계정 삭제") },
            text = { Text("서버와 이 기기의 기록이 모두 지워지고 되돌릴 수 없어요") },
            confirmButton = { TextButton(onClick = { dialog = null; vm.deleteAccount() }) {
                Text("삭제", color = MaterialTheme.colorScheme.error)
            } }, dismissButton = { TextButton(onClick = { dialog = null }) { Text("취소") } })
        null -> Unit
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleSmall)
        Card(shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String, description: String? = null, enabled: Boolean = true,
    destructive: Boolean = false, onClick: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
        .heightIn(min = 56.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

private val LOCAL_PRIVACY = """
    가볍게 개인정보처리방침

    게스트의 체중·체지방률, 식단, 하루 메모, 물 섭취량, 단식 기록, 프로필과 사진은 이 기기에 저장돼요. Google 로그인과 서버 주소가 설정되지 않은 앱은 기록을 서버로 보내지 않아요.

    Google 계정을 연결하면 계정 식별자와 이메일을 로그인에 쓰고, 프로필(하루 목표 칼로리 포함)·체중·체지방률·식단·하루 메모·물 섭취량, 단식 기록을 서버에 저장해 동기화해요. 사진과 기기 안 사진 경로는 전송하지 않아요. 로그인 토큰은 Android Keystore 키로 암호화해 저장하고 백업에서 제외해요.

    기기 기록은 Android 백업 설정에 따라 백업될 수 있어요. 내보낸 CSV는 선택한 공유 앱으로 전달돼요.

    설정에서 계정을 삭제하면 서버와 이 기기의 기록을 지워요. 앱을 삭제하면 기기 안 기록이 지워지며, Android 백업에 남은 사본은 기기의 백업 설정에서 관리할 수 있어요.
""".trimIndent()

private val LIBRARY_LICENSES = """
    AndroidX Core, Activity, Lifecycle, Compose, Room — Apache License 2.0
    https://source.android.com/docs/setup/about/licenses

    AndroidX Credentials 1.6.0, WorkManager 2.12.0, Hilt 1.4.0, DataStore 1.3.0 — Apache License 2.0
    https://github.com/androidx/androidx

    Dagger / Hilt 2.60.1 — Apache License 2.0
    https://github.com/google/dagger

    Google Identity googleid 1.2.1 — Google APIs Terms of Service
    https://developers.google.com/terms

    OkHttp 5.5.1 / Okio — Apache License 2.0
    https://github.com/square/okhttp
    https://github.com/square/okio

    Kotlin, kotlinx.coroutines, kotlinx.serialization 1.12.0 — Apache License 2.0
    https://github.com/JetBrains/kotlin
    https://github.com/Kotlin/kotlinx.coroutines
    https://github.com/Kotlin/kotlinx.serialization
""".trimIndent()
