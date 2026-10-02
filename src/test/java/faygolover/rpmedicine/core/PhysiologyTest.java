package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.MedicalState.Down;
import faygolover.rpmedicine.core.MedicalState.Heart;
import faygolover.rpmedicine.core.MedicalState.Pneumo;
import org.junit.jupiter.api.Test;

import static faygolover.rpmedicine.core.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

class PhysiologyTest {

    @Test
    void healthyIsQuietAndStaysQuiet() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertTrue(m.isQuiet(s));
        run(m, input(1), s, 60);
        assertTrue(m.isQuiet(s), "здоровый после минуты шагов остаётся в спящем режиме");
        assertEquals(4900, m.bloodVolume, 0.01);
    }

    @Test
    void arterialBleedingCausesUnconsciousnessInAboutFourMinutes() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_LEG).arterial = true;
        double t = runUntil(m, input(2), s, 900, st -> st.down != Down.NONE);
        assertTrue(t > 0, "должен потерять сознание");
        assertTrue(t >= 150 && t <= 330, "потеря сознания через ~4 минуты, было " + t);
        assertEquals(Down.KNOCKDOWN, m.down, "кровопотеря — угроза жизни, значит нокдаун");
    }

    @Test
    void tourniquetStopsArterialBleeding() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        BodyPartState foot = m.part(BodyPart.RIGHT_FOOT);
        foot.arterial = true;
        assertTrue(m.totalExternalBleed(s) > 300);
        assertNull(Treatments.check(m, BodyPart.RIGHT_LEG, TreatmentAction.TOURNIQUET, s));
        Treatments.apply(m, BodyPart.RIGHT_LEG, TreatmentAction.TOURNIQUET, false, new java.util.Random(1), s);
        assertEquals(0, m.totalExternalBleed(s), 1e-9, "жгут на ноге перекрывает и стопу");
    }

    @Test
    void knockdownTimerBetweenThreeAndFiveMinutes() {
        MedicalSettings s = settings();
        // Самый быстрый случай: остановка сердца без СЛР.
        MedicalState fast = new MedicalState(s);
        fast.heart = Heart.ARREST;
        StepInput in = input(3);
        double down = runUntil(fast, in, s, 120, st -> st.down == Down.KNOCKDOWN);
        assertTrue(down >= 0);
        double dead = runUntil(fast, in, s, 900, st -> st.brain <= 0);
        assertTrue(dead >= 175 && dead <= 305, "нокдаун при остановке сердца длится 3–5 минут, было " + dead);

        // Самый медленный случай: кровопотеря при работающем сердце.
        MedicalState slow = new MedicalState(s);
        slow.bloodVolume = slow.normalBlood(s) * 0.58;
        slow.pressure = Physiology.pressureFromVolume(0.58, s);
        StepInput in2 = input(4);
        runUntil(slow, in2, s, 120, st -> st.down == Down.KNOCKDOWN);
        double dead2 = runUntil(slow, in2, s, 900, st -> st.brain <= 0 || st.down == Down.CLINICAL);
        assertTrue(dead2 >= 175 && dead2 <= 305, "нокдаун длится 3–5 минут, было " + dead2);
    }

    @Test
    void fixingCauseStopsKnockdownTimerAndWakesUp() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_ARM).arterial = true;
        StepInput in = input(5);
        runUntil(m, in, s, 600, st -> st.down == Down.KNOCKDOWN);
        run(m, in, s, 20);
        double brainBefore = m.brain;
        assertTrue(brainBefore < 100);
        // Жгут и физраствор: причина устранена.
        Treatments.apply(m, BodyPart.LEFT_ARM, TreatmentAction.TOURNIQUET, false, in.random, s);
        Physiology.infuseSaline(m, 1500, s);
        double woke = runUntil(m, in, s, 300, st -> st.down == Down.NONE);
        assertTrue(woke > 0, "после устранения причины должен прийти в себя");
        assertTrue(woke <= 90, "пробуждение за 20–60 секунд после стабилизации, было " + woke);
        assertTrue(m.brain >= brainBefore - 15, "таймер-мозг остановился");
    }

    @Test
    void painShockCausesFaintWithoutTimer() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        // Несколько тяжёлых ожогов: очень сильная боль, без кровотечения.
        for (BodyPart p : new BodyPart[]{BodyPart.CHEST, BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.LEFT_LEG})
            m.part(p).wounds.add(new Wound(WoundType.BURN, 40));
        StepInput in = input(6);
        double t = runUntil(m, in, s, 120, st -> st.down != Down.NONE);
        assertTrue(t >= 19 && t <= 32, "болевой шок через 20–30 секунд, было " + t);
        assertEquals(Down.FAINT, m.down);
        double brain = m.brain;
        run(m, in, s, 60);
        assertEquals(brain, m.brain, 1e-6, "в обмороке таймера нет");
        // Морфин: боль уходит, человек приходит в себя.
        Treatments.apply(m, BodyPart.CHEST, TreatmentAction.MORPHINE, false, in.random, s);
        double woke = runUntil(m, in, s, 120, st -> st.down == Down.NONE);
        assertTrue(woke > 0);
    }

    @Test
    void openPneumothoraxBecomesTensionWithoutSeal() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.CHEST).wounds.add(new Wound(WoundType.STAB, 15));
        m.pneumo = Pneumo.OPEN;
        m.pneumoTimer = 150;
        StepInput in = input(7);
        double t = runUntil(m, in, s, 400, st -> st.pneumo == Pneumo.TENSION);
        assertEquals(150, t, 1.0);
        double arrest = runUntil(m, in, s, 400, st -> st.heart == Heart.ARREST);
        assertTrue(arrest > 0 && arrest <= s.tensionArrestSeconds + 1, "напряжённый пневмоторакс останавливает сердце");
    }

    @Test
    void occlusiveSealStopsProgressionAndNeedleRelievesTension() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.CHEST).wounds.add(new Wound(WoundType.GUNSHOT, 20));
        m.pneumo = Pneumo.OPEN;
        m.pneumoTimer = 10;
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.OCCLUSIVE, s));
        Treatments.apply(m, BodyPart.CHEST, TreatmentAction.OCCLUSIVE, false, new java.util.Random(1), s);
        run(m, input(8), s, 300);
        assertEquals(Pneumo.OPEN, m.pneumo, "наклейка останавливает развитие");

        MedicalState t = new MedicalState(s);
        t.pneumo = Pneumo.TENSION;
        t.tensionProgress = 0.5;
        assertNull(Treatments.check(t, BodyPart.CHEST, TreatmentAction.NEEDLE, s));
        Treatments.apply(t, BodyPart.CHEST, TreatmentAction.NEEDLE, false, new java.util.Random(1), s);
        assertEquals(Pneumo.OPEN, t.pneumo);
    }

    @Test
    void bloodRegeneratesFiveHundredPerHourWithoutBleeding() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.bloodVolume = 4000;
        run(m, input(9), s, 3600);
        assertEquals(4500, m.bloodVolume, 5);
    }

    @Test
    void salineLimitedToThirdOfBlood() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.bloodVolume = 2500;
        double added = Physiology.infuseSaline(m, 5000, s);
        assertEquals(4900 / 3.0, added, 0.5);
        assertTrue(m.oxygenCapacity(s) < m.bloodFraction(s), "физраствор не несёт кислород");
    }

    @Test
    void clinicalDeathAndResuscitation() {
        MedicalSettings s = settings();
        s.noDeathMode = true;
        MedicalState m = new MedicalState(s);
        m.heart = Heart.ARREST;
        StepInput in = input(10);
        double t = runUntil(m, in, s, 600, st -> st.down == Down.CLINICAL);
        assertTrue(t > 0, "без смерти: вместо смерти клиническая");
        assertEquals(1, m.brain, 1e-9);
        run(m, in, s, 600);
        assertEquals(Down.CLINICAL, m.down, "клиническая смерть длится бесконечно");
        // СЛР + адреналин.
        Treatments.apply(m, BodyPart.CHEST, TreatmentAction.ADRENALINE, false, in.random, s);
        double rescued = runUntil(m, in, s, 120, st -> {
            Treatments.apply(st, BodyPart.CHEST, TreatmentAction.CPR, false, in.random, s);
            return st.down != Down.CLINICAL;
        });
        assertTrue(rescued > 0, "СЛР с адреналином запускают сердце");
        assertEquals(Down.KNOCKDOWN, m.down);
        double woke = runUntil(m, in, s, 300, st -> st.down == Down.NONE);
        assertTrue(woke > 0, "после реанимации — пробуждение");
        assertTrue(m.postClinicalSeconds > 0, "остаются последствия");
    }

    @Test
    void deathWithoutNoDeathMode() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.heart = Heart.ARREST;
        StepInput in = input(11);
        boolean died = false;
        for (int i = 0; i < 2000 && !died; i++) died = Physiology.step(m, in, s).has(StepResult.Event.DIED);
        assertTrue(died);
    }

    @Test
    void pressureCurveMatchesSpec() {
        MedicalSettings s = settings();
        assertEquals(120, Physiology.pressureFromVolume(0.85, s), 1e-9);
        assertTrue(Physiology.pressureFromVolume(0.70, s) < 100, "30% — давление падает");
        assertTrue(Physiology.pressureFromVolume(0.60, s) <= 60, "40% — потеря сознания");
        assertTrue(Physiology.pressureFromVolume(0.50, s) <= 40, "50% — остановка");
    }
}
