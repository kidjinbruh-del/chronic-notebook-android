package ru.chronicnotebook

import android.app.Application
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
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_INTAKE,
                    "Приём препаратов",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Напоминания о приёме назначенных препаратов"
                    enableVibration(true)
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
        const val CHANNEL_INTAKE = "intake"
        const val CHANNEL_MEASURE = "measure"
    }
}
