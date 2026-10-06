package faygolover.rpmedicine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.client.ClientState;
import faygolover.rpmedicine.hospital.BedPose;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Живой лежачий (обморок, нокдаун, клиническая смерть, наркоз) на всех клиентах лежит на спине, как тело
 * офлайн-игрока, а не «плывёт» лицом вниз. Падает назад с ускорением, встаёт обратно.
 * <p>
 * На клиенте у лежачего поза обычная (стоя), а модель поворачиваем сами; размер и высоту глаз задаём
 * через {@link EntityEvent.Size}, чтобы камера лежачего была у земли. На сервере поза остаётся
 * горизонтальной (поза плавания) — от неё зависит, куда попадают выстрелы.
 */
@Mod.EventBusSubscriber(modid = RpMedicine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DownedPose {
    private DownedPose() {}

    /** Падение назад, мс. */
    private static final long FALL_MS = 650;
    /** Подъём, мс. */
    private static final long RISE_MS = 500;
    /** Середина тела — в точке игрока, как у тела офлайн-игрока и у хитбокса сервера. */
    private static final double HALF_BODY = 0.9;
    /** Подъём над землёй, чтобы спина не уходила в блок. */
    private static final double LIFT = 0.14;

    private static final class State {
        float yaw;
        boolean down;
        long startMs;
        float from;

        float progress(long now) {
            if (down) {
                float t = Math.min(1f, (now - startMs) / (float) FALL_MS);
                return from + (1f - from) * t * t;
            }
            float t = Math.min(1f, (now - startMs) / (float) RISE_MS);
            float e = 1f - (1f - t) * (1f - t);
            return from * (1f - e);
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<Integer, float[]> SAVED = new HashMap<>();
    /** Сдвиг и поворот, которые применили к модели (для ника — снять их обратно). */
    private static final Map<Integer, Object[]> APPLIED = new HashMap<>();

    /** Лежит ли игрок на этом клиенте: лежачий или на койке (на койке — вдоль неё). */
    public static boolean isDowned(Player p) {
        return ClientState.DOWNED.contains(p.getId()) || BedPose.CLIENT_ON_BED.contains(p.getId());
    }

    private static float yawFor(Player p, float fallback) {
        Float bed = BedPose.CLIENT_BED_YAW.get(p.getId());
        return bed != null ? bed : fallback;
    }

    /** Каждый тик клиента: начать падение или подъём, обновить размеры. */
    public static void tick(Minecraft mc) {
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        for (Player p : mc.level.players()) {
            boolean d = isDowned(p);
            State s = STATES.get(p.getId());
            if (d && (s == null || !s.down)) {
                State n = new State();
                n.from = s == null ? 0f : s.progress(now);
                n.yaw = yawFor(p, s == null ? p.yBodyRot : s.yaw);
                n.down = true;
                n.startMs = now;
                STATES.put(p.getId(), n);
                p.refreshDimensions();
            } else if (!d && s != null && s.down) {
                s.from = s.progress(now);
                s.down = false;
                s.startMs = now;
                p.refreshDimensions();
            }
        }
        Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            State s = en.getValue();
            boolean gone = mc.level.getEntity(en.getKey()) == null;
            if (gone || (!s.down && now - s.startMs > RISE_MS)) it.remove();
        }
    }

    public static void reset() {
        STATES.clear();
        SAVED.clear();
        APPLIED.clear();
    }

    /** Лежачий на клиенте низкий, глаза у земли. */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof Player p) || !p.level().isClientSide) return;
        if (e.getPose() != Pose.STANDING || !isDowned(p)) return;
        e.setNewSize(EntityDimensions.scalable(0.6f, 0.6f));
        e.setNewEyeHeight(0.35f);
    }

    @SubscribeEvent
    public static void onRenderPre(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        State s = STATES.get(p.getId());
        if (s == null) return;
        float pr = s.progress(System.currentTimeMillis());
        if (pr <= 0.001f) return;

        // Тело не крутится вслед за взглядом лежачего; голова лежит ровно.
        SAVED.put(p.getId(), new float[]{p.yBodyRot, p.yBodyRotO, p.yHeadRot, p.yHeadRotO, p.getXRot(), p.xRotO});
        p.yBodyRot = s.yaw;
        p.yBodyRotO = s.yaw;
        p.yHeadRot = lerpDeg(p.yHeadRot, s.yaw, pr);
        p.yHeadRotO = lerpDeg(p.yHeadRotO, s.yaw, pr);
        p.setXRot(p.getXRot() * (1f - pr));
        p.xRotO = p.xRotO * (1f - pr);

        double y = Math.toRadians(s.yaw);
        double fx = -Math.sin(y), fz = Math.cos(y);
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        float tx = (float) (fx * HALF_BODY * pr), ty = (float) (LIFT * pr), tz = (float) (fz * HALF_BODY * pr);
        ps.translate(tx, ty, tz);
        // Поворот вокруг оси «влево-вправо» тела на −90°: верх тела уходит назад.
        Quaternionf q = new Quaternionf().rotationAxis((float) Math.toRadians(-90.0 * pr), (float) Math.cos(y), 0f, (float) Math.sin(y));
        ps.mulPose(q);
        APPLIED.put(p.getId(), new Object[]{new org.joml.Vector3f(tx, ty, tz), q, pr});
    }

    /**
     * Ник лежачего: ванильный рисуется внутри повёрнутой модели и ложится на живот боком (замечание 4).
     * Рисуем сами: снимаем наш поворот и ставим ник низко над телом, лицом к камере.
     */
    @SubscribeEvent
    public static void onNameTag(net.minecraftforge.client.event.RenderNameTagEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Object[] ap = APPLIED.get(p.getId());
        if (ap == null) return;
        e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player || p.isInvisible() || mc.options.hideGui) return;
        if (mc.getEntityRenderDispatcher().distanceToSqr(p) > 32 * 32) return;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        Quaternionf q = (Quaternionf) ap[1];
        org.joml.Vector3f t = (org.joml.Vector3f) ap[0];
        ps.mulPose(new Quaternionf(q).conjugate());
        ps.translate(-t.x, -t.y, -t.z);
        // Над серединой лежащего тела, чуть выше груди.
        float pr = (Float) ap[2];
        double y = Math.toRadians(STATES.containsKey(p.getId()) ? STATES.get(p.getId()).yaw : p.yBodyRot);
        ps.translate(-Math.sin(y) * HALF_BODY * pr, 0.75 + (1 - pr) * 1.3, Math.cos(y) * HALF_BODY * pr);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.025f, -0.025f, 0.025f);
        var font = mc.font;
        net.minecraft.network.chat.Component name = e.getContent();
        float x = -font.width(name) / 2f;
        int bg = (int) (mc.options.getBackgroundOpacity(0.25f) * 255f) << 24;
        var mat = ps.last().pose();
        font.drawInBatch(name, x, 0, 0x20FFFFFF, false, mat, e.getMultiBufferSource(),
                net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, bg, e.getPackedLight());
        font.drawInBatch(name, x, 0, -1, false, mat, e.getMultiBufferSource(), net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, e.getPackedLight());
        ps.popPose();
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post e) {
        APPLIED.remove(e.getEntity().getId());
        float[] r = SAVED.remove(e.getEntity().getId());
        if (r == null) return;
        Player p = e.getEntity();
        p.yBodyRot = r[0];
        p.yBodyRotO = r[1];
        p.yHeadRot = r[2];
        p.yHeadRotO = r[3];
        p.setXRot(r[4]);
        p.xRotO = r[5];
        e.getPoseStack().popPose();
    }

    private static float lerpDeg(float from, float to, float t) {
        float d = ((to - from) % 360f + 540f) % 360f - 180f;
        return from + d * t;
    }
}
