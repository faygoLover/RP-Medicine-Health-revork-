package faygolover.rpmedicine.client.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель в формате Bedrock (как у GeckoLib и TaCZ): кости с точкой вращения и кубы с UV.
 * Координаты переводятся в «пространство Blockbench» (ось X отражена), как это делает GeckoLib,
 * поэтому модели и анимации из модов-референсов выглядят так же, как в них.
 */
public final class GeoModel {
    /** Четырёхугольник: 4 вершины (x, y, z в пикселях), UV в долях текстуры и нормаль. */
    public record Quad(float[][] pos, float[][] uv, float nx, float ny, float nz) {}

    public static final class Bone {
        public final String name;
        public final String parentName;
        /** Точка вращения в пикселях (X уже отражён). */
        public final float px, py, pz;
        /** Поворот покоя в радианах (X и Y уже с обратным знаком). */
        public final float rx, ry, rz;
        public final List<Quad> quads = new ArrayList<>();
        /** Кубы кости (x0, y0, z0, x1, y1, z1) — для рук игрока на месте *_pos (LR Tactical). */
        public final List<float[]> boxes = new ArrayList<>();
        /** Кубы со своим поворотом: группа квадов, точка и углы. */
        public final List<RotatedGroup> rotated = new ArrayList<>();
        public final List<Bone> children = new ArrayList<>();
        public Bone parent;

        Bone(String name, String parentName, float[] pivot, float[] rot) {
            this.name = name;
            this.parentName = parentName;
            this.px = -pivot[0];
            this.py = pivot[1];
            this.pz = pivot[2];
            this.rx = (float) Math.toRadians(-rot[0]);
            this.ry = (float) Math.toRadians(-rot[1]);
            this.rz = (float) Math.toRadians(rot[2]);
        }
    }

    public record RotatedGroup(float px, float py, float pz, float rx, float ry, float rz, List<Quad> quads) {}

    public final List<Bone> roots = new ArrayList<>();
    public final Map<String, Bone> bones = new HashMap<>();
    /** Границы модели в позе покоя (пиксели): minX, minY, minZ, maxX, maxY, maxZ. */
    public final float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

    public static GeoModel parse(JsonObject root) {
        GeoModel m = new GeoModel();
        JsonObject geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject desc = geo.has("description") ? geo.getAsJsonObject("description") : new JsonObject();
        float tw = desc.has("texture_width") ? desc.get("texture_width").getAsFloat() : 16;
        float th = desc.has("texture_height") ? desc.get("texture_height").getAsFloat() : 16;
        for (JsonElement be : geo.getAsJsonArray("bones")) {
            JsonObject b = be.getAsJsonObject();
            Bone bone = new Bone(b.get("name").getAsString(), b.has("parent") ? b.get("parent").getAsString() : null,
                    vec(b, "pivot"), vec(b, "rotation"));
            if (b.has("cubes")) for (JsonElement ce : b.getAsJsonArray("cubes")) m.addCube(bone, ce.getAsJsonObject(), tw, th);
            m.bones.put(bone.name, bone);
        }
        for (Bone b : m.bones.values()) {
            Bone p = b.parentName == null ? null : m.bones.get(b.parentName);
            if (p != null) {
                b.parent = p;
                p.children.add(b);
            } else {
                m.roots.add(b);
            }
        }
        return m;
    }

