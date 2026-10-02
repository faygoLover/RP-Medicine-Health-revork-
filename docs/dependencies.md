# Моды, с которыми работает RP Medicine

Версии — те, что стоят в сборке автора (`DEPARTMENT_s5_client`), сверены по sha1 файлов 02.10.2026.

Репозиторий публичный, поэтому чужие jar в нём не лежат, кроме собственных модов автора. Всё остальное скачивает `scripts/fetch_deps.sh` в папку `external/` (в git не попадает) или подтягивает Gradle по координатам ниже.

## 1. Свои моды (лежат в `libs/`)
| Мод | Версия | Роль | Исходники в репозитории |
|---|---|---|---|
| RP Perks (`rpperks`) | 1.0.3 | обязательная зависимость: перки, уровень «Медицины» | `docs/reference/rpperks/` |
| RP Stamina (`rpstamina`) | 1.0.2 | необязательная: выносливость | `docs/reference/rpstamina/` |

Подключение: `flatDir { dir 'libs' }` и `fg.deobf`.

## 2. Интеграции первого этапа
| Мод | Версия | Лицензия | Gradle | Исходники |
|---|---|---|---|---|
| TaCZ (`tacz`) | 1.1.8-hotfix | GPL-3.0, ассеты отдельно | `maven.modrinth:timeless-and-classics-zero:1.1.8-hotfix` | github.com/MCModderAnchor/TACZ, ветка 1.20.1, коммит `b43eb84` (на нём делался разбор) |
| Zero Contact (`zerocontact`) | 1.1.5-beta, сборка 72 | GPL-3.0-or-later, ассеты без лицензии | `maven.modrinth:zerocontact:1.1.5-beta` | github.com/TuDouNi92/ZeroContact, коммит `36c6a8f` (тот же, что у jar) |
| Simple Voice Chat (`voicechat`) | 2.6.18 | все права защищены; API открыт | `de.maxhenkel.voicechat:voicechat-api:<версия>`, репозиторий `https://maven.maxhenkel.de/repository/public` | github.com/henkelmax/simple-voice-chat, ветка 1.20.1 |
| Curios (`curios`) | 5.14.1+1.20.1 | LGPL-3.0 | `maven.modrinth:curios:5.14.1+1.20.1` или `top.theillusivec4.curios:curios-forge` с maven.theillusivec4.top | github.com/TheIllusiveC4/Curios, ветка 1.20.x |
| Carry On (`carryon`) | 2.1.2.7 | LGPL-3.0 | `maven.modrinth:carry-on:2.1.2.7` | github.com/Tschipp/CarryOn |
| Corpse (`corpse`) | 1.0.23 | — | `curse.maven:corpse-316582:7018272` | — (ничего не вызываем, только не мешаем) |

Репозитории Maven: `https://api.modrinth.com/maven` (группа `maven.modrinth`), `https://cursemaven.com` (группа `curse.maven`).

Все интеграции этого раздела — `compileOnly`, мод обязан запускаться без них. Код GPL-модов в RP Medicine не копируем: работаем через их события и публичные классы.

Что именно брать из TaCZ и Zero Contact (события, классы, порядок расчёта урона) — `docs/00_catalog.md`, раздел 21.

## 3. Оружие и бой, которые идут общим путём
Отдельной поддержки нет: урон приходит как обычный, часть тела — по высоте попадания. Нужны только для проверки, что ничего не ломается.

| Мод | Версия | Лицензия | Где |
|---|---|---|---|
| Superb Warfare | 0.8.9.1-hotfix | GPL-3.0 | Modrinth `superb-warfare` |
| Better Combat | 1.9.0 | все права защищены | Modrinth `better-combat` |
| LR Tactical | 0.4.3 | GPL-3.0 | Modrinth `lr-tactical` |
| TaCZ Tweaks, TaCZ Addon, TaCZ Additions, TaCZ NPCs, LR Armor | — | разные | аддоны TaCZ, стреляют через TaCZ |
| playerAnimator | 1.0.2-rc1 | MIT | Modrinth `playeranimator` — возможная основа для позы лежачего |

