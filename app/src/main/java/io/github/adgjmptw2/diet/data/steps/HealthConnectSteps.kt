package io.github.adgjmptw2.diet.data.steps

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Health Connect 걸음 읽기. 권한이 없거나 SDK가 unavailable이면 null을 돌려준다. */
@Singleton
class HealthConnectSteps @Inject constructor(@ApplicationContext private val context: Context) {
    val permission: String = HealthPermission.getReadPermission(StepsRecord::class)

    fun available(): Boolean = runCatching {
        HealthConnectClient.getSdkStatus(context, PROVIDER) == HealthConnectClient.SDK_AVAILABLE
    }.getOrDefault(false)

    fun client(): HealthConnectClient? = runCatching {
        if (available()) HealthConnectClient.getOrCreate(context) else null
    }.getOrNull()

    suspend fun hasPermission(client: HealthConnectClient): Boolean =
        runCatching { client.permissionController.getGrantedPermissions().contains(permission) }
            .getOrDefault(false)

    /**
     * 날짜별 걸음을 한 번에 읽는다. Health Connect는 권한 부여 전 30일까지만 읽힌다.
     * 읽을 수 없으면 null.
     */
    suspend fun daily(
        client: HealthConnectClient,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Int>? = runCatching {
        if (!hasPermission(client)) return null
        // 날(Period)로 나눌 때는 현지 시각(LocalDateTime) 범위만 받는다. Instant 범위면 요청을 만들 때 바로 예외가 난다.
        val response = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        val result = mutableMapOf<LocalDate, Int>()
        response.forEach { row ->
            val day = row.startTime.toLocalDate()
            val steps = row.result[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
            if (steps > 0) result[day] = steps
        }
        if (result.isEmpty()) {
            result.putAll(byOrigin(client, from, to))
            if (result.isNotEmpty()) Log.i(TAG, "합계가 비어 기록을 직접 셌어요(우선순위 목록에 걸음 앱 없음)")
        }
        result
    }.onFailure { Log.w(TAG, "걸음 읽기 실패", it) }.getOrNull()

    /**
     * 걸음 앱이 Health Connect 우선순위 목록에 없으면 합계가 비어 나온다(기록은 있어도).
     * 그때만 기록을 직접 읽어 날마다 앱 하나만 센다.
     */
    private suspend fun byOrigin(client: HealthConnectClient, from: LocalDate, to: LocalDate): Map<LocalDate, Int> {
        val filter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
        val zone = ZoneId.systemDefault()
        val entries = mutableListOf<StepsMath.Entry>()
        var token: String? = null
        var pages = 0
        do {
            val page = client.readRecords(ReadRecordsRequest(StepsRecord::class, filter, pageSize = 5000, pageToken = token))
            page.records.forEach { r ->
                val date = LocalDateTime.ofInstant(r.startTime, r.startZoneOffset ?: zone).toLocalDate()
                entries += StepsMath.Entry(r.metadata.dataOrigin.packageName, date, r.count)
            }
            token = page.pageToken
            pages++
        } while (token != null && pages < MAX_PAGES)
        return StepsMath.busiestOrigin(entries)
    }

    suspend fun read(client: HealthConnectClient, from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        daily(client, from, to) ?: emptyMap()

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
        const val LOOKBACK_DAYS = 30L
        private const val TAG = "HealthConnectSteps"
        private const val MAX_PAGES = 20
    }
}
