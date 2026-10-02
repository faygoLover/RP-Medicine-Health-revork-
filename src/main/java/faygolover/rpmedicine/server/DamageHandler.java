package faygolover.rpmedicine.server;

import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.InjuryProfile;
import faygolover.rpmedicine.core.Injuries;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.PatientTraits;
import faygolover.rpmedicine.data.DamageRules;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.integration.Integrations;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import org.jetbrains.annotations.Nullable;

import java.util.SplittableRandom;

/**
 * Перехват урона (п. 2, 4.6 ТЗ): ванильное здоровье игрока всегда полное, урон превращается в
 * травмы. Обработка на самом низком приоритете — после того, как другие моды (Zero Contact и т.п.)
 * изменили урон, но до ванильной брони, которую мы применяем сами только к закрытой части.
 */
public final class DamageHandler {
    private DamageHandler() {}

    private static final SplittableRandom RANDOM = new SplittableRandom();

    /** Урон, который должен убивать по-настоящему: /kill, пустота, наши типы смерти. */
    public static boolean isTrueDeath(DamageSource src) {
        return src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || DownedService.isOwnDeathType(src);
    }

    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (!Medical.isPatient(target)) {
            MobInjuries.onHurt(target, event.getSource(), event.getAmount());
            return;
        }
        DamageSource src = event.getSource();
        if (isTrueDeath(src)) return;
        long t0 = System.nanoTime();
        MedicalState m = Medical.state(target);
        if (m == null) return;
        float amount = event.getAmount();
        event.setCanceled(true);
        if (amount <= 0) return;

        // Пули TaCZ: часть тела и исход считает интеграция, раны — в конце тика.
        if (Integrations.handleGunDamage(target, src, amount)) {
            interrupt(target);
            Profiler.recordOther(System.nanoTime() - t0);
            return;
        }

        InjuryProfile prof = DamageRules.INSTANCE.profileFor(src);
        BodyPart part = null;
        double side = 0;
        if (prof.location == InjuryProfile.Location.HIT_POINT) {
            part = HitResolver.locate(target, src, target.getRandom());
            amount = armor(target, part, src, amount);
        } else if (prof.location == InjuryProfile.Location.EXPLOSION) {
            side = HitResolver.sideOf(target, src);
            amount = armorGeneric(target, src, amount);
        } else if (prof.location != InjuryProfile.Location.NONE) {
            amount = armorGeneric(target, src, amount);
        }
        applyInjury(target, m, prof, amount, part, side, src.getEntity());
        Profiler.recordOther(System.nanoTime() - t0);
    }

    /** Применить урон к пациенту: мгновенная смерть или раны. Общий путь для обычного урона и пуль. */
    public static void applyInjury(LivingEntity target, MedicalState m, InjuryProfile prof, double amount, BodyPart part, double side,
                                   @Nullable Entity killer) {
        if (amount <= 0) return;
        Medical.markHurt(target);
        MedicalSettings s = MedicalSettings.get();
        PatientTraits traits = Medical.traits(target);
        if (part != null && Injuries.isInstantlyLethal(part, amount, s) && m.down != MedicalState.Down.CLINICAL) {
            Injuries.apply(m, prof, amount, part, side, traits, RANDOM.split(), s);
            DownedService.lethal(target, DownedService.FINISHED, killer);
        } else {
            Injuries.Report rep = Injuries.apply(m, prof, amount, part, side, traits, RANDOM.split(), s);
            // Ранен в воде — грязная рана, выше шанс заражения (второй этап, п. 5.1).
            if (target.isInWater()) for (var w : rep.wounds) w.infectionRisk = Math.max(w.infectionRisk, s.dirtyWaterInfectionFactor);
            if (target instanceof ServerPlayer sp) Feedback.onInjury(sp, rep);
            MedcardHooks.injury(target, rep, part);
        }
        Medical.changed(target);
        interrupt(target);
    }

    private static void interrupt(LivingEntity target) {
        if (target instanceof ServerPlayer sp) ActionManager.cancel(sp, "rpmedicine.action.interrupted_damage");
    }

    /** Ванильная броня — только если попали в часть, закрытую бронёй (п. 2.3 ТЗ). */
    static float armor(LivingEntity target, BodyPart part, DamageSource src, float amount) {
        EquipmentSlot slot = switch (part.kind) {
            case HEAD -> EquipmentSlot.HEAD;
            case TORSO, ARM -> EquipmentSlot.CHEST;
            case LEG -> EquipmentSlot.LEGS;
            case FOOT -> EquipmentSlot.FEET;
        };
        ItemStack piece = target.getItemBySlot(slot);
        if (!piece.isEmpty() && !src.is(DamageTypeTags.BYPASSES_ARMOR)) {
            float reduced = CombatRules.getDamageAfterAbsorb(amount, (float) target.getArmorValue(),
                    (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            double k = ServerConfig.PROTECTED_ARMOR_EFFECT.get();
            amount = (float) (amount - (amount - reduced) * k);
            if (piece.isDamageableItem() && !(target instanceof Player p && p.getAbilities().instabuild))
                piece.hurtAndBreak(Math.max(1, (int) (amount / 4)), target, e -> e.broadcastBreakEvent(slot));
        }
        return effectsAndEnchantments(target, src, amount);
    }

    /** Броня по части для урона пули без Zero Contact. */
    public static float armorFor(LivingEntity target, BodyPart part, float amount) {
        return armor(target, part, target.damageSources().generic(), amount);
    }

    /** Ненаправленный урон (взрыв, падение, огонь): ванильная броня как обычно. */
    static float armorGeneric(LivingEntity target, DamageSource src, float amount) {
        if (!src.is(DamageTypeTags.BYPASSES_ARMOR))
            amount = CombatRules.getDamageAfterAbsorb(amount, (float) target.getArmorValue(), (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        return effectsAndEnchantments(target, src, amount);
    }

    private static float effectsAndEnchantments(LivingEntity target, DamageSource src, float amount) {
        if (target.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !src.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            int amp = target.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1;
            amount = Math.max(0, amount * (1 - amp * 0.2f));
        }
        if (!src.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            int prot = EnchantmentHelper.getDamageProtection(target.getArmorSlots(), src);
            if (prot > 0) amount = CombatRules.getDamageAfterMagicAbsorb(amount, prot);
        }
        return amount;
    }

    /** Лечение от зелий и чужих модов не лечит, а ускоряет заживление (п. 4.6 ТЗ). */
    public static void onHeal(LivingHealEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !Medical.isPatient(e)) return;
        MedicalState m = Medical.state(e);
        event.setCanceled(true);
        if (m != null && event.getAmount() > 0) {
            m.healBoostSeconds += event.getAmount() * ServerConfig.VANILLA_HEAL_BOOST_SECONDS.get();
            Medical.changed(e);
        }
    }

    /**
     * Режим «без смерти»: смерть от чужих причин (не /kill, не пустота, не наши типы) отменяется и
     * заменяется клинической смертью.
     */
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        if (!(e instanceof ServerPlayer) && !(e instanceof BodyStubEntity)) return;
        if (!MedicalSettings.get().noDeathMode || isTrueDeath(event.getSource())) return;
        event.setCanceled(true);
        e.setHealth(e.getMaxHealth());
        DownedService.lethal(e, DownedService.BRAIN_DEATH, null);
    }
}
