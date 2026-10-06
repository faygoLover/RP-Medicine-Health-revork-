#!/usr/bin/env python3
"""Генерирует ресурсы RP Medicine: датапак по умолчанию (damage_sources, items, item_aliases, mobs),
типы урона и теги, модели и текстуры-заглушки предметов, sounds.json.
Запуск из корня репозитория: python3 scripts/gen_data.py. Сгенерированное коммитится."""
import json, os, struct, sys, zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import foreign_assets  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
DATA = os.path.join(ROOT, "data")
ASSETS = os.path.join(ROOT, "assets", "rpmedicine")

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")

def ch(min_sev, per, mx):
    return {"min_severity": min_sev, "per_severity": per, "max": mx}

# ---------------------------------------------------------------- типы урона мода
DEATH_TYPES = ["brain_death", "surrender", "gm_kill", "finished"]
for name in DEATH_TYPES:
    write(f"{DATA}/rpmedicine/damage_type/{name}.json",
          {"message_id": f"rpmedicine.{name}", "exhaustion": 0.0, "scaling": "never"})
for tag in ["bypasses_armor", "bypasses_invulnerability", "bypasses_effects", "bypasses_resistance",
            "bypasses_enchantments", "bypasses_shield", "bypasses_cooldown", "no_knockback"]:
    write(f"{DATA}/minecraft/tags/damage_type/{tag}.json",
          {"replace": False, "values": [f"rpmedicine:{n}" for n in DEATH_TYPES]})

# ---------------------------------------------------------------- damage_sources
DS = f"{DATA}/rpmedicine/rpmedicine/damage_sources"
rules = {
    # Правило по умолчанию: всё неизвестное — ушиб по точке удара.
    "default": {"wound": "bruise", "location": "hit_point",
                "complications": {"fracture": ch(25, 0.01, 0.4), "open_fracture_fraction": 0.1,
                                  "internal": ch(40, 0.01, 0.3), "concussion": ch(20, 0.02, 0.6)}},
    "arrow": {"priority": 20, "damage_types": ["minecraft:arrow"], "wound": "stab", "location": "hit_point",
              "complications": {"arterial": ch(15, 0.01, 0.25), "internal": ch(5, 0.02, 0.6),
                                "pneumothorax": ch(5, 0.03, 0.7), "fracture": ch(25, 0.01, 0.2)}},
    "trident": {"priority": 20, "damage_types": ["minecraft:trident"], "wound": "stab", "location": "hit_point",
                "complications": {"arterial": ch(15, 0.015, 0.35), "internal": ch(5, 0.02, 0.6),
                                  "pneumothorax": ch(5, 0.03, 0.7), "fracture": ch(25, 0.01, 0.3)}},
    "melee_blade": {"priority": 30, "damage_types": ["minecraft:player_attack", "minecraft:mob_attack", "minecraft:mob_attack_no_aggro"],
                    "attacker_items": ["#minecraft:swords", "#minecraft:axes"], "wound": "cut", "location": "hit_point",
                    "complications": {"arterial": ch(20, 0.01, 0.3), "internal": ch(30, 0.01, 0.3),
                                      "fracture": ch(35, 0.01, 0.25), "open_fracture_fraction": 0.5,
                                      "pneumothorax": ch(30, 0.01, 0.2)}},
    "melee_pierce": {"priority": 31, "damage_types": ["minecraft:player_attack", "minecraft:mob_attack"],
                     "attacker_items": ["minecraft:trident"], "wound": "stab", "location": "hit_point",
                     "complications": {"arterial": ch(15, 0.01, 0.3), "internal": ch(5, 0.02, 0.6), "pneumothorax": ch(5, 0.03, 0.6)}},
    "bite": {"priority": 25, "damage_types": ["minecraft:mob_attack", "minecraft:mob_attack_no_aggro"],
             "attacker_entities": ["#rpmedicine:biting_mobs"], "wound": "bite", "location": "hit_point",
             "complications": {"arterial": ch(25, 0.01, 0.2), "fracture": ch(30, 0.01, 0.2)}},
    "melee_blunt": {"priority": 10, "damage_types": ["minecraft:player_attack", "minecraft:mob_attack", "minecraft:mob_attack_no_aggro"],
                    "wound": "bruise", "location": "hit_point",
                    "complications": {"fracture": ch(15, 0.012, 0.45), "open_fracture_fraction": 0.1,
                                      "internal": ch(25, 0.012, 0.4), "concussion": ch(10, 0.03, 0.8)}},
    "sting": {"priority": 20, "damage_types": ["minecraft:sting"], "wound": "stab", "location": "hit_point", "severity_multiplier": 0.5},
    "thorns": {"priority": 20, "damage_types": ["minecraft:thorns"], "wound": "cut", "location": "random", "severity_multiplier": 0.6},
    "fall": {"priority": 20, "damage_types": ["minecraft:fall", "minecraft:stalagmite"], "wound": "bruise", "location": "fall",
             "high_fall_damage": 10,
             "complications": {"fracture": ch(8, 0.025, 0.85), "open_fracture_fraction": 0.2,
                               "internal": ch(15, 0.015, 0.5), "concussion": ch(10, 0.03, 0.8)}},
    "head_blow": {"priority": 20, "damage_types": ["minecraft:falling_anvil", "minecraft:falling_block", "minecraft:falling_stalactite", "minecraft:fly_into_wall"],
                  "wound": "bruise", "location": "head",
                  "complications": {"concussion": ch(5, 0.04, 1.0), "arterial": ch(40, 0.005, 0.1)}},
    "explosion": {"priority": 20, "damage_types": ["#minecraft:is_explosion", "minecraft:fireworks"], "wound": "shrapnel", "location": "explosion",
                  "complications": {"foreign_body": dict(ch(5, 0.03, 0.9), count_min=1, count_max=3),
                                    "fracture": ch(15, 0.015, 0.5), "open_fracture_fraction": 0.4,
                                    "arterial": ch(20, 0.01, 0.25), "internal": ch(15, 0.015, 0.5),
                                    "pneumothorax": ch(15, 0.01, 0.3), "concussion": dict(ch(0, 0.05, 1.0), amount_per_severity=3.0)}},
    "thrown": {"priority": 20, "damage_types": ["minecraft:mob_projectile", "minecraft:thrown"], "wound": "bruise", "location": "hit_point",
               "complications": {"concussion": ch(10, 0.02, 0.5)}},
    "fire": {"priority": 20, "damage_types": ["minecraft:in_fire", "minecraft:on_fire", "minecraft:hot_floor"], "wound": "burn", "location": "fire"},
    "lava": {"priority": 21, "damage_types": ["minecraft:lava"], "wound": "burn", "location": "lava"},
    "lightning": {"priority": 21, "damage_types": ["minecraft:lightning_bolt"], "wound": "burn", "location": "random",
                  "complications": {"concussion": ch(0, 0.05, 1.0)}},
    "fireball": {"priority": 21, "damage_types": ["minecraft:fireball", "minecraft:unattributed_fireball", "minecraft:wither_skull"],
                 "wound": "burn", "location": "hit_point"},
    "plants": {"priority": 20, "damage_types": ["minecraft:cactus", "minecraft:sweet_berry_bush"], "wound": "stab", "location": "legs",
               "severity_multiplier": 0.4},
    "freeze": {"priority": 20, "damage_types": ["minecraft:freeze"], "wound": "bruise", "location": "random", "severity_multiplier": 0.3},
    "cramming": {"priority": 20, "damage_types": ["minecraft:cramming"], "wound": "bruise", "location": "chest",
                 "complications": {"fracture": ch(10, 0.02, 0.5), "internal": ch(10, 0.01, 0.3)}},
    "sonic_boom": {"priority": 20, "damage_types": ["minecraft:sonic_boom"], "wound": "bruise", "location": "chest",
                   "complications": {"internal": ch(0, 0.03, 1.0), "concussion": ch(0, 0.05, 1.0), "fracture": ch(10, 0.02, 0.6)}},
    # Без ран: прямо на физиологию.
    "drown": {"priority": 20, "damage_types": ["minecraft:drown"], "wound": "none", "physiology": {"spo2_per_damage": 2}},
    "suffocation": {"priority": 20, "damage_types": ["minecraft:in_wall"], "wound": "none", "physiology": {"spo2_per_damage": 4}},
    "starve": {"priority": 20, "damage_types": ["minecraft:starve"], "wound": "none", "physiology": {"brain_per_damage": 1}},
    "magic": {"priority": 20, "damage_types": ["minecraft:magic", "minecraft:indirect_magic", "minecraft:dragon_breath"],
              "wound": "none", "physiology": {"brain_per_damage": 0.5}},
    "wither": {"priority": 20, "damage_types": ["minecraft:wither"], "wound": "none", "physiology": {"brain_per_damage": 1}},
    # Пули, которые пришли не через TaCZ или без события (запасной путь).
    "bullet_fallback": {"priority": 15, "damage_types": ["tacz:bullet", "tacz:bullet_ignore_armor", "tacz:bullet_void",
                                                          "tacz:bullet_void_ignore_armor", "zerocontact:zc_damage"],
                        "wound": "gunshot", "location": "hit_point",
                        "complications": {"arterial": ch(15, 0.012, 0.35), "internal": ch(5, 0.02, 0.7),
                                          "pneumothorax": ch(5, 0.03, 0.8), "fracture": ch(15, 0.015, 0.6),
                                          "open_fracture_fraction": 0.6, "foreign_body": dict(ch(0, 0.0, 0.35), count_min=1, count_max=1)}},
    # Прочие снаряды модов (Superb Warfare и т.п.) — как пуля, по точке попадания.
    "projectile_fallback": {"priority": 5, "damage_types": ["#minecraft:is_projectile"], "wound": "gunshot", "location": "hit_point",
                            "complications": {"arterial": ch(15, 0.01, 0.3), "internal": ch(5, 0.02, 0.6),
                                              "pneumothorax": ch(5, 0.03, 0.6), "fracture": ch(15, 0.015, 0.5),
                                              "foreign_body": dict(ch(0, 0.0, 0.3), count_min=1, count_max=1)}},
    # TaCZ + Zero Contact: исход попадания (п. 3.4 ТЗ). Без damage_types — к ним обращаются по id.
    "gun/penetration": {"wound": "gunshot", "location": "hit_point",
                        "complications": {"arterial": ch(15, 0.012, 0.35), "internal": ch(5, 0.02, 0.7),
                                          "pneumothorax": ch(5, 0.03, 0.8), "fracture": ch(15, 0.015, 0.6),
                                          "open_fracture_fraction": 0.6,
                                          "foreign_body": dict(ch(0, 0.0, 0.35), count_min=1, count_max=1)}},
    "gun/plate": {"wound": "bruise", "location": "hit_point",
                  "complications": {"fracture": ch(5, 0.02, 0.5), "internal": ch(30, 0.01, 0.2)}},
    "gun/plate_heavy": {"wound": "bruise", "location": "hit_point",
                        "complications": {"fracture": ch(0, 0.03, 0.7), "internal": ch(0, 0.04, 0.9)}},
    "gun/ricochet": {"wound": "cut", "location": "hit_point", "severity_multiplier": 0.6},
    "gun/helmet": {"wound": "bruise", "location": "hit_point",
                   "complications": {"concussion": dict(ch(0, 0.05, 1.0), amount_per_severity=3.0)}},
}
# Осложнения для команды /rpmedicine injure по типу раны.
cmd = {
    "bruise": rules["melee_blunt"]["complications"], "cut": rules["melee_blade"]["complications"],
    "stab": rules["arrow"]["complications"], "gunshot": rules["gun/penetration"]["complications"],
    "shrapnel": rules["explosion"]["complications"], "burn": {}, "bite": rules["bite"]["complications"],
}
for t, c in cmd.items():
    rules[f"command/{t}"] = {"wound": t, "location": "hit_point", "complications": c}
