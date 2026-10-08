package faygolover.rpmedicine.integration;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpmedicine.core.PatientTraits;
import faygolover.rpmedicine.core.Skill;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

import java.util.OptionalInt;

/**
 * Навыки и черты через RP Core (обязательная зависимость). Кто их ведёт — Core знает сам: с RP Perks
 * уровень «Медицины» и черты берутся из перков, без него навыка нет — все работают как на максимальном
 * уровне (решение автора, итоговый дизайн §3.1), черт нет.
 */
public final class CoreCompat {
    private CoreCompat() {}

    /** Ведёт ли кто-то навык «Медицина» (установлен RP Perks). */
    public static boolean medicineManaged() {
        return RpCoreAPI.isSkillManaged(RpIds.MEDICINE);
    }

    /** Уровень «Медицины» от Core; без RP Perks — максимальный. */
    public static int medicineLevel(Player player) {
        OptionalInt lvl = RpCoreAPI.skill(player, RpIds.MEDICINE);
        return lvl.isPresent() ? Skill.clamp(lvl.getAsInt()) : Skill.MAX_LEVEL;
    }

    /** Записать уровень в RP Perks (через Core); {@code level < 0} — сбросить. false — навык никто не ведёт. */
    public static boolean setMedicine(Player player, int level) {
        return RpCoreAPI.setSkill(player, RpIds.MEDICINE, level);
    }

    /** Сила 0–10 (RP Perks); без него — средняя (4). */
    public static int strength(Player player) {
        OptionalInt lvl = RpCoreAPI.skill(player, RpIds.STRENGTH);
        return lvl.isPresent() ? lvl.getAsInt() : 4;
    }

    /** Сила как множитель: 4 — 1.0, каждый уровень ±7,5 % (0 — 0.7, 10 — 1.45). */
    public static double strengthFactor(Player player) {
        return 1.0 + (strength(player) - 4) * 0.075;
    }

    public static PatientTraits traits(Player player) {
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        return new PatientTraits(has(player, RpIds.TOUGH), has(player, RpIds.FRAGILE), has(player, RpIds.BRAVE),
                has(player, RpIds.COWARD), left, has(player, RpIds.DIABETIC), has(player, RpIds.SMOKER),
                has(player, RpIds.ALCOHOLIC), has(player, RpIds.TEETOTALER), has(player, RpIds.CAFFEINE_ADDICT));
    }

    private static boolean has(Player p, net.minecraft.resources.ResourceLocation trait) {
        return RpCoreAPI.hasTrait(p, trait);
    }
}
