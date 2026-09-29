package ru.chronicnotebook

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import ru.chronicnotebook.data.AppContainer

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
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
        }
    }

    companion object {
        const val CHANNEL_INTAKE = "intake"
        const val CHANNEL_MEASURE = "measure"
    }
}
