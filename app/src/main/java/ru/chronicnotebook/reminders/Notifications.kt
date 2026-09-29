package ru.chronicnotebook.reminders

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    fun showIntake(context: Context, intake: IntakeEntity, medName: String, dose: String, step: Int) {
        runCatching { show(context, intake, medName, dose, step) }
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
        val text = buildString {
            if (step == 0) append("Пора принять: $medName")
            else append("Напоминание $step: $medName всё ещё не отмечено")
            if (dose.isNotBlank()) append(", $dose")
        }
        val notification: Notification = NotificationCompat.Builder(context, App.CHANNEL_INTAKE)
            .setSmallIcon(R.drawable.ic_stat_pressure)
            .setContentTitle(text)
            .setContentText("Отметить приём")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .setOngoing(false)
            .setWhen(intake.dueAt)
            .setContentIntent(openApp(context))
            .addAction(0, "Принял", pending)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(intake.id.toInt(), notification)
    }

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
