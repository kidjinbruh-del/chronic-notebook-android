package ru.chronicnotebook.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import ru.chronicnotebook.reminders.AlarmScheduler
import ru.chronicnotebook.reminders.Notifications

/**
 * РџРѕРєР°Р·С‹РІР°РµС‚СЃСЏ РІРІРµСЂС…Сѓ РІРєР»Р°РґРєРё В«Р”РѕРјВ», РєРѕРіРґР° РЅР°РїРѕРјРёРЅР°РЅРёСЏ РјРѕРіСѓС‚ РЅРµ РґРѕР№С‚Рё:
 * Р·Р°РїСЂРµС‰РµРЅС‹ С‚РѕС‡РЅС‹Рµ Р±СѓРґРёР»СЊРЅРёРєРё, РѕС‚РєР»СЋС‡РµРЅС‹ СѓРІРµРґРѕРјР»РµРЅРёСЏ РёР»Рё РІРєР»СЋС‡РµРЅР° РѕРїС‚РёРјРёР·Р°С†РёСЏ
 * Р±Р°С‚Р°СЂРµРё. Р Р°РЅСЊС€Рµ СЌС‚Р° РёРЅС„РѕСЂРјР°С†РёСЏ Р±С‹Р»Р° СЃРїСЂСЏС‚Р°РЅР° РІРЅРёР·Сѓ РєР°СЂС‚РѕС‡РєРё В«Р”РёР°РіРЅРѕСЃС‚РёРєР°В»,
 * Рё РїРѕР»СЊР·РѕРІР°С‚РµР»СЊ РЅРµ РїРѕРЅРёРјР°Р», РїРѕС‡РµРјСѓ Р±СѓРґРёР»СЊРЅРёРє РЅРµ Р·РІРѕРЅРёС‚.
 */
@Composable
fun ReminderSetupCard() {
    val context = LocalContext.current
    var stamp by remember { mutableIntStateOf(0) }

    val exactOk = remember(stamp) { AlarmScheduler.canScheduleExact(context) }
    val batteryRestricted = remember(stamp) { isBatteryRestricted(context) }
    val notificationsOn = remember(stamp) { notificationsEnabled(context) }

    val problems = buildList {
        if (!exactOk) add("РЎРёСЃС‚РµРјР° РЅРµ СЂР°Р·СЂРµС€Р°РµС‚ С‚РѕС‡РЅС‹Рµ Р±СѓРґРёР»СЊРЅРёРєРё: РІСЂРµРјСЏ СЃСЂР°Р±Р°С‚С‹РІР°РЅРёСЏ РјРѕР¶РµС‚ СЃРґРІРёРЅСѓС‚СЊСЃСЏ РЅР° С‡Р°СЃС‹")
        if (batteryRestricted) add("Р’РєР»СЋС‡РµРЅР° РѕРїС‚РёРјРёР·Р°С†РёСЏ Р±Р°С‚Р°СЂРµРё: СЃРёСЃС‚РµРјР° РјРѕР¶РµС‚ РЅРµ РґРѕСЃС‚Р°РІРёС‚СЊ РЅР°РїРѕРјРёРЅР°РЅРёРµ")
        if (!notificationsOn) add("РЈРІРµРґРѕРјР»РµРЅРёСЏ РІС‹РєР»СЋС‡РµРЅС‹: РЅР°РїРѕРјРёРЅР°РЅРёРµ Рѕ РїСЂРёС‘РјРµ РЅРµ РїРѕСЏРІРёС‚СЃСЏ")
    }
    if (problems.isEmpty()) return

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "РќР°РїРѕРјРёРЅР°РЅРёСЏ РјРѕРіСѓС‚ РЅРµ СЃСЂР°Р±РѕС‚Р°С‚СЊ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            problems.forEach {
                Text(
                    "вЂў $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!exactOk) {
                    TextButton2("Р Р°Р·СЂРµС€РёС‚СЊ Р±СѓРґРёР»СЊРЅРёРєРё") {
                        openExactAlarmSettings(context)
                        stamp++
                    }
                }
                if (batteryRestricted) {
                    TextButton2("РћС‚РєР»СЋС‡РёС‚СЊ СЌРєРѕРЅРѕРјРёСЋ") {
                        openBatterySettings(context)
                        stamp++
                    }
                }
                if (!notificationsOn) {
                    TextButton2("Р’РєР»СЋС‡РёС‚СЊ СѓРІРµРґРѕРјР»РµРЅРёСЏ") {
                        openNotificationSettings(context)
                        stamp++
                    }
                }
            }
            TextButton2("РџСЂРѕРІРµСЂРёС‚СЊ Р·РІСѓРє СЃРµР№С‡Р°СЃ") {
                Notifications.showTest(context)
            }
        }
    }
}

internal fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

internal fun isBatteryRestricted(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java) ?: return false
    return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }
        .getOrDefault(true) == false
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

