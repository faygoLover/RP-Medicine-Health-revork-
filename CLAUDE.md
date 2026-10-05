# RP Medicine

Мод Minecraft Forge 1.20.1 (Java 17): детальная система здоровья и медицины для ролевого сервера. Автор — faygoLover (Alex). **С автором общаемся по-русски.**

Мод `rpmedicine`, название «RP Medicine», пакет `faygolover.rpmedicine`, лицензия GPL-3.0 (с 05.10.2026; ассеты родственных модов — `docs/assets_credits.md`), языки ru и en.

## Состояние на 04.10.2026
- **Этап 1 «Поле»** — готов (ветка `stage1`). Отчёт: `docs/stage1_report.md`.
- **Этап 2 «Госпиталь»** — готов в части, не зависящей от спорных решений (ветка `stage2`). Отчёт: `docs/stage2_report.md`.
- **Этап 3 «Операционная»** — в работе (ветка `stage3`, **самая свежая, содержит всё**). Готовы шаги 1–3 из 13: органы, анестезия и интубация, пошаговая хирургия (`core/Surgery.java`). Следующий — шаг 4 (план — в `docs/stage3_report.md`, раздел «На чём остановился»).
- Спорные решения ждут автора: п. 16 в `docs/07_spec_stage2.md`, п. 15 в `docs/08_spec_stage3.md`. Где без выбора нельзя — сделан вариант из ТЗ и вынесен в конфиг; каждый такой выбор записан в `docs/open_issues.md`.
- Тесты: 113 юнит-тестов ядра, 30 GameTest; всё проходит и без необязательных модов, и с ними.
- Ветки `stage1`–`stage3` влиты в `main` (PR #1 и #2, 04.10.2026). С 04.10.2026 работа идёт локально на машине автора, в `main`.
- **Ни один этап ещё не проверен человеком в игре.** Чек-лист — `docs/testing_checklist.md`.

## Сборка, запуск, тесты
JDK 17 обязательно (Forge 1.20.1, ForgeGradle 6, Gradle 8.1.1 через wrapper). На Windows — `gradlew.bat`.
```
./gradlew build                              # jar в build/libs, юнит-тесты ядра
./gradlew test                               # только юнит-тесты (src/test, ядро без Minecraft)
./gradlew runGameTestServer                  # GameTest-сценарии (src/main/java/.../gametest)
./gradlew runGameTestServer -Pwith_optional=true   # то же с TaCZ, Zero Contact, RP Stamina, Curios, Voice Chat, LSO
./gradlew runClient                          # клиент разработки (run/)
./gradlew runServer -Pwith_optional=true     # выделенный сервер с необязательными модами
```
- `-Pwith_optional=true` подтягивает необязательные моды с Modrinth Maven в рантайм (`build.gradle`, блок `withOptional`).
- `bash scripts/fetch_deps.sh` — скачать jar и исходники чужих модов в `external/` (для чтения кода и импорта ассетов; в git не попадает). Нужен `bash` (на Windows — Git Bash).
- После правки данных: `python3 scripts/gen_data.py` (датапак по умолчанию, модели, sounds.json), `python3 scripts/gen_lang.py` (ru_ru и en_us). Сгенерированное коммитится; руками JSON в `src/main/resources` не править.
- `python3 scripts/foreign_assets.py` — заново взять текстуры и звуки Tactical Medicine и Health & Disease из jar в `external/jars` (нужны ImageMagick `convert` и `ffmpeg`). Обычно не нужно: файлы уже в репозитории.
- GameTest-сценарии, которые меняют общие настройки (`MedicalSettings`), держать в своей пачке (`batch`): тесты одной пачки идут параллельно.

## Устройство кода
- `core/` — чистое ядро без Minecraft: состояние пациента (`MedicalState`, `BodyPartState`, `Wound`), физиология (`Physiology`, `Healing`, `Infections`, `Organs`), травмы (`Injuries`), лечение (`Treatments`, `Drugs`), осмотр и приборы (`Examination`, `Diagnostics`), речь (`Speech`), настройки (`MedicalSettings`). Покрыто юнит-тестами.
- `server/` — слой Minecraft: урон (`DamageHandler`), шаг пациента (`PatientTicker`), лечение предметами (`TreatmentService`), нокдаун и смерть (`DownedService`), заглушки офлайн-игроков (`StubService`), кровь, лаборатория, мини-игры, выживание (голод, жажда, LSO), ванильные эффекты, панель ГМа.
- `hospital/` — функции чужих блоков (датапак `hospital_blocks`), койка, монитор, операционный стол.
- `medcard/`, `stats/` — медкарта и журнал ГМа (файлы в `world/rpmedicine/`).
- `client/` — HUD, панель осмотра, экраны (мини-игры, медкарта, панель ГМа), эффекты экрана и звука.
- `integration/` — TaCZ, Zero Contact, LSO, Simple Voice Chat, RP Perks: классы грузятся, только если мод установлен.
- `network/` — пакеты (версия протокола в `Network.PROTOCOL`, поднимать при изменении формата).
- `config/ServerConfig` — все числа `MedicalSettings` в `rpmedicine-server.toml` (комментарии по-русски).
- Датапак: `data/rpmedicine/rpmedicine/{damage_sources,items,item_aliases,drugs,drinks,hospital_blocks,mobs}` — формат в `docs/datapack.md`.

## Что читать и в каком порядке
1. `docs/stage3_report.md` — где остановились и что дальше; `docs/08_spec_stage3.md` — ТЗ третьего этапа.
2. `docs/01_decisions.md` — все принятые решения. Пункты 1.8–1.12 новее и главнее 1.1–1.7.
3. `docs/open_issues.md` — выборы, сделанные без автора, по этапам.
4. `docs/testing_checklist.md` — что проверить руками в игре (то, что нельзя проверить без клиента).
5. ТЗ и отчёты прошлых этапов: `05_spec_stage1.md` + `stage1_report.md`, `07_spec_stage2.md` + `stage2_report.md`.
6. `docs/00_catalog.md` — справочник механик (Neurotrauma, Casualties Unknown, Tarkov, RimWorld, моды сборки). Большой; читать нужные разделы.
7. `docs/dependencies.md` — все моды, с которыми работает RP Medicine: версии, лицензии, координаты Gradle. Списки предметов и блоков чужих модов — `docs/reference/ids/`.
8. `docs/assets_credits.md` — чужие ассеты (MIT), каждый файл с автором; файл едет в jar.
9. История: `docs/02–04_questions.md` (уже учтены в решениях), `docs/06_cloud_task.md` (задание облачной сессии), `docs/zc_request.md` (запрос в Zero Contact, ответа нет).

## Главные принципы
- Ванильное здоровье скрыто и всегда полное. Урон превращается в травмы на девяти частях тела. Настоящая жизнь — мозг.
- Игрок видит ощущения, а не цифры. Цифры дают приборы, панель медика и ГМ-сканер.
- Все расчёты на сервере, клиенту уходят только изменения и только то, что ему положено видеть.
- Все числа — в конфиге или датапаке.
- В бою лечение через прогресс-бар; вне боя — мини-игры.
- Заживление идёт по времени игрока в сети, не по игровым суткам.
- Навыки не растут сами: уровень «Медицины» 0–10 выставляет ГМ (`/rpmedicine skill` или атрибут RP Perks).
- Интеграции мягкие, кроме RP Perks (обязательная зависимость). Сталкер, психика, эпидемии, сложные протезы, NPC — аддоны, не здесь.

## Соседние моды автора
Лежат в соседних папках на машине автора, на GitHub их нет. Собранные jar — в `libs/`, ключевые исходники — в `docs/reference/`.
- **RP Perks** (`rpperks`, `libs/rpperks-1.0.3-1.20.1-forge.jar`). Перки: enum `faygolover.rpperks.perk.Perk`; у игрока capability `PlayerPerksProvider.PLAYER_PERKS` → `PlayerPerks.hasPerk(Perk)`. Атрибута «Медицина» в RP Perks пока нет; RP Medicine читает `rpperks:medicine` по id, если его нет — свой уровень из `/rpmedicine skill`, иначе из перков: `FIRST_AID` → 3, `MEDIC` → 8, иначе 0.
- **RP Stamina** (`rpstamina`, `libs/rpstamina-1.0.2-1.20.1-forge.jar`). `StaminaAPI` (только сервер), атрибуты `rpstamina:max_stamina`, `regen_multiplier`, `cost_multiplier`. Редактор HUD RP Medicine сделан по образцу его `HudEditorScreen`.

## Чужие моды
| Мод | Версия в сборке | Где взять |
|---|---|---|
| TaCZ | 1.1.8-hotfix | Modrinth `timeless-and-classics-zero`; исходники github.com/MCModderAnchor/TACZ, ветка 1.20.1 |
| Zero Contact | 1.1.5-beta (сборка 72) | Modrinth `zerocontact`; исходники github.com/TuDouNi92/ZeroContact |
| Simple Voice Chat | 2.6.18 | API: `de.maxhenkel.voicechat:voicechat-api`, maven.maxhenkel.de |
| LSO | 2.4.2 | Modrinth `legendary-survival-overhaul`; исходники github.com/sfiomn/LegendarySurvivalOverhaul, ветка 1.20.1 |
| Curios | 5.14.1 | maven.theillusivec4.top |

Лицензии: код Zero Contact, TaCZ и LSO — GPL-3.0; их код в мод не копируем, работаем через события и API. Ассеты Tactical Medicine и Health & Disease (MIT) взяты с указанием авторов; всё взятое — в `docs/assets_credits.md` с пометкой «заменить позже».

## Правила работы
С 05.10.2026 работаем так: автор говорит, что делать, я делаю **большими пачками правок**, о сомнительном спрашиваю, потом **проверяем вместе в игре** (сервер разработки и два клиента, `Dev` и `Patient`). Результаты проверки — `docs/test_plan.md`, разбор — `docs/test_review.md`.

- **Мод только для локального использования.** На GitHub ничего не отправлять (никаких push), коммиты — только локально.
- **Ассеты:** можно брать всё из `docs/reference/родственные моды/` и других модов автора, с указанием авторов в `docs/assets_credits.md` (автор часто указан в самом файле модели). Лицензию RP Medicine можно менять под это. Вопрос лицензий автору больше не поднимать: он сам связывается с авторами.
- Папку `docs/reference/родственные моды/` не коммитить (в `.gitignore`).
- Сборка автора — только для понимания, какие моды стоят; ориентир по ассетам и механикам — «родственные моды».
- Своя проверка перед сдачей: `gradlew.bat build` (юнит-тесты) и `runGameTestServer`. Ошибку чиню сам, тесты не отключаю.

**Решения**
- Спорное не решаю сам и не откладываю в `docs/open_issues.md` молча — спрашиваю автора. Если ответ нужен не сразу, предлагаю вариант по умолчанию и прямо говорю, что выбрал.
- Не добавляю то, чего нет в ТЗ или в согласованном плане шага.
- Ответы автора записываю в `docs/01_decisions.md` (или в раздел подтверждений нужного ТЗ).

**Документы**
- `docs/testing_checklist.md` — обновлять после каждого шага: что проверено автором, что нет.
- Отчёты этапов (`docs/stageN_report.md`) — дописывать по ходу.
- Комментарии в коде и строки конфига — по-русски.

**Запуск для проверки**
- JDK 17: `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.14.7-hotspot"`.
- Сервер: `gradlew.bat runServer -Pwith_optional=true` (папка `run/`, офлайн-режим, `Dev` и `Patient` — операторы, EULA принята автором).
- Клиенты: `gradlew.bat runClient -Pwith_optional=true -Pusername=Dev -Pquickplay=localhost` и то же с `Patient`.

**Прочее**
- CurseForge закрыт проверкой на ботов; описания модов — через `api.cfwidget.com`, файлы — Modrinth или `edge.forgecdn.net/files/<первые 4 цифры id>/<остаток>/<имя>`.
- Сборка автора (клиент): `%APPDATA%\ElyPrismLauncher\instances\DEPARTMENT_s5_client\minecraft`. Список важных модов — `docs/01_decisions.md`, п. 1.11.
