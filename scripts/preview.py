# Предпросмотр моделей предметов без игры: JSON-модели Minecraft (elements) и Bedrock geo (наш client/geo) -> PNG.
# Нужен, чтобы проверять новые модели глазами и собирать листы «до/после». В игре модели рисует сама игра.
#   python scripts/preview.py out.png rpmedicine:item/bandage [rpmedicine:rpgeo/scissors.geo.json ...]
# Геометрия — как в игре (Java-координаты 0–16, у Bedrock x зеркален), освещение упрощённое.
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS_ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets")
REL = os.path.join(HERE, "..", "docs", "reference", "родственные моды")


# ------------------------------------------------------------------ поиск файлов по ResourceLocation
def _find(ns, rel):
    p = os.path.join(ASSETS_ROOT, ns, rel)
    if os.path.exists(p):
        return p
    for mod in os.listdir(REL):
        q = os.path.join(REL, mod, "assets", ns, rel)
        if os.path.exists(q):
            return q
        q = os.path.join(REL, mod, ns, rel)
        if os.path.exists(q):
            return q
    raise FileNotFoundError(f"{ns}:{rel}")


def split(rl, default_ns="minecraft"):
    return rl.split(":", 1) if ":" in rl else (default_ns, rl)


def texture(rl):
    ns, path = split(rl)
    im = Image.open(_find(ns, f"textures/{path}.png")).convert("RGBA")
    w, h = im.size
    if h > w and h % w == 0:
        im = im.crop((0, 0, w, w))   # анимированная текстура — первый кадр
    return np.asarray(im).astype(np.float32) / 255.0


# ------------------------------------------------------------------ JSON-модели
def load_model(rl):
    ns, path = split(rl)
    d = json.load(open(_find(ns, f"models/{path}.json"), encoding="utf-8"))
    if "parent" in d and not d["parent"].startswith(("builtin/", "minecraft:builtin/")):
        pns, ppath = split(d["parent"])
        if ppath not in ("item/generated", "item/handheld", "block/block", "block/cube"):
            try:
                base = load_model(d["parent"])
            except FileNotFoundError:
                base = {}
            tex = dict(base.get("textures", {}))
            tex.update(d.get("textures", {}))
            merged = dict(base)
            merged.update({k: v for k, v in d.items() if k not in ("parent",)})
            merged["textures"] = tex
            if "elements" not in d and "elements" in base:
                merged["elements"] = base["elements"]
            return merged
    return d


def resolve_tex(textures, key):
    seen = 0
    while key.startswith("#") and seen < 10:
        key = textures.get(key[1:], "")
        seen += 1
    return key


# Углы грани (TL, TR, BR, BL) по from/to — как в Minecraft.
def face_corners(f, t, face):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def default_uv(f, t, face):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
        "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0],
    }[face]


def rot_point(p, origin, axis, angle):
    a = math.radians(angle)
    x, y, z = (p[0] - origin[0], p[1] - origin[1], p[2] - origin[2])
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + origin[0], y + origin[1], z + origin[2])


