package faygolover.rpmedicine.server;

import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.HitLocator;
import faygolover.rpmedicine.core.Injuries;
import faygolover.rpmedicine.entity.BodyStubEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Точка удара → часть тела (п. 2.2 ТЗ): переводит мировые координаты в координаты тела. */
public final class HitResolver {
    private HitResolver() {}

    /** Точка удара по источнику урона или null, если её не определить. */
    @Nullable
    public static Vec3 hitPoint(LivingEntity target, DamageSource src) {
        AABB box = target.getBoundingBox().inflate(0.15);
        Entity direct = src.getDirectEntity();
        Entity attacker = src.getEntity();
        // Снаряд: отрезок его движения за тик пересекаем с хитбоксом.
        if (direct != null && direct != attacker) {
            Vec3 start = direct.position();
            Vec3 motion = direct.getDeltaMovement();
            Vec3 hit = clip(box, start.subtract(motion), start.add(motion.scale(2)));
            if (hit != null) return hit;
            if (box.contains(start)) return start;
            return closest(box, start);
        }
        // Ближний бой: луч взгляда атакующего.
        if (attacker instanceof LivingEntity le) {
            Vec3 eye = le.getEyePosition();
            Vec3 hit = clip(box, eye, eye.add(le.getLookAngle().scale(8)));
            if (hit != null) return hit;
            return closest(box, eye);
        }
        Vec3 pos = src.getSourcePosition();
        if (pos != null) return closest(box, pos);
        return null;
    }

    @Nullable
    private static Vec3 clip(AABB box, Vec3 from, Vec3 to) {
        Optional<Vec3> r = box.clip(from, to);
        return r.orElse(null);
    }

    private static Vec3 closest(AABB box, Vec3 p) {
        return new Vec3(clamp(p.x, box.minX, box.maxX), clamp(p.y, box.minY, box.maxY), clamp(p.z, box.minZ, box.maxZ));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static boolean isHorizontal(LivingEntity e) {
        if (e instanceof BodyStubEntity) return true;
        Pose p = e.getPose();
        return p == Pose.SWIMMING || p == Pose.SLEEPING || p == Pose.FALL_FLYING || p == Pose.DYING;
    }

    /** Часть тела по точке удара. */
    public static BodyPart locate(LivingEntity target, Vec3 point) {
        double yaw = Math.toRadians(target.yBodyRot);
        // Вперёд и вправо относительно разворота тела.
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        Vec3 c = target.position();
        double dx = point.x - c.x;
        double dz = point.z - c.z;
        double halfWidth = Math.max(0.2, target.getBbWidth() / 2.0);
        double lateral = (dx * rx + dz * rz) / halfWidth;
        if (isHorizontal(target)) {
            // Лёжа: вдоль тела от ног к голове (голова — в сторону взгляда), длина тела ~1,8.
            double along = 0.5 + (dx * fx + dz * fz) / 1.8;
            return HitLocator.locate(along, lateral, HitLocator.Posture.HORIZONTAL);
        }
        double h = (point.y - target.getY()) / Math.max(0.1, target.getBbHeight());
        HitLocator.Posture posture = target.isCrouching() ? HitLocator.Posture.CROUCHING : HitLocator.Posture.STANDING;
        return HitLocator.locate(h, lateral, posture);
    }

    /** Часть тела по источнику урона; если точку не определить — случайная. */
    public static BodyPart locate(LivingEntity target, DamageSource src, RandomSource rnd) {
        Vec3 p = hitPoint(target, src);
        if (p == null) return Injuries.randomPart(new java.util.Random(rnd.nextLong()));
        return locate(target, p);
    }

    /** С какой стороны источник: от −1 (слева) до 1 (справа) — для взрыва. */
    public static double sideOf(LivingEntity target, DamageSource src) {
        Vec3 pos = src.getSourcePosition();
        if (pos == null) return 0;
        double yaw = Math.toRadians(target.yBodyRot);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double dx = pos.x - target.getX();
        double dz = pos.z - target.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return 0;
        return (dx * rx + dz * rz) / len;
    }
}