for name, r in rules.items():
    write(f"{DS}/{name}.json", r)

# ---------------------------------------------------------------- items
ITEMS = [
    # предмет, действие, секунды, мин. уровень, тратится
    ("bandage", "bandage", 4, 0, True), ("pressure_dressing", "pressure_dressing", 5, 0, True),
    ("hemostatic_gauze", "hemostatic", 6, 1, True), ("tourniquet", "tourniquet", 3, 0, True),
    ("esmarch", "esmarch", 4, 0, True), ("splint", "splint", 8, 1, True),
    ("occlusive_dressing", "occlusive", 4, 1, True), ("decompression_needle", "needle", 5, 3, True),
    ("painkillers", "painkiller", 2, 0, True), ("morphine", "morphine", 2, 1, True),
    ("adrenaline", "adrenaline", 2, 2, True), ("txa", "txa", 2, 2, True), ("stabilization_kit", "stabilize", 8, 2, True),
    ("field_surgery_kit", "surgical_kit", 15, 4, True), ("saline", "saline", 10, 2, True), ("iv_catheter", "catheter", 6, 2, True),
    ("ammonia", "ammonia", 1, 0, True), ("airway", "airway", 4, 2, True),
    ("ambu_bag", "ambu", 1, 2, False), ("defibrillator", "defibrillator", 6, 1, True),
    ("pulse_oximeter", "pulse_oximeter", 2, 0, False), ("tonometer", "tonometer", 6, 1, False),
    # второй этап
    ("empty_blood_bag", "blood_collect", 30, 3, True), ("blood_bag", "blood_bag", 10, 3, True),
    ("stethoscope", "stethoscope", 4, 2, False), ("thermometer", "thermometer", 5, 0, False),
    ("portable_scanner", "scanner", 6, 4, False), ("hemoanalyzer", "hemoanalyzer", 10, 4, False),
    # Пробирка крови — только ланцетом (замечание 06.10); шприц — для набора из флакона.
    ("lancet", "blood_sample", 3, 1, True),
    ("surgical_tweezers", "tweezers", 8, 4, False), ("suture_kit", "suture", 10, 3, True), ("scissors", "scissors", 3, 0, False),
    # третий этап
    ("endotracheal_tube", "intubate", 6, 6, True),
    # хирургия (третий этап, п. 4)
    ("scalpel", "incise", 5, 7, False), ("hemostat", "clamp", 3, 7, False), ("retractor", "retract", 3, 7, False),
    ("vascular_suture", "vessel_suture", 12, 8, True), ("surgical_drill", "osteosynthesis", 15, 8, False),
    ("chest_drain", "drain", 10, 7, True),
    # ампутация и протезы (п. 6)
    ("bone_saw", "amputate", 12, 8, False), ("prosthetic_foot", "install_prosthesis", 8, 7, True),
    ("peg_leg", "install_prosthesis", 8, 7, True), ("prosthetic_hook", "install_prosthesis", 8, 7, True),
    # органы и конечности вне тела (п. 7)
    ("organ_container", "organ_remove", 15, 9, True), ("organ", "transplant", 20, 10, True), ("severed_limb", "reattach", 25, 10, True),
    # диабет (п. 8)
    ("glucometer", "glucometer", 3, 0, False),
]
for item, action, sec, lvl, consume in ITEMS:
    write(f"{DATA}/rpmedicine/rpmedicine/items/{item}.json",
          {"item": f"rpmedicine:{item}", "action": action, "seconds": sec, "min_level": lvl, "consume": consume})


# ---------------------------------------------------------------- препараты (второй этап, п. 6)
def eff(effect, strength, delay, duration):
    return {"effect": effect, "strength": strength, "delay": delay, "duration": duration}
