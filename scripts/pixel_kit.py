# Свои модели предметов по сетке Minecraft (замечание 10.10: «текстуры не совпадают с сеткой»).
# У каждой грани куба — своя область текстуры ровно её размера: 1 текстель = 1 пиксель модели (или 1/2 при
# density=2 для мелких предметов). Цвет грани — палитра материала с пиксельным шумом, тёмная кромка по краям,
# светлее верх; поверх — «наклейки» (экран, цифры, надписи) — рисунок пикселями на нужной грани.
import math
import random

from PIL import Image

FACES = ("north", "south", "east", "west", "up", "down")


def _clamp(v):
    return max(0, min(255, int(round(v))))


class Material:
    """Палитра: основной цвет, разброс, кромка (темнее), блик (светлее), прозрачность."""

    def __init__(self, color, noise=0.05, edge=0.78, top=1.08, alpha=255, speck=None):
        self.color = color
        self.noise = noise
        self.edge = edge
        self.top = top
        self.alpha = alpha
        self.speck = speck  # (цвет, доля) — редкие пятнышки другого цвета (царапины, ворс)

    def texel(self, rnd, edge, face):
        k = 1.0 + (rnd.random() * 2 - 1) * self.noise
        if edge:
            k *= self.edge
        if face == "up":
            k *= self.top
        c = self.color
        if self.speck and rnd.random() < self.speck[1]:
            c = self.speck[0]
        return (_clamp(c[0] * k), _clamp(c[1] * k), _clamp(c[2] * k), self.alpha)


def face_size(frm, to, face):
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


class Px:
    def __init__(self, name, density=1, seed=None):
        self.name = name
        self.d = density
        self.els = []          # (from, to, material, faces, rot, decals)
        self.rnd = random.Random(seed if seed is not None else name)

    def box(self, frm, to, mat, faces="nsewud", rot=None, decals=None, edge=True):
        """decals: {грань: функция(img, x0, y0, w, h)} — дорисовать поверх (координаты области грани в атласе)."""
        names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
        self.els.append((list(frm), list(to), mat, [names[c] for c in faces], rot, decals or {}, edge))

    # ------------------------------------------------------------------ сборка
    def build(self):
        """Атлас и элементы JSON. Возвращает (картинка, elements)."""
        regions = []
        for ei, (frm, to, mat, faces, rot, decals, edge) in enumerate(self.els):
            for f in faces:
                w, h = face_size(frm, to, f)
                tw, th = max(1, round(w * self.d)), max(1, round(h * self.d))
                regions.append((ei, f, tw, th))
        # Полки: широкие сверху.
        order = sorted(range(len(regions)), key=lambda i: (-regions[i][3], -regions[i][2]))
        W = 16
        while True:
            pos, x, y, shelf_h, ok = {}, 0, 0, 0, True
            for i in order:
                _, _, tw, th = regions[i]
                if tw > W:
                    ok = False
                    break
                if x + tw > W:
                    x, y, shelf_h = 0, y + shelf_h, 0
                pos[i] = (x, y)
                x += tw
                shelf_h = max(shelf_h, th)
            H = y + shelf_h
            if ok and H <= W:
                break
            W *= 2
        img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        px = img.load()
        uv_of = {}
        for i, (ei, f, tw, th) in enumerate(regions):
            x0, y0 = pos[i]
            frm, to, mat, faces, rot, decals, edge = self.els[ei]
            for yy in range(th):
                for xx in range(tw):
                    e = edge and (xx == 0 or yy == 0 or xx == tw - 1 or yy == th - 1) and tw > 2 and th > 2
                    px[x0 + xx, y0 + yy] = mat.texel(self.rnd, e, f)
            if f in decals:
                decals[f](img, x0, y0, tw, th)
            k = 16.0 / W
            uv_of[(ei, f)] = [round(x0 * k, 4), round(y0 * k, 4), round((x0 + tw) * k, 4), round((y0 + th) * k, 4)]
        elements = []
        for ei, (frm, to, mat, faces, rot, decals, edge) in enumerate(self.els):
            el = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to],
                  "faces": {f: {"uv": uv_of[(ei, f)], "texture": "#t"} for f in faces}}
            if rot:
                el["rotation"] = {"origin": list(rot[0]), "axis": rot[1], "angle": rot[2]}
            elements.append(el)
        return img, elements


# ------------------------------------------------------------------ наклейки
def fill(color):
    def f(img, x0, y0, w, h):
        for y in range(h):
            for x in range(w):
                img.putpixel((x0 + x, y0 + y), color + (255,) if len(color) == 3 else color)
    return f


def screen(bg, fg, pattern):
    """Экран: рамка темнее, внутри фон и рисунок pattern — список строк ('#' — fg)."""
    def f(img, x0, y0, w, h):
        for y in range(h):
            for x in range(w):
                c = bg
                if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                    c = tuple(int(v * 0.55) for v in bg)
                img.putpixel((x0 + x, y0 + y), c + (255,))
        ph = len(pattern)
        pw = max(len(r) for r in pattern) if pattern else 0
        ox, oy = x0 + (w - pw) // 2, y0 + (h - ph) // 2
        for yy, row in enumerate(pattern):
            for xx, ch in enumerate(row):
                if ch == "#" and 0 <= ox + xx - x0 < w and 0 <= oy + yy - y0 < h:
                    img.putpixel((ox + xx, oy + yy), fg + (255,))
    return f


def pattern(rows, palette):
    """Рисунок по строкам: символ -> цвет из palette ('.' — не трогать). Растягивается по центру области."""
    def f(img, x0, y0, w, h):
        ph, pw = len(rows), max(len(r) for r in rows)
        ox, oy = x0 + (w - pw) // 2, y0 + (h - ph) // 2
        for yy, row in enumerate(rows):
            for xx, ch in enumerate(row):
                if ch in palette and 0 <= ox + xx - x0 < w and 0 <= oy + yy - y0 < h:
                    c = palette[ch]
                    img.putpixel((ox + xx, oy + yy), c + (255,) if len(c) == 3 else c)
    return f


def stripes(color, every=2, horizontal=True):
    """Насечка (рукоять, рифление): каждая every-я строка или столбец темнее."""
    def f(img, x0, y0, w, h):
        for y in range(h):
            for x in range(w):
                if (y if horizontal else x) % every == 0:
                    img.putpixel((x0 + x, y0 + y), color + (255,))
    return f


def both(*fs):
    def f(img, x0, y0, w, h):
        for g in fs:
            g(img, x0, y0, w, h)
    return f
