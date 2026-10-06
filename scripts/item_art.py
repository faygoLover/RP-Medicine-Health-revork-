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
    "used_iv_bag": ("recolor", TM + "textures/item/saline.png",
                    {(175, 205, 230): (214, 220, 226), (140, 175, 205): (196, 202, 210), (210, 225, 235): (232, 236, 240)}),
    "lab_report": ("drawn", "lab_report"),
    # Испорченная кровь — бурая (решения, п. 1.16).
    "blood_bag_spoiled": ("recolor", TM + "textures/item/saline.png",
                          {(175, 205, 230): (96, 52, 30), (140, 175, 205): (70, 36, 20), (210, 225, 235): (120, 72, 44)}),
    "filled_syringe": ("liquidicon", HD + "textures/item/brinject.png", (236, 234, 224)),
    "ketamine": ("hue", BC + "textures/item/hypnotic_vial.png", 0.5),
    "lidocaine": ("hue", BC + "textures/item/hypnotic_vial.png", 0.12),
    "propofol": BC + "textures/item/emulsion_vial.png",

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
    (6, "organ_spleen", BC + "textures/item/donor_spleen.png"),
    (7, "organ_brain", BC + "textures/item/donor_brain.png"),
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
PEN_ITEMS = ("adrenaline", "morphine", "txa", "ketorolac", "naloxone", "diazepam", "atropine", "insulin", "used_pen")


def pen_geo():
    """Инжектор H&D, переделанный под шприц-ручку (координаты Bedrock, текстура 32x32 в UV)."""
    def cube(o, sz, uv):
        return {"origin": o, "size": sz, "uv": uv}
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.rpm_pen", "texture_width": 32, "texture_height": 32,
                        "visible_bounds_width": 2, "visible_bounds_height": 2.5, "visible_bounds_offset": [0, 0.75, 0]},
        "bones": [
            {"name": "bone2", "pivot": [0, 0, 0], "cubes": [
                cube([-0.75, 5.5, 0.25], [0.5, 2.5, 0.5], [0, 14]),        # игла — короче
                cube([-2.3, 17.5, -1.3], [3.6, 1, 3.6], [9, 11]),           # кольцо
                cube([-2, 8, -1], [3, 11, 3], [0, 0])]},                     # корпус с окошками
            {"name": "liquid", "parent": "bone2", "pivot": [-0.5, 9.6, 0.5], "cubes": [
                cube([-1.7, 9.6, -0.7], [2.4, 6.8, 2.4], [20, 20])]},
            {"name": "stopper", "parent": "bone2", "pivot": [-0.5, 9.6, 0.5], "cubes": [
                cube([-1.8, 9.6, -0.8], [2.6, 0.6, 2.6], [15, 1])]},
            {"name": "pad", "parent": "bone2", "pivot": [0, 19, 0], "cubes": [
                cube([-1.8, 19, -0.8], [2.6, 1, 2.6], [9, 0]),
                cube([-1, 17, 0], [1, 2, 1], [12, 3])]},
        ]}]}


