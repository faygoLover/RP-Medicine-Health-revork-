package faygolover.rpmedicine.capability;

import faygolover.rpmedicine.core.BloodType;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.DrugEffect;
import faygolover.rpmedicine.core.BodyPartState;
import faygolover.rpmedicine.core.Dressing;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Wound;
import faygolover.rpmedicine.core.WoundType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Компактное сохранение {@link MedicalState} в NBT: здоровые части и нулевые таймеры не пишутся. */
public final class MedicalNbt {
    private MedicalNbt() {}

    private static final int VERSION = 2;

    public static CompoundTag write(MedicalState m) {
        CompoundTag t = new CompoundTag();
        t.putInt("v", VERSION);
        t.putFloat("weight", (float) m.weightKg);
        t.putFloat("height", (float) m.heightCm);
        t.putFloat("blood", (float) m.bloodVolume);
        putIf(t, "saline", m.saline);
        putIf(t, "dripLeft", m.salineDripRemaining);
        putIf(t, "dripRate", m.salineDripRate);
        if (m.heart != MedicalState.Heart.NORMAL) t.putByte("heart", (byte) m.heart.ordinal());
        putIf(t, "fibSec", m.fibrillationSeconds);
        t.putFloat("bp", (float) m.pressure);
        t.putFloat("hr", (float) m.heartRate);
        t.putFloat("rr", (float) m.respRate);
        t.putFloat("spo2", (float) m.spo2);
        if (m.respiratoryArrest) t.putBoolean("apnea", true);
        if (m.airway) t.putBoolean("airway", true);
        putIf(t, "adrenaline", m.adrenalineSeconds);
        putIf(t, "adrenalineInj", m.adrenalineInjectionSeconds);
        putIf(t, "painkiller", m.painkillerSeconds);
        putIf(t, "painkillerDelay", m.painkillerDelay);
        putIf(t, "morphine", m.morphineSeconds);
        putIf(t, "morphineDelay", m.morphineDelay);
        putIf(t, "morphineOd", m.morphineOverdoseSeconds);
        putIf(t, "txa", m.txaSeconds);
        putIf(t, "shock", m.shockAccum);
        putIf(t, "shockLimit", m.shockLimit);
        if (m.painShock) t.putBoolean("painShock", true);
        putIf(t, "pain", m.pain);
        putIf(t, "rawPain", m.rawPain);
        t.putFloat("cons", (float) m.consciousness);
        t.putFloat("brain", (float) m.brain);
        putIf(t, "concussion", m.concussion);
        putIf(t, "concussionKo", m.concussionKoSeconds);
        putIf(t, "postClinical", m.postClinicalSeconds);
        if (m.pneumo != MedicalState.Pneumo.NONE) t.putByte("pneumo", (byte) m.pneumo.ordinal());
        putIf(t, "pneumoTimer", m.pneumoTimer);
        putIf(t, "tension", m.tensionProgress);
        if (m.down != MedicalState.Down.NONE) t.putByte("down", (byte) m.down.ordinal());
        if (m.wakeSeconds >= 0) t.putFloat("wake", (float) m.wakeSeconds);
        if (m.knockdownNoTimer) t.putBoolean("noTimer", true);
        putIf(t, "healBoost", m.healBoostSeconds);
        // Второй этап
        putIf(t, "sepsis", m.sepsis);
        if (Math.abs(m.bodyTemp - MedicalSettings.get().normalBodyTemp) > 0.001) t.putFloat("temp", (float) m.bodyTemp);
        putIf(t, "spoiled", m.spoiledBloodSeconds);
        if (m.bloodType != null) t.putByte("bloodType", (byte) m.bloodType.ordinal());
        if (m.bloodDripRemaining > 0) {
            t.putFloat("bDrip", (float) m.bloodDripRemaining);
            t.putFloat("bDripRate", (float) m.bloodDripRate);
            if (m.bloodDripType != null) t.putByte("bDripType", (byte) m.bloodDripType.ordinal());
            if (m.bloodDripSpoiled) t.putBoolean("bDripSpoiled", true);
        }
        putIf(t, "reaction", m.transfusionReactionSeconds);
        if (!m.effects.isEmpty()) {
            ListTag effects = new ListTag();
            for (var e : m.effects.entrySet()) {
                CompoundTag et = new CompoundTag();
                et.putByte("e", (byte) e.getKey().ordinal());
                et.putFloat("s", (float) e.getValue().strength);
                putIf(et, "d", e.getValue().delay);
                et.putFloat("t", (float) e.getValue().seconds);
                effects.add(et);
            }
            t.put("effects", effects);
        }

        ListTag parts = new ListTag();
        for (BodyPartState ps : m.parts) {
            if (ps.isHealthy()) continue;
            CompoundTag p = new CompoundTag();
            p.putByte("p", (byte) ps.part.ordinal());
            ListTag ws = new ListTag();
            for (Wound w : ps.wounds) {
                CompoundTag wt = new CompoundTag();
                wt.putByte("t", (byte) w.type.ordinal());
                wt.putFloat("s", (float) w.severity);
                wt.putFloat("peak", (float) w.peakSeverity);
                if (w.dressing != Dressing.NONE) {
                    wt.putByte("d", (byte) w.dressing.ordinal());
                    wt.putFloat("dq", (float) w.dressingQuality);
                    wt.putFloat("da", (float) w.dressingAge);
                }
                putIf(wt, "clot", w.clot);
                if (w.bandageBoost) wt.putBoolean("boost", true);
                if (w.infectionStage != Wound.Infection.NEW) wt.putByte("inf", (byte) w.infectionStage.ordinal());
                putIf(wt, "infT", w.infectionTimer);
                putIf(wt, "infV", w.infection);
                putIf(wt, "imm", w.immuneProgress);
                if (w.infectionRisk != 1.0) wt.putFloat("risk", (float) w.infectionRisk);
                ws.add(wt);
            }
            if (!ws.isEmpty()) p.put("w", ws);
            if (ps.fracture != BodyPartState.Fracture.NONE) p.putByte("fr", (byte) ps.fracture.ordinal());
            if (ps.splint) {
                p.putBoolean("splint", true);
                p.putFloat("splintQ", (float) ps.splintQuality);
            }
            putIf(p, "frHeal", ps.fractureHeal);
            if (ps.bullets > 0) p.putByte("bullets", (byte) Math.min(127, ps.bullets));
            if (ps.fragments > 0) p.putByte("frags", (byte) Math.min(127, ps.fragments));
            putIf(p, "fbSec", ps.foreignBodySeconds);
            if (ps.arterial) p.putBoolean("art", true);
            putIf(p, "internal", ps.internalBleed);
            if (ps.tourniquet != BodyPartState.Tourniquet.NONE) {
                p.putByte("tq", (byte) ps.tourniquet.ordinal());
                p.putFloat("tqSec", (float) ps.tourniquetSeconds);
            }
            putIf(p, "isch", ps.ischemia);
            if (ps.occlusive) p.putBoolean("occl", true);
            parts.add(p);
        }
        if (!parts.isEmpty()) t.put("parts", parts);
        return t;
    }

