"""Убрать мерцание (z-fighting) в моделях Bedrock: совпадающие грани двух кубов, смотрящие в одну сторону,
рисуются вперемешку (замечание живого теста 34: шприц-ручки, баллон). Грань меньшего куба сдвигаем внутрь
на EPS — на глаз не видно, мерцание пропадает. Повёрнутые кубы не трогаем."""
import itertools
import json

EPS = 0.02


def _box(c, bone):
    if c.get("rotation") and any(abs(r) > 1e-6 for r in c["rotation"]):
        return None
    if any(abs(r) > 1e-6 for r in (bone.get("rotation") or [0, 0, 0])):
        return None
    inf = c.get("inflate", 0)
    o, s = c["origin"], c["size"]
    return [o[i] - inf for i in range(3)], [o[i] + s[i] + inf for i in range(3)]


def fix(model):
    """Правит модель на месте; возвращает число сдвинутых граней."""
    cubes = []
    for g in model.get("minecraft:geometry", []):
        for b in g.get("bones", []):
            for c in b.get("cubes", []):
                box = _box(c, b)
                if box:
                    cubes.append((c, box))
    moved = 0
    for (ca, (a0, a1)), (cb, (b0, b1)) in itertools.combinations(cubes, 2):
        for ax in range(3):
            o = [i for i in range(3) if i != ax]
            if not all(min(a1[i], b1[i]) - max(a0[i], b0[i]) > 1e-4 for i in o):
                continue
            area_a = (a1[o[0]] - a0[o[0]]) * (a1[o[1]] - a0[o[1]])
            area_b = (b1[o[0]] - b0[o[0]]) * (b1[o[1]] - b0[o[1]])
            small, (s0, s1) = (ca, (a0, a1)) if area_a <= area_b else (cb, (b0, b1))
            for side in (0, 1):
                pa = a1[ax] if side else a0[ax]
                pb = b1[ax] if side else b0[ax]
                if abs(pa - pb) > 1e-4 or s1[ax] - s0[ax] <= 3 * EPS:
                    continue
                # Сдвиг грани внутрь: нижняя — origin вверх и size меньше, верхняя — только size меньше.
                if side == 0:
                    small["origin"][ax] = round(small["origin"][ax] + EPS, 5)
                    s0[ax] += EPS
                else:
                    s1[ax] -= EPS
                small["size"][ax] = round(small["size"][ax] - EPS, 5)
                moved += 1
    return moved


def fix_file(path):
    model = json.load(open(path, encoding="utf-8"))
    n = fix(model)
    if n:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(model, f, ensure_ascii=False, indent=2)
            f.write("\n")
    return n
