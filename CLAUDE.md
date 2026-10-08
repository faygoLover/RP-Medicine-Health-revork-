# RP Medicine

Мод Minecraft Forge 1.20.1 (Java 17): детальная система здоровья и медицины для ролевого сервера. Автор — faygoLover (Alex). **С автором общаемся по-русски.**

Мод `rpmedicine`, название «RP Medicine», пакет `faygolover.rpmedicine`, лицензия GPL-3.0 (с 05.10.2026; ассеты родственных модов — `docs/assets_credits.md`), языки ru и en.

## Состояние на 07.10.2026
**Проект передаётся** (автор может уехать, продолжит его брат со своим Claude). Начинать с **`docs/HANDOVER.md`**, затем **`docs/10_design_final.md`** (итоговый дизайн: что сделано, что сломано, план, открытые вопросы).
- Все три этапа (поле, госпиталь, операционная) сделаны; работа идёт локально в `main`, без push.
- Две совместные проверки в игре (05–06.10) и живой тест с игроками (07.10, голая сборка, `run_live/`). Замечания живого теста (42) записаны в `docs/test_findings.md` и разобраны в `10_design_final.md` §5 — **не исправлены**.
- Чек-лист `docs/testing_checklist.md`: пройдено 51 из 60 (дальше — п. 52, диабет).
- Тесты: 154 юнит-теста ядра, 39 GameTest — проходили на 07.10.
- Впереди после багов: RP Core (слияние RP Perks, RP Stamina, RP Medicine, Кулинарии), голод «как в жизни», `/rptime` — сначала дизайн и согласование с автором.

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
0. `docs/HANDOVER.md` и `docs/10_design_final.md` — точка входа и итоговый план.
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
- Навыки не растут сами: уровень «Медицины» 0–10 выставляет ГМ (`/rpmedicine skill` или `/rpcore skill`; с RP Perks хранится в нём).
- Интеграции мягкие; обязателен только RP Core. Сталкер, психика, эпидемии, сложные протезы, NPC — аддоны, не здесь.

## RP Core (с 08.10.2026)
RP Medicine 0.2.0 требует только **RP Core** (`rpcore`, соседняя папка `RP Core`, план — `RP Core/docs/00_plan.md`). RP Perks и RP Stamina — необязательные: навыки и черты Medicine берёт через `RpCoreAPI` (`integration/CoreCompat`), HUD — элементы общего HUD Core (`MedicalHud.CoreElement`), редактор — общий (`/rphud`). Без RP Perks уровень «Медицины» у всех максимальный (если ГМ не выставил свой). Jar Core, Perks, Stamina — в `libs/`; в разработке по умолчанию стоят все, проверка без них: `-Pno_perks=true`, `-Pno_stamina=true`. **Важно:** ForgeGradle кэширует jar из `libs/` по версии — после пересборки соседнего мода с той же версией удалить `~/.gradle/caches/forge_gradle/deobf_dependencies/blank/<мод>/<версия>*`. Навыки (оружие, Сила, рост) и телосложение — `RP Core/docs/00_plan.md`, шаг 2.

## Соседние моды автора
Лежат в соседних папках на машине автора, на GitHub их нет. Собранные jar — в `libs/`, ключевые исходники — в `docs/reference/`.
- **RP Perks** (`rpperks` 1.1.0, `libs/`). Ведёт навыки семейства для RP Core (`rpperks/compat/CoreTraits`): уровень ГМа, иначе по перкам — `FIRST_AID` → 3, `MEDIC` → 8; черты по перкам. Medicine классы Perks не использует.
- **RP Stamina** (`rpstamina` 1.1.0, `libs/`). `StaminaAPI` (только сервер), атрибуты `rpstamina:max_stamina`, `regen_multiplier`, `cost_multiplier`. Полоска — элемент общего HUD Core.

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
- Тестовая площадка: `python scripts/test_site.py` кладёт датапак `rpm_test` в `run/world/datapacks` (ванильные блоки вместо мебели госпиталя, вещества на мёде/борще/бумаге), потом в игре `/execute at Dev run function rpm_test:site`. RCON сервера разработки включён (порт 25575, пароль `rpmtest`).
- **Живой тест с игроками** (с 07.10.2026): `python scripts/live_world.py` готовит `run_live/` (офлайн, порт 25565, RCON выключен, оператор `obj_a-001`, плоский мир с госпиталем и кнопками наборов; голая сборка — RP Medicine + RP Perks + Patchouli). Запуск: `gradlew.bat runServer -Plive=true`. Моды для игроков — `dist/RP Medicine тест/`.
- Наборы предметов: `/rpmedicine kit <field|resus|diag|surgeon|transplant|drugs|substances|food|gm|all> [игроки]`.

**Прочее**
- CurseForge закрыт проверкой на ботов; описания модов — через `api.cfwidget.com`, файлы — Modrinth или `edge.forgecdn.net/files/<первые 4 цифры id>/<остаток>/<имя>`.
- Сборка автора (клиент): `%APPDATA%\ElyPrismLauncher\instances\DEPARTMENT_s5_client\minecraft`. Список важных модов — `docs/01_decisions.md`, п. 1.11.
