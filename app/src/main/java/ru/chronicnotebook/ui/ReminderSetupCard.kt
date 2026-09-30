package ru.chronicnotebook.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.chronicnotebook.App
import ru.chronicnotebook.reminders.AlarmScheduler
import ru.chronicnotebook.reminders.Notifications
import ru.chronicnotebook.reminders.ReminderSound

/**
 * Карточка на вкладке «Дом»: показывает проблемы с доставкой напоминаний и
 * всегда оставляет кнопку проверки звука.
 *
 * Проверено на Redmi Note 13 Pro (Android 16, HyperOS 3): точность будильников
 * там в порядке, а вот важность уведомлений была понижена системой, из-за чего
 * напоминание не всплывало и звучало тихо. Понизить важность обратно может
 * только пользователь, поэтому проверка звука нужна всегда, а не только когда
 * найдена проблема.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderSetupCard() {
    val context = LocalContext.current
    var stamp by remember { mutableIntStateOf(0) }

    // Возврат из настроек: система меняет важность и разрешения, а карточка
    // пересчитывалась сразу после нажатия, то есть до того, как пользователь
    // что-то поменял, и продолжала показывать старую картину.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) stamp++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exactOk = remember(stamp) { AlarmScheduler.canScheduleExact(context) }
    val batteryRestricted = remember(stamp) { isBatteryRestricted(context) }
    val notificationsOn = remember(stamp) { notificationsEnabled(context) }
    val channelHigh = remember(stamp) { intakeChannelIsHigh(context) }

    val problems = buildList {
        if (!notificationsOn) add("Уведомления выключены: напоминание о приёме не появится")
        if (!channelHigh) add("Важность канала снижена: напоминание будет тихим и без всплывашки")
        if (!exactOk) add("Система не разрешает точные будильники: время срабатывания может сдвинуться на часы")
        if (batteryRestricted) add("Включена оптимизация батареи: система может не доставить напоминание")
    }
    val broken = problems.isNotEmpty()

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (broken) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        val onColor = if (broken) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (broken) "Напоминания могут не сработать" else "Напоминание о приёме",
                style = MaterialTheme.typography.titleMedium,
                color = onColor,
            )
            problems.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, color = onColor)
            }
            if (!broken) {
                Text(
                    "Нажмите «Проверить звук» и убедитесь, что напоминание слышно. " +
                        "Если оно приходит тихо, откройте настройки уведомлений и " +
                        "поставьте важность «Высокая».",
                    style = MaterialTheme.typography.bodySmall,
                    color = onColor,
                )
            }
            // Кнопки переносятся, а не стоят в одну строку: на экране 360 dp с
            // крупным шрифтом три подписи в Row не помещались и обрезались.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!exactOk) {
                    TextButton2("Разрешить будильники") { openExactAlarmSettings(context) }
                }
                if (batteryRestricted) {
                    TextButton2("Отключить экономию") { openBatterySettings(context) }
                }
                // Показана всегда: важность всего приложения виду не проверяется,
                // понизить её может только пользователь, а на Redmi Note 13 Pro
                // канал был HIGH при общей важности DEFAULT — и кнопки не было.
                TextButton2("Настройки уведомлений") { openNotificationSettings(context) }
            }
            TextButton2("Проверить звук сейчас") { Notifications.showTest(context) }
            SoundPicker(stamp)
        }
    }
}

/**
 * Выбор мелодии напоминания.
 *
 * Вариантов три источника: стандартная мелодия будильника телефона, полная тишина
 * и любой файл, который пользователь выбрал сам через системный выбор мелодий.
 * После смены канал пересоздаётся — иначе Android продолжал бы играть прежний
 * звук, молча проигнорировав новый выбор.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundPicker(stamp: Int) {
    val context = LocalContext.current
    val app = context.applicationContext as? App
    var current by remember(stamp) {
        mutableStateOf(
            runCatching { ReminderSound.label(context, app?.container?.settings?.alarmSound) }
                .getOrDefault(ReminderSound.DEFAULT)
        )
    }

    // URI из системного выбора мелодий приходит в onActivityResult, а не через
    // Compose-состояние, поэтому результат ловится здесь же через LaunchedEffect
    // на коде возврата: без него выбор просто не применялся.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            app?.let {
                it.container.settings.alarmSound = uri.toString()
                it.syncIntakeChannel()
            }
            current = ReminderSound.label(context, uri.toString())
        }
    }

    fun choose(sound: String) {
        app?.let {
            it.container.settings.alarmSound = sound
            it.syncIntakeChannel()
        }
        current = ReminderSound.label(context, sound)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Мелодия напоминания", style = MaterialTheme.typography.titleSmall)
        Text(
            "Сейчас: $current",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton2("Стандартный будильник") { choose(ReminderSound.DEFAULT) }
            TextButton2("Без звука") { choose(ReminderSound.SILENT) }
            TextButton2("Своя мелодия") {
                runCatching {
                    picker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_ALARM,
                            )
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Мелодия напоминания")
                    )
                }
            }
        }
    }
}

/**
 * Важность, которую система реально применяет к каналу приёма. Если она ниже
 * HIGH, значит пользователь или оболочка понизили её, и напоминание придёт
 * тише обычного.
 *
 * Проверяется канал именно выбранной мелодии: у каждого варианта звука он свой,
 * и понижение могли сделать только тому, по которому приходят напоминания.
 */
private fun intakeChannelIsHigh(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    val manager = context.getSystemService(NotificationManager::class.java) ?: return true
    val app = context.applicationContext as? App
    val sound = runCatching { app?.container?.settings?.alarmSound }
        .getOrNull()
        ?: ReminderSound.DEFAULT
    val channel = manager.getNotificationChannel(ReminderSound.channelId(sound)) ?: return true
    return channel.importance >= NotificationManager.IMPORTANCE_HIGH
}

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

internal fun isBatteryRestricted(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java) ?: return false
    return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }
        .getOrDefault(true)
        .not()
}

internal fun openExactAlarmSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { openAppSettings(context) }
}

internal fun openBatterySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { openAppSettings(context) }
}

internal fun openNotificationSettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }.onFailure { openAppSettings(context) }
}

internal fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
