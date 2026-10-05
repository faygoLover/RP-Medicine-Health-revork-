package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.BodyPartState.SurgeryStage;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static faygolover.rpmedicine.core.TestUtil.input;
import static faygolover.rpmedicine.core.TestUtil.run;
import static faygolover.rpmedicine.core.TestUtil.settings;
import static org.junit.jupiter.api.Assertions.*;

/** Органы и конечности вне тела (ТЗ третьего этапа, п. 7). */
class TransplantTest {

    private static Treatments.Result apply(MedicalState m, BodyPart p, TreatmentAction a, MedicalSettings s, Treatments.Extra extra) {
        assertNull(Treatments.check(m, p, a, s, extra), a + " должно быть можно");
        return Treatments.apply(m, p, a, false, new SplittableRandom(1), s, extra);
    }

    @Test
    void removeAndTransplantWithRejection() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.down = MedicalState.Down.FAINT;
        m.bloodType = BloodType.O_NEG;
        m.part(BodyPart.ABDOMEN).surgery = SurgeryStage.RETRACTED;
        assertEquals("not_retracted", Treatments.check(m, BodyPart.CHEST, TreatmentAction.ORGAN_REMOVE, s));
        apply(m, BodyPart.ABDOMEN, TreatmentAction.ORGAN_REMOVE, s, new Treatments.OrganPick(Organ.KIDNEYS));
        assertFalse(m.hasOrgan(Organ.KIDNEYS), "почки изъяты");
        assertEquals("organ_present", Treatments.check(m, BodyPart.ABDOMEN, TreatmentAction.TRANSPLANT, s,
                new Treatments.DonorOrgan(Organ.LIVER, 0, BloodType.O_NEG, false)));
        // Несовместимая кровь донора — отторжение.
        apply(m, BodyPart.ABDOMEN, TreatmentAction.TRANSPLANT, s, new Treatments.DonorOrgan(Organ.KIDNEYS, 10, BloodType.AB_POS, false));
        assertTrue(m.hasOrgan(Organ.KIDNEYS) && (m.organRejection & Organ.KIDNEYS.bit()) != 0, "пересажено, отторжение");
        double before = m.organ(Organ.KIDNEYS);
        run(m, input(1), s, 3600);
        assertTrue(m.organ(Organ.KIDNEYS) >= before + s.rejectionPerHour * 0.9, "без иммуносупрессора орган разрушается");
        m.addEffect(DrugEffect.IMMUNOSUPPRESSION, 1, 0, 12 * 3600);
        double mid = m.organ(Organ.KIDNEYS);
        run(m, input(2), s, 3600);
        assertTrue(m.organ(Organ.KIDNEYS) <= mid + 0.5, "циклоспорин останавливает отторжение");
    }

    @Test
    void compatibleTransplantDoesNotReject() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.down = MedicalState.Down.FAINT;
        m.bloodType = BloodType.AB_POS;
        m.part(BodyPart.ABDOMEN).surgery = SurgeryStage.RETRACTED;
        m.organsMissing |= Organ.LIVER.bit();
        apply(m, BodyPart.ABDOMEN, TreatmentAction.TRANSPLANT, s, new Treatments.DonorOrgan(Organ.LIVER, 20, BloodType.O_NEG, false));
        assertEquals(0, m.organRejection, "O− подходит всем");
        assertEquals(20, m.organ(Organ.LIVER), 1e-9, "орган встаёт со своим повреждением");
        m.organsMissing |= Organ.INTESTINES.bit();
        assertEquals("organ_transplanted_dead",
                apply(m, BodyPart.ABDOMEN, TreatmentAction.TRANSPLANT, s, new Treatments.DonorOrgan(Organ.INTESTINES, 0, BloodType.O_NEG, true)).key);
        assertEquals(100, m.organ(Organ.INTESTINES), 1e-9, "испорченный — повреждение 100");
    }

    @Test
    void noHeartMeansArrest() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.organsMissing |= Organ.HEART.bit();
        run(m, input(1), s, 2);
        assertEquals(MedicalState.Heart.ARREST, m.heart, "без сердца — остановка");
    }

    @Test
    void reattachFreshLimb() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.down = MedicalState.Down.FAINT;
        Limbs.removePart(m, BodyPart.RIGHT_LEG);
        assertEquals("limb_wrong_part", Treatments.check(m, BodyPart.RIGHT_LEG, TreatmentAction.REATTACH, s, new Treatments.Limb(BodyPart.LEFT_LEG, true)));
        assertEquals("limb_spoiled", Treatments.check(m, BodyPart.RIGHT_LEG, TreatmentAction.REATTACH, s, new Treatments.Limb(BodyPart.RIGHT_LEG, false)));
        apply(m, BodyPart.RIGHT_LEG, TreatmentAction.REATTACH, s, new Treatments.Limb(BodyPart.RIGHT_LEG, true));
        assertFalse(m.part(BodyPart.RIGHT_LEG).missing || m.part(BodyPart.RIGHT_FOOT).missing, "нога со стопой на месте");
        assertTrue(m.part(BodyPart.RIGHT_LEG).wounds.stream().allMatch(w -> w.sutured), "шов");
    }
}
