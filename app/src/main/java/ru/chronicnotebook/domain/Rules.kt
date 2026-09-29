package ru.chronicnotebook.domain

object Thresholds {
    const val CRISIS_SYS = 180
    const val CRISIS_DIA = 120
    const val HIGH_SYS = 160
    const val HIGH_DIA = 100
    const val ELEVATED_SYS = 140
    const val ELEVATED_DIA = 90
    const val LOW_SYS = 90
    const val LOW_DIA = 60

    const val MORNING_FROM = 5
    const val MORNING_TO = 12
    const val EVENING_FROM = 18
    const val EVENING_TO = 24

    const val BASELINE_MIN_N = 14
    const val CORRELATION_MIN_N = 20
    const val R_THRESHOLD = 0.3
    val LAGS_H = listOf(0, 6, 12, 24, 48)
}

enum class Level(val label: String) {
    CRISIS("кризис"),
    HIGH("повышено"),
    ELEVATED("верхняя граница"),
    NORMAL("в коридоре"),
    LOW("понижено"),
}

fun classify(sys: Int, dia: Int): Level = when {
    sys >= Thresholds.CRISIS_SYS || dia >= Thresholds.CRISIS_DIA -> Level.CRISIS
    sys >= Thresholds.HIGH_SYS || dia >= Thresholds.HIGH_DIA -> Level.HIGH
    sys >= Thresholds.ELEVATED_SYS || dia >= Thresholds.ELEVATED_DIA -> Level.ELEVATED
    sys < Thresholds.LOW_SYS || dia < Thresholds.LOW_DIA -> Level.LOW
    else -> Level.NORMAL
}

/** Фиксированные тексты. Не генерируются моделью. */
fun advice(level: Level, sys: Int, dia: Int): String = when (level) {
    Level.CRISIS ->
        "Кризисное давление $sys/$dia. Перемерьте через 15 минут, сидя спокойно. " +
        "Если давление осталось 180/120 и выше или появилась одышка, боль в груди, " +
        "нарушение зрения или речи — вызывайте скорую 103."
    Level.HIGH ->
        "Давление $sys/$dia выше 160/100. Перемерьте через 15 минут спокойно, " +
        "не принимайте внеплановых препаратов. Если вернётся в норму — запишите замер."
    Level.ELEVATED ->
        "Давление $sys/$dia выше 140/90. Запишите в дневник и обсудите с врачом " +
        "на ближайшем визите. Самостоятельно терапию не меняйте."
    Level.LOW ->
        "Давление $sys/$dia ниже 90/60. Сядьте или лягте, поднимите ноги выше головы, " +
        "пейте воду небольшими глотками. Если состояние не улучшилось за 20 минут — скорая."
    Level.NORMAL ->
        "Давление $sys/$dia в пределах вашего коридора."
}

enum class Bucket(val label: String) {
    NIGHT("ночь"),
    MORNING("утро"),
    DAY("день"),
    EVENING("вечер"),
}

fun bucketOf(hour: Int): Bucket = when {
    hour < Thresholds.MORNING_FROM -> Bucket.NIGHT
    hour < Thresholds.MORNING_TO -> Bucket.MORNING
    hour < Thresholds.EVENING_FROM -> Bucket.DAY
    else -> Bucket.EVENING
}
