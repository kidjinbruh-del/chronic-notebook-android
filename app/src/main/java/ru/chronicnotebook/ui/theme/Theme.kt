package ru.chronicnotebook.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Палитры. Каждая — пара «тёмная / светлая», чтобы переключатель режима
 * работал для любой из них.
 *
 * Контейнеры заданы явно: у них собственные холодные значения по умолчанию,
 * и без них карточки становились серыми на тёплом фоне.
 */
private object Espresso {
    val dark = Scheme(
        bg = 0xFF14100A, surface = 0xFF1D1710, lowest = 0xFF0C0906, low = 0xFF181309,
        container = 0xFF201910, high = 0xFF2A2114, highest = 0xFF362B1A, variant = 0xFF2C2417,
        on = 0xFFF4EAD9, muted = 0xFFCBB89F, primary = 0xFFEFA94A, onPrimary = 0xFF2A1A05,
        primaryContainer = 0xFF4A2C0E, onPrimaryContainer = 0xFFFFDDAE, secondary = 0xFFD08A4E,
        onSecondary = 0xFF2A1A08, secondaryContainer = 0xFF3A2A18, onSecondaryContainer = 0xFFF2D3AC,
        tertiary = 0xFFB7A05E, onTertiary = 0xFF2E2708, tertiaryContainer = 0xFF3A3018,
        onTertiaryContainer = 0xFFE4D6A8, outline = 0xFF8A7660, outlineVariant = 0xFF3A3126,
        error = 0xFFF0978A, onError = 0xFF4A0F0A, errorContainer = 0xFF5A1B12, onErrorContainer = 0xFFFFD9D2,
        inverse = 0xFFF4EAD9, onInverse = 0xFF2A2114, inverseSurface = 0xFF322A1C,
        onInverseSurface = 0xFFEFE3D1, scrim = 0xFF000000,
    )
    val light = Scheme(
        bg = 0xFFFAF4E8, surface = 0xFFFFFDF8, lowest = 0xFFFFFFFF, low = 0xFFF5EEE0,
        container = 0xFFEFE6D4, high = 0xFFE9DECA, highest = 0xFFE2D5BC, variant = 0xFFE7D8BE,
        on = 0xFF241A0F, muted = 0xFF6B5A44, primary = 0xFF8A5A17, onPrimary = 0xFFFFFFFF,
        primaryContainer = 0xFFFBE0B4, onPrimaryContainer = 0xFF3B2506, secondary = 0xFFA96C2A,
        onSecondary = 0xFFFFFFFF, secondaryContainer = 0xFFF6DCC0, onSecondaryContainer = 0xFF3E2409,
        tertiary = 0xFF7C6A33, onTertiary = 0xFFFFFFFF, tertiaryContainer = 0xFFEBE2BE,
        onTertiaryContainer = 0xFF2C2607, outline = 0xFFA8967A, outlineVariant = 0xFFDCCFB6,
        error = 0xFFB3261E, onError = 0xFFFFFFFF, errorContainer = 0xFFF9DEDC, onErrorContainer = 0xFF410E0B,
        inverse = 0xFF322A1C, onInverse = 0xFFEFE3D1, inverseSurface = 0xFFF4EAD9,
        onInverseSurface = 0xFF2A2114, scrim = 0xFF000000,
    )
}

