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

## Теги
- `rpmedicine:medical_items` (предметы) — что кладётся в подсумок и аптечку.
- `rpmedicine:finishing_weapons` (предметы) — чем ещё можно добивать (оружие TaCZ уже там).
- `rpmedicine:biting_mobs` (сущности) — чьи удары дают укус.
