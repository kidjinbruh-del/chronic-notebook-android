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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.chronicnotebook.ui.theme.Density
import ru.chronicnotebook.ui.theme.DesignStore
import ru.chronicnotebook.ui.theme.FontStyle
import ru.chronicnotebook.ui.theme.Mode
import ru.chronicnotebook.ui.theme.Palette
import ru.chronicnotebook.ui.theme.scaled

/**
 * Карточка «Оформление»: палитра, режим, шрифт, плотность, градиенты.
 *
 * Группы вариантов laid out через FlowRow: жёсткие ряды на узком экране
 * сжимали последние чипы в буквенную колонку.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignCard() {
    val design = DesignStore.design

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp.scaled()),
            verticalArrangement = Arrangement.spacedBy(10.dp.scaled()),
        ) {
            Text("Оформление", style = MaterialTheme.typography.titleMedium)
            Text(
                "Выбор сохраняется и применяется сразу.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DesignGroup("Палитра") {
                Palette.entries.forEach { p ->
                    FilterChip(
                        selected = design.palette == p,
                        onClick = { DesignStore.update { it.copy(palette = p) } },
                        label = { Text(p.title) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(Color(p.dot), CircleShape)
                            )
                        },
                    )
                }
            }

            DesignGroup("Режим") {
                Mode.entries.forEach { m ->
                    FilterChip(
                        selected = design.mode == m,
                        onClick = { DesignStore.update { it.copy(mode = m) } },
                        label = { Text(m.title) },
                    )
                }
            }

            DesignGroup("Шрифт") {
                FontStyle.entries.forEach { f ->
                    FilterChip(
                        selected = design.font == f,
                        onClick = { DesignStore.update { it.copy(font = f) } },
                        label = { Text(f.title) },
                    )
                }
            }

            DesignGroup("Плотность") {
                Density.entries.forEach { d ->
                    FilterChip(
                        selected = design.density == d,
                        onClick = { DesignStore.update { it.copy(density = d) } },
                        label = { Text(d.title) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Градиенты",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = design.gradients,
                    onCheckedChange = { on -> DesignStore.update { it.copy(gradients = on) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesignGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}