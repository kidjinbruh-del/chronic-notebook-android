package ru.chronicnotebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.chronicnotebook.data.MedEntity
import ru.chronicnotebook.domain.Stock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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

/**
 * Добавляет или заменяет слот приёма.
 *
 * Скобки вокруг `hour * 60 + minute` обязательны: без них Kotlin разбирает
 * `times + hour * 60 + minute` как `(times + hour * 60) + minute`, и в список
 * попадают два числа (720 и 45) вместо одного 765 — выбранные 12:45
 * превращались в два слота «12:00» и «00:45».
 *
 * @param target слот, который заменяем, либо null если добавляем новый.
 */
fun slotTimes(times: List<Int>, target: Int?, hour: Int, minute: Int): List<Int> {
    val base = if (target == null) times else times - target
    val value = hour * 60 + minute
    return (base + value).distinct().sorted()
}

/**
 * Выбор времени приёма.
 *
 * Раньше здесь вызывался системный `android.app.TimePickerDialog`. На части
 * прошивок (HiOS) он возвращал час с нулями минут: выбранные 12:45 сохранялись
 * как 12:00, и человек получал напоминание на час раньше назначенного. Причина
 * в том, что системный диалог в режиме ввода с клавиатуры отдаёт промежуточное
 * значение, где минуты ещё не заданы. Свой диалог на Material 3 берёт и час, и
 * минуты из состояния пикера в момент подтверждения, поэтому значение всегда
 * полное: 00:00–23:59 с точностью до минуты.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeEditDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = true,
    )
    // Циферблат Material 3 даёт минуты с шагом 5, а назначение может быть
    // на любую минуту. Поэтому рядом с ним есть ручной ввод: 12:43 из него
    // берётся так же надёжно, как 12:45 с циферблата.
    var inputMode by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Время приёма") },
        text = {
            // На экранах 360dp содержимое не помещается вместе с кнопками,
            // поэтому оно прокручивается, а не обрезается.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (inputMode) {
                    TimeInput(state = state)
                } else {
                    TimePicker(state = state)
                }
                TextButton(onClick = { inputMode = !inputMode }) {
                    Text(if (inputMode) "Выбрать на циферблате" else "Ввести время вручную")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Готово") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimePickerField(
    times: List<Int>,
    onChange: (List<Int>) -> Unit,
    label: String = "Время приёма",
) {
    // Какой слот правим и в каком режиме открыт диалог.
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }

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
                    onClick = { editing = minutes },
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
            FilledTonalButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, null)
                Text("  Добавить время")
            }
        }
    }

    editing?.let { target ->
        TimeEditDialog(
            initialHour = target / 60,
            initialMinute = target % 60,
            onDismiss = { editing = null },
            onConfirm = { hour, minute ->
                // distinct(): иначе подбор времени, уже стоящего
                // в другом слоте, давал две одинаковые чипы.
                onChange(slotTimes(times, target, hour, minute))
                editing = null
            },
        )
    }

    if (adding) {
        TimeEditDialog(
            initialHour = nowHour(),
            initialMinute = nowMinute(),
            onDismiss = { adding = false },
            onConfirm = { hour, minute ->
                onChange(slotTimes(times, null, hour, minute))
                adding = false
            },
        )
    }
}

private fun nowHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
private fun nowMinute(): Int = Calendar.getInstance().get(Calendar.MINUTE)

/**
 * Черновик карточки препарата.
 *
 * Раньше `onSave` принимал шесть отдельных параметров, и каждое новое поле
 * — срок годности, остаток — добавлялось восьмым и девятым позиционным.
 * Их порядок нигде не подписан, а ошибка в вызове компилировалась. Черновик
 * читается по именам полей и не требует переписывать все вызовы.
 */
data class MedDraft(
    val name: String = "",
    val dose: String = "",
    val unit: String = "",
    val withFood: Boolean = false,
    val prescribedBy: String = "",
    val times: List<Int> = emptyList(),
    val expiresOn: String? = null,
    val stock: Int = 0,
    val stockUnit: String = "шт",
)

