package ru.chronicnotebook

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import ru.chronicnotebook.data.AppContainer

class App : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        createChannels()
        // Страховка напоминаний регистрируется сразу при старте процесса, а не
        // только когда пользователь откроет приложение: иначе будильники о
        // приёме могут не появиться вовсе.
        ru.chronicnotebook.sync.Scheduler.start(this)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            // Настройки канала нельзя менять после создания: если у пользователя
            // канал уже звучал без звука, новый код это не исправит. Поэтому
            // приём лекарств переведён на новый канал intake_v2 с явным звуком
            // и вибрацией, а старый беззвучный канал удаляется.
            runCatching { manager.deleteNotificationChannel(CHANNEL_INTAKE_LEGACY) }

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_INTAKE,
                    "Приём препаратов — срочно",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Напоминания о приёме назначенных препаратов"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 400, 600, 400)
                    enableLights(true)
                    lightColor = android.graphics.Color.RED
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    setSound(
                        android.media.RingtoneManager.getDefaultUri(
                            android.media.RingtoneManager.TYPE_NOTIFICATION
                        ),
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MEASURE,
                    "Замеры давления",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Подсказки о времени замера" }
            )
        } catch (e: Exception) {
            android.util.Log.w("ChronicNotebook", "Не удалось создать каналы уведомлений", e)
        }
    }

    companion object {
        const val CHANNEL_INTAKE = "intake_v2"
        const val CHANNEL_INTAKE_LEGACY = "intake"
        const val CHANNEL_MEASURE = "measure"
    }
}
