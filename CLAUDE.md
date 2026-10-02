# RP Medicine

Мод Minecraft Forge 1.20.1 (Java 17): детальная система здоровья и медицины для ролевого сервера. Автор — faygoLover (Alex). **С автором общаемся по-русски.**

Мод `rpmedicine`, название «RP Medicine», пакет `faygolover.rpmedicine`, лицензия MIT, языки ru и en.

## Состояние на 02.10.2026
Исследование и дизайн закончены, кода нет. Следующий шаг — первый этап, «Поле».

## Что читать и в каком порядке
1. `docs/05_spec_stage1.md` — ТЗ на первый этап. Главный документ для работы.
2. `docs/01_decisions.md` — все принятые решения. Пункты 1.8–1.12 новее и главнее пунктов 1.1–1.7 там, где расходятся.
3. `docs/06_cloud_task.md` — задание и порядок работы для сессии, которая пишет первый этап.
4. `docs/00_catalog.md` — справочник механик из Neurotrauma, Casualties Unknown, Tarkov, RimWorld и модов сборки. Большой; читать нужные разделы. Раздел 20 — Tactical Medicine, Health & Disease, Medicamod; раздел 21 — LSO, TaCZ, Zero Contact (события, классы, порядок расчёта урона).
5. `docs/zc_request.md` — запрос разработчику Zero Contact на API. Ответа пока нет.
6. `docs/02_questions.md`, `03_questions.md`, `04_questions.md` — история вопросов и ответов, уже учтена в `01_decisions.md`.

## Главные принципы
- Ванильное здоровье скрыто и всегда полное. Урон превращается в травмы на девяти частях тела. Настоящая жизнь — мозг.
- Игрок видит ощущения, а не цифры. Цифры дают приборы, панель медика и ГМ-сканер.
- Все расчёты на сервере, клиенту уходят только изменения и только то, что ему положено видеть.
- Все числа — в конфиге или датапаке.
- В бою лечение через прогресс-бар; мини-игры — второй этап.
- Заживление идёт по времени игрока в сети, не по игровым суткам.
- Навыки не растут сами: уровень «Медицины» 0–10 выставляет ГМ через RP Perks.
- Интеграции мягкие, кроме RP Perks (обязательная зависимость). Сталкер, психика, эпидемии, сложные протезы, NPC — аддоны, не здесь.

## Соседние моды автора
Лежат в соседних папках на машине автора, на GitHub их нет. Собранные jar — в `libs/`, ключевые исходники — в `docs/reference/`.
- **RP Perks** (`rpperks`, `libs/rpperks-1.0.3-1.20.1-forge.jar`). Перки: enum `faygolover.rpperks.perk.Perk`; у игрока capability `PlayerPerksProvider.PLAYER_PERKS` → `PlayerPerks.hasPerk(Perk)`. Характеристики — enum `Stat`, атрибуты других модов подключаются по id.
  Атрибута «Медицина» в RP Perks пока нет. Его добавит автор локально как `rpperks:medicine` (0–10). RP Medicine читает атрибут по id из реестра; если его нет, уровень берётся из перков: `FIRST_AID` → 3, `MEDIC` → 8, иначе 0.
- **RP Stamina** (`rpstamina`, `libs/rpstamina-1.0.2-1.20.1-forge.jar`). `StaminaAPI` (только сервер), атрибуты `rpstamina:max_stamina`, `regen_multiplier`, `cost_multiplier` и другие. Редактор HUD (`HudEditorScreen`, команда `/staminahud`) — образец для редактора HUD в RP Medicine.
- Сборку (ForgeGradle, Gradle 8.1.1, official mappings) берём как в RP Stamina: `docs/reference/rpstamina/build.gradle`, `settings.gradle`, `gradle.properties`.

## Чужие моды
| Мод | Версия в сборке | Где взять |
|---|---|---|
| TaCZ | 1.1.8-hotfix | Modrinth `timeless-and-classics-zero`; исходники github.com/MCModderAnchor/TACZ, ветка 1.20.1 |
| Zero Contact | 1.1.5-beta (сборка 72) | Modrinth `zerocontact`; исходники github.com/TuDouNi92/ZeroContact |
| Simple Voice Chat | 2.6.18 | API: `de.maxhenkel.voicechat:voicechat-api`, maven.maxhenkel.de |
| LSO | 2.4.2 | github.com/sfiomn/LegendarySurvivalOverhaul, ветка 1.20.1 (интеграция — второй этап) |
| Curios | 5.14.1 | maven.theillusivec4.top |

Лицензии: код Zero Contact и TaCZ — GPL-3.0; их код в мод не копируем, работаем через события и API. Ассеты Tactical Medicine и Health & Disease (MIT) можно брать с указанием авторов; всё взятое записывать в `docs/assets_credits.md` с пометкой «заменить позже».

## Правила работы
- Комментарии в коде и строки конфига — по-русски, как в RP Stamina и RP Perks.
- Не добавлять в мод то, чего нет в ТЗ этапа. Спорное — записать в `docs/open_issues.md`, а не решать молча.
- Если что-то из ТЗ сделать не удалось или сделано иначе — так и написать в отчёте.
- CurseForge закрыт проверкой на ботов; описания модов доступны через `api.cfwidget.com`, файлы — через Modrinth.
- Сборка автора (клиент): `%APPDATA%\ElyPrismLauncher\instances\DEPARTMENT_s5_client\minecraft`. Список важных модов — `docs/01_decisions.md`, п. 1.11.
