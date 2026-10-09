# Модели и иконки предметов по замечаниям брата от 09.10.2026 (docs/test_findings.md, И1–И44).
# Запускается после item_art.py (из gen_data.py) и переписывает модели, текстуры и rpgeo/items.json
# для перечисленных здесь предметов. Чужие модели — из docs/reference/родственные моды (авторы — в
# docs/assets_credits.md, блок item_models), свои — собраны здесь же из кубов.
#   python scripts/item_models.py            — пересобрать
#   python scripts/item_models.py --sheet F  — плюс лист предпросмотра всех моделей в F
import colorsys
import copy
import json
import random
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import geo_fix  # noqa: E402
import preview  # noqa: E402

ROOT = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "rpmedicine")
DATA = os.path.join(ROOT, "data", "rpmedicine", "rpmedicine")
REL = os.path.join(HERE, "..", "docs", "reference", "родственные моды")
CREDITS = os.path.join(HERE, "..", "docs", "assets_credits.md")

MODS = {
    "sa_combat": ("sa_essentials-forge-1.20.1-1.0.0", "Survivor's Arsenal: Essentials 1.0.0", "ogaba, titammods", "All Rights Reserved"),
    "sa_core": ("sa_lib-forge-1.20.1-1.0.1", "SA Lib 1.0.1", "ogaba, titammods", "All Rights Reserved"),
    "survivorsarsenal": ("survivorsarsenal-1.1.7_test_version", "Survivor's Arsenal 1.1.7", "ogabasferr", "All Rights Reserved"),
    "survival_instinct": ("survival_instinct-1.0.2-forge-1.20.1", "Survival Instinct 1.0.2", "tohir", "AFL-3.0"),
    "marbledsmelees": ("marbledsmelees-1.20.1-1.0.0", "Marbled's Melees 1.0.0", "MarbledNull", "All Rights Reserved"),
    "refurbished_furniture": ("refurbished_furniture-forge-1.20.1-1.0.20", "MrCrayfish's Refurbished Furniture 1.0.20", "MrCrayfish", "MIT"),
    "mekanism": ("Mekanism-1.20.1-10.4.16.80", "Mekanism 10.4.16", "Aidancbrady и др.", "MIT"),
    "selfexpression": ("selfexpression-2.22a-forge-1.20.1", "Self Expression 2.22a", "Redynine", "All Rights Reserved"),
    "lrtactical": ("lrtactical-1.20.1-0.4.3", "LesRaisins Tactical Equipments 0.4.3", "LesRaisins Studio", "GPL-3.0"),
    "health_and_disease": ("health_and_disease-1.4.2-forge-1.20.1", "Health & Disease 1.4.2", "JEDIGD", "MIT"),
}

credits = []          # (файл в моде, откуда, как)
index = {}            # rpgeo/items.json
times = {}            # use_times/animations.json
built = []            # предметы, для листа предпросмотра


# ------------------------------------------------------------------ файлы
def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def src_file(ns, rel):
    mod = MODS[ns][0]
    return os.path.join(REL, mod, "assets", ns, rel)


def credit(dst, ns, rel, how=""):
    credits.append((dst, ns, rel, how))


def save(im, rel):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    return path


def model_path(item):
    return os.path.join(ASSETS, "models", "item", f"{item}.json")


# ------------------------------------------------------------------ цвета
def hls(r, g, b):
    return colorsys.rgb_to_hls(r / 255, g / 255, b / 255)


def rgb(h, l, s):
    r, g, b = colorsys.hls_to_rgb(h % 1, max(0, min(1, l)), max(0, min(1, s)))
    return round(r * 255), round(g * 255), round(b * 255)


def recolor(im, fn):
    """fn(r, g, b, a) -> (r, g, b, a) или None (оставить)."""
    im = im.copy()
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            p = px[x, y]
            if p[3] == 0:
                continue
            q = fn(*p) if fn.__code__.co_argcount == 4 else fn(x, y, *p)
            if q is not None:
                px[x, y] = q
    return im


def hue_range(lo, hi, min_s=0.18):
    def pred(r, g, b):
        h, l, s = hls(r, g, b)
        return s >= min_s and (lo <= h <= hi if lo <= hi else (h >= lo or h <= hi))
    return pred


def to_hue(pred, hue, sat=None, light=1.0):
    """Пиксели pred -> тот же свет и насыщенность, другой тон."""
    def fn(r, g, b, a):
        if not pred(r, g, b):
            return None
        h, l, s = hls(r, g, b)
        return rgb(hue, l * light, s if sat is None else sat) + (a,)
    return fn


def palette_map(pred, dark, light):
    """Пиксели pred -> градиент от dark к light по их яркости (сохраняет светотень)."""
    def fn(r, g, b, a):
        if not pred(r, g, b):
            return None
        _, l, _ = hls(r, g, b)
        t = max(0.0, min(1.0, (l - 0.15) / 0.8))
        return tuple(round(dark[i] + (light[i] - dark[i]) * t) for i in range(3)) + (a,)
    return fn


def whitish(r, g, b):
    h, l, s = hls(r, g, b)
    return l > 0.55 and s < 0.22


# ------------------------------------------------------------------ JSON-модели других модов
BLOCK_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


def load_json_model(rl):
    """Модель со всей цепочкой родителей (родитель — тоже модель мода): элементы, текстуры, display."""
    ns, path = rl.split(":", 1)
    p = src_file(ns, f"models/{path}.json")
    d = json.load(open(p, encoding="utf-8"))
    parent = d.get("parent")
    if parent:
        pns, ppath = parent.split(":", 1) if ":" in parent else ("minecraft", parent)
        if pns == "minecraft" and ppath.startswith("block/"):
            base = {"display": BLOCK_DISPLAY}
        elif pns in MODS and os.path.exists(src_file(pns, f"models/{ppath}.json")):
            base = load_json_model(f"{pns}:{ppath}")
        else:
            base = {}
        merged = copy.deepcopy(base)
        for k, v in d.items():
            if k == "parent":
                continue
            if k in ("textures", "display") and k in merged:
                merged[k] = dict(merged[k], **v)
            else:
                merged[k] = v
        return merged
    return d


def json_copy(item, rl, tex_fn=None, scale=1.0, display=None, extra_elements=(), texture_size=None, note=""):
    """Чужая JSON-модель -> наша: текстуры копируются (с перекраской tex_fn), ссылки — на наши."""
    m = load_json_model(rl)
    texs = m.get("textures", {})
    out_tex = {}
    done = {}
    for key, ref in texs.items():
        if ref.startswith("#"):
            out_tex[key] = ref
            continue
        rns, rpath = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        if ref not in done:
            im = Image.open(src_file(rns, f"textures/{rpath}.png")).convert("RGBA")
            if tex_fn:
                im = tex_fn(im)
            name = f"{item}_{len(done)}"
            save(im, f"textures/item/m/{name}.png")
            credit(f"textures/item/m/{name}.png", rns, f"textures/{rpath}.png", note)
            done[ref] = f"rpmedicine:item/m/{name}"
        out_tex[key] = done[ref]
    out = {"texture_size": texture_size or m.get("texture_size", [16, 16]), "textures": out_tex,
           "elements": copy.deepcopy(m.get("elements", [])) + list(extra_elements),
           "display": copy.deepcopy(display if display is not None else m.get("display", {}))}
    if "particle" not in out_tex and out_tex:
        out_tex["particle"] = next(iter(v for v in out_tex.values() if not v.startswith("#")))
    if out.get("texture_size") in ([16, 16], None):
        out.pop("texture_size")
    if scale != 1.0:
        for v in out["display"].values():
            sc = v.get("scale", [1, 1, 1])
            v["scale"] = [round(c * scale, 4) for c in sc]
    if "gui_light" in m:
        out["gui_light"] = m["gui_light"]
    credit(f"models/item/{item}.json", rl.split(":")[0], f"models/{rl.split(':', 1)[1]}.json", note)
    write(model_path(item), out)
    drop_geo(item)
    built.append(f"rpmedicine:item/{item}")
    return out


# ------------------------------------------------------------------ свои JSON-модели из кубов
def box(frm, to, tex="#t", uv=None, faces="nsewud", rot=None, uvs=None):
    """Куб. uv — общий прямоугольник текстуры (0–16) на все грани, uvs — словарь по граням."""
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    fd = {}
    for c in faces:
        f = names[c]
        u = (uvs or {}).get(f, uv)
        fd[f] = {"texture": tex} if u is None else {"texture": tex, "uv": list(u)}
    el = {"from": list(frm), "to": list(to), "faces": fd}
    if rot:
        el["rotation"] = {"origin": rot[0], "axis": rot[1], "angle": rot[2]}
    return el


def swatch(colors, size=16):
    """Текстура-палитра: полоса цвета i — столбцы 2i..2i+1 (uv cell(i))."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for i, c in enumerate(colors):
        for y in range(size):
            for x in (2 * i, 2 * i + 1):
                if x < size:
                    r, g, b = c[:3]
                    # Лёгкий шум по строкам — чтобы грани не были мёртвыми.
                    k = 1.0 + (((x * 7 + y * 13) % 5) - 2) * 0.025
                    im.putpixel((x, y), (min(255, round(r * k)), min(255, round(g * k)), min(255, round(b * k)), c[3] if len(c) > 3 else 255))
    return im


def cell(i):
    return (2 * i + 0.2, 0.2, 2 * i + 1.8, 15.8)


def own_model(item, elements, tex, display, particle=None, gui_light=None):
    """Своя модель: текстура tex (PIL), элементы ссылаются на #t."""
    save(tex, f"textures/item/m/{item}.png")
    out = {"textures": {"t": f"rpmedicine:item/m/{item}", "particle": particle or f"rpmedicine:item/m/{item}"},
           "elements": elements, "display": display}
    if gui_light:
        out["gui_light"] = gui_light
    write(model_path(item), out)
    drop_geo(item)
    built.append(f"rpmedicine:item/{item}")
    return out


def extrude(item, icon, depth=1.0, depth_fn=None, display=None, scale=1.0, note=None):
    """
    Объёмная модель из плоской иконки: пиксели -> столбики. depth_fn(x, y, r, g, b) -> толщина в пикселях
    иконки (по умолчанию depth). Соседние пиксели одной толщины в строке сливаются в один элемент;
    лицо и спина — сама иконка, бока — цвет крайнего пикселя.
    """
    w, h = icon.size
    px = icon.load()
    k = 16.0 / w
    save(icon, f"textures/item/m/{item}.png")
    els = []
    for y in range(h):
        x = 0
        while x < w:
            if px[x, y][3] < 128:
                x += 1
                continue
            d = depth_fn(x, y, *px[x, y][:3]) if depth_fn else depth
            x2 = x
            while x2 + 1 < w and px[x2 + 1, y][3] >= 128 and (depth_fn(x2 + 1, y, *px[x2 + 1, y][:3]) if depth_fn else depth) == d:
                x2 += 1
            z0, z1 = 8 - d * k / 2, 8 + d * k / 2
            X0, X1 = x * k, (x2 + 1) * k
            Y0, Y1 = 16 - (y + 1) * k, 16 - y * k
            u0, u1 = x * k, (x2 + 1) * k
            v0, v1 = y * k, (y + 1) * k
            fu = {"north": (u1, v0, u0, v1), "south": (u0, v0, u1, v1),
                  "west": (u0, v0, u0 + k, v1), "east": (u1 - k, v0, u1, v1),
                  "up": (u0, v0, u1, v0 + k), "down": (u0, v1 - k, u1, v1)}
            els.append({"from": [round(X0, 4), round(Y0, 4), round(z0, 4)], "to": [round(X1, 4), round(Y1, 4), round(z1, 4)],
                        "faces": {f: {"texture": "#t", "uv": [round(c, 4) for c in u]} for f, u in fu.items()}})
            x = x2 + 1
    disp = copy.deepcopy(display or HELD)
    if scale != 1.0:
        for v in disp.values():
            v["scale"] = [round(c * scale, 4) for c in v.get("scale", [1, 1, 1])]
    out = {"textures": {"t": f"rpmedicine:item/m/{item}", "particle": f"rpmedicine:item/m/{item}"}, "elements": els, "display": disp}
    write(model_path(item), out)
    drop_geo(item)
    built.append(f"rpmedicine:item/{item}")
    return out


def edge_depth(icon, levels):
    """Толщина по расстоянию до края (середина толще — «скрутка», «пачка»)."""
    w, h = icon.size
    px = icon.load()
    dist = {}
    for y in range(h):
        for x in range(w):
            if px[x, y][3] < 128:
                continue
            d = 0
            while True:
                d += 1
                ring = [(x + dx, y + dy) for dx in range(-d, d + 1) for dy in range(-d, d + 1) if max(abs(dx), abs(dy)) == d]
                if any(not (0 <= a < w and 0 <= b < h) or px[a, b][3] < 128 for a, b in ring) or d >= len(levels):
                    break
            dist[(x, y)] = levels[min(d, len(levels)) - 1]
    return lambda x, y, r, g, b: dist.get((x, y), levels[0])


