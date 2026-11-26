# Дневник давления на iPhone

Первый порт на SwiftUI для iOS 16 и новее. В проекте есть локальная история,
добавление замеров с датой, пульсом и заметкой, импорт и экспорт JSON версии 1.
На экране подтверждения сохранения используется Pow 1.0.6. При включённом
«Уменьшении движения» эффект отключается.

## Проверка на Mac

Установите Xcode и XcodeGen, затем из этой папки выполните:

```sh
xcodegen generate
xcodebuild -project ChronicNotebook.xcodeproj -scheme ChronicNotebook \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Для физического устройства откройте `ChronicNotebook.xcodeproj` в Xcode.
Выберите команду в Signing & Capabilities и укажите уникальный Bundle ID при
необходимости. Для IPA на зарегистрированных устройствах архивируйте проект
через Product → Archive и экспортируйте его через Xcode Organizer с подходящим
профилем. Для TestFlight используется отдельный экспорт в App Store Connect.

Напоминания, препараты, графики, PDF и остальные функции Android-версии пока
предстоит перенести. Сборка Xcode и подпись на Windows не проверялись.
