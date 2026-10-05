package ru.chronicnotebook.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Insert
    suspend fun insert(item: MeasurementEntity): Long

    @Insert
    suspend fun insertAll(items: List<MeasurementEntity>)

    @Update
    suspend fun update(item: MeasurementEntity)

    @Query("SELECT * FROM measurement ORDER BY takenAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurement WHERE valid = 1 AND takenAt >= :since ORDER BY takenAt")
    fun validSince(since: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurement WHERE valid = 1 AND takenAt >= :since ORDER BY takenAt")
    suspend fun validSinceOnce(since: Long): List<MeasurementEntity>

    @Query("SELECT * FROM measurement WHERE takenAt >= :since ORDER BY takenAt")
    suspend fun allSinceOnce(since: Long): List<MeasurementEntity>

    @Query("SELECT COUNT(*) FROM measurement WHERE valid = 1 AND takenAt >= :since")
    suspend fun validCountSince(since: Long): Int

    @Query("DELETE FROM measurement WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM measurement WHERE id = :id")
    suspend fun byId(id: Long): MeasurementEntity?
}

@Dao
interface MedDao {
    @Insert
    suspend fun insertMed(item: MedEntity): Long

    @Query("SELECT * FROM med WHERE active = 1 ORDER BY name")
    fun activeMeds(): Flow<List<MedEntity>>

    @Query("SELECT * FROM med WHERE active = 1 ORDER BY name")
    suspend fun activeMedsOnce(): List<MedEntity>

    @Query("SELECT * FROM med WHERE id = :id")
    suspend fun med(id: Long): MedEntity?

    @Query("UPDATE med SET active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    // inn и sourceUrl форма не редактирует, поэтому не трогаем их здесь:
    // иначе сохранение карточки затирало бы эти поля пустыми строками.
    @Query(
        "UPDATE med SET name = :name, dose = :dose, unit = :unit, " +
            "withFood = :withFood, prescribedBy = :prescribedBy, " +
            "expiresOn = :expiresOn, stock = :stock, stockUnit = :stockUnit " +
            "WHERE id = :id"
    )
    suspend fun updateMed(
        id: Long,
        name: String,
        dose: String,
        unit: String,
        withFood: Boolean,
        prescribedBy: String,
        expiresOn: String?,
        stock: Int,
        stockUnit: String,
    )

    @Insert
    suspend fun insertSchedule(item: ScheduleEntity): Long

    @Query("UPDATE schedule SET times = :times WHERE medId = :medId AND active = 1")
    suspend fun updateScheduleTimes(medId: Long, times: String)

    @Query("SELECT * FROM schedule WHERE medId = :medId AND active = 1")
    fun schedulesFor(medId: Long): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedule WHERE active = 1")
    suspend fun allSchedulesOnce(): List<ScheduleEntity>

    /** Расписание конкретного препарата для редактирования. */
    @Query("SELECT * FROM schedule WHERE medId = :medId AND active = 1 ORDER BY id")
    suspend fun schedulesForOnce(medId: Long): List<ScheduleEntity>
}

@Dao
interface IntakeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: IntakeEntity): Long

    @Query("SELECT * FROM intake WHERE status IN ('due', 'snoozed') AND dueAt <= :now ORDER BY dueAt")
    suspend fun dueNow(now: Long): List<IntakeEntity>

    @Query("SELECT * FROM intake WHERE status IN ('due', 'snoozed') ORDER BY dueAt LIMIT :limit")
    fun pending(limit: Int): Flow<List<IntakeEntity>>

    @Query("SELECT * FROM intake WHERE id = :id")
    suspend fun byId(id: Long): IntakeEntity?

    @Query("SELECT * FROM intake WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<IntakeEntity>

    @Query("UPDATE intake SET status = 'taken', takenAt = :now WHERE id = :id")
    suspend fun markTaken(id: Long, now: Long)

    @Query("UPDATE intake SET status = 'missed' WHERE id = :id")
    suspend fun markMissed(id: Long)

    /**
     * Будущие невыполненные приёмы препарата снимаются, когда время приёма
     * изменилось или препарат деактивирован. Иначе старые будильники продолжали
     * бы звонить по прежнему расписанию.
     */
    @Query(
        "SELECT id FROM intake WHERE medId = :medId " +
            "AND status IN ('due', 'snoozed') AND dueAt >= :now"
    )
    suspend fun futurePendingIds(medId: Long, now: Long): List<Long>

    @Query(
        "UPDATE intake SET status = 'missed' WHERE medId = :medId " +
            "AND status IN ('due', 'snoozed') AND dueAt >= :now"
    )
    suspend fun dropFuturePending(medId: Long, now: Long)

    @Query("UPDATE intake SET status = 'snoozed', dueAt = :dueAt WHERE id = :id")
    suspend fun snooze(id: Long, dueAt: Long)

    @Query("UPDATE intake SET lastStepSent = :step WHERE id = :id")
    suspend fun setStep(id: Long, step: Int)

    /**
     * Смена ступени эскалации только если она ещё равна ожидаемой.
     *
     * Будильник и сторож (воркер раз в 15 минут) могут обработать один приём
     * одновременно. Раньше обе ветки читали lastStepSent, обе слали уведомление
     * и обе писали ступень — пользователь получал два одинаковых напоминания.
     * Теперь выигрывает только один: UPDATE возвращает 1 строку, и вторая
     * ветка тихо выходит.
     */
    @Query("UPDATE intake SET lastStepSent = :step WHERE id = :id AND lastStepSent = :expected")
    suspend fun claimStep(id: Long, expected: Int, step: Int): Int

    @Query("SELECT * FROM intake WHERE dueAt >= :since ORDER BY dueAt")
    suspend fun since(since: Long): List<IntakeEntity>

    /**
     * Приёмы, которые уже должны были быть, включая непогашенные.
     * `since` возвращает и будущие строки: их нельзя считать в знаменателе
     * выполнения, иначе «принято 5 из 190» вместо «5 из 12».
     */
    @Query("SELECT * FROM intake WHERE dueAt BETWEEN :since AND :until ORDER BY dueAt")
    suspend fun between(since: Long, until: Long): List<IntakeEntity>

    @Query("UPDATE intake SET status = 'due' WHERE id = :id")
    suspend fun reopen(id: Long)

    @Query("SELECT * FROM intake WHERE status = 'due' AND dueAt < :now")
    suspend fun staleBefore(now: Long): List<IntakeEntity>
}