# Укол ручкой: поднести и приставить (как у H&D), пятка поднимается и нажимается.
PEN_ANIM = {"format_version": "1.8.0", "animations": {"inject": {"animation_length": 1.0, "bones": {
    "bone2": {"rotation": {"0.0": {"vector": [0, 0, 0]}, "0.125": {"vector": [68.82717, 18.74724, -7.09597]}},
              "position": {"0.0": {"vector": [0, 0, 0]}, "0.125": {"vector": [3, 4, 0]}, "0.25": {"vector": [4, 4, 4]}}},
    "pad": {"position": {"0.0": {"vector": [0, 0, 0]}, "0.3": {"vector": [0, 2.5, 0]}, "0.6": {"vector": [0, 2.5, 0]},
                         "0.95": {"vector": [0, 0, 0]}}},
}}}}

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
    "syringe": dict(HD_INJECT, tex=("liquid", HD + "textures/item/broad-spectrum_antibiotics.png", None), hide=["bone3"]),
    "filled_syringe": dict(HD_INJECT, tex=("liquid", HD + "textures/item/broad-spectrum_antibiotics.png", (240, 238, 228))),
    "blood_draw_syringe": dict(HD_INJECT, tex=("liquid", HD + "textures/item/broad-spectrum_antibiotics.png", (150, 20, 30))),

    # Шприц-ручки: инжектор H&D в цвет своей иконки (цвет считается по иконке ниже, в main)
    **{k: dict(HD_EINJECT, tex=("tint", HD + "textures/item/einject.png", None)) for k in (
        "adrenaline", "txa", "ketorolac", "naloxone", "diazepam", "atropine", "morphine", "insulin")},
    "used_pen": dict(HD_EINJECT, tex=("tint", HD + "textures/item/einject.png", (120, 110, 100))),
    "dirty_syringe": dict(HD_INJECT, tex=("bloodtip", HD + "textures/item/broad-spectrum_antibiotics.png", None), hide=["bone3"]),
    # Наборы и кровь из LR Tactical (модели TaCZ — вписываются в куб предмета, руки скрыты)
    "first_aid_kit": dict(geo=LR + "geo_models/consumable/carfak_geo.json", tex=LR + "textures/consumable/carfak_uv.png", fit=True, size=0.8,
                          anim=LR + "animations/consumable/carfak.animation.json",
                          fp={"rotation": [5, -40, 0], "translation": [1.5, 3, -1], "scale": [0.42, 0.42, 0.42]}),
    # Пакеты — одна модель LR (пакет крови), жидкость перекрашена (замечание 06.10): физраствор, пустой.
    **{k: dict(geo=LR + "geo_models/consumable/blood_pack_geo.json", tex=tex, fit=True, size=0.95, fp=BAG_FP, tp=BAG_TP,
               anim=LR + "animations/consumable/blood_pack.animation.json", use="use")
       for k, tex in {"blood_bag": LR + "textures/consumable/blood_pack_uv.png",
                      "saline": ("bag", LR + "textures/consumable/blood_pack_uv.png", (214, 232, 240, 150)),
                      "empty_blood_bag": ("bag", LR + "textures/consumable/blood_pack_uv.png", (186, 213, 219, 70))}.items()},
}
LR_EXTRA = {"lr_ai2": "ai2", "lr_cms": "cms", "lr_surv12": "surv12", "lr_goldenstar": "goldenstar", "lr_vaseline": "vaseline",
            "lr_ibuprofen": "ibuprofen", "lr_amoxycillin": "amoxycillin"}
