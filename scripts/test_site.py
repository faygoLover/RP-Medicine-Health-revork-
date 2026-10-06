# Тестовая площадка RP Medicine для run/world (сервер разработки).
#   python scripts/test_site.py         — датапак rpm_test: вещества-заглушки и функция rpm_test:site
#   python scripts/test_site.py --pack  — ещё и скопировать моды мебели из сборки автора в libs/pack
# Сервер запускать с мебелью: gradlew runServer -Pwith_optional=true -Pwith_pack=true
# Койка Industrial Hellscape по умолчанию «залита водой» — ставим waterlogged=false.
# Для чистого опыта температура LSO в run/config выключена (жажда работает).
# Площадка строится от ног игрока: /execute at Dev run function rpm_test:site
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "run", "world", "datapacks", "rpm_test")
D = os.path.join(ROOT, "data", "rpm_test")

# Моды сборки с мебелью госпиталя (имя в libs/pack → начало имени файла в сборке).
PACK = os.path.join(os.environ.get("APPDATA", ""), "ElyPrismLauncher", "instances", "DEPARTMENT_s5_client", "minecraft", "mods")
PACK_MODS = {
    "industrialhellscape": "industrialhellscape-", "moa_decor_science": "MOAdecor+SCIENCE", "multibeds": "multibeds-forge",
    "shetiphiancore": "shetiphiancore-forge", "refurbished_furniture": "refurbished_furniture-forge", "framework": "framework-forge",
    "health_and_disease": "health_and_disease-", "butchery": "butchery-",
}


def copy_pack():
    dst = os.path.join(HERE, "..", "libs", "pack")
    os.makedirs(dst, exist_ok=True)
    for name, prefix in PACK_MODS.items():
        found = [f for f in os.listdir(PACK) if f.startswith(prefix) and f.endswith(".jar")]
        if not found:
            print("нет в сборке:", name)
            continue
        shutil.copyfile(os.path.join(PACK, sorted(found)[-1]), os.path.join(dst, f"{name}-1.jar"))
        print("libs/pack:", name, "←", sorted(found)[-1])


