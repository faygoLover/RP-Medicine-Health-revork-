# Внешний вид предметов RP Medicine: иконки, 3D-модели с анимациями, варианты органов и конечностей.
# Источники — моды-референсы в docs/reference/родственные моды (авторы — в docs/assets_credits.md)
# и нарисованные иконки из scripts/art_drawn.py. Запускается из gen_data.py последним шагом
# (или отдельно: python scripts/item_art.py) и перезаписывает то, что gen_data сделал заглушками.
import colorsys
import json
import os
import shutil
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import art_drawn  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "rpmedicine")
DATA = os.path.join(ROOT, "data", "rpmedicine", "rpmedicine")
REL = os.path.join(HERE, "..", "docs", "reference", "родственные моды")
CREDITS = os.path.join(HERE, "..", "docs", "assets_credits.md")

BC = "BodyControl-1.0.0-alpha/assets/bodycontrol/"
HD = "health_and_disease-1.4.2-forge-1.20.1/assets/health_and_disease/"
TM = "tacmed-1.1.0/assets/tacmed/"
TA = "tactical_aid-1.20.1-v1.3.9/assets/tactical_aid/"
MH = "meds_and_herbs-1.20.1-2.0.3/assets/meds_and_herbs/"
LR = "lrtactical-1.20.1-0.4.3/assets/lrtactical/"
AUTHORS = {
    BC: ("Body Control 1.0.0-alpha", "VKM", "MIT"), HD: ("Health & Disease 1.4.2", "JEDIGD", "MIT"),
    TM: ("Tactical Medicine 1.1.0", "Lector", "MIT"), TA: ("Tactical Aid 1.3.9", "17612", "MIT"),
    MH: ("Meds and Herbs 2.0.3", "ChebyPattern", "MIT"), LR: ("LesRaisins Tactical Equipments 0.4.3", "LesRaisins Studio", "GPL-3.0"),
}

# ------------------------------------------------------------------ иконки
# Источник иконки: путь в моде-референсе, ("drawn", ключ) — нарисована, ("recolor", путь, {цвет: цвет}),
# ("hue", путь, сдвиг тона, только_насыщенные). Предметы без записи сохраняют текущую иконку.
ICONS = {
    # Раны и кровотечение
    "suture_kit": MH + "textures/item/sewing_kit.png",
    "antibiotic_ointment": ("drawn", "antibiotic_ointment"),
    # Дыхание
    "ammonia": ("drawn", "ammonia"),
    "iv_catheter": ("drawn", "iv_catheter"),
    "laryngoscope": ("drawn", "laryngoscope"),
    "endotracheal_tube": ("drawn", "endotracheal_tube"),
    # Таблетки — флаконы, как их 3D-модели (Health & Disease)
    "ibuprofen": HD + "textures/item/ibru.png",
    "tramadol": HD + "textures/item/lamb.png",
    "paracetamol": ("hue", HD + "textures/item/ibru.png", -0.08),
    "amoxicillin": ("hue", HD + "textures/item/ibru.png", 0.45),
    "glucose_tablets": ("hue", HD + "textures/item/ibru.png", 0.07),
    # Уколы

    # Капельницы и кровь (#43: пустая пробирка — как с кровью, но пустая)
    "blood_bag": ("recolor", TM + "textures/item/saline.png",
                  {(175, 205, 230): (196, 36, 46), (140, 175, 205): (150, 20, 30), (210, 225, 235): (226, 90, 96)}),
    "empty_blood_bag": ("recolor", TM + "textures/item/saline.png",
                        {(175, 205, 230): (232, 234, 238), (140, 175, 205): (206, 210, 216), (210, 225, 235): (244, 245, 247),
                         (200, 45, 45): (170, 30, 40)}),
    # Пробирки и шприцы — H&D, как просил автор: с кровью bloodc, пустая tubes, грязная — из tubes.
    "test_tube": HD + "textures/item/tubes.png",
    "blood_sample": HD + "textures/item/bloodc.png",
    "dirty_test_tube": ("residue", HD + "textures/item/tubes.png"),
    "syringe": HD + "textures/item/emptyinject.png",
    "blood_draw_syringe": HD + "textures/item/emptyinject.png",
    "dirty_syringe": HD + "textures/item/cinject.png",
    "used_pen": ("recolor", TA + "textures/item/adrenaline.png", None),
    "insulin": ("hue", TA + "textures/item/metabolize.png", 0.33),
    # Все уколы-ручки — ручки (Tactical Aid), флаконы — флаконы (Body Control).
    "morphine": TA + "textures/item/glucose.png",
    "atropine": ("hue", TA + "textures/item/aggressiveness.png", 0.2),
    "ceftriaxone": ("hue", BC + "textures/item/hypnotic_vial.png", -0.3),
    "norepinephrine": BC + "textures/item/oil_vial.png",

    "lancet": ("drawn", "lancet"),
    # Диагностика
    "stethoscope": ("drawn", "stethoscope"),
    "thermometer": ("drawn", "thermometer"),
    "pulse_oximeter": ("drawn", "pulse_oximeter"),
    "tonometer": ("drawn", "tonometer"),
    "glucometer": ("drawn", "glucometer"),
    # Хирургия
    "surgical_mask": ("drawn", "surgical_mask"),
    "surgical_gloves": ("recolor", BC + "textures/item/gloves.png", {(74, 49, 28): (150, 205, 235), (109, 76, 47): (200, 232, 248)}),
    "hemostat": ("drawn", "hemostat"),
    "retractor": ("drawn", "retractor"),
    "surgical_drill": ("drawn", "surgical_drill"),
    "osteosynthesis_kit": ("drawn", "osteosynthesis_kit"),
    "chest_drain": ("drawn", "chest_drain"),
    "bone_saw": ("drawn", "bone_saw"),
    "field_surgery_kit": HD + "textures/item/surgicalinstrument.png",
    # Протезы, органы, конечности
    "prosthetic_foot": ("drawn", "prosthetic_foot"),
    "peg_leg": ("drawn", "peg_leg"),
    "prosthetic_hook": ("drawn", "prosthetic_hook"),
    "organ": BC + "textures/item/donor_heart.png",
    "severed_limb": ("drawn", "severed_arm"),
    # Документы (#55: медкарта — книга)
    "medcard": ("drawn", "medcard"),
}

