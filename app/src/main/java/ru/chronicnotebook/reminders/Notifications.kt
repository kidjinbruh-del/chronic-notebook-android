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

    /**
     * Показывает напоминание о приёме.
     *
     * Ошибку логируем, но и пробрасываем дальше: вызывающий код отмечает ступень
     * как отправленную до показа уведомления и при сбое обязан вернуть её, иначе
     * приём навсегда остался бы без напоминания. Раньше ошибка проглатывалась
     * здесь, и откат не срабатывал: в лог и в карточку «Диагностика» попадал
     * только текст ошибки, а человек не получал ничего.
     */
    fun showIntake(context: Context, intake: IntakeEntity, medName: String, dose: String, step: Int) {
        try {
            show(context, intake, medName, dose, step)
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось показать уведомление о приёме", e)
            throw e
        }
    }

    private fun show(context: Context, intake: IntakeEntity, medName: String, dose: String, step: Int) {
        val intent = Intent(context, IntakeActionReceiver::class.java).apply {
            action = IntakeActionReceiver.ACTION_TAKEN
            putExtra(IntakeActionReceiver.EXTRA_ID, intake.id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            AlarmScheduler.requestCode(intake.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when (step) {
            0 -> "Пора принять: $medName"
            1 -> "Приём не отмечен: $medName"
            2 -> "Повторное напоминание: $medName"
            else -> "Последнее напоминание: $medName"
        }
        val text = buildString {
            if (dose.isNotBlank()) append(dose)
            if (step > 0) {
                if (isNotEmpty()) append(" · ")
                append("приём не отмечен")
            }
            if (isEmpty()) append("Отметить приём")
        }
        val notification: Notification = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_stat_pressure)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(false)
            .setOngoing(false)
            .setWhen(intake.dueAt)
            .setContentIntent(openApp(context))
            .addAction(0, "Принял", pending)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(AlarmScheduler.requestCode(intake.id), notification)
    }

    /**
     * Канал, отвечающий за текущую мелодию.
     *
     * Раньше здесь был жёстко зашит [App.CHANNEL_INTAKE], и выбор мелодии
     * ничего не менял. На Android 8+ мелодия задаётся только при создании
     * канала, поэтому варианту звука соответствует свой канал.
     */
    private fun channelId(context: Context): String {
        val app = context.applicationContext
        val sound = if (app is App) {
            runCatching { app.container.settings.alarmSound }.getOrDefault(ReminderSound.DEFAULT)
        } else {
            ReminderSound.DEFAULT
        }
        val id = ReminderSound.channelId(sound)
        // На Android 8+ канал должен существовать до показа уведомления. Создаём
        // на всякий случай: если по какой-то причине он удалён, уведомление
        // иначе просто не появилось бы, и человек не получил бы напоминание.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            (app as? App)?.syncIntakeChannel()
        }
        return id
    }

    /** Снимает уведомление о приёме: после отметки «Принял» оно не должно висеть. */
    fun dismiss(context: Context, intakeId: Long) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(AlarmScheduler.requestCode(intakeId))
        }
    }

    /**
     * Проверка звука из приложения: уведомление с тем же каналом и теми же
     * настройками, что и настоящее напоминание о приёме. Пользователь может
     * убедиться, что звук есть, не дожидаясь времени приёма.
     */
    fun showTest(context: Context) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val notification = NotificationCompat.Builder(context, channelId(context))
                .setSmallIcon(R.drawable.ic_stat_pressure)
                .setContentTitle("Проверка напоминания")
                .setContentText("Так будет звучать напоминание о приёме")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
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