H = 3600
DRUGS = {
    "paracetamol": {"form": "pill", "min_level": 0, "effects": [eff("analgesia", 12, 60, 30 * 60), eff("antipyretic", 0.7, 300, 4 * H)],
                    "dose": {"limit": 4, "window_hours": 24},
                    "overdose": {"effects": [eff("pressure", -15, 600, 2 * H), eff("heart_rate", 15, 600, 2 * H),
                                             eff("liver_toxicity", 30, 1800, H)]}},
    "ibuprofen": {"form": "pill", "min_level": 0,
                  "effects": [eff("analgesia", 18, 60, 40 * 60), eff("antipyretic", 0.6, 300, 4 * H), eff("concussion_relief", 1.0, 120, 2 * H)],
                  "dose": {"limit": 3, "window_hours": 24},
                  "overdose": {"effects": [eff("coagulation", -0.3, 0, 2 * H)]}},
    "ketorolac": {"form": "injection", "min_level": 3, "effects": [eff("analgesia", 35, 30, 20 * 60)],
                  "dose": {"limit": 2, "window_hours": 12},
                  "overdose": {"effects": [eff("coagulation", -0.4, 0, 2 * H)]}},
    "tramadol": {"form": "pill", "min_level": 4, "opioid": True,
                 "effects": [eff("analgesia", 35, 120, 40 * 60), eff("resp_depression", 0.1, 120, 40 * 60)],
                 "dose": {"limit": 2, "window_hours": 12},
                 "overdose": {"effects": [eff("resp_depression", 0.4, 0, 30 * 60), eff("sedation", 30, 0, 30 * 60)], "arrest_chance": 0.1}},
    "naloxone": {"form": "injection", "min_level": 4, "special": "opioid_antidote", "effects": []},
    # Третий этап: диабет (п. 8) — инсулин сам себе, глюкоза.
    "insulin": {"form": "injection", "min_level": 0, "effects": [eff("insulin", 3, 300, 2 * H)],
                "dose": {"limit": 3, "window_hours": 12},
                "overdose": {"effects": [eff("insulin", 4, 0, 2 * H)]}},
    "glucose_tablets": {"form": "pill", "min_level": 0, "special": "glucose", "effects": []},
    # Третий этап: иммуносупрессор против отторжения (п. 7.1) — курс раз в 12 часов.
    "cyclosporine": {"form": "pill", "min_level": 4, "effects": [eff("immunosuppression", 1, 300, 12 * H)],
                     "dose": {"limit": 1, "window_hours": 12},
                     "overdose": {"effects": [eff("liver_toxicity", 15, 600, 2 * H)]}},
    "amoxicillin": {"form": "pill", "min_level": 4, "effects": [eff("antibiotic", 10, 600, 8 * H)],
                    "dose": {"limit": 1, "window_hours": 8},
                    "overdose": {"effects": [eff("pressure", -10, 300, H), eff("heart_rate", 10, 300, H)]}},
    "ceftriaxone": {"form": "injection", "min_level": 5, "effects": [eff("antibiotic", 13, 300, 12 * H)],
                    "dose": {"limit": 1, "window_hours": 12},
                    "overdose": {"effects": [eff("pressure", -15, 300, H), eff("heart_rate", 15, 300, H)]}},
    "diazepam": {"form": "injection", "min_level": 5, "substance": {"id": "benzo", "amount": 1.0}, "effects": [eff("sedation", 40, 60, 30 * 60), eff("heart_rate", -10, 60, 30 * 60)],
                 "dose": {"limit": 2, "window_hours": 8},
                 "overdose": {"effects": [eff("sedation", 70, 0, 40 * 60), eff("resp_depression", 0.3, 0, 40 * 60)], "arrest_chance": 0.05}},
    "norepinephrine": {"form": "drip", "min_level": 6, "effects": [eff("pressure", 30, 30, 20 * 60), eff("heart_rate", 10, 30, 20 * 60)],
                       "dose": {"limit": 2, "window_hours": 2},
                       "overdose": {"effects": [eff("pressure", 40, 0, 20 * 60), eff("heart_rate", 40, 0, 20 * 60)]}},
    "atropine": {"form": "injection", "min_level": 5, "effects": [eff("heart_rate", 25, 30, 15 * 60)],
                 "dose": {"limit": 3, "window_hours": 1},
                 "overdose": {"effects": [eff("heart_rate", 50, 0, 30 * 60), eff("sedation", 20, 0, 30 * 60)]}},
    "antiseptic": {"form": "topical", "min_level": 0, "special": "antiseptic", "effects": []},
    "antibiotic_ointment": {"form": "topical", "min_level": 2, "special": "antibiotic_ointment", "effects": []},
    # Третий этап: анестезия (п. 3).
    "lidocaine": {"form": "injection", "min_level": 4, "effects": [eff("local_anesthesia", 1, 0, 20 * 60)],
                  "dose": {"limit": 4, "window_hours": 2},
                  "overdose": {"effects": [eff("heart_rate", -25, 0, 20 * 60), eff("pressure", -20, 0, 20 * 60)]}},
    "ketamine": {"form": "injection", "min_level": 7, "effects": [eff("anesthesia", 1, 15, 5 * 60), eff("pressure", 10, 15, 5 * 60),
                                                                   eff("heart_rate", 15, 15, 5 * 60)],
                 "dose": {"limit": 3, "window_hours": 2},
                 "overdose": {"effects": [eff("anesthesia", 1, 0, 20 * 60), eff("resp_depression", 0.4, 0, 20 * 60)], "arrest_chance": 0.05}},
    "propofol": {"form": "injection", "min_level": 7, "effects": [eff("anesthesia", 1, 10, 10 * 60), eff("resp_depression", 0.6, 10, 10 * 60),
                                                                   eff("pressure", -10, 10, 10 * 60)],
                 "dose": {"limit": 3, "window_hours": 2},
                 "overdose": {"effects": [eff("anesthesia", 1, 0, 30 * 60), eff("resp_depression", 0.9, 0, 30 * 60),
                                          eff("pressure", -25, 0, 30 * 60)], "arrest_chance": 0.2}},
}
# Препарат в крови (решения, п. 1.16): мг в стандартной дозе (1 мл = 1 доза) и единица; полувыведение — где своё.
KINETICS = {
    "paracetamol": {"mg_per_dose": 500}, "ibuprofen": {"mg_per_dose": 400}, "ketorolac": {"mg_per_dose": 30},
    "tramadol": {"mg_per_dose": 50}, "naloxone": {"mg_per_dose": 0.4}, "insulin": {"mg_per_dose": 10, "unit": "iu"},
    "glucose_tablets": {"mg_per_dose": 4000}, "cyclosporine": {"mg_per_dose": 100}, "amoxicillin": {"mg_per_dose": 500},
    "ceftriaxone": {"mg_per_dose": 250}, "diazepam": {"mg_per_dose": 5}, "atropine": {"mg_per_dose": 1},
    "lidocaine": {"mg_per_dose": 20}, "ketamine": {"mg_per_dose": 50},
    # Капельница: короткое полувыведение — действует, пока капает.
    "norepinephrine": {"mg_per_dose": 1, "half_life_minutes": 2.5},
    "propofol": {"mg_per_dose": 10, "half_life_minutes": 4},
}
# Наркоз пропофолом держится капельницей (решения, п. 1.16).
DRUGS["propofol"]["form"] = "drip"
for name, k in KINETICS.items():
    DRUGS[name]["kinetics"] = k
for name, d in DRUGS.items():
    obj = {"items": [f"rpmedicine:{name}"]}
    obj.update(d)
    write(f"{DATA}/rpmedicine/rpmedicine/drugs/{name}.json", obj)

