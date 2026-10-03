package ru.chronicnotebook.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Оформление приложения выбирается пользователем: палитра, режим (тёмная /
 * светлая / как в системе), шрифт, плотность и градиенты.
 *
 * Раньше внешний вид был жёстко зашит в [ChronicTheme]: тёплый чёрный с янтарём.
 * Теперь это лишь первый вариант из четырёх — остальные такие же аккуратные,
 * но с другим характером: сталь, морская гладь, бумага.
 */
enum class Palette(val id: String, val title: String, val dot: Int) {
    ESPRESSO("espresso", "Эспрессо", 0xFFEFA94A.toInt()),
    GRAPHITE("graphite", "Графит", 0xFFA8D84A.toInt()),
    TIDE("tide", "Прилив", 0xFF4ED9C4.toInt()),
    PAPER("paper", "Бумага", 0xFFE08A7A.toInt()),
}

enum class Mode(val id: String, val title: String) {
    AUTO("auto", "Как в системе"),
    DARK("dark", "Тёмная"),
    LIGHT("light", "Светлая"),
}

enum class FontStyle(val id: String, val title: String) {
    SANS("sans", "Обычный"),
    SERIF("serif", "С засечками"),
    MONO("mono", "Моноширинный"),
}

enum class Density(val id: String, val title: String, val scale: Float) {
    COMFORTABLE("comfortable", "Обычная", 1.0f),
    COMPACT("compact", "Компактная", 0.9f),
}

data class Design(
    val palette: Palette = Palette.ESPRESSO,
    val mode: Mode = Mode.DARK,
    val font: FontStyle = FontStyle.SANS,
    val density: Density = Density.COMFORTABLE,
    val gradients: Boolean = true,
)

/**
 * Хранение выбора оформления. [design] — observable-состояние: смена палитры
 * перерисовывает приложение целиком, без перезапуска.
 */
object DesignStore {
    private const val PREFS = "chronic_design"
    private var ctx: Context? = null

    var design by mutableStateOf(Design())
        private set

    fun install(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        design = read(ctx!!)
    }

    fun update(transform: (Design) -> Design) {
        design = transform(design)
        ctx?.let { write(it, design) }
    }

    private fun read(c: Context): Design {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = Design()
        return Design(
            palette = Palette.entries.firstOrNull { it.id == p.getString("palette", d.palette.id) } ?: d.palette,
            mode = Mode.entries.firstOrNull { it.id == p.getString("mode", d.mode.name) } ?: d.mode,
            font = FontStyle.entries.firstOrNull { it.id == p.getString("font", d.font.id) } ?: d.font,
            density = Density.entries.firstOrNull { it.id == p.getString("density", d.density.id) } ?: d.density,
            gradients = p.getBoolean("gradients", d.gradients),
        )
    }

    private fun write(c: Context, d: Design) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("palette", d.palette.id)
            .putString("mode", d.mode.id)
            .putString("font", d.font.id)
            .putString("density", d.density.id)
            .putBoolean("gradients", d.gradients)
            .apply()
    }
}