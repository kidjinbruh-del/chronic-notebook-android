package ru.chronicnotebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import ru.chronicnotebook.reminders.Notifications

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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Мои препараты", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Вносите только то, что уже назначил врач. Приложение не подбирает " +
                            "лечение и не меняет дозировки.",
                        style = MaterialTheme.typography.bodySmall,
                    )
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
            onSave = { name, dose, unit, withFood, prescribedBy, times ->
                val id = target.med?.id
                if (id == null) {
                    vm.addMed(name, "", dose, unit, withFood, times, prescribedBy)
                } else {
                    vm.updateMed(id, name, dose, unit, withFood, prescribedBy, times)
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
    var loaded by remember(med.id) { mutableStateOf(false) }

    // Слоты приёма подтягиваются из БД, чтобы карточка и форма
    // показывали одинаковое расписание.
    LaunchedEffect(med.id) {
        if (!loaded) {
            times = vm.timesOf(med.id)
            loaded = true
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        }
    }
}

@Composable
private fun PendingIntakes(vm: MainViewModel, meds: Map<Long, MedEntity>) {
    var rows by remember { mutableStateOf(emptyList<IntakeEntity>()) }
    LaunchedEffect(Unit) { rows = vm.pendingIntakes() }

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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.takeIntake(intake.id) }) { Text("Принял") }
                        TextButton2("Через 15 мин") { vm.snoozeIntake(intake.id, 15) }
                    }
                }
            }
        }
    }
}
