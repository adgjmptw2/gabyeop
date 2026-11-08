package io.github.adgjmptw2.diet.ui.welcome

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.auth.AuthRepository
import io.github.adgjmptw2.diet.data.auth.AuthState
import io.github.adgjmptw2.diet.data.auth.SignInResult
import io.github.adgjmptw2.diet.data.sync.SyncRepository
import io.github.adgjmptw2.diet.ui.theme.DecorationKind
import io.github.adgjmptw2.diet.ui.theme.ThemeDecoration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val sync: SyncRepository,
) : ViewModel() {
    val account = auth.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState())
    val googleAvailable = auth.googleSignInAvailable
    val syncMessage = sync.message
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun guest() = runAction {
        if (account.value.account == io.github.adgjmptw2.diet.data.auth.AccountType.GOOGLE) auth.signOut(true)
        else auth.continueAsGuest()
    }

    fun google(context: Context) = runAction {
        when (val result = auth.signInWithGoogle(context)) {
            is SignInResult.Failed -> message = result.message
            SignInResult.Cancelled -> Unit
            is SignInResult.Success -> if (!result.syncCompleted) message = "동기화 확인이 필요해요"
        }
    }

    fun retrySync() = runAction {
        if (!sync.sync()) message = "동기화가 남아 있어요. 다시 시도해 주세요"
    }

    private fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "연결하지 못했어요. 다시 시도해 주세요" }
            finally { busy = false }
        }
    }
}

@Composable
fun WelcomeScreen(vm: WelcomeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val account by vm.account.collectAsStateWithLifecycle()
    val syncMessage by vm.syncMessage.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        ThemeDecoration(kind = DecorationKind.SIT)
        Text("가볍게", style = MaterialTheme.typography.displaySmall)
        Text("체중과 식단을 기록하는 일상", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = vm::guest, enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp).heightIn(min = 56.dp)) {
            Text("게스트로 시작")
        }
        if (vm.googleAvailable) {
            OutlinedButton(onClick = { vm.google(context) }, enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Google로 시작") }
        }
        if (account.initialSyncPending && account.canSync) {
            OutlinedButton(onClick = vm::retrySync, enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("동기화 다시 시도") }
        }
        if (vm.busy || account.signingIn) CircularProgressIndicator()
        (syncMessage.takeIf { account.canSync } ?: vm.message)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
