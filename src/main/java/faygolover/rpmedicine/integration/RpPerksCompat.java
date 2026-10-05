package faygolover.rpmedicine.integration;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.core.PatientTraits;
import faygolover.rpmedicine.core.Skill;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.capability.PlayerPerksProvider;
import faygolover.rpperks.perk.Perk;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * RP Perks — обязательная зависимость. Уровень «Медицины» 0–10 — атрибут {@code rpperks:medicine},
 * если он есть в реестре; иначе из перков: «Курсы первой помощи» → 3, «Анастасия» → 8, иначе 0.
 */
public final class RpPerksCompat {
    public static final ResourceLocation MEDICINE_ATTRIBUTE = new ResourceLocation("rpperks", "medicine");
    private static Attribute medicine;
    private static boolean looked;

    private RpPerksCompat() {}

    private static Attribute medicineAttribute() {
        if (!looked) {
            looked = true;
            medicine = ForgeRegistries.ATTRIBUTES.getValue(MEDICINE_ATTRIBUTE);
            RpMedicine.LOGGER.info("RP Medicine: атрибут {} {}", MEDICINE_ATTRIBUTE, medicine != null ? "найден" : "не найден, уровень берётся из перков");
        }
        return medicine;
    }

    public static int medicineLevel(Player player) {
        Attribute a = medicineAttribute();
        if (a != null) {
            AttributeInstance inst = player.getAttribute(a);
            if (inst != null) return Skill.clamp((int) Math.floor(inst.getValue() + 1e-6));
        }
        PlayerPerks perks = perks(player);
        if (perks == null) return 0;
        if (perks.hasPerk(Perk.MEDIC)) return 8;
        if (perks.hasPerk(Perk.FIRST_AID)) return 3;
        return 0;
    }

    /** Есть ли атрибут «Медицина» в RP Perks. */
    public static boolean hasMedicineAttribute() {
        return medicineAttribute() != null;
    }

    /** Записать уровень в атрибут RP Perks (база атрибута). false — атрибута нет. */
    public static boolean setMedicine(Player player, int level) {
        Attribute a = medicineAttribute();
        if (a == null) return false;
        AttributeInstance inst = player.getAttribute(a);
        if (inst == null) return false;
        inst.setBaseValue(level);
        return true;
    }

    public static PatientTraits traits(Player player) {
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        PlayerPerks p = perks(player);
        if (p == null) return new PatientTraits(false, false, false, false, left);
        boolean tough = p.hasPerk(Perk.SURVIVOR) || p.hasPerk(Perk.TOUGH);
        boolean fragile = p.hasPerk(Perk.FRAGILE) || p.hasPerk(Perk.WEAKLING) || p.hasPerk(Perk.WIMP);
        boolean brave = p.hasPerk(Perk.BRAVE) || p.hasPerk(Perk.STRONG_WILLED);
        boolean coward = p.hasPerk(Perk.COWARD);
        return new PatientTraits(tough, fragile, brave, coward, left,
                p.hasPerk(Perk.DIABETIC), p.hasPerk(Perk.SMOKER), p.hasPerk(Perk.ALCOHOLIC));
    }

    private static PlayerPerks perks(Player player) {
        return player.getCapability(PlayerPerksProvider.PLAYER_PERKS).resolve().orElse(null);
    }
}
