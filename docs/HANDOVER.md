# Передача проекта (07.10.2026)

Для брата автора и его Claude. Автор (Alex, faygoLover) может уехать; этот файл — точка входа. Всё, что раньше жило в памяти Claude автора, перенесено сюда и в `CLAUDE.md`.

## С чего начать
1. `CLAUDE.md` (корень) — устройство кода, сборка, правила. Его Claude читает автоматически.
2. **`docs/10_design_final.md`** — итоговый дизайн: что сделано, что сломано, что дальше, открытые вопросы.
3. `docs/01_decisions.md` — решения автора (главный источник правды; пункты 1.13–1.17 самые свежие).
4. `docs/test_findings.md` — все замечания проверок; последний раздел «Живой тест с игроками — 07.10.2026» (42 пункта, не исправлены).
5. Остальное — по ссылкам из `CLAUDE.md` («Что читать и в каком порядке»).

**Следующий шаг по плану:** пачка 1 из `10_design_final.md` §5.1 (баги живого теста), потом пачка 2 и 3, потом RP Core (§3, §6). Автор просил по Core и голоду сначала дизайн-документ и согласование, код — после.

## Правила работы (как договорились с автором)
- Общение — **по-русски**. Комментарии в коде и строки конфига — по-русски.
- **Никаких push** — только локальные коммиты (репозиторий когда-то был на GitHub, сейчас работа локальная).
- Работать **большими пачками**: сделал много правок → список сделанного и выбранного → проверка в игре вместе. Не дёргать по каждой мелочи; спрашивать только действительно спорное и давать вариант по умолчанию.
- **Во время совместной проверки в игре ничего не править** — только записывать замечания в `docs/test_findings.md`. Разбор и правки — после блока проверок.
- Ассеты можно брать из других модов (`docs/reference/родственные моды/` и модов автора) — **с указанием авторов в `docs/assets_credits.md`**. Вопрос лицензий автору не поднимать.
- Не коммитить `docs/reference/родственные моды/` (в `.gitignore`).
- Перед сдачей: `gradlew.bat build` (юнит-тесты) и `gradlew.bat runGameTestServer` (сервер разработки должен быть остановлен). Тесты не отключать — чинить.
- Ответы автора — в `docs/01_decisions.md`; сделанное — в `test_findings.md` («Сделано после…») и в чек-листе.
- Тестовый мир разработки — «чистый»: без побочек чужих модов (температура LSO выключена, мебель не залита водой). После перезапуска сервера всегда выдавать OP игрокам `Dev` и `Patient`.

## Окружение на новой машине
- **JDK 17** (Eclipse Adoptium). У автора: `C:/Program Files/Eclipse Adoptium/jdk-17.0.14.7-hotspot` — поправить `JAVA_HOME` под себя.
- Gradle 8.1.1 через wrapper (`gradlew.bat`), ForgeGradle 6, Forge **47.4.13**, маппинги official.
- **Python 3** — для генераторов в `scripts/` (данные, языки, книга, модели, мир). Для `foreign_assets.py` — ImageMagick и ffmpeg (обычно не нужен).
- Git Bash — для `scripts/fetch_deps.sh`.

### Что НЕ лежит в git и откуда взять
| Что | Зачем | Откуда |
|---|---|---|
| `libs/pack/*.jar` (мебель IH, MOA, H&D, Refurbished Furniture, multibeds, butchery…) | тестовая площадка `-Pwith_pack=true` | `python scripts/test_site.py --pack` копирует из сборки автора `%APPDATA%\ElyPrismLauncher\instances\DEPARTMENT_s5_client\minecraft\mods`; на другой машине — скачать те же версии (список — `docs/01_decisions.md` п. 1.11, `docs/dependencies.md`) |
| `external/` | исходники и jar чужих модов для чтения | `bash scripts/fetch_deps.sh` |
| `docs/reference/родственные моды/` | ассеты и примеры моделей | папка автора; при передаче — скопировать вручную |
| `run/`, `run_live/` | сервер/клиенты разработки и живого теста | создаются сами; `run_live` — `python scripts/live_world.py` |
| `dist/` | моды для игроков живого теста | собрать заново (см. ниже) |

`libs/rpperks-*.jar`, `libs/rpstamina-*.jar`, `libs/patchouli-*.jar` — в git (нужны для сборки).

## Сборка, запуск, тесты
```
gradlew.bat build                          # jar в build/libs + юнит-тесты
gradlew.bat test                           # только юнит-тесты ядра
gradlew.bat runGameTestServer              # GameTest (39 шт.); с модами: -Pwith_optional=true
gradlew.bat runServer -Pwith_optional=true # сервер разработки (run/, офлайн, RCON 25575, пароль rpmtest)
gradlew.bat runClient -Pwith_optional=true -Pusername=Dev -Pquickplay=localhost   # и то же с Patient
gradlew.bat runServer -Plive=true          # сервер живого теста (run_live/)
```
- `-Pwith_optional=true` — TaCZ, Zero Contact, RP Stamina, Curios, Voice Chat, LSO с Modrinth Maven.
- `-Pwith_pack=true` — мебель сборки из `libs/pack`.
- После правки данных: `python scripts/gen_data.py`, `python scripts/gen_lang.py`, `python scripts/gen_book.py`. Сгенерированное коммитится, руками JSON в `src/main/resources` не править.
- Модели и картинки предметов/блоков — `scripts/item_art.py`, `hospital_art.py`, `iv_stand_art.py`, `art_drawn.py`, `status_icons.py`.
- Тестовая площадка в dev-мире: `python scripts/test_site.py` (датапак `rpm_test`), в игре `/execute at Dev run function rpm_test:site`.
- Наборы: `/rpmedicine kit <field|resus|diag|surgeon|transplant|drugs|substances|food|gm|all> [игроки]`.