for _k, _n in LR_EXTRA.items():
    GEO[_k] = dict(geo=LR + f"geo_models/consumable/{_n}_geo.json", tex=LR + f"textures/consumable/{_n}_uv.png", fit=True, size=0.8,
                   anim=LR + f"animations/consumable/{_n}.animation.json", use="use")
    ICONS[_k] = ("small", LR + f"textures/consumable/slot/{_n}.png")

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
        if kind == "small":
            # Иконка LR (128–256 px) — до 32 px.
            return im.resize((32, 32), Image.LANCZOS)
        if kind == "liquidicon":
            # Иконка шприца H&D: насыщенная жидкость -> заданный цвет с той же светотенью.
            px = im.load()
            for y in range(im.size[1]):
                for x in range(im.size[0]):
                    r, g, b, a = px[x, y]
                    h_, l_, s_ = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
                    if a and s_ > 0.35:
                        k = 0.8 + l_ * 0.4
                        px[x, y] = tuple(min(255, round(c * k)) for c in spec[2]) + (a,)
            return im
        if kind == "bloodtip":
            # Использованный шприц: пусто, кровь на кончике иглы (UV иглы 0,14 — в 64-px текстуре x 0..3, y 28..37).
            px = im.load()
            for y in range(im.size[1]):
                for x in range(im.size[0]):
                    r, g, b, a = px[x, y]
                    h_, l_, s_ = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
                    if a and s_ > 0.3 and 0.1 < h_ < 0.2:
                        px[x, y] = (226, 234, 242, a)
            for y in range(34, 39):
                for x in range(0, 4):
                    if px[x, y][3]:
                        px[x, y] = (150, 24, 32, 255) if (x + y) % 2 else (120, 14, 22, 255)
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

    # Инструменты держат как инструмент (рукоять в кулаке), а не как плоскую картинку (замечание 06.10).
    for item in ("scalpel", "hemostat", "retractor", "surgical_tweezers", "scissors", "bone_saw", "surgical_drill", "laryngoscope",
                 "thermometer", "decompression_needle", "vascular_suture", "lancet"):
        path = os.path.join(ASSETS, "models", "item", f"{item}.json")
        if os.path.exists(path) and json.load(open(path, encoding="utf-8")).get("parent") == "minecraft:item/generated":
            write(path, {"parent": "minecraft:item/handheld", "textures": {"layer0": f"rpmedicine:item/{item}"}})

    # Хирургическая маска на голове (замечание 06.10: была над головой) — объёмная, на лице, с завязками;
    # в руке и в инвентаре — плоская иконка.
    mtex = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if y < 12:
                c = (150, 205, 228) if y % 3 else (118, 172, 200)   # складки ткани
                mtex.putpixel((x, y), c + (255,))
            else:
                mtex.putpixel((x, y), (238, 242, 246, 255))           # завязки
    mtex.save(os.path.join(ASSETS, "textures", "item", "surgical_mask_3d.png"))

    def box(a, b, uv_front, uv_side=(0, 13, 16, 14)):
        faces = {f: {"uv": list(uv_side), "texture": "#m"} for f in ("east", "west", "up", "down", "south")}
        faces["north"] = {"uv": list(uv_front), "texture": "#m"}
        return {"from": a, "to": b, "faces": faces}
    mask3d = {"textures": {"m": "rpmedicine:item/surgical_mask_3d", "particle": "rpmedicine:item/surgical_mask"},
              "elements": [
                  box([3.6, 2.1, 0.9], [12.4, 6.0, 1.6], (0, 0, 16, 11)),           # полотно маски
                  box([4.6, 6.0, 1.1], [11.4, 6.7, 1.6], (0, 0, 16, 2)),            # верх у носа
                  box([1.3, 5.2, 1.2], [1.6, 5.7, 9.0], (0, 13, 16, 14)),           # завязка слева
                  box([14.4, 5.2, 1.2], [14.7, 5.7, 9.0], (0, 13, 16, 14)),         # завязка справа
                  box([1.3, 2.6, 1.2], [1.6, 3.1, 8.0], (0, 13, 16, 14)),
                  box([14.4, 2.6, 1.2], [14.7, 3.1, 8.0], (0, 13, 16, 14)),
              ],
              "display": {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}}}
    write(os.path.join(ASSETS, "models", "item", "surgical_mask_worn.json"), mask3d)
    flat_mask = flat("rpmedicine:item/surgical_mask")
    write(os.path.join(ASSETS, "models", "item", "surgical_mask.json"), {
        "loader": "forge:separate_transforms", "gui_light": "front",
        "base": flat_mask,
        "perspectives": {"head": {"parent": "rpmedicine:item/surgical_mask_worn"}}})

    # Стетоскоп в руке (замечание 06.10): головка 3D, трубки к ушам рисует StethoscopeLayer; в инвентаре — иконка.
    # Текстура мельче (32 px), головка крупнее, ножка толще, пластины не в одной плоскости (без мерцания).
    stex = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(32):
            if y < 12:
                c = (206, 211, 219) if (x + y) % 7 else (234, 238, 242)    # металл головки
                if 3 <= x <= 28 and y in (5, 6) and x % 3 == 0:
                    c = (176, 182, 192)                                    # насечка мембраны
            elif y < 20:
                c = (150, 156, 166) if x % 2 else (136, 142, 152)          # обод, ножка
            else:
                c = (48, 48, 54) if (x + y) % 5 else (64, 64, 72)          # трубка
            stex.putpixel((x, y), c + (255,))
    stex.save(os.path.join(ASSETS, "textures", "item", "stethoscope_3d.png"))

    def sbox(a, b, uv):
        return {"from": a, "to": b, "faces": {f: {"uv": list(uv), "texture": "#s"} for f in ("north", "south", "east", "west", "up", "down")}}
    head3d = {"texture_size": [32, 32], "textures": {"s": "rpmedicine:item/stethoscope_3d", "particle": "rpmedicine:item/stethoscope"},
              "elements": [
                  sbox([5.5, 4.5, 7.3], [10.5, 11.5, 8.7], (0, 0, 16, 6)),      # мембрана (восьмиугольник из двух плашек)
                  sbox([4.5, 5.5, 7.35], [11.5, 10.5, 8.65], (0, 0, 16, 6)),
                  sbox([6.5, 6.5, 8.7], [9.5, 9.5, 9.6], (0, 6, 16, 10)),       # чашечка
                  sbox([7, 11.5, 7.2], [9, 13.5, 8.8], (0, 6, 16, 10)),         # ножка — толще
                  sbox([7.3, 13.5, 7.3], [8.7, 16, 8.7], (0, 10, 16, 16)),      # трубка
              ],
              "display": {
                  # От третьего лица трубка — вниз, в руку (замечание 06.10).
                  "thirdperson_righthand": {"rotation": [0, 0, 180], "translation": [0, 1, 1.5], "scale": [0.55, 0.55, 0.55]},
                  "thirdperson_lefthand": {"rotation": [0, 0, 180], "translation": [0, 1, 1.5], "scale": [0.55, 0.55, 0.55]},
                  "firstperson_righthand": {"rotation": [-10, 110, 0], "translation": [1, 3.5, -1], "scale": [0.4, 0.4, 0.4]},
                  "firstperson_lefthand": {"rotation": [-10, -110, 0], "translation": [-1, 3.5, -1], "scale": [0.4, 0.4, 0.4]},
                  "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
                  "fixed": {"scale": [0.6, 0.6, 0.6]}}}
    write(os.path.join(ASSETS, "models", "item", "stethoscope_held.json"), head3d)
    write(os.path.join(ASSETS, "models", "item", "stethoscope.json"), {
        "loader": "forge:separate_transforms", "gui_light": "front",
        "base": {"parent": "rpmedicine:item/stethoscope_held"},
        "perspectives": {"gui": flat("rpmedicine:item/stethoscope")}})

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
        if g.get("hide") and not g.get("fit"):
            entry["hide"] = g["hide"]
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
    # Испорченный пакет крови — своя иконка (предикат rpmedicine:spoiled).
    bb = os.path.join(ASSETS, "models", "item", "blood_bag.json")
    if os.path.exists(bb):
        m = json.load(open(bb, encoding="utf-8"))
        spoiled = json.loads(json.dumps(m))
        spoiled["perspectives"] = {"gui": flat("rpmedicine:item/blood_bag_spoiled")}
        spoiled.pop("overrides", None)
        write(os.path.join(ASSETS, "models", "item", "blood_bag_spoiled.json"), spoiled)
        m["overrides"] = [{"predicate": {"rpmedicine:spoiled": 1}, "model": "rpmedicine:item/blood_bag_spoiled"}]
        write(bb, m)

    # Шприц-ручки (замечание 06.10): своя модель на основе инжектора H&D — игла короче и стальная, окошко длиннее вниз,
    # внутри жидкость (уровень — по оставшимся дозам, рисует GeoItemRenderer: кость liquid), на ней основание поршня
    # (кость stopper), пятка поршня (кость pad) опущена и поднимается только в анимации.
    for item in PEN_ITEMS:
        if f"rpmedicine:{item}" not in index:
            continue
        write(os.path.join(ASSETS, "rpgeo", f"{item}.geo.json"), pen_geo())
        write(os.path.join(ASSETS, "rpgeo", f"{item}.anim.json"), PEN_ANIM)
        tpath = os.path.join(ASSETS, "textures", "geo", f"{item}.png")
        im = Image.open(tpath).convert("RGBA")
        px = im.load()
        # Окошки длиннее вниз: прозрачные щели до строки 24 (в 64-px текстуре).
        for x0 in (2, 8, 14, 20):
            for x in (x0, x0 + 1):
                for y in range(11, 25):
                    px[x, y] = (0, 0, 0, 0)
        # Игла — сталь, не цвет корпуса.
        for y in range(28, 35):
            for x in range(0, 4):
                v = 205 if (x + y) % 3 else 228
                px[x, y] = (v, v + 4, v + 10, 255)
        # Жидкость — светлее цвета иконки.
        c = icon_color(item)
        liq = tuple(min(255, round(v * 0.55 + 255 * 0.45)) for v in c)
        for y in range(40, 60):
            for x in range(40, 60):
                px[x, y] = liq + (235,)
        im.save(tpath)
        index[f"rpmedicine:{item}"]["use"] = "inject"
        times[f"rpmedicine:{item}"] = 1.0
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
