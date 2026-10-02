package faygolover.rpmedicine.integration.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.InjuryProfile;
import faygolover.rpmedicine.core.Injuries;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.data.DamageRules;
import faygolover.rpmedicine.integration.Integrations;
import faygolover.rpmedicine.integration.zc.ZcCompat;
import faygolover.rpmedicine.server.DamageHandler;
import faygolover.rpmedicine.server.HitResolver;
import faygolover.rpmedicine.server.Medical;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Пули TaCZ (п. 3.4 ТЗ). Одно попадание TaCZ — два вызова урона (обычная и бронебойная доля), а
 * Zero Contact отменяет их и наносит свой урон заново. Поэтому урон пули копится за тик и
 * превращается в одну рану в конце тика. Исход (пробитие, плита, рикошет, шлем) определяется
 * сравнением урона до брони (событие TaCZ) и после (что дошло до цели), пока в Zero Contact нет
 * события с исходом. Хедшот считается один раз: множители TaCZ и Zero Contact остаются, свой не добавляем.
 */
public final class GunHits {
    private GunHits() {}

    private record Key(int bullet, int target) {}

    private static final class Pending {
        LivingEntity target;
        Entity shooter;
        float originalBase = -1;
        float originalMult = 1;
        boolean originalHeadshot;
        float finalBase = -1;
        boolean headshot;
        BodyPart part;
        float receivedArmorable;
        float receivedPiercing;
        boolean sawZc;
        ResourceLocation ammo;
    }

    private static final Map<Key, Pending> PENDING = new HashMap<>();
    private static final SplittableRandom RANDOM = new SplittableRandom();
    private static final ResourceLocation ZC_DAMAGE = new ResourceLocation("zerocontact", "zc_damage");

