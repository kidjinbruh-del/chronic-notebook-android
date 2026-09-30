package ru.chronicnotebook.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Фирменный тёплый чёрный.
 *
 * Фон — не нейтральный `#000`, а глубокий эспрессо: чёрный с коричневым
 * подтоном. Акцент — янтарь вместо зелени: давление, события и предупреждения
 * читаются как «тёплые», а не «больничные». Динамические цвета системы
 * отключены намеренно: на Android 12+ они перекрашивали приложение под обои
 * и убивали этот стиль.
 */
private val EspressoBlack = Color(0xFF14100A)
private val WarmSurface = Color(0xFF1D1710)
private val WarmSurfaceVariant = Color(0xFF2C2417)
private val WarmWhite = Color(0xFFF4EAD9)
private val WarmMuted = Color(0xFFCBB89F)
private val Amber = Color(0xFFEFA94A)
private val Caramel = Color(0xFFD08A4E)
private val Gold = Color(0xFFB7A05E)
private val WarmRed = Color(0xFFF0978A)

private val WarmBlackColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1A05),
    primaryContainer = Color(0xFF4A2C0E),
    onPrimaryContainer = Color(0xFFFFDDAE),
    secondary = Caramel,
    onSecondary = Color(0xFF2A1A08),
    secondaryContainer = Color(0xFF3A2A18),
    onSecondaryContainer = Color(0xFFF2D3AC),
    tertiary = Gold,
    onTertiary = Color(0xFF2E2708),
    tertiaryContainer = Color(0xFF3A3018),
    onTertiaryContainer = Color(0xFFE4D6A8),
    error = WarmRed,
    onError = Color(0xFF4A0F0A),
    errorContainer = Color(0xFF5A1B12),
    onErrorContainer = Color(0xFFFFD9D2),
    background = EspressoBlack,
    onBackground = WarmWhite,
    surface = WarmSurface,
    onSurface = WarmWhite,
    // Контейнеры НЕ наследуются от surface: у них свои холодные дефолты,
    // и без явных значений карточки становились серыми на тёплом фоне.
    surfaceDim = Color(0xFF0E0B07),
    surfaceBright = Color(0xFF3E3423),
    surfaceContainerLowest = Color(0xFF0C0906),
    surfaceContainerLow = Color(0xFF181309),
    surfaceContainer = Color(0xFF201910),
    surfaceContainerHigh = Color(0xFF2A2114),
    surfaceContainerHighest = Color(0xFF362B1A),
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmMuted,
    outline = Color(0xFF8A7660),
    outlineVariant = Color(0xFF3A3126),
)

/**
 * Градиенты тёплого чёрного. Правило одно: градиент — это свет, а не картинка.
 * Свет идёт сверху (экран, шапка) и изнутри главной карточки; остальное
 * остаётся плоским, чтобы текст не терялся.
 */
object WarmChrome {
    /** Лёгкое тёплое свечение сверху экрана, уходящее в глубину. */
    @Composable
    fun screen(): Brush = Brush.verticalGradient(
        0.0f to Color(0xFF251B0E),
        0.35f to EspressoBlack,
        1.0f to Color(0xFF0E0B07),
    )

    /** Шапка: тёплая плашка, растворяющаяся в фоне. */
    @Composable
    fun topBar(): Brush = Brush.verticalGradient(
        0.0f to Color(0xFF2A1F10),
        1.0f to Color(0xFF171209),
    )

    /** Главная карточка сводки: янтарное ядро в тёмном корпусе. */
    @Composable
    fun heroCard(): Brush = Brush.linearGradient(
        0.0f to Color(0xFF6B3E12),
        0.55f to Color(0xFF3A2410),
        1.0f to Color(0xFF241708),
    )
}

/** Фоновая заливка экрана: градиент вместо плоского чёрного. */
@Composable
fun Modifier.warmScreenBackground(): Modifier = background(WarmChrome.screen())

/** Градиентная карточка с нужной формой. */
@Composable
fun Modifier.warmHeroBackground(shape: Shape): Modifier =
    background(WarmChrome.heroCard(), shape)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ChronicTheme(content: @Composable () -> Unit) {
    val colors = WarmBlackColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color(0xFF251B0E).toArgb()
            window.navigationBarColor = EspressoBlack.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(colorScheme = colors, typography = AppTypography) {
        // Material3 не красит «голые» Text в цвет фона: по умолчанию они чёрные.
        // На светлой теме это было незаметно, на тёплой чёрной заголовки экранов
        // становились невидимыми. Цвет задаётся один раз здесь, а не в каждом Text.
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            content()
        }
    }
}
