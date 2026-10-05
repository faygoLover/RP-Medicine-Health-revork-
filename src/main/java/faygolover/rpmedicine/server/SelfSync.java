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
        v.fever = m.bodyTemp >= 37.8;
        var ns = faygolover.rpmedicine.core.MedicalSettings.get();
        for (int i = 0; i < faygolover.rpmedicine.core.Nutrition.COUNT; i++) v.nutrients[i] = (byte) Math.round(Math.min(120, m.nutrients[i]));
        v.nutrientLow = (byte) ns.nutrientLow;
        v.balancedMin = (byte) ns.balancedMin;
        v.balancedMax = (byte) Math.min(120, ns.balancedMax);
        v.kcalRecent = (short) Math.min(30000, Math.round(m.kcalEaten));
        v.cold = m.bodyTemp <= 35.5;
        v.nausea = m.nauseaSeconds > 0;
        v.concussion = m.concussion > 20;
        v.stabilized = m.stabilizedSeconds > 0;
        v.drip = m.bloodDripRemaining > 0 || m.salineDripRemaining > 0;
        for (BodyPartState ps : m.parts) if (ps.splint) v.splint = true;
        v.sedated = m.effect(faygolover.rpmedicine.core.DrugEffect.SEDATION) > 0;

        double loss = 1 - m.bloodFraction(s);
        v.vignette = pct((m.pain - 50) / 50.0);
        v.blur = pct(Math.max((m.pain - 75) / 25.0, (m.concussion - 25) / 75.0));
        v.darken = pct((90 - m.pressure) / 50.0);
        v.tunnel = pct((loss - 0.15) / 0.3);
        v.gray = pct((s.dazedConsciousness - m.consciousness) / 30.0);
        v.ringing = pct((m.concussion - 15) / 60.0);
        // Стук сердца — только ступень (1 — учащённый, 2 — частый, 3 — очень частый), без цифры пульса.
        boolean audible = m.heart == MedicalState.Heart.NORMAL && (m.heartRate > 110 || m.pressure < 80);
        v.heartbeat = (short) (!audible ? 0 : m.heartRate > 150 ? 3 : m.heartRate > 125 ? 2 : 1);
        v.heavyBreathing = m.respRate > 24 || m.spo2 < 90;
        v.sway = pct(mods.aimSway);
        // Морфин и опиаты из датапака, седация — мир «плывёт».
        double high = 0;
        if (m.morphineSeconds > 0 && m.morphineDelay <= 0) high = Math.max(high, m.morphineOverdoseSeconds > 0 ? 1.0 : 0.6);
        high = Math.max(high, Math.min(1.0, m.effect(faygolover.rpmedicine.core.DrugEffect.SEDATION) / 60.0));
        if (m.effect(faygolover.rpmedicine.core.DrugEffect.ANALGESIA) >= 30) high = Math.max(high, 0.35);
        v.high = pct(high);

        v.noSprint = mods.noSprint || d.carrying;
        v.noJump = mods.noJump || d.carrying;
        v.crawl = mods.crawl;
        v.armsDisabled = mods.armsDisabled;
        v.breakSpeedPct = (byte) Math.round(Math.max(0, Math.min(1, mods.breakSpeed)) * 100);
        v.useTimePct = (short) Math.round(Math.min(1000, mods.useTimeFactor * 100));
        v.medLevel = (byte) Medical.medicineLevel(sp);
        v.carrying = d.carrying;
        v.carried = CarryService.isCarried(sp);
        if (!m.isDown()) v.sensations.addAll(Examination.complaints(m, s));
        // Глухота после взрыва (второй этап, п. 13): секунды, не больше 127.
        v.deaf = (byte) Math.min(127, Math.ceil(m.deafSeconds));
        return v;
    }

    private static byte pct(double f) {
        return (byte) Math.round(Math.max(0, Math.min(1, f)) * 20) ;
    }

    public static void sync(ServerPlayer sp, MedicalData d, MedicalState m, GameplayEffects.Mods mods) {
        SelfView v = build(m, mods, d, MedicalSettings.get(), sp);
        MedicalSettings s = MedicalSettings.get();
        faygolover.rpmedicine.integration.voice.VoiceState.set(sp.getUUID(), faygolover.rpmedicine.core.Speech.of(m, s),
                faygolover.rpmedicine.core.Speech.breathlessness(m, s));
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
