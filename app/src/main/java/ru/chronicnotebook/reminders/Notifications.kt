package ru.chronicnotebook.reminders

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import ru.chronicnotebook.App
import ru.chronicnotebook.MainActivity
import ru.chronicnotebook.R
import ru.chronicnotebook.data.IntakeEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Notifications {
    private val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private const val TAG = "ChronicNotebook"

    fun showIntake(context: Context, intake: IntakeEntity, medName: String, dose: String, step: Int) {
        // Раньше ошибка молча проглатывалась, и человек видел просто пустое
        // уведомление без звука. Теперь любая ошибка видна в logcat и в
        // карточке «Диагностика».
        runCatching { show(context, intake, medName, dose, step) }
            .onFailure { Log.e(TAG, "Не удалось показать уведомление о приёме", it) }
    }

    private fun show(context: Context, intake: IntakeEntity, medName: String, dose: String, step: Int) {
        val intent = Intent(context, IntakeActionReceiver::class.java).apply {
            action = IntakeActionReceiver.ACTION_TAKEN
            putExtra(IntakeActionReceiver.EXTRA_ID, intake.id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            intake.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (step == 0) "Пора принять: $medName" else "Напоминание $step: $medName"
        val text = buildString {
            if (dose.isNotBlank()) append(dose)
            if (step > 0) {
                if (isNotEmpty()) append(" · ")
                append("приём не отмечен")
            }
            if (isEmpty()) append("Отметить приём")
        }
        val notification: Notification = NotificationCompat.Builder(context, App.CHANNEL_INTAKE)
            .setSmallIcon(R.drawable.ic_stat_pressure)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(false)
            .setOngoing(false)
            .setWhen(intake.dueAt)
            .setContentIntent(openApp(context))
            .addAction(0, "Принял", pending)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(intake.id.toInt(), notification)
    }

    /**
     * Проверка звука из приложения: уведомление с тем же каналом и теми же
     * настройками, что и настоящее напоминание о приёме. Пользователь может
     * убедиться, что звук есть, не дожидаясь времени приёма.
     */
    fun showTest(context: Context) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val notification = NotificationCompat.Builder(context, App.CHANNEL_INTAKE)
                .setSmallIcon(R.drawable.ic_stat_pressure)
                .setContentTitle("Проверка напоминания")
                .setContentText("Так будет звучать напоминание о приёме")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .build()
            manager.notify(TEST_ID, notification)
        }.onFailure { Log.e(TAG, "Проверка напоминания не удалась", it) }
    }

    private const val TEST_ID = 999_001

    fun showMeasureHint(context: Context, slot: String) {
        runCatching {
            val notification = NotificationCompat.Builder(context, App.CHANNEL_MEASURE)
                .setSmallIcon(R.drawable.ic_stat_pressure)
                .setContentTitle("Время замера ($slot)")
                .setContentText("Отдых 5 минут, сидя, и замер")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(slot.hashCode(), notification)
        }
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun humanTime(millis: Long): String = time.format(Instant.ofEpochMilli(millis))
}