## 4. Второй этап и дальше
| Мод | Версия | Лицензия | Для чего | Где |
|---|---|---|---|---|
| Legendary Survival Overhaul | 2.4.2 | GPL-3.0 | жажда, температура, части тела — интеграция | `curse.maven:legendary-survival-overhaul-840254:7603852`; исходники github.com/sfiomn/LegendarySurvivalOverhaul, ветка 1.20.1, коммит `013b06b` |
| Nutritional Balance | 5.1.3 | GPL-3.0 | подсмотреть, как определяется состав блюда по рецепту | CurseForge |
| Industrial Hellscape | 0.3.6A | MIT | блоки госпиталя | CurseForge |
| MOA Decor: Science | — | AFL-3.0 | блоки госпиталя | CurseForge |
| Refurbished Furniture, Furniture Expanded, Multibeds | — | — | кровати и мебель | CurseForge |
| Midnight Thoughts | 1.4 | MIT | сон; не трогаем | Modrinth `midnight-thoughts` |
| Cybernetic System | 1.4.1 | не указана | импланты — аддон | CurseForge |
| Butchery | 5.1 | все права защищены | органы — третий этап | CurseForge |
| Tobacconist, The Dirty Stuff | — | MIT / все права защищены | табак — третий этап | Modrinth `tobacconist` |
| Mekanism | 10.4.16 | MIT | радиация — аддон | Modrinth `mekanism` |

## 5. Моды, которые RP Medicine заменяет
Удаляются из сборки, когда мод готов. Нужны как источник идей, моделей, звуков и как список предметов для датапака `item_aliases`.

| Мод | Версия | Лицензия в mods.toml | Что берём |
|---|---|---|---|
| Tactical Medicine (`tacmed`) | 1.1.0 | MIT | нокдаун, подъём, ранения груди, жгуты; модели и звуки с указанием автора |
| Health & Disease (`health_and_disease`) | 1.4.2 | MIT | лаборатория, анализы, нутриенты; модели и звуки с указанием автора |
| Tactical Aid (`tactical_aid`) | 1.3.9 | AFL-3.0 | инъекторы; модели с указанием автора |
| Medicamod (`medicamod`) | 1.0.0 | не указана | только список препаратов; ассеты не брать |
| Meds and Herbs (`meds_and_herbs`) | 2.0.3 | AFL-3.0 | только список предметов |
| Survival Instinct (`survival_instinct`) | 1.0.2 | AFL-3.0 | остаётся в сборке; бинты и шприцы — в аналоги |
| Player Revive | 2.0.31 | — | выключен, удаляется |

Jar этих модов в репозитории нет. Автор кладёт их вручную в `external/jars/`, когда нужны ассеты; всё взятое записывается в `docs/assets_credits.md`.

## 6. Списки предметов и блоков
В `docs/reference/ids/` лежит по файлу на мод: тип, id, английское и русское название. Собраны из языковых файлов jar.

| Файл | Для чего |
|---|---|
| `tacmed.tsv`, `health_and_disease.tsv`, `medicamod.tsv`, `meds_and_herbs.tsv`, `tactical_aid.tsv`, `survival_instinct.tsv` | датапак `item_aliases`: чужой предмет → наш |
| `legendarysurvivaloverhaul.tsv` | медицинские предметы LSO, которые нужно отключить или привязать |
| `industrialhellscape.tsv`, `moa_decor_science.tsv`, `refurbished_furniture.tsv`, `furnitureexpanded.tsv`, `multibeds.tsv` | блоки госпиталя (второй этап) |
| `tobacconistmod.tsv`, `the_dirty_stuff.tsv`, `butchery.tsv`, `cybernetic_system.tsv`, `nutritionalbalance.tsv` | третий этап и аддоны |

## 7. Чего в репозитории нет
- Конфигов сборки автора (Zero Contact, TaCZ, LSO и других). Значения, важные для интеграции, описаны в `docs/00_catalog.md`, раздел 21.
- Jar модов из раздела 5.