# Варианты по NBT: органы (OrganItem, тег Organ) и конечности (SeveredLimbItem, тег Part).
# Значение предиката — индекс / 10 (ClientSetup регистрирует свойства rpmedicine:organ и rpmedicine:part).
ORGAN_VARIANTS = [  # (индекс, имя текстуры, источник)
    (1, "organ_heart", BC + "textures/item/donor_heart.png"),
    (2, "organ_lungs", BC + "textures/item/donor_lungs.png"),
    (3, "organ_liver", BC + "textures/item/donor_liver.png"),
    (4, "organ_kidneys", BC + "textures/item/donor_kidneys.png"),
    (5, "organ_intestines", ("drawn", "organ_intestines")),
    (9, "organ_spoiled", BC + "textures/item/spoiled_organ.png"),
]
# Про запас (органов в механике пока нет — вопрос автору): мозг, селезёнка, желудок, поджелудочная.
ORGAN_SPARE = {
    "organ_brain": BC + "textures/item/donor_brain.png", "organ_spleen": BC + "textures/item/donor_spleen.png",
    "organ_stomach": BC + "textures/item/donor_stomach.png", "organ_pancreas": BC + "textures/item/donor_pancreas.png",
}
LIMB_VARIANTS = [
    (1, "severed_arm", ("drawn", "severed_arm")),
    (2, "severed_leg", ("drawn", "severed_leg")),
    (3, "severed_foot", ("drawn", "severed_foot")),
]

# ------------------------------------------------------------------ 3D-модели
# geo: модель bedrock; tex: текстура (или ("recolor"/"hue", ...)); anim: файл анимаций; use: анимация
# применения (её длина — минимальное время действия); display: файл с настройками вида от H&D
# (separate_transforms) или None — общий вид для «вписанных» моделей (fit).
HD_INJECT = dict(geo=HD + "geo/inject.geo.json", anim=HD + "animations/inject.animation.json", use="injectpush",
                 display=HD + "models/displaysettings/inject.item.json")
HD_EINJECT = dict(geo=HD + "geo/einject.geo.json", anim=HD + "animations/einject.animation.json", use="injectpush",
                  display=HD + "models/displaysettings/inject.item.json")