/** Сталь и лайм: холодный рабочий фон, зелёный сигнал «в норме». */
private object Graphite {
    val dark = Scheme(
        bg = 0xFF0E1113, surface = 0xFF171B1E, lowest = 0xFF0A0D0F, low = 0xFF121619,
        container = 0xFF1B2024, high = 0xFF232830, highest = 0xFF2C323A, variant = 0xFF242A2F,
        on = 0xFFE8EDF0, muted = 0xFF9AA5AE, primary = 0xFFA8D84A, onPrimary = 0xFF1A2405,
        primaryContainer = 0xFF2E3F10, onPrimaryContainer = 0xFFDDF5A8, secondary = 0xFF7FB2C4,
        onSecondary = 0xFF0B2129, secondaryContainer = 0xFF173239, onSecondaryContainer = 0xFFBFE6F2,
        tertiary = 0xFFC7B27E, onTertiary = 0xFF2A2209, tertiaryContainer = 0xFF3A3018,
        onTertiaryContainer = 0xFFEDE0B6, outline = 0xFF6E7982, outlineVariant = 0xFF2E353C,
        error = 0xFFE08080, onError = 0xFF450F0F, errorContainer = 0xFF5A2222, onErrorContainer = 0xFFFFDAD7,
        inverse = 0xFFE8EDF0, onInverse = 0xFF232830, inverseSurface = 0xFF2C323A,
        onInverseSurface = 0xFFDFE5E9, scrim = 0xFF000000,
    )
    val light = Scheme(
        bg = 0xFFF2F5F6, surface = 0xFFFFFFFF, lowest = 0xFFFFFFFF, low = 0xFFF7F9FA,
        container = 0xFFEBEFF1, high = 0xFFE3E8EB, highest = 0xFFDCE2E5, variant = 0xFFE1E7EA,
        on = 0xFF16191C, muted = 0xFF55606A, primary = 0xFF4E6B12, onPrimary = 0xFFFFFFFF,
        primaryContainer = 0xFFDFF2B0, onPrimaryContainer = 0xFF1B2606, secondary = 0xFF2E5E70,
        onSecondary = 0xFFFFFFFF, secondaryContainer = 0xFFC5E5EF, onSecondaryContainer = 0xFF0B2027,
        tertiary = 0xFF6E5A1E, onTertiary = 0xFFFFFFFF, tertiaryContainer = 0xFFEDE2C2,
        onTertiaryContainer = 0xFF241C05, outline = 0xFF8D979F, outlineVariant = 0xFFD3DADE,
        error = 0xFFB3261E, onError = 0xFFFFFFFF, errorContainer = 0xFFF9DEDC, onErrorContainer = 0xFF410E0B,
        inverse = 0xFF232830, onInverse = 0xFFE3E8EB, inverseSurface = 0xFFF2F5F6,
        onInverseSurface = 0xFF1B1F22, scrim = 0xFF000000,
    )
}

/** Морская гладь: глубокая сине-зелёная база, мятный акцент. */
private object Tide {
    val dark = Scheme(
        bg = 0xFF07161A, surface = 0xFF0E2026, lowest = 0xFF051015, low = 0xFF0B1B21,
        container = 0xFF12272E, high = 0xFF163038, highest = 0xFF1D3B44, variant = 0xFF1A3138,
        on = 0xFFDCF2F2, muted = 0xFF92B4B8, primary = 0xFF4ED9C4, onPrimary = 0xFF00302B,
        primaryContainer = 0xFF0C4A43, onPrimaryContainer = 0xFFA6F5E6, secondary = 0xFF6FA8F0,
        onSecondary = 0xFF041C36, secondaryContainer = 0xFF17375F, onSecondaryContainer = 0xFFCFE1FF,
        tertiary = 0xFF9FD9A0, onTertiary = 0xFF10351A, tertiaryContainer = 0xFF1E4A26,
        onTertiaryContainer = 0xFFBBF0BC, outline = 0xFF6A8F93, outlineVariant = 0xFF22383C,
        error = 0xFFF28F8F, onError = 0xFF4E1414, errorContainer = 0xFF632626, onErrorContainer = 0xFFFFDAD8,
        inverse = 0xFFDCF2F2, onInverse = 0xFF163038, inverseSurface = 0xFF1D3B44,
        onInverseSurface = 0xFFD2EBEC, scrim = 0xFF000000,
    )
    val light = Scheme(
        bg = 0xFFEFF7F7, surface = 0xFFFFFFFF, lowest = 0xFFFFFFFF, low = 0xFFF4FAFA,
        container = 0xFFE6F0F0, high = 0xFFDCE8E9, highest = 0xFFD2E0E1, variant = 0xFFDDE9EA,
        on = 0xFF0B1F22, muted = 0xFF46656A, primary = 0xFF006A5E, onPrimary = 0xFFFFFFFF,
        primaryContainer = 0xFF9DF2E5, onPrimaryContainer = 0xFF00201C, secondary = 0xFF2A5599,
        onSecondary = 0xFFFFFFFF, secondaryContainer = 0xFFD3E3FF, onSecondaryContainer = 0xFF0A1C36,
        tertiary = 0xFF3F6B32, onTertiary = 0xFFFFFFFF, tertiaryContainer = 0xFFC8EFC6,
        onTertiaryContainer = 0xFF0D2408, outline = 0xFF7F9A9E, outlineVariant = 0xFFC9DCDE,
        error = 0xFFB3261E, onError = 0xFFFFFFFF, errorContainer = 0xFFF9DEDC, onErrorContainer = 0xFF410E0B,
        inverse = 0xFF163038, onInverse = 0xFFDCE8E9, inverseSurface = 0xFFEFF7F7,
        onInverseSurface = 0xFF0B1F22, scrim = 0xFF000000,
    )
}

