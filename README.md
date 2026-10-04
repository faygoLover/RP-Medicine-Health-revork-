# RP Medicine

Мод для Minecraft Forge 1.20.1: детальная система здоровья и медицины для ролевого сервера. Ванильное здоровье скрыто — урон превращается в раны на девяти частях тела, физиология (кровь, давление, пульс, SpO₂, мозг) считается на сервере, игрок видит ощущения, а не цифры. Нокдаун и клиническая смерть, первая помощь, госпиталь (функции для блоков из модов мебели), кровь и переливание, инфекция и сепсис, лекарства, приборы и лаборатория, мини-игры лечения, медкарта, панель ГМа; на третьем этапе — органы, анестезия, хирургия.

Автор — faygoLover. Лицензия MIT; чужие ассеты — в [docs/assets_credits.md](docs/assets_credits.md).

## Ветки
| Ветка | Что в ней |
|---|---|
| `stage3` | **самая свежая**: этапы 1–2 и начало 3 (органы, анестезия) |
| `stage2` | этапы 1–2 |
| `stage1` | этап 1 |
| `main` | документы первого раунда |

## Сборка
Нужен JDK 17.
```
./gradlew build                 # jar в build/libs
./gradlew test                  # юнит-тесты ядра
./gradlew runGameTestServer     # сценарии GameTest (добавить -Pwith_optional=true — с TaCZ, Zero Contact, LSO и др.)
./gradlew runClient             # клиент разработки
```
На Windows — `gradlew.bat`. Обязательная зависимость в игре — RP Perks (`libs/`), остальные интеграции мягкие: TaCZ, Zero Contact, RP Stamina, Curios, Carry On, Simple Voice Chat, Legendary Survival Overhaul.

## Документы
- [CLAUDE.md](CLAUDE.md) — контекст проекта: состояние, устройство кода, правила работы.
- [docs/testing_checklist.md](docs/testing_checklist.md) — ручная проверка в игре.
- ТЗ: [этап 1](docs/05_spec_stage1.md), [этап 2](docs/07_spec_stage2.md), [этап 3](docs/08_spec_stage3.md); отчёты: [1](docs/stage1_report.md), [2](docs/stage2_report.md), [3](docs/stage3_report.md).
- [docs/01_decisions.md](docs/01_decisions.md) — принятые решения; [docs/open_issues.md](docs/open_issues.md) — выборы без автора.
- [docs/datapack.md](docs/datapack.md) — формат датапака (урон, предметы, препараты, блоки госпиталя, напитки, мобы).
