package ru.chronicnotebook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.chronicnotebook.App
import ru.chronicnotebook.data.IntakeEntity
import ru.chronicnotebook.data.IntakeScheduler
import ru.chronicnotebook.data.MedEntity
import ru.chronicnotebook.data.MeasurementEntity
import ru.chronicnotebook.data.ScheduleEntity
import ru.chronicnotebook.data.WeatherEntity
import ru.chronicnotebook.domain.Correlation
import ru.chronicnotebook.domain.Factor
import ru.chronicnotebook.domain.Level
import ru.chronicnotebook.domain.Stats
import ru.chronicnotebook.domain.advice
import ru.chronicnotebook.domain.bucketOf
import ru.chronicnotebook.domain.classify
import ru.chronicnotebook.domain.snoozedDueAt
import ru.chronicnotebook.reminders.AlarmScheduler
import ru.chronicnotebook.reminders.Escalator
import ru.chronicnotebook.sync.Scheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as App).container
    private val db = container.db

    val settings = container.settings

    val measurements = db.measurementDao().recent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val meds = db.medDao().activeMeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weather = db.weatherDao().historySince(LocalDate.now().minusDays(120).toString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class UiState(
        val level: Level = Level.NORMAL,
        val advice: String = "",
        val baselineText: String = "База набирается: нужно 14 корректных замеров",
        val factorText: String = "Нужно 20 корректных замеров и история погоды",
        val adherenceText: String = "Нет данных о приёме",
        val weatherText: String = "Погода не синхронизирована",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Тик после каждого пересчёта. Экраны, которые грузят списки отдельным
     * запросом, слушают его: раньше после нажатия «Принял» карточка приёма
     * оставалась на экране до ухода на другую вкладку.
     */
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick.asStateFlow()

    init {
        viewModelScope.launch {
            // Scheduler.start уже вызывается в App.onCreate: будильники и
            // страховка должны работать и без открытия приложения.
            // Перепланирование при каждом запуске: после обновления приложения
            // или перезагрузки будильники и приёмы должны восстановиться сами.
            IntakeScheduler(getApplication()).rescheduleAll()
            db.weatherDao().count().let { if (it == 0) Scheduler.syncNow(getApplication()) }
        }
    }

    fun addMeasurement(
        sys: Int,
        dia: Int,
        pulse: Int?,
        contextKey: String,
        rested: Boolean,
        spoke: Boolean,
        cuffOk: Boolean,
        note: String,
        issued: Boolean,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val level = classify(sys, dia)
            val issues = ru.chronicnotebook.domain.Protocol.issues(sys, dia, context(contextKey), rested, spoke, cuffOk)
            val ctx = context(contextKey)
            db.measurementDao().insert(
                MeasurementEntity(
                    takenAt = now,
                    day = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).toString(),
                    sys = sys,
                    dia = dia,
                    pulse = pulse,
                    context = ctx.key,
                    valid = rested && spoke.not() && cuffOk && ctx.key == "rest" &&
                        issued.not() && issues.none { it.contains("подозрительно мала") },
                    issues = issues.joinToString("; "),
                    note = note,
                    bucket = bucketOf(
                        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
                    ).name.lowercase(),
                )
            )
            _message.value = if (issues.isEmpty()) "Записан. ${advice(level, sys, dia)}" else
                "Записан с пометками: ${issues.joinToString(", ")}"
            // Пересчёт обязателен: карточки «Личная база», «Погода и давление»
            // и adherence на главном экране живут в UiState, который собирает
            // только refresh(). Без него после записи замера человек уходил на
            // «Дом» и видел «нужно 14 корректных замеров (сейчас 0)», хотя замер
            // только что введён. Замерений могло быть и тринадцать — счётчик
            // не двигался до перезапуска приложения.
            refresh()
        }
    }

    fun deleteMeasurement(id: Long) {
        viewModelScope.launch {
            db.measurementDao().delete(id)
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val since = System.currentTimeMillis() - 30L * 24 * 3600_000
            val all = db.measurementDao().allSinceOnce(since)
            val valid = all.filter { it.valid }
            val baseline = Stats.baseline(valid)
            val factors = Correlation.sensitivity(all, db.weatherDao().historySinceOnce(
                LocalDate.now().minusDays(120).toString()
            ))

            val baseText = if (baseline == null) {
                "База набирается: нужно 14 корректных замеров (сейчас ${valid.size})"
            } else {
                val parts = listOfNotNull(
                    baseline.morning?.let { "утро %.0f/%.0f".format(it.sys, it.dia) },
                    baseline.day?.let { "день %.0f/%.0f".format(it.sys, it.dia) },
                    baseline.evening?.let { "вечер %.0f/%.0f".format(it.sys, it.dia) },
                )
                "База по ${baseline.n} замерам: ${parts.joinToString(", ")}" +
                    (baseline.sdSys?.let { ", разброс САД ±%.0f".format(it) } ?: "")
            }

            val factorText = if (!factors.ready) {
                "Нужно 20 корректных замеров и история погоды (сейчас ${factors.n})"
            } else if (factors.factors.isEmpty()) {
                "За ${factors.n} замеров устойчивых связей с погодой не найдено"
            } else {
                factors.factors.joinToString("\n") { f ->
                    "%s, лаг %d ч: r=%.2f, %s (n=%d)".format(
                        f.label, f.lagHours, f.r, f.effectText, f.n
                    )
                }
            }

            val now = System.currentTimeMillis()
            // Знаменатель — только те приёмы, которые уже наступили. Раньше сюда
            // попадали и будущие, и «принято 5 из 190» выглядело как полный
            // провал при пяти выполненных назначениях.
            val intake = db.intakeDao().between(now - 30L * 24 * 3600_000, now)
            val taken = intake.count { it.status == IntakeEntity.STATUS_TAKEN }
            val missed = intake.count { it.status == IntakeEntity.STATUS_MISSED }
            val pending = intake.count { it.status == IntakeEntity.STATUS_DUE }
            val adherenceText = if (intake.isEmpty()) "Нет данных о приёме" else {
                "Принято $taken из ${taken + missed + pending} за 30 дней, пропущено $missed"
            }

            val today = db.weatherDao().byDay(LocalDate.now().toString())
            val weatherText = if (today?.tMean == null) "Погода не синхронизирована" else {
                "Сейчас %.1f °C, давление %.0f мм рт.ст. (%s)".format(
                    today.tMean, today.pMsl ?: 0.0, today.source
                )
            }

            _state.value = _state.value.copy(
                baselineText = baseText,
                factorText = factorText,
                adherenceText = adherenceText,
                weatherText = weatherText,
            )
            _tick.value++
        }
    }

    fun addMed(
        name: String,
        inn: String,
        dose: String,
        unit: String,
        withFood: Boolean,
        times: List<Int>,
        prescribedBy: String,
    ) {
        viewModelScope.launch {
            val medId = db.medDao().insertMed(
                MedEntity(
                    name = name,
                    inn = inn,
                    dose = dose,
                    unit = unit,
                    withFood = withFood,
                    prescribedBy = prescribedBy,
                )
            )
            if (times.isNotEmpty()) {
                db.medDao().insertSchedule(
                    ScheduleEntity(
                        medId = medId,
                        times = times.sorted().joinToString(","),
                    )
                )
            }
            IntakeScheduler(getApplication()).rescheduleAll()
            _message.value = "Препарат добавлен, напоминания запланированы"
        }
    }

    fun deactivateMed(id: Long) {
        viewModelScope.launch {
            cancelFutureIntakes(id)
            db.medDao().deactivate(id)
            IntakeScheduler(getApplication()).rescheduleAll()
            _message.value = "Препарат деактивирован"
        }
    }

    /** Снимает ещё не наступившие приёмы препарата и их будильники. */
    private suspend fun cancelFutureIntakes(medId: Long) {
        val app = getApplication<Application>()
        val now = System.currentTimeMillis()
        db.intakeDao().futurePendingIds(medId, now).forEach { AlarmScheduler.cancel(app, it) }
        db.intakeDao().dropFuturePending(medId, now)
    }

    /** Текущие слоты приёма препарата для формы редактирования. */
    suspend fun timesOf(medId: Long): List<Int> =
        db.medDao().schedulesForOnce(medId)
            .flatMap { it.times.split(',') }
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..(24 * 60 - 1) }
            .distinct()
            .sorted()

    fun updateMed(
        id: Long,
        name: String,
        dose: String,
        unit: String,
        withFood: Boolean,
        prescribedBy: String,
        times: List<Int>,
    ) {
        viewModelScope.launch {
            cancelFutureIntakes(id)
            db.medDao().updateMed(id, name, dose, unit, withFood, prescribedBy)
            val clean = times.distinct().sorted()
            if (clean.isEmpty()) {
                _message.value = "Время приёма не задано: напоминания приходить не будут"
            } else {
                db.medDao().updateScheduleTimes(id, clean.joinToString(","))
                _message.value = "Изменения сохранены, напоминания пересчитаны"
            }
            IntakeScheduler(getApplication()).rescheduleAll()
            refresh()
        }
    }

    fun takeIntake(id: Long) {
        viewModelScope.launch {
            db.intakeDao().markTaken(id, System.currentTimeMillis())
            AlarmScheduler.cancel(getApplication(), id)
            refresh()
        }
    }

    fun snoozeIntake(id: Long, minutes: Long) {
        viewModelScope.launch {
            val intake = db.intakeDao().byId(id) ?: return@launch
            val now = System.currentTimeMillis()
            val due = snoozedDueAt(intake.dueAt, now, minutes)
            db.intakeDao().snooze(id, due)
            AlarmScheduler.scheduleExact(getApplication(), intake, due - now)
            refresh()
        }
    }

    fun syncWeather() {
        Scheduler.syncNow(getApplication())
        _message.value = "Синхронизация погоды запущена"
    }

    /**
     * Отчёт для врача собирается по всей базе, а не по последним 200 строкам
     * списка на экране: иначе при большом дневнике счётчики в отчёте были тихо
     * занижены, а врач принимал их за реальную картину.
     */
    suspend fun buildReport(): String {
        val since = System.currentTimeMillis() - 30L * 24 * 3600_000
        val rows = db.measurementDao().allSinceOnce(since)
        val w = db.weatherDao().historySinceOnce(LocalDate.now().minusDays(120).toString())
        return ReportBuilder(getApplication(), rows, w).build()
    }

    suspend fun pendingIntakes(): List<IntakeEntity> =
        db.intakeDao().dueNow(System.currentTimeMillis() + 60 * 60_000)

    fun clearMessage() {
        _message.value = null
    }

    fun canScheduleExact(): Boolean = AlarmScheduler.canScheduleExact(getApplication())

    fun bootstrap() {
        Scheduler.scheduleMeasureHints(getApplication())
        viewModelScope.launch { Escalator(getApplication()).catchUp() }
        refresh()
    }

    private fun context(key: String) =
        ru.chronicnotebook.domain.Context.entries.firstOrNull { it.key == key } ?: ru.chronicnotebook.domain.Context.REST
}