/** Бумага: светлый дневник на столе, бордовый сигнал внимания. */
private object Paper {
    val dark = Scheme(
        bg = 0xFF1A1414, surface = 0xFF241C1C, lowest = 0xFF140F0F, low = 0xFF1E1717,
        container = 0xFF2A2020, high = 0xFF302524, highest = 0xFF3B2E2D, variant = 0xFF332726,
        on = 0xFFF6EAE8, muted = 0xFFC6ACA8, primary = 0xFFE08A7A, onPrimary = 0xFF33110C,
        primaryContainer = 0xFF5A2A22, onPrimaryContainer = 0xFFFFDAD2, secondary = 0xFFC9A18C,
        onSecondary = 0xFF2B1810, secondaryContainer = 0xFF4A3226, onSecondaryContainer = 0xFFF7DCC9,
        tertiary = 0xFFB0B36A, onTertiary = 0xFF23260A, tertiaryContainer = 0xFF383B16,
        onTertiaryContainer = 0xFFD6D99A, outline = 0xFF9C827D, outlineVariant = 0xFF3E302E,
        error = 0xFFF09B95, onError = 0xFF511613, errorContainer = 0xFF6B2A25, onErrorContainer = 0xFFFFDAD6,
        inverse = 0xFFF6EAE8, onInverse = 0xFF302524, inverseSurface = 0xFF3B2E2D,
        onInverseSurface = 0xFFEFE0DE, scrim = 0xFF000000,
    )
    val light = Scheme(
        bg = 0xFFFAF3F0, surface = 0xFFFFFFFF, lowest = 0xFFFFFFFF, low = 0xFFFBF6F4,
        container = 0xFFF3EAE6, high = 0xFFECE0DB, highest = 0xFFE4D6D0, variant = 0xFFF0E2DD,
        on = 0xFF211715, muted = 0xFF6E5550, primary = 0xFF9C3A2B, onPrimary = 0xFFFFFFFF,
        primaryContainer = 0xFFFFDAD3, onPrimaryContainer = 0xFF3E0B05, secondary = 0xFF8A5A3C,
        onSecondary = 0xFFFFFFFF, secondaryContainer = 0xFFF7E0D2, onSecondaryContainer = 0xFF2E1709,
        tertiary = 0xFF55600F, onTertiary = 0xFFFFFFFF, tertiaryContainer = 0xFFDFE9B6,
        onTertiaryContainer = 0xFF181C03, outline = 0xFFA48A84, outlineVariant = 0xFFE7D5D0,
        error = 0xFFB3261E, onError = 0xFFFFFFFF, errorContainer = 0xFFF9DEDC, onErrorContainer = 0xFF410E0B,
        inverse = 0xFF302524, onInverse = 0xFFECE0DB, inverseSurface = 0xFFFAF3F0,
        onInverseSurface = 0xFF211715, scrim = 0xFF000000,
    )
}