# Как держать предметы (как у generated/handheld, но для объёмных).
HELD = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
}
TOOL = dict(HELD, **{
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
})
# Небольшой предмет в 3D: в инвентаре повёрнут, чтобы был виден объём.
SMALL = dict(HELD, **{
    "gui": {"rotation": [25, -35, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
})


# ------------------------------------------------------------------ Bedrock (наш client/geo)
GEO_DIR = os.path.join(ASSETS, "rpgeo")


def drop_geo(item):
    """Предмет больше не 3D-модель client/geo — убрать из индекса и файлы."""
    index.pop(f"rpmedicine:{item}", None)
    times.pop(f"rpmedicine:{item}", None)
    for ext in (".geo.json", ".anim.json"):
        p = os.path.join(GEO_DIR, item + ext)
        if os.path.exists(p):
            os.remove(p)


FIT_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1.5, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 1.5, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -25, 0], "translation": [1, 2, 0], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, 25, 0], "translation": [-1, 2, 0], "scale": [0.6, 0.6, 0.6]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"translation": [0, 13, 7], "scale": [1, 1, 1]},
}


def geo_scale(geo, sx=1.0, sy=1.0, sz=1.0):
    """Растянуть модель Bedrock по осям (кубы и опоры костей — от начала координат)."""
    for b in geo["minecraft:geometry"][0].get("bones", []):
        if "pivot" in b:
            b["pivot"] = [b["pivot"][0] * sx, b["pivot"][1] * sy, b["pivot"][2] * sz]
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            c["origin"] = [o[0] * sx, o[1] * sy, o[2] * sz]
            c["size"] = [s[0] * sx, s[1] * sy, s[2] * sz]
            if "pivot" in c:
                c["pivot"] = [c["pivot"][0] * sx, c["pivot"][1] * sy, c["pivot"][2] * sz]
    return geo


def geo_subtree(geo, keep):
    """Оставить кости keep с потомками; предки — без кубов (поза сохраняется), остальное убрать."""
    bones = geo["minecraft:geometry"][0]["bones"]
    by = {b["name"]: b for b in bones}

    def ancestors(n):
        out = []
        while n:
            out.append(n)
            n = by[n].get("parent")
        return out

    keepset = set()
    for b in bones:
        chain = ancestors(b["name"])
        if any(k in chain for k in keep):
            keepset.add(b["name"])
    anc = set()
    for k in keep:
        anc.update(ancestors(k)[1:])
    out = []
    for b in bones:
        if b["name"] in keepset:
            out.append(b)
        elif b["name"] in anc:
            nb = {k: v for k, v in b.items() if k != "cubes"}
            out.append(nb)
    geo["minecraft:geometry"][0]["bones"] = out
    return geo


