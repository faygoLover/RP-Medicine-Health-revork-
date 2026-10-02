package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.MedicalState.Down;

/**
 * Последствия травм в игре (п. 2.4, 4.4 ТЗ): скорость, бег, прыжок, руки, выносливость.
 * Чистый расчёт; слой Minecraft применяет результат через атрибуты и события.
 */
public final class GameplayEffects {
    private GameplayEffects() {}

    public static final class Mods {
        /** Итоговый множитель скорости ходьбы. */
        public double speed = 1.0;
        public boolean noSprint;
        public boolean noJump;
        /** Только ползком (обе ноги сломаны). */
        public boolean crawl;
        public boolean mainArmBad;
        public boolean offArmBad;
        /** Обе руки сломаны: нельзя стрелять и лечить. */
        public boolean armsDisabled;
        /** Множитель времени использования предметов и перезарядки (больше — дольше). */
        public double useTimeFactor = 1.0;
        /** Множитель урона в ближнем бою. */
        public double attackFactor = 1.0;
        /** Раскачка прицела 0–1. */
        public double aimSway;
        /** Потолок выносливости 0–1 и множитель восстановления. */
        public double staminaCap = 1.0;
        public double staminaRegen = 1.0;
        public boolean dazed;
        /** Ограничения переломов временно сняты обезболиванием или адреналином. */
        public boolean fractureMasked;

        public boolean isDefault() {
            return speed == 1.0 && !noSprint && !noJump && !crawl && !mainArmBad && !offArmBad && !armsDisabled
                    && useTimeFactor == 1.0 && attackFactor == 1.0 && aimSway == 0 && staminaCap == 1.0
                    && staminaRegen == 1.0 && !dazed;
        }
    }

    public static boolean limbImpaired(BodyPartState ps, MedicalSettings s) {
        return ps.hasFracture() || ps.integrity() < s.limbIntegrityThreshold;
    }

    public static Mods compute(MedicalState m, PatientTraits traits, MedicalSettings s) {
        Mods r = new Mods();
        if (m.down != Down.NONE) {
            r.speed = 0;
            r.noSprint = true;
            r.noJump = true;
            r.armsDisabled = true;
            return r;
        }
        boolean masked = Physiology.analgesia(m, s) >= s.painkillerStrength || Physiology.adrenalineActive(m);
        r.fractureMasked = masked;

        // Ноги: каждая плохая нога или стопа — минус скорость, бег и прыжок недоступны.
        int badLower = 0;
        int brokenLegs = 0;
        for (BodyPart p : new BodyPart[]{BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG, BodyPart.RIGHT_FOOT, BodyPart.LEFT_FOOT}) {
            BodyPartState ps = m.part(p);
            boolean fracture = ps.hasFracture() && !masked;
            boolean weak = ps.integrity() < s.limbIntegrityThreshold;
            if (fracture || weak) badLower++;
            if (p.kind == BodyPart.Kind.LEG && ps.hasFracture()) brokenLegs++;
        }
        double speed = 1.0 - badLower * s.speedPenaltyPerLeg;
        if (badLower > 0) {
            r.noSprint = true;
            r.noJump = true;
        }
        if (brokenLegs >= 2 && !masked) {
            r.crawl = true;
            speed = Math.min(speed, s.crawlSpeedFactor);
        }

        // Боль.
        if (m.pain >= 30) r.aimSway = Math.max(r.aimSway, 0.3);
        if (m.pain >= 60) {
            speed -= s.highPainSpeedPenalty;
            r.aimSway = Math.max(r.aimSway, 0.6);
            r.staminaRegen *= 0.5;
        } else if (m.pain >= 30) {
            r.staminaRegen *= 0.8;
        }
        // Оглушение.
        if (m.consciousness < s.dazedConsciousness) {
            r.dazed = true;
            speed -= s.dazedSpeedPenalty;
            r.aimSway = Math.max(r.aimSway, 0.5);
        }
        r.speed = Math.max(r.crawl ? 0.05 : s.minSpeedFactor, Math.min(1.0, speed));
        if (badLower == 0 && !r.crawl && r.speed >= 1.0) r.speed = 1.0;

        // Руки: рабочая влияет сильнее.
        BodyPart main = traits.workingArm();
        BodyPartState mainArm = m.part(main);
        BodyPartState offArm = m.part(main.mirror());
        r.mainArmBad = (mainArm.hasFracture() && !masked) || mainArm.integrity() < s.limbIntegrityThreshold || mainArm.ischemia > 30;
        r.offArmBad = (offArm.hasFracture() && !masked) || offArm.integrity() < s.limbIntegrityThreshold || offArm.ischemia > 30;
        r.armsDisabled = mainArm.hasFracture() && offArm.hasFracture() && !masked;
        if (r.mainArmBad) {
            r.useTimeFactor *= s.armUseSlowMain;
            r.attackFactor -= s.armAttackPenaltyMain;
            r.aimSway = Math.max(r.aimSway, 0.5);
        }
        if (r.offArmBad) {
            r.useTimeFactor *= s.armUseSlowOff;
            r.attackFactor -= s.armAttackPenaltyOff;
            r.aimSway = Math.max(r.aimSway, 0.3);
        }
        if (m.postClinicalSeconds > 0) r.aimSway = Math.max(r.aimSway, 0.4);
        r.attackFactor = Math.max(0.2, r.attackFactor);

        // Выносливость: SpO2 и кровопотеря режут потолок, рёбра и грудь — тоже.
        double cap = 1.0;
        if (m.spo2 < 90) cap = Math.min(cap, 1.0 - (90 - m.spo2) * 0.03);
        double loss = 1 - m.bloodFraction(s);
        if (loss >= 0.15) cap = Math.min(cap, 1.0 - (loss - 0.05) * 1.5);
        if (m.part(BodyPart.CHEST).hasFracture()) cap *= 0.8;
        if (m.pneumo != MedicalState.Pneumo.NONE) cap *= 0.7;
        if (m.postClinicalSeconds > 0) cap *= 0.6;
        r.staminaCap = Math.max(0.1, Math.min(1.0, cap));
        if (m.postClinicalSeconds > 0) r.staminaRegen *= 0.6;
        return r;
    }
}
