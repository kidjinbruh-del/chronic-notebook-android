package ru.chronicnotebook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import ru.chronicnotebook.data.ImportPlan
import ru.chronicnotebook.data.ImportSummary
import ru.chronicnotebook.data.MeasurementEntity
import ru.chronicnotebook.data.MeasurementTagEntity
import ru.chronicnotebook.data.MeasurementIo
import ru.chronicnotebook.data.PortableMeasurement
import ru.chronicnotebook.data.ScheduleEntity
import ru.chronicnotebook.data.TagEntity
import ru.chronicnotebook.data.TagUsage
import ru.chronicnotebook.io.FileExchange
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

    /** Словарь тегов: список не пустеет, пока на него кто-то подписан. */
    val tags = db.tagDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Сколько замеров носит каждый тег: нужно для словаря и фильтров. */
    val tagUsage = db.tagDao().usage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _tagLinks = MutableStateFlow<List<MeasurementTagEntity>>(emptyList())
    val tagLinks: StateFlow<List<MeasurementTagEntity>> = _tagLinks.asStateFlow()

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

    /** Готовый файл выгрузки: байты ждут, пока человек выберет папку. */
    data class PendingExport(val name: String, val mimeType: String, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is PendingExport && name == other.name && mimeType == other.mimeType &&
                bytes.contentEquals(other.bytes)

        override fun hashCode(): Int =
            (name.hashCode() * 31 + mimeType.hashCode()) * 31 + bytes.contentHashCode()
    }

    private val _pendingExport = MutableStateFlow<PendingExport?>(null)
    val pendingExport: StateFlow<PendingExport?> = _pendingExport.asStateFlow()

    /** Разобранный файл импорта: сколько добавим, сколько уже есть. */
    private val _importPlan = MutableStateFlow<ImportPlan?>(null)
    val importPlan: StateFlow<ImportPlan?> = _importPlan.asStateFlow()

    /**
     * Тик после каждого пересчёта. Экраны, которые грузят списки отдельным
     * запросом, слушают его: раньше после нажатия «Принял» карточка приёма
     * оставалась на экране до ухода на другую вкладку.
     */
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick.asStateFlow()

    /** Цикл автообновления, живёт только пока экран на виду. */
    private var autoRefreshJob: Job? = null

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

    /**
     * Автообновление данных, пока приложение на экране.
     *
     * Раньше состояние пересчитывалось только в момент запуска и после части
     * действий, поэтому свежая информация появлялась «не сразу» и только после
     * перезахода: например, после «Принял» в шторке список «Ожидают отметки»
     * оставался прежним до перезапуска приложения, а погода подтягивалась
     * воркером уже после того, как карточка отрисовалась.
     *
     * Цикл нужен потому, что часть изменений приходит извне: будильник о приёме,
     * кнопка в уведомлении, погодный воркер. Их нельзя поймать вызовом из
     * обработчика кнопки, но они все попадают в ту же базу.
     */
    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_MS)
                refreshNow()
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
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
        tags: List<String> = emptyList(),
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val level = classify(sys, dia)
            val issues = ru.chronicnotebook.domain.Protocol.issues(sys, dia, context(contextKey), rested, spoke, cuffOk)
            val ctx = context(contextKey)
            val id = db.measurementDao().insert(
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
            applyTagNames(id, tags)
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

    // ---------------------------------------------------------------- теги

    /**
     * Новый тег в словаре.
     *
     * Имя сравнивается без учёта регистра и лишних пробелов: иначе в базе
     * появлялись «Кофе», «кофе» и « кофе », и фильтр на графике раздваивался.
     */
    fun createTag(name: String) {
        viewModelScope.launch {
            val clean = name.trim().replace(Regex("\\s+"), " ")
            if (clean.isEmpty()) return@launch
            if (clean.length > 40) {
                _message.value = "Название тега слишком длинное"
                return@launch
            }
            val same = db.tagDao().allOnce().firstOrNull { it.name.equals(clean, ignoreCase = true) }
            if (same != null) {
                _message.value = "Тег «${same.name}» уже есть"
                return@launch
            }
            db.tagDao().insert(
                TagEntity(name = clean, colorArgb = tagColor(clean), sortOrder = db.tagDao().allOnce().size)
            )
            _message.value = "Тег «$clean» добавлен"
            refresh()
        }
    }

    /** Тег удаляется вместе со всеми своими отметками на замерах. */
    fun deleteTag(id: Long) {
        viewModelScope.launch {
            db.tagDao().unlinkEverywhere(id)
            db.tagDao().delete(id)
            _message.value = "Тег удалён"
            refresh()
        }
    }

    fun toggleTag(measurementId: Long, tagId: Long, on: Boolean) {
        viewModelScope.launch {
            if (on) db.tagDao().link(measurementId, tagId) else db.tagDao().unlink(measurementId, tagId)
            refresh()
        }
    }

    /**
     * Навесить на замер теги по именам, создавая недостающие.
     *
     * Так работает и форма замера, и импорт файла: при импорте чужого файла
     * теги «после нагрузки» и «стресс» должны появиться в словаре сами,
     * иначе половина записей осталась бы без меток.
     */
    suspend fun applyTagNames(measurementId: Long, names: List<String>) {
        val clean = names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        if (clean.isEmpty()) return
        val existing = db.tagDao().allOnce().associateBy { it.name.lowercase() }
        var order = existing.size
        val ids = clean.map { name ->
            existing[name.lowercase()]?.id ?: db.tagDao().insert(
                TagEntity(name = name, colorArgb = tagColor(name), sortOrder = order++)
            )
        }
        ids.filter { it > 0 }.forEach { db.tagDao().link(measurementId, it) }
    }

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    /** Пересчёт состояния. Вынесено отдельно, чтобы цикл не плодил корутины. */
    private suspend fun refreshNow() {
        runCatching {
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
            // Связи «замер — тег» перечитываются тем же пересчётом: иначе метки
            // на графике и в фильтре оставались бы от прошлой правки.
            _tagLinks.value = db.tagDao().allLinksOnce()
            _tick.value++
        }.onFailure {
            // Пересчёт не должен ронять цикл автообновления: одно временно
            // недоступное чтение не лишает пользователя обновлений дальше.
            android.util.Log.w("ChronicNotebook", "Не удалось обновить данные", it)
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
        expiresOn: String? = null,
        stock: Int = 0,
        stockUnit: String = "шт",
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
                    expiresOn = expiresOn,
                    stock = stock,
                    stockUnit = stockUnit,
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
            refresh()
        }
    }

    fun deactivateMed(id: Long) {
        viewModelScope.launch {
            cancelFutureIntakes(id)
            db.medDao().deactivate(id)
            IntakeScheduler(getApplication()).rescheduleAll()
            _message.value = "Препарат деактивирован"
            refresh()
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
        expiresOn: String? = null,
        stock: Int = 0,
        stockUnit: String = "шт",
    ) {
        viewModelScope.launch {
            cancelFutureIntakes(id)
            db.medDao().updateMed(
                id, name, dose, unit, withFood, prescribedBy, expiresOn, stock, stockUnit,
            )
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

    // ------------------------------------------------- файловый обмен

    /** Замеры за весь период, с тегами, в виде, пригодном для файла. */
    suspend fun portableRows(days: Int? = null): List<PortableMeasurement> {
        val since = if (days == null) 0L else System.currentTimeMillis() - days * 24L * 3600_000
        val rows = db.measurementDao().allSinceOnce(since)
        val links = db.tagDao().allLinksOnce()
        val names = db.tagDao().allOnce().associate { it.id to it.name }
        val byMeasurement = links.groupBy { it.measurementId }
        return rows.map { m ->
            PortableMeasurement(
                takenAt = m.takenAt,
                sys = m.sys,
                dia = m.dia,
                pulse = m.pulse,
                arm = m.arm,
                context = m.context,
                valid = m.valid,
                issues = m.issues,
                note = m.note,
                bucket = m.bucket,
                tags = byMeasurement[m.id].orEmpty()
                    .mapNotNull { names[it.tagId] }
                    .sorted(),
            )
        }
    }

    /**
     * Запись выбранного файла.
     *
     * `payload` собирается до открытия файла, а не после: если сборка упадёт
     * (например, из-за слишком большого дневника), человек не получит на руках
     * пустой файл, который потом примут за выгрузку.
     */
    fun export(
        suggestedName: String,
        mimeType: String,
        payload: suspend () -> ByteArray,
    ) {
        viewModelScope.launch { prepareExport(suggestedName, mimeType, payload) }
    }

    /** Подготовка файла с блокировкой кнопки: две выгрузки не должны перетирать друг друга. */
    suspend fun prepareExport(
        suggestedName: String,
        mimeType: String,
        payload: suspend () -> ByteArray,
    ) {
        runCatching { payload() }
            .onSuccess { bytes ->
                _pendingExport.value = PendingExport(suggestedName, mimeType, bytes)
            }
            .onFailure { _message.value = "Не удалось подготовить файл: ${it.readableMessage()}" }
    }

    /**
     * Файл собран и ждёт, когда человек укажет, куда его сохранить.
     *
     * Пустой `uri` означает, что человек отказался от сохранения — сообщать
     * об успехе тогда нельзя.
     */
    fun savePendingExport(uri: android.net.Uri?) {
        val pending = _pendingExport.value ?: return
        if (uri == null) {
            _pendingExport.value = null
            return
        }
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(pending.bytes)
                    it.flush()
                } ?: error("Не удалось создать файл")
            }
                .onSuccess {
                    _pendingExport.value = null
                    _message.value = "Файл ${pending.name} сохранён"
                }
                .onFailure {
                    _pendingExport.value = null
                    _message.value = "Файл не сохранён: ${it.readableMessage()}"
                }
        }
    }

    /** Разбор файла без записи в базу: человек сначала видит, что будет добавлено. */
    fun previewImport(uri: android.net.Uri, name: String?) {
        viewModelScope.launch {
            runCatching {
                val text = FileExchange.readText(getApplication(), uri)
                MeasurementIo.fromAny(text, name)
            }
                .onSuccess { rows -> _importPlan.value = buildPlan(rows) }
                .onFailure { _message.value = "Файл не прочитан: ${it.readableMessage()}" }
        }
    }

    private suspend fun buildPlan(rows: List<PortableMeasurement>): ImportPlan {
        val existing = db.measurementDao().allSinceOnce(0)
            .map { MeasurementIo.keyOf(PortableMeasurement(it.takenAt, it.sys, it.dia, it.pulse)) }
            .toSet()
        val knownTags = db.tagDao().allOnce().map { it.name.lowercase() }.toSet()
        val plan = MeasurementIo.plan(rows, existing)
        return plan.copy(tagsToCreate = plan.tagsToCreate.filter { it.lowercase() !in knownTags })
    }

    fun cancelImport() {
        _importPlan.value = null
    }

    /** Запись подготовленного плана в базу. */
    fun applyImport() {
        val plan = _importPlan.value ?: return
        if (plan.toAdd.isEmpty()) {
            _importPlan.value = null
            _message.value = "Новых замеров в файле нет"
            return
        }
        viewModelScope.launch {
            val before = db.tagDao().allOnce().size
            plan.toAdd.forEach { p ->
                val id = db.measurementDao().insert(
                    MeasurementEntity(
                        takenAt = p.takenAt,
                        day = MeasurementIo.dayOf(p.takenAt),
                        sys = p.sys,
                        dia = p.dia,
                        pulse = p.pulse,
                        arm = p.arm,
                        context = p.context,
                        valid = p.valid,
                        issues = p.issues,
                        note = p.note,
                        bucket = p.bucket,
                    )
                )
                applyTagNames(id, p.tags)
            }
            val created = db.tagDao().allOnce().size - before
            _importPlan.value = null
            _message.value = ImportSummary(
                added = plan.toAdd.size,
                duplicates = plan.duplicates,
                skipped = plan.skipped,
                tagsCreated = created,
            ).text
            // Статистика, база и корреляции считаются по всем замерам, поэтому
            // после импорта пересчёт обязателен.
            refresh()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Сообщение из экрана: например, когда отправка файла упала уже вне ViewModel. */
    fun notify(text: String) {
        _message.value = text
    }

    fun canScheduleExact(): Boolean = AlarmScheduler.canScheduleExact(getApplication())

    fun bootstrap() {
        Scheduler.scheduleMeasureHints(getApplication())
        viewModelScope.launch { Escalator(getApplication()).catchUp() }
        refresh()
    }

    private fun context(key: String) =
        ru.chronicnotebook.domain.Context.entries.firstOrNull { it.key == key } ?: ru.chronicnotebook.domain.Context.REST

    /** Причина ошибки словами, а не `java.lang.NullPointerException`. */
    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName

    private companion object {
        /**
         * Период автообновления на экране. Достаточно частый, чтобы приём,
         * отмеченный в уведомлении, и погода появлялись сразу, и не настолько
         * частый, чтобы каждые несколько секунд перечитывать базу и историю
         * погоды за 120 дней.
         */
        const val AUTO_REFRESH_MS = 15_000L
    }
}