def geo_item(item, geo, tex, size=0.8, anim=None, use=None, fit=True, display=None, icon=True, note_src=None, icon_view=None):
    """Наша 3D-модель (client/geo). geo — dict, tex — PIL. Иконка в инвентаре — рендер модели."""
    os.makedirs(GEO_DIR, exist_ok=True)
    geo_fix.fix(geo)
    write(os.path.join(GEO_DIR, f"{item}.geo.json"), geo)
    save(tex, f"textures/geo/{item}.png")
    entry = {"geo": f"rpmedicine:rpgeo/{item}.geo.json", "texture": f"rpmedicine:textures/geo/{item}.png"}
    if anim:
        write(os.path.join(GEO_DIR, f"{item}.anim.json"), anim)
        entry["animations"] = f"rpmedicine:rpgeo/{item}.anim.json"
        if use:
            entry["use"] = use
            times[f"rpmedicine:{item}"] = round(anim_len(anim, use), 2)
    if fit:
        entry["fit"] = True
        entry["size"] = size
        entry["hide"] = ["lefthand", "righthand", "lefthand_pos", "righthand_pos"]
    index[f"rpmedicine:{item}"] = entry
    if icon:
        q = preview.geo_quads(os.path.join(GEO_DIR, f"{item}.geo.json"), os.path.join(ASSETS, "textures", "geo", f"{item}.png"))
        if icon_view:
            icon_from(item, q, yaw=icon_view[0], pitch=icon_view[1], outline=True)
        else:
            icon_from(item, q)
    model = {"loader": "forge:separate_transforms", "gui_light": "front",
             "base": {"parent": "builtin/entity", "display": display or FIT_DISPLAY, "textures": {"particle": f"rpmedicine:item/{item}"}},
             "perspectives": {"gui": {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpmedicine:item/{item}"}}}}
    write(model_path(item), model)
    built.append(f"rpmedicine:rpgeo/{item}.geo.json")


def anim_len(anim, name):
    a = anim["animations"].get(name, {})
    if "animation_length" in a:
        return float(a["animation_length"])
    t = 0.0
    for ch in a.get("bones", {}).values():
        for v in ch.values():
            if isinstance(v, dict):
                for k in v:
                    try:
                        t = max(t, float(k))
                    except ValueError:
                        pass
    return t


def our_geo(name):
    return json.load(open(os.path.join(GEO_DIR, f"{name}.geo.json"), encoding="utf-8"))


def our_anim(name):
    p = os.path.join(GEO_DIR, f"{name}.anim.json")
    return json.load(open(p, encoding="utf-8")) if os.path.exists(p) else None


def our_geo_tex(name):
    return Image.open(os.path.join(ASSETS, "textures", "geo", f"{name}.png")).convert("RGBA")


# ------------------------------------------------------------------ иконки из моделей
def icon_from(item, quads, size=32, yaw=-135, pitch=30, outline=False):
    """Иконка 32×32 — рендер модели (как предмет выглядит в 3D), без полупрозрачной каймы."""
    im = preview.render(quads, 160, yaw, pitch, bg=(0, 0, 0, 0))
    bb = im.getbbox()
    if bb:
        im = im.crop(bb)
    side = max(im.size) + 4
    sq = Image.new("RGBA", (side, side))
    sq.paste(im, ((side - im.size[0]) // 2, (side - im.size[1]) // 2))
    ic = sq.resize((size, size), Image.LANCZOS)
    px = ic.load()
    for y in range(size):
        for x in range(size):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255 if a >= 110 else 0)
    if outline:
        # Тонкий предмет: тёмная обводка, чтобы читался на фоне слота.
        src = ic.copy().load()
        for y in range(size):
            for x in range(size):
                if src[x, y][3]:
                    continue
                if any(0 <= x + dx < size and 0 <= y + dy < size and src[x + dx, y + dy][3]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    px[x, y] = (28, 30, 36, 255)
    save(ic, f"textures/item/{item}.png")
    return ic


def icon_from_json(item, yaw=-135, pitch=30):
    return icon_from(item, preview.json_quads(preview.load_model(f"rpmedicine:item/{item}")), yaw=yaw, pitch=pitch)


def our_display(name):
    """display базовой модели нашего 3D-предмета (как его держат)."""
    m = json.load(open(model_path(name), encoding="utf-8"))
    return copy.deepcopy(m.get("base", m).get("display", FIT_DISPLAY))


def geo_like(item, src, tex_fn=None, scale=(1, 1, 1), size=None, display=None, note="", icon=True):
    """Копия нашей 3D-модели src (геометрия, анимация, текстура с перекраской) под другим предметом."""
    e = index.get(f"rpmedicine:{src}") or json.load(open(os.path.join(GEO_DIR, "items.json"), encoding="utf-8"))[f"rpmedicine:{src}"]
    geo = our_geo(src)
    if scale != (1, 1, 1):
        geo_scale(geo, *scale)
    tex = our_geo_tex(src)
    if tex_fn:
        tex = tex_fn(tex)
    anim = our_anim(src)
    fit = e.get("fit", False)
    disp = display or our_display(src)
    geo_item(item, geo, tex, size=size if size is not None else e.get("size", 0.8), anim=anim, use=e.get("use"), fit=fit, display=disp, icon=icon)
    if not fit and e.get("hide"):
        index[f"rpmedicine:{item}"]["hide"] = e["hide"]
    credits.append((f"rpgeo/{item}.geo.json", "rpmedicine", f"rpgeo/{src}.geo.json", note or "копия модели"))


class Kit:
    """Своя модель из цветных кубов (JSON, ванильные повороты ±22,5/45°). Цвет — ячейка 2×32 px палитры 32×32."""

    def __init__(self, item):
        self.item = item
        self.colors = []
        self.els = []

    def c(self, color):
        color = tuple(color) + ((255,) if len(color) == 3 else ())
        if color not in self.colors:
            self.colors.append(color)
        return self.colors.index(color)

    def box(self, frm, to, color, faces="nsewud", rot=None, colors=None):
        """colors — {грань: цвет} поверх основного (например, экран спереди)."""
        i = self.c(color)
        names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
        fd = {}
        for ch in faces:
            f = names[ch]
            j = self.c(colors[f]) if colors and f in colors else i
            fd[f] = {"texture": "#t", "uv": [j + 0.1, 0.1, j + 0.9, 15.9]}
        el = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": fd}
        if rot:
            el["rotation"] = {"origin": list(rot[0]), "axis": rot[1], "angle": rot[2]}
        self.els.append(el)
        return el

    def texture(self):
        im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        for i, (r, g, b, a) in enumerate(self.colors[:16]):
            for y in range(32):
                for x in (2 * i, 2 * i + 1):
                    k = 1.0 + (((x * 7 + y * 13) % 5) - 2) * 0.02
                    im.putpixel((x, y), (min(255, round(r * k)), min(255, round(g * k)), min(255, round(b * k)), a))
        assert len(self.colors) <= 16, (self.item, len(self.colors))
        return im

    def done(self, display=None, scale=1.0, icon=True, icon_view=(-135, 30)):
        disp = copy.deepcopy(display or SMALL)
        if scale != 1.0:
            for v in disp.values():
                v["scale"] = [round(x * scale, 4) for x in v.get("scale", [1, 1, 1])]
        # uv в единицах 0–16 по ширине 32 px: ячейка i — столбцы 2i..2i+1 = uv i..i+1.
        own_model(self.item, self.els, self.texture(), disp)
        if icon:
            icon_from_json(self.item, yaw=icon_view[0], pitch=icon_view[1])


def ring(kit, cx, cy, z0, z1, r, t, color, axis="z", segs=8):
    """Кольцо-восьмиугольник в плоскости XY (ось z): 4 прямых и 4 под 45°."""
    s = r * 0.41421
    # Прямые стороны: верх, низ, лево, право.
    kit.box([cx - s, cy + r - t, z0], [cx + s, cy + r, z1], color)
    kit.box([cx - s, cy - r, z0], [cx + s, cy - r + t, z1], color)
    kit.box([cx - r, cy - s, z0], [cx - r + t, cy + s, z1], color)
    kit.box([cx + r - t, cy - s, z0], [cx + r, cy + s, z1], color)
    # Диагонали — те же плашки, повёрнутые на 45° вокруг центра.
    kit.box([cx - s, cy + r - t, z0], [cx + s, cy + r, z1], color, rot=((cx, cy, z0), "z", 45))
    kit.box([cx - s, cy - r, z0], [cx + s, cy - r + t, z1], color, rot=((cx, cy, z0), "z", 45))
    kit.box([cx - r, cy - s, z0], [cx - r + t, cy + s, z1], color, rot=((cx, cy, z0), "z", 45))
    kit.box([cx + r - t, cy - s, z0], [cx + r, cy + s, z1], color, rot=((cx, cy, z0), "z", 45))


def disc(kit, cx, cy, z0, z1, r, color, colors=None):
    """Диск-восьмиугольник: квадрат и квадрат под 45°."""
    s = r * 0.8
    kit.box([cx - s, cy - s, z0], [cx + s, cy + s, z1], color, colors=colors)
    kit.box([cx - s, cy - s, z0 + 0.01], [cx + s, cy + s, z1 - 0.01], color, rot=((cx, cy, z0), "z", 45), colors=colors)


# ================================================================== предметы

def part_a():
    blue = hue_range(0.5, 0.72)
    # И1. Аптечка: иконка — слот-иконка CAR first aid kit из LR.
    ic = Image.open(src_file("lrtactical", "textures/consumable/slot/carfak.png")).convert("RGBA").resize((32, 32), Image.LANCZOS)
    save(ic, "textures/item/first_aid_kit.png")
    credit("textures/item/first_aid_kit.png", "lrtactical", "textures/consumable/slot/carfak.png", "уменьшено")

    # И3–И4. Бинт и ИПП — бинт sa_combat; у ИПП белое — в зелёный с оттенками.
    json_copy("bandage", "sa_combat:item/bandage")
    json_copy("pressure_dressing", "sa_combat:item/bandage",
              tex_fn=lambda im: recolor(im, palette_map(lambda r, g, b: hls(r, g, b)[2] < 0.25, (34, 74, 40), (150, 206, 140))),
              note="белое -> зелёное")
    icon_from_json("bandage")
    icon_from_json("pressure_dressing")

    # И5. Набор стабилизации — вазелин LR, синее -> красные оттенки, в 1,5 раза больше.
    geo_like("stabilization_kit", "lr_vaseline", tex_fn=lambda im: recolor(im, to_hue(blue, 0.0, light=1.05)),
             size=0.8 * 1.5, note="синее -> красное, крупнее")
    # И10. Мазь — тот же вазелин, синее -> жёлтое; анимация — его же.
    geo_like("antibiotic_ointment", "lr_vaseline", tex_fn=lambda im: recolor(im, to_hue(blue, 0.13, light=1.15)),
             note="синее -> жёлтое")
    # И11. Набор для швов — AI-2 LR, жёлтое -> серое; шире в 1,5 раза.
    gray = lambda im: recolor(im, palette_map(hue_range(0.05, 0.2, 0.2), (58, 60, 64), (196, 200, 206)))
    geo_like("suture_kit", "lr_ai2", tex_fn=gray, scale=(1.5, 1, 1), note="жёлтое -> серое, шире")

    # И9. Хлоргексидин — флакон амоксициллина: полоса бледно-голубая, флакон площе (по глубине 0,7).
    pale = lambda im: recolor(im, lambda r, g, b, a: (rgb(0.55, 0.78, 0.45) + (a,)) if hue_range(0.45, 0.62, 0.25)(r, g, b) else None)
    geo_like("antiseptic", "amoxicillin", tex_fn=pale, scale=(1, 1, 0.7), note="полоса бледно-голубая, площе")
    # Растворы хлоргексидина: тот же флакон, полоса своего цвета; концентрат крупнее.
    for item, (hue, sat, light), sc in (("chlorhexidine_hands", (0.33, 0.45, 0.8), 1.0), ("chlorhexidine_skin", (0.93, 0.55, 0.78), 1.0),
                                        ("chlorhexidine_concentrate", (0.62, 0.7, 0.42), 1.15)):
        geo_like(item, "amoxicillin", tex_fn=lambda im, hue=hue, sat=sat, light=light: recolor(
            im, lambda r, g, b, a: (rgb(hue, light, sat) + (a,)) if hue_range(0.45, 0.62, 0.25)(r, g, b) else None),
            scale=(sc, sc, sc * 0.7), note="полоса своего цвета")
    # Разведение концентрата водой: больше воды — слабее (1 бутылка -> 0,5 %, 2 -> 0,2 %, 3 -> 0,05 %), выход — один флакон.
    water = {"type": "forge:partial_nbt", "item": "minecraft:potion", "nbt": {"Potion": "minecraft:water"}}
    for n, (out, cnt) in enumerate((("chlorhexidine_skin", 1), ("chlorhexidine_hands", 1), ("antiseptic", 1)), start=1):
        write(os.path.join(ROOT, "data", "rpmedicine", "recipes", f"chlorhexidine_dilute_{n}.json"), {
            "type": "minecraft:crafting_shapeless",
            "ingredients": [{"item": "rpmedicine:chlorhexidine_concentrate"}] + [water] * n,
            "result": {"item": f"rpmedicine:{out}", "count": cnt}})

    # И26. Глюкоза в таблетках — полоса жёлтая.
    geo_like("glucose_tablets", "amoxicillin",
             tex_fn=lambda im: recolor(im, to_hue(hue_range(0.45, 0.62, 0.25), 0.14, sat=0.75, light=1.05)), note="полоса жёлтая")

    # И18. Игла для декомпрессии — шприц-ручка, ярко-оранжевая. И25. Циклоспорин — ручка, тёмно-зелёная.
    def pen_color(c):
        def fn(im):
            return recolor(im, lambda r, g, b, a: None if hls(r, g, b)[1] > 0.88 else
                           tuple(round(v * (0.35 + 0.75 * hls(r, g, b)[1])) for v in c) + (a,) if hls(r, g, b)[2] > 0.12 else None)
        return fn
    geo_like("decompression_needle", "naloxone", tex_fn=pen_color((255, 120, 20)), note="ярко-оранжевая")
    geo_like("cyclosporine", "naloxone", tex_fn=pen_color((40, 96, 46)), note="тёмно-зелёная")

    # И29. Использованный пакет — модель пустого пакета крови.
    geo_like("used_iv_bag", "empty_blood_bag", tex_fn=lambda im: recolor(im, lambda r, g, b, a:
             (min(255, r + 6), g, max(0, b - 10), a) if a < 255 else None), note="чуть мутнее",
             icon=False)   # прозрачный пакет в рендере — почти одна рамка; иконка — прежняя плоская

    # И17. Шина — иконка по модели.
    icon_from("splint", preview.geo_quads(os.path.join(GEO_DIR, "splint.geo.json"), os.path.join(ASSETS, "textures", "geo", "splint.png")))

    # И23. Нашатырь — банка арахисовой пасты sa_combat без этикетки (закрашена под банку).
    def no_label(im):
        # Бока банки — x 0–11, y 0–19 текстуры 32 px; всё не коричневое там — этикетка.
        px = im.load()
        brown = hue_range(0.0, 0.12, 0.35)
        jar = [px[x, y] for y in range(20) for x in range(12) if px[x, y][3] and brown(*px[x, y][:3])]
        return recolor(im, lambda x, y, r, g, b, a: jar[(x * 7 + y * 13) % len(jar)]
                       if x < 12 and y < 20 and not brown(r, g, b) else None)
    json_copy("ammonia", "sa_combat:item/peanut_butter", tex_fn=no_label, note="этикетка закрашена")
    icon_from_json("ammonia")

    # И39. Сосудистый шов — нить survivorsarsenal. И40. Дрель — гвоздезабивной пистолет Survival Instinct.
    # И43. Костная пила — Marbled's Melees. И44. Контейнер для органов — голубой холодильник Refurbished Furniture.
    json_copy("vascular_suture", "survivorsarsenal:item/polyester_thread")
    json_copy("surgical_drill", "survival_instinct:item/nailgun")
    json_copy("bone_saw", "marbledsmelees:item/bone_saw")
    json_copy("organ_container", "refurbished_furniture:item/light_blue_cooler")
    for it in ("vascular_suture", "surgical_drill", "bone_saw", "organ_container"):
        icon_from_json(it)

    # И41. Набор для остеосинтеза — коробка винтов sa_core, голубая, с наклейкой и пластиной.
    teal = hue_range(0.42, 0.58, 0.15)
    extra = [box([4.5, 2.6, 11.95], [11.5, 4.6, 12.1], uv=(0.2, 0.2, 1.8, 1.8), tex="#lbl", faces="s"),   # белая наклейка спереди
             box([7.3, 2.9, 12.12], [8.7, 4.3, 12.2], uv=(2.2, 0.2, 3.8, 1.8), tex="#lbl", faces="s")]   # синий крест на ней
    m = json_copy("osteosynthesis_kit", "sa_core:item/box_of_screws",
                  tex_fn=lambda im: recolor(im, to_hue(teal, 0.6, sat=0.62, light=1.05)), extra_elements=extra, note="голубая, с наклейкой")
    lbl = swatch([(236, 240, 244), (60, 110, 200)])
    save(lbl, "textures/item/m/osteosynthesis_kit_label.png")
    m["textures"]["lbl"] = "rpmedicine:item/m/osteosynthesis_kit_label"
    write(model_path("osteosynthesis_kit"), m)
    icon_from_json("osteosynthesis_kit")

    # И27. Флаконы: бутылка ананасового сока sa_combat — жидкость и однотонная этикетка своего цвета.
    VIALS = {   # жидкость, этикетка
        "lidocaine": ((228, 236, 244, 210), (60, 120, 200)),
        "ketamine": ((232, 236, 240, 210), (150, 60, 170)),
        "propofol": ((246, 246, 240, 240), (230, 230, 225)),
        "norepinephrine": ((236, 228, 200, 220), (200, 90, 30)),
        "iv_glucose": ((236, 240, 246, 210), (230, 180, 40)),
        "iv_amino_acids": ((246, 236, 190, 220), (70, 150, 80)),
        "iv_lipids": ((250, 248, 236, 245), (225, 200, 120)),
        "iv_vitamins": ((246, 204, 70, 225), (210, 80, 60)),
    }
    # Текстура 32 px: этикетка — полоса y 2–8, x 0–13 (бока бутылки); жидкость (внутренний куб) — свой пиксель 20,20.
    for item, (liq, lab) in VIALS.items():
        def fn(im, liq=liq, lab=lab):
            def f(x, y, r, g, b, a):
                if 2 <= y <= 8 and x <= 13 and not hue_range(0.45, 0.7, 0.2)(r, g, b):
                    k = 0.8 if y in (2, 8) else 1.0 + (0.04 if (x + y) % 7 == 0 else 0)
                    return tuple(min(255, round(c * k)) for c in lab) + (255,)
                return None
            im = recolor(im, f)
            for yy in (20, 21):
                for xx in (20, 21):
                    im.putpixel((xx, yy), liq)
            return im
        m = json_copy(item, "sa_combat:item/pineapple_juice_bottle", tex_fn=fn, note="однотонная этикетка, своя жидкость")
        for face in m["elements"][1]["faces"].values():
            face["uv"] = [10, 10, 10.5, 10.5]
        write(model_path(item), m)
        icon_from_json(item)


def part_b():
    """И12–И16: инструменты из набора Surv 12 (LR). В сумке: 1 — травматические ножницы (prop_scissors_bandage),
    2 — медицинские (prop_scissors_damage), 3 — степлер (prop_stitcher); берём копии из правой руки (…2)."""
    src_tex = our_geo_tex("lr_surv12")

    def tex_with(extra):
        """Текстура набора + свои пиксели в свободном углу (60–63, 56–63): {(x, y): цвет}."""
        im = src_tex.copy()
        for (x, y), c in extra.items():
            for dx in (0, 1):
                for dy in (0, 1):
                    im.putpixel((x + dx, y + dy), c + (255,))
        return im

    def solid(x, y):
        return {f: {"uv": [x, y], "uv_size": [1, 1]} for f in ("north", "south", "east", "west", "up", "down")}

    def tool_geo(keep, drop_cubes=None, add=None):
        g = geo_subtree(our_geo("lr_surv12"), keep)
        for b in g["minecraft:geometry"][0]["bones"]:
            if drop_cubes and b["name"] in drop_cubes:
                b["cubes"] = [c for i, c in enumerate(b.get("cubes", [])) if i not in drop_cubes[b["name"]]]
            if add and b["name"] in add:
                b.setdefault("cubes", []).extend(add[b["name"]])
        return g

    steel = lambda im: recolor(im, lambda r, g, b, a: (lambda l: (round(120 + l * 110), round(126 + l * 110), round(134 + l * 110), a))(hls(r, g, b)[1])
                              if hls(r, g, b)[1] < 0.22 else None)

    # Медицинские ножницы (Спенсер).
    geo_item("scissors", tool_geo(["prop_scissors_damage2"]), src_tex, size=0.9, display=TOOL_FIT, icon_view=(180, 0))
    # Зажим — травматические ножницы, ручки стальные, как у зажима.
    geo_item("hemostat", tool_geo(["prop_scissors_bandage2"]), steel(src_tex), size=0.9, display=TOOL_FIT, icon_view=(180, 0))
    # Пинцет — степлер почти без изменений.
    geo_item("surgical_tweezers", tool_geo(["prop_stitcher2"]), src_tex, size=0.75, display=TOOL_FIT, icon_view=(180, 0))
    # Скальпель — половина медицинских ножниц: кольцо снято, на хвостовик надета тёмная ручка, лезвие — сталь.
    # В кости scissors_damage_small2: куб 0 — стержень (y −0,2…3), 1–2 — кончик лезвия (y 3…4), 3–10 — кольцо (y −1…0).
    scal = tool_geo(["scissors_damage_small2"], drop_cubes={"scissors_damage_small2": set(range(3, 11))},
                    add={"scissors_damage_small2": [
                        {"origin": [-1.82, -1.3, 0.3], "size": [0.76, 2.9, 0.22], "uv": solid(60, 56)},    # ручка
                        {"origin": [-1.74, 1.6, 0.31], "size": [0.6, 0.16, 0.2], "uv": solid(62, 56)},     # ободок у лезвия
                    ]})
    geo_item("scalpel", scal, tex_with({(60, 56): (78, 120, 176), (62, 56): (200, 206, 214)}), size=0.85, display=TOOL_FIT, icon_view=(180, 0))
    # Ретрактор — другая половина: кольцо-ручка остаётся, на конце лезвия (y ≈ 4) — загнутый крючок.
    ret = tool_geo(["scissors_damage_big2"], add={"scissors_damage_big2": [
        {"origin": [-2.45, 3.85, 0.24], "size": [0.95, 0.22, 0.16], "uv": solid(62, 58)},
        {"origin": [-2.45, 3.45, 0.24], "size": [0.2, 0.62, 0.16], "uv": solid(62, 58)},
    ]})
    geo_item("retractor", ret, tex_with({(62, 58): (176, 184, 194)}), size=0.9, display=TOOL_FIT, icon_view=(180, 0))
    for it in ("scissors", "hemostat", "surgical_tweezers", "scalpel", "retractor"):
        credits.append((f"rpgeo/{it}.geo.json", "lrtactical", "geo_models/consumable/surv12_geo.json", "инструмент из набора"))


# Инструмент из набора в руке: держат за ручку, остриём вперёд.
TOOL_FIT = dict(FIT_DISPLAY, **{
    "thirdperson_righthand": {"rotation": [0, 90, -35], "translation": [0, 2.5, 0.5], "scale": [0.7, 0.7, 0.7]},
    "thirdperson_lefthand": {"rotation": [0, -90, 35], "translation": [0, 2.5, 0.5], "scale": [0.7, 0.7, 0.7]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
})


def arc_tube(kit, cx, cy, R, a0, a1, w, z0, z1, color, step=22.5):
    """Изогнутая трубка из прямых кусков: центр дуги (cx, cy), радиус R, углы a0→a1 (градусы, шаг 22,5°)."""
    n = max(1, round(abs(a1 - a0) / step))
    L = 2 * math.pi * R * step / 360 * 1.12
    for i in range(n + 1):
        phi = a0 + (a1 - a0) * i / n
        px, py = cx + R * math.cos(math.radians(phi)), cy + R * math.sin(math.radians(phi))
        t = (phi + 90) % 180                      # направление касательной (0–180)
        if t <= 45 or t > 135:
            ang = t if t <= 45 else t - 180
            frm, to = [px - L / 2, py - w / 2, z0], [px + L / 2, py + w / 2, z1]
        else:
            ang = t - 90
            frm, to = [px - w / 2, py - L / 2, z0], [px + w / 2, py + L / 2, z1]
        kit.box(frm, to, color, rot=((px, py, (z0 + z1) / 2), "z", ang) if abs(ang) > 1e-6 else None)


EXTR = dict(HELD, gui={"rotation": [12, -24, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]})


def icon(name):
    """Исходная плоская иконка: при первом запуске копируется в textures/item/flat/ (дальше иконку
    предмета заменяет рендер модели, а объём строится всегда по исходной)."""
    flat_path = os.path.join(ASSETS, "textures", "item", "flat", f"{name}.png")
    if not os.path.exists(flat_path):
        os.makedirs(os.path.dirname(flat_path), exist_ok=True)
        Image.open(os.path.join(ASSETS, "textures", "item", f"{name}.png")).save(flat_path)
    return Image.open(flat_path).convert("RGBA")


def part_c():
    # ---------------- объём по плоским иконкам (лицо и спина — та же картинка)
    # И6. Гемостатик — пакетик: толщина 3 px, края тоньше.
    extrude("hemostatic_gauze", icon("hemostatic_gauze"), depth_fn=edge_depth(icon("hemostatic_gauze"), [2, 3, 3]), display=EXTR)
    # И7. Турникет и эсмарх — скрутки: в середине толще.
    extrude("tourniquet", icon("tourniquet"), depth_fn=edge_depth(icon("tourniquet"), [2, 3, 4, 4]), display=EXTR)
    extrude("esmarch", icon("esmarch"), depth_fn=edge_depth(icon("esmarch"), [3, 5, 6, 7, 7]), display=EXTR)
    # И8. Окклюзионная повязка — тонкая, в 1,5 раза меньше.
    extrude("occlusive_dressing", icon("occlusive_dressing"), depth=1.5, display=EXTR, scale=1 / 1.5)
    # И20. Ларингоскоп: рукоять толще клинка.
    lar = icon("laryngoscope")
    extrude("laryngoscope", lar, depth_fn=lambda x, y, r, g, b: 3 if (y >= 7 and x <= 5) else 1.5, display=TOOL)
    # И22. Дефибриллятор: корпус толстый со скруглёнными краями, кабель и электрод тоньше.
    de = icon("defibrillator")
    body = edge_depth(de, [4, 6, 7, 8])
    extrude("defibrillator", de, depth_fn=lambda x, y, r, g, b: body(x, y, r, g, b) if x < 17 else 3, display=EXTR)
    # И35. Гемоанализатор — по Mekanism QIO dashboard: экран бирюзовый -> красный «анализ крови», объём и кнопки.
    q = Image.open(src_file("mekanism", "textures/item/portable_qio_dashboard.png")).convert("RGBA")
    q = recolor(q, to_hue(hue_range(0.35, 0.5, 0.3), 0.98, sat=0.65, light=0.9))
    credit("textures/item/m/hemoanalyzer.png", "mekanism", "textures/item/portable_qio_dashboard.png", "экран перекрашен, объём")
    extrude("hemoanalyzer", q, depth_fn=edge_depth(q, [2, 3, 4]), display=EXTR)
    # И36. Портативный сканер — по Mekanism seismic reader: на экране зелёная кардиограмма.
    sr = Image.open(src_file("mekanism", "textures/item/seismic_reader.png")).convert("RGBA")
    pts = [(5, 9), (6, 9), (7, 9), (7, 8), (8, 6), (8, 7), (9, 10), (9, 11), (10, 9), (11, 9)]
    for x, y in pts:
        sr.putpixel((x, y), (90, 230, 120, 255))
    credit("textures/item/m/portable_scanner.png", "mekanism", "textures/item/seismic_reader.png", "кардиограмма на экране, объём")
    extrude("portable_scanner", sr, depth_fn=edge_depth(sr, [2, 3, 4]), display=EXTR)
    for it in ("hemostatic_gauze", "tourniquet", "esmarch", "occlusive_dressing", "laryngoscope", "defibrillator", "hemoanalyzer", "portable_scanner"):
        icon_from_json(it, yaw=-160, pitch=15)

    # ---------------- свои модели (перед — к +z, как у плоских предметов)
    W = (236, 238, 242)
    # И2. Подсумок: олива, клапан, пряжка, стропы MOLLE, нашивка с крестом.
    k = Kit("medical_pouch")
    k.box([3, 2, 5.5], [13, 11.5, 10.5], (98, 100, 64))
    k.box([2.8, 8.6, 5.3], [13.2, 12.2, 10.8], (74, 76, 48))
    for y in (3.2, 5.4):
        k.box([3, y, 10.5], [13, y + 0.8, 10.7], (150, 138, 96))
    k.box([6.6, 6.8, 10.8], [9.4, 9.2, 11.0], (74, 76, 48))
    k.box([7.2, 7.2, 11.0], [8.8, 8.4, 11.2], (30, 30, 32))
    k.box([5.9, 9.4, 10.8], [10.1, 11.8, 10.95], W)
    k.box([7.6, 9.7, 10.95], [8.4, 11.5, 11.05], (190, 40, 40))
    k.box([6.6, 10.25, 10.95], [9.4, 10.95, 11.05], (190, 40, 40))
    k.done(scale=1.1)

    # И19. Воздуховод Гведела: жёлтая изогнутая трубка, белый прикусной блок и фланец.
    k = Kit("airway")
    k.box([4.2, 12.4, 6.6], [11.8, 13.4, 9.4], W)                 # фланец
    k.box([6.4, 9.6, 7.0], [9.6, 12.4, 9.0], (240, 240, 236))     # прикусной блок
    k.box([7.2, 10.0, 8.95], [8.8, 12.0, 9.05], (60, 60, 60), faces="s")  # просвет
    arc_tube(k, 12.0, 9.6, 4.0, 180, 270, 2.4, 7.1, 8.9, (238, 196, 60))
    arc_tube(k, 12.0, 9.6, 4.0, 270, 315, 2.2, 7.15, 8.85, (238, 196, 60))
    k.done(scale=1.2)

    # И21. Эндотрахеальная трубка: прозрачная дуга, голубая манжета у конца, коннектор 15 мм, контрольный баллончик.
    k = Kit("endotracheal_tube")
    clear = (214, 228, 236, 210)
    arc_tube(k, 15.5, 1.0, 13.0, 112.5, 180, 1.0, 7.5, 8.5, clear)
    k.box([2.2, 2.4, 7.2], [3.8, 4.6, 8.8], (120, 180, 230, 190))   # манжета (у дистального конца)
    k.box([10.4, 12.6, 7.3], [12.4, 14.6, 8.7], W, rot=((11.4, 13.6, 8), "z", -22.5))                   # коннектор
    k.box([10.9, 14.4, 7.6], [11.9, 15.4, 8.4], (70, 130, 210), rot=((11.4, 13.6, 8), "z", -22.5))     # голубой край
    k.box([4.0, 6.0, 8.4], [4.3, 9.5, 8.7], clear)                  # линия к баллончику
    k.box([3.6, 9.5, 8.2], [4.7, 11.0, 9.0], (120, 180, 230, 200))  # контрольный баллончик
    k.done(scale=1.2)

    # И24. Блистер обезболивающих: фольга, 2×4 таблетки под пузырьками.
    k = Kit("painkillers")
    k.box([3, 1.5, 7.6], [13, 14.5, 8.2], (206, 210, 216), colors={"north": (176, 60, 60)})
    for col in range(2):
        for row in range(4):
            x0, y0 = 4.0 + col * 4.4, 2.4 + row * 3.0
            k.box([x0, y0, 8.2], [x0 + 3.6, y0 + 2.2, 8.6], (232, 238, 244, 230))
            k.box([x0 + 0.5, y0 + 0.4, 8.6], [x0 + 3.1, y0 + 1.8, 9.2], (246, 246, 246) if row < 3 else (236, 120, 110))
    k.done(display=EXTR)

    # И28. Венозный катетер: зелёные крылья и порт, прозрачная канюля, игла, белая заглушка; лежит по диагонали.
    k = Kit("iv_catheter")
    R = ((8, 8, 8), "z", 45)
    k.box([1.0, 7.3, 7.3], [2.6, 8.7, 8.7], W, rot=R)
    k.box([2.6, 7.0, 7.0], [5.2, 9.0, 9.0], (230, 236, 240, 200), rot=R)
    k.box([4.6, 5.6, 7.6], [7.0, 10.4, 8.4], (70, 170, 80), rot=R)
    k.box([5.0, 9.0, 7.4], [6.4, 10.6, 8.6], (60, 150, 70), rot=R)
    k.box([7.0, 7.6, 7.6], [12.0, 8.4, 8.4], (226, 232, 238, 200), rot=R)
    k.box([12.0, 7.8, 7.8], [14.6, 8.2, 8.2], (196, 200, 208), rot=R)
    k.done(display=TOOL)

    # И31. Ланцет: голубой корпус с рёбрами, отламываемый колпачок, кончик иглы.
    k = Kit("lancet")
    k.box([6.6, 3, 7], [9.4, 10, 9], (120, 170, 220))
    for y in (4.0, 5.2, 6.4):
        k.box([6.5, y, 6.9], [9.5, y + 0.4, 9.1], (96, 146, 200))
    k.box([7.6, 10, 7.6], [8.4, 10.6, 8.4], (120, 170, 220))
    k.box([6.9, 10.6, 7.3], [9.1, 13.4, 8.7], (120, 170, 220))
    k.box([7.4, 13.4, 7.7], [8.6, 14.0, 8.3], (96, 146, 200))
    k.box([7.85, 1.6, 7.85], [8.15, 3.0, 8.15], (200, 204, 212))
    k.done(scale=1.3)

    # И32. Пульсоксиметр: прищепка на палец — синий верх, серый низ, резиновые вкладыши, экран с цифрами.
    k = Kit("pulse_oximeter")
    k.box([4, 4, 5], [12, 6.6, 11], (70, 74, 82))
    k.box([4, 7.4, 5], [12, 10, 11], (60, 110, 180))
    k.box([4.6, 6.6, 5.6], [11.4, 7.4, 10.4], (40, 40, 44))          # щель под палец
    k.box([4, 5.8, 10.4], [12, 8.2, 11.2], (50, 54, 60))             # шарнир сзади
    k.box([5.4, 10, 6.2], [10.6, 10.2, 9.6], (24, 24, 28))           # экран
    for x0 in (5.9, 7.5, 9.1):
        k.box([x0, 10.2, 7.2], [x0 + 0.9, 10.25, 8.6], (240, 70, 60))
    k.done(scale=1.3)

    # И33. Тонометр: манометр с циферблатом, сложенная манжета, груша.
    k = Kit("tonometer")
    k.box([3, 1, 6], [13, 6, 10], (40, 52, 92))                      # манжета
    k.box([3, 2.6, 10], [13, 3.4, 10.1], (90, 104, 150))
    ring(k, 7.5, 10.0, 7.4, 9.4, 3.6, 0.7, (196, 200, 208))         # обод
    disc(k, 7.5, 10.0, 7.5, 9.25, 3.0, (244, 244, 240))             # циферблат
    k.box([7.35, 10.0, 9.25], [7.65, 12.3, 9.35], (30, 30, 30), rot=((7.5, 10.0, 9.3), "z", -45))  # стрелка
    k.box([11.3, 5.6, 7.2], [14.0, 8.8, 8.8], (30, 30, 32))           # груша
    k.box([12.2, 8.8, 7.6], [13.1, 10.0, 8.4], (190, 194, 200))       # клапан
    k.done(scale=1.1)

    # И34. Термометр электронный: белый корпус, ЖК-окошко, кнопка, металлический наконечник.
    k = Kit("thermometer")
    k.box([7.2, 1.2, 7.4], [8.8, 12.6, 8.6], W)
    k.box([7.1, 0.6, 7.3], [8.9, 1.6, 8.7], (70, 130, 210))
    k.box([7.35, 7.8, 8.6], [8.65, 11.4, 8.66], (150, 176, 150))
    k.box([7.6, 4.6, 8.6], [8.4, 5.6, 8.85], (70, 130, 210))
    k.box([7.6, 12.6, 7.7], [8.4, 15.2, 8.3], (196, 200, 208))
    k.done(display=TOOL)

    # И34. Глюкометр: корпус, экран с цифрами, две кнопки, тест-полоска с каплей.
    k = Kit("glucometer")
    k.box([4.5, 2, 7], [11.5, 12, 9.5], W)
    k.box([5.5, 6.5, 9.5], [10.5, 11, 9.6], (150, 172, 140))
    for x0 in (6.2, 8.0):
        k.box([x0, 7.6, 9.6], [x0 + 1.2, 9.8, 9.65], (40, 50, 40))
    k.box([6, 3.5, 9.5], [7.5, 4.8, 9.8], (70, 130, 210))
    k.box([8.5, 3.5, 9.5], [10, 4.8, 9.8], (150, 154, 160))
    k.box([7.3, 12, 8.0], [8.7, 15, 8.3], (250, 250, 250))
    k.box([7.6, 14.2, 8.3], [8.4, 15, 8.4], (190, 30, 40))
    k.done(scale=1.2)

    # И42. Плевральный дренаж: клапан Гимлиха (голубой корпус с белой мембраной), коннекторы, прозрачная трубка.
    k = Kit("chest_drain")
    k.box([5.5, 3.5, 6.8], [10.5, 9.5, 9.2], (90, 150, 210, 230))
    k.box([7.2, 4.5, 9.2], [8.8, 8.5, 9.3], W)
    k.box([7, 9.5, 7.2], [9, 11, 8.8], W)
    k.box([7, 2, 7.2], [9, 3.5, 8.8], W)
    arc_tube(k, 11.0, 11.0, 3.0, 180, 90, 1.2, 7.4, 8.6, (214, 228, 236, 210))
    k.box([10.4, 14.0, 7.4], [14.5, 15.2, 8.6], (214, 228, 236, 210))
    k.done(scale=1.2)


def part_d():
    # И30. Пробирки — пробирка с раствором Health & Disease, у всех трёх одна форма; крышка сиреневая (как у пробирок
    # для анализа крови). Жидкость — область развёртки x 0–15, y 36–55.
    base = Image.open(src_file("health_and_disease", "textures/item/oral.png")).convert("RGBA")
    disp = json.load(open(src_file("health_and_disease", "models/displaysettings/oral.item.json"), encoding="utf-8"))["base"]["display"]
    disp = dict(disp, ground={"translation": [0, -3, 0], "scale": [0.5, 0.5, 0.5]})

    sc = base.size[0] // 64        # текстура H&D крупнее развёртки модели (128 при 64)

    def tube(kind):
        def f(x, y, r, g, b, a):
            if x < 16 * sc and 36 * sc <= y < 56 * sc:
                if kind == "empty":
                    return (0, 0, 0, 0)
                if kind == "blood":
                    k = 0.85 + 0.3 * hls(r, g, b)[1]
                    return (round(150 * k), round(18 * k), round(28 * k), 245)
                # Грязная: внизу бурый осадок, выше пусто.
                return (118, 72, 52, 230) if y >= 51 * sc else (0, 0, 0, 0)
            h, l, s = hls(r, g, b)
            if s < 0.08 and l < 0.4:                      # крышка
                return rgb(0.76, 0.35 + l * 0.6, 0.38) + (a,)
            if kind == "dirty" and a < 255:               # мутное стекло
                return (196, 196, 182, a)
            return None
        return recolor(base, f)

    for item, kind in (("test_tube", "empty"), ("blood_sample", "blood"), ("dirty_test_tube", "dirty")):
        geo = json.load(open(src_file("health_and_disease", "geo/oral.geo.json"), encoding="utf-8"))
        geo_item(item, geo, tube(kind), fit=False, display=disp)
        credit(f"rpgeo/{item}.geo.json", "health_and_disease", "geo/oral.geo.json")
        credit(f"textures/geo/{item}.png", "health_and_disease", "textures/item/oral.png", "крышка и содержимое перекрашены")

    # И37. Перчатки — иконка «воровских перчаток» Self Expression, голубые (нитрил).
    gl = Image.open(src_file("selfexpression", "textures/item/pierchatki_vora.png")).convert("RGBA")
    gl = recolor(gl, lambda r, g, b, a: rgb(0.57, 0.38 + hls(r, g, b)[1] * 1.6, 0.62) + (a,))
    save(gl, "textures/item/surgical_gloves.png")
    credit("textures/item/surgical_gloves.png", "selfexpression", "textures/item/pierchatki_vora.png", "голубые")
    write(model_path("surgical_gloves"), {"parent": "minecraft:item/generated", "textures": {"layer0": "rpmedicine:item/surgical_gloves"}})
    # Использованные перчатки и отсыревшая маска — свои иконки (предикат rpmedicine:dirty, замечание И38).
    dirty_gl = recolor(gl, lambda x, y, r, g, b, a: (128, 52, 44, a) if (x * 5 + y * 3) % 7 == 0 else
                       tuple(round(c * 0.8 + 30) for c in (r, g, b)) + (a,))
    save(dirty_gl, "textures/item/surgical_gloves_dirty.png")
    write(model_path("surgical_gloves_dirty"), {"parent": "minecraft:item/generated", "textures": {"layer0": "rpmedicine:item/surgical_gloves_dirty"}})
    m = json.load(open(model_path("surgical_gloves"), encoding="utf-8"))
    m["overrides"] = [{"predicate": {"rpmedicine:dirty": 1}, "model": "rpmedicine:item/surgical_gloves_dirty"}]
    write(model_path("surgical_gloves"), m)
    mask = Image.open(os.path.join(ASSETS, "textures", "item", "surgical_mask.png")).convert("RGBA")
    mask = recolor(mask, lambda r, g, b, a: (lambda l: (round(150 + 70 * l), round(146 + 66 * l), round(120 + 50 * l), a))(hls(r, g, b)[1]))
    save(mask, "textures/item/surgical_mask_dirty.png")
    mm = json.load(open(model_path("surgical_mask"), encoding="utf-8"))
    dirty_mask = copy.deepcopy(mm)
    dirty_mask["base"] = {"parent": "minecraft:item/generated", "textures": {"layer0": "rpmedicine:item/surgical_mask_dirty"}}
    dirty_mask.pop("overrides", None)
    write(model_path("surgical_mask_dirty"), dirty_mask)
    mm["overrides"] = [{"predicate": {"rpmedicine:dirty": 1}, "model": "rpmedicine:item/surgical_mask_dirty"}]
    write(model_path("surgical_mask"), mm)
    # Слот Curios «руки» у игрока и перчатки в нём.
    write(os.path.join(ROOT, "data", "rpmedicine", "curios", "entities", "rpmedicine.json"), {"entities": ["player"], "slots": ["hands"]})
    write(os.path.join(ROOT, "data", "curios", "tags", "items", "hands.json"), {"replace": False, "values": ["rpmedicine:surgical_gloves"]})
    # Текстура перчаток на руках (Curios, слот «руки», замечание Ф31): своя кисть 4×5×4 (широкая рука) и 3×5×4 (тонкая),
    # развёртка как у куба Minecraft. Голубой латекс, валик манжеты сверху, кончики пальцев темнее.
    rnd = random.Random("gloves_worn")
    arm = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for v0, w in ((0, 4), (16, 3)):
        d, h = 4, 5
        for y in range(d + h):
            for x in range(2 * (d + w)):
                if y < d and not (d <= x < d + 2 * w):
                    continue
                k = 1 + (rnd.random() * 2 - 1) * 0.05
                if y == d:
                    k *= 1.18      # валик манжеты
                elif y == d + 1:
                    k *= 0.86
                elif y == d + h - 1:
                    k *= 0.9       # кончики пальцев
                if y < d and x >= d + w:
                    k *= 0.9       # низ (ладонь к земле)
                arm.putpixel((x, v0 + y), (round(min(255, 132 * k)), round(min(255, 184 * k)), round(min(255, 214 * k)), 255))
    save(arm, "textures/models/surgical_gloves_worn.png")


# ================================================================== замечания 10.10: свои модели по сетке Minecraft
import pixel_kit as pk  # noqa: E402

M = pk.Material
STEEL = M((196, 202, 210), 0.05, 0.72, 1.1)
CHROME = M((214, 218, 226), 0.04, 0.68, 1.12)
WHITE = M((232, 234, 238), 0.03, 0.8, 1.05)
BLACK = M((40, 40, 44), 0.06, 0.8, 1.15)
CLEAR = M((206, 222, 232), 0.03, 0.85, 1.05, alpha=190)
FONT = {  # 3×5 цифры для экранов
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"], "2": ["###", "..#", "###", "#..", "###"],
    "3": ["###", "..#", "###", "..#", "###"], "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."], "8": ["###", "#.#", "###", "#.#", "###"],
    "9": ["###", "#.#", "###", "..#", "###"], ".": ["...", "...", "...", "...", ".#."],
}


def digits(text):
    rows = ["", "", "", "", ""]
    for i, ch in enumerate(text):
        g = FONT[ch]
        for r in range(5):
            rows[r] += ("." if i else "") + g[r]
    return rows


def px_item(kit, display, icon_view=(-150, 25), particle=None):
    img, els = kit.build()
    save(img, f"textures/item/m/{kit.name}.png")
    tex = f"rpmedicine:item/m/{kit.name}"
    write(model_path(kit.name), {"textures": {"t": tex, "particle": particle or tex}, "elements": els, "display": copy.deepcopy(display)})
    drop_geo(kit.name)
    built.append(f"rpmedicine:item/{kit.name}")
    icon_from_json(kit.name, yaw=icon_view[0], pitch=icon_view[1])


def disp(base, scale=1.0, **over):
    d = copy.deepcopy(base)
    for k, v in over.items():
        d[k] = v
    if scale != 1.0:
        for v in d.values():
            v["scale"] = [round(c * scale, 4) for c in v.get("scale", [1, 1, 1])]
    return d


GUI_3D = {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]}
SMALL3 = dict(HELD, gui=GUI_3D, fixed={"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]})


def cross_patch(w=8, h=8):
    rows = []
    for y in range(h):
        r = ""
        for x in range(w):
            mid_x, mid_y = abs(x - (w - 1) / 2) < 1.1, abs(y - (h - 1) / 2) < 1.1
            inner = 1 <= x < w - 1 and 1 <= y < h - 1
            r += "r" if inner and (mid_x or mid_y) else ("w" if inner else "k")
        rows.append(r)
    return pk.pattern(rows, {"r": (200, 36, 36), "w": (238, 238, 238), "k": (60, 52, 40)})


def part_e():
    # ---------------- И2/Ф2. Подсумок IFAK: койот, клапан с молнией, красная ручка-отрыв, нашивка, стропы MOLLE сзади.
    k = pk.Px("medical_pouch", density=2)
    coy = M((128, 110, 78), 0.07, 0.75, 1.06, speck=((112, 96, 66), 0.12))
    k.box([3, 2.5, 5.5], [13, 10.5, 10.5], coy)
    k.box([2.5, 10.5, 5], [13.5, 12, 11], M((110, 94, 66), 0.06, 0.72, 1.08),
          decals={"up": pk.pattern(["." * 22, "z" * 22, "." * 22], {"z": (44, 40, 34)})})
    k.box([4, 3.5, 10.5], [12, 9.5, 11.5], coy, decals={"south": cross_patch(16, 12)})
    k.box([7, 12, 7.5], [9, 13.5, 8.5], M((196, 40, 40), 0.06, 0.75, 1.1))          # красная ручка
    for y in (4, 7):
        k.box([2.5, y, 4.5], [13.5, y + 1, 5.5], M((94, 86, 60), 0.05, 0.75))       # стропы MOLLE
    k.box([13, 6, 7], [13.5, 8.5, 9], BLACK)                                         # пряжка
    k.box([2.5, 6, 7], [3, 8.5, 9], BLACK)
    # Поворот в инвентаре — как у аптечки (Ф2).
    px_item(k, disp(SMALL3, 1.0, gui={"rotation": [30, 225, 0], "translation": [0, 0.5, 0], "scale": [0.95, 0.95, 0.95]}))

    # ---------------- Ф16. Дефибриллятор: коробчатый, экран только спереди, ручка сверху, сумка с электродами сбоку.
    k = pk.Px("defibrillator", density=1)
    yel = M((236, 190, 36), 0.05, 0.72, 1.06)
    aed_front = pk.both(
        pk.pattern(["kkkkkkkkkk", "kgggggggk.", "kg.g.ggg.k", "kgg.#.gggk", "kggggg#ggk", "kkkkkkkkkk"],
                   {"k": (30, 32, 36), "g": (24, 60, 40), "#": (90, 230, 120), ".": (24, 60, 40)}),
    )

    def aed_south(img, x0, y0, w, h):
        # Экран сверху, большая кнопка разряда и знак сердца внизу.
        pk.screen((24, 56, 36), (90, 230, 120), ["...#...", "..#.#..", "##...##"])(img, x0 + 2, y0 + 1, w - 4, 5)
        pk.pattern([".ooo.", "ooooo", "oo#oo", "ooooo", ".ooo."], {"o": (226, 92, 36), "#": (250, 240, 220)})(img, x0 + 1, y0 + 6, w // 2, 6)
        pk.pattern(["r.r", "rrr", ".r."], {"r": (200, 30, 30)})(img, x0 + w // 2, y0 + 6, w // 2, 6)
    k.box([2, 1, 5], [14, 12, 11], yel, decals={"south": aed_south})
    k.box([2, 0, 5], [14, 1, 11], M((60, 60, 64), 0.05, 0.8))                       # тёмное дно
    k.box([5, 12, 7.5], [6, 13.5, 8.5], BLACK)                                        # ручка
    k.box([10, 12, 7.5], [11, 13.5, 8.5], BLACK)
    k.box([5, 13.5, 7.5], [11, 14.5, 8.5], BLACK)
    k.box([14, 2, 6], [15.5, 10, 10], M((54, 56, 62), 0.06, 0.75),                    # сумка электродов, прилегает к корпусу
          decals={"east": pk.pattern(["wwww", "w..w", "w..w", "wwww"], {"w": (230, 230, 230)})})
    k.box([12.5, 9, 10.5], [14.5, 10, 11.5], BLACK)                                   # кабель к сумке
    px_item(k, disp(HELD, 1.0, gui=GUI_3D, fixed={"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]}))

    # ---------------- Ф13. Мешок Амбу: прозрачный голубой мешок, клапан, маска с чёрной манжетой, резервуар.
    k = pk.Px("ambu_bag", density=1)
    bag = M((130, 176, 220), 0.04, 0.82, 1.06, alpha=215)
    k.box([2, 5, 5], [10, 11, 11], bag, decals={"south": pk.stripes((110, 156, 204), 2, horizontal=False)})
    k.box([1, 6, 6], [2, 10, 10], bag)
    k.box([10, 6, 6], [11, 10, 10], bag)
    k.box([11, 7, 7], [13, 9, 9], M((70, 120, 200), 0.05, 0.75))                      # клапан
    k.box([13, 4.5, 4.5], [14, 11.5, 11.5], BLACK)                                    # манжета маски
    k.box([14, 5.5, 5.5], [15.5, 10.5, 10.5], CLEAR)                                  # купол маски
    k.box([0, 7, 7], [1, 9, 9], M((70, 160, 90), 0.05, 0.75))                         # вход резервуара
    px_item(k, disp(HELD, 0.9, gui=GUI_3D))

    # ---------------- Ф14. Ларингоскоп: рифлёная хромированная рукоять, клинок Макинтош вперёд, лампочка у кончика.
    k = pk.Px("laryngoscope", density=2)
    k.box([2, 2, 7], [4.5, 10, 9.5], CHROME, decals={f: pk.stripes((150, 156, 166), 2) for f in ("north", "south", "east", "west")})
    k.box([2, 10, 7], [4.5, 10.5, 9.5], STEEL)
    k.box([4.5, 9.5, 7.5], [13, 10.5, 9], STEEL)                                      # клинок
    k.box([4.5, 10.5, 7.75], [10, 11, 8.75], STEEL)                                   # ребро клинка
    k.box([13, 9, 7.5], [14.5, 10, 9], STEEL, rot=((13, 9.5, 8.25), "z", -22.5))       # загнутый кончик
    k.box([11.5, 9, 8], [12.5, 9.5, 8.5], M((250, 240, 170), 0.02, 1.0))             # лампочка
    px_item(k, TOOL)

    # ---------------- Ф29/Ф15. Термометр электронный — короткий: белый корпус, ЖК-окошко с «36.6», синяя кнопка, щуп.
    k = pk.Px("thermometer", density=2)

    def thermo_face(img, x0, y0, w, h):
        pk.screen((150, 172, 140), (30, 40, 30), digits("36"))(img, x0, y0 + 1, w, 7)
        pk.pattern(["bb", "bb"], {"b": (60, 120, 210)})(img, x0, y0 + 9, w, 3)
    k.box([6.5, 5, 7.25], [10, 12, 8.75], WHITE, decals={"south": thermo_face})
    k.box([6.5, 12, 7.25], [10, 13, 8.75], M((60, 120, 210), 0.05, 0.75))
    k.box([7.75, 2, 7.75], [8.75, 5, 8.25], STEEL)
    px_item(k, disp(TOOL, 0.8))

    # ---------------- Ф15. Глюкометр: корпус, экран «5.4», кнопки, тест-полоска с каплей крови.
    k = pk.Px("glucometer", density=2)

    def gluco_face(img, x0, y0, w, h):
        pk.screen((150, 172, 140), (30, 40, 30), digits("5.4"))(img, x0 + 1, y0 + 2, w - 2, 9)
        pk.pattern(["bb..gg", "bb..gg"], {"b": (60, 120, 210), "g": (150, 154, 160)})(img, x0, y0 + 13, w, 4)
    k.box([5, 2, 7], [11, 12, 9], M((226, 230, 236), 0.03, 0.78), decals={"south": gluco_face})
    k.box([7, 12, 7.75], [9, 12.5, 8.25], BLACK)                                      # щель
    k.box([7.5, 12.5, 7.85], [8.5, 15, 8.15], WHITE, decals={"south": pk.pattern(["r", "r"], {"r": (190, 30, 40)})})
    px_item(k, disp(SMALL3, 1.1))

    # ---------------- Ф38. Ланцет: маленький, фиолетовый корпус с белыми рёбрами, розовый колпачок, игла.
    k = pk.Px("lancet", density=2)
    k.box([7.5, 4, 7.5], [8.5, 8, 8.5], M((150, 100, 200), 0.05, 0.75), decals={f: pk.stripes((236, 236, 240), 3) for f in ("north", "south", "east", "west")})
    k.box([7.25, 8, 7.25], [8.75, 10, 8.75], M((236, 130, 170), 0.05, 0.75))
    k.box([7.75, 10, 7.75], [8.25, 10.5, 8.25], M((236, 130, 170), 0.05, 0.75))
    k.box([7.9, 3, 7.9], [8.1, 4, 8.1], STEEL, edge=False)
    px_item(k, disp(SMALL3, 0.8))

    # ---------------- Ф26. Пульсоксиметр: прищепка (синий верх, серый низ, резиновая щель), экран сверху — в 3 раза меньше.
    k = pk.Px("pulse_oximeter", density=1)

    def oxi_top(img, x0, y0, w, h):
        pk.screen((20, 22, 26), (240, 70, 60), digits("98"))(img, x0 + 1, y0 + 1, w - 2, h - 2)
    k.box([4, 3, 5], [12, 6, 11], M((70, 74, 82), 0.05, 0.75))
    k.box([4, 7, 5], [12, 10, 11], M((60, 110, 180), 0.05, 0.75), decals={"up": oxi_top})
    k.box([4.5, 6, 5.5], [11.5, 7, 10.5], M((36, 36, 40), 0.05, 0.85))               # резиновая щель под палец
    px_item(k, disp(SMALL3, 0.33))

    # ---------------- Ф27. Тонометр: циферблат со стрелкой, хромированный обод, свёрнутая манжета, груша.
    k = pk.Px("tonometer", density=2)

    def dial(img, x0, y0, w, h):
        cx, cy, r = x0 + (w - 1) / 2, y0 + (h - 1) / 2, min(w, h) / 2 - 0.5
        for y in range(h):
            for x in range(w):
                d = math.hypot(x0 + x - cx, y0 + y - cy)
                if d > r:
                    img.putpixel((x0 + x, y0 + y), (0, 0, 0, 0))
                elif d > r - 1.2:
                    img.putpixel((x0 + x, y0 + y), (190, 196, 206, 255))
                else:
                    img.putpixel((x0 + x, y0 + y), (244, 244, 238, 255))
        for a in range(0, 300, 30):   # деления
            t = math.radians(a - 240)
            img.putpixel((int(round(cx + math.cos(t) * (r - 2))), int(round(cy + math.sin(t) * (r - 2)))), (30, 30, 30, 255))
        for i in range(int(r - 2)):    # стрелка
            t = math.radians(-60)
            img.putpixel((int(round(cx + math.cos(t) * i)), int(round(cy + math.sin(t) * i))), (200, 30, 30, 255))
    k.box([4, 6, 8.5], [11, 13, 9.5], CHROME, decals={"south": dial})
    k.box([5, 7, 7.5], [10, 12, 8.5], CHROME)
    k.box([3, 1, 6], [13, 5.5, 10], M((40, 52, 92), 0.06, 0.75),
          decals={"south": pk.pattern(["ww" * 8, "." * 16, "vvvvvvvvvvvvvvvv"], {"w": (230, 230, 230), "v": (90, 104, 150)})})
    k.box([11.5, 5.5, 7.25], [14, 9, 9.25], BLACK, decals={f: pk.stripes((24, 24, 28), 2) for f in ("north", "south", "east", "west")})
    k.box([12.25, 9, 7.75], [13.25, 10, 8.75], CHROME)
    px_item(k, disp(SMALL3, 1.0))

    # ---------------- Ф15. Плевральный дренаж: клапан Гимлиха (голубой корпус, белая лепестковая мембрана, стрелка), коннекторы, трубка.
    k = pk.Px("chest_drain", density=2)
    k.box([6, 4, 7], [10, 10, 9], M((110, 170, 230), 0.04, 0.8, alpha=215),
          decals={"south": pk.pattern(["..ww..", "..ww..", ".wwww.", "..ww..", "..ww..", "..ww..", "..ww..", "..ww..", "..ww..", ".a..a.", "..aa.."],
                                      {"w": (240, 240, 240), "a": (30, 60, 140)})})
    k.box([7, 10, 7.5], [9, 11.5, 8.5], WHITE)
    k.box([7, 2.5, 7.5], [9, 4, 8.5], WHITE)
    k.box([7.5, 11.5, 7.75], [8.5, 14, 8.25], CLEAR)
    k.box([8.5, 13, 7.75], [12, 14, 8.25], CLEAR)
    k.box([11, 9, 7.75], [12, 13, 8.25], CLEAR)
    px_item(k, disp(SMALL3, 1.1))

    # ---------------- Ф15. Воздуховод Гведела: жёлтая изогнутая трубка, белый прикусной блок, фланец.
    k = pk.Px("airway", density=2)
    yel = M((236, 196, 64), 0.05, 0.75, 1.06)
    k.box([4.5, 12.5, 6.5], [11.5, 13.5, 9.5], WHITE)                                  # фланец
    k.box([6.5, 9.5, 7], [9.5, 12.5, 9], WHITE, decals={"up": pk.fill((40, 40, 40))})  # прикусной блок с просветом
    k.box([6.5, 6, 7], [9.5, 9.5, 9], yel)
    k.box([7.5, 4, 7], [10.5, 6.5, 9], yel, rot=((9, 5, 8), "z", 22.5))
    k.box([9, 2.5, 7], [12, 5, 9], yel, rot=((10.5, 3.5, 8), "z", 45))
    k.box([11, 2.5, 7], [13.5, 4.5, 9], yel)
    px_item(k, disp(SMALL3, 1.1))

    # ---------------- И21. Эндотрахеальная трубка: прозрачная с синей рентгеноконтрастной линией, манжета, коннектор, баллончик.
    k = pk.Px("endotracheal_tube", density=2)
    tube = M((214, 226, 234), 0.03, 0.85, 1.05, alpha=200)
    line = pk.stripes((60, 110, 200), 99, horizontal=False)

    def blue_line(img, x0, y0, w, h):
        for y in range(h):
            img.putpixel((x0 + w // 2, y0 + y), (60, 110, 200, 255))
    k.box([7.5, 2, 7.5], [8.5, 9, 8.5], tube, decals={"south": blue_line})
    k.box([8, 9, 7.5], [9, 12, 8.5], tube, rot=((8.5, 10.5, 8), "z", -22.5), decals={"south": blue_line})
    k.box([9.5, 11.5, 7.5], [10.5, 14, 8.5], tube, rot=((10, 12.75, 8), "z", -45), decals={"south": blue_line})
    k.box([7, 2.5, 7], [9, 5, 9], M((120, 180, 230), 0.04, 0.85, alpha=190))         # манжета
    k.box([10.5, 13, 7.25], [13, 14.5, 8.75], WHITE)                                   # коннектор 15 мм
    k.box([12.5, 13, 7.25], [13.5, 14.5, 8.75], M((70, 130, 210), 0.05, 0.75))
    k.box([6, 5, 8.25], [6.5, 9, 8.75], tube)                                          # линия к баллончику
    k.box([5.5, 9, 8], [7, 10.5, 9], M((120, 180, 230), 0.04, 0.85, alpha=200))
    px_item(k, disp(SMALL3, 1.1))

    # ---------------- Ф24. Венозный катетер: белая заглушка, прозрачная камера, зелёные крылья и порт, канюля, игла; по диагонали.
    k = pk.Px("iv_catheter", density=2)
    R = ((8, 8, 8), "z", 45)
    k.box([2, 7.5, 7.5], [3.5, 8.5, 8.5], WHITE, rot=R)
    k.box([3.5, 7.25, 7.25], [6, 8.75, 8.75], CLEAR, rot=R)
    k.box([5.5, 6, 7.75], [7.5, 10, 8.25], M((70, 170, 80), 0.05, 0.75), rot=R)
    k.box([6, 8.75, 7.5], [7, 9.75, 8.5], M((60, 150, 70), 0.05, 0.75), rot=R)
    k.box([7.5, 7.75, 7.75], [11.5, 8.25, 8.25], CLEAR, rot=R)
    k.box([11.5, 7.85, 7.85], [13.5, 8.15, 8.15], STEEL, rot=R, edge=False)
    px_item(k, disp(TOOL, 0.6))

    # ---------------- Ф33. Пинцет: две стальные бранши с насечкой, соединены сзади, кончики сходятся.
    k = pk.Px("surgical_tweezers", density=2)
    grip = pk.stripes((150, 156, 166), 2)
    k.box([6.5, 4, 7.75], [7.5, 12.5, 8.25], STEEL, decals={"south": grip, "north": grip})
    k.box([8.5, 4, 7.75], [9.5, 12.5, 8.25], STEEL, decals={"south": grip, "north": grip})
    k.box([6.5, 12.5, 7.75], [9.5, 14, 8.25], STEEL)
    k.box([7, 2, 7.75], [7.75, 4, 8.25], STEEL)
    k.box([8.25, 2, 7.75], [9, 4, 8.25], STEEL)
    px_item(k, TOOL)

    # ---------------- Ф15. Кислородный баллон (блок): зелёный восьмигранный баллон, белая полоса O₂, хромированный вентиль.
    k = pk.Px("oxygen_tank", density=1)
    green = M((46, 128, 74), 0.05, 0.75, 1.05)

    def band(img, x0, y0, w, h):
        for y in range(2, 4):
            for x in range(w):
                img.putpixel((x0 + x, y0 + y), (236, 240, 236, 255))
    k.box([5, 0, 5], [11, 1, 11], BLACK)
    k.box([5.5, 1, 4.5], [10.5, 13, 11.5], green, decals={f: band for f in ("north", "south", "east", "west")})
    k.box([4.5, 1, 5.5], [11.5, 13, 10.5], green, decals={f: band for f in ("north", "south", "east", "west")})
    k.box([6.5, 13, 6.5], [9.5, 14.5, 9.5], green)
    k.box([7, 14.5, 7], [9, 16, 9], CHROME)
    k.box([9, 15, 7.5], [11, 15.5, 8.5], CHROME)
    img, els = k.build()
    save(img, "textures/block/oxygen_tank.png")
    write(os.path.join(ASSETS, "models", "block", "oxygen_tank.json"),
          {"textures": {"t": "rpmedicine:block/oxygen_tank", "particle": "rpmedicine:block/oxygen_tank"}, "elements": els})


def strip_outline(icon):
    """Убрать чёрный контур иконки (тёмные пиксели на краю силуэта): у объёмной модели он торчит рамкой (Ф6, Ф7)."""
    im = icon.copy()
    px = im.load()
    w, h = im.size
    src = icon.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a < 128 or (r * 30 + g * 59 + b * 11) / 100 > 70:
                continue
            if any(not (0 <= x + dx < w and 0 <= y + dy < h) or src[x + dx, y + dy][3] < 128
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (0, 0, 0, 0)
    return im


def back_plain(item, color):
    """Спина объёмной иконки — однотонная (экран только спереди, Ф30): северные грани -> пиксель цвета color."""
    path = model_path(item)
    m = json.load(open(path, encoding="utf-8"))
    tpath = os.path.join(ASSETS, "textures", "item", "m", f"{item}.png")
    im = Image.open(tpath).convert("RGBA")
    w, h = im.size
    big = Image.new("RGBA", (w * 2, h * 2), (0, 0, 0, 0))
    big.paste(im, (0, 0))
    for y in range(h, h + 2):
        for x in range(w, w + 2):
            big.putpixel((x, y), tuple(color) + (255,))
    big.save(tpath)
    for el in m["elements"]:
        for f, fd in el["faces"].items():
            fd["uv"] = [round(c / 2, 4) for c in fd["uv"]]
            if f == "north":
                fd["uv"] = [8.1, 8.1, 8.4, 8.4]
    write(path, m)


def geo_box_to_faces(geo):
    """Box UV -> UV по граням с исходными размерами (перед растяжением модели: иначе развёртка съезжает, Ф9)."""
    for b in geo["minecraft:geometry"][0].get("bones", []):
        for c in b.get("cubes", []):
            uv = c.get("uv")
            if not isinstance(uv, list):
                continue
            u, v = uv
            w, h, d = c["size"]
            f = {"east": (u, v + d, d, h), "north": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h),
                 "south": (u + 2 * d + w, v + d, w, h), "up": (u + d, v, w, d), "down": (u + d + w, v + d, w, -d)}
            if c.get("mirror"):
                f["east"], f["west"] = f["west"], f["east"]
                f = {k: (x + ww, y, -ww, hh) for k, (x, y, ww, hh) in f.items()}
            c["uv"] = {k: {"uv": [x, y], "uv_size": [ww, hh]} for k, (x, y, ww, hh) in f.items()}
            c.pop("mirror", None)
    return geo


def cut_clip(anim, src, keep_until, then=None):
    """Новая анимация «use»: кадры src до keep_until, затем клип then (сдвинутый)."""
    a = anim["animations"]
    out = {"animation_length": keep_until, "bones": {}}
    for bone, ch in a[src].get("bones", {}).items():
        for chan, kf in ch.items():
            if not isinstance(kf, dict):
                continue
            keep = {k: v for k, v in kf.items() if float(k) <= keep_until + 1e-6}
            if keep:
                out["bones"].setdefault(bone, {})[chan] = keep
    if then and then in a:
        t0 = keep_until
        ln = float(a[then].get("animation_length", 0.3))
        for bone, ch in a[then].get("bones", {}).items():
            for chan, kf in ch.items():
                if not isinstance(kf, dict):
                    continue
                dst = out["bones"].setdefault(bone, {}).setdefault(chan, {})
                for k, v in kf.items():
                    dst[f"{t0 + float(k):.4f}"] = v
        out["animation_length"] = t0 + ln
    anim = copy.deepcopy(anim)
    anim["animations"]["use"] = out
    return anim


def part_f():
    # Ф6–Ф8. Объём по иконкам без чёрного контура; эсмарх ровнее; гемостатик меньше.
    hg = strip_outline(icon("hemostatic_gauze"))
    extrude("hemostatic_gauze", hg, depth_fn=edge_depth(hg, [2, 3, 3]), display=EXTR, scale=0.8)
    tq = strip_outline(icon("tourniquet"))
    extrude("tourniquet", tq, depth_fn=edge_depth(tq, [2, 3, 3]), display=EXTR)
    es = strip_outline(icon("esmarch"))
    extrude("esmarch", es, depth_fn=edge_depth(es, [3, 4, 4]), display=EXTR)
    oc = strip_outline(icon("occlusive_dressing"))
    extrude("occlusive_dressing", oc, depth=1.5, display=EXTR, scale=1 / 1.5)
    # Ф30. Гемоанализатор и сканер — экран только спереди.
    q = Image.open(src_file("mekanism", "textures/item/portable_qio_dashboard.png")).convert("RGBA")
    q = strip_outline(recolor(q, to_hue(hue_range(0.35, 0.5, 0.3), 0.98, sat=0.65, light=0.9)))
    extrude("hemoanalyzer", q, depth_fn=edge_depth(q, [2, 3, 4]), display=EXTR)
    back_plain("hemoanalyzer", (70, 72, 78))
    sr = Image.open(src_file("mekanism", "textures/item/seismic_reader.png")).convert("RGBA")
    for x, y in [(5, 9), (6, 9), (7, 9), (7, 8), (8, 6), (8, 7), (9, 10), (9, 11), (10, 9), (11, 9)]:
        sr.putpixel((x, y), (90, 230, 120, 255))
    sr = strip_outline(sr)
    extrude("portable_scanner", sr, depth_fn=edge_depth(sr, [2, 3, 4]), display=EXTR)
    back_plain("portable_scanner", (88, 90, 96))
    for it in ("hemostatic_gauze", "tourniquet", "esmarch", "occlusive_dressing", "hemoanalyzer", "portable_scanner"):
        icon_from_json(it, yaw=-160, pitch=15)

    # Ф3. Набор стабилизации — аптечка sa_combat, без анимаций. Ф5. Давящая повязка — самодельный бинт Survival Instinct.
    json_copy("stabilization_kit", "sa_combat:item/first_aid_kit")
    json_copy("pressure_dressing", "survival_instinct:item/homemade_bandage")
    icon_from_json("stabilization_kit")
    icon_from_json("pressure_dressing")

    # Ф18. Обезболивающее — блистер амоксициллина LR, вдвое меньше.
    geo_like("painkillers", "lr_amoxycillin", size=0.4, note="блистер LR, меньше")

    # Ф19–Ф20. Цефтриаксон — флакон, как лидокаин (бирюзовая этикетка); все флаконы вдвое меньше.
    juice_vial("ceftriaxone", (232, 238, 228, 210), (40, 150, 150))
    for it in ("lidocaine", "ketamine", "propofol", "norepinephrine", "iv_glucose", "iv_amino_acids", "iv_lipids", "iv_vitamins", "ceftriaxone"):
        m = json.load(open(model_path(it), encoding="utf-8"))
        for ctx_, v in m.get("display", {}).items():
            if ctx_ != "gui":
                v["scale"] = [round(c * 0.5, 4) for c in v.get("scale", [1, 1, 1])]
        write(model_path(it), m)

    # Ф17. Трамадол — полоса выше: строки 14–15 боков банки меняются местами со строками 10–11 (текстура H&D 64 px).
    t = our_geo_tex("tramadol")
    if t.getpixel((0, 14))[3] and hls(*t.getpixel((0, 14))[:3])[2] > 0.3:          # ещё не поднята
        out = t.copy()
        for x in range(32):
            for a_, b_ in ((10, 14), (11, 15)):
                out.putpixel((x, a_), t.getpixel((x, b_)))
                out.putpixel((x, b_), t.getpixel((x, a_)))
        save(out, "textures/geo/tramadol.png")
        icon_from("tramadol", preview.geo_quads(os.path.join(GEO_DIR, "tramadol.geo.json"), os.path.join(ASSETS, "textures", "geo", "tramadol.png")))

    # Ф9. Хлоргексидин: развёртка по граням до сжатия (текстуры не съезжают). Пересобрать флаконы.
    for item, (hue, sat, light), s_ in (("antiseptic", (0.55, 0.45, 0.78), 1.0), ("chlorhexidine_hands", (0.33, 0.45, 0.8), 1.0),
                                       ("chlorhexidine_skin", (0.93, 0.55, 0.78), 1.0), ("chlorhexidine_concentrate", (0.62, 0.7, 0.42), 1.15)):
        geo = geo_box_to_faces(our_geo("amoxicillin"))
        geo_scale(geo, s_, s_, s_ * 0.7)
        t = recolor(our_geo_tex("amoxicillin"), lambda r, g, b, a, hue=hue, sat=sat, light=light:
                    (rgb(hue, light, sat) + (a,)) if hue_range(0.45, 0.62, 0.25)(r, g, b) else None)
        e = json.load(open(os.path.join(GEO_DIR, "items.json"), encoding="utf-8")).get("rpmedicine:amoxicillin", {})
        geo_item(item, geo, t, fit=False, anim=our_anim("amoxicillin"), use=e.get("use"), display=our_display("amoxicillin"))
    # Ф11/Ф37. Набор для швов и AI-2: шприц из правой руки не торчит; у набора — только «открыть и убрать».
    geo = geo_box_to_faces(our_geo("lr_ai2"))
    geo_scale(geo, 1.5, 1, 1)
    gray = lambda im: recolor(im, palette_map(hue_range(0.05, 0.2, 0.2), (58, 60, 64), (196, 200, 206)))
    geo_item("suture_kit", geo, gray(our_geo_tex("lr_ai2")), size=0.8, anim=cut_clip(our_anim("lr_ai2"), "use", 1.25, "put_away"), use="use")
    index["rpmedicine:suture_kit"]["hide"] = ["lefthand", "righthand", "lefthand_pos", "righthand_pos",
                                              "injector", "injector_cap", "injector2", "injector_cap2", "injector_cap3"]
    index["rpmedicine:lr_ai2"]["hide_static"] = ["injector2", "injector_cap2", "injector_cap3"]

    # Ф21. Использованный шприц — кровь на кончике иглы со всех сторон.
    dirty = our_geo_tex("dirty_syringe")
    g = our_geo("dirty_syringe")
    tw = g["minecraft:geometry"][0]["description"].get("texture_width", 64)
    k_ = dirty.size[0] / tw
    for b in g["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            o, s_ = c["origin"], c["size"]
            if s_[1] >= 1.5 and s_[0] <= 0.6 and s_[2] <= 0.6:        # игла: тонкий куб
                uv = c["uv"]
                if isinstance(uv, list):
                    u, v = uv
                    x0, y0, x1, y1 = u, v, u + 2 * (s_[0] + s_[2]), v + s_[2] + s_[1]
                    for yy in range(int((y1 - min(1.5, s_[1])) * k_), int(y1 * k_)):
                        for xx in range(int(x0 * k_), int(x1 * k_) + 1):
                            if 0 <= xx < dirty.size[0] and 0 <= yy < dirty.size[1] and dirty.getpixel((xx, yy))[3]:
                                dirty.putpixel((xx, yy), (150, 24, 32, 255) if (xx + yy) % 2 else (120, 14, 22, 255))
    save(dirty, "textures/geo/dirty_syringe.png")

    # Ф25. Пакеты с кровью и без — без анимации «питья».
    for it in ("blood_bag", "saline", "empty_blood_bag", "used_iv_bag"):
        if f"rpmedicine:{it}" in index:
            index[f"rpmedicine:{it}"].pop("use", None)
            times.pop(f"rpmedicine:{it}", None)

    # Ф34. Остеосинтез — без мелких деталей вокруг коробки.
    m = json.load(open(model_path("osteosynthesis_kit"), encoding="utf-8"))
    big = max(m["elements"], key=lambda e: (e["to"][0] - e["from"][0]) * (e["to"][2] - e["from"][2]))
    bx0, bz0, bx1, bz1 = big["from"][0], big["from"][2], big["to"][0], big["to"][2]
    m["elements"] = [e for e in m["elements"] if e["from"][0] >= bx0 - 0.6 and e["to"][0] <= bx1 + 0.6
                     and e["from"][2] >= bz0 - 0.6 and e["to"][2] <= bz1 + 0.6]
    write(model_path("osteosynthesis_kit"), m)
    icon_from_json("osteosynthesis_kit")

    # Ф35. Пила — в слоте не вверх ногами. Ф36. Контейнер для органа — крупнее, ровно в руках.
    m = json.load(open(model_path("bone_saw"), encoding="utf-8"))
    g_ = m["display"].setdefault("gui", {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]})
    g_["rotation"] = [g_.get("rotation", [0, 0, 0])[0], g_.get("rotation", [0, 0, 0])[1], (g_.get("rotation", [0, 0, 0])[2] + 180) % 360]
    write(model_path("bone_saw"), m)
    m = json.load(open(model_path("organ_container"), encoding="utf-8"))
    m["display"]["thirdperson_righthand"] = {"rotation": [0, 0, 0], "translation": [0, 0, 3], "scale": [0.55, 0.55, 0.55]}
    m["display"]["thirdperson_lefthand"] = m["display"]["thirdperson_righthand"]
    m["display"]["firstperson_righthand"] = {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.7, 0.7, 0.7]}
    m["display"]["firstperson_lefthand"] = m["display"]["firstperson_righthand"]
    m["display"]["fixed"] = {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]}
    write(model_path("organ_container"), m)

    # Ф10. Мазь и вазелин от 3-го лица — как от 1-го (меньше).
    for it in ("antibiotic_ointment", "lr_vaseline", "stabilization_kit"):
        m = json.load(open(model_path(it), encoding="utf-8"))
        b = m.get("base", m)
        for side in ("thirdperson_righthand", "thirdperson_lefthand"):
            if side in b.get("display", {}):
                b["display"][side]["scale"] = [0.32, 0.32, 0.32]
        write(model_path(it), m)

    # Ф12/Ф32. Инструменты держат за ручку, остриём от себя; ножницы от 3-го лица меньше.
    for it in ("scissors", "scalpel", "hemostat", "retractor"):
        m = json.load(open(model_path(it), encoding="utf-8"))
        d = m["base"]["display"]
        for side, sgn in (("right", 1), ("left", -1)):
            fp = d[f"firstperson_{side}hand"]
            fp["rotation"] = [0, sgn * -90, sgn * -155]
            tp = d[f"thirdperson_{side}hand"]
            tp["rotation"] = [0, sgn * 90, sgn * 145]
            if it == "scissors":
                tp["scale"] = [0.55, 0.55, 0.55]
        write(model_path(it), m)


def juice_vial(item, liq, lab):
    """Флакон по бутылке sa_combat: однотонная этикетка lab, жидкость liq (как у лидокаина и прочих)."""
    def fn(im):
        def f(x, y, r, g, b, a):
            if 2 <= y <= 8 and x <= 13 and not hue_range(0.45, 0.7, 0.2)(r, g, b):
                k = 0.8 if y in (2, 8) else 1.0 + (0.04 if (x + y) % 7 == 0 else 0)
                return tuple(min(255, round(c * k)) for c in lab) + (255,)
            return None
        im = recolor(im, f)
        for yy in (20, 21):
            for xx in (20, 21):
                im.putpixel((xx, yy), liq)
        return im
    m = json_copy(item, "sa_combat:item/pineapple_juice_bottle", tex_fn=fn, note="однотонная этикетка, своя жидкость")
    for face in m["elements"][1]["faces"].values():
        face["uv"] = [10, 10, 10.5, 10.5]
    write(model_path(item), m)
    icon_from_json(item)


# ------------------------------------------------------------------ 3D в инвентаре
GUI_ROT = (30, 225, 0)   # как у блоков
# Модели LR, у которых «лицо» с другой стороны (Ф37: CMS и вазелин в слоте были повёрнуты спиной).
GUI_ROT_ITEM = {"lr_cms": (30, 45, 0), "lr_vaseline": (30, 45, 0), "antibiotic_ointment": (30, 45, 0)}


def _rot_xyz(rx, ry, rz):
    """Поворот display Minecraft: кватернион rotationXYZ (сначала Z, потом Y, потом X к точке)."""
    import numpy as np
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Y = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Z = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return X @ Y @ Z


def gui_display(points, rot=GUI_ROT, fill=14.5):
    """display.gui, чтобы модель (точки в пикселях блока 0–16) после поворота вписалась в слот 16×16 по центру."""
    import numpy as np
    P = np.array(points, dtype=float) - 8.0
    R = _rot_xyz(*rot)
    Q = P @ R.T
    lo, hi = Q.min(0), Q.max(0)
    ext = max(hi[0] - lo[0], hi[1] - lo[1], 1e-3)
    sc = fill / ext
    c = (lo + hi) / 2 * sc
    return {"rotation": list(rot), "translation": [round(-c[0], 3), round(-c[1], 3), 0], "scale": [round(sc, 4)] * 3}


def geo_points(item, entry):
    """Углы кубов модели client/geo в пикселях блока — так, как её ставит GeoItemRenderer."""
    q = preview.geo_quads(os.path.join(ASSETS, entry["geo"].split(":", 1)[1]), os.path.join(ASSETS, entry["texture"].split(":", 1)[1]),
                          hide=tuple(entry.get("hide", ())) + tuple(entry.get("hide_static", ())))
    pts = [p for quad in q for p in quad[0]]
    if not pts:
        return []
    if entry.get("fit"):
        import numpy as np
        P = np.array(pts)
        lo, hi = P.min(0), P.max(0)
        k = entry.get("size", 0.75) * 16 / max(hi - lo)
        mid = (lo + hi) / 2
        return [tuple((np.array(p) - mid) * k + 8) for p in pts]
    # Не вписанная: GeoItemRenderer ставит начало модели в (0,5; 0,51; 0,5) блока — по высоте это 8,16 px.
    return [(p[0], p[1] + 8.16, p[2]) for p in pts]


def gui_3d():
    """Все 3D-предметы client/geo: в инвентаре — сама модель (без плоской иконки), освещение как у блоков."""
    n = 0
    for key, entry in index.items():
        item = key.split(":", 1)[1]
        path = model_path(item)
        if not os.path.exists(path):
            continue
        m = json.load(open(path, encoding="utf-8"))
        base = m.get("base", {})
        if base.get("parent") != "builtin/entity":
            continue
        pts = geo_points(item, entry)
        if not pts:
            continue
        base.setdefault("display", {})["gui"] = gui_display(pts, rot=GUI_ROT_ITEM.get(item, GUI_ROT))
        m["base"] = base
        m["gui_light"] = "side"
        m["perspectives"] = {}          # загрузчик требует поле; плоской иконки больше нет
        write(path, m)
        n += 1
    # Испорченный пакет крови — тот же 3D-вид (порча — в подсказке).
    sp = model_path("blood_bag_spoiled")
    if os.path.exists(sp) and os.path.exists(model_path("blood_bag")):
        m = json.load(open(model_path("blood_bag"), encoding="utf-8"))
        m.pop("overrides", None)
        write(sp, m)
    print("item_models: 3D в инвентаре —", n)


# ------------------------------------------------------------------ правки из галереи
REVIEW = os.path.join(HERE, "..", "docs", "model_review.json")


def apply_review():
    """Вид, выставленный в галерее (tools/model_gallery -> docs/model_review.json), — поверх сгенерированного.
    Левая рука не пишется: игра берёт правую и сама отражает её."""
    if not os.path.exists(REVIEW):
        return
    data = json.load(open(REVIEW, encoding="utf-8")).get("items", {})
    n = 0
    for item, r in data.items():
        disp_over = r.get("display")
        path = model_path(item)
        if not disp_over or not os.path.exists(path):
            continue
        m = json.load(open(path, encoding="utf-8"))
        target = m["base"] if m.get("loader") == "forge:separate_transforms" and "base" in m else m
        if "display" not in target and "parent" in target and not target["parent"].startswith("builtin"):
            target["display"] = {}
        d = target.setdefault("display", {})
        for ctx_, v in disp_over.items():
            d[ctx_] = {"rotation": v["rotation"], "translation": v["translation"], "scale": v["scale"]}
            if ctx_.endswith("_righthand"):
                d.pop(ctx_.replace("right", "left"), None)
        write(path, m)
        n += 1
    print("item_models: правки вида из галереи —", n)


# ================================================================== запуск
def write_credits():
    rows, seen = [], set()
    for dst, ns, rel, how in credits:
        if (dst, rel) in seen:
            continue
        seen.add((dst, rel))
        if ns == "rpmedicine":
            src, author, lic = f"своя модель `{rel}`", "—", "—"
        else:
            _, name, author, lic = MODS[ns]
            src = f"{name}: `{rel}`"
        rows.append(f"| `{dst}` | {src}{' (' + how + ')' if how else ''} | {author} | {lic} |")
    text = open(CREDITS, encoding="utf-8").read()
    start, end = "<!-- item_models:begin -->", "<!-- item_models:end -->"
    block = (start + "\n## Модели предметов по замечаниям 09.10 (`scripts/item_models.py`)\n\n"
             "| Файл в моде (`assets/rpmedicine/…`) | Откуда | Автор | Лицензия |\n|---|---|---|---|\n" + "\n".join(rows) + "\n" + end)
    if start in text:
        text = text[:text.index(start)] + block + text[text.index(end) + len(end):]
    else:
        text = text.rstrip() + "\n\n" + block + "\n"
    open(CREDITS, "w", encoding="utf-8", newline="\n").write(text)


def main(sheet=None):
    index.clear()
    times.clear()
    index.update(json.load(open(os.path.join(GEO_DIR, "items.json"), encoding="utf-8")))
    tp = os.path.join(DATA, "use_times", "animations.json")
    times.update(json.load(open(tp, encoding="utf-8"))["times"])
    for part in PARTS:
        part()
    gui_3d()
    apply_review()
    write(os.path.join(GEO_DIR, "items.json"), index)
    write(tp, {"times": times})
    write_credits()
    print("item_models:", len(built), "моделей")
    if sheet:
        preview.sheet(sheet, built, size=150)


PARTS = [part_a, part_b, part_c, part_d, part_e, part_f]

if __name__ == "__main__":
    main(sys.argv[2] if len(sys.argv) > 2 and sys.argv[1] == "--sheet" else None)
