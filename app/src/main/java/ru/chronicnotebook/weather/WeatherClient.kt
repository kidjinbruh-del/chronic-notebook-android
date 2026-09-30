package ru.chronicnotebook.weather

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.chronicnotebook.data.Settings
import ru.chronicnotebook.data.WeatherEntity
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private const val TAG = "WeatherClient"

class WeatherClient(private val settings: Settings) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val browserHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/json",
        "Accept-Language" to "ru-RU,ru;q=0.9",
    )

    /** Архив Open-Meteo: работает в РФ, ключ не нужен. */
    suspend fun fetchArchive(days: Int = 121): List<WeatherEntity> = withContext(Dispatchers.IO) {
        val end = LocalDate.now().minusDays(1)
        val start = end.minusDays(days.toLong())
        val url = buildString {
            append("https://archive-api.open-meteo.com/v1/archive")
            append("?latitude=").append(settings.lat)
            append("&longitude=").append(settings.lon)
            append("&start_date=").append(start)
            append("&end_date=").append(end)
            append("&daily=temperature_2m_mean,temperature_2m_max,temperature_2m_min")
            append(",surface_pressure_mean,wind_speed_10m_max,precipitation_sum,relative_humidity_2m_mean")
            append("&timezone=").append(settings.timezone)
        }
        val body = get(url, browserHeaders) ?: return@withContext emptyList()
        try {
            val root = JsonParser.parseString(body).asJsonObject
            val daily = root.getAsJsonObject("daily")
            fun series(name: String): List<Float> =
                daily.getAsJsonArray(name).let { a ->
                    (0 until a.size()).map { i ->
                        if (a[i].isJsonNull) Float.NaN else a[i].asFloat
                    }
                }
            val times = daily.getAsJsonArray("time").map { it.asString }
            val tMean = series("temperature_2m_mean")
            val tMax = series("temperature_2m_max")
            val tMin = series("temperature_2m_min")
            val pMsl = series("surface_pressure_mean")
            val wind = series("wind_speed_10m_max")
            val precip = series("precipitation_sum")
            val humidity = series("relative_humidity_2m_mean")

            times.mapIndexed { i, day ->
                WeatherEntity(
                    day = day,
                    lat = settings.lat,
                    lon = settings.lon,
                    tMean = tMean[i].nanToNull(),
                    tMin = tMin[i].nanToNull(),
                    tMax = tMax[i].nanToNull(),
                    pMsl = pMsl[i].nanToNull(),
                    wind = wind[i].nanToNull(),
                    precip = precip[i].nanToNull(),
                    humidity = humidity[i].nanToNull(),
                    source = "open-meteo-archive",
                    fetchedAt = System.currentTimeMillis(),
                )
            }.filter { it.tMean != null }
        } catch (e: Exception) {
            Log.w(TAG, "archive parse failed", e)
            emptyList()
        }
    }

    /** Текущая погода: сперва Яндекс (ld+json), при неудаче — open-meteo forecast. */
    suspend fun fetchNow(): WeatherEntity? = withContext(Dispatchers.IO) {
        val yandex = fetchYandex()
        if (yandex != null) return@withContext yandex
        fetchForecast()
    }

    private fun fetchYandex(): WeatherEntity? {
        val slug = settings.placeName.trim().lowercase().replace(' ', '-')
        val url = "https://yandex.ru/pogoda/$slug"
        val body = get(url, browserHeaders) ?: return null
        val marker = "\"application/ld+json\""
        val start = body.indexOf(marker).takeIf { it >= 0 } ?: return null
        val open = body.indexOf('{', start)
        if (open < 0) return null
        val json = extractJson(body, open) ?: return null
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val days = root.getAsJsonObject("mainEntity").getAsJsonObject("weather")
            val now = days.getAsJsonArray("daypart")
                .asJsonArray.first().asJsonObject
            val fact = now.getAsJsonObject("params")
            val day = LocalDate.now().toString()
            WeatherEntity(
                day = day,
                lat = settings.lat,
                lon = settings.lon,
                tMean = fact.get("temperature")?.asInt?.toDouble(),
                tMax = now.get("max")?.asInt?.toDouble(),
                tMin = now.get("min")?.asInt?.toDouble(),
                humidity = fact.get("humidity")?.asInt?.toDouble(),
                wind = fact.get("wind_speed")?.asInt?.toDouble(),
                pMsl = fact.get("pressure")?.asInt?.toDouble(),
                source = "yandex",
                fetchedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "yandex parse failed", e)
            null
        }
    }

    private fun fetchForecast(): WeatherEntity? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${settings.lat}" +
            "&longitude=${settings.lon}" +
            "&current=temperature_2m,relative_humidity_2m,surface_pressure,wind_speed_10m" +
            "&timezone=${settings.timezone}"
        val body = get(url, browserHeaders) ?: return null
        return try {
            val current = JsonParser.parseString(body).asJsonObject.getAsJsonObject("current")
            WeatherEntity(
                day = LocalDate.now().toString(),
                lat = settings.lat,
                lon = settings.lon,
                tMean = current.get("temperature_2m")?.asDouble,
                humidity = current.get("relative_humidity_2m")?.asDouble,
                pMsl = current.get("surface_pressure")?.asDouble,
                wind = current.get("wind_speed_10m")?.asDouble,
                source = "open-meteo-forecast",
                fetchedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "forecast parse failed", e)
            null
        }
    }

    private fun get(url: String, headers: Map<String, String>): String? = try {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    } catch (e: Exception) {
        // Не только IOException: битая строка часового пояса в настройках даёт
        // IllegalArgumentException прямо из Request.Builder, и синхронизация
        // молча переставала работать вместо тихой отказа.
        Log.w(TAG, "request failed: $url", e)
        null
    }

    private fun extractJson(text: String, from: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in from until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(from, i + 1)
                }
            }
        }
        return null
    }
}

private fun Float.nanToNull(): Double? = if (isNaN()) null else toDouble()
