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
        /** Множитель скорости ломания блоков. */
        public double breakSpeed = 1.0;
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
                    && useTimeFactor == 1.0 && attackFactor == 1.0 && breakSpeed == 1.0 && aimSway == 0 && staminaCap == 1.0
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
        // Снимают ограничения переломов только сильное обезболивание и укол адреналина. Свой адреналин после
        // ранения лишь глушит боль: иначе травма первые полминуты вообще не ощущалась бы.
        boolean masked = Physiology.analgesia(m, s) >= s.fractureMaskAnalgesia || m.adrenalineInjectionSeconds > 0;
        r.fractureMasked = masked;
        double suppress = Physiology.analgesia(m, s) + (Physiology.adrenalineActive(m) ? s.adrenalinePainSuppression : 0);

        // Ноги: каждая плохая нога или стопа — минус скорость, бег и прыжок недоступны.
        int badLower = 0;
        int brokenLegs = 0;
        int dislocatedLower = 0;
        int sorePartsLower = 0;
        boolean soreNoJump = false;
        for (BodyPart p : new BodyPart[]{BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG, BodyPart.RIGHT_FOOT, BodyPart.LEFT_FOOT}) {
            BodyPartState ps = m.part(p);
            boolean fracture = ps.hasFracture() && !masked;
            boolean weak = ps.integrity() < s.limbIntegrityThreshold;
            if (fracture || weak) badLower++;
            else if (ps.dislocated && !masked) dislocatedLower++;
            else {
                // Рана без перелома: нога болит — хромает (огнестрел, порез, ожог ноги).
                double legPain = Physiology.partPain(m, ps, s) - suppress;
                if (legPain >= s.legPainLimpThreshold) sorePartsLower++;
                if (legPain >= s.legPainNoJumpThreshold) soreNoJump = true;
            }
            if (p.kind == BodyPart.Kind.LEG && ps.hasFracture()) brokenLegs++;
        }
        // Вывих — как перелом, но слабее (второй этап, п. 7).
        double speed = 1.0 - badLower * s.speedPenaltyPerLeg - dislocatedLower * s.dislocationSpeedPenalty
                - sorePartsLower * s.legPainLimpPenalty;
        if (badLower > 0 || dislocatedLower > 0) {
            r.noSprint = true;
            r.noJump = true;
        }
        if (sorePartsLower > 0) r.noSprint = true;
        if (soreNoJump) r.noJump = true;
        if (brokenLegs >= 2 && !masked) {
            r.crawl = true;
            speed = Math.min(speed, s.crawlSpeedFactor);
        }

        // Боль.
        if (m.pain >= 30) r.aimSway = Math.max(r.aimSway, 0.3);
        if (m.pain >= 60) {
            speed -= s.painLimpSpeedPenalty + s.highPainSpeedPenalty;
            r.aimSway = Math.max(r.aimSway, 0.6);
            r.staminaRegen *= 0.5;
        } else if (m.pain >= 30) {
            // От 30 — хромота (п. 4.4 ТЗ).
            speed -= s.painLimpSpeedPenalty;
            r.staminaRegen *= 0.8;
        }
        // Оглушение.
        if (m.consciousness < s.dazedConsciousness) {
            r.dazed = true;
            speed -= s.dazedSpeedPenalty;
            r.aimSway = Math.max(r.aimSway, 0.5);
        }
        r.speed = Math.max(r.crawl ? 0.05 : s.minSpeedFactor, Math.min(1.0, speed));

        // Руки: рабочая влияет сильнее.
        BodyPart main = traits.workingArm();
        BodyPartState mainArm = m.part(main);
        BodyPartState offArm = m.part(main.mirror());
        // Под обезболиванием сломанная рука всё равно работает хуже — только мягче.
        double mainShare = armShare(mainArm, masked, s);
        double offShare = armShare(offArm, masked, s);
        r.mainArmBad = mainShare > 0;
        r.offArmBad = offShare > 0;
        r.armsDisabled = mainArm.hasFracture() && offArm.hasFracture() && !masked;
        if (r.mainArmBad) {
            r.useTimeFactor *= 1 + (s.armUseSlowMain - 1) * mainShare;
            r.attackFactor -= s.armAttackPenaltyMain * mainShare;
            r.breakSpeed *= 1 - (1 - s.armBreakSpeedMain) * mainShare;
            r.aimSway = Math.max(r.aimSway, 0.5 * mainShare);
        }
        if (r.offArmBad) {
            r.useTimeFactor *= 1 + (s.armUseSlowOff - 1) * offShare;
            r.attackFactor -= s.armAttackPenaltyOff * offShare;
            r.breakSpeed *= 1 - (1 - s.armBreakSpeedOff) * offShare;
            r.aimSway = Math.max(r.aimSway, 0.3 * offShare);
        }
        if (r.armsDisabled) {
            r.breakSpeed = Math.min(r.breakSpeed, s.armsDisabledBreakSpeed);
            r.attackFactor = Math.min(r.attackFactor, 0.2);
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

    /** Насколько рука плохая: 0 — здорова, 1 — полностью; под обезболиванием перелом и вывих — частично. */
    private static double armShare(BodyPartState arm, boolean masked, MedicalSettings s) {
        double share = 0;
        if (arm.hasFracture() || arm.dislocated) share = masked ? s.maskedArmPenaltyShare : 1.0;
        if (arm.integrity() < s.limbIntegrityThreshold || arm.ischemia > 30) share = 1.0;
        return share;
    }
}