# Пакет в руке: крупнее и в ладони, а не над ней (замечание 06.10).
BAG_FP = {"rotation": [0, 70, 10], "translation": [1, 2, -1], "scale": [0.6, 0.6, 0.6]}
BAG_TP = {"rotation": [0, 0, 90], "translation": [0, 0, 1], "scale": [0.85, 0.85, 0.85]}
HD_BOTTLE = dict(geo=HD + "geo/bottle.geo.json", anim=HD + "animations/bottle.animation.json", use="take",
                 display=HD + "models/displaysettings/bottle.item.json")
GEO = {
    "bandage": dict(geo=HD + "geo/bengdai.geo.json", tex=HD + "textures/item/bengdai.png", anim=HD + "animations/bengdai.animation.json",
                    use="dress", display=HD + "models/displaysettings/bengdai.item.json"),
    "splint": dict(geo=HD + "geo/bonefix.geo.json", tex=("hue", HD + "textures/item/bonefix.png", 0.55),
                   anim=HD + "animations/bonefix.animation.json", use="bonefix", display=HD + "models/displaysettings/bonefix.item.json"),
    "field_surgery_kit": dict(geo=HD + "geo/surg.geo.json", tex=HD + "textures/item/surg.png", anim=HD + "animations/surg.animation.json",
                              use="surg", display=HD + "models/displaysettings/surg.item.json"),
    # Флаконы с таблетками
    "ibuprofen": dict(HD_BOTTLE, tex=HD + "textures/item/bottle7.png"),
    "tramadol": dict(HD_BOTTLE, tex=HD + "textures/item/bottle2.png"),
    "paracetamol": dict(HD_BOTTLE, tex=HD + "textures/item/bottle4.png"),
    "amoxicillin": dict(HD_BOTTLE, tex=HD + "textures/item/bottle6.png"),
    "glucose_tablets": dict(HD_BOTTLE, tex=HD + "textures/item/bottle5.png"),
    # Шприцы
    "syringe": dict(HD_INJECT, tex=("liquid", HD + "textures/item/broad-spectrum_antibiotics.png", None)),
    "blood_draw_syringe": dict(HD_INJECT, tex=("liquid", HD + "textures/item/broad-spectrum_antibiotics.png", (150, 20, 30))),

    # Шприц-ручки: инжектор H&D в цвет своей иконки (цвет считается по иконке ниже, в main)
    **{k: dict(HD_EINJECT, tex=("tint", HD + "textures/item/einject.png", None)) for k in (
        "adrenaline", "txa", "ketorolac", "naloxone", "diazepam", "atropine", "ketamine", "lidocaine", "morphine", "insulin")},
    "used_pen": dict(HD_EINJECT, tex=("tint", HD + "textures/item/einject.png", (120, 110, 100))),
    "dirty_syringe": dict(HD_INJECT, tex=("liquid", HD + "textures/item/broad-spectrum_antibiotics.png", (110, 40, 34))),
    # Наборы и кровь из LR Tactical (модели TaCZ — вписываются в куб предмета, руки скрыты)
    "first_aid_kit": dict(geo=LR + "geo_models/consumable/carfak_geo.json", tex=LR + "textures/consumable/carfak_uv.png", fit=True, size=0.8,
                          fp={"rotation": [5, -40, 0], "translation": [1.5, 3, -1], "scale": [0.42, 0.42, 0.42]}),
    # Пакеты — одна модель LR (пакет крови), жидкость перекрашена (замечание 06.10): физраствор, пустой.
    **{k: dict(geo=LR + "geo_models/consumable/blood_pack_geo.json", tex=tex, fit=True, size=0.95, fp=BAG_FP, tp=BAG_TP)
       for k, tex in {"blood_bag": LR + "textures/consumable/blood_pack_uv.png",
                      "saline": ("bag", LR + "textures/consumable/blood_pack_uv.png", (214, 232, 240, 150)),
                      "empty_blood_bag": ("bag", LR + "textures/consumable/blood_pack_uv.png", (186, 213, 219, 70))}.items()},
}
FIT_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1.5, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 1.5, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -25, 0], "translation": [1, 2, 0], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, 25, 0], "translation": [-1, 2, 0], "scale": [0.6, 0.6, 0.6]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"translation": [0, 13, 7], "scale": [1, 1, 1]},
}