    private static float[] vec(JsonObject o, String key) {
        if (!o.has(key)) return new float[3];
        JsonArray a = o.getAsJsonArray(key);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private void addCube(Bone bone, JsonObject c, float tw, float th) {
        float[] o = vec(c, "origin");
        float[] s = vec(c, "size");
        float inf = c.has("inflate") ? c.get("inflate").getAsFloat() : 0;
        float x0 = -(o[0] + s[0]) - inf, y0 = o[1] - inf, z0 = o[2] - inf;
        float x1 = -o[0] + inf, y1 = o[1] + s[1] + inf, z1 = o[2] + s[2] + inf;
        bone.boxes.add(new float[]{x0, y0, z0, x1, y1, z1});
        List<Quad> out = new ArrayList<>();
        Map<String, float[]> faces = new HashMap<>();
        if (c.has("uv") && c.get("uv").isJsonArray()) {
            JsonArray uv = c.getAsJsonArray("uv");
            float u = uv.get(0).getAsFloat(), v = uv.get(1).getAsFloat();
            float w = (float) Math.floor(s[0]), h = (float) Math.floor(s[1]), d = (float) Math.floor(s[2]);
            boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
            faces.put("east", new float[]{u, v + d, d, h, 0});
            faces.put("north", new float[]{u + d, v + d, w, h, 0});
            faces.put("west", new float[]{u + d + w, v + d, d, h, 0});
            faces.put("south", new float[]{u + 2 * d + w, v + d, w, h, 0});
            faces.put("up", new float[]{u + d, v, w, d, 0});
            faces.put("down", new float[]{u + d + w, v + d, w, -d, 0});
            if (mirror) {
                float[] e = faces.get("east");
                faces.put("east", faces.get("west"));
                faces.put("west", e);
                for (float[] f : faces.values()) {
                    f[0] += f[2];
                    f[2] = -f[2];
                }
            }
        } else if (c.has("uv")) {
            for (Map.Entry<String, JsonElement> e : c.getAsJsonObject("uv").entrySet()) {
                JsonObject f = e.getValue().getAsJsonObject();
                JsonArray uv = f.getAsJsonArray("uv");
                JsonArray size = f.has("uv_size") ? f.getAsJsonArray("uv_size") : null;
                faces.put(e.getKey(), new float[]{uv.get(0).getAsFloat(), uv.get(1).getAsFloat(),
                        size == null ? 0 : size.get(0).getAsFloat(), size == null ? 0 : size.get(1).getAsFloat(),
                        f.has("uv_rotation") ? f.get("uv_rotation").getAsFloat() : 0});
            }
        }
        for (Map.Entry<String, float[]> e : faces.entrySet()) {
            float[] f = e.getValue();
            if (f[2] == 0 && f[3] == 0) continue;
            float[] uv = {f[0] / tw, f[1] / th, (f[0] + f[2]) / tw, (f[1] + f[3]) / th};
            Quad q = quad(e.getKey(), x0, y0, z0, x1, y1, z1, uv, (int) f[4]);
            if (q != null) out.add(q);
        }
        for (float[] p : new float[][]{{x0, y0, z0}, {x1, y1, z1}}) {
            for (int i = 0; i < 3; i++) {
                bounds[i] = Math.min(bounds[i], p[i]);
                bounds[i + 3] = Math.max(bounds[i + 3], p[i]);
            }
        }
        if (c.has("rotation")) {
            float[] r = vec(c, "rotation");
            float[] pv = c.has("pivot") ? vec(c, "pivot") : new float[3];
            bone.rotated.add(new RotatedGroup(-pv[0], pv[1], pv[2], (float) Math.toRadians(-r[0]), (float) Math.toRadians(-r[1]),
                    (float) Math.toRadians(r[2]), out));
        } else {
            bone.quads.addAll(out);
        }
    }

    /**
     * Границы в позе покоя по настоящим вершинам (с поворотами костей и кубов), без скрытых костей:
     * minX, minY, minZ, maxX, maxY, maxZ в пикселях.
     */
    public float[] restBounds(List<String> hide) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Bone r : roots) collect(r, new Matrix4f(), hide, b);
        if (b[0] > b[3]) return bounds;
        return b;
    }

    private static void collect(Bone bone, Matrix4f parent, List<String> hide, float[] b) {
        if (hide.contains(bone.name)) return;
        Matrix4f m = new Matrix4f(parent).translate(bone.px, bone.py, bone.pz)
                .rotateZ(bone.rz).rotateY(bone.ry).rotateX(bone.rx).translate(-bone.px, -bone.py, -bone.pz);
        for (Quad q : bone.quads) add(m, q, b);
        for (RotatedGroup g : bone.rotated) {
            Matrix4f gm = new Matrix4f(m).translate(g.px(), g.py(), g.pz()).rotateZ(g.rz()).rotateY(g.ry()).rotateX(g.rx())
                    .translate(-g.px(), -g.py(), -g.pz());
            for (Quad q : g.quads()) add(gm, q, b);
        }
        for (Bone c : bone.children) collect(c, m, hide, b);
    }

    private static void add(Matrix4f m, Quad q, float[] b) {
        for (float[] p : q.pos()) {
            Vector4f v = m.transform(new Vector4f(p[0], p[1], p[2], 1));
            b[0] = Math.min(b[0], v.x);
            b[1] = Math.min(b[1], v.y);
            b[2] = Math.min(b[2], v.z);
            b[3] = Math.max(b[3], v.x);
            b[4] = Math.max(b[4], v.y);
            b[5] = Math.max(b[5], v.z);
        }
    }

    /** Углы грани, как их видно снаружи: верх-лево, верх-право, низ-право, низ-лево. */
    private static Quad quad(String face, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int rot) {
        float[][] c;
        float nx = 0, ny = 0, nz = 0;
        switch (face) {
            case "north" -> { c = new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}}; nz = -1; }
            case "south" -> { c = new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}}; nz = 1; }
            case "east" -> { c = new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}}; nx = 1; }
            case "west" -> { c = new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}}; nx = -1; }
            case "up" -> { c = new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}; ny = 1; }
            case "down" -> { c = new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}; ny = -1; }
            default -> { return null; }
        }
        float[][] t = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
        for (int r = ((rot / 90) % 4 + 4) % 4; r > 0; r--) t = new float[][]{t[3], t[0], t[1], t[2]};
        return new Quad(c, t, nx, ny, nz);
    }
}
