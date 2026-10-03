package ru.chronicnotebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.chronicnotebook.domain.Context
import ru.chronicnotebook.domain.Level
import ru.chronicnotebook.domain.Protocol
import ru.chronicnotebook.domain.advice
import ru.chronicnotebook.domain.classify
import ru.chronicnotebook.ui.theme.scaled

@OptIn(ExperimentalLayoutApi::class)
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
    var selectedTagIds by remember { mutableStateOf(emptySet<Long>()) }
    var newTag by remember { mutableStateOf("") }
    var pendingTag by remember { mutableStateOf<String?>(null) }
    val allTags by vm.tags.collectAsStateWithLifecycle()

    // Словарь обновляется асинхронно. Когда свежая метка появилась, выбираем её:
    // иначе её пришлось бы искать вручную сразу после добавления.
    LaunchedEffect(pendingTag, allTags) {
        val name = pendingTag ?: return@LaunchedEffect
        allTags.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let {
            selectedTagIds = selectedTagIds + it.id
            pendingTag = null
        }
    }

    val sysValue = sys.toIntOrNull()
    val diaValue = dia.toIntOrNull()
    val valid = sysValue != null && diaValue != null &&
        sysValue in 60..260 && diaValue in 30..160
    // Пульс необязателен, но если введён — должен быть правдоподобным: раньше
    // 0 или 999 попадали в базу и в средние значения отчёта.
    val pulseValue = pulse.toIntOrNull()
    val pulseBad = pulse.isNotEmpty() && (pulseValue == null || pulseValue !in 30..220)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp.scaled()),
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled()),
    ) {
        Text("Новый замер", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = sys,
                onValueChange = { sys = it.filter(Char::isDigit).take(3) },
                label = { Text("Верхнее") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = dia,
                onValueChange = { dia = it.filter(Char::isDigit).take(3) },
                label = { Text("Нижнее") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = pulse,
                onValueChange = { pulse = it.filter(Char::isDigit).take(3) },
                label = { Text("Пульс, уд/мин") },
                singleLine = true,
                isError = pulseBad,
                supportingText = if (pulseBad) {
                    { Text("Ожидается 30–220") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        Text("Состояние при замере", style = MaterialTheme.typography.titleSmall)
        // Раньше чипы раскладывались по два жёстких ряда: на узком экране
        // «после кофе» и «после еды» сжимались в буквенную колонку.
        // FlowRow переносит их сам по месту.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Context.entries.forEach { c ->
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

        Text("Метки", style = MaterialTheme.typography.titleSmall)
        TagPicker(
            allTags = allTags,
            selectedIds = selectedTagIds,
            onToggle = { id ->
                selectedTagIds = if (id in selectedTagIds) selectedTagIds - id else selectedTagIds + id
            },
        )
        if (allTags.isEmpty()) {
            SuggestedTags { pendingTag = it; vm.createTag(it) }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                label = { Text("Своя метка") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    val clean = newTag.trim()
                    if (clean.isEmpty()) return@TextButton
                    val same = allTags.firstOrNull { it.name.equals(clean, ignoreCase = true) }
                    if (same != null) {
                        selectedTagIds = selectedTagIds + same.id
                    } else {
                        pendingTag = clean
                        vm.createTag(clean)
                    }
                    newTag = ""
                },
                enabled = newTag.trim().isNotEmpty(),
            ) { Text("Добавить") }
        }

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
                Column(Modifier.padding(16.dp.scaled()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                // `pendingTag` уже добавлен в словарь вызовом createTag, но поток
                // меток может ещё не обновиться. Передаём имя напрямую, иначе
                // свежая метка терялась бы при быстром сохранении.
                val names = (allTags.filter { it.id in selectedTagIds }.map { it.name } +
                    listOfNotNull(pendingTag?.trim()?.takeIf { it.isNotEmpty() })).distinct()
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
                    tags = names,
                )
                sys = ""; dia = ""; pulse = ""; note = ""; newTag = ""
                pendingTag = null
                selectedTagIds = emptySet()
                ctx = Context.REST
            },
            enabled = valid && !pulseBad,
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
