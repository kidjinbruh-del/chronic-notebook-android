package ru.chronicnotebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.chronicnotebook.data.IntakeEntity
import ru.chronicnotebook.reminders.Notifications
import java.time.Instant

private val SLOTS = listOf(0 to "00:00", 8 * 60 to "08:00", 12 * 60 + 30 to "12:30", 14 * 60 to "14:00", 18 * 60 to "18:00", 20 * 60 to "20:00", 22 * 60 to "22:00")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MedsScreen(vm: MainViewModel) {
    val meds by vm.meds.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var withFood by remember { mutableStateOf(false) }
    var prescribedBy by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(setOf<Int>()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Мои препараты", style = MaterialTheme.typography.titleLarge)
        Text(
            "Вносите только то, что уже назначил врач. Приложение не подбирает лечение.",
            style = MaterialTheme.typography.bodySmall,
        )

        meds.forEach { med ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(med.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(med.inn, listOf(med.dose, med.unit).joinToString(" ").trim())
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (med.prescribedBy.isNotBlank()) {
                        Text("назначил: ${med.prescribedBy}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton2("Деактивировать") { vm.deactivateMed(med.id) }
                }
            }
        }

        Text("Добавить препарат", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(dose, { dose = it }, label = { Text("Доза") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(unit, { unit = it }, label = { Text("Ед. измерения") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            prescribedBy, { prescribedBy = it },
            label = { Text("Кто назначил") }, modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(withFood, { withFood = it })
            Text("С едой")
        }

        Text("Время приёма", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SLOTS.forEach { (minutes, label) ->
                FilterChip(
                    selected = minutes in chosen,
                    onClick = {
                        chosen = if (minutes in chosen) chosen - minutes else chosen + minutes
                    },
                    label = { Text(label) },
                )
            }
        }

        Button(
            onClick = {
                vm.addMed(name.trim(), "", dose.trim(), unit.trim(), withFood, chosen.toList(), prescribedBy.trim())
                name = ""; dose = ""; unit = ""; prescribedBy = ""; chosen = emptySet()
            },
            enabled = name.isNotBlank(),
        ) { Text("Сохранить и включить напоминания") }

        Text("Ожидающие приёма", style = MaterialTheme.typography.titleMedium)
        val medsById = meds.associateBy { it.id }
        PendingIntakes(vm, medsById)

        Spacer()
    }
}

@Composable
private fun PendingIntakes(
    vm: MainViewModel,
    meds: Map<Long, ru.chronicnotebook.data.MedEntity>,
) {
    var rows by remember { mutableStateOf(emptyList<ru.chronicnotebook.data.IntakeEntity>()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(meds) {
        rows = vm.pendingIntakes()
    }
    if (rows.isEmpty()) {
        Text("Всё принято", style = MaterialTheme.typography.bodyMedium)
        return
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
                    TextButton2("Принял") { vm.takeIntake(intake.id) }
                    TextButton2("Через 15 мин") { vm.snoozeIntake(intake.id, 15) }
                }
            }
        }
    }
}

@Composable
private fun Spacer() {
    androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
}
