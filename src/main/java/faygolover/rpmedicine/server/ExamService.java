package faygolover.rpmedicine.server;

import faygolover.rpmedicine.core.BodyPartState;
import faygolover.rpmedicine.core.Examination;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Physiology;
import faygolover.rpmedicine.data.ItemRules;
import faygolover.rpmedicine.network.ExamResultPacket;
import faygolover.rpmedicine.network.HoverInfoPacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Осмотр (п. 7 ТЗ): панель себя или другого и краткая сводка при наведении. Сервер решает, что
 * видно при уровне медицины осматривающего, и присылает только изменения.
 */
public final class ExamService {
    private ExamService() {}

    public static final double PANEL_DISTANCE = 5.0;
    public static final double HOVER_DISTANCE = 8.0;

    private static final class Sub {
        int targetId;
        Object last;
    }

    private static final Map<UUID, Sub> PANELS = new HashMap<>();
    private static final Map<UUID, Sub> HOVERS = new HashMap<>();

    @Nullable
    private static LivingEntity resolve(ServerPlayer viewer, int targetId, double maxDist) {
        if (targetId < 0) return viewer;
        Entity e = viewer.level().getEntity(targetId);
        if (!(e instanceof LivingEntity le) || !Medical.isPatient(le) || le.isRemoved()) return null;
        if (le != viewer && viewer.distanceTo(le) > maxDist) return null;
        return le;
    }

    public static void request(ServerPlayer viewer, int targetId, boolean open) {
        if (!open) {
            PANELS.remove(viewer.getUUID());
            return;
        }
        LivingEntity target = resolve(viewer, targetId, PANEL_DISTANCE);
        if (target == null) return;
        Sub sub = new Sub();
        sub.targetId = target == viewer ? -1 : target.getId();
        PANELS.put(viewer.getUUID(), sub);
        sendExam(viewer, target, sub);
    }

    private static void sendExam(ServerPlayer viewer, LivingEntity target, Sub sub) {
        ExamResultPacket p = build(viewer, target);
        if (p == null) return;
        if (sub.last instanceof ExamResultPacket old && old.sameAs(p)) return;
        sub.last = p;
        Network.send(viewer, p);
    }

    @Nullable
    public static ExamResultPacket build(ServerPlayer viewer, LivingEntity target) {
        MedicalState m = Medical.state(target);
        if (m == null) return null;
        MedicalSettings s = MedicalSettings.get();
        boolean self = viewer == target;
        int level = Medical.medicineLevel(viewer);
        Examination.View view = Examination.examine(m, level, self, s);
        byte[] removable = new byte[9];
        for (BodyPartState ps : m.parts) {
            int b = 0;
            if (ps.anyDressing()) b |= 1;
            if (ps.hasTourniquet()) b |= 2;
            if (ps.splint) b |= 4;
            if (ps.occlusive) b |= 8;
            // Вправить вывих (второй этап): кнопка — тем, кто умеет (уровень 2+).
            if (ps.dislocated && level >= 2) b |= 16;
            removable[ps.part.ordinal()] = (byte) b;
        }
        // Заглушка — всегда лежачее тело (её можно обыскать, даже если обморок прошёл).
        byte down = (byte) (Medical.isDown(target) ? Math.max(1, m.down.ordinal()) : 0);
        // Полоски частей: целостность ступенями по 5; общая — среднее, голова и грудь вдвое весомее.
        byte[] bars = new byte[9];
        double sum = 0, weight = 0;
        for (BodyPartState ps : m.parts) {
            double integ = ps.integrity();
            bars[ps.part.ordinal()] = (byte) (Math.round(integ / 5.0) * 5);
            double w = ps.part == faygolover.rpmedicine.core.BodyPart.HEAD || ps.part == faygolover.rpmedicine.core.BodyPart.CHEST ? 2 : 1;
            sum += integ * w;
            weight += w;
        }
        double overall = sum / weight * Math.min(1.0, m.bloodFraction(s) / 0.9);
        boolean gm = viewer.hasPermissions(2) && viewer.isCreative();
        boolean numbers = gm || level >= s.numbersMinLevel;
        short kd = -1;
        if ((self || level >= 2 || gm) && m.down == MedicalState.Down.KNOCKDOWN && !m.knockdownNoTimer && Physiology.lifeThreat(m, s)) {
            double rate = Physiology.knockdownBrainRate(m, s, Medical.traits(target));
            kd = (short) Math.min(Short.MAX_VALUE, Math.ceil(m.brain / Math.max(1e-6, rate)));
        }
        byte gen = 0;
        if (m.intubated) gen |= 1;
        if (m.airway) gen |= 2;
        return new ExamResultPacket(self ? -1 : target.getId(), self, target.getDisplayName(), view, removable, down,
                bars, (byte) Math.round(overall), numbers, kd, gen);
    }

