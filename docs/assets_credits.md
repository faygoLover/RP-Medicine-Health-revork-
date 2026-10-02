# Чужие ассеты в RP Medicine

Текстуры предметов и звуки из двух модов под лицензией MIT — **Tactical Medicine** 1.1.0 (автор Lector, CurseForge `tactical-medicine`) и **Health & Disease** 1.4.2 (автор JEDIGD, сделан в MCreator, CurseForge `health-and-disease`). Лицензия указана в `META-INF/mods.toml` обоих модов (`license = "MIT"`); отдельного файла лицензии в jar нет, поэтому текст MIT приведён ниже.

Всё взятое — временное: **заменить позже** своими ассетами. Список и копирование — `scripts/foreign_assets.py` (берёт файлы из jar, которые скачивает `scripts/fetch_deps.sh`); `scripts/gen_data.py` эти файлы не затирает. Звуки сведены в моно (стерео-звуки Minecraft не затухают с расстоянием), текстура подсумка уменьшена; остальное — без изменений.

Остальные текстуры — простые заглушки 16×16 из `scripts/gen_data.py`; остальные звуки — ссылки на ванильные звуковые события в `assets/rpmedicine/sounds.json`.

Модели GeckoLib из Health & Disease (15 штук) не взяты: для них нужен GeckoLib, а у RP Medicine одна обязательная зависимость — RP Perks. Предметы плоские (`item/generated`).

| Файл в моде (`assets/rpmedicine/…`) | Откуда | Автор | Лицензия | Заменить позже |
|---|---|---|---|---|
| `textures/item/bandage.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/bandage.png` | Lector | MIT | да |
| `textures/item/hemostatic_gauze.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/hemostatic.png` | Lector | MIT | да |
| `textures/item/tourniquet.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/tourniquet.png` | Lector | MIT | да |
| `textures/item/esmarch.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/esmarch_tourniquet.png` | Lector | MIT | да |
| `textures/item/splint.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/splint.png` | Lector | MIT | да |
| `textures/item/occlusive_dressing.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/chest_seal.png` | Lector | MIT | да |
| `textures/item/decompression_needle.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/needle_14g.png` | Lector | MIT | да |
| `textures/item/painkillers.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/pill_pack.png` | Lector | MIT | да |
| `textures/item/morphine.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/promedol.png` | Lector | MIT | да |
| `textures/item/saline.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/saline.png` | Lector | MIT | да |
| `textures/item/airway.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/npa.png` | Lector | MIT | да |
| `textures/item/ambu_bag.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/ambu_bag.png` | Lector | MIT | да |
| `textures/item/defibrillator.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/defibrillator.png` | Lector | MIT | да |
| `textures/item/first_aid_kit.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/reanimation_pack.png` | Lector | MIT | да |
| `textures/item/medical_pouch.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/ifak_pouch.png` (уменьшена с 1024×1024 до 32×32) | Lector | MIT | да |
| `textures/item/scissors.png` | Tactical Medicine 1.1.0: `assets/tacmed/textures/item/trauma_shears.png` | Lector | MIT | да |
| `textures/item/adrenaline.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/rainject.png` | JEDIGD | MIT | да |
| `textures/item/txa.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/riiject.png` | JEDIGD | MIT | да |
| `textures/item/norepinephrine.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/crainject.png` | JEDIGD | MIT | да |
| `textures/item/atropine.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/criinject.png` | JEDIGD | MIT | да |
| `textures/item/diazepam.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/cbrinject.png` | JEDIGD | MIT | да |
| `textures/item/ceftriaxone.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/brinject.png` | JEDIGD | MIT | да |
| `textures/item/amoxicillin.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/anti.png` | JEDIGD | MIT | да |
| `textures/item/paracetamol.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/flu.png` | JEDIGD | MIT | да |
| `textures/item/ibuprofen.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/flu2.png` | JEDIGD | MIT | да |
| `textures/item/tramadol.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/maobinhibitor.png` | JEDIGD | MIT | да |
| `textures/item/antiseptic.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/alcohol.png` | JEDIGD | MIT | да |
| `textures/item/ammonia.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/ibru.png` | JEDIGD | MIT | да |
| `textures/item/blood_draw_syringe.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/cinject.png` | JEDIGD | MIT | да |
| `textures/item/blood_sample.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/bloodc.png` | JEDIGD | MIT | да |
| `textures/item/field_surgery_kit.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/surgicalinstrument.png` | JEDIGD | MIT | да |
| `textures/item/suture_kit.png` | Health & Disease 1.4.2: `assets/health_and_disease/textures/item/toolkit.png` | JEDIGD | MIT | да |
| `sounds/bandage_1.ogg` (событие `bandage`) | Health & Disease 1.4.2: `assets/health_and_disease/sounds/bengdai.ogg` | JEDIGD | MIT | да |
| `sounds/tourniquet_1.ogg` (событие `tourniquet`) | Health & Disease 1.4.2: `assets/health_and_disease/sounds/guding.ogg` | JEDIGD | MIT | да |
| `sounds/bone_break_1.ogg` (событие `bone_break`) | Tactical Medicine 1.1.0: `assets/tacmed/sounds/fracture.ogg` | Lector | MIT | да |
| `sounds/pills_1.ogg` (событие `pills`) | Health & Disease 1.4.2: `assets/health_and_disease/sounds/result51.ogg` | JEDIGD | MIT | да |
| `sounds/heavy_breathing_1.ogg` (событие `heavy_breathing`) | Health & Disease 1.4.2: `assets/health_and_disease/sounds/xt2v0-t7xte.ogg` | JEDIGD | MIT | да |
| `sounds/heavy_breathing_2.ogg` (событие `heavy_breathing`) | Health & Disease 1.4.2: `assets/health_and_disease/sounds/en1te-bu0v4.ogg` | JEDIGD | MIT | да |
| `sounds/heavy_breathing_3.ogg` (событие `heavy_breathing`) | Health & Disease 1.4.2: `assets/health_and_disease/sounds/cl336-1jp1v.ogg` | JEDIGD | MIT | да |

## Текст лицензии MIT

```
MIT License

Copyright (c) Lector (Tactical Medicine)
Copyright (c) JEDIGD (Health & Disease)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
