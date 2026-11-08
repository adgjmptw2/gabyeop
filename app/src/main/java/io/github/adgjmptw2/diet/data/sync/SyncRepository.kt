package io.github.adgjmptw2.diet.data.sync

import androidx.room.withTransaction
import io.github.adgjmptw2.diet.data.DietDatabase
import io.github.adgjmptw2.diet.data.auth.SessionLock
import io.github.adgjmptw2.diet.data.auth.PushProgress
import io.github.adgjmptw2.diet.data.auth.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val db: DietDatabase,
    private val api: ApiClient,
    private val tokens: TokenStore,
    private val session: SessionLock,
    private val clock: Clock,
) {
    private val syncing = MutableStateFlow(false)
    val isSyncing = syncing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val appliedVersions = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Remote merges do not schedule an endless series of echo uploads. */
    suspend fun hasPendingLocalChanges(): Boolean {
        val since = tokens.snapshot().lastPushAt
        return db.withTransaction {
            db.profileDao().changedSince(since)?.let { appliedVersions["profile"] != it.updatedAt } == true ||
                db.weightDao().changedSince(since).any { appliedVersions["w:${it.id}"] != it.updatedAt } ||
                db.mealDao().changedSince(since).any { appliedVersions["m:${it.id}"] != it.updatedAt } ||
                db.dayNoteDao().changedSince(since).any { appliedVersions["n:${it.epochDay}"] != it.updatedAt } ||
                db.fastDao().changedSince(since).any { appliedVersions["f:${it.id}"] != it.updatedAt }
        }
    }

    suspend fun sync(): Boolean = session.mutex.withLock { syncLocked() }

    /** Caller must own SessionLock (also used for the initial Google login sync). */
    internal suspend fun syncLocked(): Boolean = withContext(Dispatchers.IO) {
        if (!tokens.snapshot().state.canSync) return@withContext true
        tokens.prepareProtocolUpgrade()
        val auth = tokens.snapshot()
        val restoreNewFields = auth.syncProtocol < 2 || auth.state.initialSyncPending
        syncing.value = true
        try {
            val progress = tokens.pushProgress() ?: PushProgress(clock.millis())
            val pushStartedAt = progress.startedAt
            val acknowledged = progress.acknowledged.toMutableMap()
            val outgoing = db.withTransaction {
                Outgoing(
                    db.profileDao().changedSince(auth.lastPushAt)
                    ?.takeIf { acknowledged["profile"] != it.updatedAt }?.wire(),
                    db.weightDao().changedSince(auth.lastPushAt)
                        .filter { acknowledged["w:${it.id}"] != it.updatedAt }.map { it.wire() },
                    db.mealDao().changedSince(auth.lastPushAt)
                        .filter { acknowledged["m:${it.id}"] != it.updatedAt }.map { it.wire() },
                    db.dayNoteDao().changedSince(auth.lastPushAt)
                        .filter { acknowledged["n:${it.epochDay}"] != it.updatedAt }.map { it.wire() },
                    db.fastDao().changedSince(auth.lastPushAt)
                        .filter { acknowledged["f:${it.id}"] != it.updatedAt }.map { it.wire() },
                )
            }
            // Persist the start time before the first request; later workers resume after the 10-page cap.
            tokens.recordSyncPage(auth.cursor, progress)
            var profile = outgoing.profile
            var weightOffset = 0
            var mealOffset = 0
            var noteOffset = 0
            var fastOffset = 0
            var cursor = auth.cursor
            repeat(10) {
                var capacity = 2_000
                var request: SyncRequest
                while (true) {
                    val weights = outgoing.weights.drop(weightOffset).take(capacity - if (profile != null) 1 else 0)
                    val mealCapacity = capacity - weights.size - if (profile != null) 1 else 0
                    val meals = outgoing.meals.drop(mealOffset).take(mealCapacity)
                    val noteCapacity = capacity - weights.size - meals.size - if (profile != null) 1 else 0
                    val notes = outgoing.notes.drop(noteOffset).take(noteCapacity)
                    val fastCapacity = noteCapacity - notes.size
                    val fasts = outgoing.fasts.drop(fastOffset).take(fastCapacity)
                    request = SyncRequest(cursor, profile, weights, meals, notes, fasts)
                    // Leave room below the server's 1 MiB body limit, including UTF-8 names.
                    if (api.json.encodeToString(request).toByteArray(Charsets.UTF_8).size <= 900_000) break
                    if (capacity == 1) throw IOException("동기화 기록이 너무 커요")
                    capacity = (capacity / 2).coerceAtLeast(1)
                }
                val response = api.sync(request)
                response.requireCurrentProtocol()
                _message.value = null
                if (response.cursor < cursor || (response.more && response.cursor == cursor)) {
                    throw IOException("잘못된 동기화 커서")
                }
                db.withTransaction {
                    response.profile?.entity()?.let { incoming ->
                        MergeRules.profile(db.profileDao().current(), incoming, restoreNewFields)?.let {
                            db.profileDao().upsert(it)
                            appliedVersions["profile"] = it.updatedAt
                        }
                    }
                    response.weights.sortedBy { it.updatedAt }.forEach { row ->
                        val incoming = row.entity()
                        val change = MergeRules.weight(db.weightDao().byId(row.id),
                            db.weightDao().findByDay(row.epochDay), incoming, restoreNewFields)
                        if (change != null) {
                            change.deleteId?.let { db.weightDao().deleteById(it) }
                            db.weightDao().upsert(change.upsert)
                            appliedVersions["w:${row.id}"] = row.updatedAt
                        }
                    }
                    response.meals.forEach { row ->
                        MergeRules.meal(db.mealDao().byId(row.id), row.entity())?.let {
                            db.mealDao().upsert(it)
                            appliedVersions["m:${row.id}"] = row.updatedAt
                        }
                    }
                    response.notes.forEach { row ->
                        MergeRules.note(db.dayNoteDao().byDay(row.epochDay), row.entity(), restoreNewFields)?.let {
                            db.dayNoteDao().upsert(it)
                            appliedVersions["n:${row.epochDay}"] = row.updatedAt
                        }
                    }
                    response.fasts.forEach { row ->
                        MergeRules.fast(db.fastDao().byId(row.id), row.entity())?.let {
                            db.fastDao().upsert(it)
                            appliedVersions["f:${row.id}"] = row.updatedAt
                        }
                    }
                    // Simultaneous starts on two devices converge on one active fast.
                    val active = db.fastDao().active()
                    if (active != null) {
                        db.fastDao().activeRows().filter {
                            it.id != active.id
                        }.forEach {
                            db.fastDao().upsert(it.copy(deleted = true,
                                updatedAt = maxOf(clock.millis(), active.updatedAt + 1, it.updatedAt + 1)))
                        }
                    }
                }
                // A crash before this write only causes an idempotent replay of the applied page.
                cursor = response.cursor
                request.profile?.let { acknowledged["profile"] = it.updatedAt }
                request.weights.forEach { acknowledged["w:${it.id}"] = it.updatedAt }
                request.meals.forEach { acknowledged["m:${it.id}"] = it.updatedAt }
                request.notes.forEach { acknowledged["n:${it.epochDay}"] = it.updatedAt }
                request.fasts.forEach { acknowledged["f:${it.id}"] = it.updatedAt }
                tokens.recordSyncPage(cursor, PushProgress(pushStartedAt, acknowledged))
                profile = null
                weightOffset += request.weights.size
                mealOffset += request.meals.size
                noteOffset += request.notes.size
                fastOffset += request.fasts.size
                val uploaded = weightOffset == outgoing.weights.size && mealOffset == outgoing.meals.size &&
                    noteOffset == outgoing.notes.size && fastOffset == outgoing.fasts.size
                if (uploaded && !response.more) {
                    tokens.finishSync(pushStartedAt)
                    return@withContext true
                }
            }
            // Do not advance the push watermark when some rows/pages are still outstanding.
            return@withContext false
        } catch (failure: ServerUpdateRequiredException) {
            _message.value = failure.message
            throw failure
        } finally {
            syncing.value = false
        }
    }

    private data class Outgoing(
        val profile: SyncProfile?,
        val weights: List<SyncWeight>,
        val meals: List<SyncMeal>,
        val notes: List<SyncNote>,
        val fasts: List<SyncFast>,
    )
}