    /** До всех модов: исходный урон и хедшот TaCZ. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreFirst(EntityHurtByGunEvent.Pre e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getHurtEntity() instanceof LivingEntity target) || !Medical.isPatient(target)) return;
        Pending p = PENDING.computeIfAbsent(new Key(e.getBullet().getId(), target.getId()), k -> new Pending());
        p.target = target;
        p.shooter = e.getAttacker();
        p.originalBase = e.getBaseAmount();
        p.originalMult = e.getHeadshotMultiplier();
        p.originalHeadshot = e.isHeadShot();
        if (e.getBullet() instanceof EntityKineticBullet b) p.ammo = b.getAmmoId();
    }

    /** После всех модов (Zero Contact меняет урон шлема здесь). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPreLast(EntityHurtByGunEvent.Pre e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getHurtEntity() instanceof LivingEntity target) || !Medical.isPatient(target)) return;
        Pending p = PENDING.get(new Key(e.getBullet().getId(), target.getId()));
        if (p == null) return;
        p.finalBase = e.getBaseAmount();
        p.headshot = e.isHeadShot();
        p.part = p.headshot ? BodyPart.HEAD : partFromBullet(target, e.getBullet());
    }

    /** Лежачий, несущий тело и человек с двумя сломанными руками не стреляют. */
    @SubscribeEvent
    public static void onShoot(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getShooter() instanceof ServerPlayer sp)) return;
        if (!faygolover.rpmedicine.server.InteractionHandler.canUseWeapons(sp)) e.setCanceled(true);
    }

    private static BodyPart partFromBullet(LivingEntity target, Entity bullet) {
        Vec3 start = bullet.position();
        Vec3 motion = bullet.getDeltaMovement();
        var hit = target.getBoundingBox().inflate(0.15).clip(start.subtract(motion), start.add(motion.scale(2)));
        return HitResolver.locate(target, hit.orElse(target.getBoundingBox().getCenter()));
    }

    /** Урон пулей по пациенту: копим до конца тика. Возвращает true, если урон — от пули. */
    public static boolean onDamage(LivingEntity target, DamageSource src, float amount) {
        if (!(src.getDirectEntity() instanceof EntityKineticBullet bullet)) return false;
        Pending p = PENDING.computeIfAbsent(new Key(bullet.getId(), target.getId()), k -> new Pending());
        if (p.target == null) {
            p.target = target;
            p.shooter = src.getEntity();
            p.ammo = bullet.getAmmoId();
        }
        if (p.part == null) p.part = HitResolver.locate(target, src, target.getRandom());
        if (src.typeHolder().is(ZC_DAMAGE)) p.sawZc = true;
        if (src.is(DamageTypeTags.BYPASSES_ARMOR)) p.receivedPiercing += amount;
        else p.receivedArmorable += amount;
        return true;
    }

    public static void clear() {
        PENDING.clear();
    }

    /** Конец тика сервера: превратить накопленные попадания в раны. */
    public static void flush() {
        if (PENDING.isEmpty()) return;
        Map<Key, Pending> copy = new HashMap<>(PENDING);
        PENDING.clear();
        for (Pending p : copy.values()) {
            if (p.target == null || p.target.isRemoved()) continue;
            float received = p.receivedArmorable + p.receivedPiercing;
            if (received <= 0) continue;
            apply(p);
        }
    }

    private enum Outcome { PENETRATION, PLATE, RICOCHET, HELMET }

    private static void apply(Pending p) {
        LivingEntity target = p.target;
        MedicalState m = Medical.state(target);
        if (m == null) return;
        BodyPart part = p.part != null ? p.part : BodyPart.CHEST;
        boolean zc = Integrations.zeroContact();
        float amount;
        if (zc) {
            amount = p.receivedArmorable + p.receivedPiercing;
        } else {
            // Без Zero Contact: ванильная броня, только если попали в закрытую часть.
            amount = DamageHandlerAccess.armor(target, part, p.receivedArmorable) + p.receivedPiercing;
        }
        Outcome outcome = Outcome.PENETRATION;
        double penRatio = ServerConfig.ZC_PENETRATION_RATIO.get();
        if (zc && part == BodyPart.HEAD && ZcCompat.hasHelmet(target) && p.originalBase > 0 && p.finalBase >= 0) {
            outcome = p.finalBase / p.originalBase >= penRatio ? Outcome.PENETRATION : Outcome.HELMET;
        } else if (zc && part != BodyPart.HEAD && p.sawZc && ZcCompat.hasBodyArmor(target) && p.originalBase > 0) {
            if (p.shooter != null && ZcCompat.ricochetAngle(target, p.shooter)) outcome = Outcome.RICOCHET;
            else outcome = amount / p.originalBase >= penRatio ? Outcome.PENETRATION : Outcome.PLATE;
        }
        ResourceLocation rule = switch (outcome) {
            case PENETRATION -> DamageRules.GUN_PENETRATION;
            case PLATE -> p.originalBase >= ServerConfig.ZC_POWERFUL_ROUND_DAMAGE.get() ? DamageRules.GUN_PLATE_HEAVY : DamageRules.GUN_PLATE;
            case RICOCHET -> DamageRules.GUN_RICOCHET;
            case HELMET -> DamageRules.GUN_HELMET;
        };
        InjuryProfile prof = DamageRules.INSTANCE.byId(rule);
        // Плита закрывает грудь и живот; попадание в конечность Zero Contact сейчас тоже гасит бронежилетом
        // (известное ограничение до ответа разработчика) — тогда ушиб на самой конечности.
        DamageHandler.applyInjury(target, m, prof, amount, part, 0, p.shooter);
        if (outcome == Outcome.HELMET) {
            Injuries.addConcussion(m, ServerConfig.ZC_HELMET_CONCUSSION.get(), RANDOM.split(), MedicalSettings.get());
            Medical.changed(target);
        }
    }

    /** Доступ к броне по частям из перехвата урона. */
    private static final class DamageHandlerAccess {
        static float armor(LivingEntity target, BodyPart part, float amount) {
            if (amount <= 0) return 0;
            return DamageHandler.armorFor(target, part, amount);
        }
    }
}
