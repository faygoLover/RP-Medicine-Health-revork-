package faygolover.rpmedicine.server;

import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.core.BodyPartState;
import faygolover.rpmedicine.core.Examination;
import faygolover.rpmedicine.core.GameplayEffects;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Physiology;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.network.SelfStatePacket;
import faygolover.rpmedicine.network.SelfView;
import net.minecraft.server.level.ServerPlayer;

/**
 * Собирает, что игрок знает о себе ({@link SelfView}), и отправляет только при изменении.
 * Цифры физиологии не уходят: только ступени ощущений и сила эффектов.
 */
public final class SelfSync {
    private SelfSync() {}

    public static SelfView build(MedicalState m, GameplayEffects.Mods mods, MedicalData d, MedicalSettings s, ServerPlayer sp) {
        SelfView v = new SelfView();
        v.down = (byte) m.down.ordinal();
        if (m.down == MedicalState.Down.KNOCKDOWN && !m.knockdownNoTimer && Physiology.lifeThreat(m, s)) {
            double rate = Physiology.knockdownBrainRate(m, s, Medical.traits(sp));
            v.knockdownSeconds = (short) Math.min(Short.MAX_VALUE, Math.ceil(m.brain / Math.max(1e-6, rate) / 5.0) * 5);
        }
        int bleed = 0;
        boolean fracture = false;
        for (BodyPartState ps : m.parts) {
            v.partColors[ps.part.ordinal()] = (byte) Examination.partColor(m, ps, s);
            bleed = Math.max(bleed, Examination.bleedClass(Physiology.partExternalBleed(m, ps, s), s));
            if (ps.hasFracture()) fracture = true;
            if (ps.hasTourniquet()) v.tourniquets.add(new int[]{ps.part.ordinal(), (int) (ps.tourniquetSeconds / 60)});
        }
        v.bleed = (byte) bleed;
        v.fracture = fracture;
        v.pain = (byte) (m.pain >= 85 ? 3 : m.pain >= 60 ? 2 : m.pain >= 30 ? 1 : 0);
        v.analgesia = Physiology.analgesia(m, s) > 0;
        v.dyspnea = m.spo2 < 90 || m.respRate > 24 || m.pneumo != MedicalState.Pneumo.NONE;

        double loss = 1 - m.bloodFraction(s);
        v.vignette = pct((m.pain - 50) / 50.0);
        v.blur = pct(Math.max((m.pain - 75) / 25.0, (m.concussion - 25) / 75.0));
        v.darken = pct((90 - m.pressure) / 50.0);
        v.tunnel = pct((loss - 0.15) / 0.3);
        v.gray = pct((s.dazedConsciousness - m.consciousness) / 30.0);
        v.ringing = pct((m.concussion - 15) / 60.0);
        v.heartbeat = (short) ((m.heartRate > 110 || (m.pressure < 80 && m.heart == MedicalState.Heart.NORMAL)) ? Math.round(m.heartRate / 5.0) * 5 : 0);
        v.heavyBreathing = m.respRate > 24 || m.spo2 < 90;
        v.sway = pct(mods.aimSway);

        v.noSprint = mods.noSprint || d.carrying;
        v.noJump = mods.noJump || d.carrying;
        v.crawl = mods.crawl;
        v.armsDisabled = mods.armsDisabled;
        v.carrying = d.carrying;
        v.carried = CarryService.isCarried(sp);
        if (!m.isDown()) v.sensations.addAll(Examination.complaints(m, s));
        return v;
    }

    private static byte pct(double f) {
        return (byte) Math.round(Math.max(0, Math.min(1, f)) * 20) ;
    }

    public static void sync(ServerPlayer sp, MedicalData d, MedicalState m, GameplayEffects.Mods mods) {
        SelfView v = build(m, mods, d, MedicalSettings.get(), sp);
        faygolover.rpmedicine.integration.voice.VoiceState.set(sp.getUUID(), v.down);
        if (v.equals(d.lastSelf)) return;
        d.lastSelf = v;
        Network.send(sp, new SelfStatePacket(v));
    }

    /** Принудительно отправить при входе. */
    public static void forceSync(ServerPlayer sp) {
        MedicalData d = Medical.data(sp);
        if (d == null) return;
        d.lastSelf = null;
        d.lastModsHash = 0;
        d.dirty = true;
    }
}
