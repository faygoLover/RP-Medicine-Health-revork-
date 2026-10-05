#!/usr/bin/env python3
# Значки состояний HUD и панели (18×18) из иконок эффектов родственных модов; недостающие рисуются здесь.
# Авторы — в docs/assets_credits.md. Нужен Pillow.
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..")
REL = os.path.join(ROOT, "docs", "reference", "родственные моды")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "rpmedicine", "textures", "gui", "status")

SRC = {
    "bleed_1": "tacmed-1.1.0/assets/tacmed/textures/mob_effect/light_bleeding.png",
    "bleed_2": "meds_and_herbs-1.20.1-2.0.3/assets/meds_and_herbs/textures/mob_effect/bleeding.png",
    "bleed_3": "tacmed-1.1.0/assets/tacmed/textures/mob_effect/heavy_bleeding.png",
    "bleed_4": "meds_and_herbs-1.20.1-2.0.3/assets/meds_and_herbs/textures/mob_effect/blood_loss.png",
    "internal": "meds_and_herbs-1.20.1-2.0.3/assets/meds_and_herbs/textures/mob_effect/internal_bleeding.png",
    "fracture": "meds_and_herbs-1.20.1-2.0.3/assets/meds_and_herbs/textures/mob_effect/broken_bone.png",
    "analgesia": "medicamod-1.0.0-forge-1.20.1/assets/medicamod/textures/mob_effect/pain_relief.png",
    "dyspnea": "medicamod-1.0.0-forge-1.20.1/assets/medicamod/textures/mob_effect/pluca.png",
    "pneumothorax": "tacmed-1.1.0/assets/tacmed/textures/mob_effect/tension_pneumothorax.png",
    "infection": "meds_and_herbs-1.20.1-2.0.3/assets/meds_and_herbs/textures/mob_effect/bacterial_infection.png",
    "fever": "legendarysurvivaloverhaul-1.20.1-2.4.2/assets/legendarysurvivaloverhaul/textures/mob_effect/heat_stroke.png",
    "cold": "legendarysurvivaloverhaul-1.20.1-2.4.2/assets/legendarysurvivaloverhaul/textures/mob_effect/frostbite.png",
    "stabilized": "legendarysurvivaloverhaul-1.20.1-2.4.2/assets/legendarysurvivaloverhaul/textures/mob_effect/recovery.png",
    "adrenaline": "meds_and_herbs-1.20.1-2.0.3/assets/meds_and_herbs/textures/mob_effect/adrenaline.png",
    "concussion": "legendarysurvivaloverhaul-1.20.1-2.4.2/assets/legendarysurvivaloverhaul/textures/mob_effect/headache.png",
    "dressed": "tacmed-1.1.0/assets/tacmed/textures/mob_effect/hemostatic_protection.png",
    "nausea": "medicamod-1.0.0-forge-1.20.1/assets/medicamod/textures/mob_effect/stomach_stabilization.png",
    "sedated": "medicamod-1.0.0-forge-1.20.1/assets/medicamod/textures/mob_effect/sedation.png",
}


def fit(img):
    img = img.convert("RGBA")
    box = img.getbbox()
    if box:
        img = img.crop(box)
    w, h = img.size
    k = 18 / max(w, h)
    nw, nh = max(1, round(w * k)), max(1, round(h * k))
    img = img.resize((nw, nh), Image.NEAREST if max(w, h) <= 36 else Image.LANCZOS)
    out = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    out.paste(img, ((18 - nw) // 2, (18 - nh) // 2), img)
    return out


def pain():
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    bolt = [(10, 1), (4, 10), (8, 10), (6, 17), (14, 7), (10, 7), (12, 1)]
    d.polygon(bolt, fill=(235, 70, 40, 255), outline=(120, 20, 10, 255))
    return im


def tourniquet():
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([1, 6, 16, 11], fill=(40, 40, 40, 255), outline=(15, 15, 15, 255))
    d.rectangle([7, 4, 10, 13], fill=(210, 80, 40, 255), outline=(110, 40, 20, 255))
    return im


def splint():
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([4, 1, 6, 16], fill=(190, 150, 90, 255))
    d.rectangle([11, 1, 13, 16], fill=(190, 150, 90, 255))
    for y in (4, 9, 14):
        d.rectangle([3, y, 14, y + 1], fill=(230, 230, 230, 255))
    return im


def drip():
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([5, 1, 12, 10], 2, fill=(200, 225, 240, 255), outline=(110, 130, 150, 255))
    d.line([8, 10, 8, 17], fill=(150, 150, 160, 255), width=1)
    return im


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    n = 0
    for name, rel in SRC.items():
        p = os.path.join(REL, rel)
        if os.path.exists(p):
            fit(Image.open(p)).save(os.path.join(OUT, name + ".png"))
            n += 1
        else:
            print("нет", rel)
    for name, fn in (("pain", pain), ("tourniquet", tourniquet), ("splint", splint), ("drip", drip)):
        fn().save(os.path.join(OUT, name + ".png"))
        n += 1
    print(n, "значков")
