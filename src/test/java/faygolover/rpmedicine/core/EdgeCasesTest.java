package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.MedicalState.Down;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static faygolover.rpmedicine.core.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Пограничные случаи физиологии, ран и нокдауна. */
class EdgeCasesTest {

    @Test
    void painLimpsFromThirty() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.pain = 35;
        assertEquals(1 - s.painLimpSpeedPenalty, GameplayEffects.compute(m, PatientTraits.NONE, s).speed, 1e-9);
        m.pain = 65;
        assertEquals(1 - s.painLimpSpeedPenalty - s.highPainSpeedPenalty, GameplayEffects.compute(m, PatientTraits.NONE, s).speed, 1e-9);
    }

    @Test
    void freshDressingReopensFromSprintButHemostaticDoesNot() {
        MedicalSettings s = settings();
        s.reopenChancePerSprintSecond = 1.0;
        MedicalState m = new MedicalState(s);
        Wound cut = new Wound(WoundType.CUT, 20);
        cut.dressing = Dressing.BANDAGE;
        Wound gauze = new Wound(WoundType.GUNSHOT, 20);
        gauze.dressing = Dressing.HEMOSTATIC;
        m.part(BodyPart.LEFT_LEG).wounds.add(cut);
        m.part(BodyPart.RIGHT_LEG).wounds.add(gauze);
        StepInput in = input(1);
        in.sprintSeconds = 1;
        StepResult r = Physiology.step(m, in, s);
        assertTrue(r.has(StepResult.Event.DRESSING_REOPENED));
        assertFalse(cut.isDressed());
        assertTrue(gauze.isDressed(), "гемостатик не рвётся повторно");
    }

    @Test
    void oldDressingDoesNotReopen() {
        MedicalSettings s = settings();
        s.reopenChancePerSprintSecond = 1.0;
        MedicalState m = new MedicalState(s);
        Wound cut = new Wound(WoundType.CUT, 20);
        cut.dressing = Dressing.BANDAGE;
        cut.dressingAge = s.freshDressingMinutes * 60 + 1;
        m.part(BodyPart.LEFT_ARM).wounds.add(cut);
        StepInput in = input(2);
        in.sprintSeconds = 1;
        Physiology.step(m, in, s);
        assertTrue(cut.isDressed());
    }

    @Test
    void tourniquetHarmsAfterFifteenMinutes() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        BodyPartState arm = m.part(BodyPart.RIGHT_ARM);
        arm.tourniquet = BodyPartState.Tourniquet.CAT;
        run(m, input(3), s, 14 * 60);
        assertEquals(0, arm.ischemia, 1e-9, "первые 15 минут жгут безопасен");
        run(m, input(3), s, 5 * 60);
        assertTrue(arm.ischemia > 0, "после 15 минут — ишемия");
        assertTrue(arm.integrity() < 100);
    }

    @Test
    void secondMorphineIsOverdose() {
        MedicalSettings s = settings();
        s.morphineOverdoseArrestChance = 1.0;
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.CHEST).wounds.add(new Wound(WoundType.BURN, 20));
        m.rawPain = 50;
        Treatments.apply(m, BodyPart.CHEST, TreatmentAction.MORPHINE, false, new Random(1), s);
        assertFalse(m.respiratoryArrest);
        Treatments.Result r = Treatments.apply(m, BodyPart.CHEST, TreatmentAction.MORPHINE, false, new Random(1), s);
        assertEquals("morphine_overdose", r.key);
        assertTrue(m.respiratoryArrest, "вторая доза подряд — остановка дыхания");
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.AMBU, s), "нужен мешок Амбу");
    }

    @Test
    void salineDripsOnlyWhenStill() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.bloodVolume = 3500;
        Treatments.apply(m, BodyPart.CHEST, TreatmentAction.SALINE, false, new Random(1), s);
        StepInput moving = input(4);
        moving.still = false;
        Physiology.step(m, moving, s);
        assertEquals(3500, m.bloodVolume, 1.0, "на ходу капельница не идёт");
        run(m, input(4), s, s.salineDripSeconds + 1);
        // 500 мл физраствора плюс собственное восстановление крови (~17 мл за 2 минуты).
        assertTrue(m.bloodVolume >= 3995 && m.bloodVolume <= 4030, "500 мл за 2 минуты, было " + m.bloodVolume);
    }

    @Test
    void txaSlowsInternalBleeding() {
        MedicalSettings s = settings();
        MedicalState a = new MedicalState(s);
        MedicalState b = new MedicalState(s);
        a.part(BodyPart.ABDOMEN).internalBleed = 150;
        b.part(BodyPart.ABDOMEN).internalBleed = 150;
        b.txaSeconds = 600;
        run(a, input(5), s, 60);
        run(b, input(5), s, 60);
        assertTrue(b.bloodVolume > a.bloodVolume + 50, "транексамовая кислота замедляет кровотечение");
    }

    @Test
    void surgicalKitStopsInternalBleeding() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.ABDOMEN).internalBleed = 120;
        assertEquals(BodyPart.ABDOMEN, Treatments.bestPart(m, TreatmentAction.SURGICAL_KIT, s));
        Treatments.apply(m, BodyPart.ABDOMEN, TreatmentAction.SURGICAL_KIT, false, new Random(1), s);
        assertEquals(0, m.totalInternalBleed(), 1e-9);
    }

    @Test
    void heavyConcussionKnocksOutBriefly() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertTrue(Injuries.addConcussion(m, 90, new Random(1), s));
        double t = runUntil(m, input(6), s, 30, st -> st.down != Down.NONE);
        assertTrue(t >= 0 && t <= 1, "сразу без сознания");
        assertEquals(Down.FAINT, m.down, "контузия без угрозы — обморок");
        double woke = runUntil(m, input(6), s, 60, st -> st.down == Down.NONE);
        assertTrue(woke > 0 && woke <= 20, "приходит в себя за 5–15 секунд");
    }

    @Test
    void ammoniaWakesFromFaintNotFromThreat() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.down = Down.FAINT;
        m.painShock = true;
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.AMMONIA, s));
        MedicalState k = new MedicalState(s);
        k.down = Down.KNOCKDOWN;
        k.heart = MedicalState.Heart.ARREST;
        assertEquals("ammonia_threat", Treatments.check(k, BodyPart.CHEST, TreatmentAction.AMMONIA, s));
    }

    @Test
    void airwayHoldsBreathingInKnockdown() {
        MedicalSettings s = settings();
        MedicalState a = new MedicalState(s);
        a.down = Down.KNOCKDOWN;
        MedicalState b = a.copy();
        b.airway = true;
        StepInput in = input(7);
        assertTrue(Physiology.ventilation(b, in) > Physiology.ventilation(a, in), "воздуховод держит дыхание");
    }

    @Test
    void brainRecoversSlowlyOnline() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.brain = 50;
        run(m, input(8), s, 3600);
        assertTrue(m.brain > 50 && m.brain < 70, "за час — немного, было " + m.brain);
        StepInput offline = input(8);
        offline.online = false;
        double before = m.brain;
        run(m, offline, s, 600);
        assertEquals(before, m.brain, 1e-9, "у заглушки мозг не восстанавливается");
    }

    @Test
    void speechFollowsDownState() {
        MedicalState m = new MedicalState(settings());
        assertEquals(Speech.NORMAL, Speech.of(m));
        m.down = Down.KNOCKDOWN;
        assertEquals(Speech.MUTED, Speech.of(m));
        assertTrue(Speech.of(m).canHear());
        m.down = Down.CLINICAL;
        assertFalse(Speech.of(m).canHear());
    }

    @Test
    void openFractureBleedsAndBandageHelps() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        BodyPartState leg = m.part(BodyPart.LEFT_LEG);
        leg.fracture = BodyPartState.Fracture.OPEN;
        double before = Physiology.partExternalBleed(m, leg, s);
        assertTrue(before > 0);
        leg.wounds.add(new Wound(WoundType.CUT, 5));
        Treatments.apply(m, BodyPart.LEFT_LEG, TreatmentAction.BANDAGE, false, new Random(1), s);
        assertTrue(Physiology.partExternalBleed(m, leg, s) < before);
    }

    @Test
    void sleepingModeAfterRecovery() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_ARM).wounds.add(new Wound(WoundType.BRUISE, 10));
        assertFalse(m.isQuiet(s));
        run(m, input(9), s, 40 * 60);
        assertTrue(m.isQuiet(s), "после заживления снова «спящий режим»");
    }

    @Test
    void stubDoesNotHeal() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_ARM).wounds.add(new Wound(WoundType.BRUISE, 10));
        StepInput offline = input(10);
        offline.online = false;
        run(m, offline, s, 3600);
        assertEquals(10, m.part(BodyPart.LEFT_ARM).totalSeverity(), 1e-9);
    }
}
