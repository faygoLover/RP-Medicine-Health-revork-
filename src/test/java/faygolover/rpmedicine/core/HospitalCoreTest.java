package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import static faygolover.rpmedicine.core.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Второй этап «Госпиталь»: ядро без Minecraft. */
class HospitalCoreTest {

    /** Множители условий (койка): заживление, кровь и мозг идут быстрее во столько раз. */
    @Test
    void bedFactorsSpeedUpHealingBloodAndBrain() {
        MedicalSettings s = settings();
        MedicalState plain = patient(s);
        MedicalState onBed = patient(s);
        StepInput a = input(1);
        StepInput b = input(1);
        b.healFactor = s.bedHealFactor;
        b.bloodRegenFactor = s.bedBloodRegenFactor;
        b.brainRecoveryFactor = s.bedBrainRecoveryFactor;
        run(plain, a, s, 600);
        run(onBed, b, s, 600);
        double healPlain = 30 - plain.part(BodyPart.LEFT_ARM).totalSeverity();
        double healBed = 30 - onBed.part(BodyPart.LEFT_ARM).totalSeverity();
        assertEquals(s.bedHealFactor, healBed / healPlain, 0.05, "заживление на койке");
        double bloodPlain = plain.bloodVolume - 3500;
        double bloodBed = onBed.bloodVolume - 3500;
        assertEquals(s.bedBloodRegenFactor, bloodBed / bloodPlain, 0.05, "кровь на койке");
        double brainPlain = plain.brain - 90;
        double brainBed = onBed.brain - 90;
        assertEquals(s.bedBrainRecoveryFactor, brainBed / brainPlain, 0.05, "мозг на койке");
    }

    /** Перевязанная рана на руке, кровопотеря без кровотечения, мозг 90. */
    private static MedicalState patient(MedicalSettings s) {
        MedicalState m = new MedicalState(s);
        Wound w = new Wound(WoundType.BRUISE, 30);
        m.part(BodyPart.LEFT_ARM).wounds.add(w);
        m.bloodVolume = 3500;
        m.brain = 90;
        return m;
    }
}