# ------------------------------------------------------------------ инструменты
used = []  # (файл в моде, источник) — для списка авторов


def src_path(rel):
    return os.path.join(REL, rel)


def load(spec):
    """Картинка по описанию источника."""
    if isinstance(spec, tuple):
        kind = spec[0]
        if kind == "drawn":
            return art_drawn.ICONS[spec[1]]
        im = Image.open(src_path(spec[1])).convert("RGBA")
        if kind == "recolor" and spec[2] is None:
            # Грязный: обесцветить и чуть затемнить в бурый.
            px = im.load()
            for y in range(im.size[1]):
                for x in range(im.size[0]):
                    r, g, b, a = px[x, y]
                    if a:
                        l = (r * 30 + g * 59 + b * 11) // 100
                        px[x, y] = (min(255, l * 92 // 100 + 12), l * 88 // 100, l * 80 // 100, a)
            return im
        if kind == "recolor":
            px = im.load()
            for y in range(im.size[1]):
                for x in range(im.size[0]):
                    r, g, b, a = px[x, y]
                    if a and (r, g, b) in spec[2]:
                        px[x, y] = spec[2][(r, g, b)] + (a,)
            return im
        if kind == "hue":
            px = im.load()
            for y in range(im.size[1]):
                for x in range(im.size[0]):
                    r, g, b, a = px[x, y]
                    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
                    if a and s > 0.25:
                        nr, ng, nb = colorsys.hls_to_rgb((h + spec[2]) % 1, l, s)
                        px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
            return im
        if kind == "bag":
            # Пакет LR: красное (кровь в пакете и трубке) -> другой цвет с той же светотенью.
            px = im.load()
            tr, tg, tb, ta = spec[2]
            for y in range(im.size[1]):
                for x in range(im.size[0]):
                    r, g, b, a = px[x, y]
                    if a == 255 and r > g + 25 and r > b + 25:
                        k = min(1.0, 0.75 + (r + g + b) / 765)
                        px[x, y] = (round(tr * k), round(tg * k), round(tb * k), ta)
            return im
        if kind == "residue":
            # Использованная: стекло мутнее, внизу бурый налёт.
            px = im.load()
            h = im.size[1]
            for y in range(h):
                for x in range(im.size[0]):
                    r, g, b, a = px[x, y]
                    if not a:
                        continue
                    lum = (r * 30 + g * 59 + b * 11) // 100
                    if y > h * 0.55 and lum > 120:
                        px[x, y] = (150, 92, 70, a)
                    else:
                        px[x, y] = (min(255, lum * 95 // 100 + 8), lum * 92 // 100, lum * 86 // 100, a)
            return im
        if kind == "tint":
            # Окрасить серую текстуру модели в цвет (яркость сохраняется, блики остаются светлыми).
            px = im.load()
            tr, tg, tb = spec[2]
            for y in range(im.size[1]):
                for x in range(im.size[0]):
                    r, g, b, a = px[x, y]
                    if not a:
                        continue
                    h_, l_, s_ = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
                    if s_ > 0.35:
                        continue
                    k = 0.7 if l_ < 0.85 else 0.25
                    nr, ng, nb = (r * (1 - k) + r * tr / 255 * k, g * (1 - k) + g * tg / 255 * k, b * (1 - k) + b * tb / 255 * k)
                    px[x, y] = (round(nr), round(ng), round(nb), a)
            return im
        if kind == "liquid":
            # Жидкость в шприце (жёлтая в оригинале): другой цвет или прозрачное стекло.
            px = im.load()
            for y in range(im.size[1]):
                for x in range(im.size[0]):
                    r, g, b, a = px[x, y]
                    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
                    if a and s > 0.3 and 0.1 < h < 0.2:
                        if spec[2] is None:
                            px[x, y] = (226, 234, 242, a)
                        else:
                            k = l / 0.6
                            px[x, y] = tuple(min(255, round(c * k)) for c in spec[2]) + (a,)
            return im
        raise ValueError(spec)
    im = Image.open(src_path(spec)).convert("RGBA")
    w, h = im.size
    if h > w and h % w == 0:
        im = im.crop((0, 0, w, w))
    return im


def note(dst, spec):
    if isinstance(spec, tuple) and spec[0] == "drawn":
        return
    rel = spec[1] if isinstance(spec, tuple) else spec
    used.append((dst, rel, spec[0] if isinstance(spec, tuple) else ""))


def save_png(spec, dst_rel):
    im = load(spec)
    dst = os.path.join(ASSETS, dst_rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    im.save(dst)
    note(dst_rel, spec)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def flat(tex):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": tex}}


def anim_length(path, name):
    d = json.load(open(src_path(path), encoding="utf-8"))
    a = d["animations"].get(name)
    if not a:
        return 0
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


def icon_color(item):
    """Основной цвет иконки предмета (насыщенные пиксели), для окраски 3D-модели в тон иконки."""
    path = os.path.join(ASSETS, "textures", "item", item + ".png")
    if not os.path.exists(path):
        return (200, 200, 200)
    im = Image.open(path).convert("RGBA")
    acc, n = [0, 0, 0], 0
    for r, g, b, a in im.getdata():
        h, l, s_ = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        if a > 128 and s_ > 0.35 and 0.2 < l < 0.85:
            acc[0] += r; acc[1] += g; acc[2] += b; n += 1
    return tuple(c // n for c in acc) if n else (200, 200, 200)


def main():
    # Иконки
    for item, spec in ICONS.items():
        save_png(spec, f"textures/item/{item}.png")
        if item not in GEO and item not in ("organ", "severed_limb"):
            write(os.path.join(ASSETS, "models", "item", f"{item}.json"), flat(f"rpmedicine:item/{item}"))
    for _, name, spec in ORGAN_VARIANTS + LIMB_VARIANTS:
        save_png(spec, f"textures/item/{name}.png")
        write(os.path.join(ASSETS, "models", "item", f"{name}.json"), flat(f"rpmedicine:item/{name}"))
    for name, spec in ORGAN_SPARE.items():
        save_png(spec, f"textures/item/{name}.png")
    write(os.path.join(ASSETS, "models", "item", "organ.json"), dict(flat("rpmedicine:item/organ"), overrides=[
        {"predicate": {"rpmedicine:organ": i / 10}, "model": f"rpmedicine:item/{n}"} for i, n, _ in ORGAN_VARIANTS]))
    write(os.path.join(ASSETS, "models", "item", "severed_limb.json"), dict(flat("rpmedicine:item/severed_limb"), overrides=[
        {"predicate": {"rpmedicine:part": i / 10}, "model": f"rpmedicine:item/{n}"} for i, n, _ in LIMB_VARIANTS]))

    # Фоны подсумка и аптечки (замечание 48): рисунки Tactical Medicine как есть, 1024×1024.
    for dst, src in {"textures/gui/medical_pouch.png": TM + "textures/gui/ifak_pouch.png",
                     "textures/gui/first_aid_kit.png": TM + "textures/gui/paramedic_backpack.png"}.items():
        os.makedirs(os.path.dirname(os.path.join(ASSETS, dst)), exist_ok=True)
        shutil.copyfile(src_path(src), os.path.join(ASSETS, dst))
        note(dst, src)

    # Сцена операции: бельё, кожа, жир, мышца, кость, полость, кровь, органы, инструменты (Body Control).
    for name in ("blood", "bone", "cavity", "drape", "fat", "muscle", "organs", "skin", "tools"):
        dst = f"textures/gui/surgery/{name}.png"
        os.makedirs(os.path.dirname(os.path.join(ASSETS, dst)), exist_ok=True)
        shutil.copyfile(src_path(BC + f"textures/gui/surgery_{name}.png"), os.path.join(ASSETS, dst))
        note(dst, BC + f"textures/gui/surgery_{name}.png")

    # 3D-модели
    index, times = {}, {}
    for item, g in GEO.items():
        name = item
        # Своя папка rpgeo, не geo/animations: GeckoLib читает те папки у всех модов и падает на чужом.
        os.makedirs(os.path.join(ASSETS, "rpgeo"), exist_ok=True)
        shutil.copyfile(src_path(g["geo"]), os.path.join(ASSETS, "rpgeo", f"{name}.geo.json"))
        note(f"rpgeo/{name}.geo.json", g["geo"])
        tex = g["tex"]
        if isinstance(tex, tuple) and tex[0] == "tint" and tex[2] is None:
            tex = ("tint", tex[1], icon_color(item))
        save_png(tex, f"textures/geo/{name}.png")
        entry = {"geo": f"rpmedicine:rpgeo/{name}.geo.json", "texture": f"rpmedicine:textures/geo/{name}.png"}
        if g.get("anim"):
            shutil.copyfile(src_path(g["anim"]), os.path.join(ASSETS, "rpgeo", f"{name}.anim.json"))
            note(f"rpgeo/{name}.anim.json", g["anim"])
            entry["animations"] = f"rpmedicine:rpgeo/{name}.anim.json"
            if g.get("use"):
                entry["use"] = g["use"]
                times[f"rpmedicine:{item}"] = round(anim_length(g["anim"], g["use"]), 2)
        if g.get("fit"):
            entry["fit"] = True
            entry["size"] = g.get("size", 0.75)
            entry["hide"] = ["lefthand", "righthand", "lefthand_pos", "righthand_pos"]
            display = dict(FIT_DISPLAY)
            if g.get("tp"):
                tp = g["tp"]
                display["thirdperson_righthand"] = tp
                display["thirdperson_lefthand"] = dict(tp, rotation=[tp["rotation"][0], -tp["rotation"][1], -tp["rotation"][2]])
            if g.get("fp"):
                # Свой вид в руке от первого лица (левая рука — зеркально по Y).
                fp = g["fp"]
                display["firstperson_righthand"] = fp
                display["firstperson_lefthand"] = dict(fp, rotation=[fp["rotation"][0], -fp["rotation"][1], -fp["rotation"][2]],
                                                       translation=[-fp["translation"][0], fp["translation"][1], fp["translation"][2]])
        else:
            display = json.load(open(src_path(g["display"]), encoding="utf-8"))["base"]["display"]
            # Начало модели — центр низа блока: на земле опускаем, чтобы не висела над тенью.
            display = dict(display, ground={"translation": [0, -3, 0], "scale": [0.5, 0.5, 0.5]})
        index[f"rpmedicine:{item}"] = entry
        model = {
            "loader": "forge:separate_transforms", "gui_light": "front",
            "base": {"parent": "builtin/entity", "display": display, "textures": {"particle": f"rpmedicine:item/{item}"}},
            "perspectives": {"gui": flat(f"rpmedicine:item/{item}")},
        }
        write(os.path.join(ASSETS, "models", "item", f"{item}.json"), model)
    write(os.path.join(ASSETS, "rpgeo", "items.json"), index)
    write(os.path.join(DATA, "use_times", "animations.json"), {"times": times})

    # Авторы
    rows = []
    seen = set()
    for dst, rel, kind in used:
        if (dst, rel) in seen:
            continue
        seen.add((dst, rel))
        mod = next(((m, a, lic) for k, (m, a, lic) in AUTHORS.items() if rel.startswith(k)), ("?", "?", "?"))
        how = {"recolor": " (перекрашено)", "hue": " (другой оттенок)", "liquid": " (другой цвет жидкости)"}.get(kind, "")
        rows.append(f"| `{dst}` | {mod[0]}: `{rel.split('/', 1)[1]}`{how} | {mod[1]} | {mod[2]} |")
    text = open(CREDITS, encoding="utf-8").read()
    start, end = "<!-- item_art:begin -->", "<!-- item_art:end -->"
    block = (start + "\n## Внешний вид предметов (`scripts/item_art.py`)\n\n"
             "Иконки, 3D-модели и анимации предметов. Нарисованные вручную иконки (`scripts/art_drawn.py`) — свои, "
             "в списке их нет. 3D-модели рисует собственный рендерер `client/geo` (формат Bedrock, без GeckoLib).\n\n"
             "| Файл в моде (`assets/rpmedicine/…`) | Откуда | Автор | Лицензия |\n|---|---|---|---|\n" + "\n".join(rows) + "\n" + end)
    if start in text:
        text = text[:text.index(start)] + block + text[text.index(end) + len(end):]
    else:
        text = text.rstrip() + "\n\n" + block + "\n"
    open(CREDITS, "w", encoding="utf-8", newline="\n").write(text)
    print("item_art:", len(ICONS), "иконок,", len(GEO), "3D-моделей,", len(times), "минимальных времён")


if __name__ == "__main__":
    main()
