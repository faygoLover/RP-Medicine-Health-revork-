package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.HitLocator.Posture;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static faygolover.rpmedicine.core.TestUtil.settings;
import static org.junit.jupiter.api.Assertions.*;

class InjuriesTest {

    @Test
    void severityIsDamageTimesFive() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        InjuryProfile p = new InjuryProfile("t", WoundType.CUT, InjuryProfile.Location.HIT_POINT);
        Injuries.hitPart(m, BodyPart.LEFT_ARM, 4, p, PatientTraits.NONE, new Random(1), s);
        assertEquals(1, m.part(BodyPart.LEFT_ARM).wounds.size());
        assertEquals(20, m.part(BodyPart.LEFT_ARM).wounds.get(0).severity, 1e-9);
        assertEquals(80, m.part(BodyPart.LEFT_ARM).integrity(), 1e-9);
    }

    @Test
    void sameWoundsMergeAndListIsCapped() {
        MedicalSettings s = settings();
        BodyPartState ps = new BodyPartState(BodyPart.CHEST);
        Injuries.mergeWound(ps, WoundType.CUT, 10, s);
        Injuries.mergeWound(ps, WoundType.CUT, 10, s);
        assertEquals(1, ps.wounds.size());
        assertEquals(20, ps.wounds.get(0).severity, 1e-9);
        // Перевязанная рана не сливается с новой — появляется новая запись.
        ps.wounds.get(0).dressing = Dressing.BANDAGE;
        Injuries.mergeWound(ps, WoundType.CUT, 5, s);
        assertEquals(2, ps.wounds.size());
        for (WoundType t : WoundType.VALUES) Injuries.mergeWound(ps, t, 1, s);
        assertTrue(ps.wounds.size() <= s.maxWoundsPerPart, "не больше 6 записей на часть");
    }

    @Test
    void complicationChanceGrowsWithSeverity() {
        Chance c = new Chance(10, 0.01, 0.5);
        assertEquals(0, c.at(5), 1e-9);
        assertEquals(0.1, c.at(20), 1e-9);
        assertEquals(0.5, c.at(100), 1e-9);
    }

    @Test
    void fractureAndArterialFromProfile() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        InjuryProfile p = new InjuryProfile("t", WoundType.GUNSHOT, InjuryProfile.Location.HIT_POINT);
        p.fracture = new Chance(0, 1, 1);
        p.arterial = new Chance(0, 1, 1);
        p.foreignBody = new Chance(0, 1, 1);
        Injuries.Report r = Injuries.hitPart(m, BodyPart.RIGHT_LEG, 5, p, PatientTraits.NONE, new Random(1), s);
        assertTrue(r.has(Injuries.Outcome.FRACTURE) || r.has(Injuries.Outcome.OPEN_FRACTURE));
        assertTrue(m.part(BodyPart.RIGHT_LEG).arterial);
        assertEquals(1, m.part(BodyPart.RIGHT_LEG).bullets);
    }

    @Test
    void chestPenetrationGivesPneumothoraxAndInternalBleeding() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        InjuryProfile p = new InjuryProfile("t", WoundType.STAB, InjuryProfile.Location.HIT_POINT);
        p.pneumothorax = new Chance(0, 1, 1);
        p.internal = new Chance(0, 1, 1);
        Injuries.hitPart(m, BodyPart.CHEST, 4, p, PatientTraits.NONE, new Random(1), s);
        assertEquals(MedicalState.Pneumo.OPEN, m.pneumo);
        assertTrue(m.pneumoTimer >= s.pneumoSealMinSeconds && m.pneumoTimer <= s.pneumoSealMaxSeconds);
        assertTrue(m.part(BodyPart.CHEST).internalBleed > 0);
    }

    @Test
    void damageToDownedShortensTimer() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.down = MedicalState.Down.KNOCKDOWN;
        InjuryProfile p = new InjuryProfile("t", WoundType.BRUISE, InjuryProfile.Location.RANDOM);
        Injuries.apply(m, p, 5, null, 0, PatientTraits.NONE, new Random(1), s);
        assertEquals(100 - 5 * s.downedDamageBrainPerDamage, m.brain, 1e-9);
    }

    @Test
    void fallHitsFeetAndLegsAndHighFallHitsPelvisAndHead() {
        InjuryProfile p = new InjuryProfile("fall", WoundType.BRUISE, InjuryProfile.Location.FALL);
        var low = Injuries.distribute(p, 5, null, 0, new Random(1));
        assertTrue(low.stream().allMatch(sh -> sh.part().isLowerLimb()));
        var high = Injuries.distribute(p, 20, null, 0, new Random(1));
        assertTrue(high.stream().anyMatch(sh -> sh.part() == BodyPart.ABDOMEN));
        assertTrue(high.stream().anyMatch(sh -> sh.part() == BodyPart.HEAD));
        assertEquals(1.0, high.stream().mapToDouble(Injuries.Share::fraction).sum(), 1e-9);
    }

    @Test
    void explosionHitsSeveralParts() {
        InjuryProfile p = new InjuryProfile("boom", WoundType.SHRAPNEL, InjuryProfile.Location.EXPLOSION);
        var parts = Injuries.distribute(p, 18, null, 1.0, new Random(3));
        assertTrue(parts.size() >= 2);
    }

    @Test
    void hitLocatorHeightsAndSides() {
        assertEquals(BodyPart.RIGHT_FOOT, HitLocator.locate(0.05, 0.3, Posture.STANDING));
        assertEquals(BodyPart.LEFT_LEG, HitLocator.locate(0.3, -0.2, Posture.STANDING));
        assertEquals(BodyPart.ABDOMEN, HitLocator.locate(0.5, 0.1, Posture.STANDING));
        assertEquals(BodyPart.CHEST, HitLocator.locate(0.7, -0.1, Posture.STANDING));
        assertEquals(BodyPart.RIGHT_ARM, HitLocator.locate(0.7, 0.9, Posture.STANDING));
        assertEquals(BodyPart.LEFT_ARM, HitLocator.locate(0.55, -0.8, Posture.STANDING));
        assertEquals(BodyPart.HEAD, HitLocator.locate(0.9, 0, Posture.STANDING));
        assertEquals(BodyPart.HEAD, HitLocator.locate(0.95, 0, Posture.HORIZONTAL));
    }

    @Test
    void instantDeathThresholds() {
        MedicalSettings s = settings();
        assertTrue(Injuries.isInstantlyLethal(BodyPart.HEAD, 30, s));
        assertFalse(Injuries.isInstantlyLethal(BodyPart.CHEST, 30, s));
        assertTrue(Injuries.isInstantlyLethal(BodyPart.CHEST, 80, s));
    }

    @Test
    void woundsHealWithTime() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_ARM).wounds.add(new Wound(WoundType.BRUISE, 20));
        // Ушиб тяжестью 20 заживает за 15 + 15×0,2 = 18 минут.
        Healing.fastForward(m, 17 * 60, s);
        assertFalse(m.part(BodyPart.LEFT_ARM).wounds.isEmpty());
        Healing.fastForward(m, 2 * 60, s);
        assertTrue(m.part(BodyPart.LEFT_ARM).wounds.isEmpty());
    }

    @Test
    void bulletInsideBlocksHealing() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        BodyPartState ps = m.part(BodyPart.LEFT_LEG);
        Wound w = new Wound(WoundType.GUNSHOT, 20);
        w.dressing = Dressing.PRESSURE;
        ps.wounds.add(w);
        ps.bullets = 1;
        Healing.fastForward(m, 5 * 3600, s);
        assertEquals(20, w.severity, 1e-9);
        ps.bullets = 0;
        Healing.fastForward(m, 3 * 3600, s);
        assertTrue(ps.wounds.isEmpty());
    }

    @Test
    void fractureHealsOnlyWithSplint() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        BodyPartState ps = m.part(BodyPart.RIGHT_LEG);
        ps.fracture = BodyPartState.Fracture.CLOSED;
        Healing.fastForward(m, 10 * 3600, s);
        assertTrue(ps.hasFracture(), "без шины не заживает");
        ps.splint = true;
        Healing.fastForward(m, 5 * 3600, s);
        assertFalse(ps.hasFracture(), "с шиной — за 4–5 часов");
    }
}
