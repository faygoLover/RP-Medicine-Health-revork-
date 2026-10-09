# Выгрузка моделей предметов для галереи (tools/model_gallery): геометрия квадами в пикселях блока (0–16),
# текстуры, display по контекстам — так же, как их ставит игра. Галерея сама считает вид в слоте, от 1-го и 3-го лица.
#   python scripts/gallery_export.py
import hashlib
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import preview  # noqa: E402

ROOT = os.path.join(HERE, "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "rpmedicine")
OUT = os.path.join(ROOT, "tools", "model_gallery", "data")
LANG = json.load(open(os.path.join(ASSETS, "lang", "ru_ru.json"), encoding="utf-8"))
INDEX = json.load(open(os.path.join(ASSETS, "rpgeo", "items.json"), encoding="utf-8"))

# Значения ванильных родителей.
GENERATED = {
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
}
HANDHELD = dict(GENERATED, thirdperson_righthand={"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
                firstperson_righthand={"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]})
CONTEXTS = ("gui", "firstperson_righthand", "thirdperson_righthand", "ground", "fixed")
# Группы: одна и та же 3D-модель, разные текстуры — в галерее одна строка, вид настраивается разом для всех.
# Название группы — по предмету, который в неё входит; иначе — общее начало названий.
GROUP_NAMES = {
    "adrenaline": "Шприц-ручки", "lidocaine": "Флаконы", "paracetamol": "Банки таблеток", "saline": "Пакеты капельницы",
    "test_tube": "Пробирки", "antiseptic": "Хлоргексидин", "lr_vaseline": "Мази", "syringe": "Шприцы (пустой и использованный)",
    "filled_syringe": "Шприцы (набранный и для забора крови)",
}

tex_ids = {}


def tex_url(path):
    """Текстура -> data/tex/<хеш>.png (одна копия на файл)."""
    path = os.path.abspath(path)
    if path not in tex_ids:
        h = hashlib.md5(open(path, "rb").read()).hexdigest()[:12]
        dst = os.path.join(OUT, "tex", h + ".png")
        if not os.path.exists(dst):
            shutil.copyfile(path, dst)
        tex_ids[path] = f"data/tex/{h}.png"
    return tex_ids[path]


def raw_model(item):
    return json.load(open(os.path.join(ASSETS, "models", "item", f"{item}.json"), encoding="utf-8"))


def flat_quads(layer_rl):
    """Плоская иконка generated: две стороны карточки толщиной 1 px (как в игре)."""
    url = tex_url(preview.texture_path(layer_rl))
    f = [(0, 0, 7.5), (16, 0, 7.5), (16, 16, 7.5), (0, 16, 7.5)]
    b = [(0, 0, 8.5), (16, 0, 8.5), (16, 16, 8.5), (0, 16, 8.5)]
    return [
        {"p": [b[3], b[2], b[1], b[0]], "uv": [(0, 0), (1, 0), (1, 1), (0, 1)], "t": url},
        {"p": [f[2], f[3], f[0], f[1]], "uv": [(0, 0), (1, 0), (1, 1), (0, 1)], "t": url},
    ]


def json_model_quads(m):
    q = preview.json_quads(m, tex_loader=lambda rl: tex_url(preview.texture_path(rl)))
    return [{"p": [list(map(float, p)) for p in pts], "uv": uvs, "t": t} for pts, uvs, t in q]


def geo_model_quads(item, entry):
    g = os.path.join(ASSETS, entry["geo"].split(":", 1)[1])
    t = os.path.join(ASSETS, entry["texture"].split(":", 1)[1])
    q = preview.geo_quads(g, t, hide=tuple(entry.get("hide", ())) + tuple(entry.get("hide_static", ())), tex_loader=lambda path: tex_url(path))
    pts = [p for quad in q for p in quad[0]]
    if entry.get("fit") and pts:
        lo = [min(p[i] for p in pts) for i in range(3)]
        hi = [max(p[i] for p in pts) for i in range(3)]
        k = entry.get("size", 0.75) * 16 / max(h - l for h, l in zip(hi, lo))
        mid = [(h + l) / 2 for h, l in zip(hi, lo)]
        fix = lambda p: [(p[i] - mid[i]) * k + 8 for i in range(3)]
    else:
        fix = lambda p: [p[0], p[1] + 8.16, p[2]]       # GeoItemRenderer: начало модели в (0,5; 0,51; 0,5)
    return [{"p": [fix(p) for p in pts_], "uv": uvs, "t": tx} for pts_, uvs, tx in q]


def resolve(item):
    """(квады, display, тип, gui_light) предмета так, как его нарисует игра в руке и в слоте."""
    m = raw_model(item)
    kind = "json"
    if m.get("loader") == "forge:separate_transforms":
        base = m.get("base", {})
        gui = (m.get("perspectives") or {}).get("gui")
        if base.get("parent") == "builtin/entity" and f"rpmedicine:{item}" in INDEX:
            quads = geo_model_quads(item, INDEX[f"rpmedicine:{item}"])
            display = dict(base.get("display", {}))
            kind = "geo"
        elif "parent" in base and base["parent"].startswith("rpmedicine:"):
            mm = preview.load_model(base["parent"])
            quads, display = json_model_quads(mm), dict(mm.get("display", {}))
        else:
            quads, display = flat_quads(base["textures"]["layer0"]), dict(GENERATED)
        if gui and "layer0" in gui.get("textures", {}):
            display["_gui_flat"] = gui["textures"]["layer0"]
        return quads, display, kind, m.get("gui_light", "side")
    parent = m.get("parent", "")
    if parent in ("minecraft:item/generated", "item/generated", "minecraft:item/handheld", "item/handheld"):
        d = dict(HANDHELD if "handheld" in parent else GENERATED)
        d.update(m.get("display", {}))
        return flat_quads(m["textures"]["layer0"]), d, "flat", "front"
    mm = preview.load_model(f"rpmedicine:item/{item}")
    return json_model_quads(mm), dict(mm.get("display", {})), "json", mm.get("gui_light", "side")


def geometry_key(quads):
    """Подпись геометрии: точки и развёртка без текстуры. Плоские карточки (2 квада) не группируются."""
    if len(quads) <= 2:
        return None
    flat = [[round(v, 2) for pt in q["p"] for v in pt] + [round(v, 3) for uv in q["uv"] for v in uv] for q in quads]
    return hashlib.md5(json.dumps(flat).encode()).hexdigest()[:10]


def group_name(members):
    for m in members:
        if m["id"] in GROUP_NAMES:
            return GROUP_NAMES[m["id"]]
    words = [m["name"].split() for m in members]
    common = []
    for ws in zip(*words):
        if len(set(ws)) > 1:
            break
        common.append(ws[0])
    return " ".join(common) if common else members[0]["name"] + " и др."


def main():
    # Чистим содержимое, а не саму папку: она может быть открыта (рабочая папка, сервер галереи).
    os.makedirs(OUT, exist_ok=True)
    for name in os.listdir(OUT):
        path = os.path.join(OUT, name)
        shutil.rmtree(path) if os.path.isdir(path) else os.remove(path)
    os.makedirs(os.path.join(OUT, "tex"))
    os.makedirs(os.path.join(OUT, "models"))
    items = []
    for key, name in LANG.items():
        if not key.startswith("item.rpmedicine.") or key.count(".") != 2:
            continue
        item = key.split(".")[2]
        if not os.path.exists(os.path.join(ASSETS, "models", "item", f"{item}.json")):
            continue
        try:
            quads, display, kind, light = resolve(item)
        except Exception as ex:  # модель не разобрать — пропускаем, но показываем в списке
            print("skip", item, ex)
            continue
        disp = {c: display.get(c) for c in CONTEXTS if display.get(c)}
        icon = os.path.join(ASSETS, "textures", "item", f"{item}.png")
        rec = {"id": item, "name": name, "kind": kind, "light": light, "display": disp,
               "icon": tex_url(icon) if os.path.exists(icon) else None, "quads": len(quads)}
        if "_gui_flat" in display:
            rec["gui_flat"] = tex_url(preview.texture_path(display["_gui_flat"]))
        with open(os.path.join(OUT, "models", f"{item}.json"), "w", encoding="utf-8") as f:
            json.dump(quads, f, separators=(",", ":"))
        rec["_geo"] = geometry_key(quads)
        items.append(rec)
    items.sort(key=lambda r: r["name"])
    by_geo = {}
    for r in items:
        g = r.pop("_geo")
        if g:
            by_geo.setdefault(g, []).append(r)
    groups = []
    for g, members in by_geo.items():
        if len(members) < 2:
            continue
        name = group_name(members)
        for r in members:
            r["group"] = g
        groups.append({"key": g, "name": name, "members": [r["id"] for r in members]})
    with open(os.path.join(OUT, "groups.json"), "w", encoding="utf-8") as f:
        json.dump(groups, f, ensure_ascii=False, indent=1)
    with open(os.path.join(OUT, "items.json"), "w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False, indent=1)
    print("gallery:", len(items), "предметов,", len(groups), "групп,", len(tex_ids), "текстур")


if __name__ == "__main__":
    main()
