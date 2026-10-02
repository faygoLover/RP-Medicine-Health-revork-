package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.MedicalState.Down;
import faygolover.rpmedicine.core.MedicalState.Heart;
import org.junit.jupiter.api.Test;

import static faygolover.rpmedicine.core.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Второй этап: инфекция ран, сепсис, температура, группы крови, переливание, эффекты лекарств. */
class InfectionAndBloodTest {

    private static MedicalSettings alwaysInfect() {
        MedicalSettings s = settings();
        for (WoundType t : WoundType.VALUES) s.infectionChance.put(t, 1.0);
        s.infectionChance.put(WoundType.BRUISE, 0.0);
        return s;
    }

    /** Ожог на руке: не кровит и заживает долго — удобно смотреть на инфекцию отдельно от кровопотери. */
    private static Wound burnOnArm(MedicalState m, double severity) {
        Wound w = new Wound(WoundType.BURN, severity);
        m.part(BodyPart.LEFT_ARM).wounds.add(w);
        return w;
    }

    // ------------------------------------------------------------------ инфекция

    @Test
    void woundIsCheckedWithinAnHourAndInfectedWoundDoesNotHeal() {
        MedicalSettings s = alwaysInfect();
        MedicalState m = new MedicalState(s);
        Wound w = burnOnArm(m, 30);
        run(m, input(1), s, 60);
        assertEquals(Wound.Infection.PENDING, w.infectionStage, "рана ждёт проверки");
        assertTrue(w.infectionTimer >= 20 * 60 - 61 && w.infectionTimer <= 60 * 60, "проверка через 20–60 минут");
        double sev = w.severity;
        run(m, input(1), s, 3600);
        assertEquals(Wound.Infection.INFECTED, w.infectionStage, "с шансом 1 рана заразилась");
        double sevInfected = w.severity;
        run(m, input(2), s, 1800);
        assertEquals(sevInfected, w.severity, 1e-9, "заражённая рана не заживает");
        assertTrue(w.pain(s) >= Math.min(100, s.painPer(WoundType.BURN) * w.severity + s.infectionPain) - 1e-9, "заражённая рана болит сильнее");
        assertTrue(sev >= sevInfected);
    }

