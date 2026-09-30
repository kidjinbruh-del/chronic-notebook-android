package ru.chronicnotebook

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import ru.chronicnotebook.data.AppContainer
import ru.chronicnotebook.reminders.ReminderSound

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
            // Старые каналы удаляются: их звук и важность зафиксированы, новый
            // код уже не может им управлять, и пользователь слышал бы то, что
            // было выбрано в прошлой версии.
            runCatching { manager()?.deleteNotificationChannel(CHANNEL_INTAKE_LEGACY) }
            runCatching { manager()?.deleteNotificationChannel(CHANNEL_INTAKE_V2) }
            syncIntakeChannel()
            manager()?.createNotificationChannel(
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

    private fun manager(): NotificationManager? = getSystemService(NotificationManager::class.java)

    /**
     * Создаёт канал приёма для текущей мелодии и убирает каналы от других
     * вариантов. Вызывается и при старте, и сразу после смены мелодии:
     * непересозданный канал продолжал бы звучать прежним.
     */
    fun syncIntakeChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = manager() ?: return
        val sound = runCatching { container.settings.alarmSound }
            .getOrDefault(ReminderSound.DEFAULT)
        val keep = ReminderSound.channelId(sound)
        try {
            manager.createNotificationChannel(
                NotificationChannel(
                    keep,
                    ReminderSound.channelName(sound),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Напоминания о приёме назначенных препаратов"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 400, 600, 400)
                    enableLights(true)
                    lightColor = android.graphics.Color.RED
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    setSound(ReminderSound.uri(this@App, sound), ReminderSound.audioAttributes)
                }
            )
            // Каналы от прочих вариантов звука только мешают: в настройках
            // системы их видно как дубли, и непонятно, какой из них рабочий.
            runCatching {
                manager.notificationChannels
                    .map { it.id }
                    .filter { it == CHANNEL_INTAKE || it.startsWith("${CHANNEL_INTAKE}_") }
                    .filter { it != keep }
                    .forEach { manager.deleteNotificationChannel(it) }
            }
        } catch (e: Exception) {
            android.util.Log.w("ChronicNotebook", "Не удалось настроить канал приёма", e)
        }
    }

    companion object {
        const val CHANNEL_INTAKE = "intake_v3"
        const val CHANNEL_INTAKE_V2 = "intake_v2"
        const val CHANNEL_INTAKE_LEGACY = "intake"
        const val CHANNEL_MEASURE = "measure"
    }
}