/** Набор цветов палитры. Значения — ARGB, чтобы не тянуть Compose в модель. */
private data class Scheme(
    val bg: Long, val surface: Long, val lowest: Long, val low: Long, val container: Long,
    val high: Long, val highest: Long, val variant: Long,
    val on: Long, val muted: Long,
    val primary: Long, val onPrimary: Long, val primaryContainer: Long, val onPrimaryContainer: Long,
    val secondary: Long, val onSecondary: Long, val secondaryContainer: Long, val onSecondaryContainer: Long,
    val tertiary: Long, val onTertiary: Long, val tertiaryContainer: Long, val onTertiaryContainer: Long,
    val outline: Long, val outlineVariant: Long,
    val error: Long, val onError: Long, val errorContainer: Long, val onErrorContainer: Long,
    val inverse: Long, val onInverse: Long, val inverseSurface: Long, val onInverseSurface: Long,
    val scrim: Long,
)

private fun Scheme.colors(isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(secondary), onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer), onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary), onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer), onTertiaryContainer = Color(onTertiaryContainer),
        error = Color(error), onError = Color(onError),
        errorContainer = Color(errorContainer), onErrorContainer = Color(onErrorContainer),
        background = Color(bg), onBackground = Color(on),
        surface = Color(surface), onSurface = Color(on),
        surfaceVariant = Color(variant), onSurfaceVariant = Color(muted),
        surfaceContainerLowest = Color(lowest), surfaceContainerLow = Color(low),
        surfaceContainer = Color(container), surfaceContainerHigh = Color(high),
        surfaceContainerHighest = Color(highest),
        surfaceDim = Color(lowest), surfaceBright = Color(highest),
        outline = Color(outline), outlineVariant = Color(outlineVariant),
        inverseSurface = Color(inverseSurface), inverseOnSurface = Color(onInverseSurface),
        inversePrimary = Color(primary), scrim = Color(scrim),
    )
} else {
    lightColorScheme(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(secondary), onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer), onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary), onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer), onTertiaryContainer = Color(onTertiaryContainer),
        error = Color(error), onError = Color(onError),
        errorContainer = Color(errorContainer), onErrorContainer = Color(onErrorContainer),
        background = Color(bg), onBackground = Color(on),
        surface = Color(surface), onSurface = Color(on),
        surfaceVariant = Color(variant), onSurfaceVariant = Color(muted),
        surfaceContainerLowest = Color(lowest), surfaceContainerLow = Color(low),
        surfaceContainer = Color(container), surfaceContainerHigh = Color(high),
        surfaceContainerHighest = Color(highest),
        surfaceDim = Color(low), surfaceBright = Color(high),
        outline = Color(outline), outlineVariant = Color(outlineVariant),
        inverseSurface = Color(inverseSurface), inverseOnSurface = Color(onInverseSurface),
        inversePrimary = Color(primary), scrim = Color(scrim),
    )
}

private fun schemeOf(palette: Palette, dark: Boolean) = when (palette) {
    Palette.ESPRESSO -> if (dark) Espresso.dark else Espresso.light
    Palette.GRAPHITE -> if (dark) Graphite.dark else Graphite.light
    Palette.TIDE -> if (dark) Tide.dark else Tide.light
    Palette.PAPER -> if (dark) Paper.dark else Paper.light
}.colors(dark)

/**
 * Градиенты — это свет, а не украшение: он идёт сверху (экран, шапка) и изнутри
 * главной карточки. Остальное остаётся плоским, чтобы текст не терялся.
 * Выключаются одним тумблером — кому-то ровный фон приятнее.
 */
