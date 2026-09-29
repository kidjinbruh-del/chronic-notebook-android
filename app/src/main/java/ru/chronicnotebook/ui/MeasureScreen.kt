package ru.chronicnotebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.chronicnotebook.domain.Context
import ru.chronicnotebook.domain.Level
import ru.chronicnotebook.domain.Protocol
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Новый замер", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = sys,
                onValueChange = { sys = it.filter(Char::isDigit).take(3) },
                label = { Text("Верхнее") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = dia,
                onValueChange = { dia = it.filter(Char::isDigit).take(3) },
                label = { Text("Нижнее") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = pulse,
                onValueChange = { pulse = it.filter(Char::isDigit).take(3) },
                label = { Text("Пульс, уд/мин") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Text("Состояние при замере", style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Context.entries.take(3).forEach { c ->
                FilterChip(
                    selected = ctx == c,
                    onClick = { ctx = c },
                    label = { Text(c.label) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Context.entries.drop(3).forEach { c ->
                FilterChip(
                    selected = ctx == c,
                    onClick = { ctx = c },
                    label = { Text(c.label) },
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
            val issues = Protocol.issues(sysValue, diaValue, ctx, rested, spoke, cuffOk)
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (level) {
                        Level.CRISIS, Level.HIGH -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${sysValue}/$diaValue — ${level.label}", style = MaterialTheme.typography.titleMedium)
                    Text(advice(level, sysValue, diaValue), style = MaterialTheme.typography.bodyMedium)
                    if (issues.isNotEmpty()) {
                        Text(
                            "Замер будет помечен: ${issues.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить замер") }

        Text(
            "Приложение не ставит диагноз и не меняет лечение. При давлении 180/120 и выше " +
                "с плохим самочувствием — скорая 103.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
