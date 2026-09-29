package ru.chronicnotebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.chronicnotebook.domain.Context
import ru.chronicnotebook.domain.Level
import ru.chronicnotebook.domain.advice
import ru.chronicnotebook.domain.classify

@Composable
fun MeasureScreen(vm: MainViewModel) {
    var sys by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }
    var ctx by remember { mutableStateOf(Context.REST) }
    var rested by remember { mutableStateOf(true) }
    var spoke by remember { mutableStateOf(false) }
    var cuffOk by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }

    val sysValue = sys.toIntOrNull()
    val diaValue = dia.toIntOrNull()
    val valid = sysValue != null && diaValue != null &&
        sysValue in 60..260 && diaValue in 30..160

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Новый замер", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = sys,
                onValueChange = { sys = it.filter(Char::isDigit).take(3) },
                label = { Text("Систолическое") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = dia,
                onValueChange = { dia = it.filter(Char::isDigit).take(3) },
                label = { Text("Диастолическое") },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = pulse,
            onValueChange = { pulse = it.filter(Char::isDigit).take(3) },
            label = { Text("Пульс, уд/мин (необязательно)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Условия замера", style = MaterialTheme.typography.titleMedium)
        Row {
            Context.entries.forEach { c ->
                FilterChip(
                    selected = ctx == c,
                    onClick = { ctx = c },
                    label = { Text(c.label) },
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }

        CheckRow("Отдыхал 5 минут", rested) { rested = it }
        CheckRow("Не разговаривал", !spoke) { spoke = !it }
        CheckRow("Манжета подобрана", cuffOk) { cuffOk = it }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Заметка") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (valid) {
            val level = classify(sysValue!!, diaValue!!)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${sysValue}/$diaValue — ${level.label}")
                    Text(advice(level, sysValue, diaValue), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Button(
            onClick = {
                vm.addMeasurement(
                    sys = sysValue ?: return@Button,
                    dia = diaValue ?: return@Button,
                    pulse = pulse.toIntOrNull(),
                    contextKey = ctx.key,
                    rested = rested,
                    spoke = spoke,
                    cuffOk = cuffOk,
                    note = note,
                    issued = false,
                )
                sys = ""; dia = ""; pulse = ""; note = ""
                ctx = Context.REST
            },
            enabled = valid,
        ) { Text("Сохранить замер") }

        Text(
            "Приложение не ставит диагноз и не меняет лечение. При кризисе 180/120 и выше " +
                "с плохим самочувствием — скорая 103.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer()
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
private fun Spacer() {
    androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
}