ALIASES = {
    # Survival Instinct (остаётся в сборке)
    "survival_instinct:bandage": "rpmedicine:bandage", "survival_instinct:homemade_bandage": "rpmedicine:bandage",
    "survival_instinct:analgesic": "rpmedicine:painkillers",
    "survival_instinct:morphine_injector": "rpmedicine:morphine", "survival_instinct:morphine_syringe": "rpmedicine:morphine",
    "survival_instinct:adrenaline_injector": "rpmedicine:adrenaline", "survival_instinct:adrenaline_syringe": "rpmedicine:adrenaline",
    # Tactical Aid (инъекторы)
    "tactical_aid:adrenalineinjector": "rpmedicine:adrenaline", "tactical_aid:adrenalineinjector_ii": "rpmedicine:adrenaline",
    "tactical_aid:adrenalineinjector_iii": "rpmedicine:adrenaline",
    "tactical_aid:painlessinjector": "rpmedicine:morphine", "tactical_aid:relief_injector": "rpmedicine:painkillers",
    # Medicamod (только как список препаратов; второй этап — к своим аналогам)
    "medicamod:morphine": "rpmedicine:morphine", "medicamod:adrenalin": "rpmedicine:adrenaline",
    "medicamod:ibuprofen": "rpmedicine:ibuprofen", "medicamod:ketonal": "rpmedicine:ibuprofen",
    "medicamod:metamizol": "rpmedicine:paracetamol", "medicamod:paracetamol": "rpmedicine:paracetamol",
    "medicamod:apirin": "rpmedicine:ibuprofen", "medicamod:codeine": "rpmedicine:tramadol",
    "medicamod:diazepam": "rpmedicine:diazepam", "medicamod:penicillin": "rpmedicine:amoxicillin",
    "medicamod:azithromycin": "rpmedicine:amoxicillin",
    # Tactical Medicine (удаляется; на время перехода)
    "tacmed:bandage": "rpmedicine:pressure_dressing", "tacmed:hemostatic": "rpmedicine:hemostatic_gauze",
    "tacmed:tourniquet": "rpmedicine:tourniquet", "tacmed:esmarch_tourniquet": "rpmedicine:esmarch",
    "tacmed:splint": "rpmedicine:splint", "tacmed:chest_seal": "rpmedicine:occlusive_dressing",
    "tacmed:needle_14g": "rpmedicine:decompression_needle", "tacmed:promedol": "rpmedicine:morphine",
    "tacmed:nefopam": "rpmedicine:painkillers", "tacmed:pill_pack": "rpmedicine:painkillers",
    "tacmed:saline": "rpmedicine:saline", "tacmed:npa": "rpmedicine:airway", "tacmed:ambu_bag": "rpmedicine:ambu_bag",
    "tacmed:defibrillator": "rpmedicine:defibrillator",
}
write(f"{DATA}/rpmedicine/rpmedicine/item_aliases/default.json", {"aliases": ALIASES})

# Мобы: по умолчанию никому (у всех ванильное здоровье). Пример в docs/datapack.md.
write(f"{DATA}/rpmedicine/rpmedicine/mobs/default.json",
      {"entities": [], "bleeding": True, "fracture": True, "pain_shock": True})

# ---------------------------------------------------------------- теги
# Вещества от модов сборки (третий этап, п. 9): допитое, ПКМ, наложенный эффект.
WINES = ["aegis_wine", "apple_wine", "bolvar_wine", "bottle_mojang_noir", "chenet_wine", "cherry_wine", "chorus_wine", "clark_wine",
         "cristel_wine", "glowing_wine", "jellie_wine", "lilitu_wine", "magnetic_wine", "mellohi_wine", "noir_wine", "red_wine",
         "solaris_wine", "stal_wine", "strad_wine", "villagers_fright"]
WHISKEY = ["whiskey_ak", "whiskey_carrasconlabel", "whiskey_cristelwalker", "whiskey_highland_hearth", "whiskey_jamesons_malt",
           "whiskey_jojannik", "whiskey_lilitusinglemalt", "whiskey_maggoallan", "whiskey_smokey_reverie"]
DIRTY = ["black_light_cigarette", "black_slim_cigar", "blue_cigar", "blue_cigarette", "cigar", "cigarette", "light_blue_cigar",
         "light_blue_cigarette", "light_gray_cigar", "light_gray_cigarette", "light_gray_light_cigarette", "light_gray_slim_cigar",
         "light_gray_ultralight_cigarette", "light_gray_ultraslim_cigar", "magenta_cigar", "magenta_cigarette", "orange_cigar",
         "orange_cigarette", "orange_ultralight_cigarette", "orange_ultraslim_cigar", "pink_ultralight_cigarette", "pink_ultraslim_cigar",
         "red_cigar", "red_cigarette", "red_ultralight_cigarette", "red_ultraslim_cigar", "white_light_cigarette", "white_slim_cigar",
         "yellow_cigar", "yellow_cigarette"]
SUBSTANCES = {
    "brewery_beer": {"substance": "alcohol", "amount": 1.0,
                     "items": [f"brewery:beer_{b}" for b in ["barley", "haley", "hops", "nettle", "oat", "wheat"]]},
    "brewery_strong": {"substance": "alcohol", "amount": 2.0, "items": [f"brewery:{w}" for w in WHISKEY] + ["brewery:dark_brew"]},
    "vinery_wine": {"substance": "alcohol", "amount": 1.5, "items": [f"vinery:{w}" for w in WINES]},
    "vinery_cider": {"substance": "alcohol", "amount": 1.0, "items": ["vinery:apple_cider", "vinery:kelp_cider", "vinery:mead"]},
    "tobacconist_nicotine": {"substance": "nicotine", "amount": 0.4, "effects": ["tobacconistmod:nicotine"], "cooldown_seconds": 30},
    "dirty_stuff_tobacco": {"substance": "nicotine", "amount": 1.0, "right_click": [f"the_dirty_stuff:{c}" for c in DIRTY], "cooldown_seconds": 60},
    "coffee": {"substance": "caffeine", "amount": 1.0, "items": ["herbalbrews:coffee_block", "herbalbrews:milk_coffee_block"]},
    "tea": {"substance": "caffeine", "amount": 0.5, "items": [f"herbalbrews:{t}_tea_block" for t in ["black", "green", "oolong", "yerba_mate"]]},
}
for name, obj in SUBSTANCES.items():
    write(f"{DATA}/rpmedicine/rpmedicine/substances/{name}.json", obj)

# Питание: базовые ингредиенты (не крафтятся, идут в рецепты). Порция — один предмет: ккал (0 — по БЖУ),
# белки, жиры, углеводы (г), витамины (условные единицы). Состав блюд мод считает сам по рецептам.
def mc(*ids): return [f"minecraft:{i}" for i in ids]
def ns(n, *ids): return [f"{n}:{i}" for i in ids]
BUTCHERY_MEAT = ["raw_bat_meat", "raw_bee_back_meat", "raw_camel_meat", "raw_cat_meat", "raw_chuck_steak", "raw_donkey_steak",
                 "raw_dragon_meat", "raw_elder_guardian_meat", "raw_enderman_steak", "raw_evoker_meat", "raw_fox_meat", "raw_guardian_meat",
                 "raw_hoglin_chunk", "raw_horse_meat", "raw_lamb_loin", "raw_lamb_rib", "raw_lamb_shoulder", "raw_lamb_sirloin",
                 "raw_leg_of_lamb", "raw_llama_steak", "raw_mule_steak", "raw_ocelot_meat", "raw_panda_steak", "raw_pillager_meat",
                 "raw_polar_bear_meat", "raw_pork_belly", "raw_pork_leg", "raw_pork_loin", "raw_pork_shoulder", "raw_ravager_meat",
                 "raw_ribeye_steak", "raw_rump_steak", "raw_shulker_meat", "raw_sirloin_steak", "raw_sniffer_steak", "raw_strider_meat",
                 "raw_tbone_steak", "raw_turtle_meat", "raw_villager_steak", "raw_vindicator_meat", "raw_warden_meat", "raw_witch_meat",
                 "raw_wolf_meat", "rawhumanmeat", "raw_ham", "raw_dolphin_meat", "raw_creeper_steak"]
BUTCHERY_SMALL = ["raw_chicken_leg", "raw_chicken_wing", "raw_cave_spider_leg", "raw_spider_leg", "raw_creeper_leg", "raw_gray_frog_leg",
                  "raw_green_frog_leg", "raw_orange_frog_leg", "raw_endermite_chunks", "raw_silverfish_chunks"]
