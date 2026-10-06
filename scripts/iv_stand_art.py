# Стойка капельницы (замечание 35): модель Industrial Hellscape (YellowUboat, MIT) без пакетов + пакеты отдельными
# моделями по крючкам, перекрашенные: физраствор, кровь, пустой после физраствора, пустой после крови.
# Источник — libs/pack/industrialhellscape-1.jar (python scripts/test_site.py --pack). Нет jar — файлы не трогаем.
import json
import os
import zipfile
from io import BytesIO

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "rpmedicine")
DATA = os.path.join(ROOT, "data", "rpmedicine")
JAR = os.path.join(HERE, "..", "libs", "pack", "industrialhellscape-1.jar")
CREDITS = os.path.join(HERE, "..", "docs", "assets_credits.md")
SRC = "assets/industrialhellscape/"

# Жидкость в пакете IHS — три сиреневых тона (светлый, средний, тёмный) и капля в капельнице.
LIQUID = [(122, 126, 144), (112, 116, 135), (102, 106, 127)]
DROP = [(136, 88, 88), (144, 56, 56)]
KINDS = {
    "saline": ([(205, 226, 238), (186, 212, 228), (168, 198, 218)], (205, 226, 238), None),
    "blood": ([(168, 26, 36), (140, 18, 28), (112, 12, 20)], (150, 20, 30), (150, 40, 46)),
    "empty_saline": ([(198, 206, 212), (186, 195, 202), (176, 186, 194)], (190, 198, 204), None),
    "empty_blood": ([(214, 182, 186), (204, 162, 168), (176, 104, 110)], (180, 120, 125), (196, 150, 154)),
}
# Трубка капельницы на боковом пакете (столбец x=2, строки 7–19) — с кровью красная.
TUBE = [(2, y) for y in range(7, 20)]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def recolor(im, kind):
    liquid, drop, tube = KINDS[kind]
    im = im.copy()
    px = im.load()
    for y in range(min(20, im.size[1])):
        for x in range(10):
            r, g, b, a = px[x, y]
            if not a:
                continue
            if (r, g, b) in LIQUID:
                px[x, y] = liquid[LIQUID.index((r, g, b))] + (a,)
            elif (r, g, b) in DROP:
                px[x, y] = drop + (a,)
            elif tube and (x, y) in TUBE:
                px[x, y] = tube + (a,)
    return im


def main():
    if not os.path.exists(JAR):
        print("iv_stand_art: нет", JAR)
        return
    z = zipfile.ZipFile(JAR)
    tex = Image.open(BytesIO(z.read(SRC + "textures/block/iv_dripstand.png"))).convert("RGBA")
    pos = json.loads(z.read(SRC + "models/block/iv_dripstand/iv_dripstand_positive.json"))
    neg = json.loads(z.read(SRC + "models/block/iv_dripstand/iv_dripstand_negative.json"))
    item = json.loads(z.read(SRC + "models/item/iv_dripstand.json"))

    os.makedirs(os.path.join(ASSETS, "textures", "block"), exist_ok=True)
    tex.save(os.path.join(ASSETS, "textures", "block", "iv_stand.png"))
    for k in KINDS:
        recolor(tex, k).save(os.path.join(ASSETS, "textures", "block", f"iv_bag_{k}.png"))

    def up(e):
        e = json.loads(json.dumps(e))
        e["from"][1] += 16
        e["to"][1] += 16
        if "rotation" in e:
            e["rotation"]["origin"][1] += 16
        return e

    base = [e for e in neg["elements"]] + [up(e) for e in pos["elements"] if e.get("name") != "iv_bag"]
    bags = [up(e) for e in pos["elements"] if e.get("name") == "iv_bag"]
    textures = {"1": "rpmedicine:block/iv_stand", "particle": "rpmedicine:block/iv_stand"}
    write(os.path.join(ASSETS, "models", "block", "iv_stand.json"),
          {"credit": "Industrial Hellscape (YellowUboat, MIT), без пакетов", "texture_size": pos.get("texture_size", [32, 32]),
           "textures": textures, "elements": base})
    for i, e in enumerate(bags):
        for k in KINDS:
            write(os.path.join(ASSETS, "models", "block", f"iv_bag_{i}_{k}.json"),
                  {"texture_size": pos.get("texture_size", [32, 32]),
                   "textures": {"1": f"rpmedicine:block/iv_bag_{k}", "particle": f"rpmedicine:block/iv_bag_{k}"},
                   "elements": [e]})
    write(os.path.join(ASSETS, "blockstates", "iv_stand.json"), {"variants": {
        f"facing={f}": {"model": "rpmedicine:block/iv_stand", **({"y": y} if y else {})}
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    display = item.get("display", {})
    display["gui"] = {"rotation": [30, -135, 0], "translation": [0, -4, 0], "scale": [0.42, 0.42, 0.42]}
    write(os.path.join(ASSETS, "models", "item", "iv_stand.json"), {"parent": "rpmedicine:block/iv_stand", "display": display})
    write(os.path.join(DATA, "loot_tables", "blocks", "iv_stand.json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "rpmedicine:iv_stand"}],
         "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write(os.path.join(DATA, "recipes", "iv_stand.json"), {
        "type": "minecraft:crafting_shaped", "pattern": ["NIN", " I ", "III"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "N": {"item": "minecraft:iron_nugget"}},
        "result": {"item": "rpmedicine:iv_stand"}})

    text = open(CREDITS, encoding="utf-8").read()
    start, end = "<!-- iv_stand:begin -->", "<!-- iv_stand:end -->"
    block = (start + "\n## Стойка капельницы (`scripts/iv_stand_art.py`)\n\n"
             "| Файл в моде (`assets/rpmedicine/…`) | Откуда | Автор | Лицензия |\n|---|---|---|---|\n"
             "| `models/block/iv_stand.json`, `textures/block/iv_stand.png` | Industrial Hellscape 0.0.1: `iv_dripstand` (без пакетов) | YellowUboat | MIT |\n"
             "| `models/block/iv_bag_*.json`, `textures/block/iv_bag_*.png` | Industrial Hellscape 0.0.1: пакеты `iv_dripstand` (перекрашено) | YellowUboat | MIT |\n"
             + end)
    if start in text:
        text = text[:text.index(start)] + block + text[text.index(end) + len(end):]
    else:
        text = text.rstrip("\n") + "\n\n" + block + "\n"
    open(CREDITS, "w", encoding="utf-8", newline="\n").write(text)
    print("iv_stand_art: основа", len(base), "элементов, пакетов", len(bags))


if __name__ == "__main__":
    main()
