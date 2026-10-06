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
    "laryngoscope": ("drawn", "laryngoscope"),
    "endotracheal_tube": ("drawn", "endotracheal_tube"),
    # Таблетки — флаконы, как их 3D-модели (Health & Disease)
    "ibuprofen": HD + "textures/item/ibru.png",
    "tramadol": HD + "textures/item/lamb.png",
    "paracetamol": ("hue", HD + "textures/item/ibru.png", -0.08),
    "amoxicillin": ("hue", HD + "textures/item/ibru.png", 0.45),
    "glucose_tablets": ("hue", HD + "textures/item/ibru.png", 0.07),
    # Уколы
    "syringe": MH + "textures/item/syringe_empty.png",
    "insulin": BC + "textures/item/insulin_syringe.png",
    # Капельницы и кровь (#43: пустая пробирка — как с кровью, но пустая)
    "blood_bag": ("recolor", TM + "textures/item/saline.png",
                  {(175, 205, 230): (196, 36, 46), (140, 175, 205): (150, 20, 30), (210, 225, 235): (226, 90, 96)}),
    "empty_blood_bag": ("recolor", TM + "textures/item/saline.png",
                        {(175, 205, 230): (232, 234, 238), (140, 175, 205): (206, 210, 216), (210, 225, 235): (244, 245, 247),
                         (200, 45, 45): (170, 30, 40)}),
    "test_tube": ("drawn", "test_tube"),
    "blood_sample": ("drawn", "blood_sample"),
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
    "insulin": dict(HD_INJECT, tex=("liquid", HD + "textures/item/broad-spectrum_antibiotics.png", (200, 225, 245))),
    # Автоинъекторы
    **{k: dict(HD_EINJECT, tex=HD + "textures/item/einject.png") for k in (
        "adrenaline", "txa", "ketorolac", "naloxone", "diazepam", "atropine", "ketamine", "lidocaine", "norepinephrine",
        "ceftriaxone", "morphine")},
    # Наборы и кровь из LR Tactical (модели TaCZ — вписываются в куб предмета, руки скрыты)
    "first_aid_kit": dict(geo=LR + "geo_models/consumable/carfak_geo.json", tex=LR + "textures/consumable/carfak_uv.png", fit=True, size=0.8,
                          fp={"rotation": [5, -40, 0], "translation": [1.5, 3, -1], "scale": [0.42, 0.42, 0.42]}),
    "blood_bag": dict(geo=LR + "geo_models/consumable/blood_pack_geo.json", tex=LR + "textures/consumable/blood_pack_uv.png", fit=True, size=0.75,
                      fp={"rotation": [0, 70, 10], "translation": [1, 3, -1], "scale": [0.5, 0.5, 0.5]}),
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


def main():
    # Иконки
    for item, spec in ICONS.items():
        save_png(spec, f"textures/item/{item}.png")
    for _, name, spec in ORGAN_VARIANTS + LIMB_VARIANTS:
        save_png(spec, f"textures/item/{name}.png")
        write(os.path.join(ASSETS, "models", "item", f"{name}.json"), flat(f"rpmedicine:item/{name}"))
    for name, spec in ORGAN_SPARE.items():
        save_png(spec, f"textures/item/{name}.png")
    write(os.path.join(ASSETS, "models", "item", "organ.json"), dict(flat("rpmedicine:item/organ"), overrides=[
        {"predicate": {"rpmedicine:organ": i / 10}, "model": f"rpmedicine:item/{n}"} for i, n, _ in ORGAN_VARIANTS]))
    write(os.path.join(ASSETS, "models", "item", "severed_limb.json"), dict(flat("rpmedicine:item/severed_limb"), overrides=[
        {"predicate": {"rpmedicine:part": i / 10}, "model": f"rpmedicine:item/{n}"} for i, n, _ in LIMB_VARIANTS]))

    # 3D-модели
    index, times = {}, {}
    for item, g in GEO.items():
        name = item
        # Своя папка rpgeo, не geo/animations: GeckoLib читает те папки у всех модов и падает на чужом.
        os.makedirs(os.path.join(ASSETS, "rpgeo"), exist_ok=True)
        shutil.copyfile(src_path(g["geo"]), os.path.join(ASSETS, "rpgeo", f"{name}.geo.json"))
        note(f"rpgeo/{name}.geo.json", g["geo"])
        save_png(g["tex"], f"textures/geo/{name}.png")
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
