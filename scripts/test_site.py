# Тестовый датапак rpm_test для run/world: ванильные блоки вместо мебели госпиталя, вещества, площадка.
import os, json
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "run", "world", "datapacks", "rpm_test")
D = os.path.join(ROOT, "data", "rpm_test")


def w(rel, obj):
    p = os.path.join(D, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(obj if isinstance(obj, str) else json.dumps(obj, ensure_ascii=False, indent=2))


os.makedirs(ROOT, exist_ok=True)
json.dump({"pack": {"pack_format": 15, "description": "RP Medicine: тестовая площадка"}}, open(os.path.join(ROOT, "pack.mcmeta"), "w", encoding="utf-8"), ensure_ascii=False)

HOSP = {
    "bed": ("minecraft:white_wool", 0), "operating_table": ("minecraft:smooth_quartz", 0),
    "restraint_table": ("minecraft:polished_andesite", 0), "monitor": ("minecraft:observer", 3),
    "iv_stand": ("minecraft:iron_bars", 3), "lab": ("minecraft:crafting_table", 0), "fridge": ("minecraft:barrel", 0),
    "sterilizer": ("minecraft:smoker", 0), "oxygen": ("minecraft:cauldron", 3),
}
for f, (b, r) in HOSP.items():
    w(f"rpmedicine/hospital_blocks/test_{f}.json", {"function": f, "blocks": [b], "radius": r})
w("rpmedicine/substances/test_alcohol.json", {"substance": "alcohol", "amount": 1.0, "items": ["minecraft:honey_bottle"]})
w("rpmedicine/substances/test_caffeine.json", {"substance": "caffeine", "amount": 1.0, "items": ["minecraft:beetroot_soup"]})
w("rpmedicine/substances/test_nicotine.json", {"substance": "nicotine", "amount": 1.0, "right_click": ["minecraft:paper"], "cooldown_seconds": 30})


def sign(x, y, z, lines, rot=8):
    msgs = ",".join("'" + json.dumps({"text": t, "bold": i == 0}, ensure_ascii=False) + "'" for i, t in enumerate((lines + ["", "", "", ""])[:4]))
    return f"setblock ~{x} ~{y} ~{z} minecraft:oak_sign[rotation={rot}]{{front_text:{{messages:[{msgs}]}}}} replace"


def kit_chest(x, z, kit):
    # Командный блок с кнопкой: выдаёт набор нажавшему (ближайшему игроку).
    return [f"setblock ~{x} ~ ~{z} minecraft:command_block{{Command:\"rpmedicine kit {kit} @p\"}} replace",
            f"setblock ~{x} ~1 ~{z} minecraft:stone_button[face=floor] replace"]


L = ["# Площадка для проверки RP Medicine. Запуск: execute at <игрок> run function rpm_test:site",
     "# Строится от ног игрока на юг (+Z) и восток (+X): 28 x 20, пол из гладкого камня.",
     "fill ~-1 ~ ~-1 ~28 ~6 ~20 minecraft:air",
     "fill ~-1 ~-1 ~-1 ~28 ~-1 ~20 minecraft:smooth_stone",
     "fill ~-1 ~-1 ~-1 ~28 ~-1 ~-1 minecraft:polished_deepslate",
     ]
# 1. Палата: две койки (2 блока шерсти) с монитором, стойкой капельницы, кислородом.
L += ["# --- Палата",
      "fill ~1 ~ ~3 ~1 ~ ~4 minecraft:white_wool", "setblock ~0 ~ ~3 minecraft:observer[facing=east]", "setblock ~2 ~ ~3 minecraft:iron_bars",
      "fill ~4 ~ ~3 ~4 ~ ~4 minecraft:white_wool", "setblock ~5 ~ ~3 minecraft:observer[facing=west]", "setblock ~3 ~ ~4 minecraft:cauldron",
      sign(2, 0, 1, ["Палата", "шерсть = койка", "наблюдатель = монитор", "решётка = капельница"]),
      sign(4, 0, 1, ["Кислород", "котёл рядом", "с койкой", ""])]
# 2. Операционная: стол, монитор, стойка, стерилизатор.
L += ["# --- Операционная",
      "fill ~9 ~ ~3 ~9 ~ ~4 minecraft:smooth_quartz", "setblock ~8 ~ ~3 minecraft:observer[facing=east]", "setblock ~10 ~ ~3 minecraft:iron_bars",
      "setblock ~10 ~ ~5 minecraft:smoker[facing=north]", "setblock ~8 ~ ~5 minecraft:cauldron",
      sign(9, 0, 1, ["Операционная", "кварц = стол", "коптильня =", "стерилизатор"])]
# 3. Стол с фиксацией.
L += ["# --- Фиксация",
      "fill ~13 ~ ~3 ~13 ~ ~4 minecraft:polished_andesite", "setblock ~14 ~ ~3 minecraft:observer[facing=west]",
      sign(13, 0, 1, ["Стол с фиксацией", "полир. андезит", "", ""])]
# 4. Лаборатория и холодильник крови.
L += ["# --- Лаборатория",
      "setblock ~17 ~ ~3 minecraft:crafting_table", "setblock ~19 ~ ~3 minecraft:barrel[facing=up]",
      sign(17, 0, 1, ["Лаборатория", "верстак: ПКМ", "пробиркой с кровью", ""]),
      sign(19, 0, 1, ["Холодильник", "бочка", "кровь и органы", "хранятся дольше"])]
# 5. Поле боя: открытое место для ранений, ванильная кровать для сравнения.
L += ["# --- Поле",
      "fill ~22 ~ ~3 ~27 ~ ~8 minecraft:coarse_dirt", "fill ~22 ~-1 ~3 ~27 ~-1 ~8 minecraft:coarse_dirt",
      "setblock ~24 ~ ~3 minecraft:target",
      sign(23, 0, 1, ["Поле", "ранения: /rpmedicine", "injure, бой, падение", ""])]
# 6. Ряд наборов: командные блоки с кнопками.
kits = [("field", "Полевой"), ("resus", "Реанимация"), ("diag", "Диагностика"), ("surgeon", "Хирург"),
        ("transplant", "Органы, протезы"), ("drugs", "Лекарства"), ("substances", "Вещества"), ("food", "Еда"), ("gm", "ГМ")]
L.append("# --- Наборы (кнопка = /rpmedicine kit <набор> @p)")
for i, (k, t) in enumerate(kits):
    x = 1 + i * 3
    L += kit_chest(x, 14, k)
    L.append(sign(x, 0, 13, ["Набор", t, "кнопка сверху", k]))
# Подсказка у входа.
L += [sign(0, 0, 0, ["RP Medicine", "тестовая площадка", "/rpmedicine kit", ""], 8),
      "gamerule commandBlockOutput false", "gamerule doDaylightCycle false", "time set noon", "weather clear",
      "say Площадка RP Medicine построена"]
w("functions/site.mcfunction", "\n".join(L) + "\n")
print("ok", ROOT)
