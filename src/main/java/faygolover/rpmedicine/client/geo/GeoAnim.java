package faygolover.rpmedicine.client.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Анимации в формате Bedrock: для каждой кости ключевые кадры поворота, сдвига и масштаба.
 * Выражения Molang не вычисляются (берётся число, если это число, иначе 0) — в анимациях предметов
 * референсов их почти нет.
 */
public final class GeoAnim {
    /** Ключевой кадр: время, значение до (pre) и после (post), сглаживание catmullrom. */
    record Key(float t, float[] pre, float[] post, boolean smooth) {}

    /** Канал: 0 — поворот, 1 — сдвиг, 2 — масштаб. */
    public static final class Clip {
        public float length;
        public final boolean loop;
        final Map<String, List<Key>[]> bones = new HashMap<>();

        Clip(float length, boolean loop) {
            this.length = length;
            this.loop = loop;
        }

        /** Значение канала кости в момент t или null, если канала нет. */
        public float[] sample(String bone, int channel, float t) {
            List<Key>[] ch = bones.get(bone);
            if (ch == null || ch[channel] == null || ch[channel].isEmpty()) return null;
            List<Key> keys = ch[channel];
            if (t <= keys.get(0).t) return keys.get(0).pre;
            for (int i = 1; i < keys.size(); i++) {
                Key b = keys.get(i);
                if (t <= b.t) {
                    Key a = keys.get(i - 1);
                    float k = b.t > a.t ? (t - a.t) / (b.t - a.t) : 1;
                    if (b.smooth || a.smooth) {
                        Key p0 = keys.get(Math.max(0, i - 2)), p3 = keys.get(Math.min(keys.size() - 1, i + 1));
                        return catmull(p0.post, a.post, b.pre, p3.pre, k);
                    }
                    return lerp(a.post, b.pre, k);
                }
            }
            return keys.get(keys.size() - 1).post;
        }
    }

    public final Map<String, Clip> clips = new HashMap<>();

    public static GeoAnim parse(JsonObject root) {
        GeoAnim out = new GeoAnim();
        JsonObject anims = root.getAsJsonObject("animations");
        for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
            JsonObject a = e.getValue().getAsJsonObject();
            float length = a.has("animation_length") ? a.get("animation_length").getAsFloat() : 0;
            boolean loop = a.has("loop") && a.get("loop").isJsonPrimitive() && a.get("loop").getAsJsonPrimitive().isBoolean()
                    && a.get("loop").getAsBoolean();
            Clip clip = new Clip(length, loop);
            float maxT = 0;
            if (a.has("bones")) {
                for (Map.Entry<String, JsonElement> be : a.getAsJsonObject("bones").entrySet()) {
                    JsonObject b = be.getValue().getAsJsonObject();
                    @SuppressWarnings("unchecked")
                    List<Key>[] ch = new List[3];
                    String[] names = {"rotation", "position", "scale"};
                    for (int i = 0; i < 3; i++) {
                        if (!b.has(names[i])) continue;
                        ch[i] = keys(b.get(names[i]), i == 2);
                        for (Key k : ch[i]) maxT = Math.max(maxT, k.t);
                    }
                    clip.bones.put(be.getKey(), ch);
                }
            }
            if (clip.length <= 0) clip.length = maxT;
            out.clips.put(e.getKey(), clip);
        }
        return out;
    }

    private static List<Key> keys(JsonElement el, boolean scale) {
        List<Key> out = new ArrayList<>();
        if (el.isJsonObject() && !isValue(el.getAsJsonObject())) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                float t;
                try {
                    t = Float.parseFloat(e.getKey());
                } catch (NumberFormatException ex) {
                    continue;
                }
                JsonElement v = e.getValue();
                boolean smooth = v.isJsonObject() && v.getAsJsonObject().has("lerp_mode")
                        && "catmullrom".equals(v.getAsJsonObject().get("lerp_mode").getAsString());
                float[] pre = value(v, "pre", scale), post = value(v, "post", scale);
                out.add(new Key(t, pre, post, smooth));
            }
            out.sort((a, b) -> Float.compare(a.t, b.t));
        } else {
            float[] v = value(el, "post", scale);
            out.add(new Key(0, v, v, false));
        }
        return out;
    }

    private static boolean isValue(JsonObject o) {
        return o.has("pre") || o.has("post") || o.has("vector");
    }

    private static float[] value(JsonElement v, String side, boolean scale) {
        if (v.isJsonObject()) {
            JsonObject o = v.getAsJsonObject();
            JsonElement x = o.has(side) ? o.get(side) : o.has("post") ? o.get("post") : o.has("pre") ? o.get("pre") : o.get("vector");
            return x == null ? new float[]{scale ? 1 : 0, scale ? 1 : 0, scale ? 1 : 0} : value(x, side, scale);
        }
        if (v.isJsonArray()) {
            JsonArray a = v.getAsJsonArray();
            float[] r = new float[3];
            for (int i = 0; i < 3; i++) r[i] = i < a.size() ? num(a.get(i)) : (scale ? 1 : 0);
            return r;
        }
        float f = num(v);
        return new float[]{f, f, f};
    }

    private static float num(JsonElement e) {
        try {
            return e.getAsFloat();
        } catch (Exception ex) {
            try {
                return Float.parseFloat(e.getAsString().trim());
            } catch (Exception ex2) {
                return 0;
            }
        }
    }

    private static float[] lerp(float[] a, float[] b, float k) {
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    private static float[] catmull(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
        float[] r = new float[3];
        float t2 = t * t, t3 = t2 * t;
        for (int i = 0; i < 3; i++) {
            r[i] = 0.5f * ((2 * p1[i]) + (-p0[i] + p2[i]) * t + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2
                    + (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t3);
        }
        return r;
    }
}
