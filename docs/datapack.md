# Датапак RP Medicine

Всё лежит в `data/<пространство>/rpmedicine/...`. Встроенные значения — в jar мода (`data/rpmedicine/rpmedicine/`), их можно перекрыть датапаком мира с тем же путём или добавить свои файлы. Перечитать: `/rpmedicine reload`.

Генератор встроенных файлов — `scripts/gen_data.py` (правится там, потом `python3 scripts/gen_data.py`).

## damage_sources — тип урона → травма
`data/<ns>/rpmedicine/damage_sources/<имя>.json`

```json
{
  "priority": 20,
  "damage_types": ["minecraft:arrow", "#minecraft:is_explosion"],
  "attacker_items": ["#minecraft:swords"],
  "attacker_entities": ["minecraft:wolf", "#rpmedicine:biting_mobs"],
  "wound": "stab",
  "location": "hit_point",
  "severity_multiplier": 1.0,
  "high_fall_damage": 10,
  "complications": {
    "fracture":      {"min_severity": 15, "per_severity": 0.012, "max": 0.45},
    "open_fracture_fraction": 0.1,
    "arterial":      {"min_severity": 15, "per_severity": 0.01, "max": 0.3},
    "internal":      {"min_severity": 5,  "per_severity": 0.02, "max": 0.6},
    "foreign_body":  {"min_severity": 0,  "per_severity": 0, "max": 0.35, "count_min": 1, "count_max": 1},
    "concussion":    {"min_severity": 10, "per_severity": 0.03, "max": 0.8, "amount_per_severity": 2.0},
    "pneumothorax":  {"min_severity": 5,  "per_severity": 0.03, "max": 0.7}
  },
  "physiology": {"spo2_per_damage": 0, "brain_per_damage": 0, "blood_per_damage": 0}
}
```

- Правило подходит, если тип урона есть в `damage_types` и (если заданы) предмет в главной руке атакующего — в `attacker_items`, атакующий — в `attacker_entities`. Из подходящих берётся правило с наибольшим `priority`. Ничего не подошло — `rpmedicine:default`.
- `wound`: `bruise`, `cut`, `stab`, `gunshot`, `shrapnel`, `burn`, `bite` или `none` (без ран, только `physiology` — утопление, голод, магия).
- `location`: `hit_point` (по точке удара), `fall`, `explosion`, `fire`, `lava`, `random`, `head`, `legs`, `chest`.
- Тяжесть раны = урон × 5 × `severity_multiplier`. Шанс осложнения = `(тяжесть − min_severity) × per_severity`, не больше `max`.
- Правила без `damage_types` вызываются по id: `rpmedicine:gun/penetration`, `gun/plate`, `gun/plate_heavy`, `gun/ricochet`, `gun/helmet` (исход пули TaCZ), `command/<тип>` (команда `injure`).

## items — свойства наших предметов
`data/<ns>/rpmedicine/items/<имя>.json`

```json
{"item": "rpmedicine:bandage", "action": "bandage", "seconds": 4, "min_level": 0, "consume": true}
```

Действия: `bandage`, `pressure_dressing`, `hemostatic`, `tourniquet`, `esmarch`, `splint`, `occlusive`, `needle`, `painkiller`, `morphine`, `adrenaline`, `txa`, `surgical_kit`, `saline`, `ammonia`, `airway`, `ambu` (удержание), `defibrillator`, `pulse_oximeter`, `tonometer`.

## item_aliases — чужой предмет → наш
`data/<ns>/rpmedicine/item_aliases/<имя>.json`

```json
{"aliases": {"survival_instinct:bandage": "rpmedicine:bandage"}}
```

Чтобы чужой предмет можно было класть в подсумок и он показывал сводку при наведении, его нужно добавить и в тег `rpmedicine:medical_items` (`data/rpmedicine/tags/items/medical_items.json`, с `"required": false`).

## mobs — упрощённые травмы мобов
`data/<ns>/rpmedicine/mobs/<имя>.json`. По умолчанию список пуст — у всех мобов ванильное здоровье.

```json
{"entities": ["minecraft:zombie", "#minecraft:raiders"], "bleeding": true, "fracture": true, "pain_shock": true}
```

## hospital_blocks — функции госпиталя для чужих блоков (второй этап)
`data/<ns>/rpmedicine/hospital_blocks/<имя>.json`. Мод не добавляет мебель, а даёт функции блокам других модов.

```json
{"function": "bed", "blocks": ["industrialhellscape:medical_bed", "#mypack:hospital_beds"], "radius": 3}
```

