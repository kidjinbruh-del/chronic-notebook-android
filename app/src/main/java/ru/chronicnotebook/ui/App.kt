package ru.chronicnotebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Дом", Icons.Filled.Favorite),
    ADD("Замер", Icons.Filled.Add),
    MEDS("Лекарства", Icons.Filled.Medication),
    STATS("Связи", Icons.Filled.Insights),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val vm: MainViewModel = viewModel()
    var tab by remember { mutableStateOf(Tab.HOME) }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.bootstrap() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Дневник давления") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, entry.title) },
                        label = { Text(entry.title) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(vm)
                Tab.ADD -> MeasureScreen(vm)
                Tab.MEDS -> MedsScreen(vm)
                Tab.STATS -> StatsScreen(vm)
            }
        }
    }
}

private val fmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())

@Composable
private fun HomeScreen(vm: MainViewModel) {
    val items by vm.measurements.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var showReport by remember { mutableStateOf(false) }

    // Раньше здесь не было прокрутки: при длинном отчёте и открытой диагностике
    // нижние карточки становились недоступны.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ReminderSetupCard() }
    item { SummaryCard(state.weatherText, state.baselineText, state.adherenceText) }
        item { ReportCard(vm, showReport) { showReport = it } }
        item { DiagnosticsCard() }

        item {
            Text(
                "Последние замеры",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (items.isEmpty()) {
            item {
                EmptyHint("Замеров пока нет. Нажмите «Замер» на нижней панели.")
            }
        } else {
            items(items.take(30), key = { it.id }) { m ->
                MeasurementRow(
                    sys = m.sys,
                    dia = m.dia,
                    pulse = m.pulse,
                    time = fmt.format(Instant.ofEpochMilli(m.takenAt)),
                    flagged = !m.valid,
                    note = m.issues,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(weather: String, baseline: String, adherence: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Сводка", style = MaterialTheme.typography.titleMedium)
            SummaryLine("Погода", weather)
            SummaryLine("Личная база", baseline)
            SummaryLine("Приём препаратов", adherence)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReportCard(vm: MainViewModel, showReport: Boolean, onToggle: (Boolean) -> Unit) {
    var text by remember(showReport) { mutableStateOf<String?>(null) }
    var loading by remember(showReport) { mutableStateOf(false) }

    // Раньше отчёт собирался прямо в теле composable: каждая перерисовка
    // заново пересчитывала статистику и корреляции на главном потоке.
    LaunchedEffect(showReport) {
        if (!showReport) return@LaunchedEffect
        loading = true
        text = withContext(Dispatchers.IO) { vm.buildReport() }
        loading = false
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Отчёт для врача", style = MaterialTheme.typography.titleMedium)
            FilledTonalButton(onClick = { onToggle(!showReport) }) {
                Text(if (showReport) "Скрыть отчёт" else "Собрать отчёт")
            }
            when {
                loading -> Text("Собираю отчёт…", style = MaterialTheme.typography.bodySmall)
                !showReport -> Unit
                else -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun MeasurementRow(
    sys: Int,
    dia: Int,
    pulse: Int?,
    time: String,
    flagged: Boolean,
    note: String = "",
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (flagged) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString {
                        append("$sys/$dia")
                        pulse?.let { append("  ·  $it уд/мин") }
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (flagged && note.isNotBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
fun TextButton2(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(text) }
}
