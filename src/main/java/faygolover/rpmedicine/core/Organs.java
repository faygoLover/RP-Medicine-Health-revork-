package faygolover.rpmedicine.core;

/**
 * Органы (ТЗ третьего этапа, п. 2): системный урон, симптомы по порогам, заживление.
 * Урон от ран — в {@link Injuries}; здесь — то, что идёт со временем.
 */
public final class Organs {
    private Organs() {}

    /** Шаг физиологии: системные причины (в сети) и последствия тяжёлых повреждений. */
    static void tick(MedicalState m, StepInput in, MedicalSettings s) {
        if (!s.organsEnabled) return;
        double hours = in.dt / 3600.0;
        if (in.online) {
            if (m.sepsis >= 30) for (Organ o : Organ.VALUES) m.damageOrgan(o, s.sepsisOrganPerHour * hours);
            if (m.spo2 < s.hypoxiaOrganSpo2 && m.heart == MedicalState.Heart.NORMAL) m.damageOrgan(Organ.HEART, s.hypoxiaHeartPerHour * hours);
            if (m.pressure < s.lowPressureKidneys && m.heart == MedicalState.Heart.NORMAL)
                m.damageOrgan(Organ.KIDNEYS, s.lowPressureKidneysPerHour * hours);
            m.damageOrgan(Organ.LIVER, m.effect(DrugEffect.LIVER_TOXICITY) * hours);
            // Отторжение: пока нет иммуносупрессии, пересаженный орган разрушается.
            if (m.organRejection != 0 && m.effect(DrugEffect.IMMUNOSUPPRESSION) <= 0)
                for (Organ o : Organ.VALUES) if ((m.organRejection & o.bit()) != 0) m.damageOrgan(o, s.rejectionPerHour * hours);
        }
        // Нет сердца — остановка; нет лёгких — не дышит (третий этап, п. 7.1).
        if (!m.hasOrgan(Organ.HEART) && m.heart == MedicalState.Heart.NORMAL) m.heart = MedicalState.Heart.ARREST;
        if (!m.hasOrgan(Organ.LUNGS)) m.respiratoryArrest = true;
        // Печень 100 % — кровь в живот.
        if (m.organ(Organ.LIVER) >= 100) {
            BodyPartState abdomen = m.part(BodyPart.ABDOMEN);
            abdomen.internalBleed = Math.max(abdomen.internalBleed, s.liverFailureInternalBleed);
        }
        // Почки: токсины бьют по мозгу. Без печени или почек — смерть мозга за полчаса.
        double brainLoss = 0;
        if (!m.hasOrgan(Organ.LIVER) || !m.hasOrgan(Organ.KIDNEYS)) brainLoss = s.missingOrganBrainPerHour;
        else if (m.organ(Organ.KIDNEYS) >= 100) brainLoss = s.kidneyBrainPerHour100;
        else if (m.organ(Organ.KIDNEYS) >= 80) brainLoss = s.kidneyBrainPerHour80;
        if (brainLoss > 0 && m.down != MedicalState.Down.CLINICAL) m.brain = Math.max(0, m.brain - brainLoss * hours);
    }

    /** Почки (≥ 80) или нет печени и почек: токсины не дают мозгу восстанавливаться. */
    static boolean toxicBrain(MedicalState m) {
        return !m.hasOrgan(Organ.LIVER) || !m.hasOrgan(Organ.KIDNEYS) || m.organ(Organ.KIDNEYS) >= 80;
    }

    /** Заживление за {@code dt} секунд в сети: органы ниже порога, кроме изъятых. */
    static void heal(MedicalState m, double dt, MedicalSettings s) {
        double amount = s.organHealPerHour * dt / 3600.0;
        for (Organ o : Organ.VALUES) {
            double v = m.organs[o.ordinal()];
            // Отторгаемый орган (без иммуносупрессора) сам не восстанавливается.
            boolean rejecting = (m.organRejection & o.bit()) != 0 && m.effect(DrugEffect.IMMUNOSUPPRESSION) <= 0;
            if (v > 0 && v < s.organSelfHealLimit && m.hasOrgan(o) && !rejecting) m.organs[o.ordinal()] = Math.max(0, v - amount);
        }
    }

    /** Лёгкие: насколько ниже потолок SpO2. */
    static double lungsSpo2Penalty(MedicalState m, MedicalSettings s) {
        double l = m.organ(Organ.LUNGS);
        if (l >= 80) return s.lungsSpo2Penalty80;
        if (l >= 50) return s.lungsSpo2Penalty50;
        return 0;
    }

    /** Печень: множитель кровотечения. */
    static double liverBleedFactor(MedicalState m, MedicalSettings s) {
        double l = m.organ(Organ.LIVER);
        if (l >= 80) return s.liverBleedFactor80;
        if (l >= 50) return s.liverBleedFactor50;
        return 1.0;
    }

    /** Сердце ≥ 80 — сердечная недостаточность: давление ниже. */
    static double heartPressurePenalty(MedicalState m, MedicalSettings s) {
        return m.organ(Organ.HEART) >= 80 ? s.heartFailurePressure : 0;
    }

    /** Боль органов части тела (грудь или живот). */
    static double pain(MedicalState m, BodyPart part, MedicalSettings s) {
        if (!part.isTorso() || !s.organsEnabled) return 0;
        double p = 0;
        for (Organ o : Organ.VALUES) {
            if (o.part != part || !m.hasOrgan(o)) continue;
            p = Math.max(p, m.organs[o.ordinal()] * s.organPainFactor);
        }
        return p;
    }

    /** Кишечник 100 % — перитонит, источник сепсиса. */
    public static boolean peritonitis(MedicalState m) {
        return m.hasOrgan(Organ.INTESTINES) && m.organs[Organ.INTESTINES.ordinal()] >= 100;
    }

    /** Самое тяжёлое повреждение органов (для сводок). */
    public static double worst(MedicalState m) {
        double w = 0;
        for (Organ o : Organ.VALUES) w = Math.max(w, m.organ(o));
        return w;
    }
}
