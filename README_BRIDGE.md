# VoidRP NeoForge Auth Bridge

Готовый каркас проекта под **Minecraft 1.21.1 + NeoForge 21.1.218**.

## Что уже разложено
- Gradle-проект под NeoForge через ModDevGradle
- базовый `@Mod` entrypoint
- структура пакетов под client/common/server/bootstrap/integration
- DTO-модели под play-ticket и backend auth
- HTTP-клиент на Java 21 для обращения к backend API
- state store для отметки аутентифицированных игроков
- заготовка под legacy `/login`
- TOML-метаданные мода
- языковые файлы `en_us` и `ru_ru`
- документация по структуре и TODO

## Важное ограничение
В этот skeleton **не вложен `gradle-wrapper.jar`**. Поэтому из коробки `gradlew.bat` покажет ошибку, пока ты не:
1. либо не сгенерируешь wrapper через локальный Gradle
2. либо не скопируешь `gradle-wrapper.jar` из чистого Gradle 8.8 проекта

См. `gradle/wrapper/gradlew-wrapper-missing.txt`.

## Быстрый старт после восстановления wrapper
```bat
gradlew.bat tasks
gradlew.bat runClient
gradlew.bat runServer
```

## JVM properties для backend
Можно переопределять без перекомпиляции:
- `-Dvoidrp.auth.backend=https://void-rp.ru`
- `-Dvoidrp.auth.timeoutMs=5000`
- `-Dvoidrp.auth.ticketPath=C:/path/to/play-ticket.json`

## Что дальше нужно дожать
- реальная регистрация packet payload под NeoForge 1.21.1
- привязка client login event -> отправка payload
- привязка server receiver -> `PlayTicketConsumeService`
- команда `/login <password>`
- интеграция с уже существующим ограничением действий для неавторизованных игроков