### Живой тест с игроками
- `python scripts/live_world.py` — готовит `run_live/`: офлайн-режим, порт **25565**, RCON выключен, OP — `obj_a-001` (ник автора), плоский мир с госпиталем из наших блоков и стойками кнопок (наборы, лечение, уровни, время). Мир строится датапаком при первом запуске; потом `--clean` (убрать датапак). Пересобрать мир: удалить `run_live/world`, `live_world.py --rcon`, запустить, затем `--clean` и `--no-rcon`.
- Голая сборка для игроков: RP Medicine + RP Perks + Patchouli на Forge 1.20.1-47.4.13. Папка `dist/RP Medicine тест/` (mods + config + README) — собрать: `gradlew.bat build`, скопировать `build/libs/rpmedicine-*.jar` и три jar из `libs/`.
- Включить что-то в конфиге на ходу: править `run_live/world/serverconfig/rpmedicine-server.toml` **перезаписью на месте** (не `sed -i`: наблюдатель Forge не видит подмену файла). Ключи в файле — snake_case (`no_death_mode`), в коде — camelCase (`noDeathMode`).
- На живом тесте 07.10 включён `no_death_mode = true`.

## Грабли (уже наступали)
- **Сеть автора:** на его ПК включается WireGuard VPN — входящие подключения к серверу на ПК ломаются (ответы уходят в туннель). Для теста VPN выключать. Внешний IP дома автора — статический Beeline; проброс 25565 на роутере → ПК `192.168.0.129`.
- После принудительного закрытия dev-клиента портится конфиг LSO: удалить `run/client/config/legendarysurvivaloverhaul/legendarysurvivaloverhaul-common*.toml*`.
- `ArmPose` (поза рук) создавать рано — в `RegisterKeyMappingsEvent`, иначе вылет клиента.
- Пакеты не должны ссылаться на клиентские классы напрямую — через `ClientHandlers`.
- Руки от первого лица (`FirstPersonGeo`): двухпроходный рендер и `entityCutoutNoCull`, иначе руки не видно.
- Результат взаимодействия — `CONSUME`, не `SUCCESS` (иначе сервер рассылает взмах руки).
- Гео-блоки H&D: поворот `-f.toYRot()` в `HospitalGeoRenderer`.
- Меню с данными — `NetworkHooks.openScreen`, не `ServerPlayer.openMenu`.
- `PoseStack.Pose` — конструктор закрыт: хранить `Matrix4f`/`Matrix3f`.
- GameTest, меняющие `MedicalSettings`, — в своей пачке (`batch`): тесты одной пачки идут параллельно.
- Bash heredoc с Python и `\n` внутри строк ломается — писать скрипты файлом.

## Соседние моды и сборка
- Соседние папки на машине автора: `RP Perks` (rpperks), `RP Stamina` (rpstamina), `RP PDA and communications` (пусто). На GitHub их нет; jar — в `libs/`, ключевые исходники — `docs/reference/`. **RP Core ещё не существует** — план в `10_design_final.md` §3.
- Перки сейчас: `faygolover.rpperks.perk.Perk`; capability `PlayerPerksProvider.PLAYER_PERKS`. Атрибута «Медицина» в RP Perks нет — RP Medicine хранит свой уровень (`/rpmedicine skill`), иначе `FIRST_AID` → 3, `MEDIC` → 8. По решению 07.10 всё это переезжает в RP Perks.
- Сборка сервера автора (клиент): `%APPDATA%\ElyPrismLauncher\instances\DEPARTMENT_s5_client\minecraft`. Тестовая сборка с Ely.by: `%APPDATA%\ElyPrismLauncher\instances\1.20.1(1)`.

## Сервер автора «Департамент S5» (не этот мод, но спрашивают)
- Proxmox `192.168.0.183` (`faygopower`), LXC-контейнер CT100 (`192.168.0.184`), панель Crafty `https://192.168.0.184:8443`. Доступ по SSH-ключу был настроен **только с ПК автора**.
- Папка сервера: `/var/opt/minecraft/crafty/crafty-4/servers/18d0450f-af7d-40e3-86df-eb228a0b9150/`, игровой порт **24511**, внешний адрес `95.31.19.9:24511`. Java работает от пользователя `crafty` — файлы, залитые от root, ломают моды (`chown -R crafty:crafty`).
- 07.10 найдена утечка памяти (дамп кучи): вышедших игроков держат **Sanity: Descent Into Madness** (`sanitydim` 1.1.0, `InnerEntitySpawner.PLAYER_TO_SPAWN_TIMEOUT` — HashMap с игроком-ключом) и **Tacz Attribute Add** (`taa` 1.4.0, `ShooterContext.shooterContext` — WeakHashMap с игроком в значении). Corpse и cosmeticcorpsecompat не виноваты (CTL ошибается). Варианты: ежедневный автоперезапуск в Crafty или мод-заплатка, чистящая эти поля при выходе. Не сделано.
- Спам в логах панели Crafty (`schedule.log`, `session.log` с `WebSocketClosedError`) — безвреден.