BUTCHERY_FISH = ["raw_cod_fillet", "raw_blue_axolotl_fillet", "raw_brown_axolotl_fillet", "raw_cyan_axolotl_fillet", "raw_gold_axolotl_fillet",
                 "raw_pink_axolotl_fillet", "raw_pufferfish", "raw_salmon", "calamari"]
STARDEW_FISH = ["blazing_oarfish", "chromatic_arapaima", "crystalline_snakehead", "cyclops_mahimahi", "demon_gar", "golden_snook",
                "goliath_grouper", "sabretoothed_tigerfish", "storm_tarpon", "vampire_payara"]
GRAPES = ["red_grape", "white_grape", "jungle_grapes_red", "jungle_grapes_white", "savanna_grapes_red", "savanna_grapes_white",
          "taiga_grapes_red", "taiga_grapes_white"]
NUTRITION = {
    # Зерно и мука
    "grain_wheat": (mc("wheat"), 0, 12, 2, 70, 2),
    "grain_other": (ns("farm_and_charm", "barley", "oat") + ns("farmersdelight", "rice"), 0, 11, 3, 68, 3),
    "corn": (ns("farm_and_charm", "corn"), 0, 3, 1, 19, 3),
    # Овощи
    "potato": (mc("potato"), 0, 2, 0, 20, 6),
    "potato_poison": (mc("poisonous_potato"), 0, 1, 0, 10, 0),
    "carrot": (mc("carrot"), 0, 1, 0, 10, 10),
    "beetroot": (mc("beetroot"), 0, 2, 0, 10, 8),
    "pumpkin": (mc("pumpkin"), 0, 4, 0, 26, 20),
    "tomato": (ns("farmersdelight", "tomato") + ns("farm_and_charm", "tomato"), 0, 1, 0, 5, 8),
    "cabbage": (ns("farmersdelight", "cabbage") + ns("farm_and_charm", "lettuce"), 0, 1, 0, 6, 12),
    "onion": (ns("farmersdelight", "onion") + ns("farm_and_charm", "onion"), 0, 1, 0, 9, 6),
    "mushroom": (mc("brown_mushroom", "red_mushroom", "crimson_fungus", "warped_fungus"), 0, 2, 0, 3, 3),
    "kelp": (mc("kelp"), 0, 1, 0, 3, 4),
    # Фрукты и ягоды
    "apple": (mc("apple"), 0, 0, 0, 25, 10),
    "melon": (mc("melon_slice"), 0, 0, 0, 8, 6),
    "berries": (mc("sweet_berries", "glow_berries") + ns("farm_and_charm", "strawberry") + ns("vinery", "cherry"), 0, 0, 0, 6, 8),
    "grapes": ([f"vinery:{g}" for g in GRAPES], 0, 1, 0, 17, 8),
    "chorus": (mc("chorus_fruit"), 0, 1, 0, 15, 3),
    # Сладкое
    "sugar": (mc("sugar", "sugar_cane"), 0, 0, 0, 10, 0),
    "honey": (mc("honey_bottle"), 0, 0, 0, 80, 1),
    "cocoa": (mc("cocoa_beans"), 0, 2, 5, 6, 1),
    # Животное
    "egg": (mc("egg"), 0, 6, 5, 1, 2),
    "milk": (mc("milk_bucket"), 0, 32, 36, 48, 4),
    "butter": (ns("farm_and_charm", "butter"), 0, 1, 80, 0, 2),
    "cheese": (ns("candlelight", "mozzarella"), 0, 22, 22, 2, 2),
    "yeast": (ns("farm_and_charm", "yeast"), 0, 4, 0, 2, 2),
    "beef": (mc("beef"), 0, 52, 30, 0, 2),
    "pork": (mc("porkchop"), 0, 50, 40, 0, 2),
    "chicken": (mc("chicken"), 0, 46, 14, 0, 2),
    "mutton": (mc("mutton"), 0, 48, 40, 0, 2),
    "rabbit": (mc("rabbit"), 0, 40, 8, 0, 2),
    "fish": (mc("cod", "tropical_fish", "pufferfish") + [f"stardew_fishing:{f}" for f in STARDEW_FISH], 0, 35, 4, 0, 3),
    "fish_fat": (mc("salmon"), 0, 40, 25, 0, 4),
    "rotten": (mc("rotten_flesh"), 0, 20, 10, 0, 0),
    "spider_eye": (mc("spider_eye"), 0, 6, 2, 0, 0),
    "butchery_meat": ([f"butchery:{i}" for i in BUTCHERY_MEAT], 0, 45, 25, 0, 2),
    "butchery_small": ([f"butchery:{i}" for i in BUTCHERY_SMALL], 0, 22, 10, 0, 1),
    "butchery_fish": ([f"butchery:{i}" for i in BUTCHERY_FISH], 0, 30, 5, 0, 3),
    "butchery_mince": (ns("butchery", "raw_beef_mince", "raw_lamb_mince"), 0, 40, 30, 0, 2),
    "butchery_sausage": (ns("butchery", "raw_sausage", "raw_blood_sausage"), 0, 22, 28, 6, 2),
    "butchery_organs": (ns("butchery", "heart", "kidney", "lungs", "stomach", "intestines", "brain"), 0, 30, 8, 1, 10),
    "butchery_liver": (ns("butchery", "liver"), 0, 30, 6, 4, 25),
    "butchery_fat": (ns("butchery", "animal_fat", "crackling"), 0, 10, 80, 0, 0),
    "blood": (ns("butchery", "bottle_of_blood"), 0, 8, 1, 0, 2),
    # Напитки (калории спирта — ккал)
    "beer": (ns("brewery", "beer_barley", "beer_haley", "beer_hops", "beer_nettle", "beer_oat", "beer_wheat"), 150, 1, 0, 12, 1),
    "spirits": ([f"brewery:{w}" for w in WHISKEY] + ns("brewery", "dark_brew"), 250, 0, 0, 0, 0),
    "wine": ([f"vinery:{w}" for w in WINES] + ns("vinery", "apple_cider", "kelp_cider", "mead"), 120, 0, 0, 4, 1),
    "juice": (ns("vinery", "apple_juice", "red_grapejuice", "white_grapejuice", "red_jungle_grapejuice", "white_jungle_grapejuice",
                 "red_savanna_grapejuice", "white_savanna_grapejuice", "red_taiga_grapejuice", "white_taiga_grapejuice"), 0, 0, 0, 25, 10),
    "coffee": (ns("herbalbrews", "coffee_block"), 0, 0, 0, 2, 0),
    "coffee_milk": (ns("herbalbrews", "milk_coffee_block"), 0, 3, 3, 5, 0),
    "tea": ([f"herbalbrews:{t}_tea_block" for t in ["black", "green", "oolong", "yerba_mate", "hibiscus", "lavender", "rooibos"]], 0, 0, 0, 1, 3),
}
# Вид еды для «приелось»; напитки и пищевые добавки не надоедают (exempt).
FOOD_CATEGORY = {
    "grain_wheat": "grain", "grain_other": "grain", "corn": "grain", "potato": "vegetables", "potato_poison": "vegetables",
    "carrot": "vegetables", "beetroot": "vegetables", "pumpkin": "vegetables", "tomato": "vegetables", "cabbage": "vegetables",
    "onion": "vegetables", "mushroom": "vegetables", "kelp": "vegetables", "apple": "fruit", "melon": "fruit", "berries": "fruit",
    "grapes": "fruit", "chorus": "fruit", "sugar": "sweet", "honey": "sweet", "cocoa": "sweet", "egg": "dairy", "milk": "dairy",
    "butter": "dairy", "cheese": "dairy", "beef": "meat", "pork": "meat", "chicken": "meat", "mutton": "meat", "rabbit": "meat",
    "fish": "fish", "fish_fat": "fish", "rotten": "meat", "spider_eye": "meat", "butchery_meat": "meat", "butchery_small": "meat",
    "butchery_fish": "fish", "butchery_mince": "meat", "butchery_sausage": "meat", "butchery_organs": "meat", "butchery_liver": "meat",
    "butchery_fat": "meat",
}
for name, (items, kcal, prot, fat, carbs, vit) in NUTRITION.items():
    obj = {"items": items, "protein": prot, "fat": fat, "carbs": carbs, "vitamins": vit}
    if name in FOOD_CATEGORY:
        obj["category"] = FOOD_CATEGORY[name]
    else:
        obj["exempt"] = True
    if kcal:
        obj["kcal"] = kcal
    write(f"{DATA}/rpmedicine/rpmedicine/nutrition/{name}.json", obj)

