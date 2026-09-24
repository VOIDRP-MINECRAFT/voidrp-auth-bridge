# TODO

- [x] Регистрация NeoForge-пакетов (`AuthPayloadRegistrar`, `ConsumePlayTicketPayload`, `AuthStatusPayload`).
- [x] Очистка состояния при выходе игрока (`PlayerLoggedOutEvent`).
- [x] Команда `/login` (запасной вход паролем).
- [x] Локализованные сообщения игроку (`lang/ru_ru.json`, `lang/en_us.json`).
- [x] Финальный формат билета лаунчера (`play-ticket.json` → `ConsumePlayTicketPayload`).
- [x] Настройки входа без рестарта (опрос `/api/v1/server/auth/settings`).
- [ ] Конфиг-файл вместо одних JVM-флагов.
- [ ] Тесты на разбор JSON и обработку ошибок бэкенда.
