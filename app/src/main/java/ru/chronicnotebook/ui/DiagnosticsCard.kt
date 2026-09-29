package ru.chronicnotebook.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ru.chronicnotebook.CrashLog
import ru.chronicnotebook.reminders.AlarmScheduler

/**
 * Диагностика: показывает модель, версию Android, состояние точных будильников
 * и последний сбой. Нужна, когда на чужом телефоне «ошибка, какая — не ясно».
 */
@Composable
fun DiagnosticsCard() {
    val context = LocalContext.current
    var showCrash by remember { mutableStateOf(false) }
    val crash = remember { CrashLog.last(context) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Диагностика", style = MaterialTheme.typography.titleMedium)
            Text("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
            Text("Android: ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}")
            Text(
                if (AlarmScheduler.canScheduleExact(context)) "Точные будильники: разрешены"
                else "Точные будильники: ЗАПРЕЩЕНЫ, напоминания могут опаздывать"
            )
            if (isBatteryRestricted(context)) {
                Text("Оптимизация батареи: включена, система может глушить напоминания")
            }
            if (crash != null) {
                Text("---")
                if (!showCrash) {
                    TextButton2("Показать причину последнего сбоя") { showCrash = true }
                } else {
                    Text(crash.take(2500), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    TextButton2("Скопировать и очистить") {
                        copyToClipboard(context, crash)
                        CrashLog.clear(context)
                    }
                }
            } else {
                Text("Сбоев не зафиксировано", style = MaterialTheme.typography.bodySmall)
            }
            if (!AlarmScheduler.canScheduleExact(context)) {
                TextButton2("Разрешить точные будильники") {
                    openExactAlarmSettings(context)
                }
            }
            if (isBatteryRestricted(context)) {
                TextButton2("Отключить оптимизацию батареи") { openBatterySettings(context) }
            }
        }
    }
}

private fun isBatteryRestricted(context: android.content.Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java) ?: return false
    return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(true) == false
}

private fun openExactAlarmSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { openAppSettings(context) }
}

private fun openBatterySettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { openAppSettings(context) }
}

private fun openAppSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun copyToClipboard(context: android.content.Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("crash", text))
    }
}
