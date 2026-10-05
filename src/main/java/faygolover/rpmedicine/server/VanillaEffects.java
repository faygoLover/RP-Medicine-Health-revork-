package faygolover.rpmedicine.server;

import faygolover.rpmedicine.core.BodyPartState;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Physiology;
import faygolover.rpmedicine.integration.Integrations;
import faygolover.rpmedicine.integration.lso.LsoCompat;
import faygolover.rpmedicine.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Ванильные эффекты по решениям п. 1.8 (ТЗ второго этапа, п. 13): отравление — тошнота и рвота без
 * урона, мгновенный урон — только боль, золотое яблоко — адреналин, тотем бессмертия в руке поднимает
 * из нокдауна и клинической смерти, взрыв оглушает. Некроз от иссушения — спорный п. 16.7, не сделан.
 */
public final class VanillaEffects {
    private VanillaEffects() {}

    /**
     * Магический урон: тик отравления (1 единица при эффекте «Отравление») и мгновенный урон от зелья,
     * стрелы или облака. Возвращает true, если урон обработан здесь и травм не будет.
     */
    public static boolean onMagic(LivingEntity target, MedicalState m, DamageSource src, float amount) {
        if (!src.is(DamageTypes.MAGIC) && !src.is(DamageTypes.INDIRECT_MAGIC)) return false;
        MedicalSettings s = MedicalSettings.get();
        if (src.is(DamageTypes.MAGIC) && target.hasEffect(MobEffects.POISON) && amount <= 1.0f) {
            m.nauseaSeconds = Math.max(m.nauseaSeconds, s.poisonNauseaSeconds);
            Medical.changed(target);
            return true;
        }
        Entity direct = src.getDirectEntity();
        boolean potion = direct == null || direct instanceof ThrownPotion || direct instanceof AreaEffectCloud || direct instanceof AbstractArrow;
        if (!potion) return false;
        m.painSpike(Math.min(100, amount * s.instantDamagePainPerDamage), s.instantDamagePainSeconds);
        Medical.markHurt(target);
        Medical.changed(target);
        return true;
    }

    /** Взрыв: баротравма — глухота на 30–60 с по силе урона. */
    public static void onExplosion(LivingEntity target, MedicalState m, double amount) {
        MedicalSettings s = MedicalSettings.get();
        if (amount < s.explosionDeafMinDamage) return;
        double k = Physiology.clamp((amount - s.explosionDeafMinDamage) / Math.max(1e-6, s.explosionDeafMaxDamage - s.explosionDeafMinDamage), 0, 1);
        double seconds = s.explosionDeafMinSeconds + (s.explosionDeafMaxSeconds - s.explosionDeafMinSeconds) * k;
        m.deafSeconds = Math.max(m.deafSeconds, seconds);
        Medical.changed(target);
    }

    /** Шаг пациента: отравление держит тошноту, раз в интервал — рвота. */
    public static void prepareStep(ServerPlayer sp, MedicalState m, double dt) {
        MedicalSettings s = MedicalSettings.get();
        if (sp.hasEffect(MobEffects.POISON)) m.nauseaSeconds = Math.max(m.nauseaSeconds, s.poisonNauseaSeconds);
        if (m.nauseaSeconds <= 0 || m.isDown()) return;
        m.vomitTimer += dt;
        if (m.vomitTimer < s.poisonVomitIntervalSeconds) return;
        m.vomitTimer = 0;
        vomit(sp, m, s);
    }

