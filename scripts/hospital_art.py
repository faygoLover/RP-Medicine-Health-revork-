# Мебель госпиталя, забранная к себе (решения, п. 1.16):
#   Industrial Hellscape (YellowUboat, MIT): койка, операционный стол, монитор — модели блоков из libs/pack/industrialhellscape-1.jar;
#   Health & Disease (JEDIGD, MIT): аптечный шкаф, ящик, стерилизатор, стол-лаборатория (3D, рисует HospitalGeoRenderer),
#   термостат с пробирками — из docs/reference/родственные моды.
# Запуск — из gen_data.py после item_art (дописывает rpgeo/items.json).
import json
import os
import shutil
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "rpmedicine")
DATA = os.path.join(ROOT, "data", "rpmedicine")
IH_JAR = os.path.join(HERE, "..", "libs", "pack", "industrialhellscape-1.jar")
HD = os.path.join(HERE, "..", "docs", "reference", "родственные моды", "health_and_disease-1.4.2-forge-1.20.1", "assets", "health_and_disease")
CREDITS = os.path.join(HERE, "..", "docs", "assets_credits.md")
FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def retex(obj, mapping):
    """Пути текстур Industrial Hellscape -> свои."""
    t = obj.get("textures", {})
    for k, v in list(t.items()):
        for a, b in mapping.items():
            if v == a:
                t[k] = b
    return obj


