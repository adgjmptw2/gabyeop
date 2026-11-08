package io.github.adgjmptw2.diet.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(
    name = "auth",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

enum class AccountType { GUEST, GOOGLE }

internal object GoogleSignInConfiguration {
    val enabled: Boolean get() = BuildConfig.API_BASE_URL.isNotBlank() && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
}

data class AuthState(
    val account: AccountType? = null,
    val email: String? = null,
    val userId: String? = null,
    val needsReauthentication: Boolean = false,
    val signingIn: Boolean = false,
    val initialSyncPending: Boolean = false,
) {
    val canSync: Boolean get() = account == AccountType.GOOGLE && !needsReauthentication
}

data class Tokens(val accessToken: String, val refreshToken: String)
data class AuthSnapshot(
    val state: AuthState, val tokens: Tokens?, val cursor: Long, val lastPushAt: Long,
    val keptOwnerId: String? = null, val keptOwnerEmail: String? = null,
    val syncProtocol: Int = 1,
) {
    internal fun ownerConflict(userId: String): String? = when {
        keptOwnerId != null && keptOwnerId != userId ->
            "이 기기 기록은 ${keptOwnerEmail.orEmpty()} 계정 것이에요. 그 계정으로 로그인하거나 설정에서 기록을 지운 뒤 연결하세요"
        state.account == AccountType.GOOGLE && state.userId != null && state.userId != userId ->
            "처음 쓰던 Google 계정(${state.email.orEmpty()})으로 로그인해 주세요"
        else -> null
    }
}
data class ReminderSettings(val enabled: Boolean = false, val hour: Int = 20, val minute: Int = 0)
data class PushProgress(val startedAt: Long, val acknowledged: Map<String, Long> = emptyMap())

/** Shared by account changes and complete sync runs; no response can cross an account boundary. */
@Singleton
class SessionLock @Inject constructor() {
    val mutex = Mutex()
}

@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.authDataStore

    val state: Flow<AuthState> = store.data.map { read(it).state }.flowOn(Dispatchers.IO).distinctUntilChanged()
    val reminder: Flow<ReminderSettings> = store.data.map {
        ReminderSettings(it[REMINDER_ENABLED] ?: false, it[REMINDER_HOUR] ?: 20, it[REMINDER_MINUTE] ?: 0)
    }.distinctUntilChanged()

    suspend fun snapshot(): AuthSnapshot = withContext(Dispatchers.IO) { read(store.data.first()) }

    suspend fun setGoogle(userId: String, email: String, tokens: Tokens) = withContext(Dispatchers.IO) {
        val access = encrypt(tokens.accessToken)
        val refresh = encrypt(tokens.refreshToken)
        store.edit {
            it[ACCOUNT] = AccountType.GOOGLE.name
            it[USER_ID] = userId
            it[EMAIL] = email
            it[ACCESS] = access
            it[REFRESH] = refresh
            it[NEEDS_LOGIN] = false
            // A new link uploads the device's existing records, including tombstones.
            it[CURSOR] = 0
            it[LAST_PUSH] = 0
            it[SYNC_PROTOCOL] = 2
            it.remove(PROTOCOL_RESET)
            it.remove(KEPT_OWNER_ID)
            it.remove(KEPT_OWNER_EMAIL)
            it[INITIAL_SYNC_PENDING] = true
            it.remove(PUSH_STARTED)
            it.remove(PUSH_ACKNOWLEDGED)
        }
    }

    suspend fun updateTokens(tokens: Tokens, expectedUserId: String) = withContext(Dispatchers.IO) {
        val access = encrypt(tokens.accessToken)
        val refresh = encrypt(tokens.refreshToken)
        store.edit {
            check(it[USER_ID] == expectedUserId && it[ACCOUNT] == AccountType.GOOGLE.name)
            it[ACCESS] = access
            it[REFRESH] = refresh
            it[NEEDS_LOGIN] = false
        }
    }

    suspend fun requireLogin() {
        store.edit {
            if (it[ACCOUNT] == AccountType.GOOGLE.name) {
                it[NEEDS_LOGIN] = true
                it.remove(ACCESS)
                it.remove(REFRESH)
            }
        }
    }

    suspend fun setGuest(keepLocalOwner: Boolean = false) {
        store.edit {
            if (keepLocalOwner && it[ACCOUNT] == AccountType.GOOGLE.name) {
                it[USER_ID]?.let { owner -> it[KEPT_OWNER_ID] = owner }
                it[EMAIL]?.let { email -> it[KEPT_OWNER_EMAIL] = email }
            }
            it[ACCOUNT] = AccountType.GUEST.name
            listOf(USER_ID, EMAIL, ACCESS, REFRESH).forEach { key -> it.remove(key) }
            it.remove(NEEDS_LOGIN)
            it[CURSOR] = 0
            it[LAST_PUSH] = 0
            it.remove(INITIAL_SYNC_PENDING)
            it.remove(PUSH_STARTED)
            it.remove(PUSH_ACKNOWLEDGED)
        }
    }

    /** Persist the reset separately so a paged or interrupted upgrade resumes its pull. */
    suspend fun prepareProtocolUpgrade() {
        store.edit {
            if ((it[SYNC_PROTOCOL] ?: 1) < 2 && it[PROTOCOL_RESET] != true) {
                it[CURSOR] = 0
                it[PROTOCOL_RESET] = true
                it.remove(PUSH_STARTED)
                it.remove(PUSH_ACKNOWLEDGED)
            }
        }
    }

    suspend fun pushProgress(): PushProgress? = withContext(Dispatchers.IO) {
        val p = store.data.first()
        p[PUSH_STARTED]?.let { started ->
            val acknowledged = p[PUSH_ACKNOWLEDGED]?.let { Json.decodeFromString<Map<String, Long>>(it) }.orEmpty()
            PushProgress(started, acknowledged)
        }
    }

    suspend fun recordSyncPage(cursor: Long, progress: PushProgress) = withContext(Dispatchers.IO) {
        val acknowledged = Json.encodeToString(progress.acknowledged)
        store.edit {
            it[CURSOR] = cursor
            it[PUSH_STARTED] = progress.startedAt
            it[PUSH_ACKNOWLEDGED] = acknowledged
        }
    }

    suspend fun finishSync(time: Long) {
        store.edit {
            it[LAST_PUSH] = time
            it[INITIAL_SYNC_PENDING] = false
            it[SYNC_PROTOCOL] = 2
            it.remove(PROTOCOL_RESET)
            it.remove(PUSH_STARTED)
            it.remove(PUSH_ACKNOWLEDGED)
        }
    }

    suspend fun setReminder(settings: ReminderSettings) {
        store.edit {
            it[REMINDER_ENABLED] = settings.enabled
            it[REMINDER_HOUR] = settings.hour.coerceIn(0, 23)
            it[REMINDER_MINUTE] = settings.minute.coerceIn(0, 59)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        store.edit { it.clear() }
        keyStore().let { if (it.containsAlias(KEY_ALIAS)) it.deleteEntry(KEY_ALIAS) }
    }

    private fun read(p: Preferences): AuthSnapshot {
        val savedAccount = p[ACCOUNT]?.let { runCatching { AccountType.valueOf(it) }.getOrNull() }
        val account = if (savedAccount == AccountType.GOOGLE && !GoogleSignInConfiguration.enabled) AccountType.GUEST else savedAccount
        val tokens = if (account == AccountType.GOOGLE) runCatching {
            Tokens(decrypt(requireNotNull(p[ACCESS])), decrypt(requireNotNull(p[REFRESH])))
                .also { require(it.accessToken.isNotBlank() && it.refreshToken.isNotBlank()) }
        }.getOrNull() else null
        return AuthSnapshot(
            AuthState(account, p[EMAIL].takeIf { account == AccountType.GOOGLE },
                p[USER_ID].takeIf { account == AccountType.GOOGLE }, account == AccountType.GOOGLE &&
                (p[NEEDS_LOGIN] == true || tokens == null || p[USER_ID].isNullOrBlank()),
                initialSyncPending = account == AccountType.GOOGLE && p[INITIAL_SYNC_PENDING] == true),
            tokens, p[CURSOR] ?: 0, p[LAST_PUSH] ?: 0,
            p[KEPT_OWNER_ID], p[KEPT_OWNER_EMAIL], p[SYNC_PROTOCOL] ?: 1,
        )
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val existingKey = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: error("Missing token key")
        cipher.init(Cipher.DECRYPT_MODE, existingKey, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
    }

    private companion object {
        const val KEY_ALIAS = "diet_tokens"
        val ACCOUNT = stringPreferencesKey("account")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val KEPT_OWNER_ID = stringPreferencesKey("kept_owner_id")
        val KEPT_OWNER_EMAIL = stringPreferencesKey("kept_owner_email")
        val ACCESS = stringPreferencesKey("access_encrypted")
        val REFRESH = stringPreferencesKey("refresh_encrypted")
        val NEEDS_LOGIN = booleanPreferencesKey("needs_login")
        val CURSOR = longPreferencesKey("sync_cursor")
        val LAST_PUSH = longPreferencesKey("last_push_at")
        val SYNC_PROTOCOL = intPreferencesKey("sync_protocol")
        val PROTOCOL_RESET = booleanPreferencesKey("sync_protocol_reset")
        val INITIAL_SYNC_PENDING = booleanPreferencesKey("initial_sync_pending")
        val PUSH_STARTED = longPreferencesKey("push_started_at")
        val PUSH_ACKNOWLEDGED = stringPreferencesKey("push_acknowledged")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }
}