def json_quads(model):
    """Квады: (4 точки, 4 uv 0..1, текстура)."""
    quads = []
    texs = model.get("textures", {})
    cache = {}
    for el in model.get("elements", []):
        f, t = el["from"], el["to"]
        r = el.get("rotation")
        for face, fd in el.get("faces", {}).items():
            key = resolve_tex(texs, fd.get("texture", ""))
            if not key:
                continue
            if key not in cache:
                cache[key] = texture(key)
            uv = fd.get("uv") or default_uv(f, t, face)
            u0, v0, u1, v1 = [c / 16.0 for c in uv]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            k = (fd.get("rotation", 0) // 90) % 4
            uvs = uvs[k:] + uvs[:k]
            pts = face_corners(f, t, face)
            if r:
                pts = [rot_point(p, r["origin"], r["axis"], r["angle"]) for p in pts]
            quads.append((pts, uvs, cache[key]))
    return quads


# ------------------------------------------------------------------ Bedrock geo
def _mat(rx, ry, rz):
    """Поворот кости или куба Bedrock (градусы) в координатах Bedrock: как у GeckoLib и нашего рендерера
    (в Java-пространстве Rz(z)·Ry(-y)·Rx(-x) при зеркальном x) — здесь Rz(-z)·Ry(y)·Rx(-x), сначала X."""
    rx, ry, rz = map(math.radians, (-rx, ry, -rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Y = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Z = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Z @ Y @ X


HANDS = {"lefthand", "righthand", "lefthand_pos", "righthand_pos"}


def geo_quads(path, tex_path, hide=()):
    d = json.load(open(path, encoding="utf-8"))
    g = d["minecraft:geometry"][0]
    tw = g["description"].get("texture_width", 64)
    th = g["description"].get("texture_height", 64)
    tex = np.asarray(Image.open(tex_path).convert("RGBA")).astype(np.float32) / 255.0
    bones = {b["name"]: b for b in g.get("bones", [])}

    def chain(name):
        out = []
        while name:
            b = bones[name]
            out.append(b)
            name = b.get("parent")
        return out

    def to_world(p, bone):
        v = np.array(p, dtype=float)
        for b in chain(bone["name"]):
            piv = np.array(b.get("pivot", [0, 0, 0]), dtype=float)
            rot = b.get("rotation")
            if rot:
                # Bedrock: x зеркальный относительно Java, поэтому знаки X и Y поворота — как у нашего рендерера.
                v = _mat(*rot) @ (v - piv) + piv
        return v

    quads = []
    for b in g.get("bones", []):
        names = [x["name"] for x in chain(b["name"])]
        if any(n in hide or n in HANDS for n in names):
            continue
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            inf = c.get("inflate", 0)
            x0, y0, z0 = o[0] - inf, o[1] - inf, o[2] - inf
            x1, y1, z1 = o[0] + s[0] + inf, o[1] + s[1] + inf, o[2] + s[2] + inf
            w, h, dd = s
            uv = c.get("uv", [0, 0])
            faces = {}
            if isinstance(uv, dict):
                for fn, fd in uv.items():
                    u, v = fd["uv"]
                    uw, vh = fd.get("uv_size", [0, 0])
                    faces[fn] = (u, v, u + uw, v + vh)
            else:
                u, v = uv
                faces = {"east": (u, v + dd, u + dd, v + dd + h), "north": (u + dd, v + dd, u + dd + w, v + dd + h),
                         "west": (u + dd + w, v + dd, u + 2 * dd + w, v + dd + h), "south": (u + 2 * dd + w, v + dd, u + 2 * dd + 2 * w, v + dd + h),
                         "up": (u + dd, v, u + dd + w, v + dd), "down": (u + dd + w, v + dd, u + dd + 2 * w, v)}
                if c.get("mirror"):
                    faces["east"], faces["west"] = faces["west"], faces["east"]
                    faces = {k: (f[2], f[1], f[0], f[3]) for k, f in faces.items()}
            crot = c.get("rotation")
            cpiv = c.get("pivot", [0, 0, 0])
            # Углы в координатах Bedrock: north = -z.
            corners = {
                "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
                "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
                "east": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
                "west": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
                "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
                "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
            }
            for fn, (a, bb, cc, dd2) in faces.items():
                if fn not in corners or (a == cc and bb == dd2):
                    continue
                pts = corners[fn]
                if crot:
                    m = _mat(*crot)
                    pts = [tuple(m @ (np.array(p) - cpiv) + cpiv) for p in pts]
                pts = [tuple(to_world(p, b)) for p in pts]
                # Java: x = -x Bedrock; центр блока — x 8, z 8.
                pts = [(8 - p[0], p[1], p[2] + 8) for p in pts]
                uvs = [(a / tw, bb / th), (cc / tw, bb / th), (cc / tw, dd2 / th), (a / tw, dd2 / th)]
                quads.append((pts, uvs, tex))
    return quads


# ------------------------------------------------------------------ растр
def render(quads, size=256, yaw=-135.0, pitch=30.0, bg=(46, 48, 54, 255)):
    img = np.zeros((size, size, 4), dtype=np.float32)
    img[:] = np.array(bg) / 255.0
    zbuf = np.full((size, size), np.inf)
    if not quads:
        return Image.fromarray((img * 255).astype(np.uint8))
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def cam(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp + z * sp, -y * sp + z * cp
        return np.array([x, y, z])

    allp = np.array([cam(p) for q in quads for p in q[0]])
    mn, mx = allp.min(0), allp.max(0)
    span = max(mx[0] - mn[0], mx[1] - mn[1]) or 1
    k = size * 0.86 / span
    cx, cyy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
    light = np.array([0.35, 0.8, -0.5])
    light /= np.linalg.norm(light)
    ys, xs = np.mgrid[0:size, 0:size]
    for pts, uvs, tex in quads:
        P = np.array([cam(p) for p in pts])
        n = np.cross(P[1] - P[0], P[3] - P[0])
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n /= nn
        shade = 0.55 + 0.45 * max(0.0, float(-n @ light) if n[2] < 0 else float(n @ light))
        # Камера смотрит в +z: справа у неё −x (иначе картинка зеркальная).
        S = np.stack([size / 2 - (P[:, 0] - cx) * k, size / 2 - (P[:, 1] - cyy) * k, P[:, 2]], 1)
        th, tw = tex.shape[:2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = S[list(tri)]
            ua, ub, uc = (np.array(uvs[i]) for i in tri)
            x0, x1 = int(max(0, math.floor(min(a[0], b[0], c[0])))), int(min(size - 1, math.ceil(max(a[0], b[0], c[0]))))
            y0, y1 = int(max(0, math.floor(min(a[1], b[1], c[1])))), int(min(size - 1, math.ceil(max(a[1], b[1], c[1]))))
            if x1 < x0 or y1 < y0:
                continue
            det = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(det) < 1e-9:
                continue
            X = xs[y0:y1 + 1, x0:x1 + 1] + 0.5
            Y = ys[y0:y1 + 1, x0:x1 + 1] + 0.5
            l1 = ((b[1] - c[1]) * (X - c[0]) + (c[0] - b[0]) * (Y - c[1])) / det
            l2 = ((c[1] - a[1]) * (X - c[0]) + (a[0] - c[0]) * (Y - c[1])) / det
            l3 = 1 - l1 - l2
            inside = (l1 >= -1e-4) & (l2 >= -1e-4) & (l3 >= -1e-4)
            if not inside.any():
                continue
            z = l1 * a[2] + l2 * b[2] + l3 * c[2]
            u = l1 * ua[0] + l2 * ub[0] + l3 * uc[0]
            v = l1 * ua[1] + l2 * ub[1] + l3 * uc[1]
            tx = np.clip((u * tw).astype(int), 0, tw - 1)
            ty = np.clip((v * th).astype(int), 0, th - 1)
            col = tex[ty, tx]
            zb = zbuf[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (col[..., 3] > 0.1) & (z < zb)
            if not ok.any():
                continue
            zb[ok] = z[ok]
            dst = img[y0:y1 + 1, x0:x1 + 1]
            al = col[..., 3:4]
            rgb = col[..., :3] * shade
            dst[ok, :3] = rgb[ok] * al[ok] + dst[ok, :3] * (1 - al[ok])
            dst[ok, 3] = al[ok][:, 0] + dst[ok, 3] * (1 - al[ok][:, 0])
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def quads_for(spec):
    """spec: ResourceLocation JSON-модели или путь к .geo.json (текстура — рядом в textures/geo/<имя>.png)."""
    if ".geo.json" in spec:
        if ":" in spec and not os.path.exists(spec):
            ns, path = split(spec)
            gpath = _find(ns, path)
            name = os.path.basename(path)[:-len(".geo.json")]
            tex = _find(ns, f"textures/geo/{name}.png")
        else:
            gpath, tex = spec.split("|") if "|" in spec else (spec, None)
        return geo_quads(gpath, tex)
    return json_quads(load_model(spec))


def sheet(out, specs, size=200, labels=True, views=((-135, 30), (-45, 20)), cols=3):
    """Лист: модели сеткой по cols в ряд, у каждой несколько ракурсов и подпись."""
    tiles = []
    for s_ in specs:
        q = quads_for(s_)
        t = Image.new("RGBA", (size * len(views), size + (14 if labels else 0)), (30, 32, 36, 255))
        for i, (yw, pt) in enumerate(views):
            t.paste(render(q, size, yw, pt), (i * size, 14 if labels else 0))
        if labels:
            ImageDraw.Draw(t).text((4, 1), s_.split(":")[-1].replace("item/", "").replace(".geo.json", ""), fill=(230, 230, 230, 255))
        tiles.append(t)
    tw, th = tiles[0].size
    rows = (len(tiles) + cols - 1) // cols
    im = Image.new("RGBA", (tw * min(cols, len(tiles)) + 4 * cols, (th + 4) * rows), (20, 20, 24, 255))
    for i, t in enumerate(tiles):
        im.paste(t, ((i % cols) * (tw + 4), (i // cols) * (th + 4)))
    im.save(out)
    return out


if __name__ == "__main__":
    sheet(sys.argv[1], sys.argv[2:])
