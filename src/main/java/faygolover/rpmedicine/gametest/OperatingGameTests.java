package faygolover.rpmedicine.gametest;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.capability.MedicalNbt;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.Diagnostics;
import faygolover.rpmedicine.core.InjuryProfile;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Organ;
import faygolover.rpmedicine.core.Organs;
import faygolover.rpmedicine.core.WoundType;
import faygolover.rpmedicine.server.DamageHandler;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static faygolover.rpmedicine.gametest.MedicalGameTests.T;
import static faygolover.rpmedicine.gametest.MedicalGameTests.noDeath;
import static faygolover.rpmedicine.gametest.MedicalGameTests.player;
import static faygolover.rpmedicine.gametest.MedicalGameTests.remove;
import static faygolover.rpmedicine.gametest.MedicalGameTests.state;

/** Сценарии третьего этапа «Операционная» (п. 13 ТЗ третьего этапа). */
@GameTestHolder(RpMedicine.MODID)
@PrefixGameTestTemplate(false)
public final class OperatingGameTests {
    private OperatingGameTests() {}

    /** Огнестрел в живот — урон органу; сканер, лаборатория, команда, сохранение. Своя пачка: меняет общий шанс. */
    @GameTest(template = T, batch = "organs", timeoutTicks = 100)
    public static void gunshotDamagesOrganScannerSeesIt(GameTestHelper h) {
        noDeath(false);
        MedicalSettings s = MedicalSettings.get();
        double chance = s.gunshotOrganChance;
        s.gunshotOrganChance = 1.0;
        try {
            ServerPlayer p = player(h, 2.5, 2.5);
            MedicalState m = state(p);
            InjuryProfile gun = new InjuryProfile("test/gunshot", WoundType.GUNSHOT, InjuryProfile.Location.HIT_POINT);
            DamageHandler.applyInjury(p, m, gun, 8, BodyPart.ABDOMEN, 0, null);
            h.assertTrue(Organs.worst(m) > 0, "орган задет");
            Organ hit = null;
            for (Organ o : Organ.VALUES) if (m.organ(o) > 0) hit = o;
            h.assertTrue(hit != null && hit.part == BodyPart.ABDOMEN, "орган живота");
            var words = Diagnostics.scanPart(m, BodyPart.ABDOMEN);
            Organ found = hit;
            h.assertTrue(words.stream().anyMatch(w -> w.startsWith("scan_" + found.id + "_")), "сканер видит: " + words);
            // Сохранение и чтение.
            MedicalState copy = new MedicalState(s);
            MedicalNbt.read(copy, MedicalNbt.write(m), s);
            h.assertTrue(Math.abs(copy.organ(hit) - m.organ(hit)) < 0.01, "органы сохраняются");
            // Команда ГМа.
            h.assertTrue(MedicalGameTests.command(h, "rpmedicine set " + p.getGameProfile().getName() + " organ heart 55") == 1, "set organ");
            h.assertTrue(m.organ(Organ.HEART) == 55, "сердце 55");
            h.assertTrue(Diagnostics.lab(m, s).troponin() > 14, "тропонин выше нормы");
            MedicalGameTests.command(h, "rpmedicine heal " + p.getGameProfile().getName());
            h.assertTrue(Organs.worst(m) == 0, "heal лечит органы");
            remove(h, p);
            h.succeed();
        } finally {
            s.gunshotOrganChance = chance;
        }
    }
}
