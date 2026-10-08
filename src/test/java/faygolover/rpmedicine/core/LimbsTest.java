package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.BodyPartState.Prosthesis;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.SplittableRandom;

import static faygolover.rpmedicine.core.TestUtil.input;
import static faygolover.rpmedicine.core.TestUtil.run;
import static faygolover.rpmedicine.core.TestUtil.settings;
import static org.junit.jupiter.api.Assertions.*;

/** Некроз, ампутация, протезы (ТЗ третьего этапа, п. 5–6). */
class LimbsTest {

    @Test
    void forgottenTourniquetBecomesIrreversibleNecrosis() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        BodyPartState leg = m.part(BodyPart.LEFT_LEG);
        leg.ischemia = 100;
        leg.tourniquet = BodyPartState.Tourniquet.CAT;
        leg.tourniquetSeconds = 99999;
        run(m, input(1), s, 3600);
        assertTrue(Limbs.irreversible(leg, s), "час с ишемией 100 — необратимый некроз, было " + leg.necrosis);
        assertTrue(Limbs.necroticParts(m, s) == 1, "источник сепсиса");
        double sepsis = m.sepsis;
        run(m, input(2), s, 600);
        assertTrue(m.sepsis > sepsis, "некроз поднимает сепсис");
    }

    @Test
    void earlyNecrosisRecoversOnAntibiotics() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        BodyPartState arm = m.part(BodyPart.RIGHT_ARM);
        arm.necrosis = 8;
        m.addEffect(DrugEffect.ANTIBIOTIC, 10, 0, 7200);
        run(m, input(1), s, 3600);
        assertEquals(0, arm.necrosis, 1e-9, "обратимый некроз без причины на антибиотике проходит");
        arm.wounds.add(new Wound(WoundType.CUT, 10));
        arm.necrosis = 30;
        double sev = arm.wounds.get(0).severity;
        run(m, input(2), s, 600);
        assertTrue(arm.wounds.get(0).severity >= sev - 1e-9, "мёртвые ткани не заживают");
    }

    @Test
    void surgicalAmputationAndProsthesis() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.down = MedicalState.Down.FAINT;
        BodyPartState leg = m.part(BodyPart.LEFT_LEG);
        leg.necrosis = 60;
        assertEquals("not_retracted", Treatments.check(m, BodyPart.LEFT_LEG, TreatmentAction.AMPUTATE, s));
        leg.surgery = BodyPartState.SurgeryStage.RETRACTED;
        assertEquals("limb_only", Treatments.check(m, BodyPart.CHEST, TreatmentAction.AMPUTATE, s));
        Treatments.Result r = Treatments.apply(m, BodyPart.LEFT_LEG, TreatmentAction.AMPUTATE, false, new SplittableRandom(1), s, Surgery.Context.DEFAULT);
        assertEquals("amputated", r.key);
        assertTrue(leg.missing && m.part(BodyPart.LEFT_FOOT).missing, "нога вместе со стопой");
        assertEquals(0, leg.necrosis, 1e-9, "некроз ушёл с ногой");
        assertFalse(leg.arterial, "хирургическая — без артерии");
        // Культю зашивают отдельным шагом (замечание живого теста 27).
        assertTrue(leg.wounds.stream().noneMatch(w -> w.sutured), "после опила культя открыта");
        assertNull(Treatments.check(m, BodyPart.LEFT_LEG, TreatmentAction.SUTURE, s), "культю можно зашить");
        Treatments.apply(m, BodyPart.LEFT_LEG, TreatmentAction.SUTURE, false, new SplittableRandom(1), s, null);
        assertTrue(leg.wounds.stream().allMatch(w -> w.sutured), "культя зашита");
        // Без протеза — ползком.
        m.down = MedicalState.Down.NONE;
        assertTrue(GameplayEffects.compute(m, PatientTraits.NONE, s).crawl, "без ноги — только ползком");
        // Культя не зажила — протез нельзя; зажила — можно.
        var peg = new Treatments.Prosthetic(Prosthesis.PEG_LEG);
        assertEquals("stump_not_healed", Treatments.check(m, BodyPart.LEFT_LEG, TreatmentAction.INSTALL_PROSTHESIS, s, peg));
        leg.wounds.clear();
        assertEquals("prosthesis_wrong_part", Treatments.check(m, BodyPart.LEFT_LEG, TreatmentAction.INSTALL_PROSTHESIS, s,
                new Treatments.Prosthetic(Prosthesis.HOOK)));
        assertNull(Treatments.check(m, BodyPart.LEFT_LEG, TreatmentAction.INSTALL_PROSTHESIS, s, peg));
        Treatments.apply(m, BodyPart.LEFT_LEG, TreatmentAction.INSTALL_PROSTHESIS, false, new SplittableRandom(1), s, peg);
        assertEquals(Prosthesis.PEG_LEG, leg.prosthesis);
        GameplayEffects.Mods mods = GameplayEffects.compute(m, PatientTraits.NONE, s);
        assertFalse(mods.crawl, "с деревянной ногой — ходит");
        assertTrue(mods.noSprint && mods.speed <= 1 - s.pegLegSpeedPenalty + 1e-9, "медленнее и без бега");
        assertEquals("part_missing", Treatments.check(m, BodyPart.LEFT_LEG, TreatmentAction.SPLINT, s), "шину на культю не наложить");
    }

    @Test
    void hitsOnMissingPartGoCloserToBody() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        Limbs.removePart(m, BodyPart.RIGHT_LEG);
        assertEquals(BodyPart.ABDOMEN, Limbs.present(m, BodyPart.RIGHT_FOOT), "стопы и ноги нет — в живот");
        InjuryProfile prof = new InjuryProfile("t", WoundType.CUT, InjuryProfile.Location.HIT_POINT);
        Injuries.hitPart(m, BodyPart.RIGHT_FOOT, 4, prof, PatientTraits.NONE, new Random(1), s);
        assertTrue(m.part(BodyPart.RIGHT_FOOT).wounds.isEmpty() && !m.part(BodyPart.ABDOMEN).wounds.isEmpty(), "рана в животе");
    }

    @Test
    void hugeHitOnBrokenLimbCanTearItOff() {
        MedicalSettings s = settings();
        s.traumaticAmputationChance = 1.0;
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_ARM).fracture = BodyPartState.Fracture.CLOSED;
        InjuryProfile prof = new InjuryProfile("t", WoundType.BRUISE, InjuryProfile.Location.HIT_POINT);
        Injuries.Report rep = Injuries.hitPart(m, BodyPart.LEFT_ARM, s.traumaticAmputationDamage + 5, prof, PatientTraits.NONE, new Random(1), s);
        assertTrue(rep.amputated.contains(BodyPart.LEFT_ARM) && m.part(BodyPart.LEFT_ARM).missing, "оторвало руку");
        assertTrue(m.part(BodyPart.LEFT_ARM).arterial, "артериальное кровотечение");
        s.traumaticAmputation = false;
        MedicalState m2 = new MedicalState(s);
        m2.part(BodyPart.LEFT_ARM).fracture = BodyPartState.Fracture.CLOSED;
        Injuries.hitPart(m2, BodyPart.LEFT_ARM, s.traumaticAmputationDamage + 5, prof, PatientTraits.NONE, new Random(1), s);
        assertFalse(m2.part(BodyPart.LEFT_ARM).missing, "выключено в конфиге — не отрывает");
    }
}
