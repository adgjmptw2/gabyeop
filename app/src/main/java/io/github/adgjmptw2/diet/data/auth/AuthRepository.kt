package io.github.adgjmptw2.diet.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.BuildConfig
import io.github.adgjmptw2.diet.data.reminder.ReminderScheduler
import io.github.adgjmptw2.diet.data.sync.ApiClient
import io.github.adgjmptw2.diet.data.sync.LoginRequiredException
import io.github.adgjmptw2.diet.data.sync.SyncRepository
import io.github.adgjmptw2.diet.data.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SignInResult {
    data class Success(val syncCompleted: Boolean) : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val message: String) : SignInResult
}

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val tokens: TokenStore,
    private val api: ApiClient,
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
    private val reminders: ReminderScheduler,
    private val cleaner: DeviceDataCleaner,
    private val session: SessionLock,
) {
    private val credentials = CredentialManager.create(context)
    private val signingIn = MutableStateFlow(false)
    private val actions = Mutex()
    val state: Flow<AuthState> = combine(tokens.state, signingIn) { auth, busy -> auth.copy(signingIn = busy) }
    val googleSignInAvailable: Boolean get() = GoogleSignInConfiguration.enabled

    suspend fun signInWithGoogle(activity: Context): SignInResult = actions.withLock {
        if (!googleSignInAvailable) return@withLock SignInResult.Failed("Google 로그인 설정이 없어요")
        signingIn.value = true
        try {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
            val response = credentials.getCredential(activity, GetCredentialRequest.Builder()
                .addCredentialOption(option).build())
            val credential = response.credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return@withLock SignInResult.Failed("Google 계정을 확인하지 못했어요")
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            session.mutex.withLock {
                val result = api.signIn(idToken)
                require(result.user.id.isNotBlank() && result.accessToken.isNotBlank() && result.refreshToken.isNotBlank())
                val previous = tokens.snapshot().state
                if (previous.account == AccountType.GOOGLE && previous.userId != null && previous.userId != result.user.id) {
                    // 이 기기의 기록이 다른 사람 계정에 섞이지 않게, 원래 계정으로만 다시 로그인한다.
                    try { api.logout(result.refreshToken) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { }
                    return@withLock SignInResult.Failed("처음 쓰던 Google 계정(${previous.email.orEmpty()})으로 로그인해 주세요")
                }
                tokens.setGoogle(result.user.id, result.user.email, Tokens(result.accessToken, result.refreshToken))
                val complete = try {
                    sync.syncLocked()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { false }
                if (complete) scheduler.afterSync() else scheduler.onAppOpened()
                SignInResult.Success(complete)
            }
        } catch (_: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: GetCredentialException) {
            SignInResult.Failed("Google 로그인에 실패했어요")
        } catch (_: IOException) {
            SignInResult.Failed("서버에 연결하지 못했어요")
        } catch (_: Exception) {
            SignInResult.Failed("Google 계정을 확인하지 못했어요")
        } finally {
            signingIn.value = false
        }
    }

    suspend fun continueAsGuest() = actions.withLock {
        session.mutex.withLock { tokens.setGuest() }
    }

    suspend fun signOut(keepLocal: Boolean) = actions.withLock {
        // Wait for an active sync before revoking tokens or erasing rows.
        session.mutex.withLock {
            val refresh = tokens.snapshot().tokens?.refreshToken
            if (refresh != null) {
                try { api.logout(refresh) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: IOException) { /* Offline sign-out still removes this device's tokens. */ }
            }
            withContext(NonCancellable) {
                tokens.setGuest()
                scheduler.cancel()
                if (!keepLocal) {
                    reminders.cancel()
                    cleaner.clear()
                    tokens.setGuest()
                }
            }
            clearCredentialState()
        }
    }

    suspend fun deleteAccount() = actions.withLock {
        session.mutex.withLock {
            if (!tokens.snapshot().state.canSync) throw LoginRequiredException()
            api.deleteAccount()
            // Server deletion must succeed before any device data is removed.
            withContext(NonCancellable) {
                tokens.setGuest()
                scheduler.cancel()
                reminders.cancel()
                cleaner.clear()
            }
            clearCredentialState()
        }
    }

    private suspend fun clearCredentialState() {
        try { credentials.clearCredentialState(ClearCredentialStateRequest()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Local tokens are already gone. */ }
    }
}
