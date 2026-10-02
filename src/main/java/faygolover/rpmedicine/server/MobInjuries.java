package faygolover.rpmedicine.server;

import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.InjuryProfile;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.WoundType;
import faygolover.rpmedicine.data.DamageRules;
import faygolover.rpmedicine.data.MobRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Упрощённые травмы мобов из списка датапака (п. 12 ТЗ): кровотечение (периодический урон после
 * ран), перелом ноги (замедление), болевой шок (короткое оглушение). Без частей тела; хранится в
 * persistent data моба и считается только у раненых.
 */
public final class MobInjuries {
    private MobInjuries() {}

    private static final String TAG = "rpmedicine_mob";

    public static void onHurt(LivingEntity mob, DamageSource src, float amount) {
        if (amount <= 0 || mob.level().isClientSide) return;
        MobRules.Rule rule = MobRules.INSTANCE.ruleFor(mob.getType());
        if (rule == null) return;
        InjuryProfile prof = DamageRules.INSTANCE.profileFor(src);
        if (prof.wound == null) return;
        MedicalSettings s = MedicalSettings.get();
        double sev = amount * s.severityPerDamage * prof.severityMultiplier;
        CompoundTag t = mob.getPersistentData().getCompound(TAG);
        if (rule.bleeding() && s.bleedPer(prof.wound) > 0) {
            double bleed = t.getDouble("bleed") + sev * s.bleedPer(prof.wound) / 60.0;
            t.putDouble("bleed", bleed);
            t.putInt("bleedTicks", (int) (ServerConfig.MOB_BLEED_SECONDS.get() * 20));
        }
        if (rule.fracture() && (prof.wound == WoundType.BRUISE || prof.wound == WoundType.GUNSHOT)) {
            double ch = prof.fracture.at(sev);
            if (mob.getRandom().nextDouble() < ch) {
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60 * 5, 1, false, false));
            }
        }
        if (rule.painShock() && sev * s.painPer(prof.wound) >= s.painShockThreshold) {
            int stun = (int) (ServerConfig.MOB_STUN_SECONDS.get() * 20);
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 6, false, false));
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stun, 4, false, false));
        }
        if (!t.isEmpty()) mob.getPersistentData().put(TAG, t);
    }

    /** Каждый тик моба — только у тех, у кого есть отметка о травме. */
    public static void tick(LivingEntity mob) {
        if (mob.tickCount % 20 != 0 || !mob.getPersistentData().contains(TAG)) return;
        CompoundTag t = mob.getPersistentData().getCompound(TAG);
        int ticks = t.getInt("bleedTicks") - 20;
        double bleed = t.getDouble("bleed");
        if (ticks <= 0 || bleed <= 0) {
            mob.getPersistentData().remove(TAG);
            return;
        }
        t.putInt("bleedTicks", ticks);
        float dmg = (float) (bleed * ServerConfig.MOB_BLEED_DAMAGE_PER_SEVERITY.get() * 60.0);
        if (dmg > 0) {
            mob.invulnerableTime = 0;
            mob.hurt(mob.damageSources().generic(), dmg);
        }
    }
}
