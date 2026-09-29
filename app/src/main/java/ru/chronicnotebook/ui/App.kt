package ru.chronicnotebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        topBar = { TopAppBar(title = { Text("Дневник давления") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.weatherText, style = MaterialTheme.typography.bodyMedium)
            Text(state.baselineText, style = MaterialTheme.typography.bodyMedium)
            Text(state.adherenceText, style = MaterialTheme.typography.bodyMedium)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Последние замеры", style = MaterialTheme.typography.titleMedium)
            items.take(20).forEach { m ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${m.sys}/${m.dia}${m.pulse?.let { ", $it уд/мин" } ?: ""}")
                    Text(fmt.format(Instant.ofEpochMilli(m.takenAt)), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (items.isEmpty()) Text("Замеров пока нет")
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Отчёт для врача", style = MaterialTheme.typography.titleMedium)
            TextButton2("Собрать отчёт") { showReport = true }
            if (showReport) {
                Text(vm.reportText(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    DiagnosticsCard()

    if (!vm.canScheduleExact()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Точные будильники выключены в системе. Напоминания могут приходить с задержкой.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun TextButton2(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(text) }
}