medical = [f"rpmedicine:{i[0]}" for i in ITEMS] + [f"rpmedicine:{d}" for d in DRUGS] + ["rpmedicine:blood_sample", "rpmedicine:filled_syringe", "rpmedicine:dirty_syringe", "rpmedicine:used_pen", "rpmedicine:dirty_test_tube", "rpmedicine:test_tube", "rpmedicine:syringe", "rpmedicine:osteosynthesis_kit", "rpmedicine:surgical_mask", "rpmedicine:surgical_gloves", "rpmedicine:laryngoscope", "rpmedicine:severed_limb", "rpmedicine:organ_container", "rpmedicine:organ"]
write(f"{DATA}/rpmedicine/tags/items/medical_items.json",
      {"replace": False, "values": medical + [{"id": k, "required": False} for k in sorted(ALIASES)]})
write(f"{DATA}/rpmedicine/tags/items/finishing_weapons.json",
      {"replace": False, "values": [{"id": "tacz:modern_kinetic_gun", "required": False}]})
write(f"{DATA}/rpmedicine/tags/entity_types/biting_mobs.json",
      {"replace": False, "values": ["minecraft:wolf", "minecraft:spider", "minecraft:cave_spider", "minecraft:zombie",
                                    "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager", "minecraft:silverfish",
                                    "minecraft:endermite", "minecraft:polar_bear", "minecraft:fox", "minecraft:cat",
                                    "minecraft:ocelot", "minecraft:panda", "minecraft:hoglin", "minecraft:zoglin",
                                    "minecraft:piglin", "minecraft:axolotl"]})

# ---------------------------------------------------------------- госпиталь (второй этап): функции чужих блоков
MOA_COLORS = ["amarilla", "azul", "azulclara", "blanca", "cafe", "cian", "gris", "grisclara", "magenta", "morada",
              "naranja", "negra", "roja", "rosa", "verde", "verdelima"]
HOSPITAL = {
    # Свои блоки (забраны из Industrial Hellscape и Health & Disease, решения, п. 1.16) и кровати других модов (мягко).
    "beds": {"function": "bed", "blocks": ["rpmedicine:hospital_bed"] + [f"moa_decor_science:camah{c}" for c in MOA_COLORS]
             + ["multibeds:cot"]},
    "operating_tables": {"function": "operating_table", "blocks": ["rpmedicine:operating_table"]},
    # Операционный стол — и стол с фиксацией.
    "restraint_tables": {"function": "restraint_table", "blocks": ["rpmedicine:operating_table"]},
    "monitors": {"function": "monitor", "blocks": ["rpmedicine:vitals_monitor", "moa_decor_science:lectordesignosvitales"], "radius": 3},
    "iv_stands": {"function": "iv_stand", "blocks": ["rpmedicine:iv_stand"], "radius": 3},
    # Холодильник — пока чужой; термостат держит пробирки.
    "fridges": {"function": "fridge", "blocks": ["refurbished_furniture:light_fridge", "refurbished_furniture:dark_fridge",
                                                 "rpmedicine:thermostat"]},
    "labs": {"function": "lab", "blocks": ["moa_decor_science:microscopio", "rpmedicine:lab_table"]},
    "sterilizers": {"function": "sterilizer", "blocks": ["rpmedicine:sterilizer"]},
    "oxygen": {"function": "oxygen", "blocks": ["rpmedicine:oxygen_tank"], "radius": 3},
}
for name, obj in HOSPITAL.items():
    write(f"{DATA}/rpmedicine/rpmedicine/hospital_blocks/{name}.json", obj)

# ---------------------------------------------------------------- напитки для своей жажды (без LSO; второй этап, п. 12)
DRINKS = {
    "water": {"items": ["minecraft:potion"], "potion": "minecraft:water", "amount": 35},
    "milk": {"items": ["minecraft:milk_bucket"], "amount": 25},
    "honey": {"items": ["minecraft:honey_bottle"], "amount": 10},
    "soups": {"items": ["minecraft:mushroom_stew", "minecraft:beetroot_soup", "minecraft:rabbit_stew", "minecraft:suspicious_stew"], "amount": 10},
    "melon": {"items": ["minecraft:melon_slice"], "amount": 5},
}
for name, obj in DRINKS.items():
    write(f"{DATA}/rpmedicine/rpmedicine/drinks/{name}.json", obj)

# ---------------------------------------------------------------- звуки (заглушки: ссылки на ванильные звуковые события)
# Ссылка на событие ("type": "event"), а не на файл: пути файлов в ресурсах ванили меняются от версии к версии.
SOUNDS = {
    "heartbeat": "minecraft:block.note_block.basedrum", "heavy_breathing": "minecraft:entity.player.hurt_drown",
    "ear_ringing": "minecraft:block.note_block.bell", "bandage": "minecraft:item.armor.equip_leather",
    "injection": "minecraft:ui.button.click", "tourniquet": "minecraft:item.armor.equip_chain",
    "defib_shock": "minecraft:block.fire.extinguish", "bone_break": "minecraft:entity.zombie.break_wooden_door",
    "pills": "minecraft:entity.generic.eat", "monitor_alarm": "minecraft:block.note_block.bit",
    "vomit": "minecraft:entity.player.burp",
}
# Звуки из BodyControl (VKM, MIT) и HBM's Nuclear Tech: копируются из docs/reference/родственные моды при генерации.
REL = os.path.join(os.path.dirname(__file__), "..", "docs", "reference", "родственные моды")
BC = os.path.join(REL, "BodyControl-1.0.0-alpha", "assets", "bodycontrol", "sounds")
EXTRA = os.path.join(REL, "_звуки")
BC_SOUNDS = {
    "heartbeat": ["heartbeat"], "heartbeat_fast": ["heartbeat_fast"],
    "heavy_breathing": ["heavy_breathing"], "gasp": ["breathe_gasp1", "breathe_gasp2", "breathe_gasp3", "breathe_gasp4"],
    "cough": ["cough1", "cough2"], "pain_groan": ["cramp_groan_1", "cramp_groan_2", "cramp_groan_3", "cramp_groan_4"],
    "pain_moan": ["old_groan_1", "old_groan_2", "old_groan_3", "old_groan_4", "old_groan_5"],
    "bone_break": ["fracture1", "fracture2", "fracture3", "fracture4"],
    "vomit": ["vomit_1", "vomit_2", "vomit_3", "vomit_4", "x:hbm_vomit"],
    "pills": ["pill_swallow"], "injection": ["mark_done"],
    "bandage": ["bandage_wrap_1", "bandage_wrap_2", "bandage_wrap_3"], "splint": ["splint_wrap"],
    "monitor_alarm": ["alarm_beep"], "ear_ringing": ["ear_ring"], "eardrum_burst": ["eardrum_burst"],
    "flatline": ["heart_flatline"], "heart_stopping": ["heart_stopping"], "scanner": ["scanner_blip"],
    "ammonia": ["sniffle_1", "sniffle_2", "sniffle_3"], "wake_up": ["second_wind"],
    "surgery_cut": ["surgery_cut_1", "surgery_cut_2", "surgery_cut_3", "surgery_cut_4"],
    "surgery_stitch": ["surgery_stitch", "surgery_thread_pull"], "surgery_clamp": ["surgery_clamp"],
    "surgery_retract": ["surgery_retract_1", "surgery_retract_2", "surgery_retract_3", "surgery_retract_4"],
    "surgery_suction": ["surgery_suction_1", "surgery_suction_2", "surgery_suction_3", "surgery_suction_4"],
    "surgery_bleed": ["surgery_bleed_1", "surgery_bleed_2", "surgery_bleed_3", "surgery_bleed_4", "surgery_bleed_5"],
    "surgery_bone_set": ["surgery_bone_set", "surgery_bone_move_1", "surgery_bone_move_2"],
    "surgery_vessel_cut": ["surgery_vessel_cut_1", "surgery_vessel_cut_2", "surgery_vessel_cut_3", "surgery_vessel_cut_4"],
    "surgery_cautery": ["surgery_cautery_1", "surgery_cautery_2", "surgery_cautery_3", "surgery_cautery_4"],
    "surgery_error": ["surgery_error"], "surgery_trachea": ["surgery_trachea_1", "surgery_trachea_2"],
    "bone_saw": ["bone_saw_1", "bone_saw_2", "bone_saw_3", "bone_saw_4", "bone_saw_5"],
    "bone_drill": ["bone_drill_1", "bone_drill_2", "bone_drill_3"], "organ_move": ["organ_move_1", "organ_move_2", "organ_move_3", "organ_move_4"],
    "minigame_ok": ["mark_done"], "minigame_slip": ["item_slip"], "body_fall": ["fall"],
}
# Укол — короткая тихая отметка, а не писк (замечание автора 06.10).
QUIET = {"injection": 0.35}
for k in BC_SOUNDS:
    SOUNDS.setdefault(k, "minecraft:ui.button.click")
