package ru.chronicnotebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.chronicnotebook.domain.Factor
import ru.chronicnotebook.domain.Level
import ru.chronicnotebook.domain.Stats
import ru.chronicnotebook.domain.classify
import ru.chronicnotebook.ui.theme.scaled

@Composable
fun StatsScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val allTags by vm.tags.collectAsStateWithLifecycle()
    val tagLinks by vm.tagLinks.collectAsStateWithLifecycle()
    val tagUsage by vm.tagUsage.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp.scaled()),
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled()),
    ) {
        item {
            Text("Связи и динамика", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            PressureChartCard(
                measurements = measurements,
                allTags = allTags,
                tagLinks = tagLinks,
            )
        }
        item {
            TagDictionaryCard(
                allTags = allTags,
                usage = tagUsage.associate { it.tagId to it.n },
                onDelete = { vm.deleteTag(it.id) },
            )
        }
        item { ExchangeCard(vm) }
        item { InfoCard("Личная база", state.baselineText) }
        item { InfoCard("Погода и давление", state.factorText) }
        item { InfoCard("Приём препаратов", state.adherenceText) }

        item {
            val valid = measurements.filter { it.valid }
            val crisis = valid.count { classify(it.sys, it.dia) == Level.CRISIS }
            val high = valid.count { classify(it.sys, it.dia) == Level.HIGH }
            InfoCard(
                title = "Пороги за 200 последних замеров",
                body = buildString {
                    if (valid.isEmpty()) {
                        append("Нет корректных замеров.")
                    } else {
                        append("САД средний %.0f, ДАД средний %.0f\n".format(
                            valid.map { it.sys }.average(),
                            valid.map { it.dia }.average(),
                        ))
                        Stats.stdev(valid.map { it.sys })?.let {
                            append("Разброс САД ±%.0f мм рт.ст.\n".format(it))
                        }
                        append("Кризисных: $crisis, повышенных: $high")
                    }
                },
            )
        }

        item {
            InfoCard(
                title = "Сводка",
                body = "Замеров в дневе: ${measurements.size}\nДней погоды в базе: ${weather.size}",
            )
        }
        item {
            FilledTonalButton(onClick = { vm.syncWeather() }, modifier = Modifier.fillMaxWidth()) {
                Text("Синхронизировать погоду")
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp.scaled()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
