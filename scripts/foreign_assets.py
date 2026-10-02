#!/usr/bin/env python3
"""Чужие ассеты под MIT: текстуры и звуки Tactical Medicine (Lector) и Health & Disease (JEDIGD).

Списки ниже читает scripts/gen_data.py (чтобы не затирать взятое заглушками). Запуск этого файла
копирует файлы из jar в external/jars (их скачивает scripts/fetch_deps.sh) в ресурсы мода.
Всё взятое записано в docs/assets_credits.md с пометкой «заменить позже».
"""
import os
import subprocess
import sys
import zipfile

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/rpmedicine")
JARS = os.path.join(ROOT, "external/jars")

TACMED = "tacmed-1.1.0.jar"
HD = "health_and_disease-1.4.2-forge-1.20.1.jar"
SOURCES = {
    TACMED: ("Tactical Medicine 1.1.0", "Lector", "assets/tacmed"),
    HD: ("Health & Disease 1.4.2", "JEDIGD", "assets/health_and_disease"),
}

# Наш предмет → (jar, файл текстуры в assets/<мод>/textures/item/).
TEXTURES = {
    "bandage": (TACMED, "bandage.png"),
    "hemostatic_gauze": (TACMED, "hemostatic.png"),
    "tourniquet": (TACMED, "tourniquet.png"),
    "esmarch": (TACMED, "esmarch_tourniquet.png"),
    "splint": (TACMED, "splint.png"),
    "occlusive_dressing": (TACMED, "chest_seal.png"),
    "decompression_needle": (TACMED, "needle_14g.png"),
    "painkillers": (TACMED, "pill_pack.png"),
    "morphine": (TACMED, "promedol.png"),
    "saline": (TACMED, "saline.png"),
    "airway": (TACMED, "npa.png"),
    "ambu_bag": (TACMED, "ambu_bag.png"),
    "defibrillator": (TACMED, "defibrillator.png"),
    "first_aid_kit": (TACMED, "reanimation_pack.png"),
    "medical_pouch": (TACMED, "ifak_pouch.png"),  # 1024×1024 → уменьшается до 32×32
    "scissors": (TACMED, "trauma_shears.png"),
    "adrenaline": (HD, "rainject.png"),
    "txa": (HD, "riiject.png"),
    "norepinephrine": (HD, "crainject.png"),
    "atropine": (HD, "criinject.png"),
    "diazepam": (HD, "cbrinject.png"),
    "ceftriaxone": (HD, "brinject.png"),
    "amoxicillin": (HD, "anti.png"),
    "paracetamol": (HD, "flu.png"),
    "ibuprofen": (HD, "flu2.png"),
    "tramadol": (HD, "maobinhibitor.png"),
    "antiseptic": (HD, "alcohol.png"),
    "ammonia": (HD, "ibru.png"),
    "blood_draw_syringe": (HD, "cinject.png"),
    "blood_sample": (HD, "bloodc.png"),
    "field_surgery_kit": (HD, "surgicalinstrument.png"),
    "suture_kit": (HD, "toolkit.png"),
}

# Наше звуковое событие → [(jar, файл в assets/<мод>/sounds/)]; несколько — варианты.
SOUNDS = {
    "bandage": [(HD, "bengdai.ogg")],
    "tourniquet": [(HD, "guding.ogg")],
    "bone_break": [(TACMED, "fracture.ogg")],
    "pills": [(HD, "result51.ogg")],
    "heavy_breathing": [(HD, "xt2v0-t7xte.ogg"), (HD, "en1te-bu0v4.ogg"), (HD, "cl336-1jp1v.ogg")],
}


def sound_file(event, i):
    """Имя файла звука в моде: assets/rpmedicine/sounds/<имя>.ogg."""
    return f"{event}_{i + 1}"


def main():
    missing = [j for j in SOURCES if not os.path.exists(os.path.join(JARS, j))]
    if missing:
        sys.exit("нет jar (запусти scripts/fetch_deps.sh): " + ", ".join(missing))
    zips = {j: zipfile.ZipFile(os.path.join(JARS, j)) for j in SOURCES}
    os.makedirs(f"{ASSETS}/textures/item", exist_ok=True)
    os.makedirs(f"{ASSETS}/sounds", exist_ok=True)
    for name, (jar, src) in TEXTURES.items():
        data = zips[jar].read(f"{SOURCES[jar][2]}/textures/item/{src}")
        out = f"{ASSETS}/textures/item/{name}.png"
        with open(out, "wb") as f:
            f.write(data)
        if name == "medical_pouch":
            subprocess.run(["convert", out, "-filter", "box", "-resize", "32x32", "-strip", out], check=True)
    n = 0
    for event, files in SOUNDS.items():
        for i, (jar, src) in enumerate(files):
            out = f"{ASSETS}/sounds/{sound_file(event, i)}.ogg"
            tmp = out + ".src.ogg"
            with open(tmp, "wb") as f:
                f.write(zips[jar].read(f"{SOURCES[jar][2]}/sounds/{src}"))
            # Minecraft не затухает стерео-звуки с расстоянием — сводим в моно (нужен ffmpeg с libvorbis).
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5",
                            "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", out], check=True)
            os.remove(tmp)
            n += 1
    print("взято:", len(TEXTURES), "текстур,", n, "звуков")


if __name__ == "__main__":
    main()
