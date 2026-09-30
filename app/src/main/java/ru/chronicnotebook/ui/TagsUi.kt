package ru.chronicnotebook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.chronicnotebook.data.TagEntity

/** Цветная точка тега: имя обязано читаться даже на узком экране. */
@Composable
fun TagDot(colorArgb: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(Color(colorArgb)),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    allTags: List<TagEntity>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (allTags.isEmpty()) {
        Text(
            "Меток пока нет. Ниже можно добавить первую — например «после нагрузки».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        allTags.forEach { tag ->
            FilterChip(
                selected = tag.id in selectedIds,
                onClick = { onToggle(tag.id) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TagDot(tag.colorArgb)
                        Text(tag.name)
                    }
                },
            )
        }
    }
}

/**
 * Подсказки для пустого словаря.
 *
 * Они не ограничивают человека: любая своя метка ниже добавляется в словарь.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestedTags(onSuggest: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Частые метки",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SUGGESTED_TAGS.forEach { name ->
                FilterChip(
                    selected = false,
                    onClick = { onSuggest(name) },
                    label = { Text(name) },
                )
            }
        }
    }
}

/**
 * Добавление и снятие меток у одного замера.
 *
 * Новая метка сразу попадает в словарь, поэтому следующий замер выбирает её
 * из того же списка, а импорт файла подхватывает по имени.
 */
@Composable
fun TagPickerDialog(
    title: String,
    allTags: List<TagEntity>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newTag by remember { mutableStateOf("") }
    var pendingName by remember { mutableStateOf<String?>(null) }

    // После создания словарь приходит с задержкой. Когда имя появилось,
    // метка выбирается сама: иначе человеку пришлось бы искать её вручную.
    LaunchedEffect(pendingName, allTags) {
        val name = pendingName ?: return@LaunchedEffect
        val created = allTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (created != null) {
            onToggle(created.id)
            pendingName = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TagPicker(allTags = allTags, selectedIds = selectedIds, onToggle = onToggle)
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text("Новая метка") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        val clean = newTag.trim()
                        if (clean.isEmpty()) return@TextButton
                        // Уже существующую выбираем сразу, новую ждём из базы.
                        val same = allTags.firstOrNull { it.name.equals(clean, ignoreCase = true) }
                        if (same != null) {
                            onToggle(same.id)
                        } else {
                            pendingName = clean
                            onCreate(clean)
                        }
                        newTag = ""
                    },
                    enabled = newTag.trim().isNotEmpty(),
                ) { Text("Добавить метку") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

/**
 * Словарь меток: управление, а не создание.
 *
 * Создаётся метка там, где ставится, — в форме замера или в диалоге меток
 * карточки. Здесь словарь только чистится: видно, сколько замеров носит
 * каждую метку, и удаление спрашивается один раз. Отменить его нельзя, а метки
 * слетают со всех замеров, где они стояли.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagDictionaryCard(
    allTags: List<TagEntity>,
    usage: Map<Long, Int>,
    onDelete: (TagEntity) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<TagEntity?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Метки замеров", style = MaterialTheme.typography.titleMedium)
            Text(
                "Метки создаются при вводе замера — здесь видно, сколько замеров " +
                    "носит каждую, и лишние можно удалить.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (allTags.isEmpty()) {
                Text(
                    "Словарь пуст. Добавьте первую метку в форме замера — например «после нагрузки».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    allTags.forEach { tag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            TagDot(tag.colorArgb)
                            Text(
                                buildString {
                                    append(tag.name)
                                    usage[tag.id]?.let { append(" · $it") }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { confirmDelete = tag }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить метку?") },
            text = {
                Text("«${tag.name}» будет снята со всех замеров. Это действие нельзя отменить.")
            },
            confirmButton = {
                TextButton(onClick = { onDelete(tag); confirmDelete = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
        )
    }
}