@Dao
interface WeatherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WeatherEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WeatherEntity>)

    @Query("SELECT * FROM weather WHERE day >= :since ORDER BY day")
    fun historySince(since: String): Flow<List<WeatherEntity>>

    @Query("SELECT * FROM weather WHERE day >= :since ORDER BY day")
    suspend fun historySinceOnce(since: String): List<WeatherEntity>

    @Query("SELECT * FROM weather WHERE day = :day")
    suspend fun byDay(day: String): WeatherEntity?

    @Query("SELECT COUNT(*) FROM weather")
    suspend fun count(): Int
}

@Dao
interface ReminderLogDao {
    @Insert
    suspend fun insert(item: ReminderLogEntity): Long
}

/** Сколько замеров помечено каждым тегом. */
data class TagUsage(
    val tagId: Long,
    val n: Int,
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY sortOrder, name")
    fun all(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag ORDER BY sortOrder, name")
    suspend fun allOnce(): List<TagEntity>

    @Query("SELECT * FROM tag WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: TagEntity): Long

    @Query("UPDATE tag SET name = :name, colorArgb = :colorArgb WHERE id = :id")
    suspend fun rename(id: Long, name: String, colorArgb: Int)

    /**
     * Тег удаляется вместе со связями. Связи снимаем явно, а не только каскадом:
     * на старых прошивках внешние ключи иногда не включены, и иначе остались бы
     * строки, ссылающиеся на несуществующий тег, а счётчики разъезжались бы.
     */
    @Query("DELETE FROM measurement_tag WHERE tagId = :id")
    suspend fun unlinkEverywhere(id: Long)

    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "SELECT t.* FROM tag t INNER JOIN measurement_tag mt ON mt.tagId = t.id " +
            "WHERE mt.measurementId = :measurementId ORDER BY t.sortOrder, t.name"
    )
    suspend fun tagsOf(measurementId: Long): List<TagEntity>

    @Query("SELECT tagId FROM measurement_tag WHERE measurementId = :measurementId")
    suspend fun tagIdsOf(measurementId: Long): List<Long>

    @Query("SELECT * FROM measurement_tag")
    suspend fun allLinksOnce(): List<MeasurementTagEntity>

    @Query("INSERT OR IGNORE INTO measurement_tag (measurementId, tagId) VALUES (:measurementId, :tagId)")
    suspend fun link(measurementId: Long, tagId: Long)

    @Query("DELETE FROM measurement_tag WHERE measurementId = :measurementId AND tagId = :tagId")
    suspend fun unlink(measurementId: Long, tagId: Long)

    @Query("SELECT tagId AS tagId, COUNT(*) AS n FROM measurement_tag GROUP BY tagId")
    fun usage(): Flow<List<TagUsage>>
}

@Database(
    entities = [
        MeasurementEntity::class,
        TagEntity::class,
        MeasurementTagEntity::class,
        MedEntity::class,
        ScheduleEntity::class,
        IntakeEntity::class,
        WeatherEntity::class,
        ReminderLogEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun medDao(): MedDao
    abstract fun intakeDao(): IntakeDao
    abstract fun weatherDao(): WeatherDao
    abstract fun reminderLogDao(): ReminderLogDao
    abstract fun tagDao(): TagDao

    companion object {
        const val NAME = "chronic.db"

        /**
         * 1 -> 2: словарь тегов и связи с замерами.
         *
         * Только CREATE: ни одна существующая таблица не меняется, поэтому
         * история замеров и приёмов сохраняется при обновлении. Порядок
         * важен — сначала tag, потом таблица со ссылкой на неё.
         */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tag` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`colorArgb` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_name` ON `tag` (`name`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `measurement_tag` (" +
                        "`measurementId` INTEGER NOT NULL, " +
                        "`tagId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`measurementId`, `tagId`), " +
                        "FOREIGN KEY(`measurementId`) REFERENCES `measurement`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`tagId`) REFERENCES `tag`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_measurement_tag_tagId` " +
                        "ON `measurement_tag` (`tagId`)"
                )
            }
        }

        /**
         * Намеренно без destructiveFallback: он стирал все замеры и историю
         * при любом обновлении схемы. Теперь при пропущенной миграции Room
         * падает громко, а не молча уничтожает данные.
         */
        /**
         * 2 -> 3: срок годности и остаток у препарата.
         *
         * Только ADD COLUMN с DEFAULT: ни одна строка не меняется и ни одна
         * таблица не пересоздаётся, поэтому все замеры, приёмы и напоминания
         * переживают обновление. Значения по умолчанию совпадают с теми, что
         * Room подставил бы при создании таблицы с нуля, иначе проверка схемы
         * при первом запуске после обновления не пройдёт.
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `med` ADD COLUMN `expiresOn` TEXT")
                db.execSQL("ALTER TABLE `med` ADD COLUMN `stock` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `med` ADD COLUMN `stockUnit` TEXT NOT NULL DEFAULT 'шт'")
            }
        }

        val MIGRATIONS: Array<androidx.room.migration.Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
