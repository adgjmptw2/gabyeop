package io.github.adgjmptw2.diet.data.sync

import io.github.adgjmptw2.diet.BuildConfig
import io.github.adgjmptw2.diet.data.auth.TokenStore
import io.github.adgjmptw2.diet.data.auth.Tokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(val status: Int, val code: String) : IOException("API $status: $code")
class LoginRequiredException : IOException("다시 로그인이 필요해요")

@Serializable data class GoogleAuthRequest(val idToken: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class ApiUser(val id: String, val email: String)
@Serializable data class GoogleAuthResponse(val accessToken: String, val refreshToken: String, val user: ApiUser)
@Serializable data class RefreshResponse(val accessToken: String, val refreshToken: String)
@Serializable private data class ApiError(val error: String = "unknown")

@Singleton
class ApiClient @Inject constructor(private val tokens: TokenStore) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        // Refresh tokens rotate. An invisible transport retry must not replay a consumed token.
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()
    private val refreshMutex = Mutex()

    suspend fun signIn(idToken: String): GoogleAuthResponse = json.decodeFromString(
        request("POST", "/v1/auth/google", json.encodeToString(GoogleAuthRequest(idToken)), false)
    )

    suspend fun logout(refreshToken: String) {
        request("POST", "/v1/auth/logout", json.encodeToString(RefreshRequest(refreshToken)), false)
    }

    suspend fun deleteAccount() { request("DELETE", "/v1/account") }

    suspend fun sync(body: SyncRequest): SyncResponse = json.decodeFromString(
        request("POST", "/v1/sync", json.encodeToString(body))
    )

    private suspend fun request(method: String, path: String, body: String? = null, authenticated: Boolean = true): String {
        val snapshot = if (authenticated) tokens.snapshot() else null
        if (authenticated && snapshot?.state?.canSync != true) throw LoginRequiredException()
        val access = snapshot?.tokens?.accessToken
        val first = execute(method, path, body, access)
        if (first.status != 401 || !authenticated) return first.successBody()
        val userId = requireNotNull(snapshot?.state?.userId)
        val replacement = refreshMutex.withLock {
            val current = tokens.snapshot()
            if (!current.state.canSync || current.state.userId != userId) throw LoginRequiredException()
            val currentTokens = current.tokens ?: throw LoginRequiredException()
            if (currentTokens.accessToken != access) currentTokens.accessToken else {
                val refreshed = try {
                    val reply = execute("POST", "/v1/auth/refresh",
                        json.encodeToString(RefreshRequest(currentTokens.refreshToken)), null)
                    json.decodeFromString<RefreshResponse>(reply.successBody())
                        .also { require(it.accessToken.isNotBlank() && it.refreshToken.isNotBlank()) }
                } catch (cancelled: CancellationException) {
                    // Cancellation may arrive after the server has consumed the refresh token.
                    withContext(NonCancellable) { tokens.requireLogin() }
                    throw cancelled
                } catch (failure: Exception) {
                    // An uncertain refresh response cannot safely be replayed after token rotation.
                    tokens.requireLogin()
                    throw LoginRequiredException()
                }
                // Persist a rotated token even if the request's caller is cancelled now.
                withContext(NonCancellable) {
                    tokens.updateTokens(Tokens(refreshed.accessToken, refreshed.refreshToken), userId)
                }
                refreshed.accessToken
            }
        }
        val second = execute(method, path, body, replacement)
        if (second.status == 401) {
            tokens.requireLogin()
            throw LoginRequiredException()
        }
        return second.successBody()
    }

    private suspend fun execute(method: String, path: String, body: String?, access: String?): Reply {
        val base = BuildConfig.API_BASE_URL.trim().trimEnd('/')
        require(base.isNotEmpty()) { "서버 주소가 없어요" }
        val request = Request.Builder().url(base + path)
            .method(method, body?.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { if (access != null) header("Authorization", "Bearer $access") }
            .build()
        val (status, text) = client.newCall(request).awaitBody()
        return Reply(status, text)
    }

    private inner class Reply(val status: Int, val body: String) {
        fun successBody(): String {
            if (status !in 200..299) {
                val error = runCatching { json.decodeFromString<ApiError>(body).error }.getOrDefault("unknown")
                throw ApiException(status, error)
            }
            return body
        }
    }
}

private suspend fun Call.awaitBody(): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            try {
                // Read and close the streaming body on OkHttp's I/O thread, before resuming UI callers.
                val reply = response.use { it.code to it.body.string() }
                continuation.resume(reply)
            } catch (failure: Exception) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
    })
}