/** Единицы остатка. Чаще всего счёт идёт на штуки или упаковки. */
private val STOCK_UNITS = listOf("шт", "уп", "мл", "г")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedEditor(
    initial: MedEntity?,
    initialTimes: List<Int>,
    onSave: (MedDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var dose by remember { mutableStateOf(initial?.dose.orEmpty()) }
    var unit by remember { mutableStateOf(initial?.unit.orEmpty()) }
    var withFood by remember { mutableStateOf(initial?.withFood ?: false) }
    var prescribedBy by remember { mutableStateOf(initial?.prescribedBy.orEmpty()) }
    var times by remember { mutableStateOf(initialTimes) }
    var expiresOn by remember { mutableStateOf(initial?.expiresOn) }
    var stock by remember { mutableStateOf(if ((initial?.stock ?: 0) > 0) initial!!.stock.toString() else "") }
    var stockUnit by remember { mutableStateOf(initial?.stockUnit?.takeIf { it.isNotBlank() } ?: "шт") }
    var pickingDate by remember { mutableStateOf(false) }

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

        // Срок годности упаковки и остаток — то же, что в «Хранилище
        // препаратов». Здесь они нужны для одного: увидеть просроченное,
        // не открывая второе приложение, потому что просроченное лекарство
        // внешне ничем не отличается от обычного.
        Text("Срок годности и остаток", style = MaterialTheme.typography.titleSmall)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { pickingDate = true }) {
                Text(if (expiresOn == null) "Выбрать дату" else Stock.human(expiresOn))
            }
            if (expiresOn != null) {
                TextButton(onClick = { expiresOn = null }) { Text("Убрать") }
            }
        }
        OutlinedTextField(
            value = stock,
            onValueChange = { stock = it.filter { ch -> ch.isDigit() }.take(4) },
            label = { Text("Осталось") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            STOCK_UNITS.forEach { u ->
                AssistChip(
                    onClick = { stockUnit = u },
                    label = { Text(u) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (stockUnit == u) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                )
            }
        }

        TimePickerField(times = times, onChange = { times = it })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        MedDraft(
                            name = name.trim(),
                            dose = dose.trim(),
                            unit = unit.trim(),
                            withFood = withFood,
                            prescribedBy = prescribedBy.trim(),
                            times = times,
                            expiresOn = expiresOn,
                            stock = stock.toIntOrNull() ?: 0,
                            stockUnit = stockUnit,
                        ),
                    )
                },
                enabled = name.isNotBlank() && times.isNotEmpty(),
            ) { Text(if (initial == null) "Добавить" else "Сохранить") }
            TextButton(onClick = onCancel) { Text("Отмена") }
        }
        // «Удалить» — на своей строке. В одном ряду с «Добавить» и «Отмена»
        // он не помещался и переносился посередине слова: «Удал / ить».
        if (initial != null && onDelete != null) {
            TextButton(onClick = onDelete) { Text("Удалить препарат") }
        }
    }

    if (pickingDate) {
        ExpiryDateDialog(
            initial = expiresOn,
            onDismiss = { pickingDate = false },
            onConfirm = {
                expiresOn = it
                pickingDate = false
            },
        )
    }
}

/**
 * Выбор даты срока годности.
 *
 * Дата выбирается пикером, а не вводится текстом: в списке препаратов даты
 * сравниваются между собой, и «через месяц» или «12.26» рядом с «24.09.2026»
 * сравнить невозможно. Заодно исчезает целый класс ошибок ввода.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDateDialog(
    initial: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val start = Stock.parse(initial) ?: LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Срок годности") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    onConfirm(
                        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            .let { Stock.toIso(it) },
                    )
                } else {
                    onDismiss()
                }
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun MedEditorDialog(
    initial: MedEntity?,
    initialTimes: List<Int>,
    onSave: (MedDraft) -> Unit,
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
