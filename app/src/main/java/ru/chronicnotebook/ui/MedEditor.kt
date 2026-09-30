package ru.chronicnotebook.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.chronicnotebook.data.MedEntity
import java.util.Calendar

/** Минуты от полуночи -> «ЧЧ:ММ». */
fun Int.toClock(): String = "%02d:%02d".format(this / 60, this % 60)

/** «ЧЧ:ММ» -> минуты от полуночи, либо null если ввод неверный. */
fun String.toMinutes(): Int? {
    val parts = trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimePickerField(
    times: List<Int>,
    onChange: (List<Int>) -> Unit,
    label: String = "Время приёма",
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
            "Нажмите на время, чтобы изменить. Любое количество слотов.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            times.sorted().forEach { minutes ->
                AssistChip(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                // distinct(): иначе подбор времени, уже стоящего
                                // в другом слоте, давал две одинаковые чипы.
                                onChange((times.minus(minutes) + hour * 60 + minute).distinct().sorted())
                            },
                            minutes / 60,
                            minutes % 60,
                            true,
                        ).show()
                    },
                    label = { Text(minutes.toClock()) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onChange(times - minutes) },
                            modifier = Modifier.padding(start = 2.dp),
                        ) {
                            Icon(Icons.Filled.Delete, "Убрать ${minutes.toClock()}", Modifier.padding(0.dp))
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
            FilledTonalButton(onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onChange((times + hour * 60 + minute).distinct().sorted())
                    },
                    nowHour(),
                    nowMinute(),
                    true,
                ).show()
            }) {
                Icon(Icons.Filled.Add, null)
                Text("  Добавить время")
            }
        }
    }
}

private fun nowHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
private fun nowMinute(): Int = Calendar.getInstance().get(Calendar.MINUTE)

@Composable
fun MedEditor(
    initial: MedEntity?,
    initialTimes: List<Int>,
    onSave: (name: String, dose: String, unit: String, withFood: Boolean, prescribedBy: String, times: List<Int>) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var dose by remember { mutableStateOf(initial?.dose.orEmpty()) }
    var unit by remember { mutableStateOf(initial?.unit.orEmpty()) }
    var withFood by remember { mutableStateOf(initial?.withFood ?: false) }
    var prescribedBy by remember { mutableStateOf(initial?.prescribedBy.orEmpty()) }
    var times by remember { mutableStateOf(initialTimes) }

    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Название") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = dose,
                onValueChange = { dose = it },
                label = { Text("Доза") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Ед.") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = prescribedBy,
            onValueChange = { prescribedBy = it },
            label = { Text("Кто назначил") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = withFood, onCheckedChange = { withFood = it })
            Text("С едой")
        }
        TimePickerField(times = times, onChange = { times = it })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(name.trim(), dose.trim(), unit.trim(), withFood, prescribedBy.trim(), times)
                },
                enabled = name.isNotBlank() && times.isNotEmpty(),
            ) { Text(if (initial == null) "Добавить" else "Сохранить") }
            TextButton(onClick = onCancel) { Text("Отмена") }
            if (initial != null && onDelete != null) {
                TextButton(onClick = onDelete) { Text("Удалить") }
            }
        }
    }
}

@Composable
fun MedEditorDialog(
    initial: MedEntity?,
    initialTimes: List<Int>,
    onSave: (name: String, dose: String, unit: String, withFood: Boolean, prescribedBy: String, times: List<Int>) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый препарат" else "Редактирование") },
        text = {
            MedEditor(
                initial = initial,
                initialTimes = initialTimes,
                onSave = onSave,
                onDelete = onDelete,
                onCancel = onDismiss,
            )
        },
        confirmButton = {},
    )
}
