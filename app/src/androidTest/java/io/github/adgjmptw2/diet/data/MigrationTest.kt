package io.github.adgjmptw2.diet.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 옛 버전 앱의 DB를 실제 SQLite로 만들고 지금 버전까지 올려 기록이 남는지 본다(app/schemas의 버전별 JSON 기준). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DietDatabase::class.java)

    /** 1.1(v3)을 쓰던 게스트가 지금 버전으로 업데이트한 경우 */
    @Test
    fun v3ToLatestKeepsGuestRecords() {
        helper.createDatabase(NAME, 3).apply {
            execSQL("INSERT INTO profile VALUES (0, 'FEMALE', 1995, 162.0, 60.0, 55.0, 'LIGHT', 20400, 1000, NULL)")
            execSQL("INSERT INTO weight_entry VALUES ('w1', 20400, 60.5, 1000, 1000, 0, 28.0)")
            execSQL(
                "INSERT INTO meal_entry VALUES ('m1', 20400, 'LUNCH', NULL, '김밥', '🍙', 1.0, '줄', 480.0, " +
                    "NULL, NULL, NULL, NULL, 1000, 1000, 0)"
            )
            execSQL("INSERT INTO day_note VALUES (20400, '산책', '', 1000, 0, 750)")
            execSQL("INSERT INTO fast VALUES ('f1', 100000, 200000, 16, 1000, 0)")
            close()
        }
        helper.runMigrationsAndValidate(NAME, LATEST, true).use { db ->
            assertEquals(1, db.count("SELECT COUNT(*) FROM profile WHERE id = 0 AND heightCm = 162.0 AND targetWeightKg = 55.0"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM weight_entry WHERE id = 'w1' AND weightKg = 60.5 AND bodyFatPct = 28.0"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM meal_entry WHERE id = 'm1' AND name = '김밥' AND kcal = 480.0"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM day_note WHERE epochDay = 20400 AND memo = '산책' AND waterMl = 750"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM fast WHERE id = 'f1' AND endAt = 200000 AND targetHours = 16"))
        }
    }

    /** 1.3(v5) 영양제 기록은 1.4에서 아침 체크로 옮겨진다 */
    @Test
    fun v5ToLatestMovesSupplementChecksToMorning() {
        helper.createDatabase(NAME, 5).apply {
            execSQL("INSERT INTO supplement VALUES ('s1', '비타민D', 0, 1000, 1000, 0)")
            execSQL("INSERT INTO supplement VALUES ('s2', '오메가3', 1, 1000, 1000, 1)")
            execSQL("INSERT INTO supplement_log VALUES (20400, 's1', 1234)")
            execSQL("INSERT INTO supplement_log VALUES (20401, 's1', 5678)")
            close()
        }
        helper.runMigrationsAndValidate(NAME, LATEST, true).use { db ->
            assertEquals(1, db.count("SELECT COUNT(*) FROM supplement WHERE id = 's1' AND slots = 1 AND deleted = 0"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM supplement WHERE id = 's2' AND deleted = 1"))
            assertEquals(2, db.count("SELECT COUNT(*) FROM supplement_log WHERE supplementId = 's1' AND slot = 1"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM supplement_log WHERE epochDay = 20401 AND takenAt = 5678"))
        }
    }

    private fun SupportSQLiteDatabase.count(sql: String): Int = query(sql).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private companion object {
        const val NAME = "migration-test"
        const val LATEST = 6
    }
}
