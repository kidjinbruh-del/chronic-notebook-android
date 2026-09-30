package ru.chronicnotebook.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Обновление не должно стирать историю.
 *
 * Схема 1 -> 2 добавляет теги, поэтому здесь сначала руками создаётся база
 * ровно первой версии, в неё кладётся замер, затем она открывается через
 * Room с MIGRATIONS. Если миграция не совпадёт с JSON-схемой, Room падает
 * громко, а если совпадёт — замер обязан остаться.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-test.db"

    /** DDL таблицы measurement из schemas/1.json — вручную, как это сделал бы Room. */
    private val v1Measurement = """
        CREATE TABLE IF NOT EXISTS `measurement` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `takenAt` INTEGER NOT NULL,
            `day` TEXT NOT NULL,
            `sys` INTEGER NOT NULL,
            `dia` INTEGER NOT NULL,
            `pulse` INTEGER,
            `arm` TEXT NOT NULL,
            `context` TEXT NOT NULL,
            `valid` INTEGER NOT NULL,
            `issues` TEXT NOT NULL,
            `note` TEXT NOT NULL,
            `bucket` TEXT NOT NULL DEFAULT 'rest')
    """.trimIndent()

    private val v1Med = """
        CREATE TABLE IF NOT EXISTS `med` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `inn` TEXT NOT NULL,
            `dose` TEXT NOT NULL,
            `unit` TEXT NOT NULL,
            `withFood` INTEGER NOT NULL,
            `startsOn` TEXT,
            `endsOn` TEXT,
            `prescribedBy` TEXT NOT NULL,
            `sourceUrl` TEXT NOT NULL,
            `active` INTEGER NOT NULL)
    """.trimIndent()

    private val v1Schedule = """
        CREATE TABLE IF NOT EXISTS `schedule` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `medId` INTEGER NOT NULL,
            `times` TEXT NOT NULL,
            `everyHours` INTEGER,
            `foodOverride` TEXT NOT NULL,
            `note` TEXT NOT NULL,
            `active` INTEGER NOT NULL)
    """.trimIndent()

    private val v1Intake = """
        CREATE TABLE IF NOT EXISTS `intake` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `medId` INTEGER NOT NULL,
            `dueAt` INTEGER NOT NULL,
            `takenAt` INTEGER,
            `status` TEXT NOT NULL,
            `note` TEXT NOT NULL,
            `lastStepSent` INTEGER NOT NULL)
    """.trimIndent()

    private val v1Weather = """
        CREATE TABLE IF NOT EXISTS `weather` (
            `day` TEXT NOT NULL PRIMARY KEY,
            `lat` REAL NOT NULL,
            `lon` REAL NOT NULL,
            `tMean` REAL,
            `tMin` REAL,
            `tMax` REAL,
            `pMsl` REAL,
            `humidity` REAL,
            `wind` REAL,
            `precip` REAL,
            `source` TEXT NOT NULL,
            `fetchedAt` INTEGER NOT NULL)
    """.trimIndent()

    private val v1ReminderLog = """
        CREATE TABLE IF NOT EXISTS `reminder_log` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `intakeId` INTEGER NOT NULL,
            `step` INTEGER NOT NULL,
            `sentAt` INTEGER NOT NULL,
            `ok` INTEGER NOT NULL,
            `detail` TEXT NOT NULL)
    """.trimIndent()

    /**
     * Индексы первой версии — из schemas/1.json.
     *
     * Room после миграции сверяет схему ВСЕХ таблиц, а не только изменившихся,
     * поэтому фикстура обязана повторять их в точности.
     */
    private val v1Indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_measurement_takenAt` ON `measurement` (`takenAt`)",
        "CREATE INDEX IF NOT EXISTS `index_measurement_day` ON `measurement` (`day`)",
        "CREATE INDEX IF NOT EXISTS `index_med_inn` ON `med` (`inn`)",
        "CREATE INDEX IF NOT EXISTS `index_med_name` ON `med` (`name`)",
        "CREATE INDEX IF NOT EXISTS `index_schedule_medId` ON `schedule` (`medId`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_intake_medId_dueAt` ON `intake` (`medId`, `dueAt`)",
        "CREATE INDEX IF NOT EXISTS `index_intake_dueAt` ON `intake` (`dueAt`)",
        "CREATE INDEX IF NOT EXISTS `index_intake_status` ON `intake` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_reminder_log_intakeId` ON `reminder_log` (`intakeId`)",
    )

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun upgradeKeepsMeasurementsAndAddsTags() = runBlocking {
        // 1. База первой версии, заполненная «вручную».
        val helper = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                listOf(
                    v1Measurement, v1Med, v1Schedule, v1Intake, v1Weather, v1ReminderLog
                ).forEach { db.execSQL(it) }
                v1Indices.forEach { db.execSQL(it) }
            }

            override fun onUpgrade(
                db: androidx.sqlite.db.SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int,
            ) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(helper)
            .build()
        val legacy = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(config)
        legacy.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO measurement " +
                    "(takenAt, day, sys, dia, pulse, arm, context, valid, issues, note, bucket) " +
                    "VALUES (1700000000000, '2023-11-14', 128, 82, 66, 'left', 'rest', 1, '', '', 'rest')"
            )
        }
        legacy.close()

        // 2. Открываем через Room — сработает миграция.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()

        val rows = db.measurementDao().allSinceOnce(0)
        assertEquals("замеры первой версии должны сохраниться", 1, rows.size)
        assertEquals(128, rows.first().sys)
        assertEquals("2023-11-14", rows.first().day)

        // 3. Теги работают на базе после миграции.
        val tagId = db.tagDao().insert(TagEntity(name = "после нагрузки", colorArgb = -1))
        assertTrue("тег должен вставиться", tagId > 0)
        db.tagDao().link(rows.first().id, tagId)
        assertEquals(
            listOf("после нагрузки"),
            db.tagDao().tagsOf(rows.first().id).map { it.name },
        )

        // 4. Повторная вставка того же тега не плодит дубликат.
        db.tagDao().insert(TagEntity(name = "после нагрузки"))
        assertEquals(1, db.tagDao().allOnce().size)

        // 5. Тег, снятый с замера, удаляется вместе со связями.
        db.tagDao().unlink(rows.first().id, tagId)
        db.tagDao().unlinkEverywhere(tagId)
        db.tagDao().delete(tagId)
        assertTrue(db.tagDao().allLinksOnce().isEmpty())
        assertTrue(db.tagDao().allOnce().isEmpty())

        db.close()
    }

    @Test
    fun migrationIsRegistered() {
        assertTrue(
            "миграция 1->2 должна быть зарегистрирована, иначе база откроется с ошибкой",
            AppDatabase.MIGRATIONS.any { it.startVersion == 1 && it.endVersion == 2 },
        )
    }
}