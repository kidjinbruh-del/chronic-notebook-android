package ru.chronicnotebook.data

import android.content.Context
import androidx.room.Room
import ru.chronicnotebook.weather.WeatherClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val db: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.NAME)
        .fallbackToDestructiveMigration()
        .build()

    val settings = Settings(appContext)
    val weather = WeatherClient(settings)
}
