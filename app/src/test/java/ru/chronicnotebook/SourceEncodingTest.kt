package ru.chronicnotebook

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Сторож кодировки. Один раз русский текст в исходниках был испорчен
 * двойным перекодированием (UTF-8, прочитанный как CP1251), и в релиз уехала
 * карточка с нечитаемым текстом. Тест ловит это до сборки релиза.
 */
class SourceEncodingTest {
    @Test
    fun noMojibakeInSources() {
        val root = File("src/main/java")
        assertTrue("не найден каталог исходников: ${root.absolutePath}", root.isDirectory)

        val offenders = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText(Charsets.UTF_8)
                // Типичный вид двойной кодировки: "РѕР°Р·" вместо "каз".
                MOJIBAKE_MARKERS.forEach { marker ->
                    if (text.contains(marker)) {
                        offenders += "${file.name}: найдено \"$marker\""
                    }
                }
            }
        assertTrue(
            "испорченные русские строки в исходниках:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun keyUserStringsSurvived() {
        val card = File("src/main/java/ru/chronicnotebook/ui/ReminderSetupCard.kt")
        assertTrue("нет файла карточки", card.isFile)
        val text = card.readText(Charsets.UTF_8)
        listOf("Проверить звук сейчас", "Напоминание о приёме").forEach {
            assertTrue("в карточке нет строки \"$it\"", text.contains(it))
        }
    }

    private companion object {
        val MOJIBAKE_MARKERS = listOf("РѕР°Р·", "Р РѕРѕРѕ", "РµРѕР°Р·Р°РЄ")
    }
}