| function | Что делает | radius |
|---|---|---|
| `bed` | больничная койка: ПКМ пустой рукой — лечь, ПКМ с телом на плече — положить; встать — присесть; заживление ×1,5, кровь ×1,5, мозг ×2 (конфиг `hospital`); выход на койке оставляет тело на ней | — |
| `operating_table` | на втором этапе — как койка | — |
| `restraint_table` | стол с фиксацией: как операционный стол; медик в панели осмотра фиксирует пациента в сознании (не встаёт, руки не действуют, можно воздуховод и интубацию — больно) | — |
| `monitor` | цифры пациента ближайшей койки в радиусе тому, кто смотрит на монитор; тревога при остановке сердца и SpO2 ниже 85 | 3 |
| `iv_stand` | капельница идёт, даже если пациент ходит, пока стойка в радиусе | 3 |
| `sterilizer`, `fridge`, `lab`, `oxygen` | функции следующих шагов второго этапа | — |

Файлы одной функции складываются, радиус берётся наибольший. Встроенные списки лежат в `data/rpmedicine/rpmedicine/hospital_blocks/` (`beds.json`, `monitors.json` и т.д.); чтобы убрать блок из встроенного списка, положите в свой датапак файл с тем же путём. Списки уходят клиентам при входе и после `/reload`.

## drugs — препараты (второй этап)
`data/<ns>/rpmedicine/drugs/<имя>.json`. Предметы препарата перечислены в самом файле; аналоги из других модов — через `item_aliases`.

```json
{
  "items": ["rpmedicine:paracetamol"],
  "form": "pill",
  "seconds": 2, "min_level": 0,
  "effects": [{"effect": "analgesia", "strength": 12, "delay": 60, "duration": 1800},
              {"effect": "antipyretic", "strength": 0.7, "delay": 300, "duration": 14400}],
  "dose": {"limit": 4, "window_hours": 24},
  "overdose": {"effects": [{"effect": "pressure", "strength": -15, "delay": 600, "duration": 7200}], "arrest_chance": 0},
  "opioid": false,
  "special": "none"
}
```
- `form`: `pill` (только в сознании), `injection`, `drip` (пациент на месте всё время установки), `topical` (на часть тела). Время по умолчанию: таблетки и укол 2 с, капельница 10 с, наружно 3 с.
- `effect`: `analgesia` (минус к боли), `antipyretic` (0–1, доля снятой лихорадки), `antibiotic` (на сколько %/ч замедляет рост инфекции; 10 — рост 4 %/ч становится −6 %/ч; сепсис спадает на 5 %/ч при силе 10), `sedation` (сознание не выше 100 − сила, медленнее болевой шок, реже пульс), `pressure` и `heart_rate` (прибавка, может быть отрицательной), `resp_depression` (0–1, ослабление дыхания), `coagulation` (0–1 меньше кровит; отрицательное — больше), `concussion_relief` (контузия проходит быстрее в 1 + сила раз). `delay` и `duration` — секунды.
- Одинаковые эффекты не складываются по силе, а продлеваются по времени; сила — наибольшая.
- `dose`: больше `limit` доз за `window_hours` часов в сети — передозировка: эффекты `overdose.effects` и шанс остановки дыхания `arrest_chance`.
- `opioid`: вместе с седацией угнетает дыхание; снимается налоксоном.
- `special`: `opioid_antidote` (налоксон: снимает морфин, опиаты, обезболивание и угнетение дыхания), `antiseptic` (шанс заражения ран части ×0,3), `antibiotic_ointment` (лечит заражение неглубокой раны или ожога).

## Теги
- `rpmedicine:medical_items` (предметы) — что кладётся в подсумок и аптечку.
- `rpmedicine:finishing_weapons` (предметы) — чем ещё можно добивать (оружие TaCZ уже там).
- `rpmedicine:biting_mobs` (сущности) — чьи удары дают укус.

## substances — вещества от чужих модов (третий этап, п. 9)
`data/<ns>/rpmedicine/substances/<имя>.json`:
```json
{"substance": "alcohol", "amount": 1.5,
 "items": ["vinery:red_wine"],
 "right_click": ["the_dirty_stuff:cigarette"],
 "effects": ["tobacconistmod:nicotine"],
 "cooldown_seconds": 30}
```
- `substance`: `opioid`, `benzo`, `alcohol`, `nicotine`, `caffeine`, `stimulant`; `amount` — «стандартных доз» за раз.
- `items` — доза, когда предмет допит или съеден; `right_click` — по ПКМ предметом (курение, которое не «допивается»); `effects` — когда мод накладывает этот эффект. `cooldown_seconds` — не чаще (затяжки, обновления эффекта).
- В `drugs` поле `"substance": {"id": "benzo", "amount": 1.0}` — вещество препарата нашего мода; `"opioid": true` — опиат.
- По умолчанию: Brewery (пиво 1, виски и Dark Brew 2), Vinery (вина 1,5, сидр и медовуха 1), Tobacconist (эффект «никотин» 0,4 раз в 30 с), The Dirty Stuff (сигареты и сигары по ПКМ, 1 раз в минуту), Herbal Brews (кофе 1, чай 0,5).

