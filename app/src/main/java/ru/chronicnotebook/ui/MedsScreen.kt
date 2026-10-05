package ru.chronicnotebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import java.time.LocalDate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.chronicnotebook.data.IntakeEntity
import ru.chronicnotebook.data.MedEntity
import ru.chronicnotebook.domain.Stock
import ru.chronicnotebook.domain.StockState
import ru.chronicnotebook.reminders.Notifications
import ru.chronicnotebook.ui.theme.scaled

private data class Editing(
    val med: MedEntity?,
    val times: List<Int>,
)

@Composable
fun MedsScreen(vm: MainViewModel) {
    val meds by vm.meds.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp.scaled()),
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled()),
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp.scaled()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Мои препараты", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Вносите только то, что уже назначил врач. Приложение не подбирает " +
                            "лечение и не меняет дозировки.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ExpirySummary(meds)
                    FilledTonalButton(onClick = { editing = Editing(null, emptyList()) }) {
                        Icon(Icons.Filled.Add, null)
                        Text("  Добавить препарат")
                    }
                }
            }
        }

        if (meds.isEmpty()) {
            item { EmptyHint("Список пуст. Добавьте назначенный препарат и время приёма.") }
        } else {
            items(meds, key = { it.id }) { med ->
                MedCard(med = med, vm = vm, onEdit = { editing = Editing(med, it) })
            }
        }

        item { PendingIntakes(vm, meds.associateBy { it.id }) }
    }

    editing?.let { target ->
        MedEditorDialog(
            initial = target.med,
            initialTimes = target.times,
            onSave = { d ->
                val id = target.med?.id
                if (id == null) {
                    vm.addMed(
                        name = d.name, inn = "", dose = d.dose, unit = d.unit,
                        withFood = d.withFood, times = d.times, prescribedBy = d.prescribedBy,
                        expiresOn = d.expiresOn, stock = d.stock, stockUnit = d.stockUnit,
                    )
                } else {
                    vm.updateMed(
                        id, d.name, d.dose, d.unit, d.withFood, d.prescribedBy, d.times,
                        d.expiresOn, d.stock, d.stockUnit,
                    )
                }
                editing = null
            },
            onDelete = target.med?.let { med ->
                {
                    vm.deactivateMed(med.id)
                    editing = null
                }
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MedCard(
    med: MedEntity,
    vm: MainViewModel,
    onEdit: (List<Int>) -> Unit,
) {
    var times by remember(med.id) { mutableStateOf(emptyList<Int>()) }
    // Каждый пересчёт (refreshNow поднимает tick) перечитывает слоты заново.
    // Раньше было LaunchedEffect(med.id), поэтому после правки расписания
    // карточка продолжала показывать старое время, хотя напоминание
    // уже было пересчитано на новое.
    val tick by vm.tick.collectAsStateWithLifecycle()
    LaunchedEffect(med.id, tick) {
        times = vm.timesOf(med.id)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp.scaled()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(med.name, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onEdit(times) }) {
                    Icon(Icons.Filled.Edit, "Редактировать ${med.name}")
                }
            }
            val details = listOf(med.inn, listOf(med.dose, med.unit).joinToString(" ").trim())
                .filter { it.isNotBlank() }
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            if (med.withFood) {
                Text("С едой", style = MaterialTheme.typography.bodySmall)
            }
            if (med.prescribedBy.isNotBlank()) {
                Text("назначил: ${med.prescribedBy}", style = MaterialTheme.typography.bodySmall)
            }
            if (times.isNotEmpty()) {
                Text(
                    "Приём: ${times.joinToString(", ") { it.toClock() }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            MedStockRow(med)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingIntakes(vm: MainViewModel, meds: Map<Long, MedEntity>) {
    var rows by remember { mutableStateOf(emptyList<IntakeEntity>()) }
    val tick by vm.tick.collectAsStateWithLifecycle()
    LaunchedEffect(tick) { rows = vm.pendingIntakes() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ожидают отметки", style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) {
            Text("Всё принято", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        rows.forEach { intake ->
            val med = meds[intake.medId]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${med?.name ?: "препарат"} — ${Notifications.humanTime(intake.dueAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.takeIntake(intake.id) }) { Text("Принял") }
                        TextButton2("Через 15 мин") { vm.snoozeIntake(intake.id, 15) }
                    }
                }
            }
        }
    }
}

/**
 * Сводка по срокам годности: сколько просрочено и сколько скоро испортится.
 *
 * Считается по всем активным препаратам, а не по видимой части списка, —
 * так же, как отчёт для врача. Смысл в том, чтобы человек узнал о
 * просроченном препарате, открыв экрон, а не долистав до нужной карточки.
 */
@Composable
private fun ExpirySummary(meds: List<MedEntity>) {
    if (meds.isEmpty()) return
    val today = LocalDate.now()
    val expired = meds.count { Stock.state(it.expiresOn, today) == StockState.EXPIRED }
    val soon = meds.count { Stock.state(it.expiresOn, today) == StockState.SOON }
    if (expired == 0 && soon == 0) return

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (expired > 0) {
            Text(
                "Просрочено: $expired — применять нельзя, выбросить",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (soon > 0) {
            Text(
                "Истекает срок: $soon — скоро выбросить",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/**
 * Строка срока годности и остатка под расписанием приёма.
 *
 * Показывается только если человек это заполнял: у большинства препаратов
 * срок не указан, и пустая строка «срок не указан» в каждой карточке только
 * шумит. Исключение — просроченное: о нём сообщать нужно всегда, если дата
 * вообще проставлена.
 */
@Composable
private fun MedStockRow(med: MedEntity) {
    val today = LocalDate.now()
    val state = Stock.state(med.expiresOn, today)
    val stock = Stock.stockLabel(med.stock, med.stockUnit)

    if (state == StockState.NONE && stock == null) return

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (state != StockState.NONE) {
            Text(
                "Срок: ${Stock.human(med.expiresOn)} · ${Stock.expiryLabel(med.expiresOn, today)}",
                style = MaterialTheme.typography.bodySmall,
                color = when (state) {
                    StockState.EXPIRED -> MaterialTheme.colorScheme.error
                    StockState.SOON -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (stock != null) {
            Text(
                "Осталось: $stock",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