def loot(block, foot_only):
    cond = [{"condition": "minecraft:survives_explosion"}]
    if foot_only:
        cond.append({"condition": "minecraft:block_state_property", "block": f"rpmedicine:{block}", "properties": {"head": "false"}})
    write(os.path.join(DATA, "loot_tables", "blocks", f"{block}.json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"rpmedicine:{block}"}], "conditions": cond}]})


def ih():
    if not os.path.exists(IH_JAR):
        print("hospital_art: нет", IH_JAR)
        return
    z = zipfile.ZipFile(IH_JAR)
    src = "assets/industrialhellscape/"
    tex_map = {}
    for name in ("medical_bed", "operating_table", "vitals_monitor"):
        new = {"medical_bed": "hospital_bed"}.get(name, name)
        os.makedirs(os.path.join(ASSETS, "textures", "block"), exist_ok=True)
        with open(os.path.join(ASSETS, "textures", "block", f"{new}.png"), "wb") as f:
            f.write(z.read(src + f"textures/block/{name}.png"))
        tex_map[f"industrialhellscape:block/{name}"] = f"rpmedicine:block/{new}"
    # Экран монитора включён всегда (на нём цифры пациента).
    for n in ("vitals_monitor_screen_on.png", "vitals_monitor_screen_on.png.mcmeta"):
        with open(os.path.join(ASSETS, "textures", "block", n), "wb") as f:
            f.write(z.read(src + "textures/block/lit_unlit_textures/" + n))
    tex_map["industrialhellscape:block/lit_unlit_textures/vitals_monitor_screen_on"] = "rpmedicine:block/vitals_monitor_screen_on"
    tex_map["industrialhellscape:block/lit_unlit_textures/vitals_monitor_screen_off"] = "rpmedicine:block/vitals_monitor_screen_on"
    parts = {
        "hospital_bed": ("medical_bed/medical_bed_positive", "medical_bed/medical_bed_negative", "medical_bed"),
        "operating_table": ("operating_table/operating_table_positive", "operating_table/operating_table_negative", "operating_table"),
        "vitals_monitor": ("vitals_monitor/vitals_monitor_top", "vitals_monitor/vitals_monitor_base", "vitals_monitor"),
    }
    for block, (head, foot, item) in parts.items():
        for part, path in (("head", head), ("foot", foot)):
            m = retex(json.loads(z.read(src + f"models/block/{path}.json")), tex_map)
            if block == "vitals_monitor" and part == "head":
                m["textures"]["1"] = "rpmedicine:block/vitals_monitor_screen_on"
            write(os.path.join(ASSETS, "models", "block", f"{block}_{part}.json"), m)
        variants = {}
        for f, y in FACING_Y.items():
            for h in ("true", "false"):
                variants[f"facing={f},head={h}"] = {"model": f"rpmedicine:block/{block}_{'head' if h == 'true' else 'foot'}", **({"y": y} if y else {})}
        write(os.path.join(ASSETS, "blockstates", f"{block}.json"), {"variants": variants})
        write(os.path.join(ASSETS, "models", "item", f"{block}.json"), retex(json.loads(z.read(src + f"models/item/{item}.json")), tex_map))
        loot(block, True)


def hd():
    tex = {"medicine_cabinet": ("medibox", "medibox"), "medicine_crate": ("collisionbox", "antibox"),
           "sterilizer": ("store", "store"), "lab_table": ("scan", "scan")}
    index_path = os.path.join(ASSETS, "rpgeo", "items.json")
    index = json.load(open(index_path, encoding="utf-8")) if os.path.exists(index_path) else {}
    display = {
        "gui": {"rotation": [30, -135, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
        "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"scale": [0.5, 0.5, 0.5]},
    }
    for block, (geo, png) in tex.items():
        shutil.copyfile(os.path.join(HD, "geo", f"{geo}.geo.json"), os.path.join(ASSETS, "rpgeo", f"{block}.geo.json"))
        shutil.copyfile(os.path.join(HD, "animations", f"{geo}.animation.json"), os.path.join(ASSETS, "rpgeo", f"{block}.anim.json"))
        shutil.copyfile(os.path.join(HD, "textures", "block", f"{png}.png"), os.path.join(ASSETS, "textures", "geo", f"{block}.png"))
        index[f"rpmedicine:{block}"] = {"geo": f"rpmedicine:rpgeo/{block}.geo.json", "texture": f"rpmedicine:textures/geo/{block}.png",
                                        "animations": f"rpmedicine:rpgeo/{block}.anim.json", "fit": True, "size": 0.95}
        write(os.path.join(ASSETS, "models", "block", f"{block}_particle.json"), {"textures": {"particle": f"rpmedicine:geo/{block}"}})
        write(os.path.join(ASSETS, "blockstates", f"{block}.json"), {"variants": {
            f"facing={f}": {"model": f"rpmedicine:block/{block}_particle"} for f in FACING_Y}})
        write(os.path.join(ASSETS, "models", "item", f"{block}.json"), {"parent": "builtin/entity", "display": display,
                                                                        "textures": {"particle": f"rpmedicine:geo/{block}"}})
        loot(block, False)
    write(index_path, index)
    # Частицы ломания берут текстуру из атласа блоков, а textures/geo туда не попадает — розовые частицы (замечание 1).
    write(os.path.join(os.path.dirname(ASSETS), "minecraft", "atlases", "blocks.json"),
          {"sources": [{"type": "single", "resource": f"rpmedicine:geo/{b}"} for b in tex]})
    # Термостат: модели H&D с 0–8 пробирками.
    # Окно термостата — из H&D (TubeScreen: 8 пробирок 4×2 и инвентарь), замечание живого теста 5.
    shutil.copyfile(os.path.join(HD, "textures", "screens", "tube.png"), os.path.join(ASSETS, "textures", "gui", "thermostat.png"))
    shutil.copyfile(os.path.join(HD, "textures", "block", "tube.png"), os.path.join(ASSETS, "textures", "block", "thermostat.png"))
    for n in range(9):
        m = json.load(open(os.path.join(HD, "models", "custom", "tube.json" if n == 0 else f"tube{n}.json"), encoding="utf-8"))
        m["textures"] = {"0": "rpmedicine:block/thermostat", "all": "rpmedicine:block/thermostat", "particle": "rpmedicine:block/thermostat"}
        m["render_type"] = "minecraft:translucent"
        write(os.path.join(ASSETS, "models", "block", f"thermostat_{n}.json"), m)
    variants = {}
    for f, y in FACING_Y.items():
        for n in range(9):
            variants[f"facing={f},tubes={n}"] = {"model": f"rpmedicine:block/thermostat_{n}", **({"y": y} if y else {})}
    write(os.path.join(ASSETS, "blockstates", "thermostat.json"), {"variants": variants})
    write(os.path.join(ASSETS, "models", "item", "thermostat.json"), {"parent": "rpmedicine:block/thermostat_3"})
    loot("thermostat", False)


def oxygen():
    """Свой кислородный баллон (в модах сборки подходящего нет): зелёный баллон, хром вентиля, белая полоса."""
    from PIL import Image
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if y < 10:                                                     # корпус
                c = (46, 128, 74) if x % 4 else (36, 108, 60)
                if y in (3, 4):
                    c = (236, 240, 236)                                    # белая полоса «O2»
            elif y < 13:
                c = (196, 202, 210) if (x + y) % 3 else (160, 166, 176)    # вентиль, хром
            else:
                c = (40, 42, 46)                                           # подставка
            im.putpixel((x, y), c + (255,))
    im.save(os.path.join(ASSETS, "textures", "block", "oxygen_tank.png"))

    def el(a, b, uv):
        return {"from": a, "to": b, "faces": {f: {"uv": uv, "texture": "#0"} for f in ("north", "south", "east", "west", "up", "down")}}
    model = {"textures": {"0": "rpmedicine:block/oxygen_tank", "particle": "rpmedicine:block/oxygen_tank"}, "elements": [
        el([5, 0, 5], [11, 1, 11], [0, 13, 16, 16]),                       # подставка
        el([5.5, 1, 4.5], [10.5, 13, 11.5], [0, 0, 16, 10]),               # баллон (восьмиугольник из двух плашек)
        el([4.5, 1, 5.5], [11.5, 13, 10.5], [0, 0, 16, 10]),
        el([6.5, 13, 6.5], [9.5, 14.5, 9.5], [0, 0, 16, 10]),              # горловина
        el([7, 14.5, 7], [9, 16, 9], [0, 10, 16, 13]),                     # вентиль
        el([9, 15, 7.5], [11, 15.6, 8.5], [0, 10, 16, 13]),                # маховик
    ]}
    write(os.path.join(ASSETS, "models", "block", "oxygen_tank.json"), model)
    write(os.path.join(ASSETS, "blockstates", "oxygen_tank.json"), {"variants": {
        f"facing={f}": {"model": "rpmedicine:block/oxygen_tank", **({"y": y} if y else {})} for f, y in FACING_Y.items()}})
    write(os.path.join(ASSETS, "models", "item", "oxygen_tank.json"), {"parent": "rpmedicine:block/oxygen_tank"})
    loot("oxygen_tank", False)


PACK = os.path.join(os.environ.get("APPDATA", ""), "ElyPrismLauncher", "instances", "DEPARTMENT_s5_client", "minecraft", "mods")


def tools():
    """Инструменты из модов сборки (решения, п. 1.16): дрель — 3D Cybernetic System, иконки скальпеля, зажима,
    ранорасширителя — [CS] Augmentations, пила — [CS] Foundation. Нет сборки — файлы не трогаем."""
    def jar(prefix):
        if not os.path.isdir(PACK):
            return None
        found = [f for f in os.listdir(PACK) if f.startswith(prefix) and f.endswith(".jar")]
        return zipfile.ZipFile(os.path.join(PACK, sorted(found)[-1])) if found else None
    cyb = jar("cybernetic_system")
    if cyb:
        m = json.loads(cyb.read("assets/cybernetic_system/models/custom/hand_drill.json"))
        m["textures"] = {"1": "rpmedicine:item/surgical_drill_3d", "particle": "rpmedicine:item/surgical_drill"}
        write(os.path.join(ASSETS, "models", "item", "surgical_drill.json"), m)
        with open(os.path.join(ASSETS, "textures", "item", "surgical_drill_3d.png"), "wb") as f:
            f.write(cyb.read("assets/cybernetic_system/textures/block/hand_drill.png"))
    aug = jar("[CS] Augmentations")
    if aug:
        # Ранорасширитель у них почти как зажим — оставляем свой рисунок.
        for ours, theirs in (("scalpel", "scalpel"), ("hemostat", "hemostat")):
            with open(os.path.join(ASSETS, "textures", "item", f"{ours}.png"), "wb") as f:
                f.write(aug.read(f"assets/csaugmentations/textures/item/{theirs}.png"))
    fnd = jar("[CS] Foundation")
    if fnd:
        with open(os.path.join(ASSETS, "textures", "item", "bone_saw.png"), "wb") as f:
            f.write(fnd.read("assets/csfoundation/textures/item/refined_saw.png"))


def two_handed():
    """Плоские двуручные предметы от третьего лица — перед собой лицом наружу (замечание 18 второй проверки)."""
    d = {"rotation": [-90, 90, 0], "translation": [-1, 1, -2], "scale": [0.7, 0.7, 0.7]}
    for item in ("defibrillator", "ambu_bag", "organ_container", "stabilization_kit"):
        path = os.path.join(ASSETS, "models", "item", f"{item}.json")
        if not os.path.exists(path):
            continue
        m = json.load(open(path, encoding="utf-8"))
        if m.get("parent") != "minecraft:item/generated":
            continue
        m["display"] = {"thirdperson_righthand": d, "thirdperson_lefthand": d}
        write(path, m)


def credits():
    text = open(CREDITS, encoding="utf-8").read()
    start, end = "<!-- hospital:begin -->", "<!-- hospital:end -->"
    block = (start + "\n## Мебель госпиталя (`scripts/hospital_art.py`)\n\n"
             "| Файл в моде (`assets/rpmedicine/…`) | Откуда | Автор | Лицензия |\n|---|---|---|---|\n"
             "| `models/block/{hospital_bed,operating_table,vitals_monitor}_*.json`, `textures/block/…` | Industrial Hellscape 0.0.1: "
             "`medical_bed`, `operating_table`, `vitals_monitor` | YellowUboat | MIT |\n"
             "| `rpgeo/{medicine_cabinet,medicine_crate,sterilizer,lab_table}.*`, `textures/geo/…` | Health & Disease 1.4.2: "
             "`medibox`, `collisionbox`, `store`, `scan` | JEDIGD | MIT |\n"
             "| `models/block/thermostat_*.json`, `textures/block/thermostat.png` | Health & Disease 1.4.2: `tube*` | JEDIGD | MIT |\n" + end)
    if start in text:
        text = text[:text.index(start)] + block + text[text.index(end) + len(end):]
    else:
        text = text.rstrip("\n") + "\n\n" + block + "\n"
    open(CREDITS, "w", encoding="utf-8", newline="\n").write(text)


def main():
    ih()
    hd()
    oxygen()
    tools()
    two_handed()
    credits()
    print("hospital_art: ок")


if __name__ == "__main__":
    main()