    // ------------------------------------------------------------------ наведение

    public static void hover(ServerPlayer viewer, int targetId) {
        if (targetId < 0) {
            Sub old = HOVERS.remove(viewer.getUUID());
            if (old != null) Network.send(viewer, new HoverInfoPacket(-1, List.of()));
            return;
        }
        Sub sub = new Sub();
        sub.targetId = targetId;
        HOVERS.put(viewer.getUUID(), sub);
        sendHover(viewer, sub);
    }

    private static void sendHover(ServerPlayer viewer, Sub sub) {
        List<Examination.Line> lines = hoverLines(viewer, sub.targetId);
        if (lines.equals(sub.last)) return;
        sub.last = lines;
        Network.send(viewer, new HoverInfoPacket(sub.targetId, lines));
    }

    /** Сводка при наведении: с медпредметом в руке, с уровня 1; время нокдауна — с уровня 2 (п. 7.3). */
    static List<Examination.Line> hoverLines(ServerPlayer viewer, int targetId) {
        List<Examination.Line> out = new ArrayList<>();
        int level = Medical.medicineLevel(viewer);
        if (level < 1) return out;
        if (ItemRules.specFor(viewer.getMainHandItem()) == null && ItemRules.specFor(viewer.getOffhandItem()) == null) return out;
        LivingEntity target = resolve(viewer, targetId, HOVER_DISTANCE);
        if (target == null || target == viewer) return out;
        MedicalState m = Medical.state(target);
        if (m == null) return out;
        MedicalSettings s = MedicalSettings.get();
        double bleed = m.totalExternalBleed(s);
        int bc = Examination.bleedClass(bleed, s);
        if (bc > 0) out.add(new Examination.Line(bc >= 3 ? "hover_bleeding_heavy" : "hover_bleeding"));
        switch (m.down) {
            case FAINT, KNOCKDOWN -> out.add(new Examination.Line("hover_unconscious"));
            case CLINICAL -> out.add(new Examination.Line("hover_no_signs"));
            default -> { }
        }
        for (BodyPartState ps : m.parts) {
            if (ps.hasTourniquet()) {
                out.add(new Examination.Line("hover_tourniquet", new int[]{(int) (ps.tourniquetSeconds / 60)}));
                break;
            }
        }
        if (level >= 2 && m.down == MedicalState.Down.KNOCKDOWN && !m.knockdownNoTimer && Physiology.lifeThreat(m, s)) {
            double rate = Physiology.knockdownBrainRate(m, s, Medical.traits(target));
            out.add(new Examination.Line("hover_knockdown", new int[]{(int) Math.ceil(m.brain / Math.max(1e-6, rate))}));
        }
        if (out.isEmpty()) out.add(new Examination.Line("hover_ok"));
        return out;
    }

    /** Каждые 10 тиков: обновить открытые панели и сводки. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        update(server, PANELS, true);
        update(server, HOVERS, false);
    }

    private static void update(MinecraftServer server, Map<UUID, Sub> subs, boolean panel) {
        Iterator<Map.Entry<UUID, Sub>> it = subs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Sub> e = it.next();
            ServerPlayer viewer = server.getPlayerList().getPlayer(e.getKey());
            if (viewer == null) {
                it.remove();
                continue;
            }
            Sub sub = e.getValue();
            if (panel) {
                LivingEntity target = resolve(viewer, sub.targetId, PANEL_DISTANCE + 1);
                if (target == null) {
                    it.remove();
                    continue;
                }
                sendExam(viewer, target, sub);
            } else {
                sendHover(viewer, sub);
            }
        }
    }

    /** Состояние цели изменилось — обновить её панель у всех, кто смотрит, сразу. */
    public static void refreshFor(ServerPlayer viewer) {
        Sub sub = PANELS.get(viewer.getUUID());
        if (sub == null) return;
        LivingEntity target = resolve(viewer, sub.targetId, PANEL_DISTANCE + 1);
        if (target != null) sendExam(viewer, target, sub);
    }

    public static void onLogout(ServerPlayer sp) {
        PANELS.remove(sp.getUUID());
        HOVERS.remove(sp.getUUID());
    }

    public static void clear() {
        PANELS.clear();
        HOVERS.clear();
    }
}