    @Test
    void bruiseNeverInfectsAndDressingLowersChance() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        Wound bruise = new Wound(WoundType.BRUISE, 30);
        m.part(BodyPart.CHEST).wounds.add(bruise);
        run(m, input(3), s, 10);
        assertEquals(Wound.Infection.CLEAN, bruise.infectionStage);
        Wound cut = new Wound(WoundType.CUT, 30);
        StepInput in = input(1);
        double open = Infections.infectionChance(cut, in, s);
        cut.dressing = Dressing.BANDAGE;
        double dressed = Infections.infectionChance(cut, in, s);
        assertEquals(0.10, open, 1e-9, "порез 10 %");
        assertEquals(0.10 * 0.2, dressed, 1e-9, "повязка ×(1 − 0,8)");
        in.infectionRiskFactor = s.bedInfectionFactor;
        cut.infectionRisk = s.antisepticInfectionFactor;
        assertEquals(0.10 * 0.2 * 0.3 * 0.7, Infections.infectionChance(cut, in, s), 1e-9, "антисептик и койка");
    }

    @Test
    void untreatedInfectionWinsTheRaceAndFeedsSepsis() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        Wound w = burnOnArm(m, 40);
        w.infectionStage = Wound.Infection.INFECTED;
        w.infection = 5;
        run(m, input(4), s, 25 * 3600);
        assertTrue(w.isInfected() && w.infection >= 100, "без лечения инфекция дошла до 100 раньше иммунитета");
        assertTrue(m.sepsis > 0, "рана с инфекцией 100 % питает сепсис");
    }

    @Test
    void antibioticClearsInfectionAndSepsis() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        Wound w = burnOnArm(m, 40);
        w.infectionStage = Wound.Infection.INFECTED;
        w.infection = 100;
        m.sepsis = 40;
        m.addEffect(DrugEffect.ANTIBIOTIC, 10, 0, 30 * 3600);
        run(m, input(5), s, 20 * 3600);
        assertFalse(w.isInfected(), "под антибиотиком рана очистилась");
        assertEquals(0, m.sepsis, 1e-6, "сепсис спал под антибиотиком");
    }

    @Test
    void immuneResponseCanWinWhenInfectionIsSlowed() {
        MedicalSettings s = settings();
        s.infectionGrowthPerHour = 1;
        MedicalState m = new MedicalState(s);
        Wound w = burnOnArm(m, 20);
        w.infectionStage = Wound.Infection.INFECTED;
        w.infection = 5;
        run(m, input(6), s, 34 * 3600);
        assertFalse(w.isInfected(), "иммунитет дошёл до 100 первым");
    }

    // ------------------------------------------------------------------ сепсис и температура

    @Test
    void sepsisLowersPressureRaisesTemperatureAndShockKnocksDown() {
        MedicalSettings s = settings();
        s.sepsisFibrillationPerHour = 0; // аритмию проверяет отдельный тест
        MedicalState m = new MedicalState(s);
        m.sepsis = 65; // тяжёлый сепсис без лечения держится
        run(m, input(7), s, 1800);
        assertTrue(m.pressure < s.normalPressure - s.sepsisPressureDrop60 + 5, "сепсис 60 %: давление ниже, было " + m.pressure);
        assertTrue(m.bodyTemp > 37.8, "лихорадка, было " + m.bodyTemp);
        assertTrue(m.heartRate > s.normalHeartRate + 10, "пульс чаще, было " + m.heartRate);

        MedicalState shock = new MedicalState(s);
        shock.sepsis = 100;
        run(shock, input(8), s, 30);
        assertEquals(Down.KNOCKDOWN, shock.down, "септический шок — нокдаун с таймером");
        assertTrue(Physiology.lifeThreat(shock, s));
    }

    @Test
    void severeSepsisCanCauseFibrillation() {
        MedicalSettings s = settings();
        s.sepsisFibrillationPerHour = 20;
        MedicalState m = new MedicalState(s);
        m.sepsis = 70;
        double t = runUntil(m, input(16), s, 3600, st -> st.heart != Heart.NORMAL);
        assertTrue(t > 0, "при сепсисе от 60 % сердце может сорваться в фибрилляцию");
    }

    @Test
    void mildSepsisResolvesAloneSevereDoesNot() {
        MedicalSettings s = settings();
        MedicalState mild = new MedicalState(s);
        MedicalState severe = new MedicalState(s);
        mild.sepsis = 20;
        severe.sepsis = 40;
        run(mild, input(17), s, 3 * 3600);
        run(severe, input(17), s, 3 * 3600);
        assertTrue(mild.sepsis < 18, "лёгкий спадает сам");
        assertEquals(40, severe.sepsis, 1e-9, "тяжёлый держится без антибиотика");
    }

    @Test
    void antipyreticLowersFeverButNotSepsis() {
        MedicalSettings s = settings();
        MedicalState a = new MedicalState(s);
        MedicalState b = new MedicalState(s);
        a.sepsis = b.sepsis = 50;
        b.addEffect(DrugEffect.ANTIPYRETIC, 0.8, 0, 3600);
        run(a, input(9), s, 1800);
        run(b, input(9), s, 1800);
        assertTrue(b.bodyTemp < a.bodyTemp - 1, "жаропонижающее снимает жар: " + a.bodyTemp + " → " + b.bodyTemp);
        assertTrue(b.sepsis >= a.sepsis - 1e-9, "причину не лечит");
    }

    // ------------------------------------------------------------------ кровь

    @Test
    void bloodTypeCompatibility() {
        for (BloodType r : BloodType.VALUES) {
            assertTrue(BloodType.O_NEG.canDonateTo(r), "O− всем");
            assertTrue(r.canDonateTo(BloodType.AB_POS), "AB+ от всех");
        }
        assertFalse(BloodType.A_POS.canDonateTo(BloodType.O_POS));
        assertFalse(BloodType.O_POS.canDonateTo(BloodType.A_NEG), "резус + не переливать резус −");
        assertTrue(BloodType.A_NEG.canDonateTo(BloodType.AB_NEG));
        assertFalse(BloodType.B_POS.canDonateTo(BloodType.A_POS));
        assertEquals(BloodType.AB_NEG, BloodType.byId("AB−").orElseThrow());
    }

    @Test
    void randomBloodTypeFollowsWeights() {
        MedicalSettings s = settings();
        java.util.Random rnd = new java.util.Random(11);
        int[] counts = new int[8];
        for (int i = 0; i < 20000; i++) counts[BloodType.random(s.bloodTypeWeights, rnd).ordinal()]++;
        assertEquals(0.35, counts[BloodType.O_POS.ordinal()] / 20000.0, 0.02, "O+ около 35 %");
        assertEquals(0.01, counts[BloodType.AB_NEG.ordinal()] / 20000.0, 0.01, "AB− около 1 %");
    }

    @Test
    void compatibleTransfusionRestoresOxygenCarryingBlood() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.bloodType = BloodType.A_POS;
        m.bloodVolume = 3000;
        m.saline = 600;
        double capBefore = m.oxygenCapacity(s);
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.BLOOD_BAG, s));
        Treatments.apply(m, BodyPart.CHEST, TreatmentAction.BLOOD_BAG, false, new java.util.Random(1), s,
                new Treatments.Bag(BloodType.O_NEG, 450, false));
        assertEquals("already_dripping", Treatments.check(m, BodyPart.CHEST, TreatmentAction.BLOOD_BAG, s));
        run(m, input(10), s, s.transfusionSeconds + 10);
        assertEquals(0, m.bloodDripRemaining, 1e-6, "пакет докапал");
        assertTrue(m.oxygenCapacity(s) > capBefore + 0.08, "настоящая кровь несёт кислород");
        assertEquals(0, m.transfusionReactionSeconds, "совместимая — без реакции");
    }

    @Test
    void incompatibleTransfusionCausesReactionThatOutlastsTheDrip() {
        MedicalSettings s = settings();
        s.transfusionReactionArrestChance = 0;
        MedicalState m = new MedicalState(s);
        m.bloodType = BloodType.O_POS;
        m.bloodVolume = 3800;
        Treatments.apply(m, BodyPart.CHEST, TreatmentAction.BLOOD_BAG, false, new java.util.Random(1), s,
                new Treatments.Bag(BloodType.A_POS, 450, false));
        run(m, input(11), s, 120);
        assertTrue(m.transfusionReactionSeconds > 0, "реакция началась");
        assertTrue(m.rawPain >= s.transfusionReactionPain - 1, "боль в спине и груди");
        // Сняли капельницу — реакция проходит за 10–20 минут.
        m.bloodDripRemaining = 0;
        run(m, input(12), s, 9 * 60);
        assertTrue(m.transfusionReactionSeconds > 0, "ещё идёт");
        assertTrue(m.bodyTemp > 37.3, "лихорадка, было " + m.bodyTemp);
        run(m, input(13), s, 12 * 60);
        assertEquals(0, m.transfusionReactionSeconds, 1e-6, "прошла");
    }

    @Test
    void incompatibleTransfusionCanStopTheHeart() {
        MedicalSettings s = settings();
        s.transfusionReactionArrestChance = 1;
        MedicalState m = new MedicalState(s);
        m.bloodType = BloodType.O_NEG;
        m.bloodVolume = 4000;
        Treatments.apply(m, BodyPart.CHEST, TreatmentAction.BLOOD_BAG, false, new java.util.Random(1), s,
                new Treatments.Bag(BloodType.AB_POS, 450, false));
        run(m, input(14), s, 2);
        assertNotEquals(Heart.NORMAL, m.heart, "фибрилляция от несовместимой крови");
    }

    @Test
    void spoiledBloodCausesSepsis() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.bloodType = BloodType.B_POS;
        m.bloodVolume = 4000;
        Treatments.apply(m, BodyPart.CHEST, TreatmentAction.BLOOD_BAG, false, new java.util.Random(1), s,
                new Treatments.Bag(BloodType.B_POS, 450, true));
        run(m, input(15), s, 3600);
        assertTrue(m.sepsis >= s.spoiledBloodSepsis, "испорченная кровь — сепсис, было " + m.sepsis);
    }

    @Test
    void donationTakesABagAndIsRefusedAfterBloodLoss() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.BLOOD_COLLECT, s));
        Treatments.Result r = Treatments.apply(m, BodyPart.CHEST, TreatmentAction.BLOOD_COLLECT, false, new java.util.Random(1), s);
        assertTrue(r.applied);
        assertEquals(m.normalBlood(s) - s.bloodBagVolume, m.bloodVolume, 1e-6, "взяли 450 мл");
        m.bloodVolume = m.normalBlood(s) * 0.8;
        assertEquals("donor_low", Treatments.check(m, BodyPart.CHEST, TreatmentAction.BLOOD_COLLECT, s), "после потери 20 % брать нельзя");
    }

    // ------------------------------------------------------------------ лекарства

    @Test
    void sameEffectExtendsDurationInsteadOfStacking() {
        MedicalState m = new MedicalState(settings());
        m.addEffect(DrugEffect.ANALGESIA, 20, 60, 600);
        m.addEffect(DrugEffect.ANALGESIA, 15, 30, 600);
        DrugEffect.Active a = m.effects.get(DrugEffect.ANALGESIA);
        assertEquals(20, a.strength, "сила не складывается — наибольшая");
        assertEquals(1200, a.seconds, "время продлевается");
        assertEquals(30, a.delay, "задержка — меньшая");
        assertEquals(0, m.effect(DrugEffect.ANALGESIA), "до конца задержки не действует");
    }

    @Test
    void stateSurvivesCopy() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.bloodType = BloodType.AB_NEG;
        m.sepsis = 12;
        m.bodyTemp = 38.2;
        m.addEffect(DrugEffect.SEDATION, 30, 0, 100);
        Wound w = burnOnArm(m, 10);
        w.infectionStage = Wound.Infection.INFECTED;
        w.infection = 42;
        MedicalState c = m.copy();
        assertEquals(BloodType.AB_NEG, c.bloodType);
        assertEquals(12, c.sepsis);
        assertEquals(38.2, c.bodyTemp);
        assertEquals(30, c.effects.get(DrugEffect.SEDATION).strength);
        assertEquals(42, c.part(BodyPart.LEFT_ARM).wounds.get(0).infection);
        c.effects.get(DrugEffect.SEDATION).strength = 1;
        assertEquals(30, m.effects.get(DrugEffect.SEDATION).strength, "копия эффектов независима");
    }
}