import shutil
def bc_entries(k):
    out = []
    os.makedirs(f"{ASSETS}/sounds/bc", exist_ok=True)
    for name in BC_SOUNDS[k]:
        src = os.path.join(EXTRA, name[2:] + ".ogg") if name.startswith("x:") else os.path.join(BC, name + ".ogg")
        base = name[2:] if name.startswith("x:") else name
        dst = f"{ASSETS}/sounds/bc/{base}.ogg"
        if os.path.exists(src):
            shutil.copyfile(src, dst)
        if os.path.exists(dst):
            e = {"name": f"rpmedicine:bc/{base}"}
            if k in QUIET:
                e["volume"] = QUIET[k]
            out.append(e)
    return out
def sound_entries(k, v):
    # BodyControl → Tactical Medicine / Health & Disease → ссылка на ванильное событие.
    if k in BC_SOUNDS:
        e = bc_entries(k)
        if e:
            return e
    if k in foreign_assets.SOUNDS:
        return [{"name": f"rpmedicine:{foreign_assets.sound_file(k, i)}"} for i in range(len(foreign_assets.SOUNDS[k]))]
    return [{"name": v, "type": "event"}]
write(f"{ASSETS}/sounds.json", {k: {"subtitle": f"subtitles.rpmedicine.{k}", "sounds": sound_entries(k, v)}
                                for k, v in SOUNDS.items()})