object Chrome {
    private var gradients = true
    private var top = Color(0xFF2A1F10)
    private var bottom = Color(0xFF0E0B07)
    private var heroA = Color(0xFF6B3E12)
    private var heroB = Color(0xFF241708)

    @Composable
    fun configure(design: Design) {
        val colors = MaterialTheme.colorScheme
        gradients = design.gradients
        top = colors.surfaceBright
        bottom = colors.surfaceContainerLowest
        heroA = colors.primaryContainer
        heroB = colors.surfaceContainerHighest
    }

    @Composable
    fun screen(): Brush {
        val bg = MaterialTheme.colorScheme.background
        return if (gradients) {
            Brush.verticalGradient(0.0f to top, 0.35f to bg, 1.0f to bottom)
        } else {
            SolidColor(bg)
        }
    }

    @Composable
    fun topBar(): Brush {
        val surface = MaterialTheme.colorScheme.surface
        return if (gradients) {
            Brush.verticalGradient(0.0f to top, 1.0f to surface)
        } else {
            SolidColor(surface)
        }
    }

    @Composable
    fun heroCard(): Brush = if (gradients) {
        Brush.linearGradient(0.0f to heroA, 0.55f to heroB, 1.0f to heroB)
    } else {
        SolidColor(heroA)
    }
}

/** Фоновая заливка экрана: градиент выбранной палитры или ровный цвет. */
@Composable
fun Modifier.warmScreenBackground(): Modifier = background(Chrome.screen())

/** Главная карточка: акцентная заливка. */
@Composable
fun Modifier.warmHeroBackground(shape: Shape): Modifier = background(Chrome.heroCard(), shape)

/** Множитель плотности: им пользуются отступы экранов. */
val LocalSpacing = staticCompositionLocalOf { 1.0f }

/** Отступ с учётом выбранной плотности. */
@Composable
fun Dp.scaled(): Dp = this * LocalSpacing.current

private fun typography(font: FontStyle, scale: Float): Typography {
    val family = when (font) {
        FontStyle.SANS -> FontFamily.SansSerif
        FontStyle.SERIF -> FontFamily.Serif
        FontStyle.MONO -> FontFamily.Monospace
    }
    fun st(size: Int, weight: FontWeight?, line: Int) = TextStyle(
        fontFamily = family,
        fontSize = (size * scale).sp,
        lineHeight = (line * scale).sp,
        fontWeight = weight,
    )
    return Typography(
        headlineSmall = st(24, FontWeight.SemiBold, 30),
        titleLarge = st(21, FontWeight.SemiBold, 27),
        titleMedium = st(16, FontWeight.SemiBold, 22),
        titleSmall = st(14, FontWeight.Medium, 20),
        bodyLarge = st(16, null, 24),
        bodyMedium = st(14, null, 20),
        bodySmall = st(12, null, 17),
        labelLarge = st(14, FontWeight.Medium, 20),
        labelMedium = st(12, FontWeight.Medium, 16),
    )
}

@Composable
fun ChronicTheme(
    design: Design = DesignStore.design,
    content: @Composable () -> Unit,
) {
    val dark = when (design.mode) {
        Mode.AUTO -> isSystemInDarkTheme()
        Mode.DARK -> true
        Mode.LIGHT -> false
    }
    val colors = schemeOf(design.palette, dark)
    val typo = typography(design.font, design.density.scale)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.surfaceBright.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalContentColor provides colors.onBackground,
        LocalSpacing provides design.density.scale,
    ) {
        MaterialTheme(colorScheme = colors, typography = typo) {
            Chrome.configure(design)
            // Material3 не красит «голые» Text в цвет фона: по умолчанию они
            // чёрные, и на тёмной теме заголовки экранов были не видны.
            CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
                content()
            }
        }
    }
}