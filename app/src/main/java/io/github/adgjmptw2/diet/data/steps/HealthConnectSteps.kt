package io.github.adgjmptw2.diet.data.steps

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
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
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, Int>? = runCatching {
        if (!hasPermission(client)) return null
        val start = from.atStartOfDay(zone).toInstant()
        val endExclusive = to.plusDays(1).atStartOfDay(zone).toInstant()
        val response = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, endExclusive),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        val result = mutableMapOf<LocalDate, Int>()
        response.forEach { row ->
            val day = row.startTime.atZone(zone).toLocalDate()
            val steps = row.result[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
            if (steps > 0) result[day] = steps
        }
        result
    }.getOrNull()

    suspend fun read(client: HealthConnectClient, from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        daily(client, from, to) ?: emptyMap()

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
        const val LOOKBACK_DAYS = 30L
    }
}
