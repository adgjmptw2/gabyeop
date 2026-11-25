package io.github.adgjmptw2.diet.data.steps

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** 실제 Health Connect에 걸음을 넣고 날짜별로 제대로 읽히는지 본다. 에뮬레이터·기기에서만 돈다. */
@RunWith(AndroidJUnit4::class)
class HealthConnectStepsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val steps = HealthConnectSteps(context)

    @Before
    fun grant() {
        assumeTrue(steps.available())
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.grantRuntimePermission(context.packageName, steps.permission)
        ui.grantRuntimePermission(context.packageName, HealthPermission.getWritePermission(StepsRecord::class))
    }

    @Test
    fun dailyStepsSplitByLocalDay() = runBlocking {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun record(day: LocalDate, hour: Int, count: Long): StepsRecord {
            val start = day.atTime(hour, 0).atZone(zone)
            val end = start.plusMinutes(30)
            return StepsRecord(start.toInstant(), start.offset, end.toInstant(), end.offset, count, Metadata.manualEntry())
        }
        val before = steps.daily(client, today.minusDays(6), today) ?: emptyMap()
        val ids = client.insertRecords(
            listOf(
                record(today.minusDays(1), 23, 1200),
                record(today, 0, 300),
                record(today, 9, 500),
            )
        ).recordIdsList
        try {
            val read = steps.daily(client, today.minusDays(6), today)
            requireNotNull(read) { "걸음을 읽지 못함" }
            assertEquals((before[today.minusDays(1)] ?: 0) + 1200, read[today.minusDays(1)])
            assertEquals((before[today] ?: 0) + 800, read[today])
        } finally {
            client.deleteRecords(StepsRecord::class, ids, emptyList())
        }
    }
}