    public static void read(MedicalState m, CompoundTag t, MedicalSettings s) {
        m.reset(s);
        if (t.isEmpty()) return;
        if (t.contains("weight")) m.weightKg = t.getFloat("weight");
        if (t.contains("height")) m.heightCm = t.getFloat("height");
        if (m.weightKg <= 0) m.weightKg = s.defaultWeightKg;
        if (m.heightCm <= 0) m.heightCm = s.defaultHeightCm;
        m.bloodVolume = t.contains("blood") ? t.getFloat("blood") : m.normalBlood(s);
        m.saline = t.getFloat("saline");
        m.salineDripRemaining = t.getFloat("dripLeft");
        m.salineDripRate = t.getFloat("dripRate");
        m.heart = MedicalState.Heart.byOrdinal(t.getByte("heart"));
        m.fibrillationSeconds = t.getFloat("fibSec");
        if (t.contains("bp")) m.pressure = t.getFloat("bp");
        if (t.contains("hr")) m.heartRate = t.getFloat("hr");
        if (t.contains("rr")) m.respRate = t.getFloat("rr");
        if (t.contains("spo2")) m.spo2 = t.getFloat("spo2");
        m.respiratoryArrest = t.getBoolean("apnea");
        m.airway = t.getBoolean("airway");
        m.adrenalineSeconds = t.getFloat("adrenaline");
        m.adrenalineInjectionSeconds = t.getFloat("adrenalineInj");
        m.painkillerSeconds = t.getFloat("painkiller");
        m.painkillerDelay = t.getFloat("painkillerDelay");
        m.morphineSeconds = t.getFloat("morphine");
        m.morphineDelay = t.getFloat("morphineDelay");
        m.morphineOverdoseSeconds = t.getFloat("morphineOd");
        m.txaSeconds = t.getFloat("txa");
        m.shockAccum = t.getFloat("shock");
        m.shockLimit = t.getFloat("shockLimit");
        m.painShock = t.getBoolean("painShock");
        m.pain = t.getFloat("pain");
        m.rawPain = t.getFloat("rawPain");
        if (t.contains("cons")) m.consciousness = t.getFloat("cons");
        if (t.contains("brain")) m.brain = t.getFloat("brain");
        m.concussion = t.getFloat("concussion");
        m.concussionKoSeconds = t.getFloat("concussionKo");
        m.postClinicalSeconds = t.getFloat("postClinical");
        m.pneumo = MedicalState.Pneumo.byOrdinal(t.getByte("pneumo"));
        m.pneumoTimer = t.getFloat("pneumoTimer");
        m.tensionProgress = t.getFloat("tension");
        m.down = MedicalState.Down.byOrdinal(t.getByte("down"));
        m.wakeSeconds = t.contains("wake") ? t.getFloat("wake") : -1;
        m.knockdownNoTimer = t.getBoolean("noTimer");
        m.healBoostSeconds = t.getFloat("healBoost");
        m.sepsis = t.getFloat("sepsis");
        if (t.contains("temp")) m.bodyTemp = t.getFloat("temp");
        m.spoiledBloodSeconds = t.getFloat("spoiled");
        m.bloodType = t.contains("bloodType") ? BloodType.byOrdinal(t.getByte("bloodType")) : null;
        m.bloodDripRemaining = t.getFloat("bDrip");
        m.bloodDripRate = t.getFloat("bDripRate");
        m.bloodDripType = t.contains("bDripType") ? BloodType.byOrdinal(t.getByte("bDripType")) : null;
        m.bloodDripSpoiled = t.getBoolean("bDripSpoiled");
        m.transfusionReactionSeconds = t.getFloat("reaction");
        ListTag effects = t.getList("effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < effects.size(); i++) {
            CompoundTag et = effects.getCompound(i);
            m.effects.put(DrugEffect.byOrdinal(et.getByte("e")), new DrugEffect.Active(et.getFloat("s"), et.getFloat("d"), et.getFloat("t")));
        }

        ListTag parts = t.getList("parts", Tag.TAG_COMPOUND);
        for (int i = 0; i < parts.size(); i++) {
            CompoundTag p = parts.getCompound(i);
            BodyPartState ps = m.part(BodyPart.byOrdinal(p.getByte("p")));
            ListTag ws = p.getList("w", Tag.TAG_COMPOUND);
            for (int j = 0; j < ws.size(); j++) {
                CompoundTag wt = ws.getCompound(j);
                Wound w = new Wound(WoundType.byOrdinal(wt.getByte("t")), wt.getFloat("s"));
                w.peakSeverity = wt.contains("peak") ? wt.getFloat("peak") : w.severity;
                w.dressing = Dressing.byOrdinal(wt.getByte("d"));
                w.dressingQuality = wt.contains("dq") ? wt.getFloat("dq") : 1.0;
                w.dressingAge = wt.getFloat("da");
                w.clot = wt.getFloat("clot");
                w.bandageBoost = wt.getBoolean("boost");
                w.infectionStage = Wound.Infection.byOrdinal(wt.getByte("inf"));
                w.infectionTimer = wt.getFloat("infT");
                w.infection = wt.getFloat("infV");
                w.immuneProgress = wt.getFloat("imm");
                w.infectionRisk = wt.contains("risk") ? wt.getFloat("risk") : 1.0;
                if (w.severity > 0 && ps.wounds.size() < 16) ps.wounds.add(w);
            }
            ps.fracture = BodyPartState.Fracture.byOrdinal(p.getByte("fr"));
            ps.splint = p.getBoolean("splint");
            ps.splintQuality = p.contains("splintQ") ? p.getFloat("splintQ") : 1.0;
            ps.fractureHeal = p.getFloat("frHeal");
            ps.bullets = p.getByte("bullets");
            ps.fragments = p.getByte("frags");
            ps.foreignBodySeconds = p.getFloat("fbSec");
            ps.arterial = p.getBoolean("art");
            ps.internalBleed = p.getFloat("internal");
            ps.tourniquet = BodyPartState.Tourniquet.byOrdinal(p.getByte("tq"));
            ps.tourniquetSeconds = p.getFloat("tqSec");
            ps.ischemia = p.getFloat("isch");
            ps.occlusive = p.getBoolean("occl");
        }
    }

    private static void putIf(CompoundTag t, String key, double v) {
        if (v != 0) t.putFloat(key, (float) v);
    }
}
