package ru.chronicnotebook.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.chronicnotebook.data.MeasurementIo
import ru.chronicnotebook.io.FileExchange
import ru.chronicnotebook.io.ReportPdf
import ru.chronicnotebook.io.ShareFiles
import ru.chronicnotebook.io.displayName

/**
 * Файлы для врача и для себя.
 *
 * Сохранение всегда идёт через системный выбор места: приложение не лезет в
 * память само и не требует разрешений. Импорт сначала показывает план, а уже
 * потом пишет в базу, поэтому чужой файл нельзя применить вслепую.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExchangeCard(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingExport by vm.pendingExport.collectAsStateWithLifecycle()
    val importPlan by vm.importPlan.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf<String?>(null) }
    val locked = busy != null || pendingExport != null

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> vm.savePendingExport(uri) }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "import"
            try {
                vm.previewImport(uri, displayName(context, uri))
            } finally {
                busy = null
            }
        }
    }
    LaunchedEffect(pendingExport) {
        pendingExport?.let { saveLauncher.launch(it.name) }
    }

    fun prepare(key: String, name: String, mime: String, payload: suspend () -> ByteArray) {
        scope.launch {
            busy = key
            try {
                vm.prepareExport(name, mime, payload)
            } finally {
                busy = null
            }
        }
    }

    fun share(key: String, work: suspend () -> Unit) {
        scope.launch {
            busy = key
            try {
                work()
            } catch (e: Exception) {
                vm.notify("Не удалось отправить: ${e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName}")
            } finally {
                busy = null
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Файлы отчёта и замеров", style = MaterialTheme.typography.titleMedium)
            Text(
                "PDF — врачу. JSON и CSV — себе: копию перед обновлением и перенос " +
                    "старых замеров. Импорт показывает план до записи.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    enabled = !locked,
                    onClick = {
                        prepare(
                            key = "pdf",
                            name = FileExchange.fileName("Отчёт", "pdf"),
                            mime = "application/pdf",
                        ) {
                            val text = withContext(Dispatchers.IO) { vm.buildReport() }
                            withContext(Dispatchers.Default) { ReportPdf.render(text) }
                        }
                    },
                ) { Text("Сохранить PDF") }
                FilledTonalButton(
                    enabled = !locked,
                    onClick = {
                        prepare(
                            key = "report-csv",
                            name = FileExchange.fileName("Отчёт-данные", "csv"),
                            mime = "text/csv",
                        ) {
                            withContext(Dispatchers.IO) {
                                MeasurementIo.toCsv(vm.portableRows(days = 30)).toByteArray(Charsets.UTF_8)
                            }
                        }
                    },
                ) { Text("Сохранить CSV") }
                FilledTonalButton(
                    enabled = !locked,
                    onClick = {
                        share("share-pdf") {
                            val text = withContext(Dispatchers.IO) { vm.buildReport() }
                            val bytes = withContext(Dispatchers.Default) { ReportPdf.render(text) }
                            ShareFiles.share(
                                context,
                                FileExchange.fileName("Отчёт", "pdf"),
                                "application/pdf",
                                bytes,
                            )
                        }
                    },
                ) { Text("Поделиться PDF") }
                FilledTonalButton(
                    enabled = !locked,
                    onClick = {
                        share("share-text") {
                            val text = withContext(Dispatchers.IO) { vm.buildReport() }
                            ShareFiles.shareText(context, "Отчёт дневника давления", text)
                        }
                    },
                ) { Text("Поделиться текстом") }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    enabled = !locked,
                    onClick = {
                        prepare(
                            key = "json",
                            name = FileExchange.fileName("Замеры", "json"),
                            mime = "application/json",
                        ) {
                            withContext(Dispatchers.IO) {
                                MeasurementIo.toJson(vm.portableRows(days = null)).toByteArray(Charsets.UTF_8)
                            }
                        }
                    },
                ) { Text("Выгрузить JSON") }
                FilledTonalButton(
                    enabled = !locked,
                    onClick = {
                        prepare(
                            key = "csv",
                            name = FileExchange.fileName("Замеры", "csv"),
                            mime = "text/csv",
                        ) {
                            withContext(Dispatchers.IO) {
                                MeasurementIo.toCsv(vm.portableRows(days = null)).toByteArray(Charsets.UTF_8)
                            }
                        }
                    },
                ) { Text("Выгрузить CSV") }
                FilledTonalButton(
                    enabled = !locked,
                    onClick = { openLauncher.launch(arrayOf("*/*")) },
                ) { Text("Загрузить замеры") }
            }
        }
    }

    importPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("Импорт замеров") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Новых замеров: ${plan.toAdd.size}")
                    Text("Уже есть в дневнике: ${plan.duplicates}")
                    if (plan.tagsToCreate.isNotEmpty()) {
                        Text("Новые метки: ${plan.tagsToCreate.joinToString(", ")}")
                    }
                    Text(
                        "Существующие замеры не меняются. Повторный импорт того же " +
                            "файла ничего не добавит.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = vm::applyImport) {
                    Text(if (plan.toAdd.isNotEmpty()) "Добавить ${plan.toAdd.size}" else "Понятно")
                }
            },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text("Отмена") } },
        )
    }
}