# ---------------------------------------------------------------- модели и текстуры-заглушки
def png(path, pixels):
    """pixels: 16 строк по 16 RGBA."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)

def icon(shape, color, accent):
    T = (0, 0, 0, 0)
    c = color + (255,)
    a = accent + (255,)
    d = tuple(max(0, x - 60) for x in color) + (255,)
    px = [[T] * 16 for _ in range(16)]
    def rect(x0, y0, x1, y1, col):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[y][x] = col
    if shape == "roll":       # бинт, повязка
        rect(3, 4, 13, 12, c); rect(3, 4, 13, 5, d); rect(3, 11, 13, 12, d); rect(6, 7, 10, 9, a)
    elif shape == "strap":    # жгут
        rect(2, 6, 14, 10, c); rect(6, 5, 10, 11, a)
    elif shape == "board":    # шина
        rect(4, 1, 7, 15, c); rect(9, 1, 12, 15, c); rect(3, 5, 13, 7, a); rect(3, 10, 13, 12, a)
    elif shape == "syringe":  # шприц, игла
        for i in range(10):
            rect(3 + i, 11 - i, 5 + i, 13 - i, c)
        rect(12, 2, 14, 4, a)
    elif shape == "pills":
        rect(4, 3, 12, 14, c); rect(4, 3, 12, 5, a); rect(6, 7, 10, 11, d)
    elif shape == "bag":      # физраствор, Амбу
        rect(4, 2, 12, 12, c); rect(7, 12, 9, 15, a); rect(5, 4, 11, 6, d)
    elif shape == "box":      # аптечка, подсумок, набор
        rect(2, 4, 14, 14, c); rect(7, 6, 9, 12, a); rect(5, 8, 11, 10, a)
    elif shape == "device":   # приборы
        rect(3, 3, 13, 13, c); rect(5, 5, 11, 9, a); rect(5, 10, 7, 12, d); rect(9, 10, 11, 12, d)
    elif shape == "patch":
        rect(3, 3, 13, 13, c); rect(5, 5, 11, 11, a)
    png_path = None
    return px

ICONS = {
    "bandage": ("roll", (235, 235, 225), (200, 60, 60)), "pressure_dressing": ("roll", (120, 140, 90), (230, 230, 220)),
    "hemostatic_gauze": ("roll", (230, 230, 210), (60, 120, 200)), "tourniquet": ("strap", (40, 40, 40), (200, 60, 40)),
    "esmarch": ("strap", (200, 120, 80), (150, 80, 50)), "splint": ("board", (180, 150, 90), (90, 90, 90)),
    "occlusive_dressing": ("patch", (220, 220, 220), (60, 60, 60)), "decompression_needle": ("syringe", (200, 200, 210), (90, 90, 230)),
    "painkillers": ("pills", (240, 240, 240), (60, 160, 60)), "morphine": ("syringe", (220, 220, 220), (180, 40, 40)),
    "adrenaline": ("syringe", (230, 230, 200), (230, 160, 30)), "txa": ("syringe", (220, 230, 240), (60, 140, 200)),
    "stabilization_kit": ("box", (40, 110, 160), (240, 240, 240)),
    "field_surgery_kit": ("box", (70, 90, 70), (230, 230, 230)), "saline": ("bag", (200, 225, 240), (120, 120, 140)),
    "ammonia": ("pills", (200, 230, 240), (40, 90, 160)), "airway": ("strap", (230, 170, 170), (230, 230, 230)),
    "ambu_bag": ("bag", (60, 80, 160), (230, 230, 230)), "defibrillator": ("device", (230, 200, 40), (60, 60, 60)),
    "pulse_oximeter": ("device", (60, 60, 70), (80, 220, 120)), "tonometer": ("device", (230, 230, 230), (60, 60, 60)),
    "medical_pouch": ("box", (90, 100, 60), (200, 50, 50)), "first_aid_kit": ("box", (200, 50, 50), (240, 240, 240)),
    "gm_scanner": ("device", (120, 40, 160), (240, 200, 60)),
    "empty_blood_bag": ("bag", (225, 225, 230), (150, 150, 160)), "blood_bag": ("bag", (170, 20, 30), (230, 230, 230)),
    "paracetamol": ("pills", (240, 240, 240), (60, 120, 220)), "ibuprofen": ("pills", (230, 120, 60), (240, 240, 240)),
    "ketorolac": ("syringe", (220, 220, 230), (120, 60, 160)), "tramadol": ("pills", (240, 240, 200), (160, 40, 40)),
    "naloxone": ("syringe", (220, 230, 220), (40, 160, 80)), "amoxicillin": ("pills", (250, 220, 120), (200, 60, 60)),
    "ceftriaxone": ("syringe", (240, 240, 220), (220, 180, 40)), "diazepam": ("syringe", (220, 220, 240), (60, 90, 200)),
    "norepinephrine": ("bag", (240, 230, 200), (200, 90, 30)), "atropine": ("syringe", (230, 220, 230), (150, 30, 120)),
    "antiseptic": ("patch", (200, 170, 120), (140, 60, 30)), "antibiotic_ointment": ("pills", (240, 240, 240), (230, 200, 40)),
    "stethoscope": ("strap", (60, 60, 70), (200, 200, 210)), "thermometer": ("syringe", (240, 240, 240), (220, 40, 40)),
    "portable_scanner": ("device", (60, 90, 120), (120, 220, 240)), "hemoanalyzer": ("device", (230, 230, 230), (200, 40, 50)),
    "lancet": ("syringe", (210, 210, 220), (210, 210, 220)), "blood_draw_syringe": ("syringe", (230, 230, 240), (170, 30, 40)),
    "blood_sample": ("pills", (230, 230, 240), (170, 20, 30)), "test_tube": ("pills", (230, 235, 245), (200, 210, 225)),
    "syringe": ("syringe", (235, 235, 240), (200, 200, 210)),
    "surgical_tweezers": ("syringe", (200, 205, 215), (120, 125, 140)),
    "scalpel": ("syringe", (210, 215, 225), (90, 90, 100)), "hemostat": ("strap", (200, 205, 215), (120, 125, 140)),
    "retractor": ("board", (200, 205, 215), (120, 125, 140)), "surgical_drill": ("device", (200, 200, 205), (60, 60, 70)),
    "osteosynthesis_kit": ("box", (180, 185, 195), (90, 90, 100)), "vascular_suture": ("box", (230, 230, 235), (200, 40, 40)),
    "chest_drain": ("bag", (220, 225, 230), (120, 160, 200)), "surgical_mask": ("patch", (140, 190, 210), (240, 240, 240)),
    "surgical_gloves": ("patch", (150, 200, 230), (110, 160, 200)),
    "bone_saw": ("board", (200, 205, 215), (90, 90, 100)), "prosthetic_foot": ("box", (160, 130, 100), (90, 70, 50)),
    "peg_leg": ("board", (150, 110, 70), (110, 80, 50)), "prosthetic_hook": ("syringe", (190, 195, 200), (120, 125, 130)),
    "severed_limb": ("roll", (210, 160, 130), (150, 30, 30)),
    "organ_container": ("box", (90, 140, 170), (230, 240, 245)), "organ": ("box", (90, 140, 170), (160, 30, 40)),
    "cyclosporine": ("pills", (240, 240, 230), (120, 80, 160)),
    "insulin": ("syringe", (230, 240, 250), (60, 120, 200)), "glucose_tablets": ("pills", (250, 250, 240), (240, 160, 40)),
    "glucometer": ("device", (60, 60, 70), (120, 200, 240)), "suture_kit": ("box", (230, 230, 235), (60, 60, 200)),
    "scissors": ("strap", (190, 195, 205), (60, 60, 60)), "medcard": ("patch", (235, 225, 200), (60, 110, 160)),
    "lidocaine": ("syringe", (235, 235, 240), (90, 160, 220)), "ketamine": ("syringe", (235, 235, 240), (200, 120, 40)),
    "propofol": ("syringe", (240, 240, 240), (245, 245, 245)), "laryngoscope": ("strap", (170, 175, 185), (230, 200, 60)),
    "endotracheal_tube": ("strap", (230, 235, 240), (120, 180, 220)),
}
# Текстуры из родственных модов (docs/reference/родственные моды): инъекторы в стиле Tarkov из Tactical Aid
# (17612), инструменты и приборы из BodyControl (VKM). Авторы — в docs/assets_credits.md.
TA = "tactical_aid-1.20.1-v1.3.9/assets/tactical_aid/textures/item/"
BCT = "BodyControl-1.0.0-alpha/assets/bodycontrol/textures/item/"
REL_TEXTURES = {
    "adrenaline": TA + "adrenaline.png", "txa": TA + "quickaction.png", "ketorolac": TA + "painless.png",
    "naloxone": TA + "metabolize.png", "diazepam": TA + "relief.png", "atropine": TA + "aggressiveness.png",
    "ceftriaxone": TA + "glucose.png", "norepinephrine": TA + "igu.png", "ketamine": TA + "narcotism.png",
    "lidocaine": TA + "adrenaline_ii.png", "propofol": BCT + "emulsion_vial.png", "syringe": BCT + "insulin_syringe.png",
    "blood_draw_syringe": BCT + "syringe.png", "surgical_tweezers": BCT + "tweezers.png", "test_tube": BCT + "microtube.png",
    "portable_scanner": BCT + "advanced_scanner.png", "hemoanalyzer": BCT + "organ_scanner.png", "lancet": BCT + "surgical_needle.png",
    "medcard": BCT + "tablet.png", "organ_container": BCT + "cooler_bag.png", "organ": BCT + "donor_heart.png",
    "cyclosporine": BCT + "immunosuppressant.png", "insulin": BCT + "insulin_syringe.png", "stabilization_kit": BCT + "medical_kit.png",
    "scalpel": BCT + "scalpel.png", "surgical_gloves": BCT + "gloves.png", "vascular_suture": BCT + "surgical_thread.png",
}
def rel_texture(name):
    src = os.path.join(REL, REL_TEXTURES[name])
    dst = f"{ASSETS}/textures/item/{name}.png"
    if not os.path.exists(src):
        return os.path.exists(dst)
    from PIL import Image
    im = Image.open(src).convert("RGBA")
    w, h = im.size
    if h > w:
        im = im.crop((0, 0, w, w))
    if w not in (16, 32, 64):
        im = im.resize((32, 32), Image.LANCZOS)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    im.save(dst)
    return True
for name, (shape, color, accent) in ICONS.items():
    # Текстуры из Tactical Medicine и Health & Disease кладёт scripts/foreign_assets.py — не затираем.
    if name in REL_TEXTURES and rel_texture(name):
        pass
    elif name not in foreign_assets.TEXTURES:
        png(f"{ASSETS}/textures/item/{name}.png", icon(shape, color, accent))
    write(f"{ASSETS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpmedicine:item/{name}"}})

# Внешний вид предметов: иконки, 3D-модели, варианты органов и конечностей (перезаписывает заглушки выше).
import item_art  # noqa: E402
item_art.main()
import iv_stand_art  # noqa: E402
iv_stand_art.main()
import hospital_art  # noqa: E402
hospital_art.main()

write(os.path.join(ROOT, "pack.mcmeta"), {"pack": {"description": "RP Medicine resources", "pack_format": 15}})
print("готово:", len(rules), "правил урона,", len(ITEMS), "предметов,", len(DRUGS), "препаратов,", len(ALIASES), "аналогов")

# ---------------------------------------------------------------- площадка для GameTest (7×5×7, пол из камня)
import gzip
def nbt_named(tag_type, name, payload):
    n = name.encode("utf-8")
    return bytes([tag_type]) + struct.pack(">H", len(n)) + n + payload
def nbt_int(v): return struct.pack(">i", v)
def nbt_string(s):
    b = s.encode("utf-8"); return struct.pack(">H", len(b)) + b
def nbt_list(elem_type, items): return bytes([elem_type]) + struct.pack(">i", len(items)) + b"".join(items)
def nbt_compound(entries): return b"".join(entries) + b"\x00"

SX, SY, SZ = 7, 5, 7
palette = [nbt_compound([nbt_named(8, "Name", nbt_string("minecraft:stone"))])]
blocks = []
for x in range(SX):
    for z in range(SZ):
        blocks.append(nbt_compound([nbt_named(9, "pos", nbt_list(3, [nbt_int(x), nbt_int(0), nbt_int(z)])),
                                    nbt_named(3, "state", nbt_int(0))]))
root = nbt_compound([
    nbt_named(3, "DataVersion", nbt_int(3465)),
    nbt_named(9, "size", nbt_list(3, [nbt_int(SX), nbt_int(SY), nbt_int(SZ)])),
    nbt_named(9, "palette", nbt_list(10, palette)),
    nbt_named(9, "blocks", nbt_list(10, blocks)),
    nbt_named(9, "entities", nbt_list(10, [])),
])
path = os.path.join(DATA, "rpmedicine", "structures", "platform.nbt")
os.makedirs(os.path.dirname(path), exist_ok=True)
with open(path, "wb") as f:
    f.write(gzip.compress(nbt_named(10, "", root), mtime=0))