def w(rel, obj):
    p = os.path.join(D, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(obj if isinstance(obj, str) else json.dumps(obj, ensure_ascii=False, indent=2))


def sign(x, y, z, lines, rot=8):
    msgs = ",".join("'" + json.dumps({"text": t, "bold": i == 0}, ensure_ascii=False) + "'" for i, t in enumerate((lines + ["", "", "", ""])[:4]))
    return f"setblock ~{x} ~{y} ~{z} minecraft:oak_sign[rotation={rot}]{{front_text:{{messages:[{msgs}]}}}} replace"


def two_forward(block, x, z, extra=""):
    """Двухблочная мебель Industrial Hellscape вдоль оси (койка, стол): лицом на север, вторая половина — к югу."""
    e = ("," + extra) if extra else ""
    return [f"setblock ~{x} ~ ~{z} {block}[facing=north,half=positive{e}] replace",
            f"setblock ~{x} ~ ~{z + 1} {block}[facing=north,half=negative{e}] replace"]


def two_vertical(block, x, z, facing="north"):
    """Двухблочная мебель в высоту (монитор, стойка капельницы)."""
    return [f"setblock ~{x} ~ ~{z} {block}[facing={facing},half=negative] replace",
            f"setblock ~{x} ~1 ~{z} {block}[facing={facing},half=positive] replace"]


def kit_button(x, z, kit):
    return [f"setblock ~{x} ~ ~{z} minecraft:command_block{{Command:\"rpmedicine kit {kit} @p\"}} replace",
            f"setblock ~{x} ~1 ~{z} minecraft:stone_button[face=floor] replace"]


def main():
    if "--pack" in sys.argv:
        copy_pack()
    if os.path.isdir(ROOT):
        shutil.rmtree(ROOT)
    os.makedirs(ROOT, exist_ok=True)
    json.dump({"pack": {"pack_format": 15, "description": "RP Medicine: тестовая площадка"}},
              open(os.path.join(ROOT, "pack.mcmeta"), "w", encoding="utf-8"), ensure_ascii=False)
    # Кислород в датапаке по умолчанию не назначен — на площадке это огнетушитель-«баллон».
    w("rpmedicine/hospital_blocks/test_oxygen.json", {"function": "oxygen", "blocks": ["industrialhellscape:fire_extinguisher"], "radius": 3})
    # Вещества без модов сборки: мёд — алкоголь, свекольный суп — кофе, бумага (ПКМ) — сигарета.
    w("rpmedicine/substances/test_alcohol.json", {"substance": "alcohol", "amount": 1.0, "items": ["minecraft:honey_bottle"]})
    w("rpmedicine/substances/test_caffeine.json", {"substance": "caffeine", "amount": 1.0, "items": ["minecraft:beetroot_soup"]})
    w("rpmedicine/substances/test_nicotine.json", {"substance": "nicotine", "amount": 1.0, "right_click": ["minecraft:paper"], "cooldown_seconds": 30})

    L = ["# Площадка для проверки RP Medicine. Запуск: execute at <игрок> run function rpm_test:site",
         "# Строится от ног игрока на юг (+Z) и восток (+X): 30 x 20, пол — светлая плитка.",
         "fill ~-1 ~ ~-1 ~30 ~6 ~20 minecraft:air",
         "fill ~-1 ~-1 ~-1 ~30 ~-1 ~20 minecraft:smooth_quartz",
         "fill ~-1 ~-1 ~-1 ~30 ~-1 ~-1 minecraft:polished_deepslate"]
    # Палата: две медицинские койки с мониторами и стойками, койка MOA с её монитором, раскладушка.
    L += ["# --- Палата"]
    L += two_forward("industrialhellscape:medical_bed", 1, 3, "waterlogged=false")
    L += two_vertical("industrialhellscape:vitals_monitor", 0, 3)
    L += ["setblock ~2 ~ ~3 rpmedicine:iv_stand[facing=west] replace"]
    L += two_forward("industrialhellscape:medical_bed", 5, 3, "waterlogged=false")
    L += two_vertical("industrialhellscape:vitals_monitor", 4, 3)
    L += two_vertical("industrialhellscape:iv_dripstand", 6, 3)
    L += ["setblock ~1 ~ ~7 moa_decor_science:camahblanca[facing=north] replace",
          "setblock ~0 ~ ~7 moa_decor_science:lectordesignosvitales[facing=north] replace",
          "setblock ~5 ~ ~7 multibeds:cot replace",
          "setblock ~3 ~ ~9 industrialhellscape:fire_extinguisher[facing=north] replace",
          sign(3, 0, 1, ["Палата", "стойка RP Medicine —", "у левой койки,", "IHS — у правой"]),
          sign(3, 0, 10, ["Кислород", "огнетушитель", "у коек", ""])]
    # Операционная: стол, монитор, стойка, стерилизатор, кислород.
    L += ["# --- Операционная"]
    L += two_forward("industrialhellscape:operating_table", 10, 3)
    L += two_vertical("industrialhellscape:vitals_monitor", 9, 3)
    L += two_vertical("industrialhellscape:iv_dripstand", 11, 3)
    L += ["setblock ~11 ~ ~6 health_and_disease:purifybox[facing=north] replace",
          "setblock ~10 ~ ~6 health_and_disease:medicalanticollisionbox[facing=north] replace",
          "setblock ~9 ~ ~6 industrialhellscape:fire_extinguisher[facing=north] replace",
          sign(10, 0, 1, ["Операционная", "стол, монитор,", "стерилизатор H&D", "ящик лекарств"])]
    # Стол с фиксацией.
    L += ["# --- Фиксация",
          "setblock ~17 ~ ~3 butchery:metal_butchers_table[facing=north] replace"]
    L += two_vertical("industrialhellscape:vitals_monitor", 16, 3)
    L += [sign(16, 0, 1, ["Стол с фиксацией", "стол мясника", "", ""])]
    # Лаборатория и холодильник.
    L += ["# --- Лаборатория",
          "setblock ~20 ~ ~3 industrialhellscape:metal_desk[type=solo,facing=north] replace",
          "setblock ~20 ~1 ~3 moa_decor_science:microscopio[facing=north] replace",
          "setblock ~21 ~ ~3 health_and_disease:pathologicalexaminationtable[facing=north] replace",
          "setblock ~23 ~ ~3 refurbished_furniture:light_fridge[facing=north] replace",
          "setblock ~19 ~ ~3 health_and_disease:thermostaticholder[facing=north] replace",
          "setblock ~19 ~ ~5 health_and_disease:medicalbox[facing=north] replace",
          sign(20, 0, 1, ["Лаборатория", "микроскоп и стол H&D:", "ПКМ пробиркой", "термостат — слева"]),
          sign(23, 0, 1, ["Холодильник", "кровь и органы", "хранятся дольше", ""])]
    # Поле: открытое место для ранений.
    L += ["# --- Поле",
          "fill ~25 ~-1 ~3 ~29 ~-1 ~8 minecraft:coarse_dirt",
          "setblock ~27 ~ ~3 minecraft:target",
          sign(26, 0, 1, ["Поле", "ранения, взрывы,", "стрельба", ""])]
    # Наборы: кнопка = /rpmedicine kit <набор> @p.
    kits = [("field", "Полевой"), ("resus", "Реанимация"), ("diag", "Диагностика"), ("surgeon", "Хирург"),
            ("transplant", "Органы, протезы"), ("drugs", "Лекарства"), ("substances", "Вещества"), ("food", "Еда"), ("gm", "ГМ")]
    L.append("# --- Наборы")
    for i, (k, t) in enumerate(kits):
        x = 1 + i * 3
        L += kit_button(x, 15, k)
        L.append(sign(x, 0, 14, ["Набор", t, "кнопка сверху", k]))
    L += [sign(0, 0, 0, ["RP Medicine", "тестовая площадка", "/rpmedicine kit", ""]),
          "gamerule commandBlockOutput false", "gamerule doDaylightCycle false", "time set noon", "weather clear",
          "say Площадка RP Medicine построена"]
    w("functions/site.mcfunction", "\n".join(L) + "\n")
    print("ok", os.path.normpath(ROOT))


if __name__ == "__main__":
    main()
