package ru.chronicnotebook.data

import android.content.Context
import androidx.room.Room
import ru.chronicnotebook.weather.WeatherClient

/**
 * Ручной DI. Экземпляры создаются лениво: на устройствах с 1-2 ГБ ОЗУ
 * (типичные Infinix) создание БД в Application.onCreate() могло падать по памяти.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings: Settings by lazy { Settings(appContext) }
    val weather: WeatherClient by lazy { WeatherClient(settings) }

    val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()
    }
}