    /** Рвота: минус вода, будит спящего, звук и брызги. */
    static void vomit(ServerPlayer sp, MedicalState m, MedicalSettings s) {
        if (Integrations.lso()) LsoCompat.loseThirst(sp, s.vomitLsoThirstLoss);
        else if (s.ownThirstEnabled) m.thirst = Math.max(0, m.thirst - s.vomitThirstLoss);
        if (sp.isSleeping()) sp.stopSleepInBed(true, true);
        var look = sp.getLookAngle();
        // Брызги зелёно-жёлтого цвета и лужа, которая пару минут лежит на земле.
        var color = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.62f, 0.66f, 0.18f), 1.4f);
        sp.serverLevel().sendParticles(color, sp.getX() + look.x * 0.5, sp.getEyeY() - 0.25, sp.getZ() + look.z * 0.5,
                24, 0.12, 0.08, 0.12, 0.02);
        sp.serverLevel().sendParticles(ParticleTypes.ITEM_SLIME, sp.getX() + look.x * 0.5, sp.getEyeY() - 0.25, sp.getZ() + look.z * 0.5,
                8, 0.1, 0.1, 0.1, 0.05);
        var puddle = faygolover.rpmedicine.registry.ModEntities.VOMIT.get().create(sp.serverLevel());
        if (puddle != null) {
            puddle.moveTo(sp.getX() + look.x * 0.8, sp.getEyeY() - 0.4, sp.getZ() + look.z * 0.8, 0, 0);
            sp.serverLevel().addFreshEntity(puddle);
        }
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), ModSounds.VOMIT.get(), SoundSource.VOICE, 0.9f,
                faygolover.rpmedicine.medcard.MedcardService.voicePitch(sp));
        sp.displayClientMessage(Component.translatable("rpmedicine.msg.vomit").withStyle(ChatFormatting.DARK_GREEN), true);
        Medical.changed(sp);
    }

    /** Съел: золотое яблоко — слабый стимулятор, адреналин на 60 с. */
    public static void onUseFinish(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack used = e.getItem();
        MedicalState m = Medical.state(sp);
        if (m == null) return;
        var food = used.getFoodProperties(sp);
        if (food != null && Medical.traits(sp).diabetic) {
            faygolover.rpmedicine.core.Metabolism.eat(m, food.getNutrition(), MedicalSettings.get());
            Medical.changed(sp);
        }
        if (!used.is(Items.GOLDEN_APPLE) && !used.is(Items.ENCHANTED_GOLDEN_APPLE)) return;
        MedicalSettings s = MedicalSettings.get();
        boolean ench = used.is(Items.ENCHANTED_GOLDEN_APPLE);
        // Ванильные золотые сердца и регенерация здесь не нужны: здоровье живёт в травмах.
        sp.removeEffect(MobEffects.ABSORPTION);
        sp.removeEffect(MobEffects.REGENERATION);
        m.adrenalineSeconds = Math.max(m.adrenalineSeconds, s.goldenAppleAdrenalineSeconds * (ench ? 2 : 1));
        m.addEffect(faygolover.rpmedicine.core.DrugEffect.ANALGESIA, s.goldenAppleAnalgesia * (ench ? 2 : 1), 0,
                s.goldenAppleAnalgesiaSeconds * (ench ? 2 : 1));
        Integrations.addStamina(sp, 200f * (ench ? 2 : 1));
        sp.displayClientMessage(Component.translatable("rpmedicine.msg.golden_apple").withStyle(ChatFormatting.GOLD), true);
        Medical.changed(sp);
    }

    /**
     * Тотем бессмертия в руке при входе в нокдаун или клиническую смерть (и вместо смерти от
     * мгновенно смертельного попадания): устраняет угрозу — кровь не ниже 60 %, сердце запускается,
     * артериальное кровотечение остановлено, человек встаёт. Тотем расходуется. Возвращает true, если спас.
     */
    public static boolean tryTotem(ServerPlayer sp, MedicalState m) {
        InteractionHand hand = null;
        for (InteractionHand h : InteractionHand.values()) {
            if (sp.getItemInHand(h).is(Items.TOTEM_OF_UNDYING)) {
                hand = h;
                break;
            }
        }
        if (hand == null) return false;
        ItemStack totem = sp.getItemInHand(hand);
        ItemStack used = totem.copy();
        totem.shrink(1);
        MedicalSettings s = MedicalSettings.get();
        boolean wasDown = Physiology.rescue(m, s, s.totemBloodFraction);
        for (BodyPartState ps : m.parts) ps.arterial = false;
        // Каждая рана: бросок плюс медицина держащего — чем выше, тем сильнее лечится (решения, п. 1.13).
        int level = Medical.medicineLevel(sp);
        var rnd = sp.getRandom();
        for (BodyPartState ps : m.parts) {
            for (var w : ps.wounds) {
                double share = Math.min(1, s.totemHealBase + rnd.nextDouble() * s.totemHealRandom + level * s.totemHealPerLevel);
                w.severity = Math.max(0, w.severity * (1 - share));
            }
            ps.wounds.removeIf(w -> w.severity < 1);
            if (ps.hasFracture() && rnd.nextDouble() < s.totemHealBase + level * s.totemHealPerLevel)
                ps.fracture = BodyPartState.Fracture.NONE;
            ps.internalBleed *= 0.3;
        }
        sp.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
        CriteriaTriggers.USED_TOTEM.trigger(sp, used);
        // Ванильная анимация и звук тотема у всех, кто видит.
        sp.level().broadcastEntityEvent(sp, (byte) 35);
        faygolover.rpmedicine.stats.StatsService.event(sp, "totem", "");
        Medical.changed(sp);
        if (wasDown) DownedService.onWokeUp(sp);
        return true;
    }
}
