package faygolover.rpmedicine.server;

import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.core.GameplayEffects;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;
import java.util.UUID;

/**
 * Применяет последствия травм к игроку (п. 2.4, 4.4, 9 ТЗ) через атрибуты: скорость, удар,
 * выносливость RP Stamina (её атрибуты читаются по id, жёсткой зависимости нет).
 */
public final class EffectsApplier {
    private EffectsApplier() {}

    private static final UUID SPEED_ID = UUID.fromString("6d1f0a52-5b4e-4b8e-9a51-1f6c2e0d7a01");
    private static final UUID ATTACK_ID = UUID.fromString("6d1f0a52-5b4e-4b8e-9a51-1f6c2e0d7a02");
    private static final UUID STAMINA_MAX_ID = UUID.fromString("6d1f0a52-5b4e-4b8e-9a51-1f6c2e0d7a03");
    private static final UUID STAMINA_REGEN_ID = UUID.fromString("6d1f0a52-5b4e-4b8e-9a51-1f6c2e0d7a04");
    private static final UUID GUN_SPREAD_ID = UUID.fromString("6d1f0a52-5b4e-4b8e-9a51-1f6c2e0d7a05");
    private static final UUID GUN_RELOAD_ID = UUID.fromString("6d1f0a52-5b4e-4b8e-9a51-1f6c2e0d7a06");
    private static final ResourceLocation TAA_SPREAD = new ResourceLocation("taa", "inaccuracy");
    private static final ResourceLocation TAA_RELOAD = new ResourceLocation("taa", "reload_time");

    private static final ResourceLocation STAMINA_MAX = new ResourceLocation("rpstamina", "max_stamina");
    private static final ResourceLocation STAMINA_REGEN = new ResourceLocation("rpstamina", "regen_multiplier");

    /** Итоговый множитель скорости с учётом переноски и лечения. */
    public static double speedFactor(MedicalData d, GameplayEffects.Mods mods, MedicalSettings s) {
        double f = mods.speed;
        // Тащить раненого сильному легче (Сила RP Perks; без него — как обычно).
        if (d.carrying) f *= 1 - Math.min(0.95, s.carrySpeedPenalty / Math.max(0.5, d.carryStrength));
        if (d.treating) f *= 1 - s.treatingSpeedPenalty;
        return Math.max(0, f);
    }

    public static void apply(ServerPlayer sp, MedicalData d, MedicalState m, GameplayEffects.Mods mods) {
        MedicalSettings s = MedicalSettings.get();
        d.lastMods = mods;
        d.carryStrength = faygolover.rpmedicine.integration.CoreCompat.strengthFactor(sp);
        m.strength = faygolover.rpmedicine.integration.CoreCompat.strength(sp);
        double speed = speedFactor(d, mods, s);
        // Стрельба поверх оружейного навыка: раны рук, боль, тремор (характеристики Tacz Attribute Add, если стоит).
        double spread = 1, reload = 1;
        if (mods.mainArmBad) {
            spread *= s.gunMainArmSpread;
            reload *= s.gunMainArmReload;
        }
        if (mods.offArmBad) {
            spread *= s.gunOffArmSpread;
            reload *= s.gunOffArmReload;
        }
        if (m.pain >= 30) spread *= s.gunPainSpread;
        if (mods.aimSway >= 0.25) spread *= s.gunTremorSpread;
        int hash = Objects.hash(speed, mods.attackFactor, mods.staminaCap, mods.staminaRegen, spread, reload);
        if (hash != d.lastModsHash) {
            d.lastModsHash = hash;
            setModifier(sp.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, "rpmedicine speed", speed - 1.0);
            setModifier(sp.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_ID, "rpmedicine attack", mods.attackFactor - 1.0);
            setModifier(attribute(sp, STAMINA_MAX), STAMINA_MAX_ID, "rpmedicine stamina cap", mods.staminaCap - 1.0);
            setModifier(attribute(sp, STAMINA_REGEN), STAMINA_REGEN_ID, "rpmedicine stamina regen", mods.staminaRegen - 1.0);
            setModifier(attribute(sp, TAA_SPREAD), GUN_SPREAD_ID, "rpmedicine gun spread", spread - 1.0);
            setModifier(attribute(sp, TAA_RELOAD), GUN_RELOAD_ID, "rpmedicine gun reload", reload - 1.0);
        }
        if ((mods.noSprint || d.carrying || m.isDown()) && sp.isSprinting()) sp.setSprinting(false);
        DownedService.updatePose(sp, m, mods);
        // Нет руки (и нет крюка) — в ней ничего не удержать: предмет в рюкзак или на землю (замечание 83).
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            boolean right = (hand == net.minecraft.world.InteractionHand.MAIN_HAND) == (sp.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
            var arm = m.part(right ? faygolover.rpmedicine.core.BodyPart.RIGHT_ARM : faygolover.rpmedicine.core.BodyPart.LEFT_ARM);
            if (!arm.missing || arm.prosthesis == faygolover.rpmedicine.core.BodyPartState.Prosthesis.HOOK) continue;
            var held = sp.getItemInHand(hand);
            if (held.isEmpty()) continue;
            sp.setItemInHand(hand, net.minecraft.world.item.ItemStack.EMPTY);
            var inv = sp.getInventory();
            boolean stored = false;
            for (int i = 9; i < 36 && !held.isEmpty(); i++) {
                if (inv.getItem(i).isEmpty()) {
                    inv.setItem(i, held);
                    stored = true;
                    break;
                }
            }
            if (!stored) sp.drop(held, false);
        }
    }

    /** Снять все модификаторы (смерть, сброс). */
    public static void clear(ServerPlayer sp, MedicalData d) {
        d.lastModsHash = 0;
        setModifier(sp.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, "", 0);
        setModifier(sp.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_ID, "", 0);
        setModifier(attribute(sp, STAMINA_MAX), STAMINA_MAX_ID, "", 0);
        setModifier(attribute(sp, STAMINA_REGEN), STAMINA_REGEN_ID, "", 0);
        setModifier(attribute(sp, TAA_SPREAD), GUN_SPREAD_ID, "", 0);
        setModifier(attribute(sp, TAA_RELOAD), GUN_RELOAD_ID, "", 0);
    }

    private static AttributeInstance attribute(ServerPlayer sp, ResourceLocation id) {
        Attribute a = ForgeRegistries.ATTRIBUTES.getValue(id);
        return a == null ? null : sp.getAttribute(a);
    }

    private static void setModifier(AttributeInstance inst, UUID id, String name, double value) {
        if (inst == null) return;
        AttributeModifier old = inst.getModifier(id);
        if (Math.abs(value) < 1e-6) {
            if (old != null) inst.removeModifier(id);
            return;
        }
        if (old != null) {
            if (Math.abs(old.getAmount() - value) < 1e-6) return;
            inst.removeModifier(id);
        }
        inst.addTransientModifier(new AttributeModifier(id, name, value, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
